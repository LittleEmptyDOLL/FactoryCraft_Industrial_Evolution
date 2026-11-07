package factorycraft.simulation;

import factorycraft.simulation.belts.BeltLane;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BeltLaneTest {
    @Test void movesAndBackpressuresWithoutOverlap() {
        var lane=new BeltLane<Integer>();
        for(int i=0;i<4;i++)lane.restore(i,1-i*.25);
        assertFalse(lane.insert(9,0));
        for(int tick=0;tick<100;tick++)lane.advance(.09375,tick,v->false);
        assertEquals(4,lane.carriers().size());
        for(int i=1;i<4;i++)assertTrue(lane.carriers().get(i-1).position-lane.carriers().get(i).position>=.25-1e-8);
    }
    @Test void crossSegmentArrivalAdvancesOnlyOnceRegardlessOfTickOrder() {
        var source=new BeltLane<Integer>();var dest=new BeltLane<Integer>();
        source.restore(42,1);
        source.advance(.09375,12,v->dest.insert(v,12));
        dest.advance(.09375,12,v->false);
        assertEquals(0,dest.carriers().getFirst().position);
        dest.advance(.09375,13,v->false);
        assertEquals(.09375,dest.carriers().getFirst().position);
        assertTrue(source.carriers().isEmpty());
    }
    @Test void randomizedCongestionConservesUniqueItems() {
        List<BeltLane<Integer>> lanes=new ArrayList<>();for(int i=0;i<32;i++)lanes.add(new BeltLane<>());
        Random random=new Random(9124);Set<Integer> delivered=new HashSet<>();int created=0;
        for(int tick=0;tick<10000;tick++) {
            if(lanes.getFirst().insert(created,tick))created++;
            final int now=tick;
            for(int i=lanes.size()-1;i>=0;i--) {
                final int index=i;
                lanes.get(i).advance(.09375*(i%3+1),tick,value->{
                    if(index+1<lanes.size())return lanes.get(index+1).insert(value,now);
                    if(random.nextInt(3)==0)return false;
                    assertTrue(delivered.add(value),"duplicate item "+value);return true;
                });
            }
            Set<Integer> all=new HashSet<>(delivered);
            for(var lane:lanes)for(var c:lane.carriers())assertTrue(all.add(c.value));
            assertEquals(created,all.size());
        }
    }
    @Test void invalidPersistenceIsRejected() {
        var lane=new BeltLane<String>();
        assertThrows(IllegalArgumentException.class,()->lane.restore("bad",Double.NaN));
        lane.restore("first",.75);
        assertThrows(IllegalArgumentException.class,()->lane.restore("overlap",.7));
    }
    @Test void restoredPositionsResumeExactly() {
        var original=new BeltLane<Integer>();original.restore(1,.8);original.restore(2,.3);
        var restored=new BeltLane<Integer>();for(var c:original.carriers())restored.restore(c.value,c.position);
        for(int tick=0;tick<20;tick++) {
            original.advance(.09375,tick,v->false);restored.advance(.09375,tick,v->false);
            for(int i=0;i<2;i++)assertEquals(original.carriers().get(i).position,restored.carriers().get(i).position);
        }
    }
    @Test void tenThousandSegmentsRemainConservativeUnderLoad() {
        int count=10000;List<BeltLane<Integer>> lanes=new ArrayList<>(count);
        for(int i=0;i<count;i++){var lane=new BeltLane<Integer>();lane.restore(i,1);lanes.add(lane);}
        long start=System.nanoTime();
        for(int tick=0;tick<200;tick++)for(int i=count-1;i>=0;i--){final int next=(i+1)%count;final int now=tick;lanes.get(i).advance(.09375,now,v->lanes.get(next).insert(v,now));}
        Set<Integer> ids=new HashSet<>();for(var lane:lanes)for(var c:lane.carriers())assertTrue(ids.add(c.value));
        assertEquals(count,ids.size());
        System.out.printf(Locale.ROOT,"Belt kernel: 10000 lanes, 200 ticks, %.2f ms; conservation verified%n",(System.nanoTime()-start)/1e6);
    }
}
