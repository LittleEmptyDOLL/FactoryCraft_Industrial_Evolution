package factorycraft.belts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Regression cost model of FactoryCraft's own 0.22 builder, not reference-project source. */
class LiftMeshComparisonTest {
  record P(double x, double y, double z) {
    P lerp(P b, double t) {
      return new P(x + (b.x - x) * t, y + (b.y - y) * t, z + (b.z - z) * t);
    }
  }

  static List<P> clip(List<P> in, double y, boolean above) {
    var out = new ArrayList<P>();
    if (in.isEmpty()) return out;
    var a = in.getLast();
    boolean old = above ? a.y >= y : a.y <= y;
    for (var b : in) {
      boolean next = above ? b.y >= y : b.y <= y;
      if (old != next) out.add(a.lerp(b, (y - a.y) / (b.y - a.y)));
      if (next) out.add(b);
      a = b;
      old = next;
    }
    return out;
  }

  static int count(List<P> p) {
    if (p.size() < 3) return 0;
    if (p.size() == 4) return area(p.get(0), p.get(1), p.get(2)) ? 2 : 0;
    int n = 0;
    for (int i = 1; i < p.size() - 1; i++) if (area(p.getFirst(), p.get(i), p.get(i + 1))) n += 2;
    return n;
  }

  static boolean area(P a, P b, P c) {
    double x = b.x - a.x, y = b.y - a.y, z = b.z - a.z, X = c.x - a.x, Y = c.y - a.y, Z = c.z - a.z;
    return Math.pow(y * Z - z * Y, 2) + Math.pow(z * X - x * Z, 2) + Math.pow(x * Y - y * X, 2)
        > 1e-12;
  }

  static P edge(BeltRoute r, int side, double t, int offset) {
    var a = r.point(0, r.length(0) * t);
    var b = r.point(1, r.length(1) * t);
    double k = side == 0 ? -.28 / .44 : 1 + .28 / .44;
    return new P(
        a.x() + (b.x() - a.x()) * k,
        a.y() + (b.y() - a.y()) * k - offset,
        a.z() + (b.z() - a.z()) * k);
  }

  static P lower(P p) {
    return new P(p.x, p.y - .1875, p.z);
  }

  static int old(BeltRoute r, int offset) {
    int n = 24 + (offset != 0 && offset != r.span ? 6 : 0), steps = 32 + Math.abs(r.span) * 16;
    for (int i = 0; i < steps; i++) {
      var a = edge(r, 0, (double) i / steps, offset);
      var b = edge(r, 1, (double) i / steps, offset);
      var c = edge(r, 1, (double) (i + 1) / steps, offset);
      var d = edge(r, 0, (double) (i + 1) / steps, offset);
      var faces = new ArrayList<List<P>>();
      faces.add(List.of(a, b, c, d));
      faces.add(List.of(lower(d), lower(c), lower(b), lower(a)));
      faces.add(List.of(a, d, lower(d), lower(a)));
      faces.add(List.of(c, b, lower(b), lower(c)));
      if (i == 0) faces.add(List.of(b, a, lower(a), lower(b)));
      if (i == steps - 1) faces.add(List.of(d, c, lower(c), lower(d)));
      for (var f : faces) n += count(clip(clip(f, 0, true), 1, false));
    }
    return n;
  }

  static int current(BeltRoute r, int y) {
    int n = 0;
    for (var f : TransportMesh.build(r, y, false).faces()) {
      var p = f.vertices().stream().map(v -> new P(v.x(), v.y(), v.z())).toList();
      n += f.doubleSided() ? count(p) : count(p) / 2;
    }
    return n;
  }

  @Test
  void boundedHeight16BaselineAndCurrentGeometryCosts() {
    var r = new BeltRoute(BeltRoute.Shape.LIFT, 16, 1);
    int old = 0, now = 0;
    for (int y = 0; y <= 16; y++) {
      old += old(r, y);
      now += current(r, y);
    }
    assertTrue(old > 0 && now > 0);
    for (int i = 0; i < 3; i++)
      for (int y = 0; y <= 16; y++) {
        old(r, y);
        current(r, y);
      }
    long a = System.nanoTime();
    for (int i = 0; i < 5; i++) for (int y = 0; y <= 16; y++) old(r, y);
    long b = System.nanoTime();
    for (int i = 0; i < 5; i++) for (int y = 0; y <= 16; y++) current(r, y);
    long c = System.nanoTime();
    System.out.printf(
        "FACTORYCRAFT_LIFT_COMPARISON: height16 turn1 actual baked quads old=%d"
            + " new=%d; vertex int[] payload old=%d new=%d bytes (not total heap); five whole-shaft"
            + " constructions baseline=%.3f current=%.3f ms; surfaces stay chunk-baked, no added"
            + " surface draw pass; no FPS claim%n",
        old, now, old * 128, now * 128, (b - a) / 1e6, (c - b) / 1e6);
  }
}
