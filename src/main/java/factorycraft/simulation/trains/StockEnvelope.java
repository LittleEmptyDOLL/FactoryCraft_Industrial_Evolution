package factorycraft.simulation.trains;

import factorycraft.simulation.rail.RailVector;

/**
 * Source-sized oriented clearance. Scalar SAT avoids allocating fifteen axes per inspected voxel.
 */
public record StockEnvelope(
    RailVector center,
    RailVector side,
    RailVector up,
    RailVector forward,
    double halfWidth,
    double halfHeight,
    double halfLength) {
  public StockEnvelope(RailVector c, RailVector s, RailVector u, RailVector f) {
    this(
        c,
        s,
        u,
        f,
        StockGeometry.HALF_WIDTH,
        (StockGeometry.TOP - StockGeometry.BOTTOM) / 2,
        StockGeometry.HALF_LENGTH);
  }

  public StockEnvelope {
    if (!Double.isFinite(center.x() + center.y() + center.z() + halfWidth + halfHeight + halfLength)
        || halfWidth <= 0
        || halfHeight <= 0
        || halfLength <= 0) throw new IllegalArgumentException("Stock hull");
  }

  public StockEnvelope grow(double pad) {
    return new StockEnvelope(
        center, side, up, forward, halfWidth + pad, halfHeight + pad, halfLength + pad);
  }

  public SpatialReservations.Bounds bounds() {
    double x =
        halfWidth * Math.abs(side.x())
            + halfHeight * Math.abs(up.x())
            + halfLength * Math.abs(forward.x());
    double y =
        halfWidth * Math.abs(side.y())
            + halfHeight * Math.abs(up.y())
            + halfLength * Math.abs(forward.y());
    double z =
        halfWidth * Math.abs(side.z())
            + halfHeight * Math.abs(up.z())
            + halfLength * Math.abs(forward.z());
    return new SpatialReservations.Bounds(
        center.x() - x,
        center.y() - y,
        center.z() - z,
        center.x() + x,
        center.y() + y,
        center.z() + z);
  }

  public static StockEnvelope of(BogiePose pose) {
    return of(pose, StockSpec.LOCOMOTIVE);
  }

  public static StockEnvelope of(BogiePose pose, StockSpec kind) {
    double height = StockGeometry.top(kind) - StockGeometry.BOTTOM;
    return new StockEnvelope(
        pose.center().add(pose.normal().scale(StockGeometry.BOTTOM + height / 2)),
        pose.side(),
        pose.normal(),
        pose.forward(),
        StockGeometry.HALF_WIDTH,
        height / 2,
        StockGeometry.HALF_LENGTH);
  }

  public boolean intersects(double x0, double y0, double z0, double x1, double y1, double z1) {
    double dx = (x0 + x1) / 2 - center.x(),
        dy = (y0 + y1) / 2 - center.y(),
        dz = (z0 + z1) / 2 - center.z();
    double hx = (x1 - x0) / 2, hy = (y1 - y0) / 2, hz = (z1 - z0) / 2;
    if (separated(side.x(), side.y(), side.z(), dx, dy, dz, hx, hy, hz)
        || separated(up.x(), up.y(), up.z(), dx, dy, dz, hx, hy, hz)
        || separated(forward.x(), forward.y(), forward.z(), dx, dy, dz, hx, hy, hz)
        || separated(1, 0, 0, dx, dy, dz, hx, hy, hz)
        || separated(0, 1, 0, dx, dy, dz, hx, hy, hz)
        || separated(0, 0, 1, dx, dy, dz, hx, hy, hz)) return false;
    return crosses(side, dx, dy, dz, hx, hy, hz)
        && crosses(up, dx, dy, dz, hx, hy, hz)
        && crosses(forward, dx, dy, dz, hx, hy, hz);
  }

  private RailVector axis(int i) {
    return i == 0 ? side : i == 1 ? up : forward;
  }

  private double radius(double x, double y, double z) {
    return halfWidth * Math.abs(x * side.x() + y * side.y() + z * side.z())
        + halfHeight * Math.abs(x * up.x() + y * up.y() + z * up.z())
        + halfLength * Math.abs(x * forward.x() + y * forward.y() + z * forward.z());
  }

  private boolean apart(StockEnvelope b, double x, double y, double z) {
    if (x * x + y * y + z * z < 1e-16) return false;
    return Math.abs(
            (b.center.x() - center.x()) * x
                + (b.center.y() - center.y()) * y
                + (b.center.z() - center.z()) * z)
        > radius(x, y, z) + b.radius(x, y, z) - 1e-8;
  }

  public boolean intersects(StockEnvelope b) {
    for (int i = 0; i < 3; i++) {
      var a = axis(i);
      var c = b.axis(i);
      if (apart(b, a.x(), a.y(), a.z()) || apart(b, c.x(), c.y(), c.z())) return false;
    }
    for (int i = 0; i < 3; i++)
      for (int j = 0; j < 3; j++) {
        var a = axis(i);
        var c = b.axis(j);
        if (apart(
            b,
            a.y() * c.z() - a.z() * c.y(),
            a.z() * c.x() - a.x() * c.z(),
            a.x() * c.y() - a.y() * c.x())) return false;
      }
    return true;
  }

  /** Exact slab ray clip in the hull frame, including a start inside the chassis. */
  public double rayFraction(RailVector from, RailVector to) {
    double enter = 0, exit = 1;
    for (int i = 0; i < 3; i++) {
      var a = axis(i);
      double origin =
          (from.x() - center.x()) * a.x()
              + (from.y() - center.y()) * a.y()
              + (from.z() - center.z()) * a.z();
      double speed =
          (to.x() - from.x()) * a.x() + (to.y() - from.y()) * a.y() + (to.z() - from.z()) * a.z();
      double h = i == 0 ? halfWidth : i == 1 ? halfHeight : halfLength;
      if (Math.abs(speed) < 1e-12) {
        if (Math.abs(origin) > h) return Double.NaN;
        continue;
      }
      double t0 = (-h - origin) / speed, t1 = (h - origin) / speed;
      enter = Math.max(enter, Math.min(t0, t1));
      exit = Math.min(exit, Math.max(t0, t1));
      if (enter > exit) return Double.NaN;
    }
    return enter;
  }

  /** Continuous SAT for a translating AABB, used for native player/mob movement. */
  public double sweepFraction(
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      double vx,
      double vy,
      double vz) {
    double dx = (x0 + x1) / 2 - center.x(),
        dy = (y0 + y1) / 2 - center.y(),
        dz = (z0 + z1) / 2 - center.z();
    double hx = (x1 - x0) / 2, hy = (y1 - y0) / 2, hz = (z1 - z0) / 2, enter = 0, exit = 1;
    // Three hull axes, three world axes, nine cross products; scalar, no per-axis objects.
    for (int i = 0; i < 15; i++) {
      double ax, ay, az;
      if (i < 3) {
        var a = axis(i);
        ax = a.x();
        ay = a.y();
        az = a.z();
      } else if (i < 6) {
        ax = i == 3 ? 1 : 0;
        ay = i == 4 ? 1 : 0;
        az = i == 5 ? 1 : 0;
      } else {
        var a = axis((i - 6) / 3);
        int k = (i - 6) % 3;
        ax = k == 0 ? 0 : k == 1 ? -a.z() : a.y();
        ay = k == 0 ? a.z() : k == 1 ? 0 : -a.x();
        az = k == 0 ? -a.y() : k == 1 ? a.x() : 0;
      }
      if (ax * ax + ay * ay + az * az < 1e-16) continue;
      double r = radius(ax, ay, az) + hx * Math.abs(ax) + hy * Math.abs(ay) + hz * Math.abs(az);
      double origin = dx * ax + dy * ay + dz * az, speed = vx * ax + vy * ay + vz * az;
      if (Math.abs(speed) < 1e-12) {
        if (Math.abs(origin) > r - 1e-9) return Double.NaN;
        continue;
      }
      double a = (-r - origin) / speed, b = (r - origin) / speed;
      enter = Math.max(enter, Math.min(a, b));
      exit = Math.min(exit, Math.max(a, b));
      if (enter > exit) return Double.NaN;
    }
    return enter;
  }

  private boolean crosses(
      RailVector a, double dx, double dy, double dz, double hx, double hy, double hz) {
    return !separated(0, a.z(), -a.y(), dx, dy, dz, hx, hy, hz)
        && !separated(-a.z(), 0, a.x(), dx, dy, dz, hx, hy, hz)
        && !separated(a.y(), -a.x(), 0, dx, dy, dz, hx, hy, hz);
  }

  private boolean separated(
      double ax,
      double ay,
      double az,
      double dx,
      double dy,
      double dz,
      double hx,
      double hy,
      double hz) {
    if (ax * ax + ay * ay + az * az < 1e-16) return false;
    double r =
        halfWidth * Math.abs(ax * side.x() + ay * side.y() + az * side.z())
            + halfHeight * Math.abs(ax * up.x() + ay * up.y() + az * up.z())
            + halfLength * Math.abs(ax * forward.x() + ay * forward.y() + az * forward.z());
    double b = hx * Math.abs(ax) + hy * Math.abs(ay) + hz * Math.abs(az);
    return Math.abs(dx * ax + dy * ay + dz * az) > r + b - 1e-8;
  }
}
