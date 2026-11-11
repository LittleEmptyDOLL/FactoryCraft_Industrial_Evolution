package factorycraft.client.screens;

import factorycraft.inserters.InserterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class InserterScreen extends AbstractContainerScreen<InserterMenu> {
  private Button enabled, mode, reflection;

  public InserterScreen(InserterMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageHeight = 233;
    inventoryLabelY = 139;
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
    reflection =
        addRenderableWidget(
            Button.builder(Component.empty(), b -> action(6))
                .bounds(leftPos + 8, topPos + 108, 160, 17)
                .build());
    mode =
        addRenderableWidget(
            Button.builder(Component.empty(), b -> action(1))
                .bounds(leftPos + 90, topPos + 23, 78, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.literal("×"), b -> action(2))
            .bounds(leftPos + 104, topPos + 52, 20, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("−"), b -> action(3))
            .bounds(leftPos + 8, topPos + 73, 20, 16)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("+"), b -> action(4))
            .bounds(leftPos + 32, topPos + 73, 20, 16)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.factorycraft.inserter_auto"), b -> action(5))
            .bounds(leftPos + 56, topPos + 73, 48, 16)
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
    for (int i = 0; i < 5; i++)
      if ((menu.data.get(7) & (1 << i)) != 0)
        g.drawString(font, "?", 12 + i * 18, 58, 0xffbc60, false);
    g.drawString(
        font, Component.translatable("gui.factorycraft.inserter_hand"), 135, 44, 0xffffff, false);
    g.drawString(
        font,
        Component.translatable(
            "gui.factorycraft.inserter_capacity",
            menu.data.get(5) == 0 ? "A" : menu.data.get(5),
            menu.data.get(6)),
        108,
        77,
        0xffffff,
        false);
    if (menu.owner.burner()) {
      long energy = 0;
      for (int i = 0; i < 4; i++) energy |= (menu.data.get(8 + i) & 65535L) << (i * 16);
      g.drawString(
          font,
          Component.translatable("gui.factorycraft.inserter_fuel", energy),
          29,
          96,
          0xffd680,
          false);
    } else
      g.drawString(
          font,
          Component.translatable("gui.factorycraft.inserter_power", menu.data.get(2)),
          8,
          96,
          0xffd680,
          false);
    g.drawString(
        font,
        Component.translatable(
            "gui.factorycraft.inserter_speed",
            String.format(java.util.Locale.ROOT, "%.2f", menu.data.get(4) / 3d),
            menu.data.get(3)),
        8,
        128,
        0xb0e4eb,
        false);
    reflection.setTooltip(
        net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("gui.factorycraft.inserter_status." + menu.data.get(12))));
    g.drawString(font, playerInventoryTitle, 8, 139, 0xffffff, false);
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    reflection.setMessage(
        Component.translatable("gui.factorycraft.inserter_reflected." + menu.data.get(15)));
    enabled.setMessage(
        Component.translatable("gui.factorycraft.inserter_enabled." + menu.data.get(0)));
    mode.setMessage(Component.translatable("gui.factorycraft.inserter_mode." + menu.data.get(1)));
    super.render(g, mx, my, partial);
    renderTooltip(g, mx, my);
  }
}
