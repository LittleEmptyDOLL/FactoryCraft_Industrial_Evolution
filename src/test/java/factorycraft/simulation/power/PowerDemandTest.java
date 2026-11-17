package factorycraft.simulation.power;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PowerDemandTest {
  private static void finish(PowerTopology<?> g) {
    for (int i = 0; g.pending(); i++) {
      assertTrue(i < 100000);
      assertTrue(g.advance(11) <= 11);
    }
  }

  private static final class Load implements PowerTopology.Receiver {
    int held, calls;

    public int receive(int n, boolean sim) {
      calls++;
      int accepted = Math.min(n, 10 - held);
      if (!sim) held += accepted;
      return accepted;
    }
  }

  @Test
  void tenThousandBufferEventsCoalesceWithoutTopologyRebuild() {
    var g = new PowerTopology<Object>(8);
    Object pole = new Object(), source = new Object(), sink = new Object();
    var load = new Load();
    g.add(
        pole,
        PowerFootprint.cell(new PowerPoint(0, 0, 0)),
        PoleSpec.SMALL,
        PowerTopology.Kind.POLE,
        null);
    g.add(source, new PowerPoint(1, 0, 0), PowerTopology.Kind.SOURCE, null);
    g.add(sink, new PowerPoint(2, 0, 0), PowerTopology.Kind.CONSUMER, load);
    finish(g);
    assertSame(sink, g.nextHungry(source));
    long builds = g.rebuilds();
    int calls = load.calls;
    load.held = 10;
    for (int i = 0; i < 10000; i++) g.changed(sink);
    assertEquals(1, g.refreshDemand(100));
    assertEquals(calls + 1, load.calls);
    assertNull(g.nextHungry(source));
    assertEquals(builds, g.rebuilds());
    assertFalse(g.pending());
    load.held = 9;
    g.changed(sink);
    assertEquals(1, g.refreshDemand(1));
    assertSame(sink, g.nextHungry(source));
  }

  @Test
  void overlappingBatteryIsAssignedToExactlyOneExplicitGrid() {
    var g = new PowerTopology<Object>(8);
    Object a = new Object(),
        b = new Object(),
        sa = new Object(),
        sb = new Object(),
        battery = new Object();
    var pa =
        g.add(
            a,
            PowerFootprint.cell(new PowerPoint(0, 0, 0)),
            PoleSpec.SMALL,
            PowerTopology.Kind.POLE,
            null);
    var pb =
        g.add(
            b,
            PowerFootprint.cell(new PowerPoint(4, 0, 0)),
            PoleSpec.SMALL,
            PowerTopology.Kind.POLE,
            null);
    var na = g.add(sa, new PowerPoint(-1, 0, 0), PowerTopology.Kind.SOURCE, null);
    var nb = g.add(sb, new PowerPoint(5, 0, 0), PowerTopology.Kind.SOURCE, null);
    var store = g.add(battery, new PowerPoint(2, 0, 0), PowerTopology.Kind.STORAGE, null);
    finish(g);
    assertEquals(2, g.networks());
    assertFalse(g.hasEdge(pa, pb));
    assertTrue(g.connected(na, store) ^ g.connected(nb, store));
    assertEquals(1, g.storageCount(sa) + g.storageCount(sb));
    assertTrue(g.link(pa, pb));
    finish(g);
    assertEquals(1, g.networks());
    assertEquals(1, g.storageCount(sa));
    assertEquals(1, g.storageCount(sb));
    assertSame(battery, g.nextStorage(sa));
  }

  @Test
  void dirtyDemandSurvivesBudgetedSnapshotPublication() {
    var g = new PowerTopology<Object>(8);
    Object p = new Object(), src = new Object(), dst = new Object();
    var load = new Load();
    g.add(
        p,
        PowerFootprint.cell(new PowerPoint(0, 0, 0)),
        PoleSpec.SMALL,
        PowerTopology.Kind.POLE,
        null);
    g.add(src, new PowerPoint(1, 0, 0), PowerTopology.Kind.SOURCE, null);
    g.add(dst, new PowerPoint(2, 0, 0), PowerTopology.Kind.CONSUMER, load);
    finish(g);
    g.add(new Object(), new PowerPoint(2, 0, 1), PowerTopology.Kind.CONSUMER, (n, sim) -> 0);
    g.advance(1);
    load.held = 10;
    g.changed(dst);
    g.refreshDemand(10);
    finish(g);
    assertNull(g.nextHungry(src));
    g.remove(dst);
    assertNull(g.nextHungry(src));
  }
}
