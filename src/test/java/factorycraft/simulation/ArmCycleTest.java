package factorycraft.simulation;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.inserters.ArmCycle;
import org.junit.jupiter.api.Test;

class ArmCycleTest {
  @Test
  void paidCyclesHaveExactDurationAndCost() {
    for (var spec : new ArmCycle.Spec[] {ArmCycle.MECHANICAL, ArmCycle.FAST, ArmCycle.LONG}) {
      var power = new EnergyBuffer(10000, () -> {});
      power.restore(10000);
      var cycle = new ArmCycle(spec);
      int ticks = 0;
      for (int half = 0; half < 2; half++) {
        assertTrue(cycle.pay(power));
        ticks++;
        cycle.begin();
        while (cycle.remaining() > 0) {
          assertTrue(cycle.advance(power));
          ticks++;
        }
      }
      assertEquals(spec.ticksPerCycle(), ticks);
      assertEquals(10000 - spec.energyPerCycle(), power.getEnergyStored());
    }
  }

  @Test
  void starvationPausesExactPhaseAndRestoredPhaseFinishesWithoutFreeWork() {
    var cycle = new ArmCycle(ArmCycle.FAST);
    var power = new EnergyBuffer(100, () -> {});
    power.restore(4);
    cycle.begin();
    assertTrue(cycle.advance(power));
    assertEquals(2, cycle.remaining());
    for (int i = 0; i < 100; i++) assertFalse(cycle.advance(power));
    assertEquals(2, cycle.remaining());
    var restored = new ArmCycle(ArmCycle.FAST);
    restored.restore(cycle.remaining());
    power.receiveEnergy(8, false);
    assertTrue(restored.advance(power));
    assertTrue(restored.advance(power));
    assertEquals(0, restored.remaining());
    assertEquals(0, power.getEnergyStored());
  }

  @Test
  void mechanicalCycleKeepsLegacySwingAndWorksWithoutEnergy() {
    var cycle = new ArmCycle(ArmCycle.MECHANICAL);
    var power = new EnergyBuffer(1, () -> fail("mechanical arm billed FE"));
    int deliveries = 0, phase = 0;
    boolean hand = false;
    for (int tick = 0; tick < 2000; tick++) {
      if (phase > 0) {
        phase--;
        assertTrue(cycle.advance(power));
      } else {
        assertTrue(cycle.pay(power));
        cycle.begin();
        phase = 12;
        hand = !hand;
        if (!hand) deliveries++;
      }
      assertEquals(phase, cycle.remaining());
    }
    assertEquals(77, deliveries);
  }

  @Test
  void invalidSpecsAndMalformedPhasesAreBounded() {
    assertThrows(IllegalArgumentException.class, () -> new ArmCycle.Spec(0, 3, 4));
    var c = new ArmCycle(ArmCycle.LONG);
    c.restore(999);
    assertEquals(7, c.remaining());
    c.restore(-9);
    assertEquals(0, c.remaining());
  }
}
