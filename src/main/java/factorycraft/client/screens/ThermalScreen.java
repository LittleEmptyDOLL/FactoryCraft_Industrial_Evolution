package factorycraft.client.screens;

import factorycraft.thermal.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ThermalScreen extends AbstractContainerScreen<ThermalMenu> {
    public ThermalScreen(ThermalMenu menu,Inventory inventory,Component title) { super(menu,inventory,title);imageWidth=196;imageHeight=204;inventoryLabelY=110;inventoryLabelX=17; }
    protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        int x=leftPos,y=topPos;g.fill(x,y,x+imageWidth,y+imageHeight,0xff17252f);g.fill(x+3,y+3,x+imageWidth-3,y+imageHeight-3,0xff2c404c);
        for(var slot:menu.slots)if(slot.isActive()) { g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff62747d);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff0a151c); }
        boolean boiler=menu.machine.kind()==ThermalKind.BOILER;
        int maximum=boiler ? Math.max(1,menu.value(10)):16000;
        int current=boiler ? menu.value(8):menu.value(4);
        int width=(int)(160L*Math.min(current,maximum)/maximum);
        g.fill(x+17,y+65,x+177,y+71,0xff0a151c);g.fill(x+17,y+65,x+17+width,y+71,boiler ? 0xffffab45:0xff5cd4e3);
    }
    protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,title,12,9,0xd8e9f0,false);
        boolean boiler=menu.machine.kind()==ThermalKind.BOILER;
        g.drawString(font,Component.translatable(boiler ? "gui.factorycraft.thermal_water":"gui.factorycraft.thermal_energy",boiler ? menu.value(0):menu.value(4)),17,26,0x8cd5f2,false);
        g.drawString(font,Component.translatable("gui.factorycraft.thermal_steam",menu.value(2)),17,39,0xd8e9f0,false);
        g.drawString(font,Component.translatable(boiler ? "gui.factorycraft.thermal_fuel":"gui.factorycraft.engine_rate",menu.value(6)),17,52,0xe6c184,false);
        g.drawString(font,Component.translatable(boiler ? "gui.factorycraft.boiler_ports":"gui.factorycraft.engine_ports"),42,80,0xd8e9f0,false);
        g.drawString(font,Component.translatable(boiler ? "gui.factorycraft.boiler_status_"+menu.data.get(12):"gui.factorycraft.engine_buffer"),42,93,0x98bfca,false);
        g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xd8e9f0,false);
    }
    public void render(GuiGraphics g,int mx,int my,float partial) { super.render(g,mx,my,partial);renderTooltip(g,mx,my); }
}
