package factorycraft.client.screens;

import factorycraft.blocks.FactoryKind;
import factorycraft.production.FactoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class FactoryScreen extends AbstractContainerScreen<FactoryMenu> {
    public FactoryScreen(FactoryMenu menu,Inventory inv,Component title) { super(menu,inv,title);imageWidth=206;imageHeight=248;inventoryLabelY=154; }
    @Override protected void init() {
        super.init();
        if(menu.machine.kind().processing())
            addRenderableWidget(Button.builder(Component.translatable("gui.factorycraft.select"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+96,topPos+80,94,20).build());
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,0xff202933);g.fill(x+3,y+3,x+imageWidth-3,y+imageHeight-3,0xff34414b);
        for(var slot:menu.slots) { g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff62747d); g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff111a20); }
        int progress=menu.data.get(0), duration=Math.max(1,menu.data.get(1));
        g.fill(x+18,y+69,x+184,y+74,0xff14202a);g.fill(x+18,y+69,x+18+Math.min(166,166*progress/duration),y+74,0xffffae34);
        g.fill(x+190,y+30,x+197,y+100,0xff14202a);g.fill(x+190,y+100-70*menu.data.get(2)/16000,x+197,y+100,0xff40c9d0);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,title,8,8,0xffe9f2f5,false);
        g.drawString(font,Component.translatable("gui.factorycraft.input"),18,34,0xffc1d0d5,false);
        g.drawString(font,Component.translatable("gui.factorycraft.output"),112,34,0xffc1d0d5,false);
        g.drawString(font,menu.data.get(2)+" FE",18,22,0xff56d7d9,false);
        if(menu.machine.kind()==FactoryKind.INDUSTRIAL_FURNACE)
            g.drawString(font,font.plainSubstrByWidth(Component.translatable("gui.factorycraft.assembly."+menu.data.get(6)).getString(),90),100,22,menu.data.get(6)==2 ? 0xff71df9a:0xffffba55,false);
        var rs=menu.machine.recipes();int index=menu.data.get(4);
        Component label=index>0 && index<=rs.size() ? rs.get(index-1).value().result().getHoverName() : Component.translatable("gui.factorycraft.auto");
        g.drawString(font,font.plainSubstrByWidth(label.getString(),115),58,103,0xffeeeeee,false);
        if(!rs.isEmpty()) {
            int preview=index>0 && index<=rs.size() ? index-1 : 0;
            int current=menu.data.get(5);
            if(index==0 && current>0 && current<=rs.size())preview=current-1;
            var recipe=rs.get(preview).value();
            for(int i=0;i<recipe.inputs().size();i++) {
                var ingredient=recipe.inputs().get(i);var examples=ingredient.ingredient().getItems();
                if(examples.length==0)continue;
                var stack=examples[(int)(System.currentTimeMillis()/1000%examples.length)].copyWithCount(ingredient.count());
                g.renderFakeItem(stack,18+i*30,116);g.renderItemDecorations(font,stack,18+i*30,116);
            }
            g.drawString(font,"->",143,120,0xffc1d0d5,false);g.renderFakeItem(recipe.result(),163,116);g.renderItemDecorations(font,recipe.result(),163,116);
            g.drawString(font,Component.translatable("gui.factorycraft.recipe_info",menu.machine.kind()==FactoryKind.INDUSTRIAL_FURNACE ? Math.ceilDiv(recipe.ticks(),2):recipe.ticks(),recipe.energy()*(menu.machine.kind()==FactoryKind.INDUSTRIAL_FURNACE ? 2:1)),18,138,0xffc1d0d5,false);
        }
        if(menu.machine.kind()==FactoryKind.GENERATOR)g.drawString(font,Component.translatable("gui.factorycraft.burn",menu.data.get(3)),42,85,0xfff5b648,false);
        g.drawString(font,playerInventoryTitle,22,154,0xffc1d0d5,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) { super.render(g,mx,my,partial);renderTooltip(g,mx,my); }
}
