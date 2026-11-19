package factorycraft.simulation.trains;

import factorycraft.simulation.rail.*;

/** Independent wheelbase samples give continuous body pitch/yaw through grades and junctions. */
public record BogiePose(
    RailCurve.Frame rear,
    RailCurve.Frame front,
    RailVector center,
    RailVector forward,
    RailVector side,
    RailVector normal) {
  /**
   * Builder samples the same two-bogie chord, extrapolating only the unknown endpoint continuation.
   */
  public static BogiePose onCurve(RailCurve curve, double center) {
    return from(
        frame(curve, center - StockSpec.BOGIE_OFFSET),
        frame(curve, center + StockSpec.BOGIE_OFFSET),
        false);
  }

  private static RailCurve.Frame frame(RailCurve curve, double at) {
    var f = curve.sample(Math.clamp(at, 0, curve.length()), false);
    if (at >= 0 && at <= curve.length()) return f;
    return new RailCurve.Frame(
        f.position().add(f.tangent().scale(at < 0 ? at : at - curve.length())),
        f.tangent(),
        f.side(),
        f.normal());
  }

  private static BogiePose from(RailCurve.Frame a, RailCurve.Frame b, boolean reversed) {
    if (reversed) {
      var t = a;
      a = b;
      b = t;
    }
    var f = b.position().subtract(a.position()).unit();
    var side = new RailVector(-f.z(), 0, f.x()).unit();
    return new BogiePose(
        a, b, a.position().add(b.position()).scale(.5), f, side, side.cross(f).unit());
  }

  public static BogiePose sample(TrainPath path, RailGraph graph, double center, boolean reversed) {
    var a = path.sample(graph, center - StockSpec.BOGIE_OFFSET);
    var b = path.sample(graph, center + StockSpec.BOGIE_OFFSET);
    return from(a, b, reversed);
  }
}
