package factorycraft.client.screens;

import factorycraft.blockentity.BeaconBlockEntity;
import factorycraft.modules.ModuleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ModuleScreen extends AbstractContainerScreen<ModuleMenu> {
  public ModuleScreen(ModuleMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 176;
    imageHeight = 190;
    inventoryLabelX = 8;
    inventoryLabelY = 96;
  }

  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    int x = leftPos, y = topPos;
    g.fill(x, y, x + imageWidth, y + imageHeight, 0xff253440);
    for (var slot : menu.slots) {
      g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff75838c);
      g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xff15232c);
    }
  }

  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, Component.translatable("gui.factorycraft.modules"), 8, 8, 0xffffff, false);
    g.drawString(font, menu.data.get(0) + " FE", 102, 8, 0x80dddd, false);
    g.drawString(
        font,
        Component.translatable("gui.factorycraft.module_rates", menu.data.get(1), menu.data.get(2)),
        8,
        58,
        0xffffff,
        false);
    if (menu.owner instanceof BeaconBlockEntity)
      g.drawString(
          font,
          Component.translatable(
              menu.data.get(3) > 0 ? "gui.factorycraft.beacon_on" : "gui.factorycraft.beacon_off"),
          8,
          72,
          0xffd680,
          false);
    else
      g.drawString(
          font,
          Component.translatable(
              "gui.factorycraft.module_productivity", menu.data.get(3), menu.data.get(4) / 100),
          8,
          72,
          0xffd680,
          false);
    g.drawString(font, playerInventoryTitle, 8, 96, 0xffffff, false);
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
    renderTooltip(g, mx, my);
  }
}
