package factorycraft.simulation.rail;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RailGraphTest {
  static RailEnd p(int x, int y, int z, int h) {
    return new RailEnd(x, y, z, new RailHeading(h));
  }

  static RailGraph.Edge edge(RailEnd a, RailEnd b) {
    return new RailGraph.Edge(UUID.randomUUID(), RailCurve.plan(a, b));
  }

  @Test
  void junctionHasExplicitForwardBranchesWithoutCreatingAReverseShortcut() {
    var g = new RailGraph();
    var in = edge(p(0, 0, 16, 0), p(0, 0, 0, 0));
    var straight = edge(p(0, 0, 0, 0), p(0, 0, -32, 0));
    var branch = edge(p(0, 0, 0, 0), p(24, 0, -24, 4));
    for (var e : List.of(in, straight, branch)) g.add(e);
    assertEquals(2, g.successors(new RailGraph.Directed(in.id(), false)).size());
    assertEquals(
        List.of(new RailGraph.Directed(in.id(), true)),
        g.successors(new RailGraph.Directed(branch.id(), true)));
    assertFalse(RailGraph.conflicts(straight.curve(), branch.curve()));
  }

  @Test
  void RemovingAndRejoiningTouchesOnlyTheExplicitEndpoints() {
    var g = new RailGraph();
    var a = edge(p(0, 0, 0, 0), p(0, 0, -16, 0));
    var b = edge(p(0, 0, -16, 0), p(0, 0, -32, 0));
    g.add(a);
    g.add(b);
    long r = g.revisions();
    assertEquals(1, g.successors(new RailGraph.Directed(a.id(), false)).size());
    assertNotNull(g.remove(b.id()));
    assertNull(g.remove(b.id()));
    assertEquals(r + 1, g.revisions());
    assertTrue(g.successors(new RailGraph.Directed(a.id(), false)).isEmpty());
    g.add(b);
    assertEquals(1, g.successors(new RailGraph.Directed(a.id(), false)).size());
  }

  @Test
  void sameHeightCrossingConflictsButBridgeIsIndependent() {
    var a = edge(p(0, 0, 0, 4), p(32, 0, 0, 4));
    var b = edge(p(16, 0, 16, 0), p(16, 0, -16, 0));
    var over = edge(p(16, 5, 16, 0), p(16, 5, -16, 0));
    assertTrue(RailGraph.conflicts(a.curve(), b.curve()));
    assertFalse(RailGraph.conflicts(a.curve(), over.curve()));
    var g = new RailGraph();
    g.add(a);
    g.add(over);
    assertTrue(g.successors(new RailGraph.Directed(a.id(), false)).isEmpty());
  }

  @Test
  void coincidentCurvesCannotBuyDuplicateTopology() {
    var a = edge(p(0, 0, 0, 0), p(0, 0, -16, 0));
    assertTrue(RailGraph.conflicts(a.curve(), a.curve()));
    assertTrue(RailGraph.conflicts(a.curve(), a.curve().reverse()));
  }

  @Test
  void twoBlockCenterConventionDoesNotShiftParallelTracks() {
    var a = edge(p(0, 0, 0, 0), p(0, 0, -32, 0));
    var b = edge(p(4, 0, 0, 0), p(4, 0, -32, 0));
    assertFalse(RailGraph.conflicts(a.curve(), b.curve()));
    assertEquals(
        4,
        a.curve().sample(12, false).position().distance(b.curve().sample(12, false).position()),
        1e-8);
  }

  @Test
  void unrelatedGeometricContactDoesNotCreateAJunction() {
    var g = new RailGraph();
    var a = edge(p(0, 0, 0, 0), p(0, 0, -16, 0));
    var b = edge(p(0, 0, -16, 4), p(16, 0, -16, 4));
    g.add(a);
    g.add(b);
    assertTrue(g.successors(new RailGraph.Directed(a.id(), false)).isEmpty());
    assertFalse(RailGraph.joined(a.curve(), b.curve()));
  }

  @Test
  void readOnlyQueriesNeverRebuildTopology() {
    var g = new RailGraph();
    var a = edge(p(0, 0, 0, 0), p(0, 0, -16, 0));
    g.add(a);
    long revision = g.revisions();
    for (int i = 0; i < 10000; i++) {
      assertTrue(g.successors(new RailGraph.Directed(a.id(), false)).isEmpty());
      assertEquals(1, g.at(a.curve().end).size());
    }
    assertEquals(revision, g.revisions());
  }

  @Test
  void shortContinuousApproachesAreNotUnrelatedCrossings() {
    var g = new RailGraph();
    var a = edge(p(0, 0, 0, 0), p(0, 0, -2, 0));
    var b = edge(p(0, 0, -2, 0), p(0, 0, -4, 0));
    var next = RailCurve.plan(p(0, 0, -4, 0), p(0, 0, -6, 0));
    g.add(a);
    g.add(b);
    var allowed = g.approaches(next, 8);
    assertTrue(allowed.containsAll(Set.of(a.id(), b.id())));
    assertTrue(RailGraph.conflicts(next, a.curve()));
    assertFalse(RailGraph.conflicts(next, a.curve(), allowed.contains(a.id())));
    assertTrue(
        g.successors(new RailGraph.Directed(b.id(), false)).isEmpty(),
        "approach inspection must not create a route before commit");
  }
}
