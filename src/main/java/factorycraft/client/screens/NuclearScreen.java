package factorycraft.client.screens;

import factorycraft.nuclear.NuclearMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class NuclearScreen extends AbstractContainerScreen<NuclearMenu> {
  public NuclearScreen(NuclearMenu m, Inventory inv, Component title) {
    super(m, inv, title);
    imageHeight = 218;
    inventoryLabelY = 124;
  }

  protected void renderBg(GuiGraphics g, float p, int x, int y) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff172730);
    for (var s : menu.slots) {
      g.fill(
          leftPos + s.x - 1, topPos + s.y - 1, leftPos + s.x + 17, topPos + s.y + 17, 0xff60727a);
      g.fill(leftPos + s.x, topPos + s.y, leftPos + s.x + 16, topPos + s.y + 16, 0xff263740);
    }
  }

  protected void renderLabels(GuiGraphics g, int x, int y) {
    // Solid GUI fills write depth. Keep glyphs in front of that plane on every driver.
    g.pose().pushPose();
    g.pose().translate(0, 0, 10);
    g.drawString(font, title, 7, 6, 0xe9d9a4, false);
    var kind = menu.owner.kind();
    boolean solar = kind == factorycraft.blocks.FactoryKind.SOLAR_PANEL;
    long scale = solar ? 500L : 25L;
    g.drawString(
        font,
        Component.translatable(
            solar ? "gui.factorycraft.nuclear_generation" : "gui.factorycraft.nuclear_stored",
            menu.metric(0) * scale),
        7,
        23,
        0xbcdbe2,
        false);
    g.drawString(
        font,
        Component.translatable(
            solar ? "gui.factorycraft.nuclear_peak" : "gui.factorycraft.nuclear_capacity",
            menu.metric(1) * scale),
        7,
        35,
        0xbcdbe2,
        false);
    if (kind == factorycraft.blocks.FactoryKind.NUCLEAR_REACTOR
        || kind == factorycraft.blocks.FactoryKind.HEAT_PIPE
        || kind == factorycraft.blocks.FactoryKind.HEAT_EXCHANGER)
      g.drawString(
          font,
          Component.translatable(
              "gui.factorycraft.nuclear_temperature",
              String.format(java.util.Locale.ROOT, "%.1f", menu.metric(2) / 1000.0)),
          7,
          54,
          0xbcdbe2,
          false);
    if (kind == factorycraft.blocks.FactoryKind.NUCLEAR_REACTOR)
      g.drawWordWrap(
          font,
          Component.translatable("gui.factorycraft.nuclear_fuel", menu.metric(3), menu.metric(4)),
          7,
          68,
          162,
          0xbcdbe2);
    if (kind == factorycraft.blocks.FactoryKind.HEAT_EXCHANGER
        || kind == factorycraft.blocks.FactoryKind.STEAM_TURBINE)
      g.drawWordWrap(
          font,
          Component.translatable("gui.factorycraft.nuclear_fluids", menu.metric(6), menu.metric(7)),
          7,
          94,
          162,
          0xbcdbe2);
    g.drawString(font, playerInventoryTitle, 7, 124, 0xbcdbe2, false);
    if (menu.metric(5) == 0)
      g.drawString(
          font,
          Component.translatable("message.factorycraft.assembly_missing"),
          7,
          118,
          0xff8888,
          false);
    g.pose().popPose();
  }

  public void render(GuiGraphics g, int x, int y, float p) {
    super.render(g, x, y, p);
    renderTooltip(g, x, y);
  }
}
