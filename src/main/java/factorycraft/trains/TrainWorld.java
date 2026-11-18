package factorycraft.trains;

import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;

/** Loaded-only collision/projection adapter. Uses the foundation's conservative3×7×4 envelope. */
public final class TrainWorld {
  public static Vec3 vec(RailVector p) {
    return new Vec3(p.x(), p.y(), p.z());
  }

  public static AABB box(BogiePose p) {
    var f = p.forward();
    var s = p.side();
    var n = p.normal();
    var c = p.center();
    double x = Math.abs(f.x()) * 3.5 + Math.abs(s.x()) * 1.5 + Math.abs(n.x()) * 2,
        y = Math.abs(f.y()) * 3.5 + Math.abs(n.y()) * 2,
        z = Math.abs(f.z()) * 3.5 + Math.abs(s.z()) * 1.5 + Math.abs(n.z()) * 2;
    var center = c.add(n.scale(2.02));
    return new AABB(
        center.x() - x,
        center.y() - y,
        center.z() - z,
        center.x() + x,
        center.y() + y,
        center.z() + z);
  }

  public static boolean available(TrainData data, TrainData.Train t) {
    if (t.checkedTick == data.level.getGameTime() && t.checkedEpoch == data.availabilityEpoch)
      return t.checkedAvailable;
    t.checkedTick = data.level.getGameTime();
    t.checkedEpoch = data.availabilityEpoch;
    return t.checkedAvailable = checkAvailable(data, t);
  }

  private static boolean checkAvailable(TrainData data, TrainData.Train t) {
    var rails = RailwayData.get(data.level);
    for (var leg : t.path.legs())
      if (leg.end() >= t.rear() && leg.begin() <= t.front())
        if (!rails.available(leg.edge().edge())) return false;
    try {
      for (int i = 0; i < t.cars.size(); i++) {
        var b = box(BogiePose.sample(t.path, rails.graph, t.center(i), t.cars.get(i).reversed));
        for (int x = ((int) Math.floor(b.minX)) >> 4; x <= ((int) Math.floor(b.maxX)) >> 4; x++)
          for (int z = ((int) Math.floor(b.minZ)) >> 4; z <= ((int) Math.floor(b.maxZ)) >> 4; z++) {
            long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
            if (data.level.getChunkSource().getChunkNow(x, z) == null
                || !data.level.shouldTickBlocksAt(key)) return false;
          }
      }
    } catch (IllegalArgumentException bad) {
      return false;
    }
    return true;
  }

  public static List<SpatialReservations.Bounds> sweep(
      TrainData data, TrainData.Train t, double from, double to) {
    var graph = RailwayData.get(data.level).graph;
    var out = new ArrayList<SpatialReservations.Bounds>();
    boolean straight = true;
    double min = Math.min(from, to) - (t.cars.size() - 1) * 7 - 3.5, max = Math.max(from, to) + 3.5;
    for (var leg : t.path.legs())
      if (leg.end() >= min && leg.begin() <= max) {
        var edge = graph.edge(leg.edge().edge());
        if (edge == null) throw new IllegalArgumentException("Unresolved sweep track");
        if (!Double.isInfinite(edge.curve().minimumRadius()) || edge.curve().maximumGrade() > 1e-10)
          straight = false;
      }
    int samples = straight ? 1 : Math.max(1, (int) Math.ceil(Math.abs(to - from) * 4));
    for (int i = 0; i < t.cars.size(); i++) {
      var a = box(BogiePose.sample(t.path, graph, from - i * 7, t.cars.get(i).reversed));
      var u = a;
      for (int sample = 1; sample <= samples; sample++) {
        var b =
            box(
                BogiePose.sample(
                    t.path,
                    graph,
                    from + (to - from) * sample / samples - i * 7,
                    t.cars.get(i).reversed));
        u = u.minmax(b);
      }
      // Curved/graded movement reserves intermediate orientations and a conservative
      // quarter-block margin; flat straight motion has an exact endpoint-union fast path.
      if (!straight && from != to) u = u.inflate(.25);
      out.add(new SpatialReservations.Bounds(u.minX, u.minY, u.minZ, u.maxX, u.maxY, u.maxZ));
    }
    return out;
  }

