package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;

/**
 * Opt-in clear-area client fixture. Live coal power plus explicitly initialized inspection stocks.
 */
public final class PowerShowcase {
  public static void build(ServerLevel level, BlockPos o, String scope) {
    for (var entry :
        new Object[][] {
          {1, FactoryKind.POLE},
          {8, FactoryKind.MEDIUM_POLE},
          {16, FactoryKind.BIG_POLE},
          {26, FactoryKind.SUBSTATION}
        })
      factorycraft.core.FactoryCommands.machine(
          level, o.offset((int) entry[0], 0, 3), (FactoryKind) entry[1], Direction.NORTH);
    var generator =
        factorycraft.core.FactoryCommands.machine(
            level, o.offset(17, 0, 7), FactoryKind.GENERATOR, Direction.NORTH);
    generator.items.setStackInSlot(8, new ItemStack(Items.COAL, 8));
    var furnace =
        factorycraft.core.FactoryCommands.machine(
            level, o.offset(8, 0, 6), FactoryKind.FURNACE, Direction.NORTH);
    furnace.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 64));
    var assembler =
        factorycraft.core.FactoryCommands.machine(
            level, o.offset(11, 0, 6), FactoryKind.ASSEMBLER, Direction.NORTH);
    assembler.items.setStackInSlot(
        0,
        new ItemStack(
            BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("factorycraft", "iron_plate")),
            32));
    var labPos =
        StructurePlacement.fromOrigin(
            level,
            o.offset(4, 0, 5),
            Content.FACTORY.get(FactoryKind.LAB).get().defaultBlockState());
    var lab = (LabBlockEntity) level.getBlockEntity(labPos);
    lab.bind(scope);
    lab.items.setStackInSlot(0, new ItemStack(Content.SCIENCE_RED.get(), 4));
    var refinery =
        factorycraft.core.FactoryCommands.machine(
            level, o.offset(20, 0, 6), FactoryKind.REFINERY, Direction.NORTH);
    refinery
        .petroleum()
        .cells
        .get(0)
        .restore(
            new factorycraft.fluids.FluidKey(factorycraft.petroleum.PetroleumFluids.CRUDE.stack(1)),
            137);
    var tankPos =
        StructurePlacement.fromOrigin(
            level,
            o.offset(26, 0, 7),
            Content.FLUID_BLOCKS.get(factorycraft.fluids.FluidKind.TANK).get().defaultBlockState());
    var tank = (FluidBlockEntity) level.getBlockEntity(tankPos);
    tank.cell()
        .restore(
            new factorycraft.fluids.FluidKey(
                new net.neoforged.neoforge.fluids.FluidStack(
                    net.minecraft.world.level.material.Fluids.WATER, 1)),
            1237);
  }

  private PowerShowcase() {}
}
