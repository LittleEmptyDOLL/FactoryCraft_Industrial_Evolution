package factorycraft.simulation.production;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PaidWorkTest {
  @Test
  void originalQuarterSpeedsAreExactAcrossRepeatedCycles() {
    for (int quarters : new int[] {1, 2, 3, 4, 5, 8}) {
      long work = 0;
      for (int t = 0; t < 8000; t++) work += PaidWork.budget(quarters, 10000);
      assertEquals(2000L * quarters, work / PaidWork.UNIT);
      assertEquals(0, work % PaidWork.UNIT);
    }
  }

  @Test
  void moduleMultiplicationKeepsEveryQuarterBasisPoint() {
    assertEquals(3 * 11733, PaidWork.budget(3, 11733));
    assertEquals(12500, PaidWork.budget(5, 2500));
  }

  @Test
  void paidEnergyRetainsNativeIntegerRemainder() {
    int remainder = 0, total = 0;
    for (int i = 0; i < 1000; i++) {
      var c = PaidWork.charge(10, 7000, remainder);
      total += c.energy();
      remainder = c.remainder();
    }
    assertEquals(7000, total);
    assertEquals(0, remainder);
  }
}
