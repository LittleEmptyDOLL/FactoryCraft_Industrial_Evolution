package factorycraft.simulation.multiblock;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlacementAimTest {
  @Test
  void missAirUnloadedAndOutOfReachNeverProduceAPreview() {
    assertFalse(PlacementAim.valid(false, false, true, 1, 5));
    assertFalse(PlacementAim.valid(true, true, true, 1, 5));
    assertFalse(PlacementAim.valid(true, false, false, 1, 5));
    assertFalse(PlacementAim.valid(true, false, true, 25.01, 5));
    assertFalse(PlacementAim.valid(true, false, true, Double.NaN, 5));
  }

  @Test
  void targetAtReachIsAllowedRegardlessOfStructureSize() {
    assertTrue(PlacementAim.valid(true, false, true, 25, 5));
    assertTrue(PlacementAim.valid(true, false, true, 0, 5));
  }
}
