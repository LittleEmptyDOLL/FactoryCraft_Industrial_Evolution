package factorycraft.recipes;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;

public record ChemicalInput(IItemHandler inventory, List<FluidStack> fluids)
    implements RecipeInput {
  public ChemicalInput {
    fluids = fluids.stream().map(FluidStack::copy).toList();
  }

  public ItemStack getItem(int slot) {
    return inventory.getStackInSlot(FactoryInput.realSlot(slot));
  }

  public int size() {
    return inventory.getSlots() == 21 ? 16 : 4;
  }
}
