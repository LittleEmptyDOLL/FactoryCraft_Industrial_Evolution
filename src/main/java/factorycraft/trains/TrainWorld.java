package factorycraft.trains;

import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;

/** Loaded-only projection and precise source-sized collision adapter. */
public final class TrainWorld {
  public static Vec3 vec(RailVector p) {
    return new Vec3(p.x(), p.y(), p.z());
  }

  public static AABB box(BogiePose p) {
    return box(StockEnvelope.of(p));
  }

  public static AABB box(StockEnvelope hull) {
    var b = hull.bounds();
    return new AABB(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1());
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
        var car = t.cars.get(i);
        var hull =
            StockEnvelope.of(
                BogiePose.sample(t.path, rails.graph, t.center(i), car.reversed), car.kind);
        var b = box(hull);
        for (int x = ((int) Math.floor(b.minX)) >> 4; x <= ((int) Math.floor(b.maxX)) >> 4; x++)
          for (int z = ((int) Math.floor(b.minZ)) >> 4; z <= ((int) Math.floor(b.maxZ)) >> 4; z++) {
            if (!hull.intersects(
                x * 16., b.minY - 1, z * 16., x * 16. + 16, b.maxY + 1, z * 16. + 16)) continue;
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

  private static boolean straight(TrainData data, TrainData.Train t, double from, double to) {
    var graph = RailwayData.get(data.level).graph;
    double min = Math.min(from, to) - (t.cars.size() - 1) * 7 - 3.5, max = Math.max(from, to) + 3.5;
    for (var leg : t.path.legs())
      if (leg.end() >= min && leg.begin() <= max) {
        var e = graph.edge(leg.edge().edge());
        if (e == null
            || !Double.isInfinite(e.curve().minimumRadius())
            || e.curve().maximumGrade() > 1e-10) return false;
      }
    return true;
  }

  public static List<SweptHull> sweep(TrainData data, TrainData.Train t, double from, double to) {
    var graph = RailwayData.get(data.level).graph;
    var out = new ArrayList<SweptHull>(t.cars.size());
    boolean linear = straight(data, t, from, to);
    int count = linear ? 1 : Math.max(1, (int) Math.ceil(Math.abs(to - from) * 8));
    for (int i = 0; i < t.cars.size(); i++) {
      var samples = new ArrayList<StockEnvelope>(count + 1);
      var car = t.cars.get(i);
      for (int k = 0; k <= count; k++) {
        var pose =
            BogiePose.sample(
                t.path,
                graph,
                from + (to - from) * k / count - i * StockSpec.SPACING,
                car.reversed);
        var hull = StockEnvelope.of(pose, car.kind);
        samples.add(from == to || linear ? hull : hull.grow(.085));
      }
      out.add(new SweptHull(samples));
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
    var shapes =
        new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<java.util.List<AABB>>(
            Math.min(8192, t.cars.size() * 128));
    var previous = new StockEnvelope[t.cars.size()];
    boolean linear = straight(data, t, from, to);
    int steps = linear ? 1 : Math.max(1, (int) Math.ceil(Math.abs(to - from) * 8));
    for (int sample = 0; sample <= steps; sample++)
      for (int car = 0; car < t.cars.size(); car++) {
        double center = from + (to - from) * sample / steps - car * StockSpec.SPACING;
        BogiePose pose;
        try {
          pose = BogiePose.sample(t.path, rails.graph, center, t.cars.get(car).reversed);
        } catch (IllegalArgumentException e) {
          return new Contact("track", -1, null, null, 0);
        }
        var envelope = StockEnvelope.of(pose, t.cars.get(car).kind);
        var motion = new MotionHull(previous[car], envelope);
        var box = box(envelope);
        if (previous[car] != null) box = box.minmax(box(previous[car]));
        if (!data.level
                .getWorldBorder()
                .isWithinBounds(BlockPos.containing(box.minX, box.minY, box.minZ))
            || !data.level
                .getWorldBorder()
                .isWithinBounds(BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
          return new Contact("border", car, null, null, Math.max(0, sample - 1) / (double) steps);
        // Neighbor origins matter: fences/pistons can extend beyond their own voxel.
        for (var p :
            BlockPos.betweenClosed(
                (int) Math.floor(box.minX) - 1,
                (int) Math.floor(box.minY) - 1,
                (int) Math.floor(box.minZ) - 1,
                (int) Math.floor(box.maxX) + 1,
                (int) Math.floor(box.maxY) + 1,
                (int) Math.floor(box.maxZ) + 1)) {
          long key = p.asLong();
          var boxes = shapes.get(key);
          if (boxes == null) {
            data.worldReads++;
            if (!data.level.hasChunkAt(p)) return new Contact("unloaded", -1, null, null, 0);
            var state = data.level.getBlockState(p);
            boxes =
                state.isAir() || state.is(RailContent.BED.get())
                    ? List.of()
                    : state.getCollisionShape(data.level, p).toAabbs();
            shapes.put(key, boxes);
          }
          for (var sub : boxes) {
            var world = sub.move(p);
            if (motion.intersects(
                world.minX, world.minY, world.minZ, world.maxX, world.maxY, world.maxZ))
              return new Contact(
                  "blocked", car, null, p.immutable(), Math.max(0, sample - 1) / (double) steps);
          }
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
            if (TrainPorts.physicalView(stock) && envelope.intersects(stock.hull()))
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
            if (motion.intersects(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ))
              return new Contact("entity", car, e, null, Math.max(0, sample - 1) / (double) steps);
          }
        }
        previous[car] = envelope;
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
