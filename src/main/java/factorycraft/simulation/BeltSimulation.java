package factorycraft.simulation;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.BeltBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Only moving lanes are visited. Jammed lanes sleep until downstream space or topology changes. */
@EventBusSubscriber(modid=FactoryCraft.ID)
public final class BeltSimulation {
    private static final Map<Level,State> STATES=new IdentityHashMap<>();
    private static final class State {
        final ArrayList<BeltBlockEntity> active=new ArrayList<>(), pending=new ArrayList<>();
        int loaded;
        long updates,nanos;
    }
    public static void register(BeltBlockEntity be){
        var s=STATES.computeIfAbsent(be.getLevel(),l->new State());s.loaded++;
        activate(be);wakeInputs(be);
    }
    public static void unregister(BeltBlockEntity be){
        var s=STATES.get(be.getLevel());if(s!=null)s.loaded--;
        be.simulationLoaded=false;
        wakeInputs(be);
    }
    public static void activate(BeltBlockEntity be){
        if(be.getLevel()==null || be.getLevel().isClientSide || !be.simulationLoaded || be.scheduled || !be.hasItems())return;
        be.scheduled=true;STATES.computeIfAbsent(be.getLevel(),l->new State()).pending.add(be);
    }
    public static void wakeInputs(BeltBlockEntity be){
        var level=be.getLevel();if(level==null || level.isClientSide)return;
        for(var d:Direction.Plane.HORIZONTAL){
            var p=be.getBlockPos().relative(d);
            if(level.hasChunkAt(p) && level.getBlockEntity(p) instanceof BeltBlockEntity neighbor)activate(neighbor);
        }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post e){
        if(e.getLevel().isClientSide)return;
        var s=STATES.get(e.getLevel());if(s==null)return;
        s.active.addAll(s.pending);s.pending.clear();
        long start=System.nanoTime();
        for(int i=0;i<s.active.size();){
            var be=s.active.get(i);
            boolean keep=be.simulationLoaded && !be.isRemoved() && be.hasItems() && be.tickMovement();
            s.updates++;
            if(keep){i++;continue;}
            be.scheduled=false;
            int last=s.active.size()-1;s.active.set(i,s.active.get(last));s.active.remove(last);
        }
        s.nanos+=System.nanoTime()-start;
    }
    public static String statistics(Level level){
        var s=STATES.get(level);return s==null ? "Belts: 0" : "Belts: "+s.loaded+", active: "+(s.active.size()+s.pending.size())+", updates: "+s.updates+", CPU: "+String.format(java.util.Locale.ROOT,"%.3f ms",s.nanos/1e6);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e){STATES.remove(e.getLevel());}
    private BeltSimulation(){}
}
