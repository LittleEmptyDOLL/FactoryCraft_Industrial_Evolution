package factorycraft.simulation.rail;

import java.util.*;
import java.util.function.Predicate;

/**
 * Factorio regular/chain aspects over the boundary-stopped traversal port, never a second graph.
 */
public final class RailSignals {
  public enum Aspect {
    INVALID,
    RED,
    YELLOW,
    GREEN,
    BLUE
  }

  public record View(Aspect aspect, Set<UUID> blocks, Set<UUID> signals) {}

  private record Walk(RailGraph.Directed direction, double offset) {}

  public record Exit(RailEdgePoints.Point point, boolean blocked) {}

  private final RailGraph graph;
  private final RailEdgePoints points;
  private final RailSignalBlocks blocks;
  private final RailBlockClaims claims;
  private final Predicate<RailEdgePoints.Point> online, forced;

  public RailSignals(
      RailGraph graph,
      RailEdgePoints points,
      RailSignalBlocks blocks,
      RailBlockClaims claims,
      Predicate<RailEdgePoints.Point> online,
      Predicate<RailEdgePoints.Point> forced) {
    this.graph = graph;
    this.points = points;
    this.blocks = blocks;
    this.claims = claims;
    this.online = online;
    this.forced = forced;
  }

  public RailEdgePoints.Point side(RailGraph.Directed travel, double offset) {
    for (var point : points.on(travel.edge()))
      if (point.boundary()
          && Math.abs(point.offset() - offset) < 1e-7
          && point.reverse() == travel.reverse()) return point;
    return null;
  }

  public boolean directionAllowed(RailGraph.Directed travel, double from, double to) {
    for (var point : points.ahead(travel, from, to))
      if (point.boundary() && side(travel, point.offset()) == null) return false;
    return true;
  }

  /** Reach first real cut on each legal turnout branch; a missing opposite side is not a U-turn. */
  public List<Exit> following(RailEdgePoints.Point source) {
    var frontier = new ArrayDeque<Walk>();
    frontier.add(new Walk(source.travel(), source.offset()));
    var seen = new HashSet<Walk>();
    var result = new LinkedHashMap<String, Exit>();
    int work = 0;
    while (!frontier.isEmpty()) {
      if (++work > 4096) return List.of(new Exit(null, true));
      var walk = frontier.removeFirst();
      // The partial initial edge can be revisited after a real loop.
      if (!seen.add(walk)) continue;
      var edge = graph.edge(walk.direction().edge());
      if (edge == null) {
        result.put("missing", new Exit(null, true));
        continue;
      }
      double end = walk.direction().reverse() ? 0 : edge.curve().length();
      var ahead = points.ahead(walk.direction(), walk.offset(), end);
      var cut = ahead.stream().filter(RailEdgePoints.Point::boundary).findFirst().orElse(null);
      if (cut != null) {
        var signal = side(walk.direction(), cut.offset());
        result.put(
            cut.edge() + "/" + cut.offset() + "/" + walk.direction().reverse(),
            new Exit(signal, signal == null));
        continue;
      }
      var next = graph.successors(walk.direction());
      if (next.isEmpty()) result.put("end/" + walk.direction(), new Exit(null, true));
      for (var direction : next) {
        var following = graph.edge(direction.edge());
        double offset = direction.reverse() ? following.curve().length() : 0;
        // Signals may sit exactly on a node in imported data; do not skip a direction barrier.
        boolean cutAtEntry =
            points.on(direction.edge()).stream()
                .anyMatch(p -> p.boundary() && Math.abs(p.offset() - offset) < 1e-7);
        if (cutAtEntry) {
          var signal = side(direction, offset);
          result.put(direction.toString(), new Exit(signal, signal == null));
        } else frontier.addLast(new Walk(direction, offset));
      }
    }
    return List.copyOf(result.values());
  }

  public View view(RailEdgePoints.Point source) {
    var dependencies = new HashSet<UUID>();
    var signals = new HashSet<UUID>();
    var aspect = aspect(source, new HashSet<>(), dependencies, signals, new int[] {4096});
    return new View(aspect, Set.copyOf(dependencies), Set.copyOf(signals));
  }

  private Aspect aspect(
      RailEdgePoints.Point source,
      Set<UUID> path,
      Set<UUID> dependencies,
      Set<UUID> signals,
      int[] budget) {
    if (--budget[0] < 0 || source == null || !source.boundary() || !online.test(source))
      return Aspect.INVALID;
    signals.add(source.id());
    var entry = blocks.at(source.edge(), source.offset(), source.reverse());
    var target = blocks.at(source.edge(), source.offset(), !source.reverse());
    if (entry == null || target == null || entry.id.equals(target.id)) return Aspect.INVALID;
    dependencies.add(entry.id);
    dependencies.add(target.id);
    if (!claims.occupants(target.id).isEmpty()) return Aspect.RED;
    if (claims.reservation(target.id) != null) return Aspect.YELLOW;
    if (forced.test(source)) return Aspect.RED;
    if (source.kind() == RailEdgePoints.Kind.SIGNAL) return Aspect.GREEN;
    if (!path.add(source.id())) return Aspect.RED; // cyclic chains have no proved safe exit
    int open = 0, shut = 0;
    for (var exit : following(source)) {
      var state =
          exit.blocked() ? Aspect.RED : aspect(exit.point(), path, dependencies, signals, budget);
      if (state == Aspect.INVALID) {
        path.remove(source.id());
        return Aspect.INVALID;
      }
      if (state == Aspect.GREEN) open++;
      else if (state == Aspect.BLUE) {
        open++;
        shut++;
      } else shut++;
    }
    path.remove(source.id());
    return open == 0 ? Aspect.RED : shut == 0 ? Aspect.GREEN : Aspect.BLUE;
  }
}
