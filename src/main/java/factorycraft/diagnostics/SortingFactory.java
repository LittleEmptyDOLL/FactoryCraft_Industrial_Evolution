package factorycraft.diagnostics;

import factorycraft.blockentity.BeltBlockEntity;
import factorycraft.blocks.*;
import factorycraft.core.FactoryCommands;
import factorycraft.logistics.SplitterSettings;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Real two-input filter line: no intermediate or output items are injected. */
public final class SortingFactory {
  public static void build(ServerLevel level, BlockPos o) {
    var p = o.offset(8, 0, 5);
    StructurePlacement.place(
        level, p, Content.FACTORY.get(FactoryKind.SPLITTER).get().defaultBlockState());
    var b = (BeltBlockEntity) level.getBlockEntity(p);
    var tag = b.saveWithoutMetadata(level.registryAccess());
    tag.put(
        "Splitter",
        new SplitterSettings(0, 1, ResourceLocation.withDefaultNamespace("raw_iron")).save());
    b.loadWithComponents(tag, level.registryAccess());
    for (int x : new int[] {8, 9}) {
      FactoryCommands.machine(level, o.offset(x, 0, 6), FactoryKind.BELT, Direction.NORTH);
      FactoryCommands.machine(level, o.offset(x, 0, 7), FactoryKind.INSERTER, Direction.NORTH);
      level.setBlockAndUpdate(o.offset(x, 0, 8), Blocks.CHEST.defaultBlockState());
      var source = (ChestBlockEntity) level.getBlockEntity(o.offset(x, 0, 8));
      source.setItem(0, new ItemStack(Items.RAW_IRON, 16));
      source.setItem(1, new ItemStack(Items.RAW_COPPER, 16));
      FactoryCommands.machine(level, o.offset(x, 0, 4), FactoryKind.BELT, Direction.NORTH);
    }
    FactoryCommands.machine(level, o.offset(7, 0, 4), FactoryKind.INSERTER, Direction.WEST);
    FactoryCommands.machine(level, o.offset(10, 0, 4), FactoryKind.INSERTER, Direction.EAST);
    level.setBlockAndUpdate(o.offset(6, 0, 4), Blocks.CHEST.defaultBlockState());
    level.setBlockAndUpdate(o.offset(11, 0, 4), Blocks.CHEST.defaultBlockState());
  }

  private SortingFactory() {}
}
