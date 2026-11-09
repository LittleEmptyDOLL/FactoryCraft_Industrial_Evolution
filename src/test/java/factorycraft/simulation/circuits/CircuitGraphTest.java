package factorycraft.simulation.circuits;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CircuitGraphTest {
  private CircuitGraph<Integer, String> graph(int nodes, int degree) {
    var g = new CircuitGraph<Integer, String>(nodes, degree, k -> {});
    for (int i = 0; i < 12; i++) g.add(i);
    return g;
  }

  @Test
  void mergingAndSplittingRebuildOnlyAffectedBuses() {
    var g = graph(8, 8);
    g.output(0, "iron", 3);
    g.output(1, "iron", 7);
    g.output(9, "iron", 99);
    assertTrue(g.connect(0, 1));
    assertFalse(g.valid(0));
    assertEquals(99, g.value(9, "iron"));
    g.rebuild(1);
    assertEquals(10, g.value(1, "iron"));
    long visits = g.visits();
    for (int i = 0; i < 10000; i++) {
      g.output(0, "iron", i);
      assertEquals(i + 7, g.value(1, "iron"));
    }
    assertEquals(visits, g.visits());
    g.disconnect(0, 1);
    assertFalse(g.valid(0));
    g.rebuild(1);
    assertTrue(g.pending());
    g.rebuild(1);
    assertEquals(9999, g.value(0, "iron"));
    assertEquals(7, g.value(1, "iron"));
    assertEquals(99, g.value(9, "iron"));
  }

  @Test
  void cyclesAndRepeatedEdgesNeverDoubleSignals() {
    var g = graph(8, 8);
    for (int i = 0; i < 4; i++) {
      g.output(i, "a", i + 1);
      assertTrue(g.connect(i, (i + 1) % 4));
    }
    assertTrue(g.connect(0, 1));
    g.rebuild(8);
    assertEquals(10, g.value(2, "a"));
    g.remove(1);
    g.rebuild(8);
    assertEquals(8, g.value(0, "a"));
    g.add(1);
    g.connect(0, 1);
    g.rebuild(8);
    assertEquals(8, g.value(1, "a"));
  }

  @Test
  void limitsRejectBeforeMutationAndColorComponentsStayIndependent() {
    var g = graph(3, 2);
    assertTrue(g.connect(0, 1));
    assertTrue(g.connect(1, 2));
    assertFalse(g.connect(2, 3));
    assertFalse(g.linked(2, 3));
    assertFalse(g.connect(1, 3));
    g.output(0, "a", 4);
    g.output(3, "a", 9);
    g.rebuild(8);
    assertEquals(4, g.value(2, "a"));
    assertEquals(9, g.value(3, "a"));
  }

  @Test
  void missingEndpointQuarantinesWholeComponentUntilRestored() {
    var g = graph(8, 8);
    g.connect(0, 1);
    g.connect(1, 2);
    g.output(0, "a", 12);
    g.rebuild(8);
    g.complete(2, false);
    assertFalse(g.valid(0));
    g.rebuild(8);
    assertFalse(g.valid(1));
    assertEquals(0, g.value(0, "a"));
    g.complete(2, true);
    g.rebuild(8);
    assertEquals(12, g.value(1, "a"));
  }

  @Test
  void totalsWrapAndChangingSignalRemovesOldContribution() {
    var g = graph(8, 8);
    g.connect(0, 1);
    g.output(0, "a", Integer.MAX_VALUE);
    g.output(1, "a", 1);
    g.rebuild(8);
    assertEquals(Integer.MIN_VALUE, g.value(0, "a"));
    g.output(0, "b", 5);
    assertEquals(1, g.value(1, "a"));
    assertEquals(5, g.value(1, "b"));
    g.output(0, null, 0);
    assertEquals(0, g.value(1, "b"));
  }

  @Test
  void randomizedTopologyMatchesIndependentComponentOracle() {
    var g = graph(12, 12);
    var edges = new boolean[12][12];
    var values = new int[12];
    var random = new Random(78943);
    for (int step = 0; step < 3000; step++) {
      int a = random.nextInt(12), b = random.nextInt(12);
      if (a != b) {
        if (edges[a][b]) g.disconnect(a, b);
        else assertTrue(g.connect(a, b));
        edges[a][b] = edges[b][a] = !edges[a][b];
      }
      values[a] = random.nextInt();
      g.output(a, "a", values[a]);
      g.rebuild(12);
      for (int start = 0; start < 12; start++) {
        var seen = new HashSet<Integer>();
        var q = new ArrayDeque<Integer>();
        q.add(start);
        int sum = 0;
        while (!q.isEmpty()) {
          int x = q.remove();
          if (!seen.add(x)) continue;
          sum += values[x];
          for (int i = 0; i < 12; i++) if (edges[x][i]) q.add(i);
        }
        assertEquals(sum, g.value(start, "a"));
      }
    }
  }
}
