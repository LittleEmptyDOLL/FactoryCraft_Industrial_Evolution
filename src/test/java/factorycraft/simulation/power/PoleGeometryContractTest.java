package factorycraft.simulation.power;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class PoleGeometryContractTest {
  @Test
  void smallPoleHeightDoesNotChangeElectricalBalanceOrOtherTiers() {
    assertEquals(3, PoleSpec.SMALL.height);
    assertEquals(1, PoleSpec.SMALL.width);
    assertEquals(5, PoleSpec.SMALL.supply);
    assertEquals(7.5, PoleSpec.SMALL.wire);
    assertEquals(3, PoleSpec.MEDIUM.height);
    assertEquals(7, PoleSpec.MEDIUM.supply);
    assertEquals(9, PoleSpec.MEDIUM.wire);
    assertEquals(5, PoleSpec.BIG.height);
    assertEquals(32, PoleSpec.BIG.wire);
    assertEquals(4, PoleSpec.SUBSTATION.height);
    assertEquals(18, PoleSpec.SUBSTATION.supply);
  }

  @Test
  void allSmallPoleGeometryAndWireEndpointFitThreeCells() throws Exception {
    var text = Files.readString(Path.of("src/main/resources/factorycraft/pole_geometry.json"));
    var pole =
        text.substring(text.indexOf("\"electric_pole\""), text.indexOf("\"medium_electric_pole\""));
    var arrays = java.util.regex.Pattern.compile("\\[([^\\[\\]]+)\\]").matcher(pole);
    double highest = 0;
    int count = 0;
    while (arrays.find()) {
      var xyz =
          java.util.Arrays.stream(arrays.group(1).split(","))
              .mapToDouble(v -> Double.parseDouble(v.trim()))
              .toArray();
      assertEquals(3, xyz.length);
      if (count++ == 0) {
        assertEquals(2.88, xyz[1], 1e-9);
        continue;
      }
      for (int axis = 0; axis < 3; axis++) {
        assertTrue(xyz[axis] >= 0);
        assertTrue(xyz[axis] <= (axis == 1 ? 3 : 1));
      }
      highest = Math.max(highest, xyz[1]);
    }
    assertTrue(count > 5);
    assertTrue(highest > 2.8);
  }
}
