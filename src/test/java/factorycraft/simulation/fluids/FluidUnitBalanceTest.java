package factorycraft.simulation.fluids;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FluidUnitBalanceTest {
  @Test
  void repeatedScaledRefiningReservesEveryInputAndOutputBeforeObservers() {
    var crude = new FluidCell<String>(40000, () -> {});
    var water = new FluidCell<String>(40000, () -> {});
    var gas = new FluidCell<String>(40000, () -> {});
    var light = new FluidCell<String>(40000, () -> {});
    var heavy = new FluidCell<String>(40000, () -> {});
    long used = 0, producedGas = 0, producedLight = 0, producedHeavy = 0;
    for (int i = 0; i < 1000; i++) {
      crude.restore("crude", FluidUnits.mb(100));
      water.restore("water", FluidUnits.mb(50));
      assertTrue(
          FluidCell.commitBatch(
              List.of(
                  new FluidCell.Mutation<>(crude, null, 0),
                  new FluidCell.Mutation<>(water, null, 0),
                  new FluidCell.Mutation<>(gas, "gas", FluidUnits.mb(55)),
                  new FluidCell.Mutation<>(light, "light", FluidUnits.mb(45)),
                  new FluidCell.Mutation<>(heavy, "heavy", FluidUnits.mb(25))),
              () -> {},
              false));
      used += 1000;
      producedGas += gas.drain(550, false);
      producedLight += light.drain(450, false);
      producedHeavy += heavy.drain(250, false);
    }
    assertEquals(used * 55, producedGas * 100);
    assertEquals(used * 45, producedLight * 100);
    assertEquals(used * 25, producedHeavy * 100);
    assertEquals(1000, FluidUnits.mb(100));
  }

  @Test
  void fullScaledOutputRejectsTheWholeAtomicBatch() {
    var input = new FluidCell<String>(40000, () -> {});
    var output = new FluidCell<String>(40000, () -> {});
    input.restore("acid", 200);
    output.restore("product", 40000);
    assertFalse(
        FluidCell.commitBatch(
            List.of(
                new FluidCell.Mutation<>(input, null, 0),
                new FluidCell.Mutation<>(output, "product", 40001)),
            () -> fail("unreserved resources charged"),
            false));
    assertEquals(200, input.amount());
    assertEquals(40000, output.amount());
  }
}
