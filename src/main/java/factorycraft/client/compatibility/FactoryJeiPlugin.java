package factorycraft.client.compatibility;

import mezz.jei.api.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

@JeiPlugin
public final class FactoryJeiPlugin implements IModPlugin {
    private final Map<String,Category> categories=new LinkedHashMap<>();
    public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("factorycraft","recipes");}
    public void registerCategories(IRecipeCategoryRegistration registration){var helper=registration.getJeiHelpers().getGuiHelper();for(var machine:List.of("electric_furnace","assembling_machine","oil_refinery","chemical_plant","boiler","lab")){var c=new Category(machine,helper);categories.put(machine,c);registration.addRecipeCategories(c);}}
    public void registerRecipes(IRecipeRegistration registration){var all=RecipeDisplay.all();for(var c:categories.values())registration.addRecipes(c.type,all.stream().filter(r->r.machine().equals(c.machine)).toList());}
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration){for(var c:categories.values())registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft",c.machine))),c.type);}
    private static final class Category implements IRecipeCategory<RecipeDisplay> {
        final String machine;final RecipeType<RecipeDisplay> type;final IDrawable icon;
        Category(String machine,IGuiHelper helper){this.machine=machine;type=new RecipeType<>(ResourceLocation.fromNamespaceAndPath("factorycraft",machine),RecipeDisplay.class);icon=helper.createDrawableItemStack(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft",machine))));}
        public RecipeType<RecipeDisplay> getRecipeType(){return type;}public Component getTitle(){return Component.translatable("block.factorycraft."+machine);}public IDrawable getIcon(){return icon;}public int getWidth(){return 168;}public int getHeight(){return 88;}
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeDisplay recipe, IFocusGroup focuses) {
            int input = 0;
            for (var item : recipe.items()) {
                builder.addSlot(RecipeIngredientRole.INPUT, 2 + (input % 4) * 20, 2 + (input / 4) * 20)
                        .setStandardSlotBackground().addItemStacks(item);
                input++;
            }
            for (var alternatives : recipe.fluids()) {
                long capacity = alternatives.stream().mapToLong(f -> f.getAmount()).max().orElse(1);
                var slot = builder.addSlot(RecipeIngredientRole.INPUT,
                        2 + (input % 4) * 20, 2 + (input / 4) * 20)
                        .setStandardSlotBackground().setFluidRenderer(capacity, false, 16, 16);
                for (var fluid : alternatives) {
                    slot.addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
                }
                input++;
            }
            int output = 0;
            for (var item : recipe.results()) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 92 + (output % 3) * 20, 2 + (output / 3) * 20)
                        .setStandardSlotBackground().addItemStack(item);
                output++;
            }
            for (var fluid : recipe.outputs()) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 92 + (output % 3) * 20, 2 + (output / 3) * 20)
                        .setStandardSlotBackground().setFluidRenderer(fluid.getAmount(), false, 16, 16)
                        .addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
                output++;
            }
        }
        public void draw(RecipeDisplay recipe,IRecipeSlotsView slots,GuiGraphics g,double mx,double my){g.drawString(Minecraft.getInstance().font,recipe.ticks()+" t / "+recipe.energy()+" FE/t",2,60,0xffffff,false);if(!recipe.note().isEmpty())g.drawString(Minecraft.getInstance().font,recipe.note(),2,74,0xffffff,false);}
    }
}
