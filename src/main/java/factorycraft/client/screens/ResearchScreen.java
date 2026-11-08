package factorycraft.client.screens;

import factorycraft.research.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Searchable dependency graph with live authoritative team state; no client progress calculation. */
public final class ResearchScreen extends AbstractContainerScreen<ResearchMenu> {
    private EditBox search;private String filter="";private ResourceLocation hovered;private int panX,panY;
    public ResearchScreen(ResearchMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=300;imageHeight=240;inventoryLabelX=132;inventoryLabelY=153;}
    protected void init(){super.init();search=new EditBox(font,leftPos+8,topPos+21,282,16,Component.translatable("gui.factorycraft.research_search"));search.setHint(Component.translatable("gui.factorycraft.research_search"));search.setMaxLength(64);search.setValue(filter);search.setResponder(v->filter=v);addRenderableWidget(search);}
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        // Container inventory/viewer shortcuts must not consume letters typed into search.
        // charTyped still reaches the focused EditBox through the normal event dispatcher.
        if(search!=null && search.isFocused() && key!=org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(key,scan,modifiers);
            return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    private Component name(ResourceLocation id){return Component.translatableWithFallback("technology."+id.getNamespace()+"."+id.getPath(),id.getPath());}
    private net.minecraft.nbt.CompoundTag state(){return ResearchNetwork.clientState.menu()==menu.containerId ? ResearchNetwork.clientState.data():new net.minecraft.nbt.CompoundTag();}
    private boolean visible(ResearchCatalog.Entry e){return name(e.id()).getString().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)) || e.id().toString().contains(filter.toLowerCase(Locale.ROOT));}
    protected void renderBg(GuiGraphics g,float partial,int mx,int my){int x=leftPos,y=topPos;g.fill(x,y,x+300,y+240,0xff253440);g.fill(x+5,y+40,x+295,y+130,0xff142029);hovered=null;var nodes=state().getCompound("Nodes");var catalog=ResearchCatalog.get(menu.lab.getLevel());
        g.enableScissor(x+5,y+40,x+295,y+130);
        for(var e:catalog.entries())if(visible(e))for(var p:e.recipe().prerequisites()){var parent=catalog.entry(p);if(parent!=null){int a=x+12+panX+parent.recipe().x()*70+31,b=y+44+panY+parent.recipe().y()*24+8,c=x+12+panX+e.recipe().x()*70,d=y+44+panY+e.recipe().y()*24+8;g.fill(a,b,c+1,b+1,0xff667d89);g.fill(c,Math.min(b,d),c+1,Math.max(b,d)+1,0xff667d89);}}
        for(var e:catalog.entries())if(visible(e)){int a=x+12+panX+e.recipe().x()*70,b=y+44+panY+e.recipe().y()*24;var n=nodes.getCompound(e.id().toString());int color=n.getInt("Level")>0 ? 0xff28745b:n.getBoolean("Available") ? 0xff526d88:0xff41464c;if(e.id().toString().equals(state().getString("Selected")))color=0xffad7530;g.fill(a,b,a+64,b+19,color);g.drawString(font,font.plainSubstrByWidth(name(e.id()).getString(),60),a+2,b+5,0xffffff,false);if(mx>=a && mx<a+64 && my>=b && my<b+19)hovered=e.id();}
        g.disableScissor();for(var slot:menu.slots){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff75838c);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff15232c);}}
    protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,8,7,0xffffff,false);g.drawString(font,state().getInt("FE")+" FE",230,7,0x80dddd,false);g.drawString(font,Component.translatable("gui.factorycraft.science"),8,129,0xffffff,false);g.drawString(font,Component.translatable("gui.factorycraft.research_drag"),8,117,0x99aabb,false);String selected=state().getString("Selected");var id=ResourceLocation.tryParse(selected);if(!selected.isEmpty() && id!=null){var n=state().getCompound("Nodes").getCompound(selected);g.drawString(font,font.plainSubstrByWidth(name(id).getString(),118),8,164,0xffda8c,false);g.drawString(font,n.getInt("Units")+" / "+n.getInt("Cost"),8,177,0xffffff,false);g.drawString(font,Component.translatable("gui.factorycraft.lab_work",state().getInt("Work")),8,190,0xffffff,false);}g.drawString(font,font.plainSubstrByWidth(state().getString("TeamLabel"),118),8,210,0x99bbcc,false);g.drawString(font,playerInventoryTitle,132,153,0xffffff,false);}
    public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(button==1 && my>=topPos+40 && my<topPos+130){panX=Math.clamp(panX+(int)dx,-2300,200);panY=Math.clamp(panY+(int)dy,-800,60);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    public boolean mouseClicked(double mx,double my,int button){if(button==0 && hovered!=null && my>=topPos+40 && my<topPos+130){PacketDistributor.sendToServer(new ResearchNetwork.Select(menu.containerId,hovered));return true;}return super.mouseClicked(mx,my,button);}
    public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);renderTooltip(g,mx,my);if(hovered!=null){var e=ResearchCatalog.get(menu.lab.getLevel()).entry(hovered);var lines=new ArrayList<Component>();lines.add(name(hovered));lines.add(Component.literal(e.recipe().units()+" × "+e.recipe().ticks()+" t"));for(var p:e.recipe().packs().entrySet())lines.add(Component.literal(p.getValue()+" × ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(p.getKey()).getDescription()));for(var p:e.recipe().prerequisites())lines.add(Component.translatable("gui.factorycraft.requires",name(p)));for(var id:e.recipe().unlocks())lines.add(Component.literal("→ ").append(Component.translatableWithFallback("recipe."+id.getNamespace()+"."+id.getPath(),net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id).getDescription().getString())));g.renderComponentTooltip(font,lines,mx,my);}}
}
