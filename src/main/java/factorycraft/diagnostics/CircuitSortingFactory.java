package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.circuits.*;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;

/** Finite tiered sorting; players set each constant from 0 to 1 to open its splitter. */
public final class CircuitSortingFactory {
  public static void build(ServerLevel level, BlockPos o) {
    SortingFactory.buildTiered(level, o);
    for (int tier = 0; tier < 3; tier++) {
      var p = o.offset(2 + 8 * tier, 0, 5);
      var c = p.west();
      var a = o.offset(2 + 8 * tier, 0, 1);
      StructurePlacement.place(
          level,
          c,
          Content.FACTORY
              .get(FactoryKind.CIRCUIT_CONTROLLER)
              .get()
              .defaultBlockState()
              .setValue(FactoryBlock.FACING, Direction.EAST));
      StructurePlacement.place(
          level, a, Content.FACTORY.get(FactoryKind.CONSTANT).get().defaultBlockState());
      var gate = (CircuitNodeBlockEntity) level.getBlockEntity(c);
      var constant = (CircuitNodeBlockEntity) level.getBlockEntity(a);
      var tag = constant.saveWithoutMetadata(level.registryAccess());
      tag.getCompound("Circuit")
          .put(
              "Program",
              new CircuitProgram(CircuitProgram.A, CircuitProgram.A, 0, 0, false).save());
      constant.loadWithComponents(tag, level.registryAccess());
      int color = tier == 0 ? 0 : tier + 1;
      wire(constant, gate, color);
      if (tier == 0) wire(constant, gate, 1);
      CircuitBinding.set(
          (BeltBlockEntity) level.getBlockEntity(p),
          new CircuitBinding(c, gate.assembly().identity()));
    }
  }

  private static void wire(CircuitNodeBlockEntity a, CircuitNodeBlockEntity b, int color) {
    a.links.add(new CircuitLink(b.getBlockPos(), b.assembly().identity(), 0, 0, color));
    b.links.add(new CircuitLink(a.getBlockPos(), a.assembly().identity(), 0, 0, color));
    a.setChanged();
    b.setChanged();
  }

  private CircuitSortingFactory() {}
}
