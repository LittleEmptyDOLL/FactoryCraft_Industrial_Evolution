package factorycraft.simulation.fluids;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class PipelineNetworkTest {
  private static PipelineNetwork<String>.Node add(
      PipelineNetwork<String> g, int x, int capacity, String key, int amount) {
    var c = new FluidCell<String>(capacity, () -> {});
    c.restore(key, amount);
    return g.add(new PipelineNetwork.Site(x, x, x, 0, 0, 0, 0), c);
  }

  private static void settle(PipelineNetwork<?> g) {
    for (int i = 0; i < 1000 && g.pending() > 0; i++) g.advance(64);
    assertEquals(0, g.pending());
  }

  @Test
  void commonCapacityImmediateFarEndAndExactCheckpointShares() {
    var g = new PipelineNetwork<String>();
    var nodes = new ArrayList<PipelineNetwork<String>.Node>();
    for (int i = 0; i < 100; i++) {
      nodes.add(add(g, i, i == 99 ? 16000 : 1000, null, 0));
      if (i > 0) g.connect(nodes.get(i - 1), nodes.get(i));
    }
    settle(g);
    var a = nodes.getFirst().port(0);
    assertEquals(115000, a.capacity());
    assertEquals(12345, a.fill("water", 12345, false));
    assertEquals(12345, nodes.getLast().port(1).amount());
    assertEquals(12345, nodes.stream().mapToInt(n -> n.owned().amount()).sum());
    assertEquals(1000, nodes.getLast().port(1).drain(1000, false));
    assertEquals(11345, a.amount());
  }

  @Test
  void splitUnloadAndRejoinNeverDuplicateRounding() {
    var g = new PipelineNetwork<String>();
    var a = add(g, 0, 1000, "water", 1);
    var b = add(g, 1, 16000, null, 0);
    var c = add(g, 2, 1000, null, 0);
    g.connect(a, b);
    g.connect(b, c);
    settle(g);
    var stale = b.port(1);
    g.active(b, false);
    assertEquals(0, stale.drain(100, false));
    settle(g);
    assertEquals(1, a.owned().amount() + b.owned().amount() + c.owned().amount());
    g.active(b, true);
    settle(g);
    assertEquals(1, c.port(0).amount());
    g.remove(b);
    settle(g);
    assertEquals(1, a.owned().amount() + b.owned().amount() + c.owned().amount());
  }

  @Test
  void incompatibleBridgeDoesNotMergeAndEmptyingReconciles() {
    var g = new PipelineNetwork<String>();
    var a = add(g, 0, 1000, "water", 100);
    var b = add(g, 1, 1000, null, 0);
    var c = add(g, 2, 1000, "oil", 200);
    g.connect(a, b);
    g.connect(b, c);
    settle(g);
    assertNotSame(a.segment(), c.segment());
    assertEquals(300, a.segment().amount() + c.segment().amount());
    assertTrue(a.segment().conflicts() > 0);
    assertEquals(100, a.port(0).drain(100, false));
    settle(g);
    assertSame(a.segment(), c.segment());
    assertEquals("oil", a.port(0).key());
    assertEquals(200, a.port(0).amount());
  }

  @Test
  void spanCountsPhysicalCoverageNotPipeNumberAndHeightIsIndependent() {
    for (int span : new int[] {319, 320, 321}) {
      var g = new PipelineNetwork<String>();
      var a = add(g, 0, 1000, "water", 17);
      var b = add(g, span - 1, 1000, null, 0);
      g.connect(a, b);
      settle(g);
      assertEquals(
          span > 320 ? PipelineNetwork.Status.TOO_LONG : PipelineNetwork.Status.READY,
          a.segment().status());
      assertEquals(17, a.segment().amount());
      if (span > 320) assertEquals(0, a.port(0).drain(17, false));
    }
  }

  @Test
  void retainedReferenceCannotDrainTwiceAndSimulationsLeaveVolume() {
    var g = new PipelineNetwork<String>();
    var a = add(g, 0, 1000, "water", 731);
    var b = add(g, 1, 1000, null, 0);
    g.connect(a, b);
    settle(g);
    var p = a.port(0);
    var q = b.port(0);
    assertEquals(731, p.drain(1000, true));
    assertEquals(731, q.amount());
    assertEquals(0, p.transferTo(q, 500, false));
    assertEquals(731, p.drain(1000, false));
    assertEquals(0, q.drain(1000, false));
    g.remove(a);
    assertEquals(0, p.fill("water", 10, false));
  }

  @Test
  void componentIdentityAndFilteredBidirectionalBoundariesStaySeparate() {
    record Fluid(String type, int grade) {}
    var g = new PipelineNetwork<Fluid>(Fluid::type, s -> {}, n -> {});
    var x = new FluidCell<Fluid>(1000, () -> {});
    x.restore(new Fluid("water", 1), 101);
    var y = new FluidCell<Fluid>(1000, () -> {});
    y.restore(new Fluid("water", 2), 203);
    var a = g.add(new PipelineNetwork.Site(0, 0, 0, 0, 0, 0, 0), x);
    var b = g.add(new PipelineNetwork.Site(1, 1, 1, 0, 0, 0, 0), y);
    g.connect(a, b);
    settle(g);
    assertNotSame(a.segment(), b.segment());
    assertEquals(304, a.owned().amount() + b.owned().amount());
    assertEquals(0, a.port(0).fill(new Fluid("water", 2), 1, false));
    b.port(0).drain(203, false);
    settle(g);
    assertSame(a.segment(), b.segment());
    g.filter(b, "oil");
    settle(g);
    assertNotSame(a.segment(), b.segment());
    assertEquals(101, a.owned().amount() + b.owned().amount());
  }

  @Test
  void cyclesBranchesVerticalHeightAndBudgetedRestartsConserveEveryMillibucket() {
    var g = new PipelineNetwork<String>();
    var n = new ArrayList<PipelineNetwork<String>.Node>();
    for (int i = 0; i < 40; i++) n.add(add(g, i, i % 7 == 0 ? 16000 : 1000, null, 0));
    for (int i = 0; i < 40; i++) g.connect(n.get(i), n.get((i + 1) % 40));
    for (int i = 0; i < 20; i++) g.connect(n.get(i), n.get(i + 20));
    settle(g);
    n.get(0).port(0).fill("water", 12347, false);
    long visits = g.visits();
    for (int i = 0; i < 100; i++) {
      assertEquals(1, n.get(39).port(0).drain(1, false));
      n.get(0).port(0).fill("water", 1, false);
    }
    assertEquals(visits, g.visits(), "ordinary volume mutations must not rebuild topology");
    var random = new Random(240);
    for (int i = 0; i < 150; i++) {
      var at = n.get(random.nextInt(n.size()));
      g.active(at, !at.active());
      assertTrue(g.advance(3) <= 3);
      settle(g);
      assertEquals(12347, n.stream().mapToLong(a -> a.owned().amount()).sum());
    }
    var vertical = new PipelineNetwork<String>();
    var a = add(vertical, 0, 1000, "water", 1);
    var b =
        vertical.add(
            new PipelineNetwork.Site(1, 0, 0, -64, 319, 0, 0),
            new FluidCell<String>(1000, () -> {}));
    vertical.connect(a, b);
    settle(vertical);
    assertEquals(PipelineNetwork.Status.READY, a.segment().status());
    assertEquals(1, b.port(0).amount());
  }

  @Test
  void independentSnapshotsReconstructAndRetiredReferencesCannotOwnTheirOldPool() {
    var g = new PipelineNetwork<String>();
    var n = new ArrayList<PipelineNetwork<String>.Node>();
    for (int i = 0; i < 4; i++) {
      n.add(add(g, i, 1000, null, 0));
      if (i > 0) g.connect(n.get(i - 1), n.get(i));
    }
    settle(g);
    n.get(0).port(0).fill("water", 3999, false);
    var stale = n.get(0).port(0);
    var snapshot = n.stream().map(a -> a.owned().amount()).toList();
    var rebuilt = new PipelineNetwork<String>();
    var restored = new ArrayList<PipelineNetwork<String>.Node>();
    for (int i = 0; i < 4; i++) {
      restored.add(add(rebuilt, i, 1000, "water", snapshot.get(i)));
      if (i > 0) rebuilt.connect(restored.get(i - 1), restored.get(i));
    }
    settle(rebuilt);
    assertEquals(3999, restored.get(3).port(0).amount());
    g.remove(n.get(0));
    settle(g);
    assertEquals(0, stale.drain(9999, false));
    assertEquals(3999, n.stream().mapToLong(a -> a.owned().amount()).sum());
  }

  @Test
  void incompatibleBidirectionalFiltersOnOnePhysicalCellQuarantineWithoutVoiding() {
    var g = new PipelineNetwork<String>();
    var a = add(g, 0, 1000, "water", 731);
    g.filter(a, PipelineNetwork.REJECT_ALL);
    settle(g);
    assertEquals(PipelineNetwork.Status.FILTER_CONFLICT, a.segment().status());
    assertEquals(731, a.owned().amount());
    assertEquals(0, a.port(0).drain(731, false));
    g.filter(a, null);
    settle(g);
    assertEquals(731, a.port(0).drain(731, false));
  }
}
