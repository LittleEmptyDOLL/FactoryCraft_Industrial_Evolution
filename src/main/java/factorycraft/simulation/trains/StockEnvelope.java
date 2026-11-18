package factorycraft.simulation.trains;

import factorycraft.simulation.rail.RailVector;

/** Oriented 3x7x4 clearance. Scalar SAT avoids allocating fifteen axes per inspected voxel. */
public record StockEnvelope(RailVector center, RailVector side, RailVector up, RailVector forward) {
  public static StockEnvelope of(BogiePose pose) {
    return new StockEnvelope(
        pose.center().add(pose.normal().scale(2.02)), pose.side(), pose.normal(), pose.forward());
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
        1.5 * Math.abs(ax * side.x() + ay * side.y() + az * side.z())
            + 2 * Math.abs(ax * up.x() + ay * up.y() + az * up.z())
            + 3.5 * Math.abs(ax * forward.x() + ay * forward.y() + az * forward.z());
    double b = hx * Math.abs(ax) + hy * Math.abs(ay) + hz * Math.abs(az);
    return Math.abs(dx * ax + dy * ay + dz * az) > r + b - 1e-8;
  }
}
