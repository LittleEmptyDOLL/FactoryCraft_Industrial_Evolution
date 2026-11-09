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
    build(level, o, FactoryKind.SPLITTER);
  }

  public static void buildTiered(ServerLevel level, BlockPos o) {
    var kinds =
        new FactoryKind[] {
          FactoryKind.SPLITTER, FactoryKind.FAST_SPLITTER, FactoryKind.EXPRESS_SPLITTER
        };
    for (int tier = 0; tier < kinds.length; tier++)
      build(level, o.offset(tier * 8 - 6, 0, 0), kinds[tier]);
  }

  public static void build(ServerLevel level, BlockPos o, FactoryKind kind) {
    if (!kind.splitter()) throw new IllegalArgumentException("Not a splitter");
    var belt =
        new FactoryKind[] {FactoryKind.BELT, FactoryKind.FAST_BELT, FactoryKind.EXPRESS_BELT}
            [kind.beltTier - 1];
    var p = o.offset(8, 0, 5);
    StructurePlacement.place(level, p, Content.FACTORY.get(kind).get().defaultBlockState());
    var b = (BeltBlockEntity) level.getBlockEntity(p);
    var tag = b.saveWithoutMetadata(level.registryAccess());
    tag.put(
        "Splitter",
        new SplitterSettings(0, 1, ResourceLocation.withDefaultNamespace("raw_iron")).save());
    b.loadWithComponents(tag, level.registryAccess());
    for (int x : new int[] {8, 9}) {
      FactoryCommands.machine(level, o.offset(x, 0, 6), belt, Direction.NORTH);
      FactoryCommands.machine(level, o.offset(x, 0, 7), FactoryKind.INSERTER, Direction.NORTH);
      level.setBlockAndUpdate(o.offset(x, 0, 8), Blocks.CHEST.defaultBlockState());
      var source = (ChestBlockEntity) level.getBlockEntity(o.offset(x, 0, 8));
      source.setItem(0, new ItemStack(Items.RAW_IRON, 16));
      source.setItem(1, new ItemStack(Items.RAW_COPPER, 16));
      FactoryCommands.machine(level, o.offset(x, 0, 4), belt, Direction.NORTH);
    }
    FactoryCommands.machine(level, o.offset(7, 0, 4), FactoryKind.INSERTER, Direction.WEST);
    FactoryCommands.machine(level, o.offset(10, 0, 4), FactoryKind.INSERTER, Direction.EAST);
    level.setBlockAndUpdate(o.offset(6, 0, 4), Blocks.CHEST.defaultBlockState());
    level.setBlockAndUpdate(o.offset(11, 0, 4), Blocks.CHEST.defaultBlockState());
  }

  private SortingFactory() {}
}
