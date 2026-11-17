package factorycraft.nuclear;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import factorycraft.registry.Content;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Exact counted recipe and rational FE rate; random outcome is owned by paid machine work. */
public record UraniumRecipe(int ore, int ticks, int energyNumerator, int energyDenominator)
    implements Recipe<RecipeInput> {
  public UraniumRecipe {
    if (ore < 1
        || ore > 64
        || ticks < 1
        || ticks > 72000
        || energyNumerator < 1
        || energyNumerator > 100000
        || energyDenominator < 1
        || energyDenominator > 1000) throw new IllegalArgumentException("Uranium recipe bounds");
  }

  public static final MapCodec<UraniumRecipe> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      com.mojang.serialization.Codec.intRange(1, 64)
                          .fieldOf("ore")
                          .forGetter(UraniumRecipe::ore),
                      com.mojang.serialization.Codec.intRange(1, 72000)
                          .fieldOf("ticks")
                          .forGetter(UraniumRecipe::ticks),
                      com.mojang.serialization.Codec.intRange(1, 100000)
                          .fieldOf("energy_numerator")
                          .forGetter(UraniumRecipe::energyNumerator),
                      com.mojang.serialization.Codec.intRange(1, 1000)
                          .fieldOf("energy_denominator")
                          .forGetter(UraniumRecipe::energyDenominator))
                  .apply(i, UraniumRecipe::new));

  public boolean matches(RecipeInput i, Level l) {
    return false;
  }

  public ItemStack assemble(RecipeInput i, HolderLookup.Provider r) {
    return ItemStack.EMPTY;
  }

  public boolean canCraftInDimensions(int w, int h) {
    return false;
  }

  public ItemStack getResultItem(HolderLookup.Provider r) {
    return NuclearItems.stack("uranium_238", 1);
  }

  public RecipeSerializer<?> getSerializer() {
    return Content.URANIUM_SERIALIZER.get();
  }

  public RecipeType<?> getType() {
    return Content.URANIUM_PROCESSING.get();
  }

  public static final class Serializer implements RecipeSerializer<UraniumRecipe> {
    public MapCodec<UraniumRecipe> codec() {
      return CODEC;
    }

    public StreamCodec<RegistryFriendlyByteBuf, UraniumRecipe> streamCodec() {
      return new StreamCodec<>() {
        public UraniumRecipe decode(RegistryFriendlyByteBuf b) {
          return new UraniumRecipe(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        public void encode(RegistryFriendlyByteBuf b, UraniumRecipe r) {
          b.writeVarInt(r.ore);
          b.writeVarInt(r.ticks);
          b.writeVarInt(r.energyNumerator);
          b.writeVarInt(r.energyDenominator);
        }
      };
    }
  }
}
