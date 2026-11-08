package factorycraft.simulation.modules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ModuleEffectsTest {
  @Test
  void fractionalSpeedPreservesWorkAcrossTicks() {
    var effect = new ModuleEffects(12000, 15000, 0);
    int total = 0, fraction = 0, energy = 0;
    for (int tick = 0; tick < 5; tick++) {
      var step = effect.plan(16, 1, 1000, fraction);
      total += step.work();
      fraction = step.remainder();
      energy += step.energy();
    }
    assertEquals(6, total);
    assertEquals(0, fraction);
    assertEquals(120, energy);
  }

  @Test
  void slowWorkStillRequiresEnergy() {
    var step = new ModuleEffects(9500, 14000, 400).plan(16, 1, 100, 0);
    assertEquals(0, step.work());
    assertEquals(9500, step.remainder());
    assertEquals(23, step.energy());
  }

  @Test
  void efficiencyIsBoundedAndRoundsUp() {
    var effect = new ModuleEffects(10000, -9000, 0);
    assertEquals(2000, effect.energy());
    assertEquals(3, effect.plan(12, 1, 100, 0).energy());
    assertEquals(1, effect.plan(1, 1, 100, 0).energy());
  }

  @Test
  void productiveCyclesNeverRoundIntoFreeBatches() {
    int credit = 0, batches = 0;
    for (int cycle = 0; cycle < 25; cycle++) {
      batches += ModuleEffects.batches(credit, 400);
      credit = ModuleEffects.credit(credit, 400);
    }
    assertEquals(26, batches);
    assertEquals(0, credit);
  }

  @Test
  void beaconDiminishingReturnMatchesNormalTransmission() {
    assertEquals(13000, ModuleEffects.combine(0, 0, 0, 2000, 5000, 1).speed());
    assertEquals(16000, ModuleEffects.combine(0, 0, 0, 8000, 20000, 4).speed());
    assertEquals(25000, ModuleEffects.combine(0, 0, 0, 0, 20000, 4).energy());
  }

  @Test
  void completionClipsWorkWithoutCarryingIntoAnotherRecipe() {
    var step = new ModuleEffects(50000, 10000, 0).plan(12, 2, 1, 9999);
    assertEquals(1, step.work());
    assertEquals(0, step.remainder());
    assertEquals(12, step.energy());
  }

  @Test
  void lateInsertionEarnsOnlyItsActualWork() {
    assertEquals(40, ModuleEffects.accrue(400, 1, 10, 0).credit());
    assertEquals(400, ModuleEffects.accrue(400, 10, 10, 0).credit());
    int credit = 0, remainder = 0;
    for (int n = 0; n < 13; n++) {
      var earned = ModuleEffects.accrue(400, 1, 13, remainder);
      credit += earned.credit();
      remainder = earned.remainder();
    }
    assertEquals(400, credit);
    assertEquals(0, remainder);
  }

  @Test
  void invalidStateCannotGrantNegativeWork() {
    assertThrows(IllegalArgumentException.class, () -> ModuleEffects.NONE.plan(1, 1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> ModuleEffects.NONE.plan(1, 1, 10, 10000));
  }
}
