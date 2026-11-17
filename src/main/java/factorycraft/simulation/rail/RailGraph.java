package factorycraft.simulation.rail;

import java.util.*;

/**
 * Saved edges are independent of loaded availability. Incidence changes touch only their endpoints.
 */
public final class RailGraph {
  public record Edge(UUID id, RailCurve curve) {}

  public record Directed(UUID edge, boolean reverse) {}

  private final Map<UUID, Edge> edges = new LinkedHashMap<>();
  private final Map<String, LinkedHashSet<UUID>> nodes = new HashMap<>();
  private long revisions;

  public void add(Edge e) {
    if (edges.containsKey(e.id())) throw new IllegalArgumentException("Duplicate edge");
    edges.put(e.id(), e);
    for (var end : List.of(e.curve.start, e.curve.end))
      nodes.computeIfAbsent(end.nodeKey(), k -> new LinkedHashSet<>()).add(e.id());
    revisions++;
  }

  public Edge remove(UUID id) {
    var e = edges.remove(id);
    if (e != null) {
      for (var end : List.of(e.curve.start, e.curve.end)) {
        var n = nodes.get(end.nodeKey());
        n.remove(id);
        if (n.isEmpty()) nodes.remove(end.nodeKey());
      }
      revisions++;
    }
    return e;
  }

  public Collection<Edge> edges() {
    return Collections.unmodifiableCollection(edges.values());
  }

  public Edge edge(UUID id) {
    return edges.get(id);
  }

  public long revisions() {
    return revisions;
  }

  public int nodes() {
    return nodes.size();
  }

  public Set<UUID> at(RailEnd end) {
    var set = nodes.get(end.nodeKey());
    return set == null ? Set.of() : Collections.unmodifiableSet(set);
  }

  public List<Directed> successors(Directed from) {
    var e = edges.get(from.edge());
    if (e == null) return List.of();
    var exit = from.reverse() ? e.curve.start.reverse() : e.curve.end;
    var out = new ArrayList<Directed>();
    for (var id : at(exit)) {
      if (id.equals(e.id())) continue;
      var next = edges.get(id);
      if (next.curve.start.samePosition(exit) && next.curve.start.heading().equals(exit.heading()))
        out.add(new Directed(id, false));
      if (next.curve.end.samePosition(exit)
          && next.curve.end.heading().reverse().equals(exit.heading()))
        out.add(new Directed(id, true));
    }
    return List.copyOf(out);
  }

  public static boolean joined(RailCurve a, RailCurve b) {
    for (var x : List.of(a.start, a.end))
      for (var y : List.of(b.start, b.end))
        if (x.samePosition(y) && x.heading().index() % 8 == y.heading().index() % 8) return true;
    return false;
  }

  /** Explicit short approaches, including several tiny edges of one continuous track. */
  public Set<UUID> approaches(RailCurve curve, double distance) {
    record Visit(RailEnd node, double remaining) {}
    var queue = new ArrayDeque<Visit>();
    queue.add(new Visit(curve.start, distance));
    queue.add(new Visit(curve.end, distance));
    var visited = new HashMap<String, Double>();
    var result = new HashSet<UUID>();
    while (!queue.isEmpty()) {
      var v = queue.removeFirst();
      if (visited.getOrDefault(v.node.nodeKey(), -1.0) >= v.remaining - 1e-8) continue;
      if (visited.size() > 1024) throw new IllegalArgumentException("junction");
      visited.put(v.node.nodeKey(), v.remaining);
      for (var id : at(v.node)) {
        result.add(id);
        var e = edges.get(id);
        var next = e.curve.start.samePosition(v.node) ? e.curve.end : e.curve.start;
        double left = v.remaining - e.curve.length();
        if (left > 0) queue.addLast(new Visit(next, left));
      }
    }
    return Set.copyOf(result);
  }

  public static boolean nearEnd(RailCurve curve, RailVector p, double distance) {
    return p.distance(curve.start.point()) <= distance || p.distance(curve.end.point()) <= distance;
  }

  /** Same-level unrelated crossings conflict; true overpasses remain independent. */
  public static boolean conflicts(RailCurve a, RailCurve b) {
    return conflicts(a, b, false);
  }

  public static boolean conflicts(RailCurve a, RailCurve b, boolean explicitApproach) {
    if (a.start.samePosition(b.start) && a.end.samePosition(b.end)
        || a.start.samePosition(b.end) && a.end.samePosition(b.start)) return true;
    for (var x : List.of(a.start, a.end))
      for (var y : List.of(b.start, b.end))
        if (x.samePosition(y)) {
          var av =
              x == a.start
                  ? a.sample(Math.min(8, a.length()), false).position()
                  : a.sample(Math.min(8, a.length()), true).position();
          var bv =
              y == b.start
                  ? b.sample(Math.min(8, b.length()), false).position()
                  : b.sample(Math.min(8, b.length()), true).position();
          if (av.distance(bv) < .25) return true;
        }
    boolean joint = joined(a, b);
    double spacing = joint ? 1.8 : 3;
    for (double x = 0; x <= a.length(); x += .75) {
      var p = a.sample(x, false).position();
      for (double y = 0; y <= b.length(); y += .75) {
        var q = b.sample(y, false).position();
        if (Math.abs(p.y() - q.y()) >= 4.5) continue;
        if (Math.hypot(p.x() - q.x(), p.z() - q.z()) < spacing) {
          if (joint && nearJoint(a, b, p, q)
              || explicitApproach && nearEnd(a, p, 8) && nearEnd(a, q, 8)) continue;
          return true;
        }
      }
    }
    return false;
  }

  private static boolean nearJoint(RailCurve a, RailCurve b, RailVector p, RailVector q) {
    for (var x : List.of(a.start, a.end))
      for (var y : List.of(b.start, b.end))
        if (x.samePosition(y)
            && x.heading().index() % 8 == y.heading().index() % 8
            && p.distance(x.point()) <= 24
            && q.distance(x.point()) <= 24) return true;
    return false;
  }
}
