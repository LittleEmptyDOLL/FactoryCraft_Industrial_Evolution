package factorycraft.diagnostics;

import factorycraft.blocks.FactoryKind;
import factorycraft.core.FactoryCommands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Finite component-fed steel/gear/pipe -> engines -> chemical science. No research is granted. */
public final class ChemicalScienceFactory {
  public static ChestBlockEntity build(ServerLevel level, BlockPos origin, String scope) {
    var engines =
        FactoryCommands.machine(
            level, origin.offset(3, 0, 2), FactoryKind.ASSEMBLER, Direction.NORTH);
    var science =
        FactoryCommands.machine(
            level, origin.offset(10, 0, 2), FactoryKind.ASSEMBLER, Direction.NORTH);
    engines.bindResearch(scope);
    science.bindResearch(scope);
    var first = chest(level, origin.offset(4, 0, 0));
    first.setItem(0, stack("steel_plate", 6));
    first.setItem(1, stack("iron_gear", 6));
    first.setItem(2, stack("fluid_pipe", 12));
    var second = chest(level, origin.offset(11, 0, 0));
    second.setItem(0, stack("advanced_circuit", 4));
    second.setItem(1, stack("sulfur", 2));
    for (int x : new int[] {4, 11})
      FactoryCommands.machine(level, origin.offset(x, 0, 1), FactoryKind.INSERTER, Direction.SOUTH);
    for (int x : new int[] {6, 9, 13, 15})
      FactoryCommands.machine(level, origin.offset(x, 0, 3), FactoryKind.INSERTER, Direction.EAST);
    for (int x : new int[] {7, 8, 14})
      FactoryCommands.machine(level, origin.offset(x, 0, 3), FactoryKind.BELT, Direction.EAST);
    var output = chest(level, origin.offset(16, 0, 3));
    FactoryCommands.machine(level, origin.offset(4, 0, 8), FactoryKind.GENERATOR, Direction.NORTH)
        .items
        .setStackInSlot(8, new ItemStack(Items.COAL, 8));
    for (int x : new int[] {2, 9})
      FactoryCommands.machine(
          level, origin.offset(x, 0, 0), FactoryKind.MEDIUM_POLE, Direction.NORTH);
    for (int x : new int[] {4, 13})
      FactoryCommands.machine(
          level, origin.offset(x, 0, 6), FactoryKind.MEDIUM_POLE, Direction.NORTH);
    return output;
  }

  private static ChestBlockEntity chest(ServerLevel level, BlockPos pos) {
    level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
    return (ChestBlockEntity) level.getBlockEntity(pos);
  }

  private static ItemStack stack(String path, int count) {
    return new ItemStack(
        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft", path)),
        count);
  }

  private ChemicalScienceFactory() {}
}
