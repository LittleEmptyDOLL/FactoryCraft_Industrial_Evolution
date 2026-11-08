package factorycraft.client.screens;

import factorycraft.blocks.FactoryKind;
import factorycraft.petroleum.PetroleumMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PetroleumScreen extends AbstractContainerScreen<PetroleumMenu> {
    public PetroleumScreen(PetroleumMenu menu,Inventory inv,Component title) { super(menu,inv,title);imageWidth=240;imageHeight=240;inventoryLabelX=39;inventoryLabelY=149; }
    protected void init() {
        super.init();
        if(menu.machine.kind().processing())addRenderableWidget(Button.builder(Component.literal("M"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,99)).bounds(leftPos+91,topPos+110,26,18).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.factorycraft.modules"))).build());
        if(menu.machine.kind()!=FactoryKind.PUMPJACK)for(int tank=0;tank<2;tank++) {
            final int buttonId=tank+1;
            addRenderableWidget(Button.builder(Component.literal("↑"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,buttonId))
                    .bounds(leftPos+223,topPos+20+tank*11,14,11)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.factorycraft.recover_fluid"))).build());
        }
        if(menu.machine.kind()!=FactoryKind.PUMPJACK)addRenderableWidget(Button.builder(Component.translatable("gui.factorycraft.select"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+18,topPos+110,70,18).build());
    }
    protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        int x=leftPos,y=topPos;g.fill(x,y,x+imageWidth,y+imageHeight,0xff202933);g.fill(x+3,y+3,x+imageWidth-3,y+imageHeight-3,0xff34414b);
        for(var slot:menu.slots) { g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff62747d);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff111a20); }
        int w=(int)(204L*Math.min(menu.progress(),menu.duration())/Math.max(1,menu.duration()));g.fill(x+18,y+69,x+222,y+74,0xff14202a);g.fill(x+18,y+69,x+18+w,y+74,0xffffae34);
    }
    private void line(GuiGraphics g,Component text,int x,int y,int width,int color) { String value=text.getString();if(font.width(value)>width)value=font.plainSubstrByWidth(value,width-font.width("…"))+"…";g.drawString(font,value,x,y,color,false); }
    protected void renderLabels(GuiGraphics g,int mx,int my) {
        line(g,title,10,8,151,0xe9f2f5);g.drawString(font,menu.data.get(4)+" FE",166,8,0x56d7d9,false);
        for(int i=0;i<menu.machine.petroleum().cells.size();i++) {
            var fluid=menu.machine.petroleum().stored(i);var name=fluid.isEmpty() ? Component.translatable("gui.factorycraft.fluid_empty"):fluid.getHoverName();
            line(g,Component.translatable("gui.factorycraft.chemical_fluid_"+i,name,fluid.getAmount()),18,22+i*9,204,i<2 ? 0xa2dce9:0xe8cb8c);
        }
        g.drawString(font,Component.translatable("gui.factorycraft.input"),18,78,0xd8e9f0,false);g.drawString(font,Component.translatable("gui.factorycraft.output"),140,78,0xd8e9f0,false);
        line(g,Component.translatable("gui.factorycraft.petroleum_status_"+menu.data.get(5)),125,115,104,0xd8e9f0);
        var recipes=menu.machine.petroleum().recipes();int index=menu.data.get(6)>0 ? menu.data.get(6):menu.data.get(8);
        if(index>0 && index<=recipes.size()) {
            var recipe=recipes.get(index-1).value();var name=recipeName(recipes.get(index-1));line(g,Component.translatable("gui.factorycraft.chemical_recipe_info",name,recipe.ticks(),recipe.energy()),18,137,194,0xe8cb8c);g.drawString(font,"?",218,137,0xa2dce9,false);
        } else line(g,Component.translatable(menu.machine.kind()==FactoryKind.PUMPJACK ? "gui.factorycraft.pumpjack_rate":"gui.factorycraft.auto"),18,137,204,0xe8cb8c);
        if(menu.machine.kind().large())line(g,Component.translatable("gui.factorycraft.assembly."+menu.data.get(7)),125,126,104,0x71df9a);
        g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xd8e9f0,false);
    }
    private Component recipeName(net.minecraft.world.item.crafting.RecipeHolder<factorycraft.recipes.ChemicalRecipe> holder) {
        var recipe=holder.value();var fallback=!recipe.results().isEmpty() ? recipe.results().getFirst().getHoverName():recipe.fluidResults().getFirst().getHoverName();
        return Component.translatableWithFallback("recipe."+holder.id().getNamespace()+"."+holder.id().getPath(),fallback.getString());
    }
    public void render(GuiGraphics g,int mx,int my,float partial) {
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        int row=(my-topPos-22)/9;
        if(mx>=leftPos+18 && mx<=leftPos+222 && my>=topPos+22 && row<menu.machine.petroleum().cells.size()) {
            var fluid=menu.machine.petroleum().stored(row);var text=fluid.isEmpty() ? Component.translatable("gui.factorycraft.fluid_empty"):fluid.getHoverName();
            g.renderComponentTooltip(font,java.util.List.of(Component.translatable("gui.factorycraft.chemical_fluid_"+row,text,fluid.getAmount()),Component.translatable("gui.factorycraft.chemical_port_"+row)),mx,my);
        }
        if(mx<leftPos+18 || mx>leftPos+228 || my<topPos+134 || my>topPos+149)return;
        var recipes=menu.machine.petroleum().recipes();int index=menu.data.get(6)>0 ? menu.data.get(6):menu.data.get(8);if(index<1 || index>recipes.size())return;
        var recipe=recipes.get(index-1).value();var lines=new java.util.ArrayList<Component>();lines.add(recipeName(recipes.get(index-1)));lines.add(Component.translatable("gui.factorycraft.input"));
        for(var item:recipe.items()) { var examples=item.ingredient().getItems();if(examples.length>0)lines.add(Component.literal(item.count()+" × ").append(examples[0].getHoverName())); }
        for(var fluid:recipe.fluids()) { var examples=fluid.getFluids();if(examples.length>0)lines.add(Component.literal(fluid.amount()+" mB ").append(examples[0].getHoverName())); }
        lines.add(Component.translatable("gui.factorycraft.output"));
        for(var item:recipe.results())lines.add(Component.literal(item.getCount()+" × ").append(item.getHoverName()));
        for(var fluid:recipe.fluidResults())lines.add(Component.literal(fluid.getAmount()+" mB ").append(fluid.getHoverName()));
        lines.add(Component.translatable("gui.factorycraft.recipe_info",recipe.ticks(),recipe.energy()));g.renderComponentTooltip(font,lines,mx,my);
    }
}
