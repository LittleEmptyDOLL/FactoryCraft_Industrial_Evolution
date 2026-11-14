package factorycraft.client.compatibility;

import dev.emi.emi.api.*;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@EmiEntrypoint
public final class FactoryEmiPlugin implements EmiPlugin {
  public void register(EmiRegistry registry) {
    var categories = new HashMap<String, EmiRecipeCategory>();
    for (var r : RecipeDisplay.all()) {
      var category = categories.get(r.machine());
      if (category == null) {
        category =
            new EmiRecipeCategory(
                ResourceLocation.fromNamespaceAndPath("factorycraft", r.machine()),
                EmiStack.of(r.catalyst()));
        categories.put(r.machine(), category);
        registry.addCategory(category);
        registry.addWorkstation(category, EmiStack.of(r.catalyst()));
      }
      registry.addRecipe(new Display(category, r));
    }
    var smelting = categories.get("smelting");
    for (var kind : factorycraft.blocks.FactoryKind.values())
      if (kind.furnace()) {
        var stack =
            EmiStack.of(
                new net.minecraft.world.item.ItemStack(
                    factorycraft.registry.Content.FACTORY.get(kind).get()));
        if (smelting != null && kind != factorycraft.blocks.FactoryKind.STONE_FURNACE)
          registry.addWorkstation(smelting, stack);
        registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, stack);
      }
    var dry = categories.get("assembling_machine");
    if (dry != null)
      registry.addWorkstation(
          dry,
          EmiStack.of(
              new net.minecraft.world.item.ItemStack(
                  factorycraft.registry.Content.FACTORY
                      .get(factorycraft.blocks.FactoryKind.ADVANCED_ASSEMBLER)
                      .get())));
  }

  private record Display(EmiRecipeCategory category, RecipeDisplay recipe) implements EmiRecipe {
    public EmiRecipeCategory getCategory() {
      return category;
    }

    public ResourceLocation getId() {
      return recipe.id();
    }

    public List<EmiIngredient> getInputs() {
      var result = new ArrayList<EmiIngredient>();
      for (var input : recipe.items())
        result.add(
            EmiIngredient.of(
                input.stream().map(EmiStack::of).toList(),
                input.isEmpty() ? 1 : input.getFirst().getCount()));
      for (var fluid : recipe.fluids())
        result.add(
            EmiIngredient.of(
                fluid.stream()
                    .map(f -> EmiStack.of(f.getFluid(), f.getComponentsPatch(), f.getAmount()))
                    .toList(),
                fluid.isEmpty() ? 1 : fluid.getFirst().getAmount()));
      return result;
    }

    public List<EmiStack> getOutputs() {
      var result = new ArrayList<EmiStack>();
      recipe.results().forEach(s -> result.add(EmiStack.of(s)));
      recipe
          .outputs()
          .forEach(
              f -> result.add(EmiStack.of(f.getFluid(), f.getComponentsPatch(), f.getAmount())));
      return result;
    }

    public int getDisplayWidth() {
      return 168;
    }

    public int getDisplayHeight() {
      return 108;
    }

    public void addWidgets(WidgetHolder widgets) {
      int input = 0;
      for (var ingredient : getInputs()) {
        widgets.addSlot(ingredient, 2 + (input % 4) * 20, 2 + (input / 4) * 20);
        input++;
      }
      int output = 0;
      for (var ingredient : getOutputs()) {
        widgets
            .addSlot(ingredient, 92 + (output % 3) * 20, 2 + (output / 3) * 20)
            .recipeContext(this);
        output++;
      }
      widgets.addText(
          Component.literal(recipe.ticks() + " t / " + recipe.energy() + " FE/t"),
          2,
          60,
          0xffffff,
          false);
      var lines = recipe.noteLines();
      for (int line = 0; line < lines.size(); line++)
        widgets.addText(lines.get(line), 2, 74 + line * 9, 0xffffff, false);
      if (!recipe.note().isEmpty())
        widgets.addTooltipText(List.of(Component.literal(recipe.note())), 2, 74, 164, 30);
    }
  }
}
