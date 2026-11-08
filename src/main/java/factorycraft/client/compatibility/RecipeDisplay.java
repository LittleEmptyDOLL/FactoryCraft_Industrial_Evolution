package factorycraft.client.compatibility;

import factorycraft.registry.Content;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

/** Shared viewer projection: counted alternatives and full native fluid components, never recipe execution. */
public record RecipeDisplay(ResourceLocation id,String machine,List<List<ItemStack>> items,List<List<FluidStack>> fluids,List<ItemStack> results,List<FluidStack> outputs,int ticks,int energy,String note) {
    public ItemStack catalyst(){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft",machine)));}
    public static List<RecipeDisplay> all(){var level=Minecraft.getInstance().level;if(level==null)return List.of();var manager=level.getRecipeManager();var all=new ArrayList<RecipeDisplay>();
        for(var h:manager.getAllRecipesFor(Content.PROCESSING.get())){var r=h.value();all.add(new RecipeDisplay(h.id(),r.machine(),r.inputs().stream().map(i->Arrays.stream(i.ingredient().getItems()).map(s->s.copyWithCount(i.count())).toList()).toList(),List.of(),List.of(r.result().copy()),List.of(),r.ticks(),r.energy(),""));}
        for(var h:manager.getAllRecipesFor(Content.CHEMISTRY.get())){var r=h.value();all.add(new RecipeDisplay(h.id(),r.machine(),r.items().stream().map(i->Arrays.stream(i.ingredient().getItems()).map(s->s.copyWithCount(i.count())).toList()).toList(),r.fluids().stream().map(f->Arrays.stream(f.getFluids()).map(s->s.copyWithAmount(f.amount())).toList()).toList(),r.results(),r.fluidResults(),r.ticks(),r.energy(),""));}
        for(var h:manager.getAllRecipesFor(Content.BOILING.get())){var r=h.value();all.add(new RecipeDisplay(h.id(),"boiler",List.of(),List.of(Arrays.stream(r.input().getFluids()).toList()),List.of(),List.of(r.result()),r.ticks(),0,""));}
        for(var h:manager.getAllRecipesFor(Content.RESEARCH.get())){var r=h.value();var packs=r.packs().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->List.of(new ItemStack(BuiltInRegistries.ITEM.get(e.getKey()),e.getValue()))).toList();all.add(new RecipeDisplay(h.id(),"lab",packs,List.of(),List.of(),List.of(),r.ticks(),20,net.minecraft.network.chat.Component.translatable("technology."+h.id().getNamespace()+"."+h.id().getPath()).getString()+" × "+r.units()));}
        for(int i=0;i<all.size();i++){var display=all.get(i);var required=manager.getAllRecipesFor(Content.RESEARCH.get()).stream().filter(t->t.value().unlocks().contains(display.id())).map(t->net.minecraft.network.chat.Component.translatable("technology."+t.id().getNamespace()+"."+t.id().getPath()).getString()).toList();if(!required.isEmpty())all.set(i,new RecipeDisplay(display.id,display.machine,display.items,display.fluids,display.results,display.outputs,display.ticks,display.energy,net.minecraft.network.chat.Component.translatable("gui.factorycraft.needs_research").getString()+": "+String.join(" / ",required)));}
        return List.copyOf(all);
    }
}
