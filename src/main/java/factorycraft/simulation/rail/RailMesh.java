package factorycraft.simulation.rail;

import java.util.*;

/** Original thin 3D rail mesh, clipped to each physical cell for ordinary chunk baking. */
public final class RailMesh {
  public record Face(List<RailVector> vertices, int material) {
    public Face {
      vertices = List.copyOf(vertices);
    }
  }

  public static Map<RailFootprint.Cell, List<Face>> build(RailCurve curve) {
    var out = new HashMap<RailFootprint.Cell, List<Face>>();
    int steps = (int) Math.ceil(curve.length() * 2);
    for (int i = 0; i < steps; i++) {
      double s = curve.length() * i / steps, t = curve.length() * (i + 1) / steps;
      var a = curve.sample(s, false);
      var b = curve.sample(t, false);
      prism(out, a, b, -1, 1, -.125, -.04, 0);
      for (double side : new double[] {-.72, .72})
        prism(out, a, b, side - .065, side + .065, .045, .18, 1);
    }
    for (double s = .35; s < curve.length(); s += .85)
      prism(
          out,
          curve.sample(Math.max(0, s - .11), false),
          curve.sample(Math.min(curve.length(), s + .11), false),
          -.95,
          .95,
          -.035,
          .04,
          2);
    for (var end : List.of(curve.start, curve.end))
      out.remove(new RailFootprint.Cell(end.x(), end.y() - 1, end.z()));
    out.replaceAll((c, v) -> List.copyOf(v));
    return Map.copyOf(out);
  }

  private static RailVector at(RailCurve.Frame f, double side, double y) {
    return f.position().add(f.side().scale(side)).add(new RailVector(0, y, 0));
  }

  private static void prism(
      Map<RailFootprint.Cell, List<Face>> out,
      RailCurve.Frame a,
      RailCurve.Frame b,
      double l,
      double r,
      double bottom,
      double top,
      int material) {
    var p =
        new RailVector[] {
          at(a, l, bottom),
          at(a, r, bottom),
          at(b, r, bottom),
          at(b, l, bottom),
          at(a, l, top),
          at(a, r, top),
          at(b, r, top),
          at(b, l, top)
        };
    for (int[] face :
        new int[][] {
          {4, 7, 6, 5}, {0, 1, 2, 3}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}
        }) clip(out, List.of(p[face[3]], p[face[2]], p[face[1]], p[face[0]]), material);
  }

  private static void clip(
      Map<RailFootprint.Cell, List<Face>> out, List<RailVector> polygon, int material) {
    double minX = Double.POSITIVE_INFINITY,
        minY = minX,
        minZ = minX,
        maxX = -minX,
        maxY = -minX,
        maxZ = -minX;
    for (var p : polygon) {
      minX = Math.min(minX, p.x());
      minY = Math.min(minY, p.y());
      minZ = Math.min(minZ, p.z());
      maxX = Math.max(maxX, p.x());
      maxY = Math.max(maxY, p.y());
      maxZ = Math.max(maxZ, p.z());
    }
    for (int x = (int) Math.floor(minX);
        x <= Math.max((int) Math.floor(minX), (int) Math.floor(maxX - 1e-8));
        x++)
      for (int y = (int) Math.floor(minY);
          y <= Math.max((int) Math.floor(minY), (int) Math.floor(maxY - 1e-8));
          y++)
        for (int z = (int) Math.floor(minZ);
            z <= Math.max((int) Math.floor(minZ), (int) Math.floor(maxZ - 1e-8));
            z++) {
          List<RailVector> v = polygon;
          int[] base = {x, y, z};
          for (int axis = 0; axis < 3 && !v.isEmpty(); axis++) {
            v = cut(v, axis, base[axis], true);
            v = cut(v, axis, base[axis] + 1, false);
          }
          if (v.size() < 3) continue;
          var local = new ArrayList<RailVector>();
          for (var p : v) local.add(p.subtract(new RailVector(x, y, z)));
          out.computeIfAbsent(new RailFootprint.Cell(x, y, z), k -> new ArrayList<>())
              .add(new Face(local, material));
        }
  }

  private static double coord(RailVector p, int axis) {
    return axis == 0 ? p.x() : axis == 1 ? p.y() : p.z();
  }

  private static List<RailVector> cut(
      List<RailVector> input, int axis, double bound, boolean greater) {
    var out = new ArrayList<RailVector>();
    if (input.isEmpty()) return out;
    var a = input.getLast();
    boolean insideA = greater ? coord(a, axis) >= bound - 1e-9 : coord(a, axis) <= bound + 1e-9;
    for (var b : input) {
      boolean insideB = greater ? coord(b, axis) >= bound - 1e-9 : coord(b, axis) <= bound + 1e-9;
      if (insideA != insideB) {
        double t = (bound - coord(a, axis)) / (coord(b, axis) - coord(a, axis));
        out.add(a.add(b.subtract(a).scale(t)));
      }
      if (insideB) out.add(b);
      a = b;
      insideA = insideB;
    }
    return out;
  }

  private RailMesh() {}
}
