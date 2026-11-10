package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.core.FactoryCommands;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Six finite lines. Burner draws its own source coal; electric types use real coal/pole power. */
public final class InserterShowcase {
  public static void build(ServerLevel level, BlockPos o, String scope) {
    var kinds =
        new FactoryKind[] {
          FactoryKind.BURNER_INSERTER,
          FactoryKind.INSERTER,
          FactoryKind.LONG_INSERTER,
          FactoryKind.FAST_INSERTER,
          FactoryKind.BULK_INSERTER,
          FactoryKind.STACK_INSERTER
        };
    for (int i = 0; i < kinds.length; i++) {
      var p = o.offset(3 + i % 3 * 7, 0, i < 3 ? 3 : 9);
      FactoryCommands.machine(level, p, kinds[i], Direction.NORTH);
      var arm = (InserterBlockEntity) level.getBlockEntity(p);
      arm.bindResearch(scope);
      for (var cell : new BlockPos[] {arm.sourcePosition(), arm.targetPosition()})
        level.setBlockAndUpdate(cell, Blocks.CHEST.defaultBlockState());
      var source = (ChestBlockEntity) level.getBlockEntity(arm.sourcePosition());
      int n = i == 5 ? 48 : 32;
      source.setItem(0, new ItemStack(Items.RAW_IRON, n));
      source.setItem(1, new ItemStack(Items.RAW_COPPER, n));
      if (i == 0) {
        source.setItem(2, new ItemStack(Items.COAL, 2));
        var tag = arm.saveWithoutMetadata(level.registryAccess());
        var ids = new net.minecraft.nbt.ListTag();
        for (var id : new String[] {"minecraft:raw_iron", "minecraft:raw_copper", "", "", ""})
          ids.add(net.minecraft.nbt.StringTag.valueOf(id));
        tag.put("Filters", ids);
        arm.loadWithComponents(tag, level.registryAccess());
      }
      if (i == 5) {
        var tag = arm.saveWithoutMetadata(level.registryAccess());
        tag.putInt("HandLimit", 6);
        arm.loadWithComponents(tag, level.registryAccess());
      }
    }
    FactoryCommands.machine(level, o.offset(24, 0, 3), FactoryKind.GENERATOR, Direction.NORTH)
        .items
        .setStackInSlot(8, new ItemStack(Items.COAL, 8));
    for (int x = 2; x <= 27; x += 5)
      FactoryCommands.machine(level, o.offset(x, 0, 6), FactoryKind.MEDIUM_POLE, Direction.NORTH);
  }

  private InserterShowcase() {}
}
