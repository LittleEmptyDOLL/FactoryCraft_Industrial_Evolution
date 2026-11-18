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
  public static BogiePose sample(TrainPath path, RailGraph graph, double center, boolean reversed) {
    var a = path.sample(graph, center - StockSpec.BOGIE_OFFSET);
    var b = path.sample(graph, center + StockSpec.BOGIE_OFFSET);
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
}
