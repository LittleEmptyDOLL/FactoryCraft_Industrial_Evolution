package factorycraft.simulation.production;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FurnaceWorkTest {
  @Test
  void speedChangesWorkNotPowerPerActiveTick() {
    assertEquals(10000, FurnaceWork.budget(1, 10000));
    assertEquals(20000, FurnaceWork.budget(2, 10000));
    assertEquals(24000, FurnaceWork.budget(2, 12000));
    assertEquals(24, FurnaceWork.charge(10000, 0).energy());
  }

  @Test
  void fractionalPowerIsExactAcrossInterruptions() {
    int remainder = 0, spent = 0;
    for (int tick = 0; tick < 10000; tick++) {
      var c = FurnaceWork.charge(2000, remainder);
      spent += c.energy();
      remainder = c.remainder();
    }
    assertEquals(48000, spent);
    assertEquals(0, remainder);
    var first = FurnaceWork.charge(2000, 0);
    assertEquals(4, first.energy());
    assertEquals(8000, first.remainder());
    assertEquals(5, FurnaceWork.charge(2000, first.remainder()).energy());
  }

  @Test
  void invalidWorkAndPowerStateAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> FurnaceWork.budget(3, 10000));
    assertThrows(IllegalArgumentException.class, () -> FurnaceWork.charge(10000, 10000));
  }
}
