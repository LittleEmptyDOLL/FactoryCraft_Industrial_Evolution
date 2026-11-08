package factorycraft.simulation;

import factorycraft.simulation.resources.ResourceVeins;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceVeinsTest {
    private static final ResourceVeins.Settings SETTINGS=new ResourceVeins.Settings(8,20,500,5000,6);
    @Test void seedAndSaltAreDeterministicAndResourcesHaveDistinctFields() {
        var a=ResourceVeins.patch(123,99,-2,7,SETTINGS);assertEquals(a,ResourceVeins.patch(123,99,-2,7,SETTINGS));
        assertNotEquals(a,ResourceVeins.patch(123,100,-2,7,SETTINGS));assertNotEquals(a,ResourceVeins.patch(124,99,-2,7,SETTINGS));
    }
    @Test void largeFieldsHaveConnectedTilesBoundedStocksAndRicherCenters() {
        for(int seed=0;seed<50;seed++) {
            var patch=ResourceVeins.patch(seed,42,0,0,SETTINGS);var tiles=new HashSet<Long>();
            for(int x=-patch.radius();x<=patch.radius();x++)for(int z=-patch.radius();z<=patch.radius();z++) {
                int units=patch.stock(patch.x()+x,patch.z()+z);if(units==0)continue;
                assertTrue(units>=500 && units<=5000);tiles.add(((long)x<<32)^(z&0xffffffffL));
            }
            assertTrue(tiles.size()>80);assertEquals(0,patch.stock(patch.x()+patch.radius()+1,patch.z()));
            var queue=new ArrayDeque<Long>();queue.add(0L);var visited=new HashSet<Long>();
            while(!queue.isEmpty()) {long key=queue.removeFirst();if(!tiles.contains(key) || !visited.add(key))continue;int x=(int)(key>>32),z=(int)key;for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})queue.add(((long)(x+d[0])<<32)^((z+d[1])&0xffffffffL));}
            assertEquals(tiles,visited);assertTrue(patch.stock(patch.x(),patch.z())>500);
        }
    }
    @Test void chunkSamplingCoversTheSameFieldWithNoMissingSeamsInEitherOrder() {
        var settings=new ResourceVeins.Settings(20,20,17,17,4);var patch=ResourceVeins.patch(7,15,-1,0,settings);var all=new HashMap<String,Integer>();
        for(int x=patch.x()-20;x<=patch.x()+20;x++)for(int z=patch.z()-20;z<=patch.z()+20;z++) {
            int stock=patch.stock(x,z);if(stock==0)continue;var candidates=ResourceVeins.inChunk(7,15,Math.floorDiv(x,16),Math.floorDiv(z,16),settings);
            assertTrue(candidates.contains(patch));assertEquals(17,stock);all.put(x+":"+z,stock);
        }
        var chunks=new ArrayList<String>(all.keySet());Collections.reverse(chunks);
        for(var key:chunks) {var p=key.split(":");int x=Integer.parseInt(p[0]),z=Integer.parseInt(p[1]);assertEquals(all.get(key),patch.stock(x,z));}
    }
    @Test void rangeAndSpacingSettingsRejectInvalidOrUnboundedWork() {
        assertThrows(IllegalArgumentException.class,()->new ResourceVeins.Settings(20,8,1,2,6));assertThrows(IllegalArgumentException.class,()->new ResourceVeins.Settings(4,49,1,2,6));
        assertThrows(IllegalArgumentException.class,()->new ResourceVeins.Settings(4,8,5,4,6));assertThrows(IllegalArgumentException.class,()->new ResourceVeins.Settings(4,8,0,4,6));
        assertThrows(IllegalArgumentException.class,()->new ResourceVeins.Settings(4,8,1,4,1));
    }
}
