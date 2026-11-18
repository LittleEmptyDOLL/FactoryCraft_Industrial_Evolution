package factorycraft.client.trains;

import factorycraft.simulation.trains.StockSpec;
import factorycraft.trains.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TrainScreen extends AbstractContainerScreen<TrainMenu> {
  public TrainScreen(TrainMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 256;
    imageHeight = menu.kind == StockSpec.CARGO ? 238 : 202;
    inventoryLabelY = imageHeight - 94;
    titleLabelX = 12;
  }

  private int ghostMouse = -1;

  public boolean mouseClicked(double x, double y, int button) {
    if (menu.kind == StockSpec.CARGO && menu.data.get(8) != 0 && (button == 0 || button == 1))
      for (int i = 0; i < 40; i++) {
        var slot = menu.slots.get(i);
        if (isHovering(slot.x, slot.y, 16, 16, x, y)) {
          minecraft.gameMode.handleInventoryMouseClick(
              menu.containerId,
              i,
              button,
              net.minecraft.world.inventory.ClickType.PICKUP,
              minecraft.player);
          ghostMouse = button;
          return true;
        }
      }
    return super.mouseClicked(x, y, button);
  }

  public boolean mouseReleased(double x, double y, int button) {
    if (ghostMouse == button) {
      ghostMouse = -1;
      return true;
    }
    return super.mouseReleased(x, y, button);
  }

  protected void init() {
    super.init();
    int x = leftPos + 169, y = topPos + 38;
    if (menu.kind == StockSpec.CARGO) {
      button(0, "filters", x, y);
      button(1, "bar_less", x, y + 21);
      button(2, "bar_more", x, y + 42);
      y += 63;
    }
    button(3, "uncouple", x, y);
    button(4, "couple", x, y + 21);
    button(5, "color", x, y + 42);
  }

  private void button(int id, String label, int x, int y) {
    addRenderableWidget(
        Button.builder(
                Component.translatable("train.factorycraft." + label),
                b -> {
                  if (minecraft != null)
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                })
            .bounds(x, y, 80, 20)
            .build());
  }

  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff23303a);
    g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xff364751);
    for (int i = 0; i < menu.slots.size(); i++) {
      var slot = menu.slots.get(i);
      if (slot.x < 0) continue;
      int x = leftPos + slot.x, y = topPos + slot.y;
      g.fill(x - 1, y - 1, x + 17, y + 17, 0xff17242b);
      g.fill(x, y, x + 16, y + 16, 0xff455963);
      if (menu.kind == StockSpec.CARGO && i < 40) {
        if (i >= menu.data.get(5)) g.fill(x, y, x + 16, y + 16, 0x999b4136);
        var filter = menu.slots.get(40 + i).getItem();
        if (!filter.isEmpty() && slot.getItem().isEmpty()) {
          g.renderFakeItem(filter, x, y);
          g.fill(x, y, x + 16, y + 16, 0x77364751);
        }
      }
    }
  }

  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 12, 9, 0xffffff, false);
    g.drawString(
        font,
        Component.translatable("train.factorycraft.summary", menu.data.get(0), menu.data.get(6)),
        12,
        23,
        0xc7d9de,
        false);
    if (menu.kind == StockSpec.LOCOMOTIVE) {
      g.drawString(
          font,
          Component.translatable("train.factorycraft.fuel", menu.fuel()),
          12,
          65,
          0xe4c275,
          false);
      g.drawWordWrap(
          font, Component.translatable("train.factorycraft.enter_help"), 12, 82, 147, 0xb6cbd3);
    }
    if (menu.kind == StockSpec.CARGO)
      g.drawString(
          font,
          Component.translatable(
              menu.data.get(8) != 0 ? "train.factorycraft.filter_hint" : "train.factorycraft.bar",
              menu.data.get(5)),
          12,
          132,
          0xe4c275,
          false);
    if (menu.kind == StockSpec.FLUID) {
      var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.byId(menu.data.get(10));
      Component name =
          menu.volume() == 0
              ? Component.translatable("train.factorycraft.empty")
              : new net.neoforged.neoforge.fluids.FluidStack(fluid, menu.volume()).getHoverName();
      g.drawString(font, name, 12, 44, 0xd5e6e8, false);
      g.drawString(font, menu.volume() + " / 500000 mB", 12, 62, 0xe4c275, false);
      g.fill(12, 82, 155, 96, 0xff15242d);
      g.fill(13, 83, 13 + (int) (141L * menu.volume() / 500000), 95, 0xff58a3b1);
    }
    g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xb6cbd3, false);
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
    renderTooltip(g, mx, my);
  }
}
