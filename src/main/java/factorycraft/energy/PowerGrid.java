package factorycraft.energy;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.FactoryKind;
import factorycraft.config.FactoryConfig;
import factorycraft.simulation.power.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Server adapter. Graph rebuilding has a separate per-level work budget and never loads chunks. */
@EventBusSubscriber(modid=FactoryCraft.ID)
public final class PowerGrid {
    private static final Map<Level,State> STATES=new IdentityHashMap<>();
    private static final class State {
        final PowerTopology<FactoryBlockEntity> graph=new PowerTopology<>(FactoryConfig.POLE_RADIUS.get());
        long delivered,totalNanos;
    }
    public static void add(FactoryBlockEntity be) {
        var state=STATES.computeIfAbsent(be.getLevel(),l->new State());
        var p=be.getBlockPos();
        var kind=be instanceof PoleBlockEntity ? PowerTopology.Kind.POLE:
                be instanceof MachineBlockEntity machine && machine.kind()==FactoryKind.GENERATOR ? PowerTopology.Kind.SOURCE:PowerTopology.Kind.CONSUMER;
        state.graph.add(be,new PowerPoint(p.getX(),p.getY(),p.getZ()),kind,
                kind==PowerTopology.Kind.CONSUMER ? ((GridConsumer)be)::receiveGridEnergy:null);
    }
    public static void remove(FactoryBlockEntity be) { var s=STATES.get(be.getLevel());if(s!=null)s.graph.remove(be); }
    @SubscribeEvent public static void tick(LevelTickEvent.Post e) {
        if(e.getLevel().isClientSide)return;
        var state=STATES.get(e.getLevel());if(state==null)return;
        state.graph.setRadius(FactoryConfig.POLE_RADIUS.get());
        long start=System.nanoTime();state.graph.advance(FactoryConfig.POWER_GRAPH_WORK.get());state.totalNanos+=System.nanoTime()-start;
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) { STATES.remove(e.getLevel()); }
    public static boolean hasDemand(MachineBlockEntity source) {
        var s=STATES.get(source.getLevel());return s!=null && s.graph.prepareDemand(s.graph.find(source),32);
    }
    public static int distribute(MachineBlockEntity source,int available) {
        var s=STATES.get(source.getLevel());if(s==null)return 0;
        int delivered=s.graph.distributePrepared(s.graph.find(source),available);s.delivered+=delivered;return delivered;
    }
    public static String statistics(Level level) {
        var s=STATES.get(level);if(s==null)return "Power nodes: 0";
        var g=s.graph;
        return "Power nodes: "+g.size()+", networks: "+g.networks()+", rebuilding: "+g.pending()+
                ", rebuild work: "+g.lastWork()+"/"+FactoryConfig.POWER_GRAPH_WORK.get()+", snapshots: "+g.rebuilds()+
                ", candidates: "+g.candidateChecks()+", delivered: "+s.delivered+" FE, rebuild CPU: "+String.format(Locale.ROOT,"%.3f ms",s.totalNanos/1e6);
    }
    public record Snapshot(int nodes,int networks,boolean pending,long rebuilds,long candidates,long delivered,long rebuildNanos) {}
    public static Snapshot snapshot(Level level) {
        var s=STATES.get(level);if(s==null)return new Snapshot(0,0,false,0,0,0,0);
        return new Snapshot(s.graph.size(),s.graph.networks(),s.graph.pending(),s.graph.rebuilds(),s.graph.candidateChecks(),s.delivered,s.totalNanos);
    }
    private PowerGrid() {}
}
