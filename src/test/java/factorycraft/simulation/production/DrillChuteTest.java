package factorycraft.simulation.production;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DrillChuteTest {
  @Test
  void perpendicularDropsTouchNearLaneInEveryRotation() {
    for (var b : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
      int rightX = -b[1], rightZ = b[0];
      assertEquals(0, DrillChute.beltLane(rightX, rightZ, b[0], b[1]));
      assertEquals(1, DrillChute.beltLane(-rightX, -rightZ, b[0], b[1]));
    }
  }

  @Test
  void parallelAndAntiparallelDropsUseRightLaneWithoutChangingBeltDirection() {
    for (var b : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
      assertEquals(1, DrillChute.beltLane(b[0], b[1], b[0], b[1]));
      assertEquals(1, DrillChute.beltLane(-b[0], -b[1], b[0], b[1]));
    }
  }
}
