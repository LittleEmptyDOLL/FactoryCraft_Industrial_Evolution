package factorycraft.rail;

import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Shared immutable geometry and voxel inspection; claims and inventory are additionally
 * authoritative.
 */
public final class RailPlan {
  public final RailCurve curve;
  public final RailFootprint footprint;

  public RailPlan(RailCurve curve) {
    this.curve = curve;
    footprint = new RailFootprint(curve);
  }

  private static boolean touches(
      RailFootprint hulls, RailFootprint.Cell cell, java.util.List<RailFootprint.Box> boxes) {
    for (var box : boxes) if (hulls.intersects(cell, box)) return true;
    return false;
  }

  /** ClientLevel.hasChunk always returns true in1.21.1; query its real cache without creation. */
  public static boolean loaded(Level level, BlockPos pos) {
    return level.isClientSide
        ? level
                .getChunkSource()
                .getChunk(
                    pos.getX() >> 4,
                    pos.getZ() >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                    false)
            != null
        : level.hasChunkAt(pos);
  }

  public String inspect(Level level, Player player) {
    if (!curve.validated()) return "geometry";
    for (var cell : footprint.obstacles) {
      var p = RailwayData.pos(cell);
      if (p.getY() < level.getMinBuildHeight()
          || p.getY() >= level.getMaxBuildHeight()
          || !level.getWorldBorder().isWithinBounds(p)) return "border";
      if (!loaded(level, p)) return "unloaded";
      var state = level.getBlockState(p);
      if (footprint.bed.containsKey(cell)) {
        if (!state.is(RailContent.BED.get()) && !RailObstacles.replaceable(level, p, state, player))
          return "blocked";
      } else if (!RailObstacles.clear(level, p, state, footprint)) return "blocked";
    }
    for (var end : List.of(curve.start, curve.end)) {
      var base = new BlockPos(end.x(), end.y() - 1, end.z());
      if (!loaded(level, base)) return "unloaded";
      if (!level.getBlockState(base).isFaceSturdy(level, base, Direction.UP)
          && !level.getBlockState(base.above()).is(RailContent.BED.get())) return "support";
    }
    for (var entry : footprint.bed.entrySet()) {
      var p = RailwayData.pos(entry.getKey());
      for (var entity :
          level.getEntities(
              (net.minecraft.world.entity.Entity) null, new AABB(p), e -> !e.isSpectator()))
        for (var box : entry.getValue())
          if (entity instanceof factorycraft.trains.StockEntity stock
              ? factorycraft.trains.TrainCollision.intersects(
                  stock.hull(),
                  new AABB(
                      p.getX() + box.x0(),
                      p.getY() + box.y0(),
                      p.getZ() + box.z0(),
                      p.getX() + box.x1(),
                      p.getY() + box.y1(),
                      p.getZ() + box.z1()))
              : entity
                  .getBoundingBox()
                  .intersects(
                      new AABB(
                          p.getX() + box.x0(),
                          p.getY() + box.y0(),
                          p.getZ() + box.z0(),
                          p.getX() + box.x1(),
                          p.getY() + box.y1(),
                          p.getZ() + box.z1()))) return "entity";
    }
    var routes = RailViews.routes(level, footprint);
    var local = new RailGraph();
    for (var old : routes) local.add(new RailGraph.Edge(old.id(), old.curve()));
    var approaches = local.approaches(curve, 8);
    for (var old : routes) {
      boolean approach = approaches.contains(old.id());
      if (RailGraph.conflicts(curve, old.curve(), approach)) return "crossing";
      if (!RailGraph.joined(curve, old.curve())) {
        for (var c : old.footprint().bed.keySet())
          if (touches(footprint, c, old.footprint().bed.get(c))
              && !(approach
                  && RailGraph.nearEnd(
                      curve, new RailVector(c.x() + .5, c.y() + .5, c.z() + .5), 8)))
            return "crossing";
        for (var c : footprint.bed.keySet())
          if (touches(old.footprint(), c, footprint.bed.get(c))
              && !(approach
                  && RailGraph.nearEnd(
                      curve, new RailVector(c.x() + .5, c.y() + .5, c.z() + .5), 8)))
            return "crossing";
      }
    }
    for (var c : footprint.bed.keySet())
      if (level.getBlockEntity(RailwayData.pos(c)) instanceof RailCellBlockEntity be
          && be.segments().size() >= 8) return "junction";
    return "ready";
  }
}
