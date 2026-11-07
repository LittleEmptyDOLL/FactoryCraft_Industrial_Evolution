package factorycraft.simulation.multiblock;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CuboidLayoutTest {
    @Test void twoByTwoHasSevenUniquePartsInEveryOrientation() {
        var layout=new CuboidLayout(2,2,2);
        for(int[] facing:new int[][]{{0,-1},{1,0},{0,1},{-1,0}}) {
            var parts=layout.parts(facing[0],facing[1]);assertEquals(7,parts.size());assertEquals(7,new HashSet<>(parts).size());
            assertFalse(parts.contains(new CuboidLayout.Offset(0,0,0)));
            for(var p:parts){assertTrue(Math.abs(p.x())<=1 && Math.abs(p.z())<=1);assertTrue(p.y()==0 || p.y()==1);}
        }
    }
    @Test void nonSquareLayoutsRotateAroundTheControllerWithoutChangingVolume() {
        var layout=new CuboidLayout(3,2,4);var north=layout.parts(0,-1);var east=new HashSet<>(layout.parts(1,0));assertEquals(23,north.size());
        for(var p:north)assertTrue(east.contains(new CuboidLayout.Offset(-p.z(),p.y(),p.x())));
    }
    @Test void dimensionsAndNonCardinalDirectionsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new CuboidLayout(0,2,2));assertThrows(IllegalArgumentException.class,()->new CuboidLayout(17,2,2));
        assertThrows(IllegalArgumentException.class,()->new CuboidLayout(2,2,2).parts(1,1));
    }
}
