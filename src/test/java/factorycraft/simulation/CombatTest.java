package factorycraft.simulation;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.combat.*;
import org.junit.jupiter.api.Test;

class CombatTest {
  @Test
  void sourceCadenceRetainsFractionalShotgunTicks() {
    var clock = new ShotClock();
    int shots = 0;
    for (int tick = 0; tick < 4000; tick++) if (clock.fire(tick, 40)) shots++;
    assertEquals(300, shots);
  }

  @Test
  void repeatedRequestsCannotExceedPaidCadence() {
    var c = new ShotClock();
    int count = 0;
    for (int tick = 0; tick < 100; tick++)
      for (int spam = 0; spam < 500; spam++) if (c.fire(tick, 6)) count++;
    assertEquals(50, count);
  }

  @Test
  void idleDoesNotAccumulateShots() {
    var c = new ShotClock();
    assertTrue(c.fire(0, 15));
    assertTrue(c.fire(1000, 15));
    assertFalse(c.fire(1000, 15));
    assertTrue(c.fire(1005, 15));
  }

  @Test
  void savedClockDoesNotRestartCooldown() {
    var c = new ShotClock();
    c.fire(100, 40);
    var d = new ShotClock();
    d.restore(c.due(), c.last());
    assertFalse(d.fire(110, 40));
    assertTrue(d.fire(114, 40));
  }

  @Test
  void resistanceUsesFlatThenPercentageAndNeverTurnsDamageNegative() {
    assertEquals(5.6, Resistance.apply(10, 3, .2), 1e-9);
    assertEquals(.8 / 7, Resistance.apply(1, 6, .2), 1e-9);
    assertEquals(0, Resistance.apply(0, 3, .2));
    assertEquals(.8 / 3, Resistance.apply(5, 6, .2), 1e-9);
    assertEquals(.4, Resistance.apply(3, 3, .2), 1e-9);
    assertThrows(IllegalArgumentException.class, () -> Resistance.apply(-1, 0, 0));
  }
}
