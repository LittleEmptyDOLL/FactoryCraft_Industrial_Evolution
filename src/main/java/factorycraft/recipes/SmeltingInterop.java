package factorycraft.recipes;

import factorycraft.blocks.FactoryKind;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Real RecipeManager smelting only; FactoryCraft definitions take deterministic priority. */
public final class SmeltingInterop {
  public static boolean furnace(FactoryKind k) {
    return k.furnace();
  }

  public static FactoryRecipe project(RecipeHolder<SmeltingRecipe> holder, Level level) {
    var r = holder.value();
    return new FactoryRecipe(
        "smelting",
        List.of(new FactoryRecipe.CountedIngredient(r.getIngredients().getFirst(), 1)),
        r.getResultItem(level.registryAccess()),
        r.getCookingTime(),
        24);
  }

  public static int slot(RecipeHolder<SmeltingRecipe> holder, FactoryInput input, Level level) {
    for (int s = 0; s < input.size(); s++)
      if (!input.getItem(s).isEmpty()
          && holder.value().matches(new SingleRecipeInput(input.getItem(s)), level)) return s;
    return -1;
  }

  public static ItemStack result(
      RecipeHolder<SmeltingRecipe> holder, ItemStack source, Level level) {
    return holder.value().assemble(new SingleRecipeInput(source), level.registryAccess());
  }

  private SmeltingInterop() {}
}
