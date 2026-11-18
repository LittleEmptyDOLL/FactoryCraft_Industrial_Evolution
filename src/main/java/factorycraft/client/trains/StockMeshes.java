package factorycraft.client.trains;

import factorycraft.simulation.trains.StockSpec;
import java.util.*;

/** Original, cached modeled stock. Coordinates in blocks; front is -Z. No imported artwork. */
public final class StockMeshes {
  public record Face(float[] vertices, int color, boolean emissive) {}

  public static final Map<StockSpec, List<Face>> BODY = new EnumMap<>(StockSpec.class);
  public static final List<Face> TRUCK, WHEEL;
  public static final int PAINT = 0x1000000, STEEL = 0x69777b, DARK = 0x252b30, EDGE = 0xc0b79d;

  static {
    for (var kind : StockSpec.values()) {
      var m = new Builder();
      m.box(-.96, .81, -2.95, .96, 1.04, 2.95, DARK);
      for (int side : new int[] {-1, 1})
        for (double z : new double[] {-2.65, 2.65}) {
          m.box(side * .96 - .09, .72, z - .32, side * .96 + .09, 1.12, z + .32, STEEL);
          for (double y : new double[] {.7, .9})
            m.box(side * 1.02 - .07, y, z - .26, side * 1.02 + .07, y + .04, z + .26, EDGE);
        }
      for (int end : new int[] {-1, 1}) {
        m.box(-.16, .66, end * 3.23 - .25, .16, .85, end * 3.23 + .25, STEEL);
        m.box(-.32, .64, end * 3.43 - .07, .32, .88, end * 3.43 + .07, DARK);
        m.box(-.8, .95, end * 2.91 - .04, .8, 1.13, end * 2.91 + .04, EDGE);
      }
      if (kind == StockSpec.LOCOMOTIVE) locomotive(m);
      if (kind == StockSpec.CARGO) cargo(m);
      if (kind == StockSpec.FLUID) tanker(m);
      BODY.put(kind, List.copyOf(m.faces));
    }
    var t = new Builder();
    t.box(-.68, .43, -.68, .68, .74, .68, DARK);
    for (int side : new int[] {-1, 1}) {
      t.box(side * .7 - .09, .48, -.86, side * .7 + .09, .66, .86, STEEL);
      for (double z : new double[] {-.6, 0, .6})
        t.box(side * .7 - .12, .38, z - .12, side * .7 + .12, .7, z + .12, DARK);
      for (double z : new double[] {-.3, .3})
        for (int i = 0; i < 4; i++)
          t.box(
              side * .7 - .11, .44 + i * .05, z - .1, side * .7 + .11, .47 + i * .05, z + .1, EDGE);
    }
    TRUCK = List.copyOf(t.faces);
    var w = new Builder();
    w.cylinderX(-.11, .11, 0, 0, .39, 16, DARK);
    w.cylinderX(-.13, .13, 0, 0, .31, 16, STEEL);
    w.cylinderX(-.14, .14, 0, 0, .11, 12, EDGE);
    for (int i = 0; i < 4; i++) {
      double a = i * Math.PI / 2;
      w.box(
          -.145,
          Math.cos(a) * .22 - .035,
          Math.sin(a) * .22 - .035,
          .145,
          Math.cos(a) * .22 + .035,
          Math.sin(a) * .22 + .035,
          DARK);
    }
    WHEEL = List.copyOf(w.faces);
  }

