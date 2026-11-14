package factorycraft.simulation;

import static factorycraft.simulation.benchmark.BenchmarkPlan.*;
import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.benchmark.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MixedPlanTest {
  @Test
  void fullMixedPlanHasUniquePhysicalCellsAndExactWorkload() {
    var p = new BenchmarkPlan(Profile.MIXED);
    var used = new HashSet<String>();
    for (var c : p.blocks())
      assertTrue(used.add(c.x() + "," + c.y() + "," + c.z()), "overlapping " + c);
    assertEquals(10000, p.blocks().stream().filter(c -> c.role().belt()).count());
    assertEquals(9800, p.movingBelts());
    assertEquals(800, p.blocks().stream().filter(c -> c.role() == Role.TRANSPORT_PART).count());
    assertEquals(400, p.blocks().stream().filter(c -> c.role() == Role.OBSTACLE).count());
    assertEquals(200, p.blocks().stream().filter(p::extraSeed).count());
    assertEquals(
        1000,
        p.blocks().stream()
            .filter(
                c -> Set.of(Role.FURNACE, Role.ASSEMBLER, Role.STEEL_FURNACE).contains(c.role()))
            .count());
    assertEquals(500, p.blocks().stream().filter(c -> c.role() == Role.INSERTER).count());
  }

  @Test
  void everyMixedOutputHasOnePhysicalReceiverAtTheCorrectHeight() {
    var p = new BenchmarkPlan(Profile.MIXED);
    var cells = new HashMap<String, Cell>();
    for (var c : p.blocks()) if (c.role().belt()) cells.put(c.x() + "," + c.y() + "," + c.z(), c);
    for (var c : cells.values()) {
      if (c.role() == Role.UNDERGROUND_INPUT) continue;
      int h =
          switch (c.role()) {
            case INCLINE_UP -> 1;
            case INCLINE_DOWN -> -1;
            case LIFT_UP -> 3;
            case LIFT_DOWN -> -3;
            default -> 0;
          };
      var target =
          cells.get((c.x() + c.heading().x) + "," + (c.y() + h) + "," + (c.z() + c.heading().z));
      assertNotNull(target, "disconnected " + c);
      assertNotEquals(c.heading(), opposite(target.heading()), "head-on " + c);
    }
  }

  private static Heading opposite(Heading d) {
    return Heading.values()[(d.ordinal() + 2) % 4];
  }

  @Test
  void legacyFullAndSmokeRemainUnchanged() {
    for (var profile : List.of(Profile.FULL, Profile.SMOKE)) {
      var p = new BenchmarkPlan(profile);
      assertEquals(profile.loops * 100, p.movingBelts());
      assertTrue(p.blocks().stream().allMatch(c -> c.tier() == 1));
      assertFalse(p.blocks().stream().anyMatch(p::extraSeed));
      assertFalse(p.blocks().stream().anyMatch(c -> c.role() == Role.TRANSPORT_PART));
    }
  }
}
