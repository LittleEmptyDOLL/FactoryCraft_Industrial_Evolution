package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.circuits.*;
import factorycraft.core.FactoryCommands;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Finite inventory feedback and a separate staged arithmetic chain; no fabricated FE/results. */
public final class CircuitFactory {
  private static CircuitNodeBlockEntity node(
      ServerLevel level, BlockPos p, FactoryKind kind, Direction facing, int op, int scalar) {
    StructurePlacement.place(
        level,
        p,
        Content.FACTORY.get(kind).get().defaultBlockState().setValue(FactoryBlock.FACING, facing));
    var n = (CircuitNodeBlockEntity) level.getBlockEntity(p);
    var tag = n.saveWithoutMetadata(level.registryAccess());
    tag.getCompound("Circuit")
        .put(
            "Program",
            new CircuitProgram(CircuitProgram.A, CircuitProgram.A, op, scalar, false).save());
    n.loadWithComponents(tag, level.registryAccess());
    return n;
  }

  private static void wire(
      CircuitNodeBlockEntity a, int pa, CircuitNodeBlockEntity b, int pb, int color) {
    a.links.add(new CircuitLink(b.getBlockPos(), b.assembly().identity(), pa, pb, color));
    b.links.add(new CircuitLink(a.getBlockPos(), a.assembly().identity(), pb, pa, color));
    a.setChanged();
    b.setChanged();
  }

  public static void build(ServerLevel level, BlockPos o) {
    FactoryCommands.machine(level, o.offset(8, 0, 5), FactoryKind.FAST_INSERTER, Direction.NORTH);
    var arm = (InserterBlockEntity) level.getBlockEntity(o.offset(8, 0, 5));
    for (var p : new BlockPos[] {arm.sourcePosition(), arm.targetPosition()})
      level.setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
    ((ChestBlockEntity) level.getBlockEntity(arm.sourcePosition()))
        .setItem(0, new ItemStack(Items.RAW_IRON, 32));
    var reader = node(level, o.offset(9, 0, 4), FactoryKind.CIRCUIT_READER, Direction.WEST, 0, 0);
    var controller =
        node(level, o.offset(9, 0, 5), FactoryKind.CIRCUIT_CONTROLLER, Direction.WEST, 1, 8);
    wire(reader, 0, controller, 0, 0);
    var constant = node(level, o.offset(16, 0, 5), FactoryKind.CONSTANT, Direction.NORTH, 0, 4);
    var arithmetic = node(level, o.offset(20, 0, 4), FactoryKind.ARITHMETIC, Direction.NORTH, 0, 3);
    var decider = node(level, o.offset(24, 0, 4), FactoryKind.DECIDER, Direction.NORTH, 0, 6);
    wire(constant, 0, arithmetic, 0, 0);
    wire(arithmetic, 1, decider, 0, 1);
    var generator =
        FactoryCommands.machine(level, o.offset(2, 0, 9), FactoryKind.GENERATOR, Direction.NORTH);
    generator.items.setStackInSlot(8, new ItemStack(Items.COAL, 2));
    FactoryCommands.machine(level, o.offset(6, 0, 9), FactoryKind.POLE, Direction.NORTH);
    FactoryCommands.machine(level, o.offset(9, 0, 8), FactoryKind.POLE, Direction.NORTH);
  }

  private CircuitFactory() {}
}
