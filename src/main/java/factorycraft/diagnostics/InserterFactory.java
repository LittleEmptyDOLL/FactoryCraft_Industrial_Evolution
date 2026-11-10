package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.core.FactoryCommands;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Real finite sources and coal/pole power, never seeded arm energy or delivered products. */
public final class InserterFactory {
  public static void build(ServerLevel level, BlockPos o) {
    var kinds =
        new FactoryKind[] {
          FactoryKind.FAST_INSERTER, FactoryKind.LONG_INSERTER, FactoryKind.BULK_INSERTER
        };
    for (int i = 0; i < kinds.length; i++) {
      var p = o.offset(3 + 7 * i, 0, 5);
      FactoryCommands.machine(level, p, kinds[i], Direction.NORTH);
      var a = (InserterBlockEntity) level.getBlockEntity(p);
      for (var position : new BlockPos[] {a.sourcePosition(), a.targetPosition()})
        level.setBlockAndUpdate(position, Blocks.CHEST.defaultBlockState());
      var source = (ChestBlockEntity) level.getBlockEntity(a.sourcePosition());
      source.setItem(0, new ItemStack(Items.RAW_IRON, 32));
      source.setItem(1, new ItemStack(Items.RAW_COPPER, 32));
      if (i == 2) {
        var tag = a.saveWithoutMetadata(level.registryAccess());
        tag.putBoolean("Enabled", true);
        var filters = new net.minecraft.nbt.ListTag();
        filters.add(net.minecraft.nbt.StringTag.valueOf("minecraft:raw_iron"));
        for (int n = 1; n < 5; n++) filters.add(net.minecraft.nbt.StringTag.valueOf(""));
        tag.put("Filters", filters);
        a.loadWithComponents(tag, level.registryAccess());
      }
    }
    var generator =
        FactoryCommands.machine(level, o.offset(24, 0, 2), FactoryKind.GENERATOR, Direction.NORTH);
    generator.items.setStackInSlot(8, new ItemStack(Items.COAL, 4));
    for (int x = 2; x <= 22; x += 5)
      FactoryCommands.machine(level, o.offset(x, 0, 8), FactoryKind.MEDIUM_POLE, Direction.NORTH);
    FactoryCommands.machine(level, o.offset(25, 0, 6), FactoryKind.MEDIUM_POLE, Direction.NORTH);
  }

  private InserterFactory() {}
}
