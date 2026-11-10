package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.FactoryKind;
import factorycraft.core.FactoryCommands;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Finite powered comparison: productivity on the left, beacon speed/efficiency on the right. */
public final class ModuleFactory {
  public static void build(ServerLevel level, BlockPos o) {
    var plate =
        BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("factorycraft", "iron_plate"));
    for (int x : new int[] {3, 12}) {
      var machine =
          FactoryCommands.machine(level, o.offset(x, 0, 2), FactoryKind.ASSEMBLER, Direction.NORTH);
      machine.modules.items.setStackInSlot(
          0,
          new ItemStack(
              x == 3 ? Content.PRODUCTIVITY_MODULE.get() : Content.EFFICIENCY_MODULE.get()));
      level.setBlockAndUpdate(o.offset(x + 1, 0, 0), Blocks.CHEST.defaultBlockState());
      ((ChestBlockEntity) level.getBlockEntity(o.offset(x + 1, 0, 0)))
          .setItem(0, new ItemStack(plate, x == 3 ? 50 : 20));
      FactoryCommands.machine(level, o.offset(x + 1, 0, 1), FactoryKind.INSERTER, Direction.SOUTH);
      FactoryCommands.machine(level, o.offset(x + 3, 0, 3), FactoryKind.INSERTER, Direction.EAST);
      FactoryCommands.machine(level, o.offset(x + 4, 0, 3), FactoryKind.BELT, Direction.EAST);
      FactoryCommands.machine(level, o.offset(x + 5, 0, 3), FactoryKind.INSERTER, Direction.EAST);
      level.setBlockAndUpdate(o.offset(x + 6, 0, 3), Blocks.CHEST.defaultBlockState());
    }
    var pos = o.offset(12, 0, 6);
    StructurePlacement.place(
        level, pos, Content.FACTORY.get(FactoryKind.BEACON).get().defaultBlockState());
    ((BeaconBlockEntity) level.getBlockEntity(pos))
        .modules.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
    FactoryCommands.machine(level, o.offset(3, 0, 8), FactoryKind.GENERATOR, Direction.NORTH)
        .items
        .setStackInSlot(8, new ItemStack(Items.COAL, 8));
    for (int x : new int[] {3, 8, 11, 16})
      FactoryCommands.machine(level, o.offset(x, 0, 5), FactoryKind.MEDIUM_POLE, Direction.NORTH);
    for (int x : new int[] {7, 16})
      FactoryCommands.machine(level, o.offset(x, 0, 0), FactoryKind.MEDIUM_POLE, Direction.NORTH);
  }

  private ModuleFactory() {}
}
