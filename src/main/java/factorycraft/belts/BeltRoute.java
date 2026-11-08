package factorycraft.belts;

import factorycraft.simulation.belts.BeltLane;
import java.util.*;

/** Pure metric geometry; samples never own or move resources. Coordinates face north by default. */
public final class BeltRoute {
  public enum Shape {
    FLAT,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    LIFT,
    UNDERGROUND
  }

  public record Point(double x, double y, double z) {
    Point add(Point p) {
      return new Point(x + p.x, y + p.y, z + p.z);
    }

    Point scale(double scale) {
      return new Point(x * scale, y * scale, z * scale);
    }

    public double distance(Point p) {
      return Math.sqrt((x - p.x) * (x - p.x) + (y - p.y) * (y - p.y) + (z - p.z) * (z - p.z));
    }
  }

  public final Shape shape;
  public final int span, exitTurn;
  private final Point[][] points = new Point[2][];
  private final double[][] distances = new double[2][];
  private final int[] lengths = new int[2];
  public static final BeltRoute FLAT = new BeltRoute(Shape.FLAT, 0, 0);

  public BeltRoute(Shape shape, int span, int exitTurn) {
    if (Math.abs(span) > 64
        || exitTurn < 0
        || exitTurn > 3
        || shape == Shape.LIFT && span == 0
        || shape == Shape.UNDERGROUND && span < 1)
      throw new IllegalArgumentException("Invalid route");
    this.shape = shape;
    this.span = span;
    this.exitTurn = exitTurn;
    int samples =
        shape == Shape.FLAT
                || shape == Shape.UP
                || shape == Shape.DOWN
                || shape == Shape.UNDERGROUND
            ? 1
            : shape == Shape.LIFT ? 64 + Math.abs(span) * 8 : 64;
    for (int lane = 0; lane < 2; lane++) {
      points[lane] = new Point[samples + 1];
      distances[lane] = new double[samples + 1];
      for (int i = 0; i <= samples; i++) {
        points[lane][i] = raw(lane, (double) i / samples);
        if (i > 0)
          distances[lane][i] =
              distances[lane][i - 1] + points[lane][i].distance(points[lane][i - 1]);
      }
      lengths[lane] = Math.max(1, (int) Math.round(distances[lane][samples] * BeltLane.LENGTH));
    }
  }

  @Override
  public boolean equals(Object value) {
    return value instanceof BeltRoute other
        && shape == other.shape
        && span == other.span
        && exitTurn == other.exitTurn;
  }

  @Override
  public int hashCode() {
    return Objects.hash(shape, span, exitTurn);
  }

  public int length(int lane) {
    return lengths[lane];
  }

  public int heightDelta() {
    return shape == Shape.UP ? 1 : shape == Shape.DOWN ? -1 : shape == Shape.LIFT ? span : 0;
  }

  public int reach() {
    return shape == Shape.UNDERGROUND ? span : 0;
  }

  public Point point(int lane, double units) {
    double at =
        Math.clamp(units / lengths[lane], 0, 1) * distances[lane][distances[lane].length - 1];
    int i = Arrays.binarySearch(distances[lane], at);
    if (i >= 0) return points[lane][i];
    i = -i - 1;
    if (i == 0) return points[lane][0];
    if (i >= points[lane].length) return points[lane][points[lane].length - 1];
    double t = (at - distances[lane][i - 1]) / (distances[lane][i] - distances[lane][i - 1]);
    return points[lane][i - 1].scale(1 - t).add(points[lane][i].scale(t));
  }

  public Point tangent(int lane, double units) {
    var a = point(lane, units - .05);
    var b = point(lane, units + .05);
    return b.add(a.scale(-1));
  }

  public boolean visible(double units, int lane) {
    return shape != Shape.UNDERGROUND
        || units <= BeltLane.LENGTH
        || units >= length(lane) - BeltLane.LENGTH;
  }

  private Point raw(int lane, double t) {
    double x = lane == 0 ? .28 : .72;
    return switch (shape) {
      case FLAT -> new Point(x, .1875, 1 - t);
      case UP -> new Point(x, .1875 + t, 1 - t);
      case DOWN -> new Point(x, .1875 - t, 1 - t);
      case LEFT, RIGHT -> {
        double r = shape == Shape.LEFT ? x : 1 - x;
        double a = (1 - t) * Math.PI / 2;
        yield new Point(
            shape == Shape.LEFT ? r * Math.cos(a) : 1 - r * Math.cos(a), .1875, r * Math.sin(a));
      }
      case UNDERGROUND -> new Point(x, .1875, 1 - t * (span + 1));
      case LIFT -> {
        double height = Math.abs(span),
            sign = Math.signum(span),
            path = height - 1 + Math.PI / 2,
            at = t * path;
        double cx = .5, cz = .5, y;
        if (at < Math.PI / 4) {
          double angle = at / .5;
          cz = .5 + .5 * (1 - Math.sin(angle));
          y = .5 * (1 - Math.cos(angle));
        } else if (at > path - Math.PI / 4) {
          double angle = (at - (path - Math.PI / 4)) / .5;
          double d = .5 * (1 - Math.cos(angle));
          y = height - .5 + .5 * Math.sin(angle);
          cx += Math.sin(exitTurn * Math.PI / 2) * d;
          cz += -Math.cos(exitTurn * Math.PI / 2) * d;
        } else y = .5 + at - Math.PI / 4;
        int turn = exitTurn == 3 ? -1 : exitTurn;
        double angle = turn * Math.PI / 2 * Math.clamp((y - height / 2 + .1) / .2, 0, 1),
            offset = lane == 0 ? -.22 : .22;
        yield new Point(
            cx + offset * Math.cos(angle), .1875 + sign * y, cz + offset * Math.sin(angle));
      }
    };
  }
}
