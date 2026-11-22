package factorycraft.trains;

import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;

/**
 * Autonomous intent and prospective logical claims. Physical motion/payment stay in
 * TrainSimulation.
 */
public final class TrainAutomation {
  public static void manual(TrainData d, TrainData.Train t) {
    var a = t.automation;
    a.automatic = false;
    a.phase = AutoTrainState.Phase.MANUAL;
    a.routeJob = null;
    a.repathing = false;
    a.signalWaitTicks = 0;
    a.repathAt = 0;
    a.routed = false;
    a.arrived = false;
    a.destination = null;
    a.departure = null;
    a.departurePass = false;
    a.upcoming.clear();
    a.continuationJob = null;
    a.dispatchOrder = 0;
    a.dispatchTarget = null;
    a.heldSignal = null;
    a.blockingSignal = null;
    a.reserved = Set.of();
    var system = RailAutomationData.peek(d.level);
    if (system != null) {
      system.slots.release(t.id);
      system.dispatch.remove(t.id);
      system.claims.reserve(t.id, Set.of());
    }
    t.desired = 0;
    t.brake = true;
    d.active.add(t.id);
    d.setDirty();
  }

  public static boolean automatic(TrainData d, TrainData.Train t) {
    if (t.provisional || t.automation.invalid != null || t.automation.schedule.entries().isEmpty())
      return false;
    var s = RailAutomationData.get(d.level);
    s.trainNumber(t);
    t.automation.automatic = true;
    t.automation.retryAt = 0;
    t.automation.routeJob = null;
    t.automation.continuationJob = null;
    t.automation.continuationRetry = 0;
    t.automation.dispatchTarget = null;
    t.automation.repathing = false;
    t.automation.repathAt = 0;
    d.active.add(t.id);
    if (!t.automation.routed) enroll(d, t, s);
    d.setDirty();
    return true;
  }

  /** Enroll the bounded active batch before any of its trains can take a slot. */
  public static void enroll(TrainData d, TrainData.Train t, RailAutomationData s) {
    var a = t.automation;
    if (t.provisional
        || !a.automatic
        || a.routed
        || t.speed != 0
        || a.invalid != null
        || a.schedule.entries().isEmpty()
        || !s.runtimeReady(t)
        || !TrainWorld.available(d, t)) {
      s.dispatch.remove(t.id);
      return;
    }
    if ((a.phase == AutoTrainState.Phase.NO_PATH || a.phase == AutoTrainState.Phase.ROUTE_LIMIT)
        && d.level.getGameTime() < a.retryAt) {
      // Unrelated slot churn must not immediately re-enroll a failed high-priority search
      // before the lower-ranked completed search gets a chance to commit.
      s.dispatch.remove(t.id);
      return;
    }
    if (a.entry >= a.schedule.entries().size()) a.entry = 0;
    var origin = a.departure == null ? null : s.device(a.departure);
    if (a.departure != null
        && (origin == null || !origin.scope.equals(t.scope) || !s.online(origin.point))) {
      s.dispatch.remove(t.id);
      return;
    }
    if (a.dispatchOrder == 0) {
      a.dispatchOrder = s.dispatchOrder();
      d.setDirty();
    }
    String name = a.schedule.entries().get(a.entry).station();
    var targets = new HashSet<UUID>();
    for (var stop : s.slots.named(t.scope, name)) targets.add(stop.id());
    if (a.dispatchTarget != null) targets.retainAll(Set.of(a.dispatchTarget));
    s.dispatch.put(
        new RailDispatchQueue.Request(
            t.id,
            t.scope,
            name,
            a.departure,
            origin == null ? 0 : s.stop(origin).priority(),
            a.dispatchOrder,
            targets,
            a.dispatchTarget != null));
  }

  /** Indexing runs before impact callbacks can inspect the committed new pose. */
  public static void departed(TrainData d, TrainData.Train t) {
    var a = t.automation;
    var s = RailAutomationData.peek(d.level);
    if (a.departure != null && t.head != a.departureHead) {
      if (s != null) s.slots.releaseOrigin(t.id);
      a.departure = null;
      a.departurePass = false;
      d.setDirty();
    }
    if (s != null) passed(d, t, s);
  }

