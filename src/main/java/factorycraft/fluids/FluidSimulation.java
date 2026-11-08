package factorycraft.fluids;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.blocks.FluidDeviceBlock;
import factorycraft.config.FactoryConfig;
import factorycraft.simulation.fluids.FluidTransport;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

/** Loaded-only adjacency. Empty/balanced edges and blocked/unpowered pumps sleep until relevant changes. */
@EventBusSubscriber(modid=FactoryCraft.ID)
public final class FluidSimulation {
    public static final int PUMP_RATE=100,PUMP_FE=20,EDGE_RATE=100;
    private static final Map<Level,State> STATES=new IdentityHashMap<>();
    private static final class Link {
        final FluidBlockEntity be;
        final FluidTransport.Node<FluidKey> node;
        final Link[] neighbors=new Link[6];
        boolean queued,dirty,removed;
        FluidKey water;
        Link(FluidBlockEntity be,FluidTransport.Node<FluidKey> node) { this.be=be;this.node=node; }
    }
    private static final class State {
        final FluidTransport<FluidKey> transport=new FluidTransport<>();
        final Map<BlockPos,Link> nodes=new HashMap<>();
        final ArrayDeque<Link> pumps=new ArrayDeque<>();
        final ArrayList<Link> dirty=new ArrayList<>();
        long nanos,pumped;
    }
    public static void register(FluidBlockEntity be) {
        var s=STATES.computeIfAbsent(be.getLevel(),level->new State());
        if(s.nodes.containsKey(be.getBlockPos()))throw new IllegalStateException("Duplicate fluid node");
        s.nodes.put(be.getBlockPos(),new Link(be,s.transport.add(be.cell(),be::simulationAvailable)));topologyChanged(be);changed(be);
    }
    public static void unregister(FluidBlockEntity be) {
        var s=STATES.get(be.getLevel());if(s==null)return;
        var link=s.nodes.remove(be.getBlockPos());if(link==null)return;
        link.removed=true;s.transport.remove(link.node);
        for(var neighbor:link.neighbors)if(neighbor!=null)refresh(s,neighbor);
        if(s.nodes.isEmpty())STATES.remove(be.getLevel());
    }
    public static void topologyChanged(FluidBlockEntity be) {
        var s=STATES.get(be.getLevel());if(s==null)return;var link=s.nodes.get(be.getBlockPos());if(link==null)return;
        refresh(s,link);
        for(var direction:Direction.values()) { var other=s.nodes.get(be.getBlockPos().relative(direction));if(other!=null)refresh(s,other); }
    }
    private static void refresh(State s,Link link) {
        if(link.removed)return;
        for(var direction:Direction.values()) {
            int index=direction.get3DDataValue();var previous=link.neighbors[index];
            var next=s.nodes.get(link.be.getBlockPos().relative(direction));
            if(next!=null && (!FluidBlocks.port(link.be.getBlockState(),direction) || !FluidBlocks.port(next.be.getBlockState(),direction.getOpposite())))next=null;
            if(previous==next)continue;
            if(previous!=null && !previous.removed && link.be.kind()!=FluidKind.PUMP && previous.be.kind()!=FluidKind.PUMP)s.transport.disconnect(link.node,previous.node);
            link.neighbors[index]=next;
            if(next!=null && link.be.kind()!=FluidKind.PUMP && next.be.kind()!=FluidKind.PUMP)s.transport.connect(link.node,next.node);
            if(previous!=null)wake(s,previous);if(next!=null)wake(s,next);
        }
        wake(s,link);
    }
    private static void wake(State s,Link link) {
        if(!link.removed && link.be.kind()==FluidKind.PUMP && !link.queued) { link.queued=true;s.pumps.addLast(link); }
    }
    public static void activatePump(FluidBlockEntity be) {
        var s=STATES.get(be.getLevel());if(s!=null) { var link=s.nodes.get(be.getBlockPos());if(link!=null)wake(s,link); }
    }
    public static void changed(FluidBlockEntity be) {
        var s=STATES.get(be.getLevel());if(s==null)return;var link=s.nodes.get(be.getBlockPos());if(link==null)return;
        s.transport.wake(link.node);
        if(!link.dirty) { link.dirty=true;s.dirty.add(link); }
        wake(s,link);for(var neighbor:link.neighbors)if(neighbor!=null)wake(s,neighbor);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        var level=event.getLevel();if(level.isClientSide)return;var s=STATES.get(level);if(s==null)return;
        long start=System.nanoTime();int jobs=Math.min(s.pumps.size(),FactoryConfig.FLUID_WORK.get());
        for(int i=0;i<jobs;i++) {
            var link=s.pumps.removeFirst();link.queued=false;if(link.removed || !link.be.loaded())continue;
            if(!link.be.simulationAvailable()) { wake(s,link);continue; }
            boolean worked=pump(s,link);
            if(link.be.loaded() && link.be.getBlockState().getValue(FluidDeviceBlock.ACTIVE)!=worked)
                level.setBlock(link.be.getBlockPos(),link.be.getBlockState().setValue(FluidDeviceBlock.ACTIVE,worked),3);
        }
        s.transport.tick(FactoryConfig.FLUID_WORK.get(),EDGE_RATE);
        for(int i=0;i<s.dirty.size();) {
            var link=s.dirty.get(i);
            if(link.removed || Math.floorMod(level.getGameTime()+link.be.getBlockPos().asLong(),5)==0) {
                if(!link.removed)link.be.flushUpdate();link.dirty=false;
                int last=s.dirty.size()-1;s.dirty.set(i,s.dirty.get(last));s.dirty.remove(last);
            } else i++;
        }
        s.nanos+=System.nanoTime()-start;
    }
    private static boolean pump(State s,Link link) {
        var be=link.be;var cell=be.cell();if(be.power.getEnergyStored()<PUMP_FE)return false;
        var facing=be.facing();var input=link.neighbors[facing.getOpposite().get3DDataValue()];var output=link.neighbors[facing.get3DDataValue()];
        if(input!=null && !input.be.simulationAvailable())input=null;
        if(output!=null && !output.be.simulationAvailable())output=null;
        if(input!=null && input.be.kind()==FluidKind.PUMP && input.be.facing()!=facing)input=null;
        if(output!=null && output.be.kind()==FluidKind.PUMP && output.be.facing()!=facing)output=null;
        int out=output==null ? 0:cell.transferTo(output.be.cell(),PUMP_RATE,true);
        int in=input==null ? 0:input.be.cell().transferTo(cell,PUMP_RATE,true);
        boolean source=false;
        if(input==null && cell.space()>0) {
            var pos=be.getBlockPos().relative(facing.getOpposite());var level=be.getLevel();
            source=level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource();
            if(source) {
                if(link.water==null)link.water=new FluidKey(new FluidStack(Fluids.WATER,1));
                in=cell.fill(link.water,PUMP_RATE,true);
            }
        }
        if(in==0 && out==0 || !be.power.spend(PUMP_FE))return false;
        int moved=0;
        if(in>0) {
            if(source)moved+=cell.fill(link.water,in,false);
            else if(input!=null && !input.removed)moved+=input.be.cell().transferTo(cell,in,false);
        }
        // A source container's neighbor callback may have removed the pump after the first commit.
        if(be.loaded() && out>0 && output!=null && !output.removed)moved+=cell.transferTo(output.be.cell(),out,false);
        s.pumped+=moved;return moved>0;
    }
    public static String statistics(Level level) {
        var s=STATES.get(level);return s==null ? "Fluid nodes: 0":"Fluid nodes: "+s.nodes.size()+", passive segments: "+s.transport.components()+", passive edges: "+s.transport.edges()+
                ", pending edges: "+s.transport.pendingEdges()+", queued pumps: "+s.pumps.size()+", edge visits: "+s.transport.visits()+
                ", transferred: "+s.transport.volumeMoved()+" mB, pump chamber transfers: "+s.pumped+" mB, CPU: "+String.format(Locale.ROOT,"%.3f ms",s.nanos/1e6);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        var s=STATES.remove(event.getLevel());if(s==null)return;
        for(var link:s.nodes.values()) { link.removed=true;s.transport.remove(link.node);link.be.onChunkUnloaded(); }
        s.nodes.clear();s.dirty.clear();s.pumps.clear();
    }
    @SubscribeEvent public static void chunkUnloading(ChunkEvent.Unload event) {
        if(!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) || chunk.getLevel().isClientSide)return;
        for(var be:chunk.getBlockEntities().values())if(be instanceof FluidBlockEntity fluid) {
            // ChunkMap fires this before serializing an outgoing chunk, earlier than BE unload callbacks.
            chunk.setUnsaved(true);fluid.onChunkUnloaded();
        }
    }
    private FluidSimulation() {}
}
