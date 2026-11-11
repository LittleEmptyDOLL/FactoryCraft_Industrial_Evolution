package factorycraft.simulation.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class VolumetricFieldsTest {
  @Test
  void bodyHasDepthTaperAndRichnessGradient() {
    var s = new ResourceVeins.Settings(48, 48, 500, 5000, 16, 64, .12);
    var p = ResourceVeins.patch(71, 17, 2, 3, s);
    assertTrue(p.depthAt(p.x(), p.z()) > 50);
    assertTrue(p.depthAt(p.x() + 40, p.z()) < p.depthAt(p.x(), p.z()));
    assertTrue(p.stock(p.x(), p.z(), 0) > p.stock(p.x(), p.z(), 40));
    assertEquals(0, p.stock(p.x(), p.z(), 64));
    assertEquals(0, p.depthAt(p.x() + 49, p.z()));
  }

  @Test
  void chunkTraversalOrderCannotChangeTheBody() {
    var s = new ResourceVeins.Settings(48, 48, 500, 5000, 16, 48, .3);
    var p = ResourceVeins.patch(19, 24, 0, 0, s);
    var chunks = new ArrayList<int[]>();
    for (int x = (p.x() - 48) >> 4; x <= (p.x() + 48) >> 4; x++)
      for (int z = (p.z() - 48) >> 4; z <= (p.z() + 48) >> 4; z++) chunks.add(new int[] {x, z});
    var a = sample(chunks, s);
    Collections.reverse(chunks);
    assertEquals(a, sample(chunks, s));
    assertTrue(a.size() > 10000);
  }

  private Map<String, Integer> sample(List<int[]> chunks, ResourceVeins.Settings s) {
    var result = new HashMap<String, Integer>();
    for (var c : chunks)
      for (var p : ResourceVeins.inChunk(19, 24, c[0], c[1], s))
        for (int x = c[0] * 16; x < c[0] * 16 + 16; x++)
          for (int z = c[1] * 16; z < c[1] * 16 + 16; z++)
            for (int d = 0; d < p.depthAt(x, z); d++)
              result.merge(x + "," + z + "," + d, p.stock(x, z, d), Math::max);
    return result;
  }

  @Test
  void oilFieldsAreDeterministicUniqueAndYieldBounded() {
    var points = new HashSet<OilFields.Point>();
    for (int x = -20; x < 20; x++)
      for (int z = -20; z < 20; z++) {
        var a = OilFields.inChunk(15, x, z, 16, 24, 4, 12, 10000, 30000);
        assertEquals(a, OilFields.inChunk(15, x, z, 16, 24, 4, 12, 10000, 30000));
        for (var p : a) {
          assertEquals(x, p.x() >> 4);
          assertEquals(z, p.z() >> 4);
          assertTrue(p.yieldHundredths() >= 10000 && p.yieldHundredths() <= 30000);
          assertTrue(points.add(p));
        }
      }
    assertFalse(points.isEmpty());
  }
}
