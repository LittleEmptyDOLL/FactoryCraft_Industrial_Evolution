package factorycraft.client.screens;

import factorycraft.blocks.FactoryKind;
import factorycraft.circuits.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CircuitScreen extends AbstractContainerScreen<CircuitMenu> {
  private EditBox operand;
  private Button apply, operation, count, mode;
  private int lastOperand = Integer.MIN_VALUE;

  public CircuitScreen(CircuitMenu m, Inventory inv, Component title) {
    super(m, inv, title);
    imageHeight = 236;
    inventoryLabelY = 142;
  }

  private void submit() {
    try {
      PacketDistributor.sendToServer(
          new CircuitNetwork.Operand(menu.containerId, Integer.parseInt(operand.getValue())));
    } catch (NumberFormatException ignored) {
    }
  }

  protected void init() {
    super.init();
    operation =
        addRenderableWidget(
            Button.builder(
                    Component.empty(),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0))
                .bounds(leftPos + 8, topPos + 66, 48, 20)
                .build());
    operation.active =
        menu.owner.kind() != FactoryKind.CONSTANT
            && menu.owner.kind() != FactoryKind.CIRCUIT_READER;
    operation.visible = operation.active;
    operand =
        new EditBox(
            font,
            leftPos + 62,
            topPos + 67,
            92,
            18,
            Component.translatable("gui.factorycraft.circuit_operand"));
    operand.setMaxLength(11);
    operand.setFilter(s -> s.matches("-?[0-9]{0,10}"));
    lastOperand = menu.operand();
    operand.setValue(Integer.toString(lastOperand));
    addRenderableWidget(operand);
    apply =
        addRenderableWidget(
            Button.builder(Component.literal("✓"), b -> submit())
                .bounds(leftPos + 155, topPos + 66, 16, 20)
                .build());
    count =
        addRenderableWidget(
            Button.builder(
                    Component.empty(),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1))
                .bounds(leftPos + 8, topPos + 92, 160, 18)
                .build());
    count.visible = menu.owner.kind() == FactoryKind.DECIDER;
    mode =
        addRenderableWidget(
            Button.builder(
                    Component.empty(),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2))
                .bounds(leftPos + 8, topPos + 92, 160, 18)
                .build());
    mode.visible = menu.owner.kind() == FactoryKind.CIRCUIT_READER;
    if (menu.owner.kind() == FactoryKind.CIRCUIT_READER) {
      operand.setVisible(false);
      apply.visible = false;
    }
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
    if (menu.slots.get(0).isActive())
      g.drawString(
          font,
          Component.translatable(
              menu.fluidReader()
                  ? "gui.factorycraft.circuit_fluid_filter"
                  : "gui.factorycraft.circuit_input"),
          8,
          27,
          0xbacbd4,
          false);
    if (menu.slots.get(1).isActive())
      g.drawString(
          font,
          Component.translatable("gui.factorycraft.circuit_output"),
          110,
          27,
          0xbacbd4,
          false);
    if (menu.owner.kind() != FactoryKind.CONSTANT
        && menu.owner.kind() != FactoryKind.CIRCUIT_READER)
      g.drawString(
          font,
          Component.translatable("gui.factorycraft.circuit_live_input", menu.input()),
          8,
          114,
          0xffd680,
          false);
    g.drawString(
        font,
        Component.translatable(
            menu.owner.kind() == FactoryKind.CIRCUIT_CONTROLLER
                ? "gui.factorycraft.circuit_enabled"
                : menu.fluidReader()
                    ? "gui.factorycraft.circuit_fluid_output"
                    : "gui.factorycraft.circuit_live_output",
            menu.owner.kind() == FactoryKind.CIRCUIT_CONTROLLER ? menu.data.get(8) : menu.output()),
        8,
        126,
        0xffd680,
        false);
    g.drawString(font, playerInventoryTitle, 8, 142, 0xffffff, false);
    if (menu.owner.kind() == FactoryKind.CIRCUIT_READER)
      g.drawString(
          font,
          Component.translatable("gui.factorycraft.circuit_sensor_ready." + menu.data.get(10)),
          8,
          114,
          menu.data.get(10) == 1 ? 0x80dca0 : 0xffa080,
          false);
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    if (!operand.isFocused() && lastOperand != menu.operand()) {
      lastOperand = menu.operand();
      operand.setValue(Integer.toString(lastOperand));
    }
    String[] names =
        menu.owner.kind() == FactoryKind.ARITHMETIC
            ? new String[] {"+", "−", "×", "÷", "%", "AND", "OR", "XOR", "<<", ">>"}
            : new String[] {">", "<", "=", "≠", "≥", "≤"};
    operation.setMessage(Component.literal(names[Math.floorMod(menu.data.get(0), names.length)]));
    count.setMessage(Component.translatable("gui.factorycraft.circuit_count." + menu.data.get(3)));
    mode.setMessage(
        Component.translatable("gui.factorycraft.circuit_reader_mode." + menu.data.get(9)));
    try {
      Integer.parseInt(operand.getValue());
      apply.active = true;
    } catch (NumberFormatException e) {
      apply.active = false;
    }
    super.render(g, mx, my, partial);
    boolean ghost =
        menu.getCarried().isEmpty()
            && my >= topPos + 41
            && my < topPos + 60
            && (mx >= leftPos + 23 && mx < leftPos + 42
                || mx >= leftPos + 135 && mx < leftPos + 154);
    if (ghost && menu.fluidReader() && mx < leftPos + 60) {
      var fluid =
          factorycraft.items.FluidCanisterItem.handler(menu.slots.getFirst().getItem().copy())
              .getFluid();
      var lines = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
      lines.addAll(
          font.split(
              fluid.isEmpty()
                  ? Component.translatable("gui.factorycraft.circuit_all_fluids")
                  : fluid.getHoverName(),
              150));
      lines.addAll(font.split(Component.translatable("gui.factorycraft.circuit_fluid_hint"), 150));
      g.renderTooltip(font, lines, mx, my);
    } else if (ghost)
      g.renderTooltip(
          font,
          font.split(
              Component.translatable(
                  menu.fluidReader() && mx < leftPos + 60
                      ? "gui.factorycraft.circuit_fluid_hint"
                      : "gui.factorycraft.circuit_signal_hint"),
              150),
          mx,
          my);
    else renderTooltip(g, mx, my);
  }

  @Override
  public boolean keyPressed(int key, int scan, int mods) {
    if (key != 256 && operand.isFocused()) {
      if (key == 257) {
        submit();
        return true;
      }
      return operand.keyPressed(key, scan, mods) || operand.canConsumeInput();
    }
    return super.keyPressed(key, scan, mods);
  }
}
