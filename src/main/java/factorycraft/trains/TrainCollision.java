package factorycraft.trains;

import factorycraft.simulation.rail.RailVector;
import factorycraft.simulation.trains.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Native adapters: AABBs discover; one shared scalar oriented hull decides. */
public final class TrainCollision {
  public static boolean intersects(StockEnvelope hull, VoxelShape shape, BlockPos at) {
    for (var b : shape.toAabbs())
      if (hull.intersects(
          b.minX + at.getX(),
          b.minY + at.getY(),
          b.minZ + at.getZ(),
          b.maxX + at.getX(),
          b.maxY + at.getY(),
          b.maxZ + at.getZ())) return true;
    return false;
  }

  public static boolean intersects(StockEnvelope hull, AABB b) {
    return hull.intersects(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
  }

  public static Optional<Vec3> clip(StockEntity stock, Vec3 from, Vec3 to) {
    double t =
        stock
            .hull()
            .rayFraction(new RailVector(from.x, from.y, from.z), new RailVector(to.x, to.y, to.z));
    return Double.isNaN(t) ? Optional.empty() : Optional.of(from.lerp(to, t));
  }

  private static boolean eligible(Entity mover, StockEntity stock) {
    return stock != mover
        && !stock.isRemoved()
        && (stock.level().isClientSide || TrainPorts.physicalView(stock))
        && !(mover != null
            && mover.getVehicle() instanceof StockEntity ridden
            && ridden.trainId().equals(stock.trainId()));
  }

  public static boolean occupied(Level level, Entity mover, AABB box) {
    for (var stock : level.getEntitiesOfClass(StockEntity.class, box))
      if (eligible(mover, stock) && intersects(stock.hull(), box)) return true;
    return false;
  }

  /** Used after vanilla block/entity collision resolution, preserving its axis sliding order. */
  public static Vec3 clipMotion(Entity mover, Vec3 permitted) {
    if (mover instanceof StockEntity || permitted.lengthSqr() < 1e-14) return permitted;
    var box = mover.getBoundingBox();
    var candidates =
        mover
            .level()
            .getEntitiesOfClass(
                StockEntity.class,
                box.expandTowards(permitted).inflate(.01),
                s -> eligible(mover, s));
    if (candidates.isEmpty()) return permitted;
    double y = axis(candidates, box, permitted.y, 1);
    box = box.move(0, y, 0);
    double x, z;
    if (Math.abs(permitted.x) < Math.abs(permitted.z)) {
      z = axis(candidates, box, permitted.z, 2);
      box = box.move(0, 0, z);
      x = axis(candidates, box, permitted.x, 0);
    } else {
      x = axis(candidates, box, permitted.x, 0);
      box = box.move(x, 0, 0);
      z = axis(candidates, box, permitted.z, 2);
    }
    return new Vec3(x, y, z);
  }

  private static double axis(List<StockEntity> cars, AABB box, double delta, int axis) {
    if (Math.abs(delta) < 1e-12) return delta;
    double fraction = 1;
    for (var car : cars) {
      var h = car.hull();
      if (intersects(h, box)) continue; // Escape an externally-created overlap; never trap a rider.
      double t =
          h.sweepFraction(
              box.minX,
              box.minY,
              box.minZ,
              box.maxX,
              box.maxY,
              box.maxZ,
              axis == 0 ? delta : 0,
              axis == 1 ? delta : 0,
              axis == 2 ? delta : 0);
      if (!Double.isNaN(t)) fraction = Math.min(fraction, Math.max(0, t - 1e-7 / Math.abs(delta)));
    }
    return delta * fraction;
  }

  private TrainCollision() {}
}
