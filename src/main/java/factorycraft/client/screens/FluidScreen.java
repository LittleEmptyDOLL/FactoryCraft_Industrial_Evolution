package factorycraft.client.screens;

import factorycraft.fluids.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

/** Read-only shared segment status; no delete/flush action or mutable resource slot. */
public final class FluidScreen extends AbstractContainerScreen<FluidMenu> {
  public FluidScreen(FluidMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 280;
    imageHeight = 202;
  }

  protected void init() {
    super.init();
    addRenderableWidget(
        Button.builder(
                Component.translatable("gui.factorycraft.pipeline_preview"),
                b -> factorycraft.client.PipelineOverlay.show(menu.preview))
            .bounds(leftPos + 10, topPos + 174, 260, 20)
            .build());
  }

  protected void renderBg(GuiGraphics g, float partial, int x, int y) {
    int a = leftPos, b = topPos;
    g.fill(a, b, a + imageWidth, b + imageHeight, 0xff17252f);
    g.fill(a + 3, b + 3, a + imageWidth - 3, b + imageHeight - 3, 0xff2c404c);
    g.fill(a + 15, b + 32, a + 45, b + 154, 0xff0a151c);
    int h = (int) (118d * menu.totalAmount() / Math.max(1, menu.totalCapacity()));
    int color =
        viewFluid().isEmpty()
            ? 0xff607985
            : net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(
                        viewFluid().getFluid())
                    .getTintColor(viewFluid())
                | 0xff000000;
    g.fill(a + 18, b + 151 - Math.min(118, h), a + 42, b + 151, color);
  }

  private FluidStack viewFluid() {
    var stored = menu.machine.stored();
    return !stored.isEmpty() && BuiltInRegistries.FLUID.getId(stored.getFluid()) == menu.fluidId()
        ? stored.copyWithAmount(1)
        : new FluidStack(BuiltInRegistries.FLUID.byId(menu.fluidId()), 1);
  }

  protected void renderLabels(GuiGraphics g, int x, int y) {
    g.drawString(font, title, 10, 10, 0xd8e9f0, false);
    g.drawString(font, "#" + menu.segmentId(), 220, 10, 0xa2dce9, false);
    var fluid =
        menu.totalAmount() == 0
            ? Component.translatable("gui.factorycraft.fluid_empty")
            : viewFluid().getHoverName();
    g.drawString(font, font.plainSubstrByWidth(fluid.getString(), 210), 55, 32, 0xa2dce9, false);
    line(g, "pipeline_amount", menu.totalAmount(), 48);
    line(g, "pipeline_capacity", menu.totalCapacity(), 62);
    g.drawString(
        font,
        String.format(
            java.util.Locale.ROOT,
            "%.2f%%",
            100d * menu.totalAmount() / Math.max(1, menu.totalCapacity())),
        55,
        76,
        0xd8e9f0,
        false);
    line(g, "pipeline_members", menu.cells(), 90);
    g.drawString(
        font,
        Component.translatable("gui.factorycraft.pipeline_status." + menu.data.get(9)),
        55,
        104,
        menu.data.get(9) >= 2 ? 0xffa060 : 0x6fe4b3,
        false);
    line(g, "pipeline_conflicts", menu.data.get(10), 118);
    g.drawString(
        font,
        Component.translatable(
            "gui.factorycraft.pipeline_extent",
            menu.data.get(11),
            menu.data.get(12),
            menu.data.get(13)),
        55,
        132,
        0xd8e9f0,
        false);
    if (menu.machine.kind() == FluidKind.PUMP)
      g.drawString(
          font,
          menu.data.get(4) + " FE · " + FluidSimulation.PUMP_RATE + " mB/t",
          55,
          148,
          0x5ed3c8,
          false);
  }

  private void line(GuiGraphics g, String key, Object value, int y) {
    g.drawString(
        font, Component.translatable("gui.factorycraft." + key, value), 55, y, 0xd8e9f0, false);
  }

  public void render(GuiGraphics g, int x, int y, float partial) {
    super.render(g, x, y, partial);
    renderTooltip(g, x, y);
  }
}
