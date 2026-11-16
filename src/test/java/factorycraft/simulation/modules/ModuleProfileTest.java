package factorycraft.simulation.modules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ModuleProfileTest {
  @Test
  void allNineCanonicalProfiles() {
    int[][] speeds = {{2000, 3000, 5000}, {0, 0, 0}, {-500, -1000, -1500}};
    int[][] energies = {{5000, 6000, 7000}, {-3000, -4000, -5000}, {4000, 6000, 8000}};
    for (var family : ModuleProfile.Family.values())
      for (int tier = 1; tier <= 3; tier++) {
        var p = ModuleProfile.of(family, tier);
        assertEquals(tier, p.tier());
        assertEquals(speeds[family.ordinal()][tier - 1], p.speed());
        assertEquals(energies[family.ordinal()][tier - 1], p.energy());
        assertEquals(
            family == ModuleProfile.Family.PRODUCTIVITY ? new int[] {400, 600, 1000}[tier - 1] : 0,
            p.productivity());
      }
  }

  @Test
  void mixedTierEffectsAreAdditiveAndEnergyHasTwentyPercentFloor() {
    var a = ModuleProfile.of(ModuleProfile.Family.SPEED, 3);
    var b = ModuleProfile.of(ModuleProfile.Family.PRODUCTIVITY, 2);
    var e =
        ModuleEffects.combine(
            a.speed() + b.speed(), a.energy() + b.energy(), b.productivity(), 0, 0, 0);
    assertEquals(14000, e.speed());
    assertEquals(23000, e.energy());
    assertEquals(600, e.productivity());
    assertEquals(2000, ModuleEffects.combine(0, -10000, 0, 0, 0, 0).energy());
  }

  @Test
  void beaconsMultiplyOnlySpeedAndEnergyAndNeverProductivity() {
    var e = ModuleEffects.combine(-1500, 8000, 1000, 5000, 7000, 1);
    assertEquals(16000, e.speed());
    assertEquals(28500, e.energy());
    assertEquals(1000, e.productivity());
  }

  @Test
  void normalQualityBeaconArraysAreNotArtificiallyCappedAtFiveTimesSpeed() {
    var e = ModuleEffects.combine(20000, 28000, 0, 120000, 168000, 12);
    assertTrue(e.speed() > 80000);
    assertTrue(e.energy() > 110000);
    assertDoesNotThrow(() -> factorycraft.simulation.production.PaidWork.budget(5, e.speed()));
    assertDoesNotThrow(() -> factorycraft.simulation.production.FurnaceWork.charge(e.energy(), 0));
  }

  @Test
  void unsupportedTiersFailClosed() {
    assertThrows(
        IllegalArgumentException.class, () -> ModuleProfile.of(ModuleProfile.Family.SPEED, 4));
  }
}
