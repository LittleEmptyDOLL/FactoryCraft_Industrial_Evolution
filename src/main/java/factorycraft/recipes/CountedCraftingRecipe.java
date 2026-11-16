package factorycraft.recipes;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Native shapeless handcraft with one component stack per ingredient and exact original counts. */
public final class CountedCraftingRecipe extends ShapelessRecipe {
  private final List<FactoryRecipe.CountedIngredient> inputs;

  public CountedCraftingRecipe(List<FactoryRecipe.CountedIngredient> inputs, ItemStack result) {
    super("", CraftingBookCategory.MISC, result, ingredients(inputs));
    if (inputs.isEmpty()
        || inputs.size() > 9
        || inputs.stream().anyMatch(i -> i.count() < 1 || i.count() > 64)
        || result.isEmpty()
        || result.getCount() > result.getMaxStackSize())
      throw new IllegalArgumentException("Invalid counted handcraft");
    this.inputs = List.copyOf(inputs);
  }

  private static NonNullList<Ingredient> ingredients(List<FactoryRecipe.CountedIngredient> inputs) {
    var list = NonNullList.<Ingredient>create();
    for (var i : inputs)
      list.add(
          Ingredient.of(
              java.util.Arrays.stream(i.ingredient().getItems())
                  .map(stack -> stack.copyWithCount(i.count()))));
    return list;
  }

  public List<FactoryRecipe.CountedIngredient> inputs() {
    return inputs;
  }

  public int[] plan(CraftingInput input) {
    var seen = new boolean[inputs.size()];
    int[] counts = new int[input.size()];
    for (int slot = 0; slot < input.size(); slot++) {
      var stack = input.getItem(slot);
      if (stack.isEmpty()) continue;
      // Industrial component handcrafts have no container remainders. Reject unsupported data
      // safely.
      if (!stack.getCraftingRemainingItem().isEmpty()) return null;
      int match = -1;
      for (int i = 0; i < inputs.size(); i++)
        if (inputs.get(i).ingredient().test(stack)) {
          if (match >= 0) return null;
          match = i;
        }
      if (match < 0 || seen[match] || stack.getCount() < inputs.get(match).count()) return null;
      seen[match] = true;
      counts[slot] = inputs.get(match).count();
    }
    for (boolean present : seen) if (!present) return null;
    return counts;
  }

  @Override
  public boolean matches(CraftingInput input, Level level) {
    return plan(input) != null;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return Content.COUNTED_CRAFTING_SERIALIZER.get();
  }

  public static final class Serializer implements RecipeSerializer<CountedCraftingRecipe> {
    private static final MapCodec<CountedCraftingRecipe> CODEC =
        RecordCodecBuilder.mapCodec(
            i ->
                i.group(
                        FactoryRecipe.CountedIngredient.CODEC
                            .listOf(1, 9)
                            .fieldOf("inputs")
                            .forGetter(CountedCraftingRecipe::inputs),
                        ItemStack.STRICT_CODEC
                            .fieldOf("result")
                            .forGetter(
                                r -> r.getResultItem(net.minecraft.core.RegistryAccess.EMPTY)))
                    .apply(i, CountedCraftingRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, CountedCraftingRecipe> STREAM =
        new StreamCodec<>() {
          public CountedCraftingRecipe decode(RegistryFriendlyByteBuf b) {
            int size = b.readVarInt();
            if (size < 1 || size > 9)
              throw new IllegalArgumentException("Invalid handcraft inputs");
            var inputs = new ArrayList<FactoryRecipe.CountedIngredient>(size);
            for (int i = 0; i < size; i++)
              inputs.add(
                  new FactoryRecipe.CountedIngredient(
                      Ingredient.CONTENTS_STREAM_CODEC.decode(b), b.readVarInt()));
            return new CountedCraftingRecipe(inputs, ItemStack.STREAM_CODEC.decode(b));
          }

          public void encode(RegistryFriendlyByteBuf b, CountedCraftingRecipe r) {
            b.writeVarInt(r.inputs.size());
            for (var input : r.inputs) {
              Ingredient.CONTENTS_STREAM_CODEC.encode(b, input.ingredient());
              b.writeVarInt(input.count());
            }
            ItemStack.STREAM_CODEC.encode(
                b, r.getResultItem(net.minecraft.core.RegistryAccess.EMPTY));
          }
        };

    public MapCodec<CountedCraftingRecipe> codec() {
      return CODEC;
    }

    public StreamCodec<RegistryFriendlyByteBuf, CountedCraftingRecipe> streamCodec() {
      return STREAM;
    }
  }
}
