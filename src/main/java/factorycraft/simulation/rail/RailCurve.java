package factorycraft.simulation.rail;

import java.util.*;

/** Cubic horizontal family and smoothstep height, with deterministic sampled metric inversion. */
public final class RailCurve {
  public static final double MIN_RADIUS = 13, MAX_GRADE = .25, MAX_LENGTH = 128;
  public static final int STEPS = 1024;

  public record Frame(
      RailVector position, RailVector tangent, RailVector side, RailVector normal) {}

  public final RailEnd start, end;
  public final double handle;
  private final boolean validated;

  public boolean validated() {
    return validated;
  }

  private final RailVector a, b, c, d;
  private final double[] distances = new double[STEPS + 1];
  private double radius = Double.POSITIVE_INFINITY, grade;

  public RailCurve(RailEnd start, RailEnd end, double handle) {
    this(start, end, handle, true);
  }

  /** Non-authoritative ghost only; invalid curvature/grades must remain visible in red. */
  public static RailCurve preview(RailEnd start, RailEnd end) {
    return new RailCurve(start, end, Math.max(.1, start.point().distance(end.point()) / 3), false);
  }

  private RailCurve(RailEnd start, RailEnd end, double handle, boolean validate) {
    this.validated = validate;
    this.start = start;
    this.end = end;
    this.handle = handle;
    a = start.point();
    d = end.point();
    double chord = Math.hypot(d.x() - a.x(), d.z() - a.z());
    if (chord < 1
        || chord > 128
        || Math.abs(d.y() - a.y()) > (validate ? 24 : 128)
        || !Double.isFinite(handle)
        || handle < .1
        || handle > 128) throw new IllegalArgumentException("distance");
    b = a.add(start.heading().vector().scale(handle));
    c = d.subtract(end.heading().vector().scale(handle));
    var previous = point(0);
    var delta = new RailVector(d.x() - a.x(), 0, d.z() - a.z()).unit();
    for (int i = 0; i <= STEPS; i++) {
      double t = i / (double) STEPS;
      var p = point(t);
      var v = derivative(t);
      var acc = second(t);
      double horizontal = Math.hypot(v.x(), v.z());
      if (validate && (horizontal < 1e-7 || v.x() * delta.x() + v.z() * delta.z() <= 0))
        throw new IllegalArgumentException("fold");
      grade = Math.max(grade, Math.abs(v.y()) / horizontal);
      double curvature = Math.abs(v.x() * acc.z() - v.z() * acc.x());
      if (curvature > 1e-8)
        radius = Math.min(radius, horizontal * horizontal * horizontal / curvature);
      if (i > 0) distances[i] = distances[i - 1] + p.distance(previous);
      previous = p;
    }
    if (validate && length() > MAX_LENGTH + .000001) throw new IllegalArgumentException("distance");
    if (validate && grade > MAX_GRADE + 1e-8) throw new IllegalArgumentException("grade");
    if (validate && radius < MIN_RADIUS - .000001) throw new IllegalArgumentException("radius");
  }

  public static RailCurve plan(RailEnd a, RailEnd b) {
    double chord = a.point().distance(b.point());
    RailCurve best = null;
    String reason = "geometry";
    for (double factor : new double[] {1.0 / 3, .25, .4, .5, .6, .75})
      try {
        var c = new RailCurve(a, b, chord * factor);
        if (best == null || c.length() < best.length()) best = c;
      } catch (IllegalArgumentException e) {
        reason = e.getMessage();
      }
    if (best == null) throw new IllegalArgumentException(reason);
    return best;
  }

  public RailVector point(double t) {
    double u = 1 - t;
    return new RailVector(
        u * u * u * a.x() + 3 * u * u * t * b.x() + 3 * u * t * t * c.x() + t * t * t * d.x(),
        a.y() + (d.y() - a.y()) * t * t * (3 - 2 * t),
        u * u * u * a.z() + 3 * u * u * t * b.z() + 3 * u * t * t * c.z() + t * t * t * d.z());
  }

  private RailVector derivative(double t) {
    double u = 1 - t;
    return new RailVector(
        3 * u * u * (b.x() - a.x()) + 6 * u * t * (c.x() - b.x()) + 3 * t * t * (d.x() - c.x()),
        6 * (d.y() - a.y()) * t * u,
        3 * u * u * (b.z() - a.z()) + 6 * u * t * (c.z() - b.z()) + 3 * t * t * (d.z() - c.z()));
  }

  private RailVector second(double t) {
    return new RailVector(
        6 * (1 - t) * (c.x() - 2 * b.x() + a.x()) + 6 * t * (d.x() - 2 * c.x() + b.x()),
        6 * (d.y() - a.y()) * (1 - 2 * t),
        6 * (1 - t) * (c.z() - 2 * b.z() + a.z()) + 6 * t * (d.z() - 2 * c.z() + b.z()));
  }

  public double length() {
    return distances[STEPS];
  }

  public double minimumRadius() {
    return radius;
  }

  public double maximumGrade() {
    return grade;
  }

  public int cost() {
    double units =
        Double.isInfinite(radius) && grade < 1e-8
            ? Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()))
            : length();
    return Math.max(1, (int) Math.ceil((units - 1e-7) / 2));
  }

  public Frame sample(double distance, boolean reverse) {
    double s = Math.max(0, Math.min(length(), reverse ? length() - distance : distance));
    int i = Arrays.binarySearch(distances, s);
    double t;
    if (i >= 0) t = i / (double) STEPS;
    else {
      int hi = -i - 1;
      if (hi <= 0) t = 0;
      else if (hi > STEPS) t = 1;
      else t = (hi - 1 + (s - distances[hi - 1]) / (distances[hi] - distances[hi - 1])) / STEPS;
    }
    var velocity = derivative(t);
    var tangent = velocity.length() < 1e-8 ? d.subtract(a).unit() : velocity.unit();
    if (reverse) tangent = tangent.scale(-1);
    var horizontalSide = new RailVector(-tangent.z(), 0, tangent.x());
    var side =
        horizontalSide.length() < 1e-8
            ? new RailVector(-start.heading().vector().z(), 0, start.heading().vector().x())
            : horizontalSide.unit();
    return new Frame(point(t), tangent, side, side.cross(tangent).unit());
  }

  public RailCurve reverse() {
    return new RailCurve(end.reverse(), start.reverse(), handle);
  }
}
