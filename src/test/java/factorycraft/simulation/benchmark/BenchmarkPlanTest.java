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
    assertEquals(30248, counts.get(Role.CASING));
    assertEquals(
        1000,
        counts.get(Role.FURNACE) + counts.get(Role.ASSEMBLER) + counts.get(Role.STEEL_FURNACE));
    assertEquals(250, counts.get(Role.GENERATOR));
    assertEquals(749, counts.get(Role.POLE));
    assertEquals(42997, points.size());
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
  void insertersConnectRealInventoriesAndSteelUsesTwoByTwoFootprint() {
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
      if (c.role() == Role.STEEL_FURNACE) {
        for (int y = 0; y < 3; y++)
          for (int z = 0; z < 2; z++)
            for (int x = 0; x < 2; x++) {
              if (x == 1 && y == 0 && z == 0) continue;
              var part = map.get(new Point(c.x() + x - 1, c.y() + y, c.z() + z));
              assertNotNull(part);
              assertEquals(Role.CASING, part.role());
            }
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
            Role.STEEL_FURNACE,
            Role.CASING,
            Role.GENERATOR,
            Role.POLE,
            Role.INSERTER,
            Role.SOURCE),
        roles);
    assertEquals(757, blocks.size());
    assertEquals(Profile.FULL, Profile.parse("full"));
    assertThrows(IllegalArgumentException.class, () -> Profile.parse("arbitrary"));
  }

  @Test
  void fullFactoryUsesOneSharedPowerNetworkRatherThanCheapIsolatedCells() {
    var graph = new factorycraft.simulation.power.PowerTopology<Cell>(6);
    var plan = new BenchmarkPlan(Profile.FULL);
    for (var c : plan.blocks()) {
      var kind =
          switch (c.role()) {
            case POLE -> factorycraft.simulation.power.PowerTopology.Kind.POLE;
            case GENERATOR -> factorycraft.simulation.power.PowerTopology.Kind.SOURCE;
            case FURNACE, ASSEMBLER, INSERTER ->
                factorycraft.simulation.power.PowerTopology.Kind.CONSUMER;
            default -> null;
          };
      if (kind != null) {
        var point = new factorycraft.simulation.power.PowerPoint(c.x(), c.y(), c.z());
        var bounds =
            c.role() == Role.POLE || c.role() == Role.INSERTER
                ? factorycraft.simulation.power.PowerFootprint.cell(point)
                : new factorycraft.simulation.power.PowerFootprint(
                    c.x() - 1, c.x() + 1, c.y(), c.z(), c.z() + 2);
        graph.add(
            c,
            bounds,
            c.role() == Role.POLE ? factorycraft.simulation.power.PoleSpec.MEDIUM : null,
            kind,
            (n, simulate) -> n);
      }
    }
    var poles = plan.blocks().stream().filter(c -> c.role() == Role.POLE).toList();
    for (int i = 0; i < poles.size(); i++)
      for (int k = 0; k < i; k++)
        if (new factorycraft.simulation.power.PowerPoint(poles.get(i).x(), 0, poles.get(i).z())
            .within(
                new factorycraft.simulation.power.PowerPoint(poles.get(k).x(), 0, poles.get(k).z()),
                9)) graph.link(graph.find(poles.get(i)), graph.find(poles.get(k)));
    int rounds = 0;
    while (graph.pending() && rounds++ < 1000) graph.advance(4096);
    assertFalse(graph.pending());
    assertEquals(2249, graph.size());
    assertEquals(1, graph.networks());
    assertEquals(1250, graph.info(graph.find(poles.getFirst())).consumers());
    assertEquals(250, graph.info(graph.find(poles.getFirst())).sources());
  }
}
