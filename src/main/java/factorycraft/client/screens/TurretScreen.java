package factorycraft.client.screens;

import factorycraft.combat.TurretMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TurretScreen extends AbstractContainerScreen<TurretMenu> {
  public TurretScreen(TurretMenu m, Inventory i, Component title) {
    super(m, i, title);
    imageHeight = 194;
    inventoryLabelY = 100;
  }

  protected void renderBg(GuiGraphics g, float p, int x, int y) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff17232a);
    for (var s : menu.slots) {
      if (!s.isActive()) continue;
      g.fill(
          leftPos + s.x - 1, topPos + s.y - 1, leftPos + s.x + 17, topPos + s.y + 17, 0xff51606a);
      g.fill(leftPos + s.x, topPos + s.y, leftPos + s.x + 16, topPos + s.y + 16, 0xff283740);
    }
  }

  protected void renderLabels(GuiGraphics g, int x, int y) {
    g.drawString(font, title, 8, 6, 0xe9d9a4, false);
    int kind = menu.data.get(3);
    g.drawString(
        font,
        Component.translatable(
            kind == 0
                ? "gui.factorycraft.turret_ammo"
                : kind == 1 ? "gui.factorycraft.turret_laser" : "gui.factorycraft.turret_oil"),
        8,
        24,
        0xffffff,
        false);
    if (kind == 1)
      g.drawString(font, "FE: " + menu.data.get(0) + " / 2136", 8, 52, 0xbcdbe2, false);
    else if (kind == 2) {
      g.drawString(
          font,
          Component.translatable("gui.factorycraft.turret_pipe", menu.data.get(5)),
          8,
          48,
          0xbcdbe2,
          false);
      g.drawString(
          font,
          Component.translatable("gui.factorycraft.turret_fuel", menu.data.get(1)),
          8,
          65,
          0xbcdbe2,
          false);
    } else
      g.drawString(
          font,
          Component.translatable("tooltip.factorycraft.loaded", menu.data.get(2)),
          8,
          80,
          0xbcdbe2,
          false);
    if (menu.data.get(4) != 0)
      g.drawString(
          font,
          Component.translatable("gui.factorycraft.turret_status_" + menu.data.get(6)),
          8,
          92,
          0xe9d9a4,
          false);
    if (menu.data.get(4) == 0)
      g.drawString(
          font,
          Component.translatable("message.factorycraft.assembly_missing"),
          8,
          92,
          0xff8888,
          false);
  }

  public void render(GuiGraphics g, int x, int y, float p) {
    super.render(g, x, y, p);
    renderTooltip(g, x, y);
  }
}
