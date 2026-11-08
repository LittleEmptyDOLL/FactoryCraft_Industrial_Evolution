package factorycraft.belts;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;

/** Cached physical body fragments; geometry contains no inventories or world lookups. */
public final class TransportShapes {
  private static final Map<BeltModelData.Cell, VoxelShape> CACHE =
      new java.util.concurrent.ConcurrentHashMap<>();

  public static VoxelShape shape(BeltModelData.Cell cell) {
    return CACHE.computeIfAbsent(cell, TransportShapes::build);
  }

  private static VoxelShape build(BeltModelData.Cell cell) {
    var r = cell.route();
    if (r.shape == BeltRoute.Shape.LIFT) return Shapes.box(.05, 0, .05, .95, 1, .95);
    if (r.shape == BeltRoute.Shape.UNDERGROUND || cell.output())
      return Shapes.box(0, 0, 0, 1, .6, 1);
    if (r.heightDelta() == 0) return Shapes.box(0, 0, 0, 1, .1875, 1);
    VoxelShape result = Shapes.empty();
    int samples = 24;
    for (int i = 0; i < samples; i++) {
      var a = r.point(0, r.length(0) * (double) i / samples);
      var b = r.point(0, r.length(0) * (double) (i + 1) / samples);
      double lo = Math.max(0, Math.min(a.y(), b.y()) - cell.offsetY() - .1875),
          hi = Math.min(1, Math.max(a.y(), b.y()) - cell.offsetY());
      if (hi <= lo) continue;
      var p = rotate(0, Math.min(a.z(), b.z()), cell.facing());
      var q = rotate(1, Math.max(a.z(), b.z()), cell.facing());
      result =
          Shapes.or(
              result,
              Shapes.box(
                  Math.min(p[0], q[0]),
                  lo,
                  Math.min(p[1], q[1]),
                  Math.max(p[0], q[0]),
                  hi,
                  Math.max(p[1], q[1])));
    }
    return result.optimize();
  }

  public static double[] rotate(double x, double z, Direction d) {
    return switch (d) {
      case EAST -> new double[] {1 - z, x};
      case SOUTH -> new double[] {1 - x, 1 - z};
      case WEST -> new double[] {z, 1 - x};
      default -> new double[] {x, z};
    };
  }

  private TransportShapes() {}
}
