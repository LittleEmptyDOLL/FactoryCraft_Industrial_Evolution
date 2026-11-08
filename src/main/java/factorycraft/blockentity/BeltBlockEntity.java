package factorycraft.blockentity;

import factorycraft.blocks.*;
import factorycraft.registry.Content;
import factorycraft.simulation.belts.*;
import factorycraft.simulation.BeltSimulation;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.*;

/** One controller owns either one two-lane belt or both physical splitter tracks. */
public final class BeltBlockEntity extends FactoryBlockEntity implements factorycraft.multiblock.AssemblyOwner {
    private final factorycraft.multiblock.IndustrialAssembly assembly;
    private final List<BeltLane<ItemStack>> lanes;
    private final List<BeltTransport.Node<ItemStack>> nodes=new ArrayList<>(4);
    private final boolean[] alternate;
    private final IItemHandler[] handlers;
    private BeltTransport<ItemStack> transport;
    private boolean queuedForSync,contentsChanged;
    public boolean simulationLoaded;
    private long snapshotTick;
    public BeltBlockEntity(BlockPos p,BlockState s) {
        super(Content.BELT.get(),p,s);boolean split=((FactoryBlock)s.getBlock()).kind==FactoryKind.SPLITTER;
        assembly=split ? new factorycraft.multiblock.IndustrialAssembly(this):null;
        lanes=new ArrayList<>(split ? 4:2);for(int i=0;i<(split ? 4:2);i++)lanes.add(new BeltLane<>());
        alternate=new boolean[lanes.size()];handlers=new IItemHandler[split ? 2:1];for(int i=0;i<handlers.length;i++)handlers[i]=new TrackHandler(i);
    }
    public factorycraft.multiblock.IndustrialAssembly assembly() { return assembly; }
    public boolean loaded() { return simulationLoaded && !isRemoved(); }
    public boolean structureReady() { return assembly==null || assembly.ready(); }
    private boolean available() { return loaded() && structureReady() && factorycraft.fluids.FluidConnections.canonical(this); }
    public int laneCount() { return lanes.size(); }
    public int tracks() { return handlers.length; }
    public BeltLane<ItemStack> lane(int i) { return lanes.get(i); }
    private BeltTransport.Node<ItemStack> node(int i) { return nodes.get(i); }
    public BlockPos trackPosition(int track) { Objects.checkIndex(track,tracks());return worldPosition.relative(getBlockState().getValue(FactoryBlock.FACING).getClockWise(),track); }
    public int trackAt(BlockPos physical) { for(int i=0;i<tracks();i++)if(trackPosition(i).equals(physical))return i;return -1; }
    public IItemHandler inventoryAt(BlockPos physical) { int track=trackAt(physical);return track<0 ? null:handlers[track]; }
    public double speed() { return ((FactoryBlock)getBlockState().getBlock()).kind.beltTier*.09375; }
    public long snapshotTick() { return snapshotTick; }
    public IItemHandler inventory() { return handlers[0]; }
    public boolean hasItems() { for(var lane:lanes)if(!lane.carriers().isEmpty())return true;return false; }
    public boolean isScheduled() { for(var n:nodes)if(n.isActive())return true;return false; }
    private final class TrackHandler implements IItemHandler {
        private final int track;
        TrackHandler(int track) { this.track=track; }
        public int getSlots() { return 8; }
        private int index(int slot) { Objects.checkIndex(slot,8);return track*2+slot/4; }
        public ItemStack getStackInSlot(int slot) { var lane=lane(index(slot));return slot%4<lane.carriers().size() ? lane.carriers().get(slot%4).value.copy():ItemStack.EMPTY; }
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {
            int index=index(slot);if(stack.isEmpty() || !available() || transport==null || !transport.canInsert(node(index)))return stack;
            if(!simulate) { if(!transport.insert(node(index),stack.copyWithCount(1)))return stack;sync(); }
            return stack.getCount()==1 ? ItemStack.EMPTY:stack.copyWithCount(stack.getCount()-1);
        }
        public ItemStack extractItem(int slot,int amount,boolean simulate) {
            var lane=lane(index(slot));if(!available() || amount<=0 || slot%4>=lane.carriers().size())return ItemStack.EMPTY;
            var result=lane.carriers().get(slot%4).value.copy();if(!simulate) { lane.remove(slot%4);sync();wakeTransport(); }return result;
        }
        public int getSlotLimit(int slot) { index(slot);return 1; }
        public boolean isItemValid(int slot,ItemStack stack) { index(slot);return true; }
    }
    public void bindTransport(BeltTransport<ItemStack> transport) {
        this.transport=transport;int tier=((FactoryBlock)getBlockState().getBlock()).kind.beltTier;
        for(int i=0;i<laneCount();i++) { var n=transport.create(lane(i),tier,c->BeltSimulation.changed(this,c));n.restorePreference(alternate[i]);nodes.add(n); }
    }
    public void unbindTransport() {
        if(transport==null)return;for(int i=0;i<nodes.size();i++) { alternate[i]=node(i).prefersSecondary();transport.remove(node(i)); }nodes.clear();transport=null;
    }
    public void wakeTransport() { if(transport!=null)for(var n:nodes)transport.changedExternally(n); }
    public void refreshConnections() {
        if(transport==null || level==null)return;boolean ready=structureReady();var facing=getBlockState().getValue(FactoryBlock.FACING);
        for(int i=0;i<laneCount();i++) {
            var n=node(i);transport.setAvailable(n,ready);
            transport.connect(n,ready ? targetAt(i%2,trackPosition(0).relative(facing),facing):null,
                    ready && tracks()==2 ? targetAt(i%2,trackPosition(1).relative(facing),facing):null);
        }
    }
    private BeltTransport.Node<ItemStack> targetAt(int lane,BlockPos physical,Direction output) {
        var target=BeltSimulation.at(level,physical);
        if(target==null || !target.loaded() || target.transport==null || !target.structureReady())return null;
        int track=target.trackAt(physical);if(track<0)return null;var facing=target.getBlockState().getValue(FactoryBlock.FACING);
        if(facing==output.getOpposite())return null;
        int side=facing==output ? lane:output==facing.getClockWise() ? 0:1;
        return target.node(track*2+side);
    }
    public boolean markSimulationChanged(boolean contents) { contentsChanged|=contents;if(queuedForSync)return false;queuedForSync=true;return true; }
    public void flushSimulationChanges(long tick) {
        factorycraft.diagnostics.FactoryTelemetry.beltChanged(this);if(contentsChanged)setChanged();else markPersistentState();
        if(contentsChanged || (tick&3)==0)sendUpdate();queuedForSync=false;contentsChanged=false;
    }
    public void setBlockState(BlockState state) {
        boolean rotated=getBlockState().getValue(FactoryBlock.FACING)!=state.getValue(FactoryBlock.FACING);super.setBlockState(state);
        if(rotated && simulationLoaded)BeltSimulation.topologyChanged(this);
    }
    public void onLoad() { super.onLoad();if(!level.isClientSide && !simulationLoaded) { simulationLoaded=true;BeltSimulation.register(this);if(assembly!=null)assembly.invalidate(); } }
    public void setRemoved() { if(simulationLoaded)BeltSimulation.unregister(this);super.setRemoved(); }
    public void onChunkUnloaded() { if(simulationLoaded)BeltSimulation.unregister(this);super.onChunkUnloaded(); }
    public void dropContents() {
        if(level==null || level.isClientSide)return;if(assembly!=null) { assembly.removeWhole(true);return; }
        for(var lane:lanes)while(!lane.carriers().isEmpty())net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,lane.remove(0));
        setChanged();wakeTransport();
    }
    protected void saveAdditional(CompoundTag tag,HolderLookup.Provider r) {
        super.saveAdditional(tag,r);if(assembly!=null)assembly.save(tag);tag.putInt("Schema",tracks()==2 ? 3:2);
        tag.putLong("SnapshotTick",level==null ? 0:level.getGameTime());
        for(int i=0;i<laneCount();i++) {
            tag.putBoolean(i==0 ? "Alternate":i==1 ? "AlternateRight":"Alternate"+i,transport==null ? alternate[i]:node(i).prefersSecondary());
            var list=new ListTag();for(var carrier:lane(i).carriers()) { var n=new CompoundTag();n.put("Stack",carrier.value.save(r));n.putDouble("Position",carrier.position());n.putInt("Units",carrier.positionUnits());list.add(n); }tag.put("Lane"+i,list);
        }
    }
    protected void loadAdditional(CompoundTag tag,HolderLookup.Provider r) {
        super.loadAdditional(tag,r);if(assembly!=null)assembly.load(tag);snapshotTick=tag.getLong("SnapshotTick");
        for(int i=0;i<laneCount();i++) {
            alternate[i]=tag.getBoolean(i==0 ? "Alternate":i==1 ? "AlternateRight":"Alternate"+i);if(i==1 && !tag.contains("AlternateRight"))alternate[i]=alternate[0];
            lane(i).clear();var list=tag.getList("Lane"+i,Tag.TAG_COMPOUND);
            for(int j=0;j<list.size();j++) { var n=list.getCompound(j);var stack=ItemStack.parseOptional(r,n.getCompound("Stack"));double position=n.contains("Units") ? (double)n.getInt("Units")/BeltLane.LENGTH:n.getDouble("Position");if(!stack.isEmpty())lane(i).restore(stack,position); }
            if(transport!=null)node(i).restorePreference(alternate[i]);
        }
        if(transport!=null)wakeTransport();
    }
}
