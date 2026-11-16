package factorycraft.client.screens;

import factorycraft.research.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Searchable dependency graph with live authoritative team state; no client progress calculation.
 */
public final class ResearchScreen extends AbstractContainerScreen<ResearchMenu> {
  private EditBox search;
  private String filter = "";
  private ResourceLocation hovered;
  private int panX, panY;

  public ResearchScreen(ResearchMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 360;
    imageHeight = 240;
    inventoryLabelX = 190;
    inventoryLabelY = 153;
  }

  protected void init() {
    super.init();
    search =
        new EditBox(
            font,
            leftPos + 8,
            topPos + 21,
            342,
            16,
            Component.translatable("gui.factorycraft.research_search"));
    search.setHint(Component.translatable("gui.factorycraft.research_search"));
    search.setMaxLength(64);
    search.setValue(filter);
    search.setResponder(
        v -> {
          filter = v;
          if (!v.isBlank())
            ResearchCatalog.get(menu.lab.getLevel()).entries().stream()
                .filter(this::visible)
                .min(
                    Comparator.comparingInt((ResearchCatalog.Entry e) -> e.recipe().x())
                        .thenComparingInt(e -> e.recipe().y()))
                .ifPresent(
                    e -> {
                      panX = -e.recipe().x() * 70;
                      panY = -e.recipe().y() * 24;
                      clampPan();
                    });
        });
    addRenderableWidget(search);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    // Container inventory/viewer shortcuts must not consume letters typed into search.
    // charTyped still reaches the focused EditBox through the normal event dispatcher.
    if (search != null && search.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
      search.keyPressed(key, scan, modifiers);
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }

  private Component name(ResourceLocation id) {
    if (SciencePacks.IDS.contains(id))
      return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id).getDescription();
    return Component.translatableWithFallback(
        "technology." + id.getNamespace() + "." + id.getPath(), id.getPath());
  }

  private net.minecraft.nbt.CompoundTag state() {
    return ResearchNetwork.clientState.menu() == menu.containerId
        ? ResearchNetwork.clientState.data()
        : new net.minecraft.nbt.CompoundTag();
  }

