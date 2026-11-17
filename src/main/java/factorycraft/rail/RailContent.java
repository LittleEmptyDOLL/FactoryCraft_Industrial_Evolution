package factorycraft.rail;

import factorycraft.registry.Content;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.*;

public final class RailContent {
  public static final DeferredBlock<RailBlock> BED =
      Content.BLOCKS.register(
          "rail_bed",
          () ->
              new RailBlock(
                  BlockBehaviour.Properties.of()
                      .strength(.8f)
                      .noOcclusion()
                      .dynamicShape()
                      .sound(SoundType.METAL)));
  public static final DeferredItem<RailItem> RAIL =
      Content.ITEMS.register("rail", () -> new RailItem(new Item.Properties()));
  public static final DeferredItem<Item> IRON_STICK =
      Content.ITEMS.registerSimpleItem("iron_stick");
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RailCellBlockEntity>>
      CELL =
          Content.ENTITIES.register(
              "rail_cell",
              () -> BlockEntityType.Builder.of(RailCellBlockEntity::new, BED.get()).build(null));

  public static void init() {}

  private RailContent() {}
}
