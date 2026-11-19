package factorycraft.simulation.trains;

import java.util.*;

/** Per-car staged oriented samples. Bounds discover candidates; SAT decides real conflicts. */
public record SweptHull(List<StockEnvelope> samples, SpatialReservations.Bounds bounds) {
  public SweptHull(List<StockEnvelope> samples) {
    this(List.copyOf(samples), boundsOf(samples));
  }

  public SweptHull {
    if (samples.isEmpty() || samples.size() > 128)
      throw new IllegalArgumentException("Sweep samples");
  }

  private static SpatialReservations.Bounds boundsOf(List<StockEnvelope> list) {
    if (list.isEmpty()) throw new IllegalArgumentException("Empty sweep");
    double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0, x1 = -x0, y1 = -x0, z1 = -x0;
    for (var s : list) {
      var b = s.bounds();
      x0 = Math.min(x0, b.x0());
      y0 = Math.min(y0, b.y0());
      z0 = Math.min(z0, b.z0());
      x1 = Math.max(x1, b.x1());
      y1 = Math.max(y1, b.y1());
      z1 = Math.max(z1, b.z1());
    }
    return new SpatialReservations.Bounds(x0, y0, z0, x1, y1, z1);
  }

  public boolean intersects(SweptHull other) {
    if (!bounds.intersects(other.bounds)) return false;
    for (var a : samples) for (var b : other.samples) if (a.intersects(b)) return true;
    return false;
  }
}
