package factorycraft.client.screens;

import factorycraft.blockentity.MachineBlockEntity;
import factorycraft.blocks.FactoryKind;
import factorycraft.recipes.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Readonly recipe cards backed by the synced RecipeManager; selection is validated by the server.
 */
public final class RecipeSelectionScreen extends Screen {
  private record Entry(
      ResourceLocation id,
      String group,
      List<FactoryRecipe.CountedIngredient> inputs,
      List<FluidStack> fluids,
      List<ItemStack> outputs,
      List<FluidStack> fluidOutputs,
      int ticks,
      int energy) {
    Component name() {
      Component fallback =
          !outputs.isEmpty()
              ? outputs.getFirst().getHoverName()
              : fluidOutputs.isEmpty()
                  ? Component.literal(id.toString())
                  : fluidOutputs.getFirst().getHoverName();
      return Component.translatableWithFallback(
          "recipe." + id.getNamespace() + "." + id.getPath(), fallback.getString());
    }
  }

  private final Screen parent;
  private final MachineBlockEntity machine;
  private final int menu;
  private EditBox search;
  private final List<Entry> all = new ArrayList<>();
  private List<Entry> filtered = List.of();
  private int page, group, visibleRows = 7, imageH = 252;
  private Entry focused;
  private RecipeSelectionNetwork.State lastState;
  private long recipeEpoch = RecipeSelectionNetwork.generation;
  private final List<Button> rows = new ArrayList<>();
  private Button choose;
  private int x, y;

  public RecipeSelectionScreen(Screen parent, MachineBlockEntity machine, int menu) {
    super(Component.translatable("gui.factorycraft.recipe_browser"));
    this.parent = parent;
    this.machine = machine;
    this.menu = menu;
  }

  public static void open(Screen parent, MachineBlockEntity machine, int menu) {
    RecipeSelectionNetwork.clientState =
        new RecipeSelectionNetwork.State(-1, new net.minecraft.nbt.CompoundTag());
    net.minecraft.client.Minecraft.getInstance()
        .setScreen(new RecipeSelectionScreen(parent, machine, menu));
    PacketDistributor.sendToServer(new RecipeSelectionNetwork.Request(menu, false, null));
  }

