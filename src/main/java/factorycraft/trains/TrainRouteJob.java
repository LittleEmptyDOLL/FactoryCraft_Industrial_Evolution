package factorycraft.trains;

import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;

/**
 * Bounded two-ended stopped dispatch or direction-locked future continuation. Only complete routes
 * commit.
 */
public final class TrainRouteJob {
  private record Candidate(int direction, TrainPath.Leg start, RailPathSearch search) {}

  public record Result(
      int direction, TrainPath path, double targetHead, UUID destination, double cost) {}

  private final List<Candidate> candidates = new ArrayList<>();
  private final TrainPath originalPath;
  private final double originalHead;
  private final boolean continuation;
  private final int fixedDirection;
  private final long graphRevision, rulesRevision;
  private final int stepLimit;
  private Result resolved;
  private int cursor;
  public boolean done, stale, limited;

  public TrainRouteJob(
      TrainData d,
      TrainData.Train t,
      RailAutomationData system,
      List<RailPathSearch.Target> targets) {
    this(d, t, system, targets, 256);
  }

  /** Per-job cooperative allowance; shared world budget still applies. */
  public TrainRouteJob(
      TrainData d,
      TrainData.Train t,
      RailAutomationData system,
      List<RailPathSearch.Target> targets,
      int stepLimit) {
    this(d, t, system, targets, stepLimit, t.head, false);
  }

  /** Same bounded search, anchored at a future leading-nose pose in the current direction. */
  public TrainRouteJob(
      TrainData d,
      TrainData.Train t,
      RailAutomationData system,
      List<RailPathSearch.Target> targets,
      int stepLimit,
      double anchorHead,
      boolean continuation) {
    this.continuation = continuation;
    fixedDirection = t.direction;
    if (stepLimit < 1 || stepLimit > 256) throw new IllegalArgumentException("Route step budget");
    this.stepLimit = stepLimit;
    originalPath = t.path;
    originalHead = anchorHead;
    graphRevision = system.rails.graph.revisions();
    rulesRevision = system.revision;
    for (int direction : new int[] {1, -1}) {
      if (continuation && direction != fixedDirection) continue;
      var leading = t.cars.get(direction > 0 ? 0 : t.cars.size() - 1);
      if (leading.kind != StockSpec.LOCOMOTIVE || (leading.reversed ? -1 : 1) != direction)
        continue;
      double nose =
          anchorHead
              + (direction > 0
                  ? StockSpec.HALF_LENGTH
                  : -(t.cars.size() - 1) * StockSpec.SPACING - StockSpec.HALF_LENGTH);
      var start = t.path.at(nose);
      if (start == null) continue;
      var travel =
          direction > 0
              ? start.edge()
              : new RailGraph.Directed(start.edge().edge(), !start.edge().reverse());
      double offset = Math.clamp(nose - start.begin(), 0, start.length());
      if (start.edge().reverse()) offset = start.length() - offset;
      var search =
          new RailPathSearch(
              system.rails.graph,
              travel,
              offset,
              targets,
              new TrainRoutePolicy(d, t, system),
              16384,
              continuation || t.automation.departurePass ? .001 : 0);
      candidates.add(new Candidate(direction, start, search));
    }
    done = candidates.isEmpty();
  }

  public int advance(TrainData.Train t, RailAutomationData system, int budget) {
    if (t.path != originalPath
        || (continuation
            ? t.direction != fixedDirection || (t.head - originalHead) * fixedDirection > .061
            : t.head != originalHead || t.speed != 0)
        || graphRevision != system.rails.graph.revisions()
        || rulesRevision != system.revision) {
      done = stale = true;
      return 0;
    }
    if (done) return 0;
    budget = Math.min(budget, stepLimit);
    int work = 0;
    for (int attempts = 0; attempts < candidates.size() && work < budget; attempts++) {
      var c = candidates.get(cursor++ % candidates.size());
      work += c.search.advance(Math.max(1, (budget - work) / (candidates.size() - attempts)));
    }
    done = candidates.stream().noneMatch(c -> c.search.status() == RailPathSearch.Status.SEARCHING);
    limited = candidates.stream().anyMatch(c -> c.search.status() == RailPathSearch.Status.LIMIT);
    return work;
  }

  public Result result(TrainData.Train t, RailGraph graph) {
    if (!done || stale) return null;
    if (resolved != null) return resolved;
    var best =
        candidates.stream()
            .filter(c -> c.search.result() != null)
            .min(
                Comparator.<Candidate>comparingInt(c -> -c.search.result().target().priority())
                    .thenComparingDouble(c -> c.search.result().cost())
                    .thenComparing(c -> c.direction == t.direction ? 0 : 1)
                    .thenComparing(c -> c.search.result().target().id()))
            .orElse(null);
    if (best == null) return null;
    var route = best.search.result();
    var old = originalPath.legs();
    int at = old.indexOf(best.start);
    if (at < 0) return null;
    var legs = new ArrayList<TrainPath.Leg>();
    if (best.direction > 0) {
      legs.addAll(old.subList(0, at + 1));
      for (int i = 1; i < route.edges().size(); i++) {
        var e = route.edges().get(i);
        legs.add(new TrainPath.Leg(e, legs.getLast().end(), graph.edge(e.edge()).curve().length()));
      }
    } else {
      legs.addAll(old.subList(at, old.size()));
      for (int i = 1; i < route.edges().size(); i++) {
        var e = route.edges().get(i);
        double length = graph.edge(e.edge()).curve().length();
        legs.addFirst(
            new TrainPath.Leg(
                new RailGraph.Directed(e.edge(), !e.reverse()),
                legs.getFirst().begin() - length,
                length));
      }
    }
    if (legs.size() > 4096) {
      limited = true;
      return null;
    }
    var path = new TrainPath(legs);
    if (!path.connected(graph)) return null;
    return resolved =
        new Result(
            best.direction,
            path,
            originalHead + best.direction * route.distance(),
            route.target().id(),
            route.cost());
  }
}