  /** An incoming commitment alone never exposes cargo at the target station. */
  public static UUID station(TrainData.Train t, RailAutomationData s) {
    var a = t.automation;
    if (t.speed != 0) return null;
    if (a.arrived) return a.destination;
    var origin = a.departure == null ? null : s.device(a.departure);
    if (origin == null || t.head != a.departureHead) return null;
    double nose = a.departureDirection > 0 ? t.front() : t.rear();
    var leg = t.path.at(nose);
    if (leg == null || !leg.edge().edge().equals(origin.point.edge())) return null;
    boolean reverse = a.departureDirection > 0 ? leg.edge().reverse() : !leg.edge().reverse();
    double at = Math.clamp(nose - leg.begin(), 0, leg.length());
    if (leg.edge().reverse()) at = leg.length() - at;
    return reverse == origin.point.reverse() && Math.abs(at - origin.point.offset()) <= .061
        ? origin.id
        : null;
  }

  /** Safe before fuel reservation, including during a budgeted topology publication. */
  public static boolean prepare(TrainData d, TrainData.Train t) {
    var a = t.automation;
    var s = RailAutomationData.get(d.level);
    int searchStart = s.searchBudget();
    t.desired = 0;
    t.brake = true;
    if (a.routed && a.checkedGraph != s.rails.graph.revisions()) {
      a.checkedGraph = s.rails.graph.revisions();
      if (!t.path.connected(s.rails.graph)) {
        a.routed = false;
        a.arrived = false;
        a.routeJob = null;
        clearUpcoming(t, s);
      }
    }
    if (!s.runtimeReady(t)) {
      s.dispatch.remove(t.id);
      a.phase = AutoTrainState.Phase.UNLOADED;
      return false;
    }
    if (a.schedule.entries().isEmpty()) {
      s.dispatch.remove(t.id);
      a.phase = AutoTrainState.Phase.NO_PATH;
      return t.speed > 0;
    }
    if (a.entry >= a.schedule.entries().size()) a.entry = 0;
    if (a.departure != null && !a.departure.equals(s.slots.origin(t.id))) {
      a.departure = null;
      a.departurePass = false;
      d.setDirty();
    }
    if (a.departure != null) {
      var origin = s.device(a.departure);
      if (origin == null || !s.online(origin.point)) {
        s.dispatch.remove(t.id);
        a.phase = AutoTrainState.Phase.UNLOADED;
        return false;
      }
    }
    if (a.routed
        && (s.device(a.destination) == null
            || !Objects.equals(s.slots.destination(t.id), a.destination))) {
      a.routed = false;
      a.arrived = false;
      a.routeJob = null;
      clearUpcoming(t, s);
    }
    if (!a.routed) {
      if (t.speed > 0) return true; // change definition/destination only after a real brake
      a.repathing = false;
      dispatch(d, t, s, false);
      if (!a.routed) return false;
    }
    var stop = s.device(a.destination);
    if (stop == null) return false;
    if (!s.online(stop.point)) {
      a.phase = AutoTrainState.Phase.UNLOADED;
      return false;
    }
    if (t.speed == 0 && a.heldSignal != null) {
      a.signalWaitTicks = Math.min(Integer.MAX_VALUE - 1, a.signalWaitTicks + 1);
      d.setDirty();
      if (repathAtSignal(d, t, s)) return false;
      stop = s.device(a.destination);
      if (stop == null) return false;
    } else if (a.signalWaitTicks != 0) {
      a.signalWaitTicks = 0;
      a.repathAt = 0;
      d.setDirty();
    }
    if (!a.upcoming.isEmpty()) {
      boolean valid = a.waypointDirection == t.direction;
      for (var w : a.upcoming) {
        var future = s.device(w.destination());
        if (future == null
            || !future.scope.equals(t.scope)
            || !s.online(future.point)
            || !s.slots.planned(t.id, w.destination())) {
          valid = false;
          break;
        }
      }
      if (!valid) clearUpcoming(t, s);
    }
    extend(d, t, s, Math.max(0, 256 - (searchStart - s.searchBudget())));
    passed(d, t, s);
    double remaining = (a.targetHead - t.head) * t.direction;
    if (t.speed == 0 && remaining >= -1e-6 && remaining <= .06) {
      if (a.schedule.entries().get(a.entry).waits().isEmpty()) {
        // A genuinely unavailable continuation may stop here. No fake station-wait tick.
        departure(d, t, s, true);
        return false;
      }
      if (!a.arrived) {
        a.arrived = true;
        a.stationTicks = 0;
        a.idleTicks = 0;
        a.lastCargo = Long.MIN_VALUE;
        TrainPorts.wakePumps(d, t);
      }
      a.phase = AutoTrainState.Phase.STATION_WAIT;
      long version = 0;
      for (var car : t.cars) version = 31 * version + car.cargoVersion;
      if (a.lastCargo != Long.MIN_VALUE && a.lastCargo != version) a.idleTicks = 0;
      a.lastCargo = version;
      a.stationTicks = Math.min(Integer.MAX_VALUE - 1, a.stationTicks + 1);
      a.idleTicks = Math.min(Integer.MAX_VALUE - 1, a.idleTicks + 1);
      d.setDirty();
      var waits = a.schedule.entries().get(a.entry).waits();
      if (RailWait.ready(
          waits, c -> TrainWaitSamples.sample(d, t, s, c), TrainWaitSamples::threshold)) {
        departure(d, t, s, false);
      }
      return false;
    }
    if (remaining < -.06) {
      a.phase = AutoTrainState.Phase.INVALID_CONFIG;
      return t.speed > 0;
    }
    a.phase = AutoTrainState.Phase.DRIVING;
    t.desired = t.direction;
    t.brake = false;
    return true;
  }

