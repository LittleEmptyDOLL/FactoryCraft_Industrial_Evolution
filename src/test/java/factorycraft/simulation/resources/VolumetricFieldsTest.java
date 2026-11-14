package factorycraft.simulation.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class VolumetricFieldsTest {
  @Test
  void completeBodyIncludesUpperAndLowerHalvesWithRadiusFiveToEight() {
    var s = new ResourceVeins.Settings(8, 20, 500, 5000, 16);
    var radii = new HashSet<Integer>();
    for (int n = 0; n < 64; n++) {
      var p = ResourceVeins.patch(71, 17, n, -3, s);
      var b = p.atHeight(70);
      radii.add(p.radiusY());
      assertTrue(p.radiusY() >= 5 && p.radiusY() <= 8);
      int buried = 0, upper = 0;
      for (int y = 50; y <= 90; y++)
        if (b.stock(p.x(), y, p.z()) > 0) {
          if (y <= 70) buried++;
          else upper++;
        }
      assertEquals(p.radiusY(), buried);
      assertEquals(p.radiusY() - 1, upper);
      assertEquals(0, b.stock(p.x(), 70 + p.radiusY(), p.z()));
      assertEquals(0, b.stock(p.x(), 70 - p.radiusY(), p.z()));
      assertTrue(b.stock(p.x(), 70, p.z()) > b.stock(p.x(), 70 - p.radiusY() + 1, p.z()));
    }
    assertEquals(Set.of(5, 6, 7, 8), radii);
  }

  @Test
  void localGroundCanClipButCannotMoveOrDrapeTheFixedBody() {
    var p = ResourceVeins.patch(9, 19, 0, 0, new ResourceVeins.Settings(20, 20, 100, 1000, 16));
    var body = p.atHeight(70);
    for (int dx = -20; dx <= 20; dx++)
      for (int z = p.z() - 20; z <= p.z() + 20; z++) {
        int x = p.x() + dx, ground = dx < 0 ? 88 : 51;
        for (int y = 30; y <= 95; y++) {
          int stock = body.stock(x, y, z);
          if (stock > 0) {
            assertTrue(y > 70 - p.radiusY() && y < 70 + p.radiusY());
            assertTrue(stock >= 100 && stock <= 1000);
          }
          // Neither far lower ground nor tall cliff makes an extruded resource curtain.
          if (Math.abs(y - 70) > p.radiusY()) assertEquals(0, stock);
        }
        assertEquals(0, body.stock(x, ground - 40, z));
      }
  }

  @Test
  void chunkTraversalAndDistantAnchorCannotChangeCenterShapeOrRichness() {
    var s = new ResourceVeins.Settings(48, 48, 500, 5000, 16, 5, 8, .3);
    var p = ResourceVeins.patch(19, 24, -1, 0, s);
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
      for (var p : ResourceVeins.inChunk(19, 24, c[0], c[1], s)) {
        // A single deterministic anchor source at its own coordinates, never the decorating chunk.
        int anchor = 70 + Math.floorDiv(p.x(), 47) - Math.floorDiv(p.z(), 39);
        var b = p.atHeight(anchor);
        for (int x = c[0] * 16; x < c[0] * 16 + 16; x++)
          for (int z = c[1] * 16; z < c[1] * 16 + 16; z++) {
            var column = b.column(x, z);
            for (int y = b.minY(); y <= b.maxY(); y++) {
              int amount = column.stock(y);
              if (amount > 0) result.merge(x + "," + y + "," + z, amount, Math::max);
            }
          }
      }
    return result;
  }

  @Test
  void maximumConfigurationStaysBoundedAndHasFullCrossSections() {
    var p =
        ResourceVeins.patch(
            42, 19, 0, 0, new ResourceVeins.Settings(48, 48, 17, 31, 16, 64, 64, .3));
    var b = p.atHeight(-30);
    long count = 0;
    for (int x = p.x() - 49; x <= p.x() + 49; x++)
      for (int z = p.z() - 49; z <= p.z() + 49; z++) {
        var col = b.column(x, z);
        for (int y = -95; y <= 35; y++) {
          int n = col.stock(y);
          if (n > 0) {
            count++;
            assertTrue(n >= 17 && n <= 31);
            assertTrue(
                Math.abs(x - p.x()) <= 48 && Math.abs(z - p.z()) <= 48 && Math.abs(y + 30) < 64);
          }
        }
      }
    assertTrue(count > 100000 && count < 650000);
  }

  @Test
  void smoothBodyHasNoDetachedSpeckles() {
    var p =
        ResourceVeins.patch(91, 21, 0, 0, new ResourceVeins.Settings(12, 12, 5, 8, 16, 8, 8, .3));
    var b = p.atHeight(0);
    var all = new HashSet<String>();
    var seen = new HashSet<String>();
    var q = new ArrayDeque<int[]>();
    q.add(new int[] {p.x(), 0, p.z()});
    for (int x = p.x() - 12; x <= p.x() + 12; x++)
      for (int z = p.z() - 12; z <= p.z() + 12; z++)
        for (int y = -8; y <= 8; y++) if (b.stock(x, y, z) > 0) all.add(x + "," + y + "," + z);
    int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    while (!q.isEmpty()) {
      var at = q.remove();
      String key = at[0] + "," + at[1] + "," + at[2];
      if (!all.contains(key) || !seen.add(key)) continue;
      for (var off : d) q.add(new int[] {at[0] + off[0], at[1] + off[1], at[2] + off[2]});
    }
    assertEquals(all, seen);
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
