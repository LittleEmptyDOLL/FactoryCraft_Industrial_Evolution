package factorycraft.research;

import factorycraft.FactoryCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Real produced quantities only. Simulation, inventory moves and portable installation never count.
 */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class ResearchTriggers {
  public static void record(ServerLevel level, String scope, String event, int count) {
    if (count <= 0 || scope.isEmpty() || !ResearchTeams.exists(level.getServer(), scope)) return;
    var data = ResearchData.get(level.getServer());
    var ledger = data.team(scope);
    for (var e : ResearchCatalog.get(level).triggers(event))
      if (ledger.trigger(e.logic(), count)) data.setDirty();
  }

  public static void crafted(ServerLevel level, String scope, ItemStack stack, int count) {
    if (!stack.isEmpty())
      record(level, scope, "craft:" + BuiltInRegistries.ITEM.getKey(stack.getItem()), count);
  }

  @SubscribeEvent
  public static void craft(PlayerEvent.ItemCraftedEvent event) {
    if (event.getEntity().level() instanceof ServerLevel level)
      ResearchTeams.resolve(event.getEntity())
          .ifPresent(
              scope -> crafted(level, scope, event.getCrafting(), event.getCrafting().getCount()));
  }

  @SubscribeEvent
  public static void smelt(PlayerEvent.ItemSmeltedEvent event) {
    if (event.getEntity().level() instanceof ServerLevel level)
      ResearchTeams.resolve(event.getEntity())
          .ifPresent(
              scope -> crafted(level, scope, event.getSmelting(), event.getSmelting().getCount()));
  }

  private ResearchTriggers() {}
}
