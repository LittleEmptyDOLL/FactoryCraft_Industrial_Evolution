package factorycraft.client.screens;

import factorycraft.logistics.SplitterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SplitterScreen extends AbstractContainerScreen<SplitterMenu> {
  private Button input, output;

  public SplitterScreen(SplitterMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 176;
    imageHeight = 228;
    inventoryLabelY = 134;
  }

  private Component mode(int value) {
    return Component.translatable("gui.factorycraft.splitter_mode." + value);
  }

  protected void init() {
    super.init();
    input =
        addRenderableWidget(
            Button.builder(
                    Component.empty(),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0))
                .bounds(leftPos + 8, topPos + 25, 160, 20)
                .build());
    output =
        addRenderableWidget(
            Button.builder(
                    Component.empty(),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1))
                .bounds(leftPos + 8, topPos + 49, 160, 20)
                .build());
    addRenderableWidget(
        Button.builder(
                Component.translatable("gui.factorycraft.splitter_clear"),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2))
            .bounds(leftPos + 105, topPos + 81, 63, 20)
            .build());
  }

  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff253440);
    for (var slot : menu.slots) {
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
        Component.translatable("gui.factorycraft.splitter_circuit." + menu.data.get(3)),
        8,
        72,
        menu.data.get(3) == 2 ? 0xffa080 : 0x80dca0,
        false);
    g.drawString(
        font, Component.translatable("gui.factorycraft.splitter_filter"), 8, 87, 0xffd680, false);
    g.drawWordWrap(
        font, Component.translatable("gui.factorycraft.splitter_hint"), 8, 109, 160, 0xbacbd4);
    g.drawString(font, playerInventoryTitle, 8, 134, 0xffffff, false);
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    input.setMessage(
        Component.translatable("gui.factorycraft.splitter_input", mode(menu.data.get(0))));
    output.setMessage(
        Component.translatable("gui.factorycraft.splitter_output", mode(menu.data.get(1))));
    super.render(g, mx, my, partial);
    boolean filterHovered =
        menu.getCarried().isEmpty()
            && mx >= leftPos + 78
            && mx < leftPos + 97
            && my >= topPos + 82
            && my < topPos + 101;
    if (filterHovered)
      g.renderTooltip(
          font,
          font.split(
              Component.translatable("gui.factorycraft.splitter_filter_tip"),
              Math.min(150, width - 32)),
          mx,
          my);
    else renderTooltip(g, mx, my);
  }
}