  public static void clearUpcoming(TrainData.Train t, RailAutomationData s) {
    t.automation.upcoming.clear();
    t.automation.continuationJob = null;
    t.automation.continuationRetry = 0;
    s.slots.clearPlanned(t.id);
    // Prospective station slots are not already-entered logical block/chain exit claims.
  }

  private static void departure(
      TrainData d, TrainData.Train t, RailAutomationData s, boolean pass) {
    var a = t.automation;
    if (!s.slots.depart(t.id, a.destination)) {
      a.phase = AutoTrainState.Phase.INVALID_CONFIG;
      return;
    }
    clearUpcoming(t, s);
    a.departure = a.destination;
    a.departureHead = t.head;
    a.departureDirection = t.direction;
    a.departurePass = pass;
    a.destination = null;
    a.routed = false;
    a.arrived = false;
    a.entry = (a.entry + 1) % a.schedule.entries().size();
    a.routeJob = null;
    a.retryAt = 0;
    a.dispatchTarget = null;
    a.dispatchOrder = 0;
    a.phase = AutoTrainState.Phase.DISPATCHING;
    enroll(d, t, s);
    d.setDirty();
  }

  /** Called only at canonical committed poses, including before native impact callbacks. */
  private static void passed(TrainData d, TrainData.Train t, RailAutomationData s) {
    var a = t.automation;
    while (a.automatic
        && a.routed
        && !a.upcoming.isEmpty()
        && a.schedule.entries().get(a.entry).waits().isEmpty()
        && (a.targetHead - t.head) * t.direction <= (t.speed == 0 ? .06 : 1e-7)) {
      var next = a.upcoming.getFirst();
      var remaining = new HashSet<UUID>();
      for (int i = 1; i < a.upcoming.size(); i++) remaining.add(a.upcoming.get(i).destination());
      if (t.speed == 0 && a.departure == null) {
        if (!s.slots.depart(t.id, a.destination)) break;
        a.departure = a.destination;
        a.departureHead = t.head;
        a.departureDirection = t.direction;
        a.departurePass = true;
      }
      if (!s.slots.promote(t.id, next.destination(), remaining)) break;
      a.upcoming.removeFirst();
      a.entry = next.entry();
      a.destination = next.destination();
      a.targetHead = next.head();
      a.arrived = false;
      a.stationTicks = a.idleTicks = 0;
      a.lastCargo = Long.MIN_VALUE;
      // Keep entered-chain exit leases and the exact TrainPath under every carriage.
      d.setDirty();
    }
  }

