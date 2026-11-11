package factorycraft.recipes;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import factorycraft.registry.Content;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public record FactoryRecipe(
    String machine, List<CountedIngredient> inputs, ItemStack result, int ticks, int energy)
    implements Recipe<FactoryInput> {
  public FactoryRecipe {
    if (machine == null
        || machine.isBlank()
        || inputs.isEmpty()
        || inputs.size() > 16
        || inputs.stream().anyMatch(i -> i.count() < 1 || i.count() > 64)
        || result.isEmpty()
        || result.getCount() > result.getMaxStackSize() * 4
        || ticks < 1
        || ticks > 72000
        || energy < 0
        || energy > 100000) throw new IllegalArgumentException("Invalid factory recipe bounds");
    inputs = List.copyOf(inputs);
    result = result.copy();
  }

  @Override
  public ItemStack result() {
    return result.copy();
  }

  public record CountedIngredient(Ingredient ingredient, int count) {
    public static final Codec<CountedIngredient> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        Ingredient.CODEC_NONEMPTY
                            .fieldOf("ingredient")
                            .forGetter(CountedIngredient::ingredient),
                        Codec.intRange(1, 64).fieldOf("count").forGetter(CountedIngredient::count))
                    .apply(i, CountedIngredient::new));
  }

  public static final MapCodec<FactoryRecipe> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      Codec.STRING.fieldOf("machine").forGetter(FactoryRecipe::machine),
                      CountedIngredient.CODEC
                          .listOf(1, 16)
                          .fieldOf("inputs")
                          .forGetter(FactoryRecipe::inputs),
                      ItemStack.CODEC.fieldOf("result").forGetter(FactoryRecipe::result),
                      Codec.intRange(1, 72000).fieldOf("ticks").forGetter(FactoryRecipe::ticks),
                      Codec.intRange(0, 100000).fieldOf("energy").forGetter(FactoryRecipe::energy))
                  .apply(i, FactoryRecipe::new));

  /**
   * Reserve counts in a local plan so overlapping ingredient predicates never spend a slot twice.
   */
  public int[] consumptionPlan(FactoryInput input) {
    int[] used = new int[input.size()];
    for (var ingredient : inputs) {
      int needed = ingredient.count;
      for (int s = 0; s < used.length && needed > 0; s++) {
        var stack = input.getItem(s);
        if (!ingredient.ingredient.test(stack)) continue;
        int take = Math.min(needed, stack.getCount() - used[s]);
        used[s] += take;
        needed -= take;
      }
      if (needed > 0) return null;
    }
    return used;
  }

  @Override
  public boolean matches(FactoryInput input, Level level) {
    return consumptionPlan(input) != null;
  }

  @Override
  public ItemStack assemble(FactoryInput input, HolderLookup.Provider r) {
    return result.copy();
  }

  @Override
  public boolean canCraftInDimensions(int w, int h) {
    return true;
  }

  @Override
  public ItemStack getResultItem(HolderLookup.Provider r) {
    return result.copy();
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return Content.PROCESSING_SERIALIZER.get();
  }

  @Override
  public RecipeType<?> getType() {
    return Content.PROCESSING.get();
  }

  @Override
  public boolean isSpecial() {
    return true;
  }

  public static final class Serializer implements RecipeSerializer<FactoryRecipe> {
    private static final StreamCodec<RegistryFriendlyByteBuf, FactoryRecipe> STREAM =
        new StreamCodec<>() {
          @Override
          public FactoryRecipe decode(RegistryFriendlyByteBuf b) {
            String machine = b.readUtf(128);
            int size = b.readVarInt();
            if (size < 1 || size > 16)
              throw new IllegalArgumentException("Invalid ingredient count");
            var inputs = new java.util.ArrayList<CountedIngredient>(size);
            for (int i = 0; i < size; i++)
              inputs.add(
                  new CountedIngredient(
                      Ingredient.CONTENTS_STREAM_CODEC.decode(b), b.readVarInt()));
            return new FactoryRecipe(
                machine, inputs, ItemStack.STREAM_CODEC.decode(b), b.readVarInt(), b.readVarInt());
          }

          @Override
          public void encode(RegistryFriendlyByteBuf b, FactoryRecipe recipe) {
            b.writeUtf(recipe.machine);
            b.writeVarInt(recipe.inputs.size());
            for (var input : recipe.inputs) {
              Ingredient.CONTENTS_STREAM_CODEC.encode(b, input.ingredient);
              b.writeVarInt(input.count);
            }
            ItemStack.STREAM_CODEC.encode(b, recipe.result);
            b.writeVarInt(recipe.ticks);
            b.writeVarInt(recipe.energy);
          }
        };

    @Override
    public MapCodec<FactoryRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, FactoryRecipe> streamCodec() {
      return STREAM;
    }
  }
}
