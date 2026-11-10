package factorycraft.simulation.power;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class PowerTopologyTest {
  private static final class Store implements PowerTopology.Receiver {
    int energy, calls;
    final int capacity;

    Store(int cap) {
      capacity = cap;
    }

    public int receive(int n, boolean sim) {
      calls++;
      int a = Math.min(n, capacity - energy);
      if (!sim) energy += a;
      return a;
    }
  }

  private static PowerFootprint rect(int x, int y, int z, int w, int d) {
    return new PowerFootprint(x, x + w - 1, y, z, z + d - 1);
  }

  private static PowerTopology.Node<Object> pole(PowerTopology<Object> g, int x, int z) {
    return pole(g, x, 0, z, PoleSpec.SMALL);
  }

  private static PowerTopology.Node<Object> pole(
      PowerTopology<Object> g, int x, int y, int z, PoleSpec s) {
    return g.add(new Object(), rect(x, y, z, s.width, s.width), s, PowerTopology.Kind.POLE, null);
  }

  private static PowerTopology.Node<Object> source(PowerTopology<Object> g, int x, int y, int z) {
    return g.add(new Object(), new PowerPoint(x, y, z), PowerTopology.Kind.SOURCE, null);
  }

  private static PowerTopology.Node<Object> consumer(
      PowerTopology<Object> g, int x, int y, int z, Store s) {
    return g.add(new Object(), new PowerPoint(x, y, z), PowerTopology.Kind.CONSUMER, s);
  }

  private static void finish(PowerTopology<?> g) {
    int steps = 0;
    while (g.pending()) {
      assertTrue(g.advance(17) <= 17);
      assertTrue(++steps < 1000000);
    }
  }

  @Test
  void exactNormalQualityFactorioValues() {
    assertArrayEquals(
        new int[] {5, 7, 4, 18},
        Arrays.stream(PoleSpec.values()).mapToInt(p -> p.supply).toArray());
    assertArrayEquals(
        new double[] {7.5, 9, 32, 18},
        Arrays.stream(PoleSpec.values()).mapToDouble(p -> p.wire).toArray());
  }

  @Test
  void squareCoverageContainsExactlyTheDeclaredNumberOfCellsEvenForEvenFootprints() {
    for (var s : PoleSpec.values()) {
      var p = rect(-17, 30, -17, s.width, s.width);
      int cells = 0;
      for (int x = -40; x < 10; x++)
        for (int z = -40; z < 10; z++) if (rect(x, 30, z, 1, 1).suppliedBy(p, s, 4)) cells++;
      assertEquals(s.supply * s.supply, cells, s.name());
    }
  }

  @Test
  void onlyTheOverlappingCornerOfARefineryNeedsPower() {
    var pole = rect(3, 20, 3, 1, 1);
    var refinery = rect(5, 20, 5, 5, 5);
    assertTrue(refinery.suppliedBy(pole, PoleSpec.SMALL, 4));
    assertFalse(rect(6, 20, 6, 5, 5).suppliedBy(pole, PoleSpec.SMALL, 4));
  }

  @Test
  void verticalBaseReachDoesNotShrinkTheSquare() {
    var p = rect(0, 20, 0, 2, 2);
    assertTrue(rect(9, 24, 9, 1, 1).suppliedBy(p, PoleSpec.SUBSTATION, 4));
    assertFalse(rect(9, 25, 9, 1, 1).suppliedBy(p, PoleSpec.SUBSTATION, 4));
    assertFalse(rect(10, 20, 9, 1, 1).suppliedBy(p, PoleSpec.SUBSTATION, 4));
  }

  @Test
  void mixedTypesMustSatisfyBothWireDistances() {
    var g = new PowerTopology<Object>(4);
    var b = pole(g, 0, 0, 0, PoleSpec.BIG);
    var near = pole(g, 8, 0, 0, PoleSpec.MEDIUM);
    var far = pole(g, 10, 0, 0, PoleSpec.MEDIUM);
    assertTrue(g.link(b, near));
    assertFalse(g.link(b, far));
  }

  @Test
  void proximityAndOverlappingSupplyNeverCreateEdges() {
    var g = new PowerTopology<Object>(4);
    pole(g, 0, 0);
    pole(g, 4, 0);
    var a = source(g, -1, 1, 0);
    var b = consumer(g, 5, 1, 0, new Store(100));
    finish(g);
    assertFalse(g.connected(a, b));
    assertFalse(g.prepareDemand(a, 32));
    assertEquals(2, g.networks());
  }

  @Test
  void explicitEdgesMergeAndRemovalSplitsImmediately() {
    var g = new PowerTopology<Object>(4);
    var p = pole(g, 0, 0);
    var q = pole(g, 6, 0);
    var a = source(g, 0, 1, 0);
    var b = consumer(g, 6, 1, 0, new Store(100));
    finish(g);
    assertFalse(g.connected(a, b));
    assertTrue(g.link(p, q));
    finish(g);
    assertTrue(g.connected(a, b));
    assertTrue(g.unlink(p, q));
    assertFalse(g.connected(a, b));
    finish(g);
    assertFalse(g.connected(a, b));
  }

  @Test
  void repeatedRebuildsDoNotRestoreManuallyRemovedConnections() {
    var g = new PowerTopology<Object>(4);
    var p = pole(g, 0, 0);
    var q = pole(g, 6, 0);
    g.link(p, q);
    finish(g);
    g.unlink(p, q);
    for (int i = 0; i < 20; i++) {
      pole(g, i * 10, 100);
      finish(g);
      assertFalse(g.connected(p, q));
    }
  }

  @Test
  void cutQuarantinesOnlyItsOldComponent() {
    var g = new PowerTopology<Object>(4);
    var p = pole(g, 0, 0);
    var q = pole(g, 6, 0);
    g.link(p, q);
    var a = source(g, 0, 1, 0);
    var b = consumer(g, 6, 1, 0, new Store(100));
    pole(g, 100, 0);
    var c = source(g, 100, 1, 0);
    var d = consumer(g, 100, 2, 0, new Store(100));
    finish(g);
    g.unlink(p, q);
    assertFalse(g.connected(a, b));
    assertTrue(g.connected(c, d));
    g.advance(1);
    assertTrue(g.connected(c, d));
    finish(g);
  }

  @Test
  void aRemovedRecipientCannotReceivePreparedEnergy() {
    var g = new PowerTopology<Object>(4);
    pole(g, 0, 0);
    var a = source(g, 0, 1, 0);
    var s = new Store(100);
    var b = consumer(g, 0, 2, 0, s);
    finish(g);
    assertTrue(g.prepareDemand(a, 32));
    g.remove(b.value());
    assertEquals(0, g.distributePrepared(a, 80));
    assertEquals(0, s.energy);
  }

  @Test
  void aWireCutInvalidatesPreparedTransfers() {
    var g = new PowerTopology<Object>(4);
    var p = pole(g, 0, 0);
    var q = pole(g, 6, 0);
    g.link(p, q);
    var a = source(g, 0, 1, 0);
    var s = new Store(100);
    consumer(g, 6, 1, 0, s);
    finish(g);
    assertTrue(g.prepareDemand(a, 32));
    g.unlink(p, q);
    assertEquals(0, g.distributePrepared(a, 80));
    assertEquals(0, s.energy);
  }

  @Test
  void sharedConsumerIsNeverCreditedTwice() {
    var g = new PowerTopology<Object>(4);
    var p = pole(g, -2, 0);
    var q = pole(g, 2, 0);
    var a = source(g, 0, 1, 0);
    var s = new Store(100);
    consumer(g, 0, 2, 0, s);
    finish(g);
    assertFalse(g.connected(p, q));
    assertTrue(g.prepareDemand(a, 32));
    assertEquals(80, g.distributePrepared(a, 80));
    assertEquals(80, s.energy);
  }

  @Test
  void generationIsConservedAndFullBuffersStopDemand() {
    var g = new PowerTopology<Object>(4);
    pole(g, 0, 0);
    var a = source(g, 0, 1, 0);
    var x = new Store(31);
    var y = new Store(41);
    consumer(g, 0, 2, 0, x);
    consumer(g, 0, 3, 0, y);
    finish(g);
    int n = 0;
    for (int i = 0; i < 5; i++) if (g.prepareDemand(a, 32)) n += g.distributePrepared(a, 80);
    assertEquals(72, n);
    assertEquals(n, x.energy + y.energy);
    assertFalse(g.prepareDemand(a, 32));
  }

  @Test
  void cutsInEveryBuilderPhaseCannotPublishAStaleConnection() {
    for (int cut = 0; cut < 160; cut++) {
      var g = new PowerTopology<Object>(4);
      var p = pole(g, 0, 0);
      var q = pole(g, 6, 0);
      g.link(p, q);
      var a = source(g, 0, 1, 0);
      var b = consumer(g, 6, 1, 0, new Store(100));
      g.advance(cut);
      g.unlink(p, q);
      while (g.pending()) {
        g.advance(3);
        assertFalse(g.connected(a, b));
      }
    }
  }

  @Test
  void additionsDoNotSuspendIndependentLiveNetworks() {
    var g = new PowerTopology<Object>(4);
    pole(g, 0, 0);
    var a = source(g, 0, 1, 0);
    var b = consumer(g, 0, 2, 0, new Store(100));
    finish(g);
    pole(g, 100, 0);
    g.advance(1);
    assertTrue(g.connected(a, b));
    finish(g);
    assertTrue(g.connected(a, b));
  }

  @Test
  void tenThousandExplicitPolesRemainBudgetedAndSleepWhenUnchanged() {
    var g = new PowerTopology<Object>(4);
    PowerTopology.Node<Object> previous = null;
    for (int i = 0; i < 10000; i++) {
      var p = pole(g, i * 6, 0);
      if (previous != null) assertTrue(g.link(previous, p));
      previous = p;
    }
    var a = source(g, 0, 1, 0);
    var b = consumer(g, 59994, 1, 0, new Store(100));
    int ticks = 0;
    while (g.pending()) {
      assertTrue(g.advance(4096) <= 4096);
      assertTrue(++ticks < 1000);
    }
    assertTrue(g.connected(a, b));
    assertTrue(g.candidateChecks() < 100000);
    long checks = g.candidateChecks();
    for (int i = 0; i < 100; i++) assertEquals(0, g.advance(4096));
    assertEquals(checks, g.candidateChecks());
  }

  @Test
  void verticalConfigurationChangesInvalidateOldMembershipsAtomically() {
    var g = new PowerTopology<Object>(4);
    pole(g, 0, 0);
    var a = source(g, 0, 1, 0);
    var b = consumer(g, 0, 4, 0, new Store(100));
    finish(g);
    assertTrue(g.connected(a, b));
    g.setVerticalReach(2);
    assertFalse(g.connected(a, b));
    finish(g);
    assertFalse(g.connected(a, b));
  }

  @Test
  void alternateWirePathsSurviveACutAfterRebuild() {
    var g = new PowerTopology<Object>(4);
    var p = pole(g, 0, 0);
    var q = pole(g, 6, 0);
    var r = pole(g, 3, 5);
    g.link(p, q);
    g.link(q, r);
    g.link(r, p);
    finish(g);
    g.unlink(p, q);
    finish(g);
    assertTrue(g.connected(p, q));
  }

  @Test
  void networkInfoReportsRealRegisteredDeviceCounts() {
    var g = new PowerTopology<Object>(4);
    var p = pole(g, 0, 0);
    source(g, 0, 1, 0);
    consumer(g, 0, 2, 0, new Store(100));
    finish(g);
    assertEquals(new PowerTopology.NetworkInfo(true, 1, 1, 1), g.info(p));
  }

  @Test
  void moreThanFiveManualConnectionsAreAllowed() {
    var g = new PowerTopology<Object>(4);
    var p = pole(g, 0, 0, 0, PoleSpec.BIG);
    for (int i = 0; i < 16; i++)
      assertTrue(g.link(p, pole(g, 4 + (i % 4) * 4, 0, (i / 4) * 4, PoleSpec.BIG)));
    finish(g);
    assertEquals(17, g.info(p).poles());
  }
}
