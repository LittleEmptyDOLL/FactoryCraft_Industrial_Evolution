package factorycraft.recipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.List;

public record ChemicalInput(IItemHandler inventory,List<FluidStack> fluids) implements RecipeInput {
    public ChemicalInput { fluids=fluids.stream().map(FluidStack::copy).toList(); }
    public ItemStack getItem(int slot) { return inventory.getStackInSlot(slot); }
    public int size() { return 4; }
}
