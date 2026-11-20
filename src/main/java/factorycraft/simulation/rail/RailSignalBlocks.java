package factorycraft.simulation.rail;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Fractional signal-region flood adapted from Create SignalPropagator.walkSignals (MIT). Copyright
 * (c) The Create Team / The Creators of Create. Upstream37ea4afc; full notice in
 * THIRD_PARTY_NOTICES.md. Existing physical edges are never split or copied. The local flood and
 * publication both consume a caller budget; unfinished components cannot be used for movement.
 */
public final class RailSignalBlocks {
  public record Span(UUID id, UUID edge, double from, double to) {}

  public static final class Region {
    public final UUID id;
    public final List<Span> spans;
    private boolean ready;

    Region(List<Span> spans) {
      this.spans = List.copyOf(spans);
      id = spans.stream().map(Span::id).min(UUID::compareTo).orElseThrow();
    }

    public boolean ready() {
      return ready;
    }
  }

  private final RailGraph graph;
  private final RailEdgePoints points;
  private final Map<UUID, List<Span>> edges = new HashMap<>();
  private final Map<UUID, Span> spans = new HashMap<>();
  private final Map<UUID, Region> membership = new HashMap<>();
  private final Map<UUID, Region> regions = new HashMap<>();
  private final LinkedHashSet<UUID> pending = new LinkedHashSet<>();
  private final LinkedHashSet<UUID> invalidatedEdges = new LinkedHashSet<>();
  private Job job;
  private long revision, visited;

  private static final class Job {
    final ArrayDeque<Span> frontier = new ArrayDeque<>();
    final LinkedHashMap<UUID, Span> found = new LinkedHashMap<>();
    Region publishing;
    int cursor;

    Job(Span first) {
      frontier.add(first);
      found.put(first.id(), first);
    }
  }

  public RailSignalBlocks(RailGraph graph, RailEdgePoints points) {
    this.graph = graph;
    this.points = points;
  }

  public long revision() {
    return revision;
  }

  public long visited() {
    return visited;
  }

  public boolean busy() {
    return job != null || !pending.isEmpty();
  }

  public List<Span> spans(UUID edge) {
    return edges.getOrDefault(edge, List.of());
  }

  public Region region(UUID id) {
    var value = regions.get(id);
    return value != null && value.ready ? value : null;
  }

  /** Old component edges, for adapters to reconcile only indexed affected train owners. */
  public Set<UUID> takeInvalidatedEdges() {
    var result = Set.copyOf(invalidatedEdges);
    invalidatedEdges.clear();
    return result;
  }

  private boolean current(Span s) {
    return spans.get(s.id()) == s;
  }

  private void invalidate(Span s) {
    var region = membership.get(s.id());
    if (region != null && region.ready) {
      region.ready = false;
      regions.remove(region.id, region);
      for (var member : region.spans) {
        if (current(member)) pending.add(member.id());
        invalidatedEdges.add(member.edge());
      }
    } else if (current(s)) pending.add(s.id());
  }

  private List<Span> neighbors(Span s) {
    var edge = graph.edge(s.edge());
    if (edge == null) return List.of();
    var result = new ArrayList<Span>();
    if (s.from() == 0) neighborsAt(edge.curve().start, s.edge(), result);
    if (s.to() == edge.curve().length()) neighborsAt(edge.curve().end, s.edge(), result);
    return result;
  }

  private void neighborsAt(RailEnd end, UUID ownEdge, List<Span> out) {
    for (var id : graph.at(end)) {
      if (id.equals(ownEdge)) continue;
      var edge = graph.edge(id);
      var parts = spans(id);
      if (edge == null || parts.isEmpty()) continue;
      if (edge.curve().start.samePosition(end)) out.add(parts.getFirst());
      if (edge.curve().end.samePosition(end)) out.add(parts.getLast());
    }
  }

