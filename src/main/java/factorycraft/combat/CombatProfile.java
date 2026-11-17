package factorycraft.combat;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import factorycraft.registry.Content;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Native datapack definition. Damage/range/time remain in original Factorio units. */
public record CombatProfile(
    String category,
    String ammunition,
    int cooldown,
    double range,
    double damage,
    int pellets,
    double spread,
    int energy)
    implements Recipe<RecipeInput> {
  public CombatProfile {
    if (!java.util.Set.of("bullet", "shotgun-shell", "rocket", "fire", "explosion", "laser")
            .contains(category)
        || cooldown < 1
        || cooldown > 3600
        || range <= 0
        || range > 128
        || !Double.isFinite(range)
        || damage <= 0
        || damage > 10000
        || !Double.isFinite(damage)
        || pellets < 1
        || pellets > 32
        || spread < 0
        || spread > 1
        || !Double.isFinite(spread)
        || energy < 0
        || energy > 100000000) throw new IllegalArgumentException("Invalid combat profile");
  }

  public static final MapCodec<CombatProfile> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      Codec.STRING.fieldOf("category").forGetter(CombatProfile::category),
                      Codec.STRING.fieldOf("ammunition").forGetter(CombatProfile::ammunition),
                      Codec.intRange(1, 3600)
                          .fieldOf("cooldown")
                          .forGetter(CombatProfile::cooldown),
                      Codec.doubleRange(0.001, 128)
                          .fieldOf("range")
                          .forGetter(CombatProfile::range),
                      Codec.doubleRange(0.001, 10000)
                          .fieldOf("damage")
                          .forGetter(CombatProfile::damage),
                      Codec.intRange(1, 32)
                          .optionalFieldOf("pellets", 1)
                          .forGetter(CombatProfile::pellets),
                      Codec.doubleRange(0, 1)
                          .optionalFieldOf("spread", 0.0)
                          .forGetter(CombatProfile::spread),
                      Codec.intRange(0, 100000000)
                          .optionalFieldOf("energy", 0)
                          .forGetter(CombatProfile::energy))
                  .apply(i, CombatProfile::new));

  public static CombatProfile get(Level level, String id) {
    if (level == null) return null;
    return level
        .getRecipeManager()
        .byKey(ResourceLocation.fromNamespaceAndPath("factorycraft", "combat/" + id))
        .map(RecipeHolder::value)
        .filter(CombatProfile.class::isInstance)
        .map(CombatProfile.class::cast)
        .orElse(null);
  }

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
    return ItemStack.EMPTY;
  }

  public boolean isSpecial() {
    return true;
  }

  public RecipeType<?> getType() {
    return Content.COMBAT_TYPE.get();
  }

  public RecipeSerializer<?> getSerializer() {
    return Content.COMBAT_SERIALIZER.get();
  }

  public static final class Serializer implements RecipeSerializer<CombatProfile> {
    public MapCodec<CombatProfile> codec() {
      return CODEC;
    }

    public StreamCodec<RegistryFriendlyByteBuf, CombatProfile> streamCodec() {
      return new StreamCodec<>() {
        public CombatProfile decode(RegistryFriendlyByteBuf b) {
          return new CombatProfile(
              b.readUtf(32),
              b.readUtf(32),
              b.readVarInt(),
              b.readDouble(),
              b.readDouble(),
              b.readVarInt(),
              b.readDouble(),
              b.readVarInt());
        }

        public void encode(RegistryFriendlyByteBuf b, CombatProfile p) {
          b.writeUtf(p.category);
          b.writeUtf(p.ammunition);
          b.writeVarInt(p.cooldown);
          b.writeDouble(p.range);
          b.writeDouble(p.damage);
          b.writeVarInt(p.pellets);
          b.writeDouble(p.spread);
          b.writeVarInt(p.energy);
        }
      };
    }
  }
}
