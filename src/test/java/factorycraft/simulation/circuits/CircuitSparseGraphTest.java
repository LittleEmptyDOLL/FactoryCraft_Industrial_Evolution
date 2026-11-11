package factorycraft.simulation.circuits;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CircuitSparseGraphTest {
  @Test
  void sparseMapsSumOnceAcrossCyclesAndUnavailableSignalCapacityNeverTruncates() {
    var graph = new CircuitGraph<Integer, SignalKey>(8, 8, k -> {});
    for (int i = 0; i < 4; i++) {
      graph.add(i);
    }
    for (int i = 0; i < 4; i++) assertTrue(graph.connect(i, (i + 1) % 4));
    var unknown = SignalKey.virtual("unknown");
    graph.output(0, Map.of(SignalKey.A, Integer.MAX_VALUE, unknown, -19));
    graph.output(
        1,
        Map.of(
            SignalKey.A, 1, SignalKey.item("missing:same"), 7, SignalKey.fluid("missing:same"), 3));
    graph.rebuild(8);
    assertEquals(Integer.MIN_VALUE, graph.signals(2).get(SignalKey.A));
    assertEquals(4, graph.signals(2).size());
    var map = new HashMap<SignalKey, Integer>();
    for (int i = 0; i < 4096; i++) map.put(SignalKey.virtual("key_" + i), 1);
    graph.output(0, map);
    assertFalse(graph.valid(2));
    assertTrue(graph.signals(2).isEmpty());
    graph.output(1, Map.of());
    assertTrue(graph.valid(2));
    assertEquals(4096, graph.signals(2).size());
    assertEquals(4096, map.size());
  }

  @Test
  void bounded1024Port128SignalBenchChangesValuesWithoutTopologyRebuild() {
    var graph = new CircuitGraph<Integer, SignalKey>(32, 4, k -> {});
    var base = new HashMap<SignalKey, Integer>();
    for (int s = 0; s < 128; s++) base.put(SignalKey.virtual("bench_" + s), s + 1);
    for (int i = 0; i < 1024; i++) graph.add(i);
    for (int c = 0; c < 64; c++) {
      int start = c * 16;
      for (int i = 0; i < 16; i++) assertTrue(graph.connect(start + i, start + (i + 1) % 16));
      graph.output(start, base);
    }
    while (graph.pending()) graph.rebuild(8);
    long visits = graph.visits(), rebuilds = graph.rebuilds();
    long start = System.nanoTime();
    for (int tick = 0; tick < 200; tick++)
      for (int c = 0; c < 64; c++) {
        int root = c * 16;
        graph.output(root + 1, Map.of(SignalKey.A, tick + 1));
        assertEquals(tick + 1, graph.signals(root + 15).get(SignalKey.A));
        assertEquals(129, graph.signals(root + 15).size());
      }
    long elapsed = System.nanoTime() - start;
    assertEquals(visits, graph.visits());
    assertEquals(rebuilds, graph.rebuilds());
    graph.disconnect(0, 1);
    graph.disconnect(0, 15);
    graph.rebuild(2);
    assertEquals(200, graph.signals(1).get(SignalKey.A));
    assertEquals(128, graph.signals(0).size());
    System.out.printf(
        java.util.Locale.ROOT,
        "FACTORYCRAFT_CIRCUIT_BENCH: 1024 ports / 64 rings / 128 static + 1 changed signal, 12800"
            + " updates in %.3f ms; zero topology visits during value updates%n",
        elapsed / 1e6);
  }
}
