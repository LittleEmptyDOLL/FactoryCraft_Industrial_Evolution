package factorycraft.resources;

import net.minecraft.world.item.*;

public enum ResourceKind {
  IRON("iron_ore", Items.RAW_IRON),
  COPPER("copper_ore", Items.RAW_COPPER),
  COAL("coal_deposit", Items.COAL),
  STONE("stone_deposit", Items.COBBLESTONE),
  URANIUM("uranium_ore", null);
  public final String id;
  private final Item result;

  ResourceKind(String id, Item result) {
    this.id = id;
    this.result = result;
  }

  public ItemStack result() {
    return this == URANIUM
        ? new ItemStack(factorycraft.registry.Content.URANIUM_ORE_ITEM.get())
        : new ItemStack(result);
  }
}
