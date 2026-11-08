package factorycraft.research;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import factorycraft.recipes.FactoryInput;
import factorycraft.registry.Content;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** Native recipe sync also distributes datapack technology definitions to optional viewers/our UI. */
public record TechnologyRecipe(List<ResourceLocation> prerequisites,Map<ResourceLocation,Integer> packs,int units,int ticks,boolean repeatable,List<ResourceLocation> unlocks,int x,int y) implements Recipe<FactoryInput> {
    public TechnologyRecipe {
        if(prerequisites.size()>16 || packs.isEmpty() || packs.size()>4 || packs.values().stream().anyMatch(n->n<1 || n>64) || units<1 || units>100000 || ticks<1 || ticks>72000 || unlocks.size()>64 || x<0 || x>32 || y<0 || y>32)throw new IllegalArgumentException("Invalid technology bounds");
        prerequisites=List.copyOf(prerequisites);packs=Map.copyOf(packs);unlocks=List.copyOf(unlocks);
    }
    public static final MapCodec<TechnologyRecipe> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        ResourceLocation.CODEC.listOf(0,16).optionalFieldOf("prerequisites",List.of()).forGetter(TechnologyRecipe::prerequisites),
        Codec.unboundedMap(ResourceLocation.CODEC,Codec.intRange(1,64)).fieldOf("packs").forGetter(TechnologyRecipe::packs),
        Codec.intRange(1,100000).fieldOf("units").forGetter(TechnologyRecipe::units),Codec.intRange(1,72000).fieldOf("ticks").forGetter(TechnologyRecipe::ticks),
        Codec.BOOL.optionalFieldOf("repeatable",false).forGetter(TechnologyRecipe::repeatable),ResourceLocation.CODEC.listOf(0,64).optionalFieldOf("unlocks",List.of()).forGetter(TechnologyRecipe::unlocks),
        Codec.intRange(0,32).fieldOf("x").forGetter(TechnologyRecipe::x),Codec.intRange(0,32).fieldOf("y").forGetter(TechnologyRecipe::y)).apply(i,TechnologyRecipe::new));
    public boolean matches(FactoryInput input,Level level) { return false; }
    public ItemStack assemble(FactoryInput input,HolderLookup.Provider r) { return ItemStack.EMPTY; }
    public ItemStack getResultItem(HolderLookup.Provider r) { return ItemStack.EMPTY; }
    public boolean canCraftInDimensions(int w,int h) { return false; }
    public boolean isSpecial() { return true; }
    public RecipeType<?> getType() { return Content.RESEARCH.get(); }
    public RecipeSerializer<?> getSerializer() { return Content.RESEARCH_SERIALIZER.get(); }
    public static final class Serializer implements RecipeSerializer<TechnologyRecipe> {
        private static int count(RegistryFriendlyByteBuf b,int max) { int n=b.readVarInt();if(n<0 || n>max)throw new IllegalArgumentException("Invalid technology list");return n; }
        private static final StreamCodec<RegistryFriendlyByteBuf,TechnologyRecipe> STREAM=new StreamCodec<>() {
            public TechnologyRecipe decode(RegistryFriendlyByteBuf b) {
                var parents=new ArrayList<ResourceLocation>();for(int n=count(b,16);n>0;n--)parents.add(b.readResourceLocation());
                var packs=new HashMap<ResourceLocation,Integer>();for(int n=count(b,4);n>0;n--){var id=b.readResourceLocation();if(packs.put(id,b.readVarInt())!=null)throw new IllegalArgumentException("Duplicate science pack");}
                int units=b.readVarInt(),ticks=b.readVarInt();boolean repeat=b.readBoolean();var unlocks=new ArrayList<ResourceLocation>();for(int n=count(b,64);n>0;n--)unlocks.add(b.readResourceLocation());
                return new TechnologyRecipe(parents,packs,units,ticks,repeat,unlocks,b.readVarInt(),b.readVarInt());
            }
            public void encode(RegistryFriendlyByteBuf b,TechnologyRecipe t) {
                b.writeVarInt(t.prerequisites.size());for(var id:t.prerequisites)b.writeResourceLocation(id);b.writeVarInt(t.packs.size());for(var e:t.packs.entrySet()){b.writeResourceLocation(e.getKey());b.writeVarInt(e.getValue());}
                b.writeVarInt(t.units);b.writeVarInt(t.ticks);b.writeBoolean(t.repeatable);b.writeVarInt(t.unlocks.size());for(var id:t.unlocks)b.writeResourceLocation(id);b.writeVarInt(t.x);b.writeVarInt(t.y);
            }
        };
        public MapCodec<TechnologyRecipe> codec() { return CODEC; }
        public StreamCodec<RegistryFriendlyByteBuf,TechnologyRecipe> streamCodec() { return STREAM; }
    }
}
