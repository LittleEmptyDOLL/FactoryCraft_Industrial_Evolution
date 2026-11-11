package factorycraft.simulation.fluids;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class BoundaryFluidCellTest {
  @Test
  void fractionalPressureCreditPersistsButDoesNotCatchUpOffline() {
    var c = new FluidCell<String>(1000, () -> {});
    c.restore("water", 1);
    var tick = new AtomicLong();
    var p =
        new BoundaryFluidCell<String>(
            () -> c, () -> true, tick::get, 60, () -> false, () -> true, () -> {});
    for (int i = 0; i < 16; i++) {
      tick.incrementAndGet();
      assertEquals(0, p.drain(1, false));
    }
    tick.incrementAndGet();
    assertEquals(1, p.drain(1, false));
    assertEquals(0, c.amount());
    c.restore("water", 1);
    tick.set(100000);
    assertEquals(0, p.drain(1, false));
  }

  @Test
  void simulationsAndRepeatedCallersCannotSpendQuotaOrVolumeTwice() {
    var c = new FluidCell<String>(1000, () -> {});
    c.restore("water", 500);
    var tick = new AtomicLong();
    var paid = new AtomicInteger();
    var p =
        new BoundaryFluidCell<String>(
            () -> c, () -> true, tick::get, 60, () -> false, () -> true, paid::incrementAndGet);
    for (int i = 0; i < 10; i++) assertEquals(30, p.drain(100, true));
    assertEquals(500, c.amount());
    assertEquals(0, paid.get());
    assertEquals(20, p.drain(20, false));
    assertEquals(10, p.drain(20, false));
    assertEquals(0, p.drain(20, false));
    tick.incrementAndGet();
    assertEquals(28, p.drain(100, false));
    assertEquals(442, c.amount());
  }

  @Test
  void powerAndPhysicalAvailabilityGateActualTransferAndManualContainersOnlyBypassPressure() {
    var c = new FluidCell<String>(1000, () -> {});
    var power = new AtomicBoolean();
    var live = new AtomicBoolean(true);
    var manual = new AtomicBoolean();
    var p =
        new BoundaryFluidCell<String>(
            () -> c,
            live::get,
            () -> 0,
            60,
            manual::get,
            power::get,
            () -> assertTrue(power.get()));
    assertEquals(0, p.fill("water", 1000, false));
    power.set(true);
    assertEquals(60, p.fill("water", 1000, false));
    manual.set(true);
    assertEquals(940, p.fill("water", 1000, false));
    live.set(false);
    assertEquals(0, p.drain(1000, false));
    assertEquals(1000, c.amount());
  }

  @Test
  void differentBoundariesShareMassWithoutSharingOneSegmentRateLimit() {
    var c = new FluidCell<String>(10000, () -> {});
    c.restore("water", 5000);
    var a =
        new BoundaryFluidCell<String>(
            () -> c, () -> true, () -> 0, 3000, () -> false, () -> true, () -> {});
    var b =
        new BoundaryFluidCell<String>(
            () -> c, () -> true, () -> 0, 3000, () -> false, () -> true, () -> {});
    assertEquals(1500, a.drain(2000, false));
    assertEquals(1050, b.drain(2000, false));
    assertEquals(2450, c.amount());
  }

  @Test
  void pressurizedPumpDriverAndExternalCallersShareOnePhysicalQuota() {
    var c = new FluidCell<String>(1000, () -> {});
    c.restore("water", 500);
    var p =
        new BoundaryFluidCell<String>(
            () -> c, () -> true, () -> 0, 60, () -> false, () -> true, () -> {}, () -> Double.NaN);
    var target = new FluidCell<String>(1000, () -> {});
    assertEquals(40, p.transferTo(target, 40, false));
    assertEquals(20, p.drain(60, false));
    assertEquals(0, p.transferTo(target, 60, false));
    assertEquals(440, c.amount());
  }

  @Test
  void manualContainerDoesNotBypassOverlongSegmentQuarantine() {
    var g = new PipelineNetwork<String>();
    var raw = new FluidCell<String>(1000, () -> {});
    raw.restore("water", 731);
    var n = g.add(new PipelineNetwork.Site(0, 0, 320, 0, 0, 0, 0), raw);
    while (g.pending() > 0) g.advance(10);
    var p =
        new BoundaryFluidCell<String>(
            () -> n.port(0), () -> true, () -> 0, 3000, () -> true, () -> true, () -> {});
    assertEquals(731, p.amount());
    assertEquals(0, p.drain(731, true));
    assertEquals(0, p.drain(731, false));
    assertEquals(731, n.owned().amount());
  }
}
