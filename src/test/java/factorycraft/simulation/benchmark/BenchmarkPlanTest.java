package factorycraft.simulation.benchmark;

import static factorycraft.simulation.benchmark.BenchmarkPlan.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class BenchmarkPlanTest {
  private record Point(int x, int y, int z) {}

  @Test
  void fullFootprintHasExactCountsAndNoOverlaps() {
    var plan = new BenchmarkPlan(Profile.FULL);
    var points = new HashSet<Point>();
    var counts = new EnumMap<Role, Integer>(Role.class);
    for (var c : plan.blocks()) {
      assertTrue(points.add(new Point(c.x(), c.y(), c.z())), "Overlapping fixture cells: " + c);
      counts.merge(c.role(), 1, Integer::sum);
      assertTrue(
          c.x() >= 0 && c.x() < 402 && c.z() >= 0 && c.z() < 122 && c.y() >= 0 && c.y() < 50);
    }
    assertEquals(10000, counts.get(Role.BELT));
    assertEquals(500, counts.get(Role.INSERTER));
    assertEquals(32500, counts.get(Role.CASING));
    assertEquals(
        1000,
        counts.get(Role.FURNACE)
            + counts.get(Role.ASSEMBLER)
            + counts.get(Role.INDUSTRIAL_FURNACE));
    assertEquals(250, counts.get(Role.GENERATOR));
    assertEquals(749, counts.get(Role.POLE));
    assertEquals(45249, points.size());
  }

  @Test
  void everyBeltHasAnAdjacentOutputAndEachRingClosesAfterOneHundredCells() {
    var plan = new BenchmarkPlan(Profile.FULL);
    var belts = new HashMap<Point, Cell>();
    for (var c : plan.blocks())
      if (c.role() == Role.BELT) belts.put(new Point(c.x(), c.y(), c.z()), c);
    for (var start : belts.values()) {
      var cell = start;
      var visited = new HashSet<Point>();
      for (int step = 0; step < 100; step++) {
        assertTrue(visited.add(new Point(cell.x(), cell.y(), cell.z())));
        cell =
            belts.get(
                new Point(cell.x() + cell.heading().x, cell.y(), cell.z() + cell.heading().z));
        assertNotNull(cell);
      }
      assertEquals(start, cell);
    }
  }

  @Test
  void insertersConnectRealInventoriesAndEachControllerHasTwentySixParts() {
    var map = new HashMap<Point, Cell>();
    for (var c : new BenchmarkPlan(Profile.FULL).blocks())
      map.put(new Point(c.x(), c.y(), c.z()), c);
    for (var c : map.values()) {
      if (c.role() == Role.INSERTER) {
        var source = map.get(new Point(c.x() - c.heading().x, c.y(), c.z() - c.heading().z));
        var target = map.get(new Point(c.x() + c.heading().x, c.y(), c.z() + c.heading().z));
        assertNotNull(source);
        assertNotNull(target);
        assertTrue(
            source.role() == Role.SOURCE
                || source.role() == Role.FURNACE
                || source.role() == Role.CASING);
        assertTrue(
            target.role() == Role.FURNACE
                || target.role() == Role.ASSEMBLER
                || target.role() == Role.CASING);
      }
      if (c.role() == Role.INDUSTRIAL_FURNACE) {
        for (var p : new factorycraft.simulation.multiblock.CuboidLayout(3, 3, 3).parts(0, -1))
          assertEquals(
              Role.CASING, map.get(new Point(c.x() + p.x(), c.y() + p.y(), c.z() + p.z())).role());
      }
    }
  }

  @Test
  void smokeIsSmallButIncludesEveryProductionAndTransportRole() {
    var blocks = new BenchmarkPlan(Profile.SMOKE).blocks();
    var roles = EnumSet.noneOf(Role.class);
    blocks.forEach(c -> roles.add(c.role()));
    assertEquals(
        EnumSet.of(
            Role.BELT,
            Role.FURNACE,
            Role.ASSEMBLER,
            Role.INDUSTRIAL_FURNACE,
            Role.CASING,
            Role.GENERATOR,
            Role.POLE,
            Role.INSERTER,
            Role.SOURCE),
        roles);
    assertEquals(804, blocks.size());
    assertEquals(Profile.FULL, Profile.parse("full"));
    assertThrows(IllegalArgumentException.class, () -> Profile.parse("arbitrary"));
  }

  @Test
  void fullFactoryUsesOneSharedPowerNetworkRatherThanCheapIsolatedCells() {
    var graph = new factorycraft.simulation.power.PowerTopology<Cell>(6);
    for (var c : new BenchmarkPlan(Profile.FULL).blocks()) {
      var kind =
          switch (c.role()) {
            case POLE -> factorycraft.simulation.power.PowerTopology.Kind.POLE;
            case GENERATOR -> factorycraft.simulation.power.PowerTopology.Kind.SOURCE;
            case FURNACE, ASSEMBLER, INDUSTRIAL_FURNACE ->
                factorycraft.simulation.power.PowerTopology.Kind.CONSUMER;
            default -> null;
          };
      if (kind != null)
        graph.add(
            c,
            new factorycraft.simulation.power.PowerPoint(
                c.x() + (c.role() == Role.POLE ? 0 : 1),
                c.y(),
                c.z() + (c.role() == Role.POLE ? 0 : 1)),
            kind,
            (n, simulate) -> n);
    }
    int rounds = 0;
    while (graph.pending() && rounds++ < 1000) graph.advance(4096);
    assertFalse(graph.pending());
    assertEquals(1999, graph.size());
    assertEquals(1, graph.networks());
  }
}
