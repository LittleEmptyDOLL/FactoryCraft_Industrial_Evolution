package factorycraft.trains;

import factorycraft.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;

/** Bounded per-active-train movement; claims and fuel settle before position or callbacks. */
public final class TrainSimulation {
  public static void tick(TrainData d, TrainData.Train t) {
    if (d.train(t.id) != t || !d.moving.add(t.id)) return;
    try {
      advance(d, t);
    } finally {
      d.moving.remove(t.id);
    }
  }

  private static void advance(TrainData d, TrainData.Train t) {
    var l = d.level;
    var rails = RailwayData.get(l);
    d.ensureIndex();
    if (!TrainWorld.available(d, t)) {
      boolean lost = false;
      for (var leg : t.path.legs())
        if (leg.end() >= t.rear()
            && leg.begin() <= t.front()
            && rails.graph.edge(leg.edge().edge()) == null) lost = true;
      t.status = lost ? TrainData.Status.BROKEN_TRACK : TrainData.Status.UNLOADED;
      if (lost) {
        t.speed = 0;
        t.desired = 0;
        TrainWorld.views(d, t);
      }
      for (var car : t.cars)
        if (l.getEntity(car.id) instanceof StockEntity view) view.pauseVisual();
      d.active.remove(t.id);
      return;
    }
    net.minecraft.server.level.ServerPlayer driver = null;
    if (t.driver != null)
      for (var car : t.cars)
        if (l.getEntity(car.id) instanceof StockEntity view)
          for (var rider : view.getPassengers())
            if (rider instanceof net.minecraft.server.level.ServerPlayer player
                && player.getUUID().equals(t.driver)) driver = player;
    boolean controlled =
        driver != null
            && driver.getVehicle() instanceof StockEntity s
            && s.trainId().equals(t.id)
            && l.getGameTime() - t.lastInput <= 20;
    boolean automatic = t.automation.automatic;
    if (automatic) {
      if (!TrainAutomation.prepare(d, t)) {
        if (t.automation.phase == AutoTrainState.Phase.UNLOADED) {
          for (var car : t.cars)
            if (l.getEntity(car.id) instanceof StockEntity view) view.pauseVisual();
        } else TrainWorld.views(d, t);
        d.setDirty();
        return;
      }
      controlled = true;
    }
    if (!controlled) {
      t.desired = 0;
      t.brake = true;
    }
    if (t.speed == 0 && t.desired != 0) t.direction = t.desired;
    boolean brake = t.brake || t.desired != 0 && t.desired != t.direction;
    boolean throttle = t.desired == t.direction && !brake;
    boolean wasMoving = t.speed > 0;
    double weight = 0, friction = 0, braking = 0, traction = 0, cap = 1.2;
    var pay = new ArrayList<TrainCar.FuelPlan>();
    var plans = new HashMap<UUID, TrainCar.FuelPlan>();
    boolean aligned = false;
    for (var c : t.cars) {
      weight += c.kind.weight;
      friction += .5;
      braking += c.kind.brake;
      if (throttle && c.kind == StockSpec.LOCOMOTIVE) {
        var plan = c.reserveFuel(30_000);
        if (plan != null) {
          plans.put(c.id, plan);
          if ((c.reversed ? -1 : 1) == t.direction) aligned = true;
        }
      }
    }
    for (var c : t.cars)
      if (c.kind == StockSpec.LOCOMOTIVE && throttle) {
        boolean forward = (c.reversed ? -1 : 1) == t.direction;
        if (aligned && !forward) continue;
        var p = plans.get(c.id);
        if (p != null) {
          pay.add(p);
          traction += 10 * p.acceleration() * (forward ? 1 : .6);
          cap = Math.max(cap, 1.2 * p.speed());
        }
      }
    if (t.cars.stream().anyMatch(c -> c.health <= 0)) {
      t.status = TrainData.Status.DESTROYED;
      throttle = false;
      brake = true;
      pay.clear();
      traction = 0;
    }
    if (throttle && pay.isEmpty()) {
      throttle = false;
      t.status = TrainData.Status.NO_FUEL;
      if (automatic) t.automation.phase = AutoTrainState.Phase.NO_FUEL;
    }
    var p =
        new TrainMotion.Parameters(
            weight,
            friction,
            braking,
            t.cars.get(t.direction > 0 ? 0 : t.cars.size() - 1).kind.air,
            traction,
            cap);
    // Source braking headway against known ends and other edge claims; retain atomic swept
    // checks below for unexpected walls, entities or graph edits inside the braking distance.
    double stopping = TrainMotion.stoppingDistance(t.speed, weight, braking) + 4;
    int ahead = 0;
    double leading = t.direction > 0 ? t.front() : t.rear();
    while (!automatic
        && (t.direction > 0 ? t.path.max() - leading : leading - t.path.min()) < stopping
        && ahead++ < 128) if (!t.path.extend(rails.graph, t.direction > 0, t.branch)) break;
    double free = t.direction > 0 ? t.path.max() - t.front() : t.rear() - t.path.min();
    free = d.occupancy.freeDistance(t.id, t.path, t.rear(), t.front(), t.direction, free);
    if (automatic)
      free =
          Math.min(free, TrainAutomation.freeDistance(d, t, stopping + Math.max(16, t.speed * 6)));
    boolean mustBrake =
        automatic
            ? TrainMotion.tick(t.speed, p, throttle, false).distance()
                    + TrainMotion.brakeDistance(
                        TrainMotion.tick(t.speed, p, throttle, false).speed(), p)
                > free - 1e-7
            : free <= stopping;
    if (mustBrake) {
      brake = true;
      throttle = false;
      pay.clear();
    }
    var next = TrainMotion.tick(t.speed, p, throttle, brake);
    if (automatic && brake && next.speed() == 0 && t.automation.blockingSignal != null)
      t.automation.heldSignal = t.automation.blockingSignal;
    double delta = next.distance() * t.direction, target = t.head + delta;
    // Pre-extend only the leading route. Rear bogies keep historical turnout choices.
    double min = target - (t.cars.size() - 1) * StockSpec.SPACING - StockSpec.HALF_LENGTH,
        max = target + StockSpec.HALF_LENGTH;
    int extension = 0;
    while ((min < t.path.min() || max > t.path.max()) && extension++ < 128) {
      boolean front = max > t.path.max();
      if (automatic || !t.path.extend(rails.graph, front, t.branch)) {
        t.speed = 0;
        t.status = TrainData.Status.END_OF_TRACK;
        d.setDirty();
        TrainWorld.views(d, t);
        return;
      }
    }
    // A cached lookahead may have been deleted or unloaded after the current body was
    // checked. Never sample missing bogie geometry or pay movement into an unknown edge.
    var route = TrainWorld.routeStatus(d, t, Math.min(t.rear(), min), Math.max(t.front(), max));
    if (!route.equals("ready")) {
      t.status = route.equals("unloaded") ? TrainData.Status.UNLOADED : TrainData.Status.BLOCKED;
      if (route.equals("track")) t.speed = 0;
      d.setDirty();
      TrainWorld.views(d, t);
      return;
    }
    var wanted = TrackOccupancy.span(t.path, Math.min(t.rear(), min), Math.max(t.front(), max));
    var spatial = TrainWorld.sweep(d, t, t.head, target);
    if (!d.occupancy.canReserve(t.id, wanted) || !d.spatial.canReserveShapes(t.id, spatial)) {
      t.speed = 0;
      t.status = TrainData.Status.RESERVED;
      TrainWorld.views(d, t);
      d.setDirty();
      return;
    }
    var contact = TrainWorld.contact(d, t, t.head, target, null);
    if (contact.reason().equals("unloaded") || contact.reason().equals("track")) {
      t.status =
          contact.reason().equals("unloaded")
              ? TrainData.Status.UNLOADED
              : TrainData.Status.BLOCKED;
      if (contact.reason().equals("track")) t.speed = 0;
      d.setDirty();
      TrainWorld.views(d, t);
      return;
    }
    // Retain the complete swept claims through callbacks. Fuel settles exactly once, regardless
    // of how many targets are encountered in the remaining fraction of this Minecraft tick.
    d.occupancy.reserve(t.id, wanted);
    d.spatial.reserveShapes(t.id, spatial);
    for (var debit : pay) debit.commit();
    int direction = t.direction;
    double reservedTarget = target;
    double motionSpeed = next.speed();
    TrainMotion.Remainder remainder = null;
    java.util.Set<UUID> resolved = null;
    for (int hits = 0; ; hits++) {
      if (contact.reason().equals("ready")) {
        double travelled = target - t.head;
        t.head = target;
        t.distance += travelled;
        t.speed = motionSpeed;
        break;
      }
      if (contact.reason().equals("unloaded") || contact.reason().equals("track")) {
        t.status =
            contact.reason().equals("unloaded")
                ? TrainData.Status.UNLOADED
                : TrainData.Status.BLOCKED;
        t.speed = contact.reason().equals("track") ? 0 : motionSpeed;
        settle(d, t);
        return;
      }
      double travelled = (target - t.head) * contact.fraction();
      if (remainder == null) remainder = new TrainMotion.Remainder(t.speed, p, throttle, brake);
      double impactSpeed = remainder.consume(Math.abs(travelled));
      t.head += travelled;
      t.distance += travelled;
      t.speed = 0;
      t.status = TrainData.Status.BLOCKED;
      var members = List.copyOf(t.cars);
      var path = t.path;
      double settledHead = t.head, settledDistance = t.distance;
      d.index(t);
      d.setDirty();
      TrainWorld.views(d, t);
      if (!canonical(d, t, members, path, settledHead, settledDistance, direction)) return;
      if (!(contact.entity() instanceof net.minecraft.world.entity.LivingEntity)
          || impactSpeed <= 0
          || contact.car() < 0) {
        if (contact.car() >= 0
            && impactSpeed > 0
            && (contact.block() != null || contact.entity() != null))
          TrainDamage.impact(d, t, contact, weight, impactSpeed, driver);
        settle(d, t);
        return;
      }
      if (resolved == null) resolved = new HashSet<>();
      if (!resolved.add(contact.entity().getUUID())) {
        settle(d, t); // A death callback revived/replaced this target. Never replay its hit.
        return;
      }
      var result = TrainDamage.living(d, t, contact, weight, impactSpeed, driver);
      if (!canonical(d, t, members, path, settledHead, settledDistance, direction)) return;
      if (!result.continues()) {
        settle(d, t);
        return;
      }
      remainder.impact(result.speed());
      target =
          Math.clamp(
              t.head + remainder.distance() * direction,
              Math.min(t.head, reservedTarget),
              Math.max(t.head, reservedTarget));
      motionSpeed = remainder.speed();
      if (hits == 15) {
        t.speed = motionSpeed;
        settle(d, t); // Bounded crowd work; preserve momentum and resume on the next tick.
        return;
      }
      // Recheck the real world after native damage/death callbacks; they may place a wall,
      // change chunks, or spawn a new blocker. No uninspected remainder can be teleported over.
      contact = TrainWorld.contact(d, t, t.head, target, null);
    }
    if (t.speed > 0) t.status = brake ? TrainData.Status.BRAKING : TrainData.Status.DRIVING;
    else if (t.status != TrainData.Status.NO_FUEL && t.status != TrainData.Status.DESTROYED)
      t.status = TrainData.Status.STOPPED;
    if (automatic) t.path.trimBehind(t.rear(), t.front(), t.direction);
    else t.path.trim(t.rear(), t.front());
    d.occupancy.reserve(t.id, TrackOccupancy.span(t.path, t.rear(), t.front()));
    d.index(t);
    d.setDirty();
    TrainWorld.views(d, t);
    if (t.speed == 0 && wasMoving) TrainPorts.wakePumps(d, t);
    if (!automatic
        && t.speed == 0
        && t.desired == 0
        && t.cars.stream().allMatch(c -> l.getEntity(c.id) instanceof StockEntity))
      d.active.remove(t.id);
  }

  private static boolean canonical(
      TrainData d,
      TrainData.Train t,
      List<TrainCar> members,
      TrainPath path,
      double head,
      double distance,
      int direction) {
    return d.train(t.id) == t
        && !t.provisional
        && t.cars.equals(members)
        && t.path == path
        && t.head == head
        && t.distance == distance
        && t.speed == 0
        && t.direction == direction
        && members.stream().allMatch(c -> d.owner(c.id) == t && d.car(c.id) == c);
  }

  private static void settle(TrainData d, TrainData.Train t) {
    if (d.train(t.id) != t) return;
    d.occupancy.reserve(t.id, TrackOccupancy.span(t.path, t.rear(), t.front()));
    d.index(t);
    d.setDirty();
    TrainWorld.views(d, t);
  }

  private TrainSimulation() {}
}
