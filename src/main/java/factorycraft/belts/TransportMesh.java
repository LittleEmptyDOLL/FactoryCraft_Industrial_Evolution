package factorycraft.belts;

import java.util.*;

/** Original cell-local render mesh. No Minecraft/client classes, resources, or world reads. */
public final class TransportMesh {
  public enum Skin {
    SURFACE,
    FRAME,
    RAIL,
    ACCENT,
    ROLLER
  }

  public record Vertex(double x, double y, double z, double u, double v) {
    Vertex lerp(Vertex b, double t) {
      return new Vertex(
          x + (b.x - x) * t,
          y + (b.y - y) * t,
          z + (b.z - z) * t,
          u + (b.u - u) * t,
          v + (b.v - v) * t);
    }
  }

  public record Face(Skin skin, List<Vertex> vertices, boolean doubleSided) {
    public Face {
      vertices = List.copyOf(vertices);
    }
  }

  public record Mesh(List<Face> faces, int sampledSegments) {
    public Mesh {
      faces = List.copyOf(faces);
    }
  }

  private final List<Face> faces = new ArrayList<>();
  private int sampled;
  private final int offset;

  private TransportMesh(int offset) {
    this.offset = offset;
  }

  public static Mesh build(BeltRoute route, int offsetY, boolean output) {
    var m = new TransportMesh(offsetY);
    if (route.shape == BeltRoute.Shape.LIFT) m.lift(route);
    else if (route.shape == BeltRoute.Shape.UNDERGROUND || output) m.terminal(output);
    else m.ribbon(route, 0, 1, route.shape == BeltRoute.Shape.FLAT ? 1 : 24, true);
    return new Mesh(m.faces, m.sampled);
  }

  private void add(Skin skin, Vertex... points) {
    var p = clip(clip(Arrays.asList(points), 0, true), 1, false);
    if (p.size() >= 3) faces.add(new Face(skin, p, true));
  }

  private static List<Vertex> clip(List<Vertex> input, double y, boolean above) {
    var out = new ArrayList<Vertex>();
    if (input.isEmpty()) return out;
    var prev = input.getLast();
    boolean old = above ? prev.y >= y : prev.y <= y;
    for (var next : input) {
      boolean in = above ? next.y >= y : next.y <= y;
      if (in != old) out.add(prev.lerp(next, (y - prev.y) / (next.y - prev.y)));
      if (in) out.add(next);
      prev = next;
      old = in;
    }
    return out;
  }

  private Vertex edge(BeltRoute r, double t, double across, double dy) {
    var a = r.point(0, r.length(0) * t);
    var b = r.point(1, r.length(1) * t);
    double k = (across - .28) / .44;
    var tangent = r.tangent(0, r.length(0) * t);
    double rx = b.x() - a.x(), ry = b.y() - a.y(), rz = b.z() - a.z();
    double nx = ry * tangent.z() - rz * tangent.y(),
        ny = rz * tangent.x() - rx * tangent.z(),
        nz = rx * tangent.y() - ry * tangent.x();
    double norm = Math.sqrt(nx * nx + ny * ny + nz * nz);
    if (norm < 1e-12) {
      nx = 0;
      ny = 1;
      nz = 0;
      norm = 1;
    }
    return new Vertex(
        a.x() + (b.x() - a.x()) * k + dy * nx / norm,
        a.y() + (b.y() - a.y()) * k - offset + dy * ny / norm,
        a.z() + (b.z() - a.z()) * k + dy * nz / norm,
        across,
        r.shape == BeltRoute.Shape.LIFT
            ? (r.span > 0 ? 1 - (a.y() - offset) : a.y() - offset)
            : 1 - t);
  }

  private void ribbon(BeltRoute r, double start, double end, int steps, boolean rails) {
    for (int i = 0; i < steps; i++) {
      sampled++;
      double a = start + (end - start) * i / steps, b = start + (end - start) * (i + 1) / steps;
      // Surface remains on the unchanged logical route. Side strips are separate solid material.
      strip(r, a, b, .08, .92, 0, -.15, Skin.SURFACE, Skin.FRAME);
      if (rails) {
        strip(r, a, b, .015, .075, .055, -.13, Skin.RAIL, Skin.FRAME);
        strip(r, a, b, .925, .985, .055, -.13, Skin.RAIL, Skin.FRAME);
      }
    }
    if (r.shape == BeltRoute.Shape.FLAT) {
      for (double z : new double[] {.10, .9})
        box(Skin.ROLLER, .08, .065, z - .035, .92, .145, z + .035);
      for (double x : new double[] {.01, .93}) box(Skin.ACCENT, x, .08, .30, x + .06, .16, .7);
    }
  }

