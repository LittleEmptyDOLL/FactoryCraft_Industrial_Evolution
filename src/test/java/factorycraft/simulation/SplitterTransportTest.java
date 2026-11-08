package factorycraft.simulation;

import factorycraft.simulation.belts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SplitterTransportTest {
    @Test void fourLaneSplitterSustainsTwoFullBeltsAndConservesUniqueItems() {
        for(int phase=0;phase<24;phase++)for(int order=0;order<4;order++) {
        var transport=new BeltTransport<Integer>();var inputs=new ArrayList<BeltTransport.Node<Integer>>();var split=new ArrayList<BeltTransport.Node<Integer>>();var outputs=new ArrayList<BeltTransport.Node<Integer>>();var seen=new HashSet<Integer>();int[] delivered={0,0};
        for(int i=0;i<4;i++) {inputs.add(transport.create(new BeltLane<>(),1,c->{}));split.add(transport.create(new BeltLane<>(),1,c->{}));outputs.add(transport.create(new BeltLane<>(),1,c->{}));final int track=i/2;transport.connect(outputs.get(i),transport.createSink(value->{assertTrue(seen.add(value));delivered[track]++;}),null);}
        for(int i=0;i<4;i++) {transport.connect(inputs.get(i),split.get(i),null);transport.connect(split.get(i),outputs.get(i%2),outputs.get(2+i%2));}
        int created=0;
        for(int step=0;step<2000;step++){for(int k=0;k<inputs.size();k++){int i=(k+order)%4;if((i<2 || step>=phase) && transport.insert(inputs.get(i),created))created++;}transport.step();}
        int before=seen.size(),a=delivered[0],b=delivered[1];
        for(int step=0;step<10800;step++) {for(var n:inputs)if(transport.insert(n,created))created++;transport.step();assertEquals(created,seen.size()+java.util.stream.Stream.of(inputs,split,outputs).flatMap(List::stream).mapToInt(n->n.lane().carriers().size()).sum());}
        assertEquals(1800,seen.size()-before,"phase="+phase+", activation order="+order);assertEquals(900,delivered[0]-a);assertEquals(900,delivered[1]-b);
        }
    }
    @Test void packedFiniteInputStartsANewBalancedDecisionForEachCarrier() {
        for(int tier=1;tier<=3;tier++) {
            var t=new BeltTransport<Integer>();var source=t.create(new BeltLane<>(),tier,c->{});var a=t.create(new BeltLane<>(),tier,c->{});var b=t.create(new BeltLane<>(),tier,c->{});
            t.connect(source,a,b);for(int i=0;i<4;i++)source.lane().restore(i,1-i*.25);t.changedExternally(source);
            for(int tick=0;tick<100;tick++)t.tick();
            assertTrue(source.lane().carriers().isEmpty());assertEquals(2,a.lane().carriers().size());assertEquals(2,b.lane().carriers().size());
            var ids=new HashSet<Integer>();for(var lane:List.of(a,b))for(var c:lane.lane().carriers())assertTrue(ids.add(c.value));assertEquals(4,ids.size());
        }
    }
    @Test void unavailableTrackFreezesExactlyAndBlocksCachedInjectionUntilResume() {
        var t=new BeltTransport<Integer>();var lane=new BeltLane<Integer>();var n=t.create(lane,1,c->{});int[] delivered={0};t.connect(n,t.createSink(v->delivered[0]++),null);assertTrue(t.insert(n,17));
        for(int i=0;i<12;i++)t.step();int before=lane.carriers().getFirst().positionUnits();t.setAvailable(n,false);
        for(int i=0;i<100;i++)t.tick();assertEquals(before,lane.carriers().getFirst().positionUnits());assertFalse(t.insert(n,18));assertEquals(0,delivered[0]);
        t.setAvailable(n,true);for(int i=0;i<20;i++)t.tick();assertEquals(1,delivered[0]);assertTrue(lane.carriers().isEmpty());
    }
}