  public static String routeStatus(TrainData data, TrainData.Train t, double min, double max) {
    var rails = RailwayData.get(data.level);
    for (var leg : t.path.legs())
      if (leg.end() >= min && leg.begin() <= max) {
        if (rails.graph.edge(leg.edge().edge()) == null) return "track";
        if (!rails.available(leg.edge().edge())) return "unloaded";
      }
    return "ready";
  }

  public static String inspect(
      TrainData data, TrainData.Train t, double from, double to, UUID ignore) {
    return contact(data, t, from, to, ignore).reason();
  }

  public record Contact(String reason, int car, Entity entity, BlockPos block, double fraction) {}

  private static final Contact READY = new Contact("ready", -1, null, null, 1);

  /** Earliest swept contact; callers must commit ownership before invoking damage callbacks. */
  public static Contact contact(
      TrainData data, TrainData.Train t, double from, double to, UUID ignore) {
    var rails = RailwayData.get(data.level);
    for (var leg : t.path.legs())
      if (leg.end() >= Math.min(from, to) - (t.cars.size() - 1) * 7 - 3.5
          && leg.begin() <= Math.max(from, to) + 3.5) {
        if (rails.graph.edge(leg.edge().edge()) == null)
          return new Contact("track", -1, null, null, 0);
        if (!rails.available(leg.edge().edge())) return new Contact("unloaded", -1, null, null, 0);
      }
    var seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(Math.min(8192, t.cars.size() * 128));
    int steps = Math.max(1, (int) Math.ceil(Math.abs(to - from) * 4));
    for (int sample = 0; sample <= steps; sample++)
      for (int car = 0; car < t.cars.size(); car++) {
        double center = from + (to - from) * sample / steps - car * StockSpec.SPACING;
        BogiePose pose;
        try {
          pose = BogiePose.sample(t.path, rails.graph, center, t.cars.get(car).reversed);
        } catch (IllegalArgumentException e) {
          return new Contact("track", -1, null, null, 0);
        }
        var box = box(pose);
        var envelope = StockEnvelope.of(pose);
        if (!data.level
                .getWorldBorder()
                .isWithinBounds(BlockPos.containing(box.minX, box.minY, box.minZ))
            || !data.level
                .getWorldBorder()
                .isWithinBounds(BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
          return new Contact("border", car, null, null, Math.max(0, sample - 1) / (double) steps);
        // The thin bed belongs below the train; complete prior rail clearance must stay clear.
        for (var p :
            BlockPos.betweenClosed(
                (int) Math.floor(box.minX + 1e-6),
                (int) Math.floor(box.minY + 1e-6),
                (int) Math.floor(box.minZ + 1e-6),
                (int) Math.floor(box.maxX - 1e-6),
                (int) Math.floor(box.maxY - 1e-6),
                (int) Math.floor(box.maxZ - 1e-6))) {
          if (!envelope.intersects(
              p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1)) continue;
          if (!seen.add(p.asLong())) continue;
          data.worldReads++;
          if (!data.level.hasChunkAt(p)) return new Contact("unloaded", -1, null, null, 0);
          var state = data.level.getBlockState(p);
          if (!state.isAir()
              && !state.is(RailContent.BED.get())
              && !state.getCollisionShape(data.level, p).isEmpty())
            return new Contact(
                "blocked", car, null, p.immutable(), Math.max(0, sample - 1) / (double) steps);
        }
        for (var e :
            data.level.getEntities(
                (Entity) null,
                box,
                e ->
                    !e.isSpectator()
                        && !(e instanceof net.minecraft.world.entity.item.ItemEntity))) {
          if (e instanceof StockEntity stock) {
            if (stock.trainId().equals(t.id) || ignore != null && stock.trainId().equals(ignore))
              continue;
            if (TrainPorts.physicalView(stock)
                && envelope.intersects(
                    e.getBoundingBox().minX,
                    e.getBoundingBox().minY,
                    e.getBoundingBox().minZ,
                    e.getBoundingBox().maxX,
                    e.getBoundingBox().maxY,
                    e.getBoundingBox().maxZ))
              return new Contact(
                  "occupied", car, stock, null, Math.max(0, sample - 1) / (double) steps);
            continue;
          }
          if (e instanceof Player p
              && (p.isCreative()
                  || p.getVehicle() instanceof StockEntity vehicle
                      && vehicle.trainId().equals(t.id))) continue;
          if (e.isAlive() && (e instanceof LivingEntity || e.canBeCollidedWith())) {
            var b = e.getBoundingBox();
            if (envelope.intersects(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ))
              return new Contact("entity", car, e, null, Math.max(0, sample - 1) / (double) steps);
          }
        }
      }
    return READY;
  }

  public static void views(TrainData data, TrainData.Train t) {
    var graph = RailwayData.get(data.level).graph;
    var cars = List.copyOf(t.cars);
    var poses = new ArrayList<BogiePose>();
    // Stage the entire consist before entity-join listeners can inspect any projection.
    for (int i = 0; i < cars.size(); i++) {
      BogiePose pose = null;
      try {
        pose = BogiePose.sample(t.path, graph, t.center(i), cars.get(i).reversed);
      } catch (IllegalArgumentException ignored) {
      }
      poses.add(pose);
      if (pose != null) {
        var car = cars.get(i);
        car.recovery = pose.center();
        car.recoveryYaw = StockEntity.yaw(pose.forward());
        car.recoveryPitch = (float) -Math.toDegrees(Math.asin(pose.forward().y()));
      }
    }
    for (int i = 0; i < cars.size(); i++) {
      if (data.owner(cars.get(i).id) != t) continue;
      var car = cars.get(i);
      BogiePose pose = poses.get(i);
      if (pose == null) {
        if (car.recovery != null
            && data.level.getEntity(car.id) == null
            && data.level.hasChunkAt(BlockPos.containing(vec(car.recovery)))) {
          var projection = TrainContent.TYPES.get(car.kind).get().create(data.level);
          projection.setUUID(car.id);
          projection.bind(t.id);
          projection.moveTo(
              car.recovery.x(),
              car.recovery.y(),
              car.recovery.z(),
              car.recoveryYaw,
              car.recoveryPitch);
          data.level.addFreshEntity(projection);
        }
        continue;
      }
      var found = data.level.getEntity(car.id);
      StockEntity entity;
      if (found == null) {
        var p = BlockPos.containing(vec(pose.center()));
        if (!data.level.hasChunkAt(p)
            || !data.level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(p)))
          continue;
        entity = TrainContent.TYPES.get(car.kind).get().create(data.level);
        entity.setUUID(car.id);
        entity.bind(t.id);
        entity.authoritative(pose, car, t.distance, t.speed);
        if (!data.level.addFreshEntity(entity)) continue;
      } else if (found instanceof StockEntity stock) entity = stock;
      else continue;
      if (data.owner(car.id) != t) {
        entity.discard();
        continue;
      }
      entity.bind(t.id);
      entity.authoritative(pose, car, t.distance, t.speed);
      data.snapshots++;
    }
  }

  public static boolean mayUse(TrainData.Train t, net.minecraft.server.level.ServerPlayer p) {
    return !p.isSpectator()
        && p.mayBuild()
        && (p.isCreative()
            || t.owner.equals(p.getUUID())
            || factorycraft.research.ResearchTeams.member(p, t.scope));
  }

  private TrainWorld() {}
}
