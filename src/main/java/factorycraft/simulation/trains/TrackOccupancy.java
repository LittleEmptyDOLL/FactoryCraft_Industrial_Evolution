package factorycraft.simulation.trains;

import java.util.*;

/** Atomic edge-interval reservations. No signals yet; stopped bodies retain their claims. */
public final class TrackOccupancy {
  public record Interval(UUID edge, double min, double max) {
    public Interval {
      if (edge == null || !Double.isFinite(min + max) || min < 0 || max < min)
        throw new IllegalArgumentException("Track interval");
    }

    boolean intersects(Interval b) {
      return edge.equals(b.edge) && min < b.max - 1e-7 && b.min < max - 1e-7;
    }
  }

  private record Claim(UUID train, Interval interval) {}

  private final Map<UUID, List<Claim>> byEdge = new HashMap<>();
  private final Map<UUID, List<Interval>> byTrain = new HashMap<>();

  public boolean canReserve(UUID train, List<Interval> wanted) {
    for (int i = 0; i < wanted.size(); i++)
      for (int j = i + 1; j < wanted.size(); j++)
        if (wanted.get(i).intersects(wanted.get(j))) return false;
    for (var interval : wanted)
      for (var old : byEdge.getOrDefault(interval.edge, List.of()))
        if (!old.train.equals(train) && old.interval.intersects(interval)) return false;
    return true;
  }

  /** Validate a merge before either old owner is retired, including loop self-overlap. */
  public boolean canReserveMerged(UUID first, UUID second, List<Interval> wanted) {
    for (int i = 0; i < wanted.size(); i++)
      for (int j = i + 1; j < wanted.size(); j++)
        if (wanted.get(i).intersects(wanted.get(j))) return false;
    for (var interval : wanted)
      for (var old : byEdge.getOrDefault(interval.edge, List.of()))
        if (!old.train.equals(first)
            && !old.train.equals(second)
            && old.interval.intersects(interval)) return false;
    return true;
  }

  public boolean reserve(UUID train, List<Interval> wanted) {
    if (!canReserve(train, wanted)) return false;
    var copy = List.copyOf(wanted);
    release(train);
    byTrain.put(train, copy);
    for (var interval : copy)
      byEdge.computeIfAbsent(interval.edge, k -> new ArrayList<>()).add(new Claim(train, interval));
    return true;
  }

  public void release(UUID train) {
    var old = byTrain.remove(train);
    if (old == null) return;
    for (var interval : old) {
      var claims = byEdge.get(interval.edge);
      if (claims == null) continue;
      claims.removeIf(c -> c.train.equals(train));
      if (claims.isEmpty()) byEdge.remove(interval.edge);
    }
  }

  public Set<UUID> owners(UUID edge) {
    var out = new HashSet<UUID>();
    for (var c : byEdge.getOrDefault(edge, List.of())) out.add(c.train);
    return Set.copyOf(out);
  }

  public List<Interval> intervals(UUID train) {
    return byTrain.getOrDefault(train, List.of());
  }

  /** Distance from the leading body to the first other claim along this historical route. */
  public double freeDistance(
      UUID train, TrainPath path, double rear, double front, int direction, double limit) {
    double free = limit;
    for (var leg : path.legs())
      for (var claim : byEdge.getOrDefault(leg.edge().edge(), List.of())) {
        if (claim.train.equals(train)) continue;
        var i = claim.interval;
        double a = leg.begin() + (leg.edge().reverse() ? leg.length() - i.max : i.min),
            b = leg.begin() + (leg.edge().reverse() ? leg.length() - i.min : i.max);
        if (direction > 0 && b > front + 1e-7) free = Math.min(free, Math.max(0, a - front));
        if (direction < 0 && a < rear - 1e-7) free = Math.min(free, Math.max(0, rear - b));
      }
    return free;
  }

  public static List<Interval> span(TrainPath path, double min, double max) {
    if (min > max) {
      double t = min;
      min = max;
      max = t;
    }
    var out = new ArrayList<Interval>();
    var legs = path.legs();
    int first = path.indexAt(min);
    for (int at = Math.max(0, first); at < legs.size(); at++) {
      var leg = legs.get(at);
      if (leg.begin() > max + 1e-8) break;
      double a = Math.max(min, leg.begin()), b = Math.min(max, leg.end());
      if (a >= b - 1e-8) continue;
      // Route coordinates include an accumulated origin. Subtracting it from begin + length
      // can round beyond length; reverse mapping would then create a negative track position.
      // Preserve exact clipped endpoints and bound only this global-to-local conversion.
      // Interval itself remains strict: malformed claims must not be silently accepted.
      double lo = a == leg.begin() ? 0 : Math.clamp(a - leg.begin(), 0, leg.length()),
          hi = b == leg.end() ? leg.length() : Math.clamp(b - leg.begin(), 0, leg.length());
      if (leg.edge().reverse()) {
        double t = lo;
        lo = leg.length() - hi;
        hi = leg.length() - t;
      }
      out.add(new Interval(leg.edge().edge(), lo, hi));
    }
    return List.copyOf(out);
  }
}