  /** Call only for physical adjacency or signal-boundary edits, never volume/occupancy changes. */
  public void changed(UUID edgeId) {
    if (job != null) {
      // Abort publication atomically; no partly published component becomes ready.
      for (var s : job.found.values()) if (current(s)) pending.add(s.id());
      job = null;
    }
    var old = spans(edgeId);
    for (var s : old) {
      invalidate(s);
      for (var neighbor : neighbors(s)) invalidate(neighbor);
    }
    for (var s : old) {
      spans.remove(s.id());
      membership.remove(s.id());
      pending.remove(s.id());
    }
    edges.remove(edgeId);
    var edge = graph.edge(edgeId);
    if (edge != null) {
      var cuts = new TreeSet<Double>();
      cuts.add(0.0);
      cuts.add(edge.curve().length());
      for (var point : points.on(edgeId))
        if (point.boundary()
            && point.offset() > 1e-7
            && point.offset() < edge.curve().length() - 1e-7) cuts.add(point.offset());
      var parts = new ArrayList<Span>();
      Double last = null;
      for (double cut : cuts) {
        if (last != null) {
          var id =
              UUID.nameUUIDFromBytes(
                  (edgeId + "/" + Double.toHexString(last) + "/" + Double.toHexString(cut))
                      .getBytes(StandardCharsets.UTF_8));
          var span = new Span(id, edgeId, last, cut);
          parts.add(span);
          spans.put(id, span);
          pending.add(id);
        }
        last = cut;
      }
      edges.put(edgeId, List.copyOf(parts));
      for (var s : parts) for (var neighbor : neighbors(s)) invalidate(neighbor);
    }
    invalidatedEdges.add(edgeId);
    revision++;
  }

  public int advance(int budget) {
    if (budget < 0) throw new IllegalArgumentException("Budget");
    int work = 0;
    while (work < budget) {
      if (job == null) {
        if (pending.isEmpty()) break;
        var id = pending.iterator().next();
        pending.remove(id);
        var span = spans.get(id);
        var region = membership.get(id);
        if (span == null || region != null && region.ready) continue;
        job = new Job(span);
      }
      if (job.publishing != null) {
        if (job.cursor < job.publishing.spans.size()) {
          var span = job.publishing.spans.get(job.cursor++);
          membership.put(span.id(), job.publishing);
          work++;
          continue;
        }
        job.publishing.ready = true;
        regions.put(job.publishing.id, job.publishing);
        job = null;
        revision++;
        continue;
      }
      if (job.frontier.isEmpty()) {
        job.publishing = new Region(new ArrayList<>(job.found.values()));
        continue;
      }
      var span = job.frontier.removeFirst();
      pending.remove(span.id());
      work++;
      visited++;
      for (var next : neighbors(span)) {
        var previous = membership.get(next.id());
        if (previous != null && previous.ready) invalidate(next);
        if (job.found.putIfAbsent(next.id(), next) == null) job.frontier.addLast(next);
      }
    }
    return work;
  }

  public Region at(UUID edge, double offset, boolean after) {
    var all = spans(edge);
    if (all.isEmpty()) return null;
    double p = Math.clamp(offset + (after ? 1e-8 : -1e-8), 0, all.getLast().to());
    int lo = 0, hi = all.size() - 1;
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (p >= all.get(mid).to()) lo = mid + 1;
      else hi = mid;
    }
    var region = membership.get(all.get(lo).id());
    return region != null && region.ready ? region : null;
  }

  /** Null means unknown, never an empty set that would authorize entry during a rebuild. */
  public Set<UUID> covering(UUID edge, double from, double to) {
    var all = spans(edge);
    if (all.isEmpty()) return null;
    var result = new HashSet<UUID>();
    for (var s : all) {
      if (s.to() < from + 1e-8 || s.from() > to - 1e-8) continue;
      var region = membership.get(s.id());
      if (region == null || !region.ready) return null;
      result.add(region.id);
    }
    return Set.copyOf(result);
  }
}
