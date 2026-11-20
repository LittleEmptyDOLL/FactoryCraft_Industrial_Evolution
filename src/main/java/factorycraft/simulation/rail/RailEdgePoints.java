package factorycraft.simulation.rail;

import java.util.*;

/**
 * Ordered edge-point indexing adapted from Create graph/EdgeData.java (MIT). Copyright (c) The
 * Create Team / The Creators of Create. Upstream 37ea4afcfe5bf85875d4b0749798a45330070a1f; see
 * THIRD_PARTY_NOTICES.md. Uses FactoryCraft UUID edges, immutable views, binary lookup and explicit
 * reverse traversal.
 */
public final class RailEdgePoints {
  public enum Kind {
    SIGNAL,
    CHAIN,
    STOP
  }

  public record Point(UUID id, UUID edge, double offset, boolean reverse, Kind kind) {
    public Point {
      Objects.requireNonNull(id);
      Objects.requireNonNull(edge);
      Objects.requireNonNull(kind);
      if (!Double.isFinite(offset) || offset < 0) throw new IllegalArgumentException("Rail point");
    }

    public boolean boundary() {
      return kind != Kind.STOP;
    }

    public RailGraph.Directed travel() {
      return new RailGraph.Directed(edge, reverse);
    }
  }

  private static final Comparator<Point> ORDER =
      Comparator.comparingDouble(Point::offset).thenComparing(Point::id);
  private final Map<UUID, Point> points = new LinkedHashMap<>();
  private final Map<UUID, List<Point>> edges = new HashMap<>();

  public int size() {
    return points.size();
  }

  private long revision;

  public Point get(UUID id) {
    return points.get(id);
  }

  /** One physical cut per metric position; paired sides remain distinct logical signals. */
  public List<Point> cutsAhead(RailGraph.Directed travel, double from, double to) {
    var out = new ArrayList<Point>();
    double last = Double.NaN;
    for (var point : ahead(travel, from, to))
      if (point.boundary() && !(Math.abs(point.offset() - last) < 1e-7)) {
        out.add(point);
        last = point.offset();
      }
    return List.copyOf(out);
  }

  public long revision() {
    return revision;
  }

  public Collection<Point> all() {
    return Collections.unmodifiableCollection(points.values());
  }

  public List<Point> on(UUID edge) {
    return edges.getOrDefault(edge, List.of());
  }

  public void put(Point point, RailGraph graph) {
    var edge = graph.edge(point.edge());
    if (edge == null || point.offset() > edge.curve().length())
      throw new IllegalArgumentException("Point outside saved edge");
    var old = points.get(point.id());
    if (point.equals(old)) return;
    if (on(point.edge()).size() - (old != null && old.edge().equals(point.edge()) ? 1 : 0) >= 256)
      throw new IllegalArgumentException("Edge point capacity");
    if (old != null) remove(old.id());
    var ordered = new ArrayList<>(on(point.edge()));
    int i = Collections.binarySearch(ordered, point, ORDER);
    ordered.add(i < 0 ? -i - 1 : i, point);
    edges.put(point.edge(), List.copyOf(ordered));
    points.put(point.id(), point);
    revision++;
  }

  public Point remove(UUID id) {
    var old = points.remove(id);
    if (old == null) return null;
    var ordered = new ArrayList<>(on(old.edge()));
    ordered.remove(old);
    if (ordered.isEmpty()) edges.remove(old.edge());
    else edges.put(old.edge(), List.copyOf(ordered));
    revision++;
    return old;
  }

  /** Strictly ahead, like Create EdgeData.next; paired signals at one cut are all returned. */
  public List<Point> ahead(RailGraph.Directed direction, double offset, double limit) {
    var all = on(direction.edge());
    int lo = 0, hi = all.size();
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (all.get(mid).offset() <= offset + 1e-8) lo = mid + 1;
      else hi = mid;
    }
    var out = new ArrayList<Point>();
    if (!direction.reverse()) {
      for (int i = lo; i < all.size() && all.get(i).offset() <= limit + 1e-8; i++)
        out.add(all.get(i));
    } else {
      int i = lo - 1;
      while (i >= 0 && all.get(i).offset() >= offset - 1e-8) i--;
      for (; i >= 0 && all.get(i).offset() >= limit - 1e-8; i--) out.add(all.get(i));
    }
    return List.copyOf(out);
  }

  public Point next(RailGraph.Directed direction, double offset, Kind kind) {
    for (var p : ahead(direction, offset, direction.reverse() ? 0 : Double.MAX_VALUE))
      if (p.kind() == kind && p.reverse() == direction.reverse()) return p;
    return null;
  }
}
