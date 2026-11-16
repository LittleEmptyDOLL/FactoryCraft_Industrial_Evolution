package factorycraft.gametest;

import factorycraft.blockentity.FactoryBlockEntity;
import factorycraft.circuits.*;
import factorycraft.diagnostics.CircuitFactory;
import factorycraft.simulation.circuits.*;
import java.util.*;

/** Replaces removed remote reader/controller fixtures with actual direct contacts. */
final class DirectGateFixture {
  static CircuitAttachment gate(
      FactoryBlockEntity owner,
      SignalKey key,
      SignalMath.Compare comparison,
      int threshold,
      int colors) {
    var n = owner.circuit(true);
    CircuitFactory.configure(
        n,
        CircuitFactory.settings(
            false, true, List.of(CircuitFactory.condition(key, comparison, threshold)), colors, 0),
        n.program());
    if (owner instanceof factorycraft.blockentity.BeltBlockEntity b) b.refreshCircuitControl();
    return n;
  }

  static CircuitAttachment sensor(
      net.minecraft.world.level.block.entity.BlockEntity owner, int colors) {
    var n = CircuitAccess.at(owner.getLevel(), owner.getBlockPos());
    CircuitFactory.configure(
        n, CircuitFactory.settings(true, false, List.of(), 0, colors), n.program());
    return n;
  }

  static void wire(CircuitAttachment a, CircuitAttachment b, int color) {
    CircuitFactory.wire(a, a.outputPort(), b, 0, color);
  }

  private DirectGateFixture() {}
}
