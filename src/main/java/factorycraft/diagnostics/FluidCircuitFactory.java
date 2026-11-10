package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.circuits.*;
import factorycraft.core.FactoryCommands;
import factorycraft.fluids.*;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Finite source water and real coal/pole power; output volume and pump FE are never seeded. */
public final class FluidCircuitFactory {
  public static FluidBlockEntity fluid(ServerLevel level, BlockPos p, FluidKind kind) {
    StructurePlacement.place(
        level,
        p,
        Content.FLUID_BLOCKS
            .get(kind)
            .get()
            .defaultBlockState()
            .setValue(
                FluidDeviceBlock.FACING,
                kind == FluidKind.PUMP ? Direction.EAST : Direction.NORTH));
    return (FluidBlockEntity) level.getBlockEntity(p);
  }

  public static void build(ServerLevel level, BlockPos o) {
    var source = fluid(level, o.offset(3, 0, 5), FluidKind.TANK);
    source.cell().fill(new FluidKey(new FluidStack(Fluids.WATER, 1)), 4000, false);
    source.setChanged();
    for (int x = 5; x <= 6; x++)
      level.setBlockAndUpdate(
          o.offset(x, 0, 6), Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    fluid(level, o.offset(8, 0, 6), FluidKind.PUMP);
    fluid(level, o.offset(11, 0, 7), FluidKind.TANK);
    level.setBlockAndUpdate(
        o.offset(9, 0, 6), Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    StructurePlacement.place(
        level,
        o.offset(13, 0, 8),
        Content.FACTORY
            .get(FactoryKind.CIRCUIT_READER)
            .get()
            .defaultBlockState()
            .setValue(FactoryBlock.FACING, Direction.WEST));
    StructurePlacement.place(
        level,
        o.offset(8, 0, 5),
        Content.FACTORY
            .get(FactoryKind.CIRCUIT_CONTROLLER)
            .get()
            .defaultBlockState()
            .setValue(FactoryBlock.FACING, Direction.SOUTH));
    var reader = (CircuitNodeBlockEntity) level.getBlockEntity(o.offset(13, 0, 8));
    var controller = (CircuitNodeBlockEntity) level.getBlockEntity(o.offset(8, 0, 5));
    var r = reader.saveWithoutMetadata(level.registryAccess());
    r.getCompound("Circuit")
        .put(
            "Reader",
            ReaderSettings.fluid(new FluidStack(Fluids.WATER, 1)).save(level.registryAccess()));
    reader.loadWithComponents(r, level.registryAccess());
    var c = controller.saveWithoutMetadata(level.registryAccess());
    c.getCompound("Circuit")
        .put(
            "Program",
            new CircuitProgram(CircuitProgram.A, CircuitProgram.A, 1, 1000, false).save());
    controller.loadWithComponents(c, level.registryAccess());
    reader.links.add(
        new CircuitLink(controller.getBlockPos(), controller.assembly().identity(), 0, 0, 0));
    controller.links.add(
        new CircuitLink(reader.getBlockPos(), reader.assembly().identity(), 0, 0, 0));
    reader.setChanged();
    controller.setChanged();
    var generator =
        FactoryCommands.machine(level, o.offset(2, 0, 0), FactoryKind.GENERATOR, Direction.NORTH);
    generator.items.setStackInSlot(8, new ItemStack(Items.COAL, 2));
    FactoryCommands.machine(level, o.offset(5, 0, 3), FactoryKind.MEDIUM_POLE, Direction.NORTH);
  }

  private FluidCircuitFactory() {}
}