  /** Prepare a bounded window ahead using the same path search, never publish a partial result. */
  private static void extend(TrainData d, TrainData.Train t, RailAutomationData s, int allowance) {
    var a = t.automation;
    int end = a.upcoming.isEmpty() ? a.entry : a.upcoming.getLast().entry();
    if (!a.schedule.entries().get(end).waits().isEmpty() || allowance <= 0) return;
    double weight = 0, braking = 0;
    for (var car : t.cars) {
      weight += car.kind.weight;
      braking += car.kind.brake;
    }
    double horizon =
        Math.max(
            128,
            TrainMotion.stoppingDistance(t.speed, weight, braking) + t.front() - t.rear() + 32);
    for (int count = 0; count < 8 && a.upcoming.size() < 64; count++) {
      int last = a.upcoming.isEmpty() ? a.entry : a.upcoming.getLast().entry();
      if (!a.schedule.entries().get(last).waits().isEmpty()
          || (a.horizon() - t.head) * t.direction > horizon) return;
      int next = (last + 1) % a.schedule.entries().size();
      if (a.continuationJob == null) {
        if (d.level.getGameTime() < a.continuationRetry) return;
        var targets = new ArrayList<RailPathSearch.Target>();
        for (var stop : s.slots.named(t.scope, a.schedule.entries().get(next).station())) {
          var device = s.device(stop.id());
          if (device != null
              && s.online(device.point)
              && s.slots.available(stop.id(), t.id)
              && (!s.dispatch.contested(stop.id())
                  || stop.id().equals(s.slots.destination(t.id))
                  || s.slots.planned(t.id, stop.id())))
            targets.add(new RailPathSearch.Target(stop.id(), device.point, stop.priority()));
        }
        if (targets.isEmpty()) {
          a.continuationRetry = d.level.getGameTime() + 20;
          return;
        }
        a.continuationJob = new TrainRouteJob(d, t, s, targets, 256, a.horizon(), true);
      }
      if (s.searchBudget() <= 0 || allowance <= 0) return;
      long start = System.nanoTime();
      int work = a.continuationJob.advance(t, s, Math.min(allowance, s.searchBudget()));
      allowance -= work;
      s.spentSearch(work, System.nanoTime() - start);
      if (!a.continuationJob.done) return;
      var job = a.continuationJob;
      var route = job.result(t, s.rails.graph);
      a.continuationJob = null;
      if (route == null) {
        a.continuationRetry = d.level.getGameTime() + (job.stale ? 0 : 20);
        return;
      }
      if ((s.dispatch.contested(route.destination())
              && !route.destination().equals(s.slots.destination(t.id))
              && !s.slots.planned(t.id, route.destination()))
          || !s.slots.plan(t.id, route.destination())) {
        a.continuationRetry = d.level.getGameTime() + 20;
        return;
      }
      t.path = route.path();
      a.waypointDirection = t.direction;
      a.upcoming.add(new AutoTrainState.Waypoint(next, route.destination(), route.targetHead()));
      d.index(t);
      d.setDirty();
    }
  }

  /** A stopped scout may reconsider its path, never surrender an entered chain's safe exit. */
  private static boolean repathAtSignal(TrainData d, TrainData.Train t, RailAutomationData s) {
    var a = t.automation;
    if (!a.repathing) {
      if (!s.claims.body(t.id).containsAll(a.reserved)) return false;
      var signal = s.points.get(a.heldSignal);
      if (signal == null) return false;
      int interval =
          signal.kind() == RailEdgePoints.Kind.CHAIN
              ? RailRoutePenalties.chainRetryTicks(
                  Math.max(
                      1,
                      s.slots.named(t.scope, a.schedule.entries().get(a.entry).station()).size()))
              : 100;
      long due = a.repathAt == 0 ? interval : a.repathAt;
      if (a.signalWaitTicks < due) return false;
      a.repathAt = (long) a.signalWaitTicks + interval;
      a.repathing = true;
      clearUpcoming(t, s);
      a.routeJob = null;
      a.retryAt = 0;
    }
    dispatch(d, t, s, true);
    if (a.routeJob != null) return true; // paused, not partially published
    a.repathing = false;
    return false;
  }

