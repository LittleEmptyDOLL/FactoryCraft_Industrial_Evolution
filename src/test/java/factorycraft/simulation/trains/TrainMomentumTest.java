package factorycraft.simulation.trains;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TrainMomentumTest {
  @Test
  void collisionRemainderUsesInstantaneousSourceSpeedAndRetainsTheInterruptedStepTime() {
    var p = new TrainMotion.Parameters(2000, 0, 0, 0, 20, 2);
    var remaining = new TrainMotion.Remainder(1, p, true, false);
    assertEquals(3.06, remaining.distance(), 1e-12);
    assertEquals(1.01, remaining.consume(.505), 1e-12);
    remaining.impact(.5);
    assertEquals(.25 + .51 + .52, remaining.distance(), 1e-12);
    assertEquals(.52, remaining.speed(), 1e-12);
    assertEquals(.51, remaining.consume(.25 + .1), 1e-12);
    remaining.impact(.25);
    assertEquals(.25 * (1 - .1 / .51) + .26, remaining.distance(), 1e-12);
  }

  @Test
  void lethalWeakContactSpendsActualFiniteEnergyInsteadOfAllSpeedOrHalfTheTrainHealth() {
    var result = TrainImpact.living(2000, 1.2, StockSpec.LOCOMOTIVE, 50, true, true);
    assertTrue(result.continues());
    assertTrue(result.speed() > 1 && result.speed() < 1.2);
    assertEquals(250, result.spent());
    assertEquals(50, result.recoilHp());
    assertEquals(1440, TrainImpact.kinetic(2000, result.speed()) + result.spent(), 1e-9);
  }

  @Test
  void survivedCancelledImmuneOrExhaustedTargetsNeverBecomePassThroughKills() {
    for (var result :
        new TrainImpact.LivingOutcome[] {
          TrainImpact.living(2000, .1, StockSpec.LOCOMOTIVE, 2, true, false),
          TrainImpact.living(2000, 1.2, StockSpec.LOCOMOTIVE, 0, false, false),
          TrainImpact.living(2000, 1.2, StockSpec.LOCOMOTIVE, 50, false, true),
          TrainImpact.living(2000, 1.2, StockSpec.LOCOMOTIVE, 10000, true, true)
        }) {
      assertFalse(result.continues());
      assertEquals(0, result.speed());
    }
  }

  @Test
  void successiveKillsAndFluidCarHitHaveExactMonotoneKineticBudgets() {
    double speed = 1.2, spent = 0;
    for (int i = 0; i < 3; i++) {
      var r = TrainImpact.living(2000, speed, StockSpec.LOCOMOTIVE, 50, true, true);
      assertTrue(r.continues());
      assertTrue(r.speed() < speed);
      spent += r.spent();
      speed = r.speed();
    }
    assertEquals(1440, TrainImpact.kinetic(2000, speed) + spent, 1e-9);
    assertEquals(300, TrainImpact.living(2000, 1.2, StockSpec.FLUID, 50, true, true).spent());
  }
}