  private static void locomotive(Builder m) {
    // Long stepped hood, chamfered roof, open cab windows and a real interior behind it.
    m.hull(
        -2.8,
        .72,
        new double[][] {
          {-.71, 1.07}, {.71, 1.07}, {.71, 2.24}, {.5, 2.48}, {-.5, 2.48}, {-.71, 2.24}
        },
        PAINT);
    m.box(-.76, 1.08, .62, .76, 1.86, 2.68, PAINT);
    m.hull(.62, 2.7, new double[][] {{-.94, 3.05}, {.94, 3.05}, {.75, 3.42}, {-.75, 3.42}}, DARK);
    for (int side : new int[] {-1, 1})
      for (double z : new double[] {.65, 1.74, 2.62})
        m.box(side * .83 - .055, 1.85, z - .06, side * .83 + .055, 3.09, z + .06, PAINT);
    m.box(-.83, 2.9, .61, .83, 3.06, .75, EDGE);
    m.box(-.83, 2.9, 2.57, .83, 3.06, 2.72, EDGE);
    m.box(-.83, 1.78, .64, .83, 2.08, .78, PAINT);
    m.box(-.83, 1.78, 2.58, .83, 2.08, 2.72, PAINT);
    // Cab floor, control desk, seat/back. Windows are openings, not opaque black faces.
    m.box(-.76, 1.07, .75, .76, 1.15, 2.58, STEEL);
    m.box(-.65, 1.55, .83, .65, 1.83, 1.07, DARK);
    for (double x : new double[] {-.38, 0, .38})
      m.box(x - .07, 1.83, .86, x + .07, 1.87, .98, 0x7ca79c);
    m.box(-.26, 1.22, 1.68, .26, 1.46, 2.23, 0x3f3d35);
    m.box(-.26, 1.46, 2.12, .26, 2.05, 2.25, 0x3f3d35);
    // Exhaust, service panels, cooling fins, access rails and front handrail.
    m.box(-.24, 2.45, -.2, .24, 2.85, .32, DARK);
    m.box(-.29, 2.84, -.25, .29, 2.93, .37, STEEL);
    for (int side : new int[] {-1, 1}) {
      for (double z = -2.45; z < .4; z += .27)
        m.box(side * .72 - .018, 1.42, z - .04, side * .72 + .018, 2.12, z + .04, DARK);
      for (double z : new double[] {-2.7, -1.2, .55})
        m.box(side * .93 - .025, 1.05, z - .025, side * .93 + .025, 1.66, z + .025, EDGE);
      m.box(side * .93 - .025, 1.63, -2.7, side * .93 + .025, 1.68, .55, EDGE);
      m.box(side * .81 - .04, 1.27, -2.87, side * .81 + .04, 1.37, -2.81, 0xf0c745);
    }
    m.box(-.48, 1.8, -2.86, .48, 1.89, -2.8, EDGE);
    m.box(-.17, 2.02, -2.84, .17, 2.18, -2.79, 0xffedc1, true);
    m.box(-.17, 2.65, 2.7, .17, 2.76, 2.74, 0xe0714c, true);
  }

  private static void cargo(Builder m) {
    m.box(-.86, 1.03, -2.72, .86, 1.12, 2.72, 0x5b4b3e);
    for (int side : new int[] {-1, 1}) {
      m.box(side * .88 - .06, 1.08, -2.76, side * .88 + .06, 2.65, 2.76, PAINT);
      m.box(side * .89 - .08, 2.61, -2.8, side * .89 + .08, 2.73, 2.8, EDGE);
      for (double z = -2.6; z < 2.7; z += .65)
        m.box(side * .94 - .035, 1.1, z - .04, side * .94 + .035, 2.59, z + .04, DARK);
      for (double y : new double[] {1.35, 1.9, 2.4})
        m.box(side * .94 - .02, y, -2.7, side * .94 + .02, y + .05, 2.7, 0x805b41);
    }
    for (int end : new int[] {-1, 1})
      m.box(-.86, 1.08, end * 2.7 - .06, .86, 2.65, end * 2.7 + .06, PAINT);
    m.box(-.91, 2.62, -2.78, .91, 2.73, -2.65, EDGE);
    m.box(-.91, 2.62, 2.65, .91, 2.73, 2.78, EDGE);
  }

