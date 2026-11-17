package factorycraft.simulation.rail;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RailGeometryTest {
  static RailEnd end(int x, int y, int z, int h) {
    return new RailEnd(x, y, z, new RailHeading(h));
  }

  @Test
  void sixteenSourceHeadingFamiliesAreDistinctAndReverseExactly() {
    var set = new HashSet<RailVector>();
    for (int i = 0; i < 16; i++) {
      var h = new RailHeading(i);
      assertTrue(set.add(h.vector()));
      assertEquals(1, h.vector().length(), 1e-12);
      assertEquals(h, h.reverse().reverse());
      assertEquals(-1, h.vector().dot(h.reverse().vector()), 1e-12);
    }
    assertEquals(.5, new RailHeading(1).vector().x() / -new RailHeading(1).vector().z(), 1e-12);
  }

  @Test
  void everyStraightHeadingUsesExactCenteredEndpoints() {
    for (int i = 0; i < 16; i++) {
      var h = new RailHeading(i);
      var v = h.vector();
      int x =
          (int)
              Math.round(v.x() * Math.sqrt(i % 4 == 1 || i % 4 == 3 ? 5 : i % 4 == 2 ? 2 : 1) * 8);
      int z =
          (int)
              Math.round(v.z() * Math.sqrt(i % 4 == 1 || i % 4 == 3 ? 5 : i % 4 == 2 ? 2 : 1) * 8);
      var c = RailCurve.plan(end(0, 70, 0, i), end(x, 70, z, i));
      assertEquals(Math.hypot(x, z), c.length(), 1e-8);
      assertEquals(.5, c.sample(0, false).position().x(), 1e-9);
      assertEquals(70.125, c.sample(0, false).position().y(), 1e-9);
      assertEquals(1, c.sample(0, false).tangent().dot(h.vector()), 1e-9);
    }
  }

  @Test
  void sourceStraightDiagonalAndHalfDiagonalPrices() {
    assertEquals(1, RailCurve.plan(end(0, 0, 0, 0), end(0, 0, -2, 0)).cost());
    assertEquals(1, RailCurve.plan(end(0, 0, 0, 2), end(2, 0, -2, 2)).cost());
    assertEquals(2, RailCurve.plan(end(0, 0, 0, 1), end(2, 0, -4, 1)).cost());
  }

  @Test
  void mirroredQuarterTurnsRetainThirteenTileMinimumRadius() {
    for (int sign : new int[] {-1, 1}) {
      var c = RailCurve.plan(end(0, 0, 0, 0), end(sign * 24, 0, -24, sign > 0 ? 4 : 12));
      assertTrue(c.minimumRadius() >= 13);
      assertEquals(1, c.sample(0, false).tangent().dot(new RailHeading(0).vector()), 1e-9);
      assertEquals(
          1,
          c.sample(c.length(), false).tangent().dot(new RailHeading(sign > 0 ? 4 : 12).vector()),
          1e-9);
    }
  }

  @Test
  void halfDiagonalCurvedFamiliesConnectSmoothly() {
    for (int i = 0; i < 16; i++) {
      var a = new RailHeading(i).vector();
      var b = new RailHeading(i + 1).vector();
      int x = (int) Math.round((a.x() + b.x()) * 24), z = (int) Math.round((a.z() + b.z()) * 24);
      var c = RailCurve.plan(end(0, 2, 0, i), end(x, 2, z, i + 1));
      assertTrue(c.minimumRadius() >= 13);
      assertEquals(1, c.sample(c.length(), false).tangent().dot(b), 1e-8);
    }
  }

  @Test
  void compactImpossibleCornerAndFoldAreRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> RailCurve.plan(end(0, 0, 0, 0), end(3, 0, -3, 4)));
    assertThrows(
        IllegalArgumentException.class, () -> RailCurve.plan(end(0, 0, 0, 0), end(0, 0, 20, 0)));
  }

  @Test
  void SBendIsContinuousAndArcSamplesHaveConsistentDistance() {
    var c = RailCurve.plan(end(0, 0, 0, 0), end(8, 0, -48, 0));
    assertTrue(c.minimumRadius() >= 13);
    var last = c.sample(0, false).position();
    for (double d = .25; d < c.length(); d += .25) {
      var p = c.sample(d, false);
      assertEquals(.25, last.distance(p.position()), .003);
      assertEquals(0, p.tangent().dot(p.normal()), 1e-8);
      assertTrue(p.normal().y() > 0, "bogie normal must point above the track");
      last = p.position();
    }
  }

  @Test
  void smoothInclineAndDeclineHaveFlatEndpointTangents() {
    for (int dy : new int[] {-4, 4}) {
      var c = RailCurve.plan(end(0, 70, 0, 0), end(0, 70 + dy, -32, 0));
      assertEquals(0, c.sample(0, false).tangent().y(), 1e-10);
      assertEquals(0, c.sample(c.length(), false).tangent().y(), 1e-10);
      assertTrue(c.maximumGrade() <= .25);
      assertTrue(c.length() > 32);
    }
  }

  @Test
  void steepSlopeAndLongOrNonfiniteGeometryAreRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> RailCurve.plan(end(0, 0, 0, 0), end(0, 10, -20, 0)));
    assertThrows(
        IllegalArgumentException.class, () -> RailCurve.plan(end(0, 0, 0, 0), end(0, 0, -129, 0)));
    assertThrows(IllegalArgumentException.class, () -> new RailVector(Double.NaN, 0, 0));
  }

  @Test
  void reverseTraversalPreservesPositionAndFlipsTangent() {
    var c = RailCurve.plan(end(0, 7, 0, 0), end(24, 10, -24, 4));
    var r = c.reverse();
    assertEquals(c.length(), r.length(), 1e-8);
    for (double d = 0; d < c.length(); d += .3) {
      var a = c.sample(d, false);
      var b = r.sample(c.length() - d, false);
      assertTrue(a.position().distance(b.position()) < .001);
      assertEquals(-1, a.tangent().dot(b.tangent()), 1e-5);
    }
  }

  @Test
  void cachedMeshIsClippedToRealThinCellsAndHasThreeMaterials() {
    var c = RailCurve.plan(end(0, 4, 0, 0), end(12, 6, -40, 0));
    var mesh = RailMesh.build(c);
    var materials = new HashSet<Integer>();
    for (var entry : mesh.entrySet())
      for (var face : entry.getValue()) {
        materials.add(face.material());
        for (var p : face.vertices()) {
          assertTrue(p.x() >= -1e-8 && p.x() <= 1 + 1e-8);
          assertTrue(p.y() >= -1e-8 && p.y() <= 1 + 1e-8);
          assertTrue(p.z() >= -1e-8 && p.z() <= 1 + 1e-8);
        }
      }
    assertEquals(Set.of(0, 1, 2), materials);
    var f = new RailFootprint(c);
    assertTrue(f.bed.keySet().containsAll(mesh.keySet()), "mesh leaves registered physical bed");
  }

  @Test
  void technicalCorridorAndFutureTrainEnvelopeAreDistinctFromSolidBed() {
    var f = new RailFootprint(RailCurve.plan(end(0, 5, 0, 0), end(0, 5, -16, 0)));
    assertTrue(f.bed.containsKey(new RailFootprint.Cell(-1, 5, -8)));
    assertTrue(f.bed.containsKey(new RailFootprint.Cell(1, 5, -8)));
    assertFalse(f.bed.containsKey(new RailFootprint.Cell(2, 5, -8)));
    assertTrue(f.clearance.contains(new RailFootprint.Cell(0, 8, -8)));
    assertFalse(f.bed.containsKey(new RailFootprint.Cell(0, 8, -8)));
  }
}
