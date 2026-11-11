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
    var target = (FluidBlockEntity) level.getBlockEntity(o.offset(11, 0, 7));
    var pump = (FluidBlockEntity) level.getBlockEntity(o.offset(8, 0, 6));
    var sensor = target.circuit(true);
    var gate = pump.circuit(true);
    CircuitFactory.configure(
        sensor, CircuitFactory.settings(true, false, java.util.List.of(), 0, 1), sensor.program());
    CircuitFactory.configure(
        gate,
        CircuitFactory.settings(
            false,
            true,
            java.util.List.of(
                CircuitFactory.condition(
                    factorycraft.simulation.circuits.SignalKey.fluid("minecraft:water"),
                    factorycraft.simulation.circuits.SignalMath.Compare.LESS,
                    1000)),
            1,
            0),
        gate.program());
    CircuitFactory.wire(sensor, 0, gate, 0, 0);
    var generator =
        FactoryCommands.machine(level, o.offset(2, 0, 0), FactoryKind.GENERATOR, Direction.NORTH);
    generator.items.setStackInSlot(8, new ItemStack(Items.COAL, 2));
    FactoryCommands.machine(level, o.offset(5, 0, 3), FactoryKind.MEDIUM_POLE, Direction.NORTH);
  }

  private FluidCircuitFactory() {}
}
