package factorycraft.simulation.rail;

import java.util.*;

/** Factorio same-name stop slots. Native adapters persist each train's commitment exactly once. */
public final class RailStopSlots {
  public record Stop(UUID id, String scope, String name, boolean enabled, int limit, int priority) {
    public Stop {
      if (id == null
          || scope == null
          || scope.isBlank()
          || name == null
          || name.isBlank()
          || name.length() > 128
          || limit < -1
          || limit > 65535
          || priority < 0
          || priority > 255) throw new IllegalArgumentException("Train stop settings");
    }
  }

  private record Name(String scope, String name) {}

  private final Map<UUID, Stop> stops = new HashMap<>();
  private final Map<Name, Set<UUID>> names = new HashMap<>();
  private final Map<UUID, UUID> trains = new HashMap<>();
  private final Map<UUID, UUID> departures = new HashMap<>();
  private final Map<UUID, Set<UUID>> planned = new HashMap<>();
  private final Map<UUID, Set<UUID>> approaching = new HashMap<>();
  private long revision;

  public long revision() {
    return revision;
  }

  public Stop get(UUID id) {
    return stops.get(id);
  }

  public UUID destination(UUID train) {
    return trains.get(train);
  }

  public UUID origin(UUID train) {
    return departures.get(train);
  }

  public Set<UUID> trains(UUID stop) {
    return Set.copyOf(approaching.getOrDefault(stop, Set.of()));
  }

  public int count(UUID stop) {
    return approaching.getOrDefault(stop, Set.of()).size();
  }

  /**
   * Rename/scope changes invalidate precisely their indexed commitments. Limit decreases do not.
   */
  public Set<UUID> put(Stop stop) {
    var old = stops.get(stop.id());
    if (stop.equals(old)) return Set.of();
    Set<UUID> released = Set.of();
    if (old != null && (!old.scope().equals(stop.scope()) || !old.name().equals(stop.name()))) {
      var previous = names.get(new Name(old.scope(), old.name()));
      previous.remove(old.id());
      if (previous.isEmpty()) names.remove(new Name(old.scope(), old.name()));
      var affected = new HashSet<UUID>();
      for (var train : Set.copyOf(approaching.getOrDefault(stop.id(), Set.of()))) {
        if (unplan(train, stop.id())) affected.add(train);
        if (stop.id().equals(trains.get(train))) {
          releaseTarget(train);
          affected.add(train);
        }
        if (!old.scope().equals(stop.scope()) && stop.id().equals(departures.get(train))) {
          releaseOrigin(train);
          affected.add(train);
        }
      }
      released = Set.copyOf(affected);
    }
    stops.put(stop.id(), stop);
    names.computeIfAbsent(new Name(stop.scope(), stop.name()), k -> new HashSet<>()).add(stop.id());
    revision++;
    return released;
  }

  public Set<UUID> remove(UUID stop) {
    var old = stops.remove(stop);
    if (old == null) return Set.of();
    var namesake = names.get(new Name(old.scope(), old.name()));
    namesake.remove(stop);
    if (namesake.isEmpty()) names.remove(new Name(old.scope(), old.name()));
    var released = Set.copyOf(approaching.getOrDefault(stop, Set.of()));
    for (var train : released) {
      unplan(train, stop);
      if (stop.equals(trains.get(train))) releaseTarget(train);
      if (stop.equals(departures.get(train))) releaseOrigin(train);
    }
    revision++;
    return released;
  }

  public boolean available(UUID stop, UUID train) {
    var target = stops.get(stop);
    if (target == null) return false;
    if (references(train, stop)) return true;
    return target.enabled()
        && target.limit() != 0
        && (target.limit() < 0 || count(stop) < target.limit());
  }

