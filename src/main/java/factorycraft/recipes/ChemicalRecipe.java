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
import java.util.*;

/** Fixed fluid ports avoid double-reserving a cell; items reserve all matching slot counts together. */
public record ChemicalRecipe(String machine,List<FactoryRecipe.CountedIngredient> items,List<SizedFluidIngredient> fluids,
                             List<ItemStack> results,List<FluidStack> fluidResults,int ticks,int energy) implements Recipe<ChemicalInput> {
    public ChemicalRecipe {
        if(!List.of("oil_refinery","chemical_plant").contains(machine) || items.size()>4 || fluids.size()>2 || results.size()>4 || fluidResults.size()>2 ||
                items.isEmpty() && fluids.isEmpty() || results.isEmpty() && fluidResults.isEmpty() || ticks<1 || ticks>72000 || energy<1 || energy>16000 ||
                items.stream().anyMatch(i->i.count()<1 || i.count()>64) || fluids.stream().anyMatch(f->f.amount()>4000) ||
                results.stream().anyMatch(s->s.isEmpty() || s.getCount()>s.getMaxStackSize()) || fluidResults.stream().anyMatch(f->f.isEmpty() || f.getAmount()>4000))throw new IllegalArgumentException("Invalid chemical recipe bounds");
        items=List.copyOf(items);fluids=List.copyOf(fluids);results=results.stream().map(ItemStack::copy).toList();fluidResults=fluidResults.stream().map(FluidStack::copy).toList();
    }
    @Override public List<ItemStack> results() { return results.stream().map(ItemStack::copy).toList(); }
    @Override public List<FluidStack> fluidResults() { return fluidResults.stream().map(FluidStack::copy).toList(); }
    private static final Codec<SizedFluidIngredient> FLUID= SizedFluidIngredient.FLAT_CODEC.validate(f->f.amount()<=4000 ? DataResult.success(f):DataResult.error(()->"Fluid input exceeds 4000 mB"));
    private static final Codec<FluidStack> OUTPUT=FluidStack.CODEC.validate(f->f.getAmount()<=4000 ? DataResult.success(f):DataResult.error(()->"Fluid result exceeds 4000 mB"));
    public static final MapCodec<ChemicalRecipe> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            Codec.STRING.fieldOf("machine").forGetter(ChemicalRecipe::machine),
            FactoryRecipe.CountedIngredient.CODEC.listOf(0,4).optionalFieldOf("items",List.of()).forGetter(ChemicalRecipe::items),
            FLUID.listOf(0,2).optionalFieldOf("fluids",List.of()).forGetter(ChemicalRecipe::fluids),
            ItemStack.CODEC.listOf(0,4).optionalFieldOf("results",List.of()).forGetter(ChemicalRecipe::results),
            OUTPUT.listOf(0,2).optionalFieldOf("fluid_results",List.of()).forGetter(ChemicalRecipe::fluidResults),
            Codec.intRange(1,72000).fieldOf("ticks").forGetter(ChemicalRecipe::ticks),
            Codec.intRange(1,16000).fieldOf("energy").forGetter(ChemicalRecipe::energy)).apply(i,ChemicalRecipe::new));
    @Override public boolean matches(ChemicalInput input,Level level) {
        if(input.fluids().size()<fluids.size())return false;
        for(int i=0;i<fluids.size();i++)if(!fluids.get(i).test(input.fluids().get(i)))return false;
        int[] used=new int[4];
        for(var ingredient:items) {
            int needed=ingredient.count();for(int s=0;s<4 && needed>0;s++)if(ingredient.ingredient().test(input.getItem(s))) {
                int take=Math.min(needed,input.getItem(s).getCount()-used[s]);used[s]+=take;needed-=take;
            }
            if(needed>0)return false;
        }
        return true;
    }
    @Override public ItemStack assemble(ChemicalInput input,HolderLookup.Provider r) { return getResultItem(r); }
    @Override public ItemStack getResultItem(HolderLookup.Provider r) { return results.isEmpty() ? ItemStack.EMPTY:results.getFirst().copy(); }
    @Override public boolean canCraftInDimensions(int w,int h) { return false; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeType<?> getType() { return Content.CHEMISTRY.get(); }
    @Override public RecipeSerializer<?> getSerializer() { return Content.CHEMISTRY_SERIALIZER.get(); }
    public static final class Serializer implements RecipeSerializer<ChemicalRecipe> {
        private static int size(RegistryFriendlyByteBuf b,int max) { int n=b.readVarInt();if(n<0 || n>max)throw new IllegalArgumentException("Invalid chemical list size");return n; }
        private static final StreamCodec<RegistryFriendlyByteBuf,ChemicalRecipe> STREAM=new StreamCodec<>() {
            public ChemicalRecipe decode(RegistryFriendlyByteBuf b) {
                var machine=b.readUtf(64);var items=new ArrayList<FactoryRecipe.CountedIngredient>();var fluids=new ArrayList<SizedFluidIngredient>();var results=new ArrayList<ItemStack>();var outputs=new ArrayList<FluidStack>();
                for(int n=size(b,4);n>0;n--)items.add(new FactoryRecipe.CountedIngredient(Ingredient.CONTENTS_STREAM_CODEC.decode(b),b.readVarInt()));
                for(int n=size(b,2);n>0;n--)fluids.add(SizedFluidIngredient.STREAM_CODEC.decode(b));
                for(int n=size(b,4);n>0;n--)results.add(ItemStack.STREAM_CODEC.decode(b));
                for(int n=size(b,2);n>0;n--)outputs.add(FluidStack.STREAM_CODEC.decode(b));
                return new ChemicalRecipe(machine,items,fluids,results,outputs,b.readVarInt(),b.readVarInt());
            }
            public void encode(RegistryFriendlyByteBuf b,ChemicalRecipe r) {
                b.writeUtf(r.machine,64);b.writeVarInt(r.items.size());for(var item:r.items) { Ingredient.CONTENTS_STREAM_CODEC.encode(b,item.ingredient());b.writeVarInt(item.count()); }
                b.writeVarInt(r.fluids.size());for(var f:r.fluids)SizedFluidIngredient.STREAM_CODEC.encode(b,f);
                b.writeVarInt(r.results.size());for(var item:r.results)ItemStack.STREAM_CODEC.encode(b,item);
                b.writeVarInt(r.fluidResults.size());for(var f:r.fluidResults)FluidStack.STREAM_CODEC.encode(b,f);
                b.writeVarInt(r.ticks);b.writeVarInt(r.energy);
            }
        };
        public MapCodec<ChemicalRecipe> codec() { return CODEC; }
        public StreamCodec<RegistryFriendlyByteBuf,ChemicalRecipe> streamCodec() { return STREAM; }
    }
}
