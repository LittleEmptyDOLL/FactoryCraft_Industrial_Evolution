package factorycraft.client.screens;

import factorycraft.energy.PoleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PoleScreen extends AbstractContainerScreen<PoleMenu> {
  public PoleScreen(PoleMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 240;
    imageHeight = 150;
  }

  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff263541);
    g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xff344650);
  }

  protected void renderLabels(GuiGraphics g, int mx, int my) {
    var s = menu.pole.spec();
    g.drawString(font, title, 12, 10, 0xffffff, false);
    line(g, "supply", 28, s.supply, s.supply);
    line(g, "wire", 44, s.wire);
    line(g, "connections", 60, menu.data.get(5), menu.data.get(0));
    line(
        g,
        "network",
        76,
        menu.data.get(1) == 0
            ? Component.translatable("gui.factorycraft.pole_pending")
            : menu.data.get(2));
    line(g, "sources", 92, menu.data.get(3));
    line(g, "consumers", 108, menu.data.get(4));
    g.drawString(
        font, Component.translatable("gui.factorycraft.pole_controls"), 12, 130, 0x88c9de, false);
  }

  private void line(GuiGraphics g, String id, int y, Object... values) {
    g.drawString(
        font,
        Component.translatable("gui.factorycraft.pole_" + id, values),
        12,
        y,
        0xd2e7eb,
        false);
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
    renderTooltip(g, mx, my);
  }
}
