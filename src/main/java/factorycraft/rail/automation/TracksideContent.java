package factorycraft.rail.automation;

import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class TracksideContent {
  public static final Map<TracksideKind, DeferredBlock<TracksideBlock>> BLOCKS =
      new EnumMap<>(TracksideKind.class);
  public static final Map<TracksideKind, DeferredItem<TracksideItem>> ITEMS =
      new EnumMap<>(TracksideKind.class);

  static {
    for (var kind : TracksideKind.values()) {
      BLOCKS.put(
          kind,
          Content.BLOCKS.register(
              kind.id,
              () ->
                  new TracksideBlock(
                      kind,
                      BlockBehaviour.Properties.of()
                          .strength(1.5f)
                          .sound(SoundType.METAL)
                          .noOcclusion()
                          .dynamicShape())));
      ITEMS.put(
          kind,
          Content.ITEMS.register(kind.id, () -> new TracksideItem(kind, new Item.Properties())));
    }
  }

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TracksideBlockEntity>>
      ENTITY =
          Content.ENTITIES.register(
              "rail_trackside",
              () ->
                  BlockEntityType.Builder.of(
                          TracksideBlockEntity::new,
                          BLOCKS.values().stream()
                              .map(DeferredBlock::get)
                              .toArray(net.minecraft.world.level.block.Block[]::new))
                      .build(null));
  public static final DeferredHolder<MenuType<?>, MenuType<RailAutomationMenu>> MENU =
      Content.MENUS.register(
          "rail_automation", () -> IMenuTypeExtension.create(RailAutomationMenu::new));

  public static void init() {}

  private TracksideContent() {}
}
