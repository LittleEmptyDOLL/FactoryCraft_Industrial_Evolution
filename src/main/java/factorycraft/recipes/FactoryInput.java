package factorycraft.recipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.items.IItemHandler;

public record FactoryInput(IItemHandler inventory) implements RecipeInput {
    @Override public ItemStack getItem(int slot) { return inventory.getStackInSlot(slot); }
    @Override public int size() { return 4; }
}
