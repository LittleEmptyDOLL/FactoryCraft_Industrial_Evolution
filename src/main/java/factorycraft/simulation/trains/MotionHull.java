package factorycraft.simulation.trains;

import factorycraft.simulation.rail.RailVector;

/** A bounded interval: exact translational SAT plus the measured corner rotation bound. */
public final class MotionHull {
  private final StockEnvelope current, expanded;
  private final double vx, vy, vz;
  private final boolean longitudinal;
  private final SpatialReservations.Bounds bounds;

  public MotionHull(StockEnvelope previous, StockEnvelope current) {
    this.current = current;
    if (previous == null) previous = current;
    double pad =
        distance(previous.side(), current.side()) * current.halfWidth()
            + distance(previous.up(), current.up()) * current.halfHeight()
            + distance(previous.forward(), current.forward()) * current.halfLength();
    expanded = pad < 1e-12 ? previous : previous.grow(pad);
    vx = previous.center().x() - current.center().x();
    vy = previous.center().y() - current.center().y();
    vz = previous.center().z() - current.center().z();
    longitudinal =
        pad < 1e-12
            && Math.abs(vx * current.side().x() + vy * current.side().y() + vz * current.side().z())
                < 1e-10
            && Math.abs(vx * current.up().x() + vy * current.up().y() + vz * current.up().z())
                < 1e-10
            && Math.abs(
                    vx * current.forward().x()
                        + vy * current.forward().y()
                        + vz * current.forward().z())
                <= 2 * current.halfLength();
    var a = expanded.bounds();
    var b = current.bounds();
    // The expanded previous orientation translated to the end also bounds in-between corners.
    bounds =
        new SpatialReservations.Bounds(
            Math.min(a.x0(), Math.min(a.x0() - vx, b.x0())),
            Math.min(a.y0(), Math.min(a.y0() - vy, b.y0())),
            Math.min(a.z0(), Math.min(a.z0() - vz, b.z0())),
            Math.max(a.x1(), Math.max(a.x1() - vx, b.x1())),
            Math.max(a.y1(), Math.max(a.y1() - vy, b.y1())),
            Math.max(a.z1(), Math.max(a.z1() - vz, b.z1())));
  }

  public SpatialReservations.Bounds bounds() {
    return bounds;
  }

  public boolean intersects(double x0, double y0, double z0, double x1, double y1, double z1) {
    return current.intersects(x0, y0, z0, x1, y1, z1)
        || (longitudinal
            ? expanded.intersects(x0, y0, z0, x1, y1, z1)
            : !Double.isNaN(expanded.sweepFraction(x0, y0, z0, x1, y1, z1, vx, vy, vz)));
  }

  /** Earliest conservative time of contact in this interval, not collection iteration order. */
  public double fraction(double x0, double y0, double z0, double x1, double y1, double z1) {
    if (!intersects(x0, y0, z0, x1, y1, z1)) return Double.NaN;
    double t = expanded.sweepFraction(x0, y0, z0, x1, y1, z1, vx, vy, vz);
    return Double.isNaN(t) ? 1 : t;
  }

  private static double distance(RailVector a, RailVector b) {
    double x = a.x() - b.x(), y = a.y() - b.y(), z = a.z() - b.z();
    return Math.sqrt(x * x + y * y + z * z);
  }
}
