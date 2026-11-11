package factorycraft.simulation.fluids;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;

class PipelinePerformanceTest {
  private static ArrayList<PipelineNetwork<String>.Node> chain(
      PipelineNetwork<String> g, int count) {
    var ns = new ArrayList<PipelineNetwork<String>.Node>();
    for (int i = 0; i < count; i++) {
      var c = new FluidCell<String>(1000, () -> {});
      if (i == 0) c.restore("water", 777);
      var n = g.add(new PipelineNetwork.Site(i, i % 200, i % 200, 0, 0, i / 200, i / 200), c);
      ns.add(n);
      if (i > 0) g.connect(ns.get(i - 1), n);
    }
    return ns;
  }

  private static void settle(PipelineNetwork<?> g, int budget) {
    int guard = 0;
    while (g.pending() > 0) {
      assertTrue(g.advance(budget) <= budget);
      assertTrue(++guard < 100000);
    }
  }

  private static long mass(List<PipelineNetwork<String>.Node> ns) {
    return ns.stream().mapToLong(n -> n.owned().amount()).sum();
  }

  @Test
  void tenThousandContentOperationsDoNotRebuildOrFreeze() {
    var g = new PipelineNetwork<String>();
    var ns = chain(g, 10000);
    settle(g, 127);
    var p = ns.getFirst().port(0);
    long visits = g.visits(), builds = g.rebuilds(), freeze = g.freezes();
    for (int i = 0; i < 10000; i++) {
      assertEquals(1, p.drain(1, false));
      assertEquals(1, p.fill("water", 1, false));
      g.contentChanged(ns.getFirst());
    }
    assertEquals(visits, g.visits());
    assertEquals(builds, g.rebuilds());
    assertEquals(freeze, g.freezes());
    assertEquals(777, mass(ns));
  }

  @Test
  void finalizeIsBudgetedAndNeverExposesPartialOwners() {
    var g = new PipelineNetwork<String>();
    var ns = chain(g, 1000);
    int loops = 0;
    while (g.pending() > 0) {
      assertTrue(g.advance(17) <= 17);
      assertEquals(777, mass(ns));
      if (g.rebuilds() == 0) for (var n : ns) assertEquals(0, n.port(0).drain(1, true));
      assertTrue(++loops < 10000);
    }
    assertTrue(g.finalizeWork() >= 1000);
    assertEquals(777, ns.getLast().port(0).amount());
  }

  @Test
  void unrelatedTopologyDoesNotStarveAnUnfinishedComponent() {
    var g = new PipelineNetwork<String>();
    var ns = chain(g, 1000);
    g.advance(100);
    long aborted = g.aborts();
    for (int i = 0; i < 1000 && ns.getFirst().segment() == null; i++) {
      var n =
          g.add(
              new PipelineNetwork.Site(10000 + i, 0, 0, 1, 1, 0, 0),
              new FluidCell<String>(1000, () -> {}));
      g.advance(32);
      g.remove(n);
    }
    assertTrue(g.aborts() <= aborted + 1);
    assertNotNull(ns.getFirst().segment());
    settle(g, 32);
    assertEquals(777, mass(ns));
  }

  @Test
  void abortDuringStagingPreservesIndependentSnapshotOwners() {
    var g = new PipelineNetwork<String>();
    var ns = chain(g, 200);
    int guard = 0;
    g.advance(1);
    while (g.pending() >= 201) {
      g.advance(1);
      assertTrue(++guard < 10000);
    }
    assertEquals(0, g.rebuilds());
    assertEquals(777, mass(ns));
    g.active(ns.getFirst(), false);
    settle(g, 11);
    assertEquals(777, mass(ns));
    assertEquals(0, ns.getFirst().port(0).drain(10, false));
    g.active(ns.getFirst(), true);
    settle(g, 11);
    assertEquals(777, ns.getLast().port(0).amount());
    assertTrue(g.aborts() > 0);
  }

  @Test
  void checkpointDuringUnpublishedStageRestoresExactly() {
    var g = new PipelineNetwork<String>();
    var ns = chain(g, 100);
    g.advance(1);
    while (g.pending() >= 101) g.advance(1);
    var re = new PipelineNetwork<String>();
    PipelineNetwork<String>.Node prev = null, last = null;
    for (var n : ns) {
      var c = new FluidCell<String>(1000, () -> {});
      c.restore(n.owned().key(), n.owned().amount());
      last = re.add(n.site, c);
      if (prev != null) re.connect(prev, last);
      prev = last;
    }
    settle(re, 13);
    assertEquals(777, last.port(0).amount());
    settle(g, 13);
    assertEquals(777, mass(ns));
  }

  @Test
  void integerProjectionMatchesExactArithmeticWithoutOverflowOrRoundingLoss() {
    for (long total : new long[] {0, 1, 777, Long.MAX_VALUE})
      for (long cap : new long[] {1, 1000, Long.MAX_VALUE})
        for (long p : new long[] {0, 1, cap / 2, cap}) {
          if (p > cap) continue;
          long expected =
              BigInteger.valueOf(total)
                  .multiply(BigInteger.valueOf(p))
                  .divide(BigInteger.valueOf(cap))
                  .longValueExact();
          assertEquals(expected, PipelineNetwork.ratio(total, p, cap));
        }
  }

  @Test
  void addingRedundantConnectionAndTrueLeafDoesNotRebuildOrFreeze() {
    var g = new PipelineNetwork<String>();
    var ns = chain(g, 1000);
    settle(g, 64);
    long builds = g.rebuilds(), frozen = g.freezes();
    var owner = ns.getFirst().segment();
    g.connect(ns.getFirst(), ns.getLast());
    assertSame(owner, ns.getLast().segment());
    var c = new FluidCell<String>(16000, () -> {});
    var n = g.add(new PipelineNetwork.Site(1000, 1, 1, 1, 1, 1, 1), c);
    g.connect(n, ns.getLast());
    assertSame(owner, n.segment());
    assertEquals(777, mass(ns) + n.owned().amount());
    g.remove(n);
    assertEquals(777, mass(ns) + n.owned().amount());
    assertEquals(builds, g.rebuilds());
    assertEquals(frozen, g.freezes());
    assertEquals(0, n.port(0).drain(777, false));
  }

  @Test
  void changingConsumerBoundaryDuringStagingRefreshesMetadataWithoutExposingShadowVolume() {
    var consumer = new boolean[] {false};
    var prepared = new boolean[] {false};
    var cached = new java.util.IdentityHashMap<PipelineNetwork<String>.Segment, Boolean>();
    var g =
        new PipelineNetwork<String>(
            k -> k,
            s -> {
              if (!s.valid()) cached.remove(s);
            },
            n -> {},
            (s, n) -> {
              if (n.site.order() == 0) {
                prepared[0] = true;
                cached.put(s, consumer[0]);
              }
            });
    var ns = chain(g, 100);
    while (!prepared[0]) g.advance(1);
    assertNull(ns.getFirst().segment());
    assertEquals(777, mass(ns));
    consumer[0] = true;
    g.boundaryChanged(ns.getFirst());
    settle(g, 7);
    assertTrue(cached.get(ns.getFirst().segment()));
    assertEquals(777, ns.getLast().port(0).amount());
    long builds = g.rebuilds(), frozen = g.freezes();
    g.boundaryChanged(ns.getFirst());
    assertEquals(builds, g.rebuilds());
    assertEquals(frozen, g.freezes());
  }
}
