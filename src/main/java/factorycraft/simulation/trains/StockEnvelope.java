package factorycraft.simulation.trains;

import factorycraft.simulation.rail.RailVector;

/** Oriented 3x7x4 clearance; SAT prevents a pitched broadphase box hitting unrelated ground. */
public record StockEnvelope(RailVector center, RailVector side, RailVector up, RailVector forward) {
  public static StockEnvelope of(BogiePose pose) {
    return new StockEnvelope(
        pose.center().add(pose.normal().scale(2.02)), pose.side(), pose.normal(), pose.forward());
  }

  public boolean intersects(double x0, double y0, double z0, double x1, double y1, double z1) {
    var delta = new RailVector((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2).subtract(center);
    double hx = (x1 - x0) / 2, hy = (y1 - y0) / 2, hz = (z1 - z0) / 2;
    var axes =
        new RailVector[] {
          side,
          up,
          forward,
          new RailVector(1, 0, 0),
          new RailVector(0, 1, 0),
          new RailVector(0, 0, 1)
        };
    for (var axis : axes) if (separated(axis, delta, hx, hy, hz)) return false;
    for (int i = 0; i < 3; i++)
      for (int j = 3; j < 6; j++)
        if (separated(axes[i].cross(axes[j]), delta, hx, hy, hz)) return false;
    return true;
  }

  private boolean separated(RailVector axis, RailVector delta, double hx, double hy, double hz) {
    if (axis.dot(axis) < 1e-16) return false;
    double r =
        1.5 * Math.abs(axis.dot(side))
            + 2 * Math.abs(axis.dot(up))
            + 3.5 * Math.abs(axis.dot(forward));
    double b = hx * Math.abs(axis.x()) + hy * Math.abs(axis.y()) + hz * Math.abs(axis.z());
    return Math.abs(delta.dot(axis)) > r + b - 1e-8;
  }
}
