package factorycraft.simulation;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.BeltBlockEntity;
import factorycraft.simulation.belts.BeltTransport;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Loaded-only topology adapter. The independent kernel schedules moving lanes, not the entire factory. */
@EventBusSubscriber(modid=FactoryCraft.ID)
public final class BeltSimulation {
    private static final Map<Level,State> STATES=new IdentityHashMap<>();
    private static final class State {
        final BeltTransport<ItemStack> transport=new BeltTransport<>();
        final ArrayList<BeltBlockEntity> dirty=new ArrayList<>();
        int loaded;
        long nanos;
    }
    public static void register(BeltBlockEntity be) {
        var s=STATES.computeIfAbsent(be.getLevel(),l->new State());s.loaded++;
        be.bindTransport(s.transport);topologyChanged(be);activate(be);
    }
    public static void unregister(BeltBlockEntity be) {
        var s=STATES.get(be.getLevel());if(s!=null)s.loaded--;
        be.simulationLoaded=false;be.unbindTransport();topologyChanged(be);
    }
    public static void activate(BeltBlockEntity be) { if(be.simulationLoaded)be.wakeTransport(); }
    public static void topologyChanged(BeltBlockEntity be) {
        var level=be.getLevel();if(level==null || level.isClientSide)return;
        if(be.simulationLoaded)be.refreshConnections();
        for(var d:Direction.Plane.HORIZONTAL) {
            var p=be.getBlockPos().relative(d);
            if(level.hasChunkAt(p) && level.getBlockEntity(p) instanceof BeltBlockEntity neighbor && neighbor.simulationLoaded)
                neighbor.refreshConnections();
        }
    }
    public static void changed(BeltBlockEntity be,boolean contents) {
        var state=STATES.get(be.getLevel());
        if(state!=null && be.markSimulationChanged(contents))state.dirty.add(be);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post e) {
        if(e.getLevel().isClientSide)return;
        var s=STATES.get(e.getLevel());if(s==null)return;
        long start=System.nanoTime();s.transport.tick();s.nanos+=System.nanoTime()-start;
        for(var be:s.dirty)if(!be.isRemoved())be.flushSimulationChanges(e.getLevel().getGameTime());
        s.dirty.clear();
    }
    public static String statistics(Level level) {
        var s=STATES.get(level);
        return s==null ? "Belts: 0" : "Belts: "+s.loaded+", active lanes: "+s.transport.activeLanes()+
                ", microstep visits: "+s.transport.visits()+", transfers: "+s.transport.transfers()+
                ", CPU: "+String.format(Locale.ROOT,"%.3f ms",s.nanos/1e6);
    }
    public record Snapshot(int loaded,int activeLanes,long visits,long transfers,long kernelNanos) {}
    public static Snapshot snapshot(Level level) {
        var s=STATES.get(level);return s==null ? new Snapshot(0,0,0,0,0):
                new Snapshot(s.loaded,s.transport.activeLanes(),s.transport.visits(),s.transport.transfers(),s.nanos);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) { STATES.remove(e.getLevel()); }
    private BeltSimulation() {}
}