  /** Check all before releasing an existing commitment, including a disabled committed arrival. */
  public boolean acquire(UUID train, UUID stop) {
    if (!available(stop, train)) return false;
    if (stop.equals(trains.get(train))) return true;
    releaseTarget(train);
    trains.put(train, stop);
    approaching.computeIfAbsent(stop, k -> new HashSet<>()).add(train);
    revision++;
    return true;
  }

  /** Restore a canonical saved lease without reapplying a subsequently reduced station limit. */
  public boolean restore(UUID train, UUID stop) {
    if (!stops.containsKey(stop) || trains.containsKey(train)) return false;
    trains.put(train, stop);
    approaching.computeIfAbsent(stop, k -> new HashSet<>()).add(train);
    revision++;
    return true;
  }

  public void release(UUID train) {
    clearPlanned(train);
    releaseTarget(train);
    releaseOrigin(train);
  }

  /** A docked ready train remains counted here until its first actual displacement. */
  public boolean depart(UUID train, UUID stop) {
    if (!stop.equals(trains.get(train))
        || departures.containsKey(train) && !stop.equals(departures.get(train))) return false;
    departures.put(train, stop);
    releaseTarget(train);
    revision++;
    return true;
  }

  public boolean restoreOrigin(UUID train, UUID stop) {
    if (!stops.containsKey(stop) || departures.containsKey(train)) return false;
    departures.put(train, stop);
    approaching.computeIfAbsent(stop, k -> new HashSet<>()).add(train);
    revision++;
    return true;
  }

  public void releaseOrigin(UUID train) {
    var stop = departures.remove(train);
    if (stop == null) return;
    if (!references(train, stop)) uncount(train, stop);
    revision++;
  }

  private void releaseTarget(UUID train) {
    var stop = trains.remove(train);
    if (stop == null) return;
    if (!references(train, stop)) uncount(train, stop);
    revision++;
  }

  private boolean references(UUID train, UUID stop) {
    return stop.equals(trains.get(train))
        || stop.equals(departures.get(train))
        || planned.getOrDefault(train, Set.of()).contains(stop);
  }

  /** A bounded prepared waypoint holds a real lease before its path is published. */
  public boolean plan(UUID train, UUID stop) {
    if (!available(stop, train)) return false;
    return restorePlanned(train, stop);
  }

  public boolean restorePlanned(UUID train, UUID stop) {
    if (!stops.containsKey(stop)) return false;
    if (planned.computeIfAbsent(train, k -> new HashSet<>()).add(stop)) {
      approaching.computeIfAbsent(stop, k -> new HashSet<>()).add(train);
      revision++;
    }
    return true;
  }

  public boolean planned(UUID train, UUID stop) {
    return planned.getOrDefault(train, Set.of()).contains(stop);
  }

  /** Promote without any transient release/reacquire gap, even for repeated physical stops. */
  public boolean promote(UUID train, UUID stop, Set<UUID> remaining) {
    if (!planned(train, stop)) return false;
    acquire(train, stop);
    for (var old : Set.copyOf(planned.getOrDefault(train, Set.of())))
      if (!remaining.contains(old)) unplan(train, old);
    return true;
  }

  public void clearPlanned(UUID train) {
    for (var stop : Set.copyOf(planned.getOrDefault(train, Set.of()))) unplan(train, stop);
  }

  private boolean unplan(UUID train, UUID stop) {
    var set = planned.get(train);
    if (set == null || !set.remove(stop)) return false;
    if (set.isEmpty()) planned.remove(train);
    if (!references(train, stop)) uncount(train, stop);
    revision++;
    return true;
  }

  private void uncount(UUID train, UUID stop) {
    var set = approaching.get(stop);
    set.remove(train);
    if (set.isEmpty()) approaching.remove(stop);
  }

  public List<Stop> named(String scope, String name) {
    return names.getOrDefault(new Name(scope, name), Set.of()).stream()
        .map(stops::get)
        .sorted(Comparator.comparingInt(Stop::priority).reversed().thenComparing(Stop::id))
        .toList();
  }
}
