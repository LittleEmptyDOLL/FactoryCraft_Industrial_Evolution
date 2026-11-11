package factorycraft.recipes;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import factorycraft.registry.Content;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/** Independent fluid recipe type; existing item-processing JSON and wire format are unchanged. */
public record BoilingRecipe(SizedFluidIngredient input, FluidStack result, int ticks)
    implements Recipe<BoilingRecipe.Input> {
  public BoilingRecipe {
    if (input.amount() > 40000
        || result.isEmpty()
        || result.getAmount() > 40000
        || ticks < 1
        || ticks > 72000)
      throw new IllegalArgumentException("Boiling recipe exceeds storage/time limits");
    result = result.copy();
  }

  @Override
  public FluidStack result() {
    return result.copy();
  }

  public record Input(FluidStack fluid) implements RecipeInput {
    public ItemStack getItem(int slot) {
      throw new IndexOutOfBoundsException(slot);
    }

    public int size() {
      return 0;
    }
  }

  public static final MapCodec<BoilingRecipe> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      SizedFluidIngredient.FLAT_CODEC
                          .validate(
                              v ->
                                  v.amount() <= 40000
                                      ? DataResult.success(v)
                                      : DataResult.error(() -> "Boiling input exceeds 40000 mB"))
                          .fieldOf("input")
                          .forGetter(BoilingRecipe::input),
                      FluidStack.CODEC
                          .validate(
                              v ->
                                  v.getAmount() <= 40000
                                      ? DataResult.success(v)
                                      : DataResult.error(() -> "Boiling output exceeds 40000 mB"))
                          .fieldOf("result")
                          .forGetter(BoilingRecipe::result),
                      Codec.intRange(1, 72000).fieldOf("ticks").forGetter(BoilingRecipe::ticks))
                  .apply(i, BoilingRecipe::new));

  @Override
  public boolean matches(Input input, Level level) {
    return this.input.test(input.fluid);
  }

  @Override
  public ItemStack assemble(Input input, HolderLookup.Provider r) {
    return ItemStack.EMPTY;
  }

  @Override
  public ItemStack getResultItem(HolderLookup.Provider r) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean canCraftInDimensions(int w, int h) {
    return false;
  }

  @Override
  public boolean isSpecial() {
    return true;
  }

  @Override
  public RecipeType<?> getType() {
    return Content.BOILING.get();
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return Content.BOILING_SERIALIZER.get();
  }

  public static final class Serializer implements RecipeSerializer<BoilingRecipe> {
    private static final StreamCodec<RegistryFriendlyByteBuf, BoilingRecipe> STREAM =
        new StreamCodec<>() {
          public BoilingRecipe decode(RegistryFriendlyByteBuf b) {
            return new BoilingRecipe(
                SizedFluidIngredient.STREAM_CODEC.decode(b),
                FluidStack.STREAM_CODEC.decode(b),
                b.readVarInt());
          }

          public void encode(RegistryFriendlyByteBuf b, BoilingRecipe r) {
            SizedFluidIngredient.STREAM_CODEC.encode(b, r.input);
            FluidStack.STREAM_CODEC.encode(b, r.result);
            b.writeVarInt(r.ticks);
          }
        };

    public MapCodec<BoilingRecipe> codec() {
      return CODEC;
    }

    public StreamCodec<RegistryFriendlyByteBuf, BoilingRecipe> streamCodec() {
      return STREAM;
    }
  }
}
