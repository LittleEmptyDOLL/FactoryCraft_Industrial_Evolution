package factorycraft.simulation.petroleum;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OilYieldTest {
  @Test
  void threeHundredSuccessfulCyclesReduceExactlyOnePercentagePoint() {
    var y = new OilYield(10000);
    for (int i = 0; i < 299; i++) y.commit();
    assertEquals(150000 - 299 * 5, y.current());
    y.commit();
    assertEquals(99 * 1500, y.current());
    assertEquals(300, y.cycles());
  }

  @Test
  void floorIsTwentyPercentAbsoluteOrOneFifthOfInitialAndKeepsProducing() {
    for (int initial : new int[] {2000, 10000, 11550, 20000}) {
      var y = new OilYield(initial);
      for (int i = 0; i < 200000; i++) y.commit();
      assertEquals(Math.max(30000, initial * 3L), y.current());
      long before = y.generated();
      for (int i = 0; i < 1500; i++) y.commit();
      assertTrue(y.generated() > before);
      assertEquals(Math.max(30000, initial * 3L), y.current());
    }
  }

  @Test
  void fractionalProductionIsExactAndRestartsPreserveResidue() {
    var y = new OilYield(11550);
    long numerator = 0;
    for (int i = 0; i < 1000; i++) {
      numerator += y.current();
      y.commit();
      if (i % 17 == 0) {
        var re = new OilYield(2000);
        re.restore(y.initial(), y.current(), y.cycles(), y.residue(), y.generated());
        y = re;
      }
    }
    assertEquals(numerator / 1500, y.generated());
    assertEquals(numerator % 1500, y.residue());
  }

  @Test
  void cappedOutputAndPureReservationsDoNotAdvanceState() {
    var y = new OilYield(10000000);
    for (int i = 0; i < 100; i++) assertEquals(10000, y.nextAmount());
    assertEquals(0, y.generated());
    assertEquals(0, y.cycles());
    assertEquals(10000, y.commit());
  }

  @Test
  void corruptStatesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new OilYield(1999));
    var y = new OilYield(10000);
    assertThrows(IllegalArgumentException.class, () -> y.restore(150000, 0, 1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> y.restore(150000, 150000, 0, 1500, 0));
  }
}
