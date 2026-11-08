package factorycraft.client.screens;

import factorycraft.fluids.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidScreen extends AbstractContainerScreen<FluidMenu> {
    public FluidScreen(FluidMenu menu,Inventory inventory,Component title) { super(menu,inventory,title);imageWidth=220;imageHeight=154; }
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY) {
        int x=leftPos,y=topPos;graphics.fill(x,y,x+imageWidth,y+imageHeight,0xff17252f);graphics.fill(x+3,y+3,x+imageWidth-3,y+imageHeight-3,0xff2c404c);
        graphics.fill(x+16,y+31,x+48,y+124,0xff0a151c);int height=(int)(88L*menu.amount()/Math.max(1,menu.capacity()));
        int color=net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(viewFluid().getFluid()).getTintColor(viewFluid())|0xff000000;
        graphics.fill(x+19,y+121-height,x+45,y+121,color);
    }
    private FluidStack viewFluid() {
        var stored=menu.machine.stored();
        return !stored.isEmpty() && BuiltInRegistries.FLUID.getId(stored.getFluid())==menu.fluidId() ? stored.copyWithAmount(1):new FluidStack(BuiltInRegistries.FLUID.byId(menu.fluidId()),1);
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {
        graphics.drawString(font,title,12,11,0xd8e9f0,false);
        Component fluid=menu.amount()==0 ? Component.translatable("gui.factorycraft.fluid_empty"):viewFluid().getHoverName();
        graphics.drawString(font,font.plainSubstrByWidth(fluid.getString(),148),60,35,0xa2dce9,false);
        graphics.drawString(font,Component.translatable("gui.factorycraft.fluid_volume",menu.amount(),menu.capacity()),60,52,0xd8e9f0,false);
        if(menu.machine.kind()==FluidKind.PUMP) {
            graphics.drawString(font,menu.data.get(4)+" FE",60,71,0x5ed3c8,false);
            graphics.drawString(font,Component.translatable("gui.factorycraft.pump_rate"),12,132,0xc0d5df,false);
        } else graphics.drawString(font,Component.translatable("gui.factorycraft.bucket_controls"),12,132,0xc0d5df,false);
        graphics.drawString(font,Component.translatable(menu.machine.kind()==FluidKind.PUMP ? "gui.factorycraft.fluid_pump_sides":"gui.factorycraft.fluid_sides"),60,95,0xc0d5df,false);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) { super.render(graphics,mouseX,mouseY,partial);renderTooltip(graphics,mouseX,mouseY); }
}
