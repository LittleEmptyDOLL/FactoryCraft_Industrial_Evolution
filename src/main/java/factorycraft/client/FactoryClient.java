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
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent e) { e.register(Content.MENU.get(),FactoryScreen::new); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(Content.BELT.get(),BeltRenderer::new);
        e.registerBlockEntityRenderer(Content.INSERTER.get(),InserterRenderer::new);
    }
}
