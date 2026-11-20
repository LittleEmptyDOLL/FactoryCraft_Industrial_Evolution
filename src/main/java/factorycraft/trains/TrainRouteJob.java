package factorycraft.trains;

import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;

/**
 * Bounded two-ended dispatch. Source speed/pose stay stopped until a complete actual route commits.
 */
public final class TrainRouteJob {
  private record Candidate(int direction, TrainPath.Leg start, RailPathSearch search) {}

  public record Result(int direction, TrainPath path, double targetHead, UUID destination) {}

  private final List<Candidate> candidates = new ArrayList<>();
  private final TrainPath originalPath;
  private final double originalHead;
  private final long graphRevision, rulesRevision;
  private int cursor;
  public boolean done, stale, limited;

  public TrainRouteJob(
      TrainData d,
      TrainData.Train t,
      RailAutomationData system,
      List<RailPathSearch.Target> targets) {
    originalPath = t.path;
    originalHead = t.head;
    graphRevision = system.rails.graph.revisions();
    rulesRevision = system.revision;
    for (int direction : new int[] {1, -1}) {
      var leading = t.cars.get(direction > 0 ? 0 : t.cars.size() - 1);
      if (leading.kind != StockSpec.LOCOMOTIVE || (leading.reversed ? -1 : 1) != direction)
        continue;
      double nose = direction > 0 ? t.front() : t.rear();
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
              (edge, from, to) -> TrainAutomation.routePenalty(d, t, system, edge, from, to),
              16384);
      candidates.add(new Candidate(direction, start, search));
    }
    done = candidates.isEmpty();
  }

  public int advance(TrainData.Train t, RailAutomationData system, int budget) {
    if (done) return 0;
    if (t.path != originalPath
        || t.head != originalHead
        || t.speed != 0
        || graphRevision != system.rails.graph.revisions()
        || rulesRevision != system.revision) {
      done = stale = true;
      return 0;
    }
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
    return new Result(
        best.direction,
        path,
        originalHead + best.direction * route.distance(),
        route.target().id());
  }
}
