package factorycraft.simulation.circuits;

import java.util.*;
import java.util.function.Consumer;

/**
 * Bounded components, cached totals, local batched rebuilds. No world or global factory tick scan.
 */
public final class CircuitGraph<K, S> {
  public static final class Port<K, S> {
    public final K key;
    private final Set<Port<K, S>> edges = new LinkedHashSet<>();
    private Bus<K, S> bus;
    private Map<S, Integer> output = Map.of();
    private boolean present = true, complete = true;

    private Port(K key) {
      this.key = key;
    }
  }

  private static final class Bus<K, S> {
    final Set<Port<K, S>> ports = new LinkedHashSet<>();
    final Map<S, Integer> signals = new HashMap<>();
    boolean valid = true, complete = true;
    Map<S, Integer> snapshot;
  }

  private final Map<K, Port<K, S>> ports = new LinkedHashMap<>();
  private final LinkedHashSet<Port<K, S>> dirty = new LinkedHashSet<>();
  private final int maxNodes, maxDegree;
  private final Consumer<K> changed;
  private long rebuilds, visits;

  public CircuitGraph(int maxNodes, int maxDegree, Consumer<K> changed) {
    if (maxNodes < 2 || maxDegree < 1) throw new IllegalArgumentException();
    this.maxNodes = maxNodes;
    this.maxDegree = maxDegree;
    this.changed = changed;
  }

  public Port<K, S> add(K key) {
    return ports.computeIfAbsent(
        key,
        k -> {
          var p = new Port<K, S>(k);
          var b = new Bus<K, S>();
          p.bus = b;
          b.ports.add(p);
          return p;
        });
  }

  public Port<K, S> get(K key) {
    return ports.get(key);
  }

  private void notifyBus(Bus<K, S> bus) {
    for (var p : bus.ports) if (p.present) changed.accept(p.key);
  }

  private void invalidate(Port<K, S> p) {
    var b = p.bus;
    if (b.valid) {
      b.valid = false;
      for (var n : b.ports) if (n.present) dirty.add(n);
      notifyBus(b);
    }
    if (p.present) dirty.add(p);
  }

  public void remove(K key) {
    var p = ports.remove(key);
    if (p == null) return;
    invalidate(p);
    p.present = false;
    dirty.remove(p);
    for (var n : List.copyOf(p.edges)) {
      n.edges.remove(p);
      dirty.add(n);
    }
    p.edges.clear();
  }

  public boolean connect(K a, K b) {
    var x = ports.get(a);
    var y = ports.get(b);
    if (x == null || y == null || x == y) return false;
    if (x.edges.contains(y)) return true;
    if (x.edges.size() >= maxDegree || y.edges.size() >= maxDegree) return false;
    var seen = new HashSet<Port<K, S>>();
    var queue = new ArrayDeque<Port<K, S>>();
    queue.add(x);
    queue.add(y);
    while (!queue.isEmpty()) {
      var p = queue.removeFirst();
      if (!seen.add(p)) continue;
      if (seen.size() > maxNodes) return false;
      queue.addAll(p.edges);
    }
    invalidate(x);
    invalidate(y);
    x.edges.add(y);
    y.edges.add(x);
    return true;
  }

  public boolean linked(K a, K b) {
    var x = ports.get(a);
    var y = ports.get(b);
    return x != null && y != null && x.edges.contains(y);
  }

  public void disconnect(K a, K b) {
    var x = ports.get(a);
    var y = ports.get(b);
    if (x == null || y == null || !x.edges.contains(y)) return;
    invalidate(x);
    invalidate(y);
    x.edges.remove(y);
    y.edges.remove(x);
  }

  public void complete(K key, boolean complete) {
    var p = ports.get(key);
    if (p == null || p.complete == complete) return;
    invalidate(p);
    p.complete = complete;
  }

  public boolean valid(K key) {
    var p = ports.get(key);
    return p != null && p.present && p.bus.valid && p.bus.complete && p.bus.signals.size() <= 4096;
  }

  public int value(K key, S signal) {
    var p = ports.get(key);
    return valid(key) ? p.bus.signals.getOrDefault(signal, 0) : 0;
  }

  public Map<S, Integer> signals(K key) {
    var p = ports.get(key);
    if (!valid(key)) return Map.of();
    if (p.bus.snapshot == null) p.bus.snapshot = Map.copyOf(p.bus.signals);
    return p.bus.snapshot;
  }

  public void output(K key, S signal, int value) {
    output(key, signal == null || value == 0 ? Map.of() : Map.of(signal, value));
  }

  public void output(K key, Map<S, Integer> values) {
    var p = ports.get(key);
    if (p == null || p.output.equals(values)) return;
    var clean = new HashMap<S, Integer>();
    values.forEach(
        (k, v) -> {
          Objects.requireNonNull(k);
          if (v != 0) clean.put(k, v);
        });
    if (clean.size() > 4096) throw new IllegalArgumentException("Signal capacity exceeded");
    if (p.output.equals(clean)) return;
    var b = p.bus;
    if (b.valid) {
      p.output.forEach((k, v) -> add(b.signals, k, -v));
      clean.forEach((k, v) -> add(b.signals, k, v));
      b.snapshot = null;
    }
    p.output = Map.copyOf(clean);
    if (b.valid) notifyBus(b);
  }

  private static <S> void add(Map<S, Integer> values, S key, int amount) {
    int next = values.getOrDefault(key, 0) + amount;
    if (next == 0) values.remove(key);
    else values.put(key, next);
  }

  public int rebuild(int componentBudget) {
    int groups = 0;
    while (groups < componentBudget && !dirty.isEmpty()) {
      var start = dirty.iterator().next();
      if (!start.present) {
        dirty.remove(start);
        continue;
      }
      var b = new Bus<K, S>();
      var queue = new ArrayDeque<Port<K, S>>();
      queue.add(start);
      while (!queue.isEmpty()) {
        var p = queue.removeFirst();
        if (!b.ports.add(p)) continue;
        visits++;
        if (b.ports.size() > maxNodes)
          throw new IllegalStateException("Oversized circuit component");
        for (var n : p.edges) {
          visits++;
          if (n.present && !b.ports.contains(n)) queue.addLast(n);
        }
      }
      for (var p : b.ports) {
        p.bus = b;
        dirty.remove(p);
        b.complete &= p.complete;
        p.output.forEach((k, v) -> add(b.signals, k, v));
      }
      rebuilds++;
      groups++;
      notifyBus(b);
    }
    return groups;
  }

  public boolean pending() {
    return !dirty.isEmpty();
  }

  public long rebuilds() {
    return rebuilds;
  }

  public long visits() {
    return visits;
  }
}
