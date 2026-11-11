package factorycraft.simulation.resources;

import java.util.*;

/** Pure coordinate-based surface point distribution; point spacing prevents pumpjack overlap. */
public final class OilFields {
  public record Point(int x, int z, int yieldHundredths) {}

  public static List<Point> inChunk(
      long seed,
      int cx,
      int cz,
      int spacingChunks,
      int radius,
      int minPoints,
      int maxPoints,
      int minYield,
      int maxYield) {
    int span = spacingChunks * 16;
    var result = new ArrayList<Point>();
    for (int gx = Math.floorDiv(cx * 16 - radius, span);
        gx <= Math.floorDiv(cx * 16 + 15 + radius, span);
        gx++)
      for (int gz = Math.floorDiv(cz * 16 - radius, span);
          gz <= Math.floorDiv(cz * 16 + 15 + radius, span);
          gz++) {
        var r =
            new SplittableRandom(seed ^ 0x64f17a3e2L ^ mix(((long) gx << 32) ^ (gz & 0xffffffffL)));
        int centerX = gx * span + r.nextInt(span),
            centerZ = gz * span + r.nextInt(span),
            count = r.nextInt(Math.min(minPoints, maxPoints), Math.max(minPoints, maxPoints) + 1),
            side = radius / 6;
        count = Math.min(count, (2 * side + 1) * (2 * side + 1));
        var used = new HashSet<Long>();
        for (int i = 0; i < count; i++) {
          int dx, dz;
          long key;
          do {
            dx = r.nextInt(-side, side + 1);
            dz = r.nextInt(-side, side + 1);
            key = ((long) dx << 32) ^ (dz & 0xffffffffL);
          } while (!used.add(key));
          int x = centerX + dx * 6,
              z = centerZ + dz * 6,
              yield = r.nextInt(Math.min(minYield, maxYield), Math.max(minYield, maxYield) + 1);
          if ((x >> 4) == cx && (z >> 4) == cz) result.add(new Point(x, z, yield));
        }
      }
    return result;
  }

  private static long mix(long x) {
    x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
    x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
    return x ^ (x >>> 31);
  }

  private OilFields() {}
}
