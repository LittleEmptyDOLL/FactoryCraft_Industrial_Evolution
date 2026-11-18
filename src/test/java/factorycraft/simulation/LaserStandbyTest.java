package factorycraft.simulation;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.combat.LaserStandby;
import org.junit.jupiter.api.Test;

class LaserStandbyTest {
  @Test
  void nearBufferSizedShotPaysEveryIdleUnitAcrossTheNextCharge() {
    var p = new EnergyBuffer(2136, () -> {});
    var bill = new LaserStandby();
    p.restore(2136);
    assertTrue(bill.begin(p));
    assertTrue(p.spend(2134));
    bill.finish(p);
    assertEquals(0, p.getEnergyStored());
    assertEquals(1, bill.debt());
    assertEquals(1, bill.remainder());
    var saved = new LaserStandby();
    saved.restore(bill.remainder(), bill.debt());
    p.receiveEnergy(5, false);
    assertTrue(saved.begin(p));
    saved.finish(p);
    assertEquals(1, p.getEnergyStored());
    assertEquals(0, saved.debt());
    assertEquals(2, saved.remainder());
  }

  @Test
  void outagesDoNotInventWorkOrAccumulateUnlimitedUnpaidIdle() {
    var p = new EnergyBuffer(2136, () -> {});
    var bill = new LaserStandby();
    bill.restore(4, 3);
    for (int i = 0; i < 10000; i++) assertFalse(bill.begin(p));
    assertEquals(3, bill.debt());
    assertEquals(4, bill.remainder());
    p.receiveEnergy(2, false);
    assertFalse(bill.begin(p));
    assertEquals(2, p.getEnergyStored());
    p.receiveEnergy(2, false);
    assertTrue(bill.begin(p));
    bill.finish(p);
    assertEquals(3, bill.debt());
    assertEquals(0, bill.remainder());
  }

  @Test
  void fiveActiveTicksCostExactlySixteenFE() {
    var p = new EnergyBuffer(2136, () -> {});
    p.restore(2136);
    var bill = new LaserStandby();
    for (int i = 0; i < 5; i++) {
      assertTrue(bill.begin(p));
      bill.finish(p);
    }
    assertEquals(2120, p.getEnergyStored());
    assertEquals(0, bill.remainder());
    assertEquals(0, bill.debt());
    assertThrows(IllegalArgumentException.class, () -> bill.restore(0, 5));
  }
}