  protected void init() {
    imageH = Math.min(252, height - 8);
    visibleRows = Math.max(3, (imageH - 98) / 22);
    x = (width - 370) / 2;
    y = (height - imageH) / 2;
    all.clear();
    rows.clear();
    if (machine.petroleum() != null)
      for (var h : machine.petroleum().recipes()) {
        var r = h.value();
        var fluids = new ArrayList<FluidStack>();
        for (var i : r.fluids()) {
          var fs = i.getFluids();
          if (fs.length > 0) fluids.add(fs[0]);
        }
        all.add(
            new Entry(
                h.id(),
                "chemistry",
                r.items(),
                fluids,
                r.results(),
                r.fluidResults(),
                r.ticks(),
                r.energy()));
      }
    else
      for (var h : machine.recipes()) {
        var r = h.value();
        all.add(
            new Entry(
                h.id(),
                machine.nativeSmelting(h.id()) ? "smelting" : "processing",
                r.inputs(),
                List.of(),
                List.of(r.result()),
                List.of(),
                machine.kind() == FactoryKind.INDUSTRIAL_FURNACE
                    ? Math.ceilDiv(r.ticks(), 2)
                    : r.ticks(),
                r.energy() * (machine.kind() == FactoryKind.INDUSTRIAL_FURNACE ? 2 : 1)));
      }
    search =
        addRenderableWidget(
            new EditBox(
                font, x + 8, y + 25, 220, 18, Component.translatable("gui.factorycraft.search")));
    search.setMaxLength(128);
    search.setResponder(
        v -> {
          page = 0;
          filter();
        });
    addRenderableWidget(
        Button.builder(
                Component.translatable("gui.factorycraft.group"),
                b -> {
                  group = (group + 1) % 4;
                  page = 0;
                  filter();
                })
            .bounds(x + 235, y + 24, 127, 20)
            .build());
    for (int i = 0; i < visibleRows; i++) {
      final int slot = i;
      rows.add(
          addRenderableWidget(
              Button.builder(
                      Component.empty(),
                      b -> {
                        int at = page * visibleRows + slot;
                        if (at < filtered.size()) focused = filtered.get(at);
                      })
                  .bounds(x + 8, y + 50 + i * 22, 174, 21)
                  .build()));
    }
    addRenderableWidget(
        Button.builder(
                Component.literal("<"),
                b -> {
                  page = Math.max(0, page - 1);
                  refresh();
                })
            .bounds(x + 8, y + imageH - 45, 26, 18)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal(">"),
                b -> {
                  if ((page + 1) * visibleRows < filtered.size()) page++;
                  refresh();
                })
            .bounds(x + 156, y + imageH - 45, 26, 18)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.factorycraft.recipe_auto"), b -> send(null))
            .bounds(x + 8, y + imageH - 22, 80, 18)
            .build());
    choose =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("gui.factorycraft.recipe_choose"),
                    b -> {
                      if (focused != null) send(focused.id);
                    })
                .bounds(x + 190, y + imageH - 22, 82, 18)
                .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.back"), b -> onClose())
            .bounds(x + 280, y + imageH - 22, 82, 18)
            .build());
    filter();
  }

  private boolean allowed(Entry e) {
    var state = RecipeSelectionNetwork.clientState;
    return state.menu() == menu && state.data().getBoolean(e.id.toString());
  }

  private void send(ResourceLocation id) {
    PacketDistributor.sendToServer(new RecipeSelectionNetwork.Request(menu, true, id));
  }

  private void filter() {
    String q = search.getValue().toLowerCase(Locale.ROOT);
    filtered =
        all.stream()
            .filter(
                e ->
                    group == 0
                        || group == 1 && e.group.equals("processing")
                        || group == 2 && e.group.equals("chemistry")
                        || group == 3 && e.group.equals("smelting"))
            .filter(
                e ->
                    e.id.toString().contains(q)
                        || e.name().getString().toLowerCase(Locale.ROOT).contains(q))
            .toList();
    refresh();
  }

  private void refresh() {
    for (int i = 0; i < rows.size(); i++) {
      int at = page * visibleRows + i;
      var b = rows.get(i);
      b.visible = at < filtered.size();
      if (b.visible) {
        var e = filtered.get(at);
        b.setMessage(
            Component.literal(
                font.plainSubstrByWidth(e.name().getString(), 120) + (allowed(e) ? "" : " 🔒")));
      }
    }
  }

  public void tick() {
    if (recipeEpoch != RecipeSelectionNetwork.generation) {
      open(parent, machine, menu);
      return;
    }
    if (minecraft.player == null
        || minecraft.player.containerMenu.containerId != menu
        || !machine.loaded() && !machine.getLevel().isClientSide) {
      onClose();
      return;
    }
    var state = RecipeSelectionNetwork.clientState;
    if (lastState != state) {
      lastState = state;
      refresh();
    }
    choose.active = focused != null && allowed(focused);
    if (state.menu() == menu
        && focused != null
        && focused.id.toString().equals(state.data().getString("Selected")))
      choose.setMessage(Component.translatable("gui.factorycraft.recipe_chosen"));
    else choose.setMessage(Component.translatable("gui.factorycraft.recipe_choose"));
  }

  @Override
  public void onClose() {
    minecraft.setScreen(
        minecraft.player != null && minecraft.player.containerMenu.containerId == menu
            ? parent
            : null);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
    int p =
        Math.clamp(
            page + (vertical < 0 ? 1 : -1), 0, Math.max(0, (filtered.size() - 1) / visibleRows));
    if (p != page) {
      page = p;
      refresh();
      return true;
    }
    return super.mouseScrolled(mx, my, horizontal, vertical);
  }

  private void text(GuiGraphics g, Component c, int px, int py, int w, int color) {
    g.drawString(font, font.plainSubstrByWidth(c.getString(), w), px, py, color, false);
  }

  @Override
  public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
    super.renderBackground(g, mx, my, partial);
    g.fill(x, y, x + 370, y + imageH, 0xff202933);
    g.fill(x + 186, y + 48, x + 362, y + imageH - 27, 0xff303c46);
    text(g, title, x + 8, y + 8, 350, 0xfff4bb55);
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    // Screen.render invokes renderBackground before its widgets in Minecraft 1.21.1.
    super.render(g, mx, my, partial);
    for (int i = 0; i < rows.size(); i++)
      if (rows.get(i).visible) {
        var e = filtered.get(page * visibleRows + i);
        if (!e.outputs.isEmpty()) g.renderFakeItem(e.outputs.getFirst(), x + 11, y + 52 + i * 22);
        else if (!e.fluidOutputs.isEmpty()) {
          var bucket = e.fluidOutputs.getFirst().getFluid().getBucket();
          g.renderFakeItem(
              new ItemStack(
                  bucket == net.minecraft.world.item.Items.AIR
                      ? net.minecraft.world.item.Items.BUCKET
                      : bucket),
              x + 11,
              y + 52 + i * 22);
        }
      }
    text(
        g,
        Component.literal(
            (page + 1) + " / " + Math.max(1, (filtered.size() + visibleRows - 1) / visibleRows)),
        x + 65,
        y + imageH - 41,
        90,
        0xffe1e8eb);
    var state = RecipeSelectionNetwork.clientState;
    if (state.menu() != menu)
      text(
          g,
          Component.translatable("gui.factorycraft.recipe_wait"),
          x + 192,
          y + 53,
          160,
          0xffefc55b);
    else
      text(
          g,
          Component.translatable("gui.factorycraft.recipe_group." + group),
          x + 192,
          y + 53,
          160,
          0xffa5c1cd);
    if (focused == null) return;
    var e = focused;
    text(g, e.name(), x + 192, y + 69, 164, 0xffffffff);
    text(g, Component.literal(e.id.toString()), x + 192, y + 81, 164, 0xff9babb5);
    int line = y + 97;
    for (var i : e.inputs) {
      var samples = i.ingredient().getItems();
      if (samples.length == 0) continue;
      var stack = samples[0].copyWithCount(i.count());
      g.renderFakeItem(stack, x + 192, line);
      g.renderItemDecorations(font, stack, x + 192, line);
      text(g, stack.getHoverName(), x + 212, line + 4, 140, 0xffd8e2e6);
      if (mx >= x + 192 && mx < x + 358 && my >= line && my < line + 17)
        g.renderTooltip(font, stack, mx, my);
      line += 18;
      if (line > y + 170) break;
    }
    for (var f : e.fluids) {
      text(
          g,
          Component.literal(f.getAmount() + " mB ").append(f.getHoverName()),
          x + 192,
          line,
          164,
          0xff8dd8ee);
      line += 11;
    }
    line = Math.max(line, y + Math.min(164, imageH - 88));
    for (var out : e.outputs) {
      g.renderFakeItem(out, x + 192, line);
      g.renderItemDecorations(font, out, x + 192, line);
      text(g, out.getHoverName(), x + 212, line + 4, 140, 0xffffd27e);
      line += 18;
    }
    for (var f : e.fluidOutputs) {
      text(
          g,
          Component.literal("→ " + f.getAmount() + " mB ").append(f.getHoverName()),
          x + 192,
          line,
          164,
          0xffffd27e);
      line += 11;
    }
    text(
        g,
        Component.translatable("gui.factorycraft.recipe_info", e.ticks, e.energy),
        x + 192,
        y + imageH - 47,
        164,
        0xffbeced7);
    text(
        g,
        Component.translatable(
            allowed(e) ? "gui.factorycraft.recipe_unlocked" : "gui.factorycraft.needs_research"),
        x + 192,
        y + imageH - 36,
        164,
        allowed(e) ? 0xff80dba1 : 0xfff1ac62);
  }
}
