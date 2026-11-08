package factorycraft.client;

import factorycraft.FactoryCraft;
import factorycraft.client.screens.FactoryScreen;
import factorycraft.client.renderers.*;
import factorycraft.registry.Content;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid=FactoryCraft.ID,value=Dist.CLIENT)
public final class FactoryClient {
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent e) { e.register(Content.THERMAL_MENU.get(),factorycraft.client.screens.ThermalScreen::new);e.register(Content.MENU.get(),FactoryScreen::new);e.register(Content.FLUID_MENU.get(),factorycraft.client.screens.FluidScreen::new); }
    @SubscribeEvent public static void fluidExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            public net.minecraft.resources.ResourceLocation getStillTexture() { return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID,"block/steam"); }
            public net.minecraft.resources.ResourceLocation getFlowingTexture() { return getStillTexture(); }
            public int getTintColor() { return 0xffd4e8ed; }
        },factorycraft.thermal.SteamFluids.TYPE.get());
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(Content.BELT.get(),BeltRenderer::new);
        e.registerBlockEntityRenderer(Content.INSERTER.get(),InserterRenderer::new);
    }
}
