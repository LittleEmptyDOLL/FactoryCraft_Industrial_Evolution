package factorycraft.client;

import factorycraft.FactoryCraft;
import factorycraft.client.renderers.*;
import factorycraft.client.screens.FactoryScreen;
import factorycraft.registry.Content;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class FactoryClient {
  @SubscribeEvent
  public static void geometry(ModelEvent.RegisterGeometryLoaders event) {
    event.register(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "machine"),
        new factorycraft.client.models.MachineGeometryLoader());
  }

  @SubscribeEvent
  public static void beltGeometry(ModelEvent.RegisterGeometryLoaders event) {
    event.register(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "belt"),
        new factorycraft.client.models.BeltGeometryLoader());
  }

  @SubscribeEvent
  public static void movingModels(ModelEvent.RegisterAdditional event) {
    for (var p : factorycraft.multiblock.MachineProfile.values())
      if (p != factorycraft.multiblock.MachineProfile.TANK
          && p != factorycraft.multiblock.MachineProfile.PUMP
          && !p.splitter()
          && !p.circuit())
        event.register(
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    FactoryCraft.ID, "block/machine/" + p.id + "_moving")));
  }

  @SubscribeEvent
  public static void menus(RegisterMenuScreensEvent e) {
    e.register(Content.CIRCUIT_MENU.get(), factorycraft.client.screens.CircuitScreen::new);
    e.register(Content.INSERTER_MENU.get(), factorycraft.client.screens.InserterScreen::new);
    e.register(Content.SPLITTER_MENU.get(), factorycraft.client.screens.SplitterScreen::new);
    e.register(Content.MODULE_MENU.get(), factorycraft.client.screens.ModuleScreen::new);
    e.register(Content.RESEARCH_MENU.get(), factorycraft.client.screens.ResearchScreen::new);
    e.register(Content.PETROLEUM_MENU.get(), factorycraft.client.screens.PetroleumScreen::new);
    e.register(Content.THERMAL_MENU.get(), factorycraft.client.screens.ThermalScreen::new);
    e.register(Content.MENU.get(), FactoryScreen::new);
    e.register(Content.FLUID_MENU.get(), factorycraft.client.screens.FluidScreen::new);
  }

  @SubscribeEvent
  public static void fluidExtensions(
      net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
    event.registerFluidType(
        new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
          public net.minecraft.resources.ResourceLocation getStillTexture() {
            return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                FactoryCraft.ID, "block/steam");
          }

          public net.minecraft.resources.ResourceLocation getFlowingTexture() {
            return getStillTexture();
          }

          public int getTintColor() {
            return 0xffd4e8ed;
          }
        },
        factorycraft.thermal.SteamFluids.TYPE.get());
  }

  @SubscribeEvent
  public static void petroleumExtensions(
      net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
    for (var kind : factorycraft.petroleum.PetroleumFluids.ALL)
      event.registerFluidType(
          new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            public net.minecraft.resources.ResourceLocation getStillTexture() {
              return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                  FactoryCraft.ID, "block/" + kind.id);
            }

            public net.minecraft.resources.ResourceLocation getFlowingTexture() {
              return getStillTexture();
            }

            public int getTintColor() {
              return kind.tint;
            }
          },
          kind.type.get());
  }

  @SubscribeEvent
  public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
    e.registerBlockEntityRenderer(Content.BEACON.get(), MachineAnimationRenderer::new);
    e.registerBlockEntityRenderer(Content.LAB.get(), MachineAnimationRenderer::new);
    e.registerBlockEntityRenderer(Content.MACHINE.get(), MachineAnimationRenderer::new);
    e.registerBlockEntityRenderer(Content.THERMAL.get(), MachineAnimationRenderer::new);
    e.registerBlockEntityRenderer(Content.BELT.get(), BeltRenderer::new);
    e.registerBlockEntityRenderer(
        Content.CIRCUIT.get(), factorycraft.client.renderers.CircuitRenderer::new);
    e.registerBlockEntityRenderer(Content.INSERTER.get(), InserterRenderer::new);
  }
}
