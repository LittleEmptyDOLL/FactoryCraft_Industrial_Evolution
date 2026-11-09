package factorycraft.client.screens;

import factorycraft.inserters.InserterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class InserterScreen extends AbstractContainerScreen<InserterMenu> {
  private Button enabled, mode;

  public InserterScreen(InserterMenu m, Inventory inv, Component title) {
    super(m, inv, title);
    imageHeight = 196;
    inventoryLabelY = 102;
  }

  private void action(int id) {
    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
  }

  protected void init() {
    super.init();
    enabled =
        addRenderableWidget(
            Button.builder(Component.empty(), b -> action(0))
                .bounds(leftPos + 8, topPos + 23, 78, 20)
                .build());
    mode =
        addRenderableWidget(
            Button.builder(Component.empty(), b -> action(1))
                .bounds(leftPos + 90, topPos + 23, 78, 20)
                .build());
    mode.active = menu.filterable();
    mode.visible = menu.filterable();
    if (menu.filterable())
      addRenderableWidget(
          Button.builder(Component.translatable("gui.factorycraft.splitter_clear"), b -> action(2))
              .bounds(leftPos + 8, topPos + 58, 60, 20)
              .build());
  }

  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff253440);
    for (var slot : menu.slots)
      if (slot.isActive()) {
        g.fill(
            leftPos + slot.x - 1,
            topPos + slot.y - 1,
            leftPos + slot.x + 17,
            topPos + slot.y + 17,
            0xff75838c);
        g.fill(
            leftPos + slot.x,
            topPos + slot.y,
            leftPos + slot.x + 16,
            topPos + slot.y + 16,
            0xff15232c);
      }
  }

  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 8, 8, 0xffffff, false);
    g.drawString(
        font,
        Component.translatable("gui.factorycraft.inserter_power", menu.data.get(2)),
        8,
        88,
        0xffd680,
        false);
    g.drawString(
        font, Component.translatable("gui.factorycraft.inserter_hand"), 111, 48, 0xffffff, false);
    g.drawString(font, playerInventoryTitle, 8, 102, 0xffffff, false);
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    enabled.setMessage(
        Component.translatable("gui.factorycraft.inserter_enabled." + menu.data.get(0)));
    mode.setMessage(Component.translatable("gui.factorycraft.inserter_mode." + menu.data.get(1)));
    super.render(g, mx, my, partial);
    if (menu.filterable()
        && menu.getCarried().isEmpty()
        && mx >= leftPos + 78
        && mx < leftPos + 97
        && my >= topPos + 59
        && my < topPos + 78)
      g.renderTooltip(
          font,
          font.split(Component.translatable("gui.factorycraft.splitter_filter_tip"), 150),
          mx,
          my);
    else renderTooltip(g, mx, my);
  }
}
