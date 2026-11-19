package factorycraft.simulation.trains;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.client.trains.StockMeshes;
import factorycraft.simulation.rail.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TrainHotfixKernelTest {
  StockEnvelope hull(double x, double z, double yaw) {
    double a = Math.toRadians(yaw);
    var f = new RailVector(-Math.sin(a), 0, Math.cos(a));
    var s = new RailVector(-f.z(), 0, f.x());
    return new StockEnvelope(
        new RailVector(x, (StockGeometry.TOP + StockGeometry.BOTTOM) / 2, z),
        s,
        new RailVector(0, 1, 0),
        f);
  }

  @Test
  void sharedGeometryContainsEveryOriginalBodyVertexAndTheWheels() {
    for (var kind : StockSpec.values()) {
      for (var face : StockMeshes.BODY.get(kind))
        for (int i = 0; i < 12; i += 3) {
          var v = face.vertices();
          assertTrue(
              Math.abs(v[i]) <= StockGeometry.HALF_WIDTH
                  && Math.abs(v[i + 2]) <= StockGeometry.HALF_LENGTH
                  && v[i + 1] <= StockGeometry.top(kind)
                  && v[i + 1] >= StockGeometry.BOTTOM);
        }
      for (var face : StockMeshes.WHEEL)
        for (int i = 0; i < 12; i += 3)
          assertTrue(face.vertices()[i + 1] + .41 >= StockGeometry.BOTTOM);
    }
  }

  @Test
  void rotatedOuterCornerIsBroadphaseOnlyWhileAChassisRayStillHits() {
    var h = hull(0, 0, 45);
    var b = h.bounds();
    assertTrue(2.95 < b.x1() && 2.95 < b.z1());
    assertFalse(h.intersects(2.9, .5, 2.9, 3, 1, 3));
    assertTrue(
        Double.isNaN(h.rayFraction(new RailVector(2.95, 1, 6), new RailVector(2.95, 1, 2.95))));
    assertFalse(Double.isNaN(h.rayFraction(new RailVector(6, 1, 0), new RailVector(-6, 1, 0))));
    assertEquals(0, h.rayFraction(new RailVector(0, 1, 0), new RailVector(0, 1, 0)));
  }

  @Test
  void parallelDiagonalBodiesCanReserveOverlappingBroadBoxesButTrueContactsCannot() {
    var a = hull(0, 0, 45);
    double shift = 2.6 / Math.sqrt(2);
    var b = hull(shift, shift, 45);
    assertTrue(a.bounds().intersects(b.bounds()));
    assertFalse(a.intersects(b));
    var grid = new SpatialReservations();
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    assertTrue(grid.reserveShapes(first, List.of(new SweptHull(List.of(a)))));
    assertTrue(grid.reserveShapes(second, List.of(new SweptHull(List.of(b)))));
    assertFalse(grid.reserveShapes(second, List.of(new SweptHull(List.of(hull(.5, -.5, 45))))));
    assertFalse(grid.canReserveShapes(UUID.randomUUID(), List.of(new SweptHull(List.of(b)))));
    grid.release(first);
    assertTrue(grid.canReserveShapes(second, List.of(new SweptHull(List.of(hull(.5, -.5, 45))))));
  }

  @Test
  void orientedPairsMatchIndependentCornerProjectionAcrossYawAndPitch() {
    var random = new Random(311);
    for (int i = 0; i < 2000; i++) {
      var a = randomHull(random);
      var b = randomHull(random);
      var axes =
          new ArrayList<RailVector>(
              List.of(a.side(), a.up(), a.forward(), b.side(), b.up(), b.forward()));
      for (var x : List.of(a.side(), a.up(), a.forward()))
        for (var y : List.of(b.side(), b.up(), b.forward())) axes.add(x.cross(y));
      boolean expected = true;
      for (var axis : axes) {
        if (axis.length() < 1e-8) continue;
        double amin = Double.POSITIVE_INFINITY,
            amax = Double.NEGATIVE_INFINITY,
            bmin = amin,
            bmax = amax;
        for (int corner = 0; corner < 8; corner++) {
          double ap = corner(a, corner).dot(axis), bp = corner(b, corner).dot(axis);
          amin = Math.min(amin, ap);
          amax = Math.max(amax, ap);
          bmin = Math.min(bmin, bp);
          bmax = Math.max(bmax, bp);
        }
        if (amax < bmin - 1e-7 || bmax < amin - 1e-7) {
          expected = false;
          break;
        }
      }
      assertEquals(expected, a.intersects(b), "independent OBB pair " + i);
      assertEquals(a.intersects(b), b.intersects(a));
    }
  }

  private static RailVector corner(StockEnvelope h, int bits) {
    return h.center()
        .add(h.side().scale((bits % 2 == 0 ? -1 : 1) * h.halfWidth()))
        .add(h.up().scale(((bits & 2) == 0 ? -1 : 1) * h.halfHeight()))
        .add(h.forward().scale(((bits & 4) == 0 ? -1 : 1) * h.halfLength()));
  }

  private static StockEnvelope randomHull(Random r) {
    double yaw = r.nextDouble() * Math.PI * 2, pitch = (r.nextDouble() - .5) * .5;
    var f =
        new RailVector(
            -Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    var side = new RailVector(-f.z(), 0, f.x()).unit();
    var up = side.cross(f).unit();
    return new StockEnvelope(
        new RailVector(r.nextDouble() * 10 - 5, r.nextDouble() * 4, r.nextDouble() * 10 - 5),
        side,
        up,
        f,
        1.125,
        .8 + r.nextDouble(),
        3.5);
  }

  @Test
  void continuousTranslationStopsAtTheBodyAndIgnoresAnEmptyRotatedCorner() {
    var h = hull(0, 0, 0);
    double t = h.sweepFraction(5, .5, -.2, 5.6, 1, .2, -10, 0, 0);
    assertEquals((5 - StockGeometry.HALF_WIDTH) / 10, t, 1e-8);
    assertTrue(Double.isNaN(hull(0, 0, 45).sweepFraction(2.9, .5, 2.9, 3, 1, 3, 0, .1, 0)));
    assertEquals(
        0,
        h.sweepFraction(-.2, StockGeometry.TOP, -.2, .2, StockGeometry.TOP + 1, .2, 0, -1, 0),
        1e-8);
    assertTrue(
        Double.isNaN(
            h.sweepFraction(-.2, StockGeometry.TOP, -.2, .2, StockGeometry.TOP + 1, .2, 1, 0, 0)));
  }

  @Test
  void thresholdAndSourceMaximumCrossMetricEdgesWithoutNanOrDisplacementInflation() {
    var g = new RailGraph();
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    g.add(
        new RailGraph.Edge(
            a,
            RailCurve.plan(
                new RailEnd(0, 70, 0, new RailHeading(0)),
                new RailEnd(0, 70, -128, new RailHeading(0)))));
    g.add(
        new RailGraph.Edge(
            b,
            RailCurve.plan(
                new RailEnd(0, 70, -128, new RailHeading(0)),
                new RailEnd(0, 70, -256, new RailHeading(0)))));
    var path = new TrainPath(g, new RailGraph.Directed(a, false));
    assertTrue(path.extend(g, true, 0));
    var p = new TrainMotion.Parameters(2000, .5, 10, .0075, 10, 1.2);
    for (double speed : new double[] {60 / 216., 100 / 216., 120 / 216., 150 / 216., 1.2}) {
      var next = TrainMotion.tick(speed, p, true, false);
      assertTrue(next.speed() <= 1.2 && next.distance() <= 3.6 + 1e-8);
      var pose = BogiePose.sample(path, g, 127 + next.distance(), false);
      var box = StockEnvelope.of(pose).bounds();
      assertTrue(Double.isFinite(box.x0() + box.y0() + box.z0()));
      assertEquals(127 + next.distance(), .5 - pose.center().z(), 1e-7);
    }
  }

  @Test
  void orientedFutureClearanceRemovesEmptyAabbWedgesButKeepsTheChassis() {
    var curve =
        RailCurve.plan(
            new RailEnd(0, 70, 0, new RailHeading(2)),
            new RailEnd(32, 70, -32, new RailHeading(2)));
    var fp = new RailFootprint(curve);
    assertFalse(fp.clearance.contains(new RailFootprint.Cell(-3, 71, -3)));
    assertTrue(fp.clearance.contains(new RailFootprint.Cell(0, 71, 0)));
    assertFalse(fp.clearance.contains(new RailFootprint.Cell(0, 74, 0)));
  }

  @Test
  void continuousFutureGradeDoesNotInventAFoundationCollision() {
    var curve =
        RailCurve.plan(
            new RailEnd(248, 70, -792, new RailHeading(4)),
            new RailEnd(312, 78, -768, new RailHeading(4)));
    var fp = new RailFootprint(curve);
    for (var end : List.of(curve.start, curve.end))
      assertFalse(
          fp.intersects(
              new RailFootprint.Cell(end.x(), end.y() - 1, end.z()),
              new RailFootprint.Box(0, 0, 0, 1, 1, 1)));
    var a = hull(0, 0, 0);
    var b = hull(0, 3, 0);
    var sweep = new MotionHull(a, b);
    assertTrue(sweep.intersects(-.1, .5, 1.3, .1, 1, 1.4));
    assertFalse(sweep.intersects(1.3, .5, 1.3, 1.4, 1, 1.4));
  }

  @Test
  void publishedBedGeometryRemainsByteExactAcrossCurvesGradesAndHalfDiagonals() throws Exception {
    var curves =
        List.of(
            RailCurve.plan(
                new RailEnd(0, 70, 0, new RailHeading(0)),
                new RailEnd(0, 70, -128, new RailHeading(0))),
            RailCurve.plan(
                new RailEnd(0, 70, 0, new RailHeading(0)),
                new RailEnd(24, 70, -24, new RailHeading(4))),
            RailCurve.plan(
                new RailEnd(248, 70, -792, new RailHeading(4)),
                new RailEnd(312, 78, -768, new RailHeading(4))),
            RailCurve.plan(
                new RailEnd(0, 70, 0, new RailHeading(1)),
                new RailEnd(32, 70, -64, new RailHeading(1))));
    // Golden physical-cell/box records generated from published37cd998, not this implementation.
    var expected =
        List.of(
            "84bcd12c04d508a78960572b9663e2a33c861ec019ff423ac5748a32b5db8bee",
            "ebfbe84e346b72d9e92db8064487c12404266ab2dbb0840209f11e27fe5c0050",
            "6258f8f9cafc7e49eedc51e2889bbf9b1cae7e0d6dc897f62bd40900784786b9",
            "68af1e61f0a5eae177615ef9b6d281cc8b4cea1e983e189c647f189ea2b3ad7e");
    for (int n = 0; n < curves.size(); n++)
      assertEquals(
          expected.get(n),
          HexFormat.of()
              .formatHex(
                  java.security.MessageDigest.getInstance("SHA-256")
                      .digest(
                          new RailFootprint(curves.get(n))
                              .bed
                              .toString()
                              .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  }

  @Test
  void discoveryPaddingContainsTheFullBodyAtEveryAllowedYawAndGrade() {
    for (int yaw = 0; yaw < 360; yaw += 5)
      for (double pitch : new double[] {-Math.atan(.25), 0, Math.atan(.25)}) {
        double angle = Math.toRadians(yaw);
        var f =
            new RailVector(
                -Math.sin(angle) * Math.cos(pitch),
                Math.sin(pitch),
                Math.cos(angle) * Math.cos(pitch));
        var side = new RailVector(-f.z(), 0, f.x()).unit();
        var up = side.cross(f).unit();
        var hull =
            new StockEnvelope(
                up.scale((StockGeometry.TOP + StockGeometry.BOTTOM) / 2), side, up, f);
        var b = hull.bounds();
        assertTrue(
            Math.max(Math.abs(b.x0()), Math.abs(b.x1())) <= StockGeometry.DISCOVERY_HORIZONTAL
                && Math.max(Math.abs(b.z0()), Math.abs(b.z1()))
                    <= StockGeometry.DISCOVERY_HORIZONTAL
                && b.y0() >= -StockGeometry.DISCOVERY_BELOW
                && b.y1() <= StockGeometry.DISCOVERY_ABOVE,
            "section-discovery margin misses a source-sized pitched body");
      }
  }

  @Test
  void cameraYawSelectsAllSixteenSourceFamiliesIndependentlyOfTargetLocation() {
    for (int i = 0; i < 16; i++) {
      var f = new RailHeading(i).vector();
      double yaw = Math.toDegrees(Math.atan2(-f.x(), f.z()));
      assertEquals(i, RailHeading.fromYaw(yaw).index());
    }
    var first = new RailEnd(20, 70, -20, RailHeading.fromYaw(180));
    var turned = new RailEnd(20, 70, -20, RailHeading.fromYaw(90));
    assertNotEquals(first, turned);
    assertTrue(first.samePosition(turned));
  }

  @Test
  void hintBudgetIsBoundedAndTheSameHoveredCellInvalidatesWhenCameraRotates() {
    var hints = new EndpointHints();
    var start = new RailEnd(0, 70, 0, new RailHeading(0));
    var end = new RailEnd(20, 70, -20, new RailHeading(4));
    var id = UUID.randomUUID();
    var key = new EndpointHints.Key(start, end, -1, id, 0);
    assertTrue(hints.key(key));
    int[] calls = {0};
    for (int i = 0; i < 25; i++)
      assertEquals(
          1,
          hints.advance(
              1,
              p -> {
                calls[0]++;
                return new EndpointHints.Result(p, "ready", 3);
              }));
    assertEquals(25, calls[0]);
    assertTrue(hints.complete());
    assertEquals(
        0,
        hints.advance(
            1,
            p -> {
              throw new AssertionError();
            }));
    assertFalse(hints.key(key));
    assertTrue(
        hints.key(
            new EndpointHints.Key(start, new RailEnd(20, 70, -20, new RailHeading(3)), -1, id, 0)));
    assertFalse(hints.complete());
    assertNull(hints.get(12));
  }
}
