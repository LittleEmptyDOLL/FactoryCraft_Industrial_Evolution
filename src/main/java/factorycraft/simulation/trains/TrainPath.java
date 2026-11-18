package factorycraft.simulation.trains;

import factorycraft.simulation.rail.*;
import java.util.*;

/**
 * A bounded, explicit, historical route. Never reselect the rear bogie's turnout after the head.
 */
public final class TrainPath {
  public record Leg(RailGraph.Directed edge, double begin, double length) {
    public Leg {
      if (edge == null || !Double.isFinite(begin + length) || length <= 0)
        throw new IllegalArgumentException("Route leg");
    }

    public double end() {
      return begin + length;
    }
  }

  private final ArrayList<Leg> legs = new ArrayList<>();

  public TrainPath(RailGraph graph, RailGraph.Directed start) {
    var e = graph.edge(start.edge());
    if (e == null) throw new IllegalArgumentException("Missing rail");
    legs.add(new Leg(start, 0, e.curve().length()));
  }

  public TrainPath(List<Leg> saved) {
    if (saved.isEmpty() || saved.size() > 4096) throw new IllegalArgumentException("Route size");
    for (var leg : saved) {
      if (!legs.isEmpty() && Math.abs(legs.getLast().end() - leg.begin) > 1e-7)
        throw new IllegalArgumentException("Route gap");
      legs.add(leg);
    }
  }

  public List<Leg> legs() {
    return Collections.unmodifiableList(legs);
  }

  public double min() {
    return legs.getFirst().begin;
  }

  public double max() {
    return legs.getLast().end();
  }

  public boolean contains(double coordinate) {
    return coordinate >= min() - 1e-8 && coordinate <= max() + 1e-8;
  }

  public Leg at(double coordinate) {
    if (!contains(coordinate)) return null;
    int lo = 0, hi = legs.size() - 1;
    while (lo < hi) {
      int mid = (lo + hi) / 2;
      if (coordinate > legs.get(mid).end() + 1e-9) lo = mid + 1;
      else hi = mid;
    }
    return legs.get(lo);
  }

  public RailCurve.Frame sample(RailGraph graph, double coordinate) {
    var leg = at(coordinate);
    if (leg == null) throw new IllegalArgumentException("Outside route");
    var edge = graph.edge(leg.edge.edge());
    if (edge == null) throw new IllegalArgumentException("Removed rail");
    return edge.curve()
        .sample(Math.clamp(coordinate - leg.begin, 0, leg.length), leg.edge.reverse());
  }

  /** Choose left/straight/right only at the leading end; all wheels retain the committed route. */
  public boolean extend(RailGraph graph, boolean front, int choice) {
    if (legs.size() >= 4096) return false;
    var last = front ? legs.getLast() : legs.getFirst();
    var exit = front ? last.edge : new RailGraph.Directed(last.edge.edge(), !last.edge.reverse());
    if (graph.edge(exit.edge()) == null) return false;
    var options = new ArrayList<>(graph.successors(exit));
    if (options.isEmpty()) return false;
    var incoming = graph.edge(exit.edge()).curve().sample(last.length, exit.reverse()).tangent();
    options.sort(
        Comparator.<RailGraph.Directed>comparingDouble(
                d -> {
                  var curve = graph.edge(d.edge()).curve();
                  var next =
                      curve
                          .sample(Math.min(8, curve.length()), d.reverse())
                          .position()
                          .subtract(curve.sample(0, d.reverse()).position())
                          .unit();
                  return Math.atan2(
                      incoming.x() * next.z() - incoming.z() * next.x(),
                      incoming.x() * next.x() + incoming.z() * next.z());
                })
            .thenComparing(d -> d.edge().toString()));
    RailGraph.Directed selected =
        options.get(
            choice < 0 ? 0 : choice > 0 ? options.size() - 1 : straight(graph, options, incoming));
    if (!front) selected = new RailGraph.Directed(selected.edge(), !selected.reverse());
    double length = graph.edge(selected.edge()).curve().length();
    if (front) legs.add(new Leg(selected, last.end(), length));
    else legs.addFirst(new Leg(selected, last.begin - length, length));
    return true;
  }

  private static int straight(RailGraph g, List<RailGraph.Directed> options, RailVector incoming) {
    int best = 0;
    double score = -2;
    for (int i = 0; i < options.size(); i++) {
      var d = options.get(i);
      var c = g.edge(d.edge()).curve();
      double v =
          c.sample(Math.min(8, c.length()), d.reverse())
              .position()
              .subtract(c.sample(0, d.reverse()).position())
              .unit()
              .dot(incoming);
      if (v > score) {
        score = v;
        best = i;
      }
    }
    return best;
  }

  public boolean connected(RailGraph graph) {
    for (int i = 0; i < legs.size(); i++) {
      var leg = legs.get(i);
      var edge = graph.edge(leg.edge.edge());
      if (edge == null || Math.abs(edge.curve().length() - leg.length) > 1e-6) return false;
      if (i > 0 && !graph.successors(legs.get(i - 1).edge).contains(leg.edge)) return false;
    }
    return true;
  }

  /** Intent may replace unoccupied lookahead, never the shared historical path under a car. */
  public void discardUnoccupied(double rear, double front, int direction) {
    if (direction > 0)
      while (legs.size() > 1 && legs.getLast().begin() > front + 1e-7) legs.removeLast();
    else while (legs.size() > 1 && legs.getFirst().end() < rear - 1e-7) legs.removeFirst();
  }

  /** Drop only historical legs behind all bodies and ahead of neither reservation. */
  public void trim(double rear, double front) {
    while (legs.size() > 1 && legs.getFirst().end() < rear - 8) legs.removeFirst();
    while (legs.size() > 1 && legs.getLast().begin > front + 8) legs.removeLast();
  }
}
