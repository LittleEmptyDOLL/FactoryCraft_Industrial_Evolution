package factorycraft.trains;

import factorycraft.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;

/** Bounded per-active-train movement; claims and fuel settle before position or callbacks. */
public final class TrainSimulation {
  public static void tick(TrainData d, TrainData.Train t) {
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
    while ((t.direction > 0 ? t.path.max() - leading : leading - t.path.min()) < stopping
        && ahead++ < 128) if (!t.path.extend(rails.graph, t.direction > 0, t.branch)) break;
    double free = t.direction > 0 ? t.path.max() - t.front() : t.rear() - t.path.min();
    free = d.occupancy.freeDistance(t.id, t.path, t.rear(), t.front(), t.direction, free);
    if (free <= stopping) {
      brake = true;
      throttle = false;
      pay.clear();
    }
    var next = TrainMotion.tick(t.speed, p, throttle, brake);
    double delta = next.distance() * t.direction, target = t.head + delta;
    // Pre-extend only the leading route. Rear bogies keep historical turnout choices.
    double min = target - (t.cars.size() - 1) * StockSpec.SPACING - StockSpec.HALF_LENGTH,
        max = target + StockSpec.HALF_LENGTH;
    int extension = 0;
    while ((min < t.path.min() || max > t.path.max()) && extension++ < 128) {
      boolean front = max > t.path.max();
      if (!t.path.extend(rails.graph, front, t.branch)) {
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
    if (!contact.reason().equals("ready")) {
      t.status =
          contact.reason().equals("unloaded")
              ? TrainData.Status.UNLOADED
              : TrainData.Status.BLOCKED;
      if (contact.reason().equals("unloaded") || contact.reason().equals("track")) {
        if (contact.reason().equals("track")) t.speed = 0;
        // Unknown chunks cannot consume fuel or kinetic state, or damage an unseen target.
        TrainWorld.views(d, t);
        return;
      }
      // An unexpected obstacle inside braking distance absorbs paid movement. Commit the
      // last clear pose and sole resource owners before any hurt/death/entity-join callbacks.
      d.occupancy.reserve(t.id, wanted);
      d.spatial.reserveShapes(t.id, spatial);
      for (var debit : pay) debit.commit();
      double travelled = delta * contact.fraction();
      t.head += travelled;
      t.distance += travelled;
      t.speed = 0;
      d.occupancy.reserve(t.id, TrackOccupancy.span(t.path, t.rear(), t.front()));
      d.index(t);
      d.setDirty();
      TrainWorld.views(d, t);
      if (contact.car() >= 0
          && next.distance() > 0
          && (contact.block() != null || contact.entity() != null))
        TrainDamage.impact(d, t, contact, weight, next.speed(), driver);
      return;
    }
    // No world callback occurs between the full reservation, fuel ownership and pose commit.
    d.occupancy.reserve(t.id, wanted);
    d.spatial.reserveShapes(t.id, spatial);
    for (var debit : pay) debit.commit();
    t.speed = next.speed();
    t.head = target;
    t.distance += delta;
    if (t.speed > 0) t.status = brake ? TrainData.Status.BRAKING : TrainData.Status.DRIVING;
    else if (t.status != TrainData.Status.NO_FUEL && t.status != TrainData.Status.DESTROYED)
      t.status = TrainData.Status.STOPPED;
    t.path.trim(t.rear(), t.front());
    d.occupancy.reserve(t.id, TrackOccupancy.span(t.path, t.rear(), t.front()));
    d.index(t);
    d.setDirty();
    TrainWorld.views(d, t);
    if (t.speed == 0 && wasMoving) TrainPorts.wakePumps(d, t);
    if (t.speed == 0
        && t.desired == 0
        && t.cars.stream().allMatch(c -> l.getEntity(c.id) instanceof StockEntity))
      d.active.remove(t.id);
  }

  private TrainSimulation() {}
}
