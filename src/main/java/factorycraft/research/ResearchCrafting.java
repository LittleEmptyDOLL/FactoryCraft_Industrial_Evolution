package factorycraft.research;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Vanilla handcrafts share the same team recipe locks as factory production. */
public final class ResearchCrafting {
  public static boolean allowed(Player player, RecipeHolder<?> recipe) {
    if (player.level().isClientSide || player.isCreative() || recipe == null) return true;
    var catalog = ResearchCatalog.get(player.level());
    var id = recipe.id();
    if (!id.getNamespace().equals("factorycraft")) return true;
    if (id.getPath().startsWith("craft_"))
      id =
          net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
              id.getNamespace(), id.getPath().substring(6));
    var scope = ResearchTeams.resolve(player);
    return scope.isPresent() && catalog.allowed(scope.get(), id, player.level());
  }

  private ResearchCrafting() {}
}