  private void strip(
      BeltRoute r,
      double a,
      double b,
      double left,
      double right,
      double top,
      double bottom,
      Skin face,
      Skin side) {
    var p = edge(r, a, left, top);
    var q = edge(r, a, right, top);
    var s = edge(r, b, right, top);
    var t = edge(r, b, left, top);
    var pl = edge(r, a, left, bottom);
    var ql = edge(r, a, right, bottom);
    var sl = edge(r, b, right, bottom);
    var tl = edge(r, b, left, bottom);
    add(face, p, q, s, t);
    add(side, tl, sl, ql, pl);
    add(side, p, t, tl, pl);
    add(side, s, q, ql, sl);
    // End caps occur only at the global ends; neighboring mesh sections share no duplicate cap.
    if (a == 0) add(side, q, p, pl, ql);
    if (b == 1) add(side, t, s, sl, tl);
  }

  private void lift(BeltRoute r) {
    for (double x : new double[] {.035, .875})
      for (double z : new double[] {.055, .875}) box(Skin.FRAME, x, 0, z, x + .09, 1, z + .07);
    // Open front, framed technical windows and narrow side service panels. Never new ports.
    box(Skin.ACCENT, .905, .08, .28, .97, .92, .74);
    box(Skin.RAIL, .03, .08, .3, .075, .92, .72);
    for (double y : new double[] {.02, .94}) {
      box(Skin.RAIL, .04, y, .86, .96, y + .04, .96);
      box(Skin.FRAME, .04, y, .07, .16, y + .04, .9);
      box(Skin.FRAME, .84, y, .07, .96, y + .04, .9);
    }
    box(Skin.ROLLER, .16, .46, .62, .84, .54, .71);
    // Invert monotonic height once: a section samples only its own interval, at most 24 segments.
    double a = heightParameter(r, offset - .25), b = heightParameter(r, offset + 1.2);
    double lo = Math.min(a, b), hi = Math.max(a, b);
    boolean end = offset == 0 || offset == r.span;
    double twist = r.span / 2d + .1875;
    boolean twisting = r.exitTurn != 0 && offset <= twist + .1 && offset + 1 >= twist - .1;
    if (hi > lo) ribbon(r, lo, hi, end || twisting ? 16 : 1, false);
    if (offset == 0) mouth(0);
    if (offset == r.span) mouth((r.exitTurn + 2) % 4);
  }

  private double heightParameter(BeltRoute r, double y) {
    double lo = 0, hi = 1;
    boolean up = r.span > 0;
    for (int i = 0; i < 28; i++) {
      double mid = (lo + hi) / 2;
      double at = r.point(0, r.length(0) * mid).y();
      if ((at < y) == up) lo = mid;
      else hi = mid;
    }
    return (lo + hi) / 2;
  }

  private void mouth(int turn) {
    for (double x : new double[] {.015, .91})
      rotatedBox(Skin.ACCENT, x, 0, .68, x + .075, .44, 1, turn);
    rotatedBox(Skin.FRAME, .055, .36, .72, .945, .43, .9, turn);
    rotatedBox(Skin.ROLLER, .09, .06, .9, .91, .15, .98, turn);
  }

  private void terminal(boolean output) {
    // A terminal is one flat visible mouth; the underground path remains entirely virtual.
    ribbon(BeltRoute.FLAT, 0, 1, 1, true);
    double z = output ? .55 : .05;
    box(Skin.ACCENT, .015, .15, z, .11, .55, z + .4);
    box(Skin.ACCENT, .89, .15, z, .985, .55, z + .4);
    box(Skin.FRAME, .08, .48, z, .92, .57, z + .4);
    box(Skin.ROLLER, .13, .22, z + .18, .87, .44, z + .27);
  }

  private void box(Skin skin, double x, double y, double z, double X, double Y, double Z) {
    rotatedBox(skin, x, y, z, X, Y, Z, 0);
  }

  private void rotatedBox(
      Skin skin, double x, double y, double z, double X, double Y, double Z, int turn) {
    Vertex a = v(x, y, z, 0, 1, turn),
        b = v(X, y, z, 1, 1, turn),
        c = v(X, Y, z, 1, 0, turn),
        d = v(x, Y, z, 0, 0, turn);
    Vertex e = v(x, y, Z, 0, 1, turn),
        f = v(X, y, Z, 1, 1, turn),
        g = v(X, Y, Z, 1, 0, turn),
        h = v(x, Y, Z, 0, 0, turn);
    solid(skin, a, b, c, d);
    solid(skin, f, e, h, g);
    solid(skin, e, a, d, h);
    solid(skin, b, f, g, c);
    solid(skin, d, c, g, h);
    solid(skin, e, f, b, a);
  }

  private void solid(Skin skin, Vertex... points) {
    var reversed = new ArrayList<Vertex>(Arrays.asList(points));
    Collections.reverse(reversed); // closed boxes use outward winding, one face only
    var clipped = clip(clip(reversed, 0, true), 1, false);
    if (clipped.size() >= 3) faces.add(new Face(skin, clipped, false));
  }

  private Vertex v(double x, double y, double z, double u, double v, int turn) {
    for (int i = 0; i < turn; i++) {
      double old = x;
      x = 1 - z;
      z = old;
    }
    return new Vertex(x, y, z, u, v);
  }

  private TransportMesh() {
    offset = 0;
  }
}
