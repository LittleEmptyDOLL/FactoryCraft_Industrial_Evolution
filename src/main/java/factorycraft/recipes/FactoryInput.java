package factorycraft.recipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.items.IItemHandler;

/** Virtual contiguous inputs; real output/fuel indices remain inaccessible to recipes. */
public record FactoryInput(IItemHandler inventory) implements RecipeInput {
  public static int realSlot(int slot) {
    return slot < 4 ? slot : slot + 5;
  }

  @Override
  public ItemStack getItem(int slot) {
    return inventory.getStackInSlot(realSlot(slot));
  }

  @Override
  public int size() {
    return inventory.getSlots() == 21 ? 16 : 4;
  }
}
