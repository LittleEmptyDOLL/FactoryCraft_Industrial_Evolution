package factorycraft.simulation.trains;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.rail.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TrainKernelTest {
  @Test
  void changedIntentReplacesOnlyUnoccupiedLookaheadNotAnEnteredTurnout() {
    var graph = new RailGraph();
    var first = UUID.randomUUID();
    var straight = UUID.randomUUID();
    var right = UUID.randomUUID();
    graph.add(new RailGraph.Edge(first, curve(0, -32, 70, 70)));
    graph.add(new RailGraph.Edge(straight, curve(-32, -64, 70, 70)));
    graph.add(
        new RailGraph.Edge(
            right,
            RailCurve.plan(
                new RailEnd(0, 70, -32, new RailHeading(0)),
                new RailEnd(24, 70, -56, new RailHeading(4)))));
    var path = new TrainPath(graph, new RailGraph.Directed(first, false));
    assertTrue(path.extend(graph, true, 0));
    assertEquals(straight, path.legs().getLast().edge().edge());
    path.discardUnoccupied(20, 31, 1);
    assertEquals(1, path.legs().size());
    assertTrue(path.extend(graph, true, 1));
    assertEquals(right, path.legs().getLast().edge().edge());
    path.discardUnoccupied(20, 33, 1);
    assertEquals(right, path.legs().getLast().edge().edge());
    assertTrue(path.connected(graph));
  }

  @Test
  void exactSourceRecurrenceAndPureBrakingMatchThreeSixtiethSteps() {
    var p = new TrainMotion.Parameters(2000, .5, 10, .0075, 10, 1.2);
    double v = .2, dist = 0;
    for (int i = 0; i < 3; i++) {
      v = (Math.max(0, v - .5 / 2000) + 10.0 / 2000) * (1 - .0075 / 2);
      dist += v;
    }
    var s = TrainMotion.tick(.2, p, true, false);
    assertEquals(v, s.speed(), 1e-14);
    assertEquals(dist, s.distance(), 1e-14);
    assertEquals(.185, TrainMotion.tick(.2, p, false, true).speed(), 1e-14);
  }

  @Test
  void routeChoiceUsesBranchGeometryRatherThanIdenticalEndpointTangents() {
    var g = new RailGraph();
    var a = UUID.randomUUID();
    g.add(new RailGraph.Edge(a, curve(0, -32, 70, 70)));
    var start = new RailEnd(0, 70, -32, new RailHeading(0));
    for (int sign : new int[] {-1, 0, 1}) {
      var end =
          new RailEnd(
              sign * 24,
              70,
              sign == 0 ? -64 : -56,
              new RailHeading(sign < 0 ? 12 : sign > 0 ? 4 : 0));
      g.add(new RailGraph.Edge(UUID.randomUUID(), RailCurve.plan(start, end)));
    }
    for (int choice : new int[] {-1, 0, 1}) {
      var path = new TrainPath(g, new RailGraph.Directed(a, false));
      assertTrue(path.extend(g, true, choice));
      assertEquals(choice, Math.signum(path.sample(g, path.max()).position().x() - .5));
    }
  }

  @Test
  void headwayReadsCanonicalReverseClaimsWithoutBlockingBehind() {
    var g = new RailGraph();
    var e = UUID.randomUUID();
    g.add(new RailGraph.Edge(e, curve(0, -64, 70, 70)));
    var path = new TrainPath(g, new RailGraph.Directed(e, true));
    var occ = new TrackOccupancy();
    var me = UUID.randomUUID();
    occ.reserve(UUID.randomUUID(), List.of(new TrackOccupancy.Interval(e, 20, 27)));
    assertEquals(27, occ.freeDistance(me, path, 3, 10, 1, 100), 1e-9);
    assertEquals(100, occ.freeDistance(me, path, 3, 10, -1, 100));
  }

  @Test
  void spatialClaimsConflictAcrossEdgesButNotAcrossElevationsAndFailureIsAtomic() {
    var s = new SpatialReservations();
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    var box = new SpatialReservations.Bounds(-1, 70, -3, 1, 74, 3);
    assertTrue(s.reserve(a, List.of(box)));
    assertFalse(s.reserve(b, List.of(new SpatialReservations.Bounds(-3, 70, -1, 3, 74, 1))));
    assertTrue(s.reserve(b, List.of(new SpatialReservations.Bounds(-3, 75, -1, 3, 79, 1))));
    assertFalse(s.reserve(b, List.of(box)));
    s.release(a);
    assertTrue(s.reserve(b, List.of(box)));
  }

  @Test
  void pitchedEnvelopeRejectsBroadphaseOnlyGroundAndIncludesActualBody() {
    var g = new RailGraph();
    var id = UUID.randomUUID();
    g.add(new RailGraph.Edge(id, curve(0, -64, 70, 78)));
    var path = new TrainPath(g, new RailGraph.Directed(id, false));
    var pose = BogiePose.sample(path, g, 32, false);
    var env = StockEnvelope.of(pose);
    var c = env.center();
    assertTrue(
        env.intersects(c.x() - .1, c.y() - .1, c.z() - .1, c.x() + .1, c.y() + .1, c.z() + .1));
    assertFalse(env.intersects(c.x() - 1, c.y() - 10, c.z() - 1, c.x() + 1, c.y() - 9, c.z() + 1));
  }

  static RailCurve curve(int z0, int z1, int y0, int y1) {
    return RailCurve.plan(
        new RailEnd(0, y0, z0, new RailHeading(0)), new RailEnd(0, y1, z1, new RailHeading(0)));
  }

  @Test
  void sixtyHzConversionUsesThreePaidSourceStepsWithoutInventingGravity() {
    var p = new TrainMotion.Parameters(3000, 1, 13, .0075, 10, 1.2);
    var step = TrainMotion.tick(0, p, true, false);
    assertTrue(step.speed() > 0);
    assertTrue(step.distance() > step.speed());
    var reverse =
        TrainMotion.tick(0, new TrainMotion.Parameters(3000, 1, 13, .0075, 6, 1.2), true, false);
    assertTrue(reverse.speed() < step.speed());
    assertEquals(0, TrainMotion.tick(0, p, false, false).distance());
    var brake = TrainMotion.tick(.1, p, false, true);
    assertTrue(brake.speed() < .1);
    assertEquals(
        1.2,
        TrainMotion.tick(1.2, new TrainMotion.Parameters(2000, 0, 10, 0, 10, 1.2), true, false)
            .speed());
  }

  @Test
  void sourceMassAndCapacityAreConstantWhenCargoIsLoaded() {
    assertEquals(1000, StockSpec.CARGO.weight);
    assertEquals(1000, StockSpec.FLUID.weight);
    assertEquals(40, StockSpec.CARGO.slots);
    assertEquals(500000, StockSpec.FLUID_CAPACITY);
    assertEquals(3, StockSpec.LOCOMOTIVE.slots);
    assertEquals(7, StockSpec.SPACING);
  }

  @Test
  void bogiesRemainContinuousAcrossSavedDirectedEdgesAndGrades() {
    var g = new RailGraph();
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    g.add(new RailGraph.Edge(a, curve(0, -32, 70, 70)));
    g.add(new RailGraph.Edge(b, curve(-32, -96, 70, 78)));
    var path = new TrainPath(g, new RailGraph.Directed(a, false));
    assertTrue(path.extend(g, true, 0));
    assertTrue(path.connected(g));
    var before = BogiePose.sample(path, g, 31.999, false);
    var after = BogiePose.sample(path, g, 32.001, false);
    assertTrue(before.center().distance(after.center()) < .003);
    assertTrue(after.front().position().y() > after.rear().position().y());
    var saved = new TrainPath(path.legs());
    assertEquals(after, BogiePose.sample(saved, g, 32.001, false));
    var back = BogiePose.sample(path, g, 32.001, true);
    assertEquals(-1, back.forward().dot(after.forward()), 1e-9);
  }

  @Test
  void reservationFailureLeavesOldClaimsAndIndependentIntervalsIntact() {
    var occ = new TrackOccupancy();
    var e = UUID.randomUUID();
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    assertTrue(occ.reserve(a, List.of(new TrackOccupancy.Interval(e, 0, 7))));
    assertTrue(occ.reserve(b, List.of(new TrackOccupancy.Interval(e, 8, 15))));
    assertFalse(occ.reserve(a, List.of(new TrackOccupancy.Interval(e, 2, 9))));
    assertEquals(0, occ.intervals(a).getFirst().min());
    assertEquals(Set.of(a, b), occ.owners(e));
    occ.release(b);
    assertTrue(occ.reserve(a, List.of(new TrackOccupancy.Interval(e, 2, 9))));
  }

  @Test
  void reversedEdgeClaimsHaveCanonicalCoordinates() {
    var g = new RailGraph();
    var id = UUID.randomUUID();
    g.add(new RailGraph.Edge(id, curve(0, -32, 70, 70)));
    var path = new TrainPath(g, new RailGraph.Directed(id, true));
    var span = TrackOccupancy.span(path, 2, 9);
    assertEquals(23, span.getFirst().min(), 1e-8);
    assertEquals(30, span.getFirst().max(), 1e-8);
  }

  @Test
  void unknownAndChangedRoutesFailClosedAndStoppingBoundsGrowWithMass() {
    var g = new RailGraph();
    var id = UUID.randomUUID();
    g.add(new RailGraph.Edge(id, curve(0, -32, 70, 70)));
    var path = new TrainPath(g, new RailGraph.Directed(id, false));
    g.remove(id);
    assertFalse(path.connected(g));
    assertThrows(IllegalArgumentException.class, () -> path.sample(g, 4));
    assertTrue(
        TrainMotion.stoppingDistance(1, 4000, 13) > TrainMotion.stoppingDistance(1, 2000, 13));
  }
}
