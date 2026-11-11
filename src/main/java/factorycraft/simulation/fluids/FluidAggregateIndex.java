package factorycraft.simulation.fluids;

import java.util.*;

/**
 * Read-only loaded-pipeline totals, independent of fluid flow/mixing. Rebuilds spend bounded
 * vertices.
 */
public final class FluidAggregateIndex<K, S> {
  public record Snapshot<S>(boolean ready, int cells, Map<S, Integer> amounts) {}

  private final class Group {
    final Map<S, Integer> totals = new HashMap<>();
    int size;
    boolean valid, building;
    Map<S, Integer> snapshot;
  }

  private final class Node {
    final K key;
    final Set<Node> edges = new LinkedHashSet<>();
    Map<S, Integer> values = Map.of();
    Group group;
    boolean active = true, removed;

    Node(K key) {
      this.key = key;
      group = new Group();
      group.valid = true;
      group.size = 1;
    }
  }

  private final class Build {
    final Node root;
    final Group group = new Group();
    final Set<Node> seen = new HashSet<>();
    final ArrayDeque<Node> queue = new ArrayDeque<>();
    final long revision;

    Build(Node root) {
      this.root = root;
      revision = FluidAggregateIndex.this.revision;
      group.building = true;
      queue.add(root);
    }
  }

  private final Map<K, Node> nodes = new HashMap<>();
  private final LinkedHashSet<Node> pending = new LinkedHashSet<>();
  private Build build;
  private long revision, visits;

  public void add(K key) {
    nodes.computeIfAbsent(key, Node::new);
  }

  private void dirty(Node node) {
    if (node == null) return;
    node.group.valid = false;
    if (node.active && !node.removed) pending.add(node);
  }

  private void topology(Node a, Node b) {
    revision++;
    dirty(a);
    dirty(b);
  }

  public void connect(K a, K b) {
    var x = nodes.get(a);
    var y = nodes.get(b);
    if (x == null || y == null || x == y || x.edges.contains(y)) return;
    x.edges.add(y);
    y.edges.add(x);
    topology(x, y);
  }

  public void disconnect(K a, K b) {
    var x = nodes.get(a);
    var y = nodes.get(b);
    if (x == null || y == null || !x.edges.remove(y)) return;
    y.edges.remove(x);
    topology(x, y);
  }

  public void active(K key, boolean active) {
    var n = nodes.get(key);
    if (n == null || n.active == active) return;
    revision++;
    dirty(n);
    for (var e : n.edges) dirty(e);
    n.active = active;
    if (active) pending.add(n);
  }

  public void remove(K key) {
    var n = nodes.remove(key);
    if (n == null) return;
    revision++;
    dirty(n);
    n.removed = true;
    n.active = false;
    pending.remove(n);
    for (var e : List.copyOf(n.edges)) {
      e.edges.remove(n);
      dirty(e);
    }
    n.edges.clear();
  }

  private void add(Map<S, Integer> totals, S key, int amount) {
    int next = totals.getOrDefault(key, 0) + amount;
    if (next == 0) totals.remove(key);
    else totals.put(key, next);
  }

  public void update(K key, Map<S, Integer> values) {
    var n = nodes.get(key);
    if (n == null || n.values.equals(values)) return;
    for (var e : n.values.entrySet()) add(n.group.totals, e.getKey(), -e.getValue());
    for (var e : values.entrySet()) add(n.group.totals, e.getKey(), e.getValue());
    n.values = Map.copyOf(values);
    n.group.snapshot = null;
  }

  public int advance(int budget) {
    int work = 0;
    while (work < budget) {
      if (build != null && build.revision != revision) {
        dirty(build.root);
        build = null;
      }
      if (build == null) {
        Node root = null;
        while (!pending.isEmpty()) {
          var n = pending.iterator().next();
          pending.remove(n);
          if (!n.removed && n.active && !n.group.valid) {
            root = n;
            break;
          }
        }
        if (root == null) break;
        build = new Build(root);
      }
      if (build.queue.isEmpty()) {
        build.group.building = false;
        build.group.valid = true;
        build = null;
        continue;
      }
      var n = build.queue.removeFirst();
      if (n.removed || !n.active || !build.seen.add(n)) continue;
      work++;
      visits++;
      n.group = build.group;
      build.group.size++;
      for (var e : n.values.entrySet()) add(build.group.totals, e.getKey(), e.getValue());
      for (var e : n.edges)
        if (!e.removed && e.active && !build.seen.contains(e)) build.queue.addLast(e);
    }
    return work;
  }

  public Snapshot<S> snapshot(K key) {
    var n = nodes.get(key);
    if (n == null || n.removed || !n.active || !n.group.valid)
      return new Snapshot<>(false, 0, Map.of());
    var g = n.group;
    if (g.snapshot == null) g.snapshot = Map.copyOf(g.totals);
    return new Snapshot<>(true, g.size, g.snapshot);
  }

  public boolean pending() {
    return build != null || !pending.isEmpty();
  }

  public long visits() {
    return visits;
  }
}