  private static void tanker(Builder m) {
    m.cylinderZ(-2.56, 2.56, 0, 2.04, .9, 24, 0xd5cfb7);
    // Rounded ends are stepped rings, rather than square cuboids.
    for (int end : new int[] {-1, 1}) {
      m.cylinderZ(end * 2.56, end * 2.7, 0, 2.04, .78, 24, STEEL);
      m.cylinderZ(end * 2.7, end * 2.79, 0, 2.04, .56, 24, 0xd5cfb7);
      for (double y = 1.2; y < 2.8; y += .25)
        m.box(-.23, y, end * 2.85 - .025, .23, y + .04, end * 2.85 + .025, STEEL);
      for (int side : new int[] {-1, 1})
        m.box(
            side * .25 - .025,
            1.2,
            end * 2.85 - .025,
            side * .25 + .025,
            2.83,
            end * 2.85 + .025,
            STEEL);
    }
    for (double z : new double[] {-1.7, 1.7})
      m.cylinderZ(z - .06, z + .06, 0, 2.04, .926, 24, PAINT);
    m.box(-.33, 2.86, -.37, .33, 3.05, .37, DARK);
    m.box(-.39, 3.04, -.43, .39, 3.12, .43, PAINT);
    for (double z : new double[] {-2, 0, 2})
      for (int side : new int[] {-1, 1}) {
        m.box(side * .87 - .07, 1.48, z - .1, side * .87 + .07, 1.7, z + .1, DARK);
        m.box(side * .99 - .025, 1.5, z - .14, side * .99 + .025, 1.68, z + .14, EDGE);
      }
    for (int side : new int[] {-1, 1})
      m.box(side * .56 - .025, 2.91, -1.9, side * .56 + .025, 3.17, 1.9, STEEL);
  }

  private static final class Builder {
    final List<Face> faces = new ArrayList<>();

    void face(int c, boolean e, double... p) {
      var f = new float[p.length];
      for (int i = 0; i < p.length; i++) f[i] = (float) p[i];
      faces.add(new Face(f, c, e));
    }

    void box(double x, double y, double z, double X, double Y, double Z, int c) {
      box(x, y, z, X, Y, Z, c, false);
    }

    void box(double x, double y, double z, double X, double Y, double Z, int c, boolean e) {
      face(c, e, x, y, z, x, Y, z, X, Y, z, X, y, z);
      face(c, e, X, y, Z, X, Y, Z, x, Y, Z, x, y, Z);
      face(c, e, x, y, Z, x, Y, Z, x, Y, z, x, y, z);
      face(c, e, X, y, z, X, Y, z, X, Y, Z, X, y, Z);
      face(c, e, x, Y, z, x, Y, Z, X, Y, Z, X, Y, z);
      face(c, e, x, y, Z, x, y, z, X, y, z, X, y, Z);
    }

    void hull(double z0, double z1, double[][] section, int c) {
      for (int i = 0; i < section.length; i++) {
        var a = section[i];
        var b = section[(i + 1) % section.length];
        face(c, false, a[0], a[1], z0, b[0], b[1], z0, b[0], b[1], z1, a[0], a[1], z1);
      }
      for (int i = 1; i < section.length - 1; i++) {
        var a = section[0];
        var b = section[i];
        var d = section[i + 1];
        face(c, false, a[0], a[1], z0, b[0], b[1], z0, d[0], d[1], z0, d[0], d[1], z0);
        face(c, false, d[0], d[1], z1, b[0], b[1], z1, a[0], a[1], z1, a[0], a[1], z1);
      }
    }

    void cylinderZ(double a, double b, double x, double y, double radius, int n, int c) {
      var s = new double[n][2];
      for (int i = 0; i < n; i++) {
        double f = i * 2 * Math.PI / n;
        s[i] = new double[] {x + Math.cos(f) * radius, y + Math.sin(f) * radius};
      }
      hull(Math.min(a, b), Math.max(a, b), s, c);
    }

    void cylinderX(double a, double b, double y, double z, double radius, int n, int c) {
      int start = faces.size();
      cylinderZ(a, b, 0, 0, radius, n, c);
      for (int i = start; i < faces.size(); i++) {
        var f = faces.get(i);
        var v = f.vertices;
        for (int j = 0; j < v.length; j += 3) {
          float x = v[j];
          v[j] = v[j + 2];
          v[j + 2] = (float) (z + v[j + 1]);
          v[j + 1] = (float) (y + x);
        }
      }
    }
  }

  private StockMeshes() {}
}