  private static void dispatch(
      TrainData d, TrainData.Train t, RailAutomationData s, boolean replacement) {
    var a = t.automation;
    long now = d.level.getGameTime();
    if (!replacement) {
      enroll(d, t, s);
      if (s.dispatch.get(t.id) == null) return;
    }
    boolean changed =
        a.checkedGraph != s.rails.graph.revisions()
            || a.checkedRules != s.revision + s.slots.revision();
    if (a.routeJob == null) {
      if (!replacement && !changed && now < a.retryAt) return;
      a.checkedGraph = s.rails.graph.revisions();
      a.checkedRules = s.revision + s.slots.revision();
      a.retryAt = now + 20;
      var named = s.slots.named(t.scope, a.schedule.entries().get(a.entry).station());
      var targets = new ArrayList<RailPathSearch.Target>();
      for (var stop : named) {
        var device = s.device(stop.id());
        if (s.slots.available(stop.id(), t.id) && device != null && s.online(device.point))
          targets.add(new RailPathSearch.Target(stop.id(), device.point, stop.priority()));
      }
      if (targets.isEmpty()) {
        if (!replacement)
          a.phase =
              named.isEmpty()
                  ? AutoTrainState.Phase.NO_PATH
                  : AutoTrainState.Phase.DESTINATION_FULL;
        if (!replacement && named.isEmpty()) s.dispatch.remove(t.id);
        return;
      }
      a.routeJob = new TrainRouteJob(d, t, s, targets);
      a.phase = replacement ? AutoTrainState.Phase.SIGNAL_WAIT : AutoTrainState.Phase.DISPATCHING;
    }
    if (s.searchBudget() <= 0) return;
    long start = System.nanoTime();
    int work = a.routeJob.advance(t, s, Math.min(256, s.searchBudget()));
    s.spentSearch(work, System.nanoTime() - start);
    if (!a.routeJob.done) return;
    var job = a.routeJob;
    var route = job.result(t, s.rails.graph);
    if (route == null) {
      a.routeJob = null;
      a.dispatchTarget = null;
      if (job.stale && !replacement) {
        // Revalidation is not a proof that this higher-priority request has no path.
        // Keep its rank while restarting, rather than opening a one-tick slot-stealing gap.
        a.retryAt = 0;
        a.phase = AutoTrainState.Phase.DISPATCHING;
        enroll(d, t, s);
        return;
      }
      if (!replacement) s.dispatch.remove(t.id);
      a.retryAt = job.stale ? 0 : now + 20;
      if (!replacement)
        a.phase = job.limited ? AutoTrainState.Phase.ROUTE_LIMIT : AutoTrainState.Phase.NO_PATH;
      return;
    }
    if (!replacement) {
      a.dispatchTarget = route.destination();
      s.dispatch.resolve(t.id, route.destination());
      if (!s.dispatch.first(t.id, route.destination())) return;
    }
    if (!s.slots.acquire(t.id, route.destination())) {
      a.routeJob = null;
      a.dispatchTarget = null;
      if (!replacement) a.phase = AutoTrainState.Phase.DESTINATION_FULL;
      return;
    }
    a.routeJob = null;
    a.dispatchTarget = null;
    a.dispatchOrder = 0;
    s.dispatch.remove(t.id);
    t.path = route.path();
    t.direction = route.direction();
    a.targetHead = route.targetHead();
    if (!replacement) {
      a.heldSignal = null;
      a.signalWaitTicks = 0;
    }
    a.blockingSignal = null;
    a.reservationEnd = t.direction > 0 ? t.rear() : t.front();
    a.destination = route.destination();
    a.routed = true;
    a.arrived = false;
    a.stationTicks = 0;
    a.idleTicks = 0;
    a.checkedGraph = s.rails.graph.revisions();
    a.checkedRules = s.revision + s.slots.revision();
    d.index(t);
    d.setDirty();
  }

  private record Boundary(RailEdgePoints.Point point, UUID cut, double distance) {}

