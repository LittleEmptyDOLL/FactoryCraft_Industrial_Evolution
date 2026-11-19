package factorycraft.trains;

import factorycraft.registry.Content;
import factorycraft.simulation.trains.StockSpec;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class TrainContent {
  public static final Map<StockSpec, DeferredItem<StockItem>> ITEMS =
      new EnumMap<>(StockSpec.class);
  public static final Map<StockSpec, DeferredHolder<EntityType<?>, EntityType<StockEntity>>> TYPES =
      new EnumMap<>(StockSpec.class);
  public static final DeferredHolder<MenuType<?>, MenuType<TrainMenu>> MENU =
      Content.MENUS.register("train", () -> IMenuTypeExtension.create(TrainMenu::new));

  static {
    for (var kind : StockSpec.values()) {
      ITEMS.put(
          kind,
          Content.ITEMS.register(
              kind.id, () -> new StockItem(kind, new Item.Properties().stacksTo(5))));
      TYPES.put(
          kind,
          Content.MOBILE_ENTITIES.register(
              kind.id,
              () ->
                  EntityType.Builder.<StockEntity>of(
                          (type, level) -> new StockEntity(type, level, kind), MobCategory.MISC)
                      .sized(
                          (float) (factorycraft.simulation.trains.StockGeometry.HALF_WIDTH * 2),
                          (float) factorycraft.simulation.trains.StockGeometry.TOP)
                      .clientTrackingRange(12)
                      .updateInterval(3)
                      .build("factorycraft:" + kind.id)));
    }
  }

  private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS =
      DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, "factorycraft");
  public static final DeferredHolder<
          net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent>
      ENGINE =
          SOUNDS.register(
              "train_engine",
              () ->
                  net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                      net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                          "factorycraft", "train_engine")));

  public static void init() {}

  public static void registerAudio(net.neoforged.bus.api.IEventBus bus) {
    SOUNDS.register(bus);
  }

  private TrainContent() {}
}
