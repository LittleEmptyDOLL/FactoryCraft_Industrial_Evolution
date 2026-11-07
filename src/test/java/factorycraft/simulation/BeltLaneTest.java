package factorycraft.simulation;

import factorycraft.simulation.belts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BeltLaneTest {
    @Test void movesAndBackpressuresWithoutOverlap() {
        var transport=new BeltTransport<Integer>();var lane=new BeltLane<Integer>();
        var node=transport.create(lane,1,c->{});
        for(int i=0;i<4;i++)lane.restore(i,1-i*.25);
        transport.changedExternally(node);
        for(int tick=0;tick<100;tick++)transport.tick();
        assertEquals(4,lane.carriers().size());assertFalse(transport.canInsert(node));assertFalse(node.isActive());
        assertSpacing(lane);
    }
    @Test void carriesResidualDistanceAcrossTheBoundaryExactlyOnce() {
        var transport=new BeltTransport<Integer>();var a=new BeltLane<Integer>();var b=new BeltLane<Integer>();
        var source=transport.create(a,3,c->{});var target=transport.create(b,3,c->{});transport.connect(source,target,null);
        a.restore(42,95.0/96);var original=a.carriers().getFirst();transport.changedExternally(source);transport.step();
        assertTrue(a.carriers().isEmpty());assertSame(original,b.carriers().getFirst());assertEquals(2,b.carriers().getFirst().positionUnits());
        transport.step();assertEquals(5,b.carriers().getFirst().positionUnits());
    }
    @Test void invalidPersistenceIsRejected() {
        var lane=new BeltLane<String>();
        assertThrows(IllegalArgumentException.class,()->lane.restore("bad",Double.NaN));
        lane.restore("first",.75);
        assertThrows(IllegalArgumentException.class,()->lane.restore("overlap",.7));
    }
    @Test void legacyCoordinatesOnThirtySecondGridAreRestoredExactly() {
        var lane=new BeltLane<Integer>();lane.restore(1,.9375);lane.restore(2,.65625);
        assertEquals(.9375,lane.carriers().get(0).position());assertEquals(.65625,lane.carriers().get(1).position());
        var copy=new BeltLane<Integer>();for(var c:lane.carriers())copy.restore(c.value,c.position());
        assertEquals(lane.carriers().get(1).positionUnits(),copy.carriers().get(1).positionUnits());
    }
    static void assertSpacing(BeltLane<?> lane) {
        var cs=lane.carriers();assertTrue(cs.size()<=4);
        for(int i=0;i<cs.size();i++) {
            assertTrue(cs.get(i).positionUnits()>=0 && cs.get(i).positionUnits()<=96);
            if(i>0)assertTrue(cs.get(i-1).positionUnits()-cs.get(i).positionUnits()>=24);
        }
    }
}