  private boolean visible(ResearchCatalog.Entry e) {
    return name(e.id())
            .getString()
            .toLowerCase(Locale.ROOT)
            .contains(filter.toLowerCase(Locale.ROOT))
        || e.id().toString().contains(filter.toLowerCase(Locale.ROOT));
  }

  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    int x = leftPos, y = topPos;
    g.fill(x, y, x + 360, y + 240, 0xff253440);
    g.fill(x + 5, y + 40, x + 355, y + 115, 0xff142029);
    hovered = null;
    var nodes = state().getCompound("Nodes");
    var catalog = ResearchCatalog.get(menu.lab.getLevel());
    g.enableScissor(x + 5, y + 40, x + 355, y + 115);
    for (var e : catalog.entries())
      if (visible(e))
        for (var p : e.recipe().prerequisites()) {
          var parent = catalog.definition(p);
          if (parent != null) {
            int a = x + 12 + panX + parent.recipe().x() * 70 + 31,
                b = y + 44 + panY + parent.recipe().y() * 24 + 8,
                c = x + 12 + panX + e.recipe().x() * 70,
                d = y + 44 + panY + e.recipe().y() * 24 + 8;
            g.fill(a, b, c + 1, b + 1, 0xff667d89);
            g.fill(c, Math.min(b, d), c + 1, Math.max(b, d) + 1, 0xff667d89);
          }
        }
    for (var e : catalog.entries())
      if (visible(e)) {
        int a = x + 12 + panX + e.recipe().x() * 70, b = y + 44 + panY + e.recipe().y() * 24;
        var n = nodes.getCompound(e.id().toString());
        int color =
            n.getInt("Level") > 0
                ? 0xff28745b
                : n.getBoolean("Available")
                    ? 0xff526d88
                    : n.getBoolean("Disabled") ? 0xff513d53 : 0xff41464c;
        if (e.id().toString().equals(state().getString("Selected"))) color = 0xffad7530;
        g.fill(a, b, a + 64, b + 19, color);
        g.drawString(
            font,
            font.plainSubstrByWidth(name(e.id()).getString(), 60),
            a + 2,
            b + 5,
            0xffffff,
            false);
        if (mx >= x + 5
            && mx < x + 355
            && my >= y + 40
            && my < y + 115
            && mx >= a
            && mx < a + 64
            && my >= b
            && my < b + 19) hovered = e.id();
      }
    g.disableScissor();
    for (var slot : menu.slots) {
      g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff75838c);
      g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xff15232c);
    }
  }

  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 8, 7, 0xffffff, false);
    g.drawString(font, state().getInt("FE") + " FE", 290, 7, 0x80dddd, false);
    g.drawString(font, Component.translatable("gui.factorycraft.science"), 8, 129, 0xffffff, false);
    g.drawString(
        font, Component.translatable("gui.factorycraft.research_drag"), 8, 117, 0x99aabb, false);
    g.drawString(
        font, Component.translatable("gui.factorycraft.lab_modules"), 154, 129, 0xffffff, false);
    String selected = state().getString("Selected");
    var id = ResourceLocation.tryParse(selected);
    if (!selected.isEmpty() && id != null) {
      var n = state().getCompound("Nodes").getCompound(selected);
      g.drawString(
          font, font.plainSubstrByWidth(name(id).getString(), 174), 8, 181, 0xffda8c, false);
      g.drawString(font, n.getInt("Units") + " / " + n.getInt("Cost"), 8, 193, 0xffffff, false);
      g.drawString(
          font,
          Component.translatable("gui.factorycraft.lab_work", state().getInt("Work")),
          8,
          205,
          0xffffff,
          false);
    }
    g.drawString(
        font,
        Component.translatable(
            "gui.factorycraft.lab_rate",
            state().getInt("Rate") / 100.0,
            state().getInt("EnergyRate") / 10000.0),
        8,
        217,
        0x80dddd,
        false);
    g.drawString(
        font,
        Component.translatable(
            "gui.factorycraft.lab_bonus",
            state().getInt("Productivity") / 100.0,
            state().getInt("Bonus") / 100.0),
        8,
        229,
        0xc5df8a,
        false);
    g.drawString(
        font,
        font.plainSubstrByWidth(
            Component.translatable("gui.factorycraft.lab_status_" + state().getString("Status"))
                .getString(),
            150),
        125,
        7,
        0x99bbcc,
        false);
    g.drawString(font, playerInventoryTitle, 190, 153, 0xffffff, false);
  }

  private void clampPan() {
    var entries = ResearchCatalog.get(menu.lab.getLevel()).entries();
    int maxX = entries.stream().mapToInt(e -> e.recipe().x() * 70 + 76).max().orElse(350),
        maxY = entries.stream().mapToInt(e -> e.recipe().y() * 24 + 24).max().orElse(75);
    panX = Math.clamp(panX, Math.min(0, 350 - maxX), 0);
    panY = Math.clamp(panY, Math.min(0, 75 - maxY), 0);
  }

  public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
    if (button == 1
        && mx >= leftPos + 5
        && mx < leftPos + 355
        && my >= topPos + 40
        && my < topPos + 115) {
      panX += (int) dx;
      panY += (int) dy;
      clampPan();
      return true;
    }
    return super.mouseDragged(mx, my, button, dx, dy);
  }

  public boolean mouseClicked(double mx, double my, int button) {
    if (button == 0 && hovered != null && my >= topPos + 40 && my < topPos + 115) {
      PacketDistributor.sendToServer(new ResearchNetwork.Select(menu.containerId, hovered));
      return true;
    }
    return super.mouseClicked(mx, my, button);
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
    renderTooltip(g, mx, my);
    if (hovered != null) {
      var e = ResearchCatalog.get(menu.lab.getLevel()).definition(hovered);
      var lines = new ArrayList<Component>();
      lines.add(name(hovered));
      if (!e.recipe().trigger().isEmpty())
        lines.add(
            Component.translatable(
                "gui.factorycraft.research_trigger", e.recipe().units(), e.recipe().trigger()));
      else lines.add(Component.literal(e.recipe().units() + " × " + e.recipe().ticks() + " t"));
      var node = state().getCompound("Nodes").getCompound(hovered.toString());
      if (node.getBoolean("Future"))
        lines.add(Component.translatable("gui.factorycraft.research_future"));
      else if (node.getBoolean("Disabled"))
        lines.add(Component.translatable("gui.factorycraft.research_invalid"));
      for (var p : e.recipe().packs().entrySet())
        lines.add(
            Component.literal(p.getValue() + " × ")
                .append(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(p.getKey())
                        .getDescription()));
      for (var p : e.recipe().prerequisites())
        lines.add(Component.translatable("gui.factorycraft.requires", name(p)));
      for (var id : e.recipe().unlocks())
        lines.add(
            Component.literal("→ ")
                .append(
                    Component.translatableWithFallback(
                        "recipe." + id.getNamespace() + "." + id.getPath(),
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                            ? net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .get(id)
                                .getDescription()
                                .getString()
                            : id.getPath().replace('_', ' '))));
      g.renderComponentTooltip(font, lines, mx, my);
    }
  }
}
