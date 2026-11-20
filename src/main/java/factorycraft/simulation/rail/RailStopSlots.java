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
    if (old != null && (!old.scope().equals(stop.scope()) || !old.name().equals(stop.name())))
      released = remove(stop.id());
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
    for (var train : released) release(train);
    revision++;
    return released;
  }

  public boolean available(UUID stop, UUID train) {
    var target = stops.get(stop);
    if (target == null) return false;
    if (stop.equals(trains.get(train))) return true;
    return target.enabled()
        && target.limit() != 0
        && (target.limit() < 0 || count(stop) < target.limit());
  }

  /** Check all before releasing an existing commitment, including a disabled committed arrival. */
  public boolean acquire(UUID train, UUID stop) {
    if (!available(stop, train)) return false;
    if (stop.equals(trains.get(train))) return true;
    release(train);
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
    var stop = trains.remove(train);
    if (stop == null) return;
    var set = approaching.get(stop);
    set.remove(train);
    if (set.isEmpty()) approaching.remove(stop);
    revision++;
  }

  public List<Stop> named(String scope, String name) {
    return names.getOrDefault(new Name(scope, name), Set.of()).stream()
        .map(stops::get)
        .sorted(Comparator.comparingInt(Stop::priority).reversed().thenComparing(Stop::id))
        .toList();
  }
}
