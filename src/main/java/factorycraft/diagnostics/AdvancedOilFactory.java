package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.core.FactoryCommands;
import factorycraft.fluids.FluidKind;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Opt-in full-size finite oil → sulfur → acid → battery factory. No intermediate fluid injection.
 */
public final class AdvancedOilFactory {
  public static boolean available(ServerLevel level) {
    return level.getRecipeManager().getAllRecipesFor(Content.CHEMISTRY.get()).stream()
        .anyMatch(
            r ->
                r.id()
                        .equals(
                            ResourceLocation.fromNamespaceAndPath(
                                "factorycraft", "advanced_oil_processing"))
                    && r.value().machine().equals("oil_refinery"));
  }

  public static void build(ServerLevel level, BlockPos o) {
    level.setBlockAndUpdate(o.offset(2, -1, 3), Content.OIL_DEPOSIT.get().defaultBlockState());
    machine(level, o, 3, 2, FactoryKind.PUMPJACK);
    for (int x = 4; x <= 6; x++) pipe(level, o, x, 0, 2);
    var refinery = machine(level, o, 11, 1, FactoryKind.REFINERY);
    if (!refinery
        .petroleum()
        .selectRecipe(
            ResourceLocation.fromNamespaceAndPath("factorycraft", "advanced_oil_processing")))
      throw new IllegalStateException("Advanced oil recipe missing");
    chemical(level, o, 15, 4);
    chemical(level, o, 15, 10);
    chemical(level, o, 21, 10);
    // West-facing inputs put gas at z=4 and water at z=2, on the physical front.
    for (int x = 12; x <= 14; x++) pipe(level, o, x, 0, 5);
    pipe(level, o, 14, 0, 4);
    for (int x = 18; x <= 20; x++) pipe(level, o, x, 0, 10);
    arm(level, o, 16, 5, Direction.SOUTH);
    var acidFeed = chest(level, o, 16, 6);
    acidFeed.setItem(0, stack("iron_plate", 16));
    arm(level, o, 16, 7, Direction.SOUTH);
    var batteryFeed = chest(level, o, 22, 6);
    batteryFeed.setItem(0, stack("iron_plate", 8));
    batteryFeed.setItem(1, stack("copper_plate", 8));
    arm(level, o, 22, 7, Direction.SOUTH);
    arm(level, o, 24, 9, Direction.EAST);
    chest(level, o, 25, 9);
    // Separate product risers at z=1/3, separated by two Y levels from each other and water.
    for (int y = 0; y <= 5; y++) pipe(level, o, 12, y, 1);
    pipe(level, o, 12, 5, 0);
    for (int x = 12; x <= 18; x++) pipe(level, o, x, 5, 0);
    for (int y = 0; y < 5; y++) pipe(level, o, 18, y, 0);
    for (int y = 0; y <= 7; y++) pipe(level, o, 12, y, 3);
    for (int z = 3; z <= 6; z++) pipe(level, o, 12, 7, z);
    for (int x = 12; x <= 18; x++) pipe(level, o, x, 7, 6);
    pipe(level, o, 18, 7, 5);
    for (int y = 0; y < 7; y++) pipe(level, o, 18, y, 5);
    tank(level, o, 20, 1);
    tank(level, o, 20, 6);
    var water = o.offset(1, 0, 13);
    level.setBlockAndUpdate(water.below(), Blocks.STONE.defaultBlockState());
    for (var side : Direction.Plane.HORIZONTAL)
      if (side != Direction.EAST)
        level.setBlockAndUpdate(water.relative(side), Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
    StructurePlacement.place(
        level,
        o.offset(3, 0, 13),
        Content.FLUID_BLOCKS
            .get(FluidKind.PUMP)
            .get()
            .defaultBlockState()
            .setValue(FluidDeviceBlock.FACING, Direction.EAST));
    for (int y = 0; y <= 9; y++) pipe(level, o, 4, y, 13);
    for (int x = 4; x <= 14; x++) pipe(level, o, x, 9, 13);
    for (int z = 4; z <= 13; z++) pipe(level, o, 6, 9, z);
    for (int z = 2; z <= 13; z++) pipe(level, o, 14, 9, z);
    for (int y = 0; y < 9; y++) {
      pipe(level, o, 6, y, 4);
      pipe(level, o, 14, y, 2);
      pipe(level, o, 14, y, 10);
    }
    machine(level, o, 8, 13, FactoryKind.GENERATOR)
        .items
        .setStackInSlot(8, new ItemStack(Items.COAL, 8));
    FactoryCommands.machine(level, o.offset(13, 0, 6), FactoryKind.MEDIUM_POLE, Direction.NORTH);
    for (int x = 0; x < 30; x += 4)
      for (int z = 0; z < 16; z += 4)
        if (level.getBlockState(o.offset(x, 0, z)).isAir())
          FactoryCommands.machine(
              level, o.offset(x, 0, z), FactoryKind.MEDIUM_POLE, Direction.NORTH);
  }

  private static void chemical(ServerLevel level, BlockPos o, int x, int z) {
    FactoryCommands.machine(level, o.offset(x, 0, z), FactoryKind.CHEMICAL_PLANT, Direction.WEST);
  }

  private static MachineBlockEntity machine(
      ServerLevel level, BlockPos o, int x, int z, FactoryKind kind) {
    return FactoryCommands.machine(level, o.offset(x, 0, z), kind, Direction.EAST);
  }

  private static void arm(ServerLevel level, BlockPos o, int x, int z, Direction facing) {
    FactoryCommands.machine(level, o.offset(x, 0, z), FactoryKind.INSERTER, facing);
  }

  private static ChestBlockEntity chest(ServerLevel level, BlockPos o, int x, int z) {
    level.setBlockAndUpdate(o.offset(x, 0, z), Blocks.CHEST.defaultBlockState());
    return (ChestBlockEntity) level.getBlockEntity(o.offset(x, 0, z));
  }

  private static void pipe(ServerLevel level, BlockPos o, int x, int y, int z) {
    var p = o.offset(x, y, z);
    if (!level.getBlockState(p).isAir()
        && !level.getBlockState(p).is(Content.FLUID_BLOCKS.get(FluidKind.PIPE).get()))
      throw new IllegalStateException("Pipe would overwrite structure at " + p);
    level.setBlockAndUpdate(p, Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
  }

  private static void tank(ServerLevel level, BlockPos o, int x, int z) {
    StructurePlacement.place(
        level,
        o.offset(x, 0, z),
        Content.FLUID_BLOCKS.get(FluidKind.TANK).get().defaultBlockState());
  }

  private static ItemStack stack(String id, int n) {
    return new ItemStack(
        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft", id)), n);
  }

  private AdvancedOilFactory() {}
}
