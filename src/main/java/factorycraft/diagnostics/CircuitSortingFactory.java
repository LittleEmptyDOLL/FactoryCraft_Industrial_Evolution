package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.circuits.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;

/** Finite tiered sorting; players set each constant from 0 to 1 to open its splitter. */
public final class CircuitSortingFactory {
  public static void build(ServerLevel level, BlockPos o) {
    SortingFactory.buildTiered(level, o);
    for (int tier = 0; tier < 3; tier++) {
      var p = o.offset(2 + 8 * tier, 0, 5);
      var a = o.offset(2 + 8 * tier, 0, 1);
      int color = tier == 0 ? 0 : tier + 1, mask = tier == 0 ? 3 : 1 << color;
      var constant =
          CircuitFactory.node(
              level,
              a,
              FactoryKind.CONSTANT,
              CircuitFactory.constant(factorycraft.simulation.circuits.SignalKey.A, 0),
              0,
              mask);
      var splitter = ((BeltBlockEntity) level.getBlockEntity(p)).circuit(true);
      CircuitFactory.configure(
          splitter,
          CircuitFactory.settings(
              false,
              true,
              java.util.List.of(
                  CircuitFactory.condition(
                      factorycraft.simulation.circuits.SignalKey.A,
                      factorycraft.simulation.circuits.SignalMath.Compare.GREATER,
                      0)),
              mask,
              0),
          splitter.program());
      CircuitFactory.wire(constant.circuit(true), 0, splitter, 0, color);
      if (tier == 0) CircuitFactory.wire(constant.circuit(true), 0, splitter, 0, 1);
    }
  }

  private CircuitSortingFactory() {}
}
