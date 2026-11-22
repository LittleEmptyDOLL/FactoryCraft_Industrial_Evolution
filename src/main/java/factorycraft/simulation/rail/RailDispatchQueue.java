package factorycraft.simulation.rail;

import java.util.*;

/** Derived contention index, never a station-slot or resource owner. */
public final class RailDispatchQueue {
  public record Request(
      UUID train,
      String scope,
      String name,
      UUID origin,
      int priority,
      long order,
      Set<UUID> targets,
      boolean resolved) {
    public Request {
      if (train == null
          || scope == null
          || scope.isBlank()
          || name == null
          || name.isBlank()
          || priority < 0
          || priority > 255
          || order < 1) throw new IllegalArgumentException("Dispatch request");
      targets = Set.copyOf(targets);
    }
  }

  private record Name(String scope, String name) {}

  private static final Comparator<Request> ORDER =
      Comparator.comparingInt(Request::priority)
          .reversed()
          .thenComparingLong(Request::order)
          .thenComparing(Request::train);
  private final Map<UUID, Request> requests = new HashMap<>();
  private final Map<UUID, NavigableSet<Request>> byTarget = new HashMap<>();
  private final Map<Name, Set<UUID>> byName = new HashMap<>();
  private final Map<UUID, Set<UUID>> byOrigin = new HashMap<>();

  public Request get(UUID train) {
    return requests.get(train);
  }

  public void put(Request request) {
    if (request.equals(requests.get(request.train()))) return;
    remove(request.train());
    requests.put(request.train(), request);
    byName
        .computeIfAbsent(new Name(request.scope(), request.name()), k -> new HashSet<>())
        .add(request.train());
    if (request.origin() != null)
      byOrigin.computeIfAbsent(request.origin(), k -> new HashSet<>()).add(request.train());
    for (var stop : request.targets())
      byTarget.computeIfAbsent(stop, k -> new TreeSet<>(ORDER)).add(request);
  }

  public void remove(UUID train) {
    var old = requests.remove(train);
    if (old == null) return;
    var key = new Name(old.scope(), old.name());
    var named = byName.get(key);
    named.remove(train);
    if (named.isEmpty()) byName.remove(key);
    if (old.origin() != null) {
      var set = byOrigin.get(old.origin());
      set.remove(train);
      if (set.isEmpty()) byOrigin.remove(old.origin());
    }
    for (var stop : old.targets()) {
      var set = byTarget.get(stop);
      set.remove(old);
      if (set.isEmpty()) byTarget.remove(stop);
    }
  }

  /** An unfinished higher-ranked search participates just like a completed lower-ranked one. */
  public boolean first(UUID train, UUID target) {
    var set = byTarget.get(target);
    return set != null && !set.isEmpty() && set.first().train().equals(train);
  }

  /** Moving lookahead yields to already enrolled stopped requests for a new slot. */
  public boolean contested(UUID target) {
    var set = byTarget.get(target);
    return set != null && !set.isEmpty();
  }

  public void resolve(UUID train, UUID target) {
    var r = requests.get(train);
    if (r == null || !r.targets().contains(target))
      throw new IllegalArgumentException("Dispatch target");
    put(
        new Request(
            r.train(),
            r.scope(),
            r.name(),
            r.origin(),
            r.priority(),
            r.order(),
            Set.of(target),
            true));
  }

  public void priority(UUID origin, int priority) {
    for (var train : Set.copyOf(byOrigin.getOrDefault(origin, Set.of()))) {
      var r = requests.get(train);
      put(
          new Request(
              r.train(),
              r.scope(),
              r.name(),
              r.origin(),
              priority,
              r.order(),
              r.targets(),
              r.resolved()));
    }
  }

  /** Called for affected names on device changes, never every train every tick. */
  public void targets(String scope, String name, Set<UUID> targets) {
    for (var train : Set.copyOf(byName.getOrDefault(new Name(scope, name), Set.of()))) {
      var r = requests.get(train);
      var next = r.resolved() ? new HashSet<>(r.targets()) : new HashSet<>(targets);
      if (r.resolved()) next.retainAll(targets);
      put(
          new Request(
              r.train(),
              r.scope(),
              r.name(),
              r.origin(),
              r.priority(),
              r.order(),
              next,
              r.resolved()));
    }
  }
}
