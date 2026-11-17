package factorycraft.nuclear;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class NuclearItems {
  public static ItemStack stack(String id, int n) {
    return new ItemStack(
        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft", id)), n);
  }

  public static boolean is(ItemStack stack, String id) {
    return !stack.isEmpty()
        && BuiltInRegistries.ITEM
            .getKey(stack.getItem())
            .equals(ResourceLocation.fromNamespaceAndPath("factorycraft", id));
  }

  private NuclearItems() {}
}
