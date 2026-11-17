package factorycraft.simulation.nuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NuclearKernelTest {
  @Test
  void canonicalEnergyAndEnthalpyAreExact() {
    assertEquals(375, EnergyUnits.FE * EnergyUnits.JOULES);
    assertEquals(8, EnergyUnits.COLD_STEAM / EnergyUnits.FE);
    assertEquals(5820000, EnergyUnits.TURBINE_PER_TICK * EnergyUnits.JOULES * 20);
    assertEquals(40000000, EnergyUnits.REACTOR_PER_TICK * EnergyUnits.JOULES * 20);
    assertEquals(9700, EnergyUnits.HOT_STEAM * EnergyUnits.JOULES);
  }

  @Test
  void accumulatorUsesOneSharedTickQuotaAndDirection() {
    var s = new ChargeStore();
    assertEquals(390, s.fill(1, 390, false));
    assertEquals(210, s.fill(1, 500, false));
    assertEquals(0, s.drain(1, 600, false));
    assertEquals(600, s.units());
    assertEquals(555, s.drain(2, 555, false));
    assertEquals(0, s.fill(2, 600, false));
    assertEquals(45, s.drain(2, 600, false));
    assertEquals(0, s.units());
  }

  @Test
  void accumulatorSimulationDoesNotAdvanceOrClaim() {
    var s = new ChargeStore();
    s.fill(1, 600, false);
    for (int i = 0; i < 10000; i++) assertEquals(600, s.drain(2, 600, true));
    assertEquals(600, s.units());
    assertEquals(1, s.leaseTick());
    assertEquals(600, s.drain(2, 600, false));
  }

  @Test
  void fractionalChargeAndLeaseRoundTrip() {
    var s = new ChargeStore();
    s.restore(123457, 100, 13, -1);
    var t = new ChargeStore();
    t.restore(s.units(), s.leaseTick(), s.used(), s.direction());
    assertEquals(587, t.drain(100, 600, false));
    assertEquals(122870, t.units());
    assertEquals(0, t.fill(100, 600, false));
  }

  @Test
  void capacityAndRefundNeverCreateCharge() {
    var s = new ChargeStore();
    s.restore(199999, 0, 0, 0);
    assertEquals(1, s.fill(1, 1000, false));
    assertEquals(600, s.drain(2, 999, false));
    s.refund(599);
    assertEquals(199999, s.units());
    assertEquals(599, s.drain(2, 999, false));
    assertThrows(IllegalArgumentException.class, () -> s.refund(1000));
  }

  @Test
  void solarNoNightShadeOrDuplicateTickGeneration() {
    var s = new SolarCycle();
    assertEquals(120, s.generate(1, 6000, true));
    assertEquals(0, s.generate(1, 6000, true));
    assertEquals(0, s.generate(2, 18000, true));
    assertEquals(0, s.generate(3, 6000, false));
    assertEquals(0, s.generate(4, 0, true));
  }

  @Test
  void solarFractionRoundTripPreservesFullDayLedger() {
    var a = new SolarCycle();
    var b = new SolarCycle();
    long total = 0, copy = 0;
    for (int t = 0; t < 24000; t++) {
      total += a.generate(t, t, true);
      copy += b.generate(t, t, true);
      if (t % 97 == 0) {
        var c = new SolarCycle();
        c.restore(b.remainder(), b.lastTick());
        b = c;
      }
    }
    assertEquals(total, copy);
    assertEquals(1119999, total);
    assertEquals(23760, a.remainder());
    assertEquals(a.remainder(), b.remainder());
  }

  @Test
  void unequalHeatCapacitiesConserveAndApproachCommonTemperature() {
    var a = new HeatStore(400000);
    var b = new HeatStore(40000);
    a.add(200000000);
    long initial = a.units() + b.units();
    for (int i = 0; i < 1000; i++) HeatStore.transfer(a, b, 2000000);
    assertEquals(initial, a.units() + b.units());
    assertTrue(Math.abs(a.temperature() - b.temperature()) < .0001);
  }

  @Test
  void heatCapAndMinimumConsumptionNeverMakeEnergy() {
    var s = new HeatStore(40000);
    assertEquals(39400000, s.add(Long.MAX_VALUE));
    assertEquals(0, s.add(5));
    assertEquals(20000000, s.drainAbove(Long.MAX_VALUE, 500));
    assertEquals(500, s.temperature());
    assertEquals(0, s.drainAbove(20000, 500));
  }

  @Test
  void zeroLimitAndSameHeatOwnerAreNoOps() {
    var a = new HeatStore(40000);
    var b = new HeatStore(40000);
    a.add(123456);
    assertEquals(0, HeatStore.transfer(a, a, 12345));
    assertEquals(0, HeatStore.transfer(a, b, 0));
    assertEquals(123456, a.units());
  }

  @Test
  void uraniumPaidChoiceSurvivesInspectionAndReconstruction() {
    var r = new UraniumRoll(817);
    int result = r.beginPaidWork();
    for (int i = 0; i < 10000; i++) assertEquals(result, r.beginPaidWork());
    assertEquals(1, r.serial());
    var copy = new UraniumRoll(0);
    copy.restore(r.seed(), r.serial(), r.pending());
    assertEquals(result, copy.beginPaidWork());
    r.completed();
    copy.completed();
    assertEquals(r.beginPaidWork(), copy.beginPaidWork());
    assertEquals(2, copy.serial());
  }

  @Test
  void uraniumCategoricalDistributionAndDeterminism() {
    int rare = 0;
    for (int n = 0; n < 200000; n++) {
      int outcome = UraniumRoll.outcome(1, n);
      assertEquals(outcome, UraniumRoll.outcome(1, n));
      assertTrue(outcome == 0 || outcome == 1);
      if (outcome == 0) rare++;
    }
    assertTrue(rare > 1150 && rare < 1650, "rare samples=" + rare);
  }
}
