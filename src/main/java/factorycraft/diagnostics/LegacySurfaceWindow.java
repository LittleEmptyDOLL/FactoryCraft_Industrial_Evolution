package factorycraft.diagnostics;

import java.util.function.IntBinaryOperator;

/**
 * Exact dense box filtering with a summed-area table. Query cost is constant and chunk-order
 * independent.
 */
public final class LegacySurfaceWindow {
  private final int x, z, width, depth, radius, stride;
  private final long[] sums;

  public LegacySurfaceWindow(
      int x, int z, int width, int depth, int radius, IntBinaryOperator heights) {
    if (width < 1 || depth < 1 || radius < 1 || radius > 8)
      throw new IllegalArgumentException("Invalid terrain window");
    this.x = x;
    this.z = z;
    this.width = width;
    this.depth = depth;
    this.radius = radius;
    stride = width + 2 * radius + 1;
    sums = new long[stride * (depth + 2 * radius + 1)];
    for (int iz = 1; iz <= depth + 2 * radius; iz++)
      for (int ix = 1; ix < stride; ix++) {
        int at = iz * stride + ix;
        sums[at] =
            heights.applyAsInt(x - radius + ix - 1, z - radius + iz - 1)
                + sums[at - 1]
                + sums[at - stride]
                - sums[at - stride - 1];
      }
  }

  public int height(int worldX, int worldZ) {
    int dx = worldX - x, dz = worldZ - z;
    if (dx < 0 || dx >= width || dz < 0 || dz >= depth)
      throw new IllegalArgumentException("Outside terrain window");
    int side = radius * 2 + 1,
        a = dz * stride + dx,
        b = a + side,
        c = (dz + side) * stride + dx,
        d = c + side;
    return Math.toIntExact(
        Math.floorDiv(sums[d] - sums[b] - sums[c] + sums[a], (long) side * side));
  }
}
