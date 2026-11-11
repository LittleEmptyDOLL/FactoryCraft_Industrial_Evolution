package factorycraft.simulation.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class SurfaceWindowTest {
  @Test
  void exactAveragePreservesFlatAndSlopedTerrainIncludingNegativeHeights() {
    IntBinaryOperator terrain = (x, z) -> -20 + Math.floorDiv(x, 4) + Math.floorDiv(z, 3);
    var w = new SurfaceWindow(-16, -16, 32, 32, 8, terrain);
    for (int x = -16; x < 16; x++)
      for (int z = -16; z < 16; z++) {
        long sum = 0;
        for (int dx = -8; dx <= 8; dx++)
          for (int dz = -8; dz <= 8; dz++) sum += terrain.applyAsInt(x + dx, z + dz);
        assertEquals(Math.floorDiv(sum, 289), w.height(x, z));
      }
  }

  @Test
  void cliffDoesNotCreateStridedVerticalCurtains() {
    var w = new SurfaceWindow(-16, -16, 32, 32, 8, (x, z) -> x <= 0 ? 70 : 60);
    for (int x = -15; x < 16; x++) assertTrue(Math.abs(w.height(x, 0) - w.height(x - 1, 0)) <= 1);
  }

  @Test
  void independentlyBuiltChunkWindowsUseIdenticalGlobalSamples() {
    IntBinaryOperator terrain = (x, z) -> 70 + Math.floorDiv(x, 3) - (z > 10 ? 7 : 0);
    var whole = new SurfaceWindow(0, 0, 32, 16, 8, terrain);
    var right = new SurfaceWindow(16, 0, 16, 16, 8, terrain);
    for (int x = 16; x < 32; x++)
      for (int z = 0; z < 16; z++) assertEquals(whole.height(x, z), right.height(x, z));
    assertThrows(IllegalArgumentException.class, () -> right.height(15, 0));
  }
}
