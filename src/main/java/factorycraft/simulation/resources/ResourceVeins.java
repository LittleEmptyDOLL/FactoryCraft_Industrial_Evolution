package factorycraft.simulation.resources;

import java.util.*;

/** Deterministic surface fields. Sampling has no chunk/world state, so chunk order cannot cut seams. */
public final class ResourceVeins {
    public record Settings(int minRadius,int maxRadius,int minStock,int maxStock,int spacingChunks) {
        public Settings {
            if(minRadius<4 || maxRadius>48 || minRadius>maxRadius || minStock<1 || maxStock>10_000_000 || minStock>maxStock || spacingChunks<2 || spacingChunks>32)
                throw new IllegalArgumentException("Invalid resource vein radius/stock/spacing range");
        }
    }
    public record Patch(int x,int z,int radius,long seed,Settings settings) {
        public int stock(int worldX,int worldZ) {
            double dx=worldX-x,dz=worldZ-z,angle=StrictMath.atan2(dz,dx),phase=unit(seed)*Math.PI*2;
            double edge=radius*(.88+.08*StrictMath.sin(angle*3+phase)+.04*StrictMath.sin(angle*5-phase));
            double distance=StrictMath.sqrt(dx*dx+dz*dz)/edge;
            if(distance>1)return 0;
            double richness=(1-distance)*(.7+.3*unit(mix(seed^((long)worldX<<32)^(worldZ&0xffffffffL))));
            return settings.minStock+(int)Math.round((settings.maxStock-settings.minStock)*richness);
        }
    }
    public static Patch patch(long worldSeed,long salt,int gridX,int gridZ,Settings settings) {
        long seed=mix(worldSeed^salt^mix(((long)gridX<<32)^(gridZ&0xffffffffL)));int spacing=settings.spacingChunks*16;
        int x=gridX*spacing+(int)Math.floorMod(seed,spacing),z=gridZ*spacing+(int)Math.floorMod(mix(seed),spacing);
        int radius=settings.minRadius+(int)Math.floorMod(mix(seed+1),(long)settings.maxRadius-settings.minRadius+1);
        return new Patch(x,z,radius,seed,settings);
    }
    public static List<Patch> inChunk(long worldSeed,long salt,int chunkX,int chunkZ,Settings settings) {
        int x=chunkX*16,z=chunkZ*16,r=settings.maxRadius,spacing=settings.spacingChunks*16;var patches=new ArrayList<Patch>();
        for(int gx=Math.floorDiv(x-r,spacing);gx<=Math.floorDiv(x+15+r,spacing);gx++)
            for(int gz=Math.floorDiv(z-r,spacing);gz<=Math.floorDiv(z+15+r,spacing);gz++) {
                var patch=patch(worldSeed,salt,gx,gz,settings);
                if(patch.x+patch.radius>=x && patch.x-patch.radius<=x+15 && patch.z+patch.radius>=z && patch.z-patch.radius<=z+15)patches.add(patch);
            }
        return patches;
    }
    private static double unit(long bits) { return (bits>>>11)*0x1.0p-53; }
    private static long mix(long value) { value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;return value^(value>>>31); }
    private ResourceVeins() {}
}