  /** Ordered scout on the already installed directed path; never scans the whole railway. */
  public static double freeDistance(TrainData d, TrainData.Train t, double lookahead) {
    var s = RailAutomationData.get(d.level);
    var a = t.automation;
    a.blockingSignal = null;
    double target = Math.max(0, (a.horizon() - t.head) * t.direction);
    double nose = t.direction > 0 ? t.front() : t.rear();
    var boundaries = new ArrayList<Boundary>();
    var legs = t.path.legs();
    int begin = t.path.indexAt(nose);
    double scoutUntil = lookahead + (t.front() - t.rear());
    boolean seekingExit = false;
    double knownEnd = 0;
    int scanned = 0;
    for (int at = begin; at >= 0 && at < legs.size(); at += t.direction) {
      if (++scanned > 512) {
        a.phase = AutoTrainState.Phase.ROUTE_LIMIT;
        return 0;
      }
      var leg = legs.get(at);
      double near = t.direction > 0 ? Math.max(nose, leg.begin()) : Math.min(nose, leg.end());
      double far = t.direction > 0 ? leg.end() : leg.begin();
      if ((far - near) * t.direction < 0) continue;
      double from = near - leg.begin(), to = far - leg.begin();
      if (leg.edge().reverse()) {
        from = leg.length() - from;
        to = leg.length() - to;
      }
      var travel =
          t.direction > 0
              ? leg.edge()
              : new RailGraph.Directed(leg.edge().edge(), !leg.edge().reverse());
      for (var p : s.points.cutsAhead(travel, from, to)) {
        double coordinate =
            leg.begin() + (leg.edge().reverse() ? leg.length() - p.offset() : p.offset());
        double distance = (coordinate - nose) * t.direction;
        if (distance <= target + 1e-7) {
          var side = s.signals.side(travel, p.offset());
          boundaries.add(new Boundary(side, p.id(), distance));
          if (side != null && side.kind() == RailEdgePoints.Kind.CHAIN && distance <= lookahead)
            seekingExit = true;
          if (side != null && side.kind() == RailEdgePoints.Kind.SIGNAL && seekingExit) {
            seekingExit = false;
            scoutUntil = Math.max(scoutUntil, distance + (t.front() - t.rear()));
          }
        }
      }
      knownEnd = Math.max(knownEnd, (far - nose) * t.direction);
      if (knownEnd >= target || knownEnd >= scoutUntil && !seekingExit) break;
    }
    knownEnd = Math.min(knownEnd, target);
    var wanted = new LinkedHashSet<UUID>();
    // Restore canonical unexpired claims only after all body occupancy has been reconciled.
    for (var id : a.reserved) if (s.blocks.region(id) != null) wanted.add(id);
    // Retain an already-entered block until the last wagon clears it.
    var body = new HashSet<UUID>();
    for (var interval : TrackOccupancy.span(t.path, t.rear(), t.front())) {
      var regions = s.blocks.covering(interval.edge(), interval.min(), interval.max());
      if (regions == null) return 0;
      for (var region : regions) if (!s.claims.available(region, t.id)) return 0;
      body.addAll(regions);
    }
    double tail = t.direction > 0 ? t.rear() : t.front();
    if ((tail - a.reservationEnd) * t.direction > 1e-7) wanted.retainAll(body);
    double free = target;
    for (int i = 0; i < boundaries.size(); i++) {
      var b = boundaries.get(i);
      if (b.distance() > lookahead) break;
      var prospective = new LinkedHashSet<>(wanted);
      boolean okay = true, chain = false, exit = false;
      int last = i;
      for (int j = i; j < boundaries.size(); j++) {
        var next = boundaries.get(j);
        var p = next.point();
        last = j;
        if (p == null || !s.online(p)) {
          okay = false;
          break;
        }
        var region = s.blocks.at(p.edge(), p.offset(), !p.reverse());
        var before = s.blocks.at(p.edge(), p.offset(), p.reverse());
        if (region == null || before == null || region.id.equals(before.id)) {
          okay = false;
          break;
        }
        boolean held = a.reserved.contains(region.id);
        if (s.forced(p) && !held) {
          okay = false;
          break;
        }
        prospective.add(region.id);
        if (p.kind() == RailEdgePoints.Kind.CHAIN) {
          chain = true;
          continue;
        }
        if (!chain) {
          exit = true;
          break;
        }
        // The chosen first regular exit must hold the entire consist beyond the junction.
        double end = j + 1 < boundaries.size() ? boundaries.get(j + 1).distance() : knownEnd;
        if (end - next.distance() + 1e-7 < t.front() - t.rear()) {
          okay = false;
          break;
        }
        exit = true;
        break;
      }
      if (chain && !exit) okay = false;
      if (!okay || !s.claims.reserve(t.id, prospective)) {
        a.blockingSignal = b.point() == null ? b.cut() : b.point().id();
        free = Math.min(free, Math.max(0, b.distance() - .15));
        // Once source braking has brought us to rest at this refusal, do not repeatedly
        // purchase tiny acceleration/braking cycles. Only a successful scout releases it.
        if (t.speed == 0 && a.heldSignal != null && a.heldSignal.equals(a.blockingSignal)) free = 0;
        break;
      }
      a.reservationEnd =
          t.direction > 0
              ? Math.max(a.reservationEnd, nose + boundaries.get(last).distance())
              : Math.min(a.reservationEnd, nose - boundaries.get(last).distance());
      wanted = prospective;
      i = last;
    }
    if (a.blockingSignal == null) a.heldSignal = null;
    s.claims.reserve(t.id, wanted);
    if (!a.reserved.equals(wanted)) {
      a.reserved = Set.copyOf(wanted);
      d.setDirty();
    }
    if (free < target - 1e-6) a.phase = AutoTrainState.Phase.SIGNAL_WAIT;
    return free;
  }

  private TrainAutomation() {}
}
