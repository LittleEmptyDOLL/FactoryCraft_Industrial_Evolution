package factorycraft.energy;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.FactoryKind;
import factorycraft.config.FactoryConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.util.*;

/** Cached loaded-node graph. Consumer work is bounded to 32 nodes/generator/tick, with round-robin fairness. */
@EventBusSubscriber(modid=FactoryCraft.ID)
public final class PowerGrid {
    private static final Map<Level, State> STATES = new IdentityHashMap<>();
    private static final class Route {
        final List<MachineBlockEntity> consumers;
        int cursor;
        Route(List<MachineBlockEntity> c) { consumers=c; }
    }
    private static final class State {
        final Set<FactoryBlockEntity> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<MachineBlockEntity, Route> routes = new IdentityHashMap<>();
        boolean dirty=true;
        int radius;
    }
    public static void add(FactoryBlockEntity be) { var s=STATES.computeIfAbsent(be.getLevel(),l->new State()); if(s.nodes.add(be))s.dirty=true; }
    public static void remove(FactoryBlockEntity be) { var s=STATES.get(be.getLevel()); if(s!=null && s.nodes.remove(be))s.dirty=true; }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) { STATES.remove(e.getLevel()); }
    private static boolean covered(BlockPos a, BlockPos b, int radius) { return a.distSqr(b) <= (double)radius*radius; }
    private static State state(MachineBlockEntity generator) {
        State s=STATES.computeIfAbsent(generator.getLevel(),l->new State());
        int radius=FactoryConfig.POLE_RADIUS.get();
        if(!s.dirty && s.radius==radius)return s;
        s.routes.clear(); s.radius=radius; s.dirty=false;
        var poles=s.nodes.stream().filter(n->n instanceof PoleBlockEntity && !n.isRemoved()).toList();
        int[] parent=new int[poles.size()]; for(int i=0;i<parent.length;i++)parent[i]=i;
        for(int i=0;i<poles.size();i++) for(int j=0;j<i;j++)
            if(covered(poles.get(i).getBlockPos(),poles.get(j).getBlockPos(),radius))parent[root(parent,i)]=root(parent,j);
        Map<MachineBlockEntity,Set<Integer>> memberships=new IdentityHashMap<>();
        for(var node:s.nodes) if(node instanceof MachineBlockEntity m && !m.isRemoved()) {
            Set<Integer> groups=new HashSet<>();
            for(int p=0;p<poles.size();p++)if(covered(m.getBlockPos(),poles.get(p).getBlockPos(),radius))groups.add(root(parent,p));
            memberships.put(m,groups);
        }
        for(var entry:memberships.entrySet()) {
            if(entry.getKey().kind()!=FactoryKind.GENERATOR)continue;
            List<MachineBlockEntity> consumers=new ArrayList<>();
            for(var target:memberships.entrySet()) if(target.getKey().kind()!=FactoryKind.GENERATOR && !Collections.disjoint(entry.getValue(),target.getValue()))consumers.add(target.getKey());
            consumers.sort(Comparator.comparingLong(m->m.getBlockPos().asLong()));
            s.routes.put(entry.getKey(),new Route(consumers));
        }
        return s;
    }
    private static int root(int[] p,int i) { while(p[i]!=i){p[i]=p[p[i]];i=p[i];} return i; }
    public static boolean hasDemand(MachineBlockEntity generator) {
        Route r=state(generator).routes.get(generator); if(r==null || r.consumers.isEmpty())return false;
        int limit=Math.min(32,r.consumers.size());
        for(int i=0;i<limit;i++)if(r.consumers.get((r.cursor+i)%r.consumers.size()).power.receiveEnergy(1,true)>0)return true;
        r.cursor=(r.cursor+limit)%r.consumers.size(); return false;
    }
    public static void distribute(MachineBlockEntity generator,int available) {
        Route r=state(generator).routes.get(generator); if(r==null || r.consumers.isEmpty())return;
        int limit=Math.min(32,r.consumers.size());
        for(int i=0;i<limit && available>0;i++) {
            var m=r.consumers.get((r.cursor+i)%r.consumers.size());
            int share=Math.max(1,available/(limit-i));
            available-=m.power.receiveEnergy(share,false);
        }
        r.cursor=(r.cursor+limit)%r.consumers.size();
    }
    private PowerGrid() {}
}
