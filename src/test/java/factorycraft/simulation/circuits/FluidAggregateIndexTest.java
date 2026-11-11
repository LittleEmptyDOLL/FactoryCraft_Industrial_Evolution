package factorycraft.simulation.circuits;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.fluids.FluidAggregateIndex;
import java.util.*;
import org.junit.jupiter.api.Test;

class FluidAggregateIndexTest {
  @Test
  void totalsUpdateWithoutTraversingAndCutsQuarantineOnlyAffectedPipelines() {
    var g = new FluidAggregateIndex<Integer, String>();
    for (int i = 0; i < 7; i++) {
      g.add(i);
      g.update(i, Map.of(i % 2 == 0 ? "water" : "oil", 10));
    }
    for (int i = 1; i < 5; i++) g.connect(i - 1, i);
    g.connect(5, 6);
    while (g.pending()) assertTrue(g.advance(2) <= 2);
    assertEquals(5, g.snapshot(0).cells());
    assertEquals(Map.of("water", 30, "oil", 20), g.snapshot(0).amounts());
    long visits = g.visits();
    g.update(2, Map.of("water", 7));
    assertEquals(27, g.snapshot(0).amounts().get("water"));
    assertEquals(visits, g.visits());
    g.disconnect(2, 3);
    assertFalse(g.snapshot(0).ready());
    assertTrue(g.snapshot(5).ready());
    while (g.pending()) g.advance(1);
    assertEquals(Map.of("water", 17, "oil", 10), g.snapshot(0).amounts());
    assertEquals(Map.of("water", 10, "oil", 10), g.snapshot(4).amounts());
  }

  @Test
  void changesDuringBudgetedCaptureRemovalAndReloadKeepExactFiniteTotals() {
    var g = new FluidAggregateIndex<Integer, String>();
    for (int i = 0; i < 1000; i++) {
      g.add(i);
      g.update(i, Map.of("water", i));
      if (i > 0) g.connect(i - 1, i);
    }
    g.advance(20);
    g.update(3, Map.of("water", 103));
    g.update(900, Map.of("water", 1000));
    while (g.pending()) g.advance(13);
    assertEquals(499700, g.snapshot(0).amounts().get("water"));
    g.active(500, false);
    while (g.pending()) g.advance(17);
    assertEquals(500, g.snapshot(0).cells());
    assertEquals(499, g.snapshot(999).cells());
    g.active(500, true);
    while (g.pending()) g.advance(19);
    assertEquals(499700, g.snapshot(500).amounts().get("water"));
    g.remove(500);
    while (g.pending()) g.advance(29);
    assertEquals(
        499200, g.snapshot(0).amounts().get("water") + g.snapshot(999).amounts().get("water"));
  }
}
