package factorycraft.simulation;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.inserters.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ArmCycleTest {
  private static final List<ArmCycle.Spec> SPECS =
      List.of(
          ArmCycle.BURNER,
          ArmCycle.STANDARD,
          ArmCycle.LONG,
          ArmCycle.FAST,
          ArmCycle.BULK,
          ArmCycle.STACK);

  @Test
  void exactOriginalNominalUnitsAndRelativeCycleEnergy() {
    int[] expected = {76, 70, 48, 24, 24, 24};
    for (int i = 0; i < SPECS.size(); i++) {
      var spec = SPECS.get(i);
      assertEquals(expected[i], spec.units());
      assertEquals(expected[i] / 3d, spec.ticksPerCycle());
      var cycle = new ArmCycle(spec);
      var power = new EnergyBuffer(1000000, () -> {});
      power.restore(1000000);
      for (int round = 0; round < 100; round++)
        for (int half = 0; half < 2; half++) {
          cycle.begin();
          while (cycle.remaining() > 0) assertTrue(cycle.advance(power));
        }
      assertEquals(1000000 - 100 * spec.energyPerCycle(), power.getEnergyStored());
      assertEquals(0, cycle.energyRemainder());
    }
  }

  @Test
  void starvationAndRestartKeepEveryPaidFraction() {
    var spec = ArmCycle.LONG;
    var cycle = new ArmCycle(spec);
    var power = new EnergyBuffer(1000, () -> {});
    power.restore(7);
    cycle.begin();
    while (cycle.advance(power)) {}
    int remaining = cycle.remaining(),
        fraction = cycle.energyRemainder(),
        energy = power.getEnergyStored();
    for (int n = 0; n < 100; n++) assertFalse(cycle.advance(power));
    assertEquals(remaining, cycle.remaining());
    assertEquals(fraction, cycle.energyRemainder());
    assertEquals(energy, power.getEnergyStored());
    var copy = new ArmCycle(spec);
    copy.restore(remaining, fraction);
    var copyPower = new EnergyBuffer(1000, () -> {});
    copyPower.restore(energy);
    power.receiveEnergy(100, false);
    copyPower.receiveEnergy(100, false);
    while (cycle.remaining() > 0) {
      assertEquals(cycle.advance(power), copy.advance(copyPower));
      assertEquals(cycle.remaining(), copy.remaining());
      assertEquals(cycle.energyRemainder(), copy.energyRemainder());
    }
    assertEquals(power.getEnergyStored(), copyPower.getEnergyStored());
  }

  @Test
  void emptyPowerCannotAdvanceAnyModelIncludingBurner() {
    for (var spec : SPECS) {
      var cycle = new ArmCycle(spec);
      cycle.begin();
      assertFalse(cycle.advanceUnit(n -> false));
      assertEquals(spec.swing(), cycle.remaining());
    }
  }

  @Test
  void capacityUsesAllSevenBonusesSpaceAgeNormalBonusAndItemLimits() {
    assertEquals(1, InserterCapacity.maximum(ArmCycle.STANDARD, 0, 0));
    assertEquals(2, InserterCapacity.maximum(ArmCycle.BULK, 0, 0));
    assertEquals(6, InserterCapacity.maximum(ArmCycle.STACK, 0, 0));
    assertEquals(3, InserterCapacity.maximum(ArmCycle.STANDARD, 7, 0));
    assertEquals(4, InserterCapacity.maximum(ArmCycle.STANDARD, 7, 2));
    assertEquals(12, InserterCapacity.maximum(ArmCycle.BULK, 7, 2));
    assertEquals(16, InserterCapacity.maximum(ArmCycle.STACK, 7, 2));
    assertEquals(12, InserterCapacity.handLimit(ArmCycle.STACK, 7, 2, 15, 64, true));
    assertEquals(1, InserterCapacity.handLimit(ArmCycle.STACK, 7, 2, 0, 1, true));
    assertEquals(2, InserterCapacity.handLimit(ArmCycle.BULK, 0, 0, 16, 64, false));
  }

  @Test
  void fiveFiltersEmptyListsUnknownIdsAndBlacklistHaveDefinedSemantics() {
    var ids =
        List.of(
            "minecraft:iron_ingot",
            "missing:gone",
            "minecraft:coal",
            "minecraft:stick",
            "minecraft:diamond");
    var white = new ItemFilters(ids, false);
    assertTrue(white.matches("minecraft:coal"));
    assertFalse(white.matches("minecraft:dirt"));
    assertTrue(new ItemFilters(ids, true).matches("minecraft:dirt"));
    assertFalse(new ItemFilters(ids, true).matches("minecraft:diamond"));
    assertTrue(ItemFilters.empty().matches("minecraft:anything"));
    assertTrue(new ItemFilters(Collections.nCopies(5, ""), true).matches("minecraft:anything"));
    assertThrows(IllegalArgumentException.class, () -> new ItemFilters(List.of("one"), false));
  }
}
