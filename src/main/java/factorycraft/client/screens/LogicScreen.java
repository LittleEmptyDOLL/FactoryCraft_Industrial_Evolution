package factorycraft.client.screens;

import factorycraft.circuits.*;
import factorycraft.simulation.circuits.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Structured editing, with an in-screen signal picker so changing a signal never closes its menu.
 */
public final class LogicScreen extends AbstractContainerScreen<LogicMenu> {
  private CompoundTag draft = new CompoundTag();
  private int tab, page, section;
  private boolean dirty, waiting, rejected;
  private String editor = "";
  private CompoundTag editing;
  private Consumer<SignalKey> picker;
  private SignalKey.Type pickType = SignalKey.Type.ITEM;
  private List<SignalKey> choices = List.of(), filteredChoices = List.of();
  private String cachedQuery;
  private List<SignalKey> cachedChoices;
  private String query = "";
  private int pickPage;
  private Button save;

  public LogicScreen(LogicMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 330;
    imageHeight = 233;
    inventoryLabelY = 201;
  }

  private static Component tr(String id, Object... args) {
    return Component.translatable("gui.factorycraft.logic_" + id, args);
  }

  private CompoundTag settings() {
    return draft.getCompound("Settings");
  }

  private CompoundTag program() {
    return draft.getCompound("Program");
  }

  private Button button(int x, int y, int w, Component label, Runnable run) {
    return addRenderableWidget(
        Button.builder(label, b -> run.run()).bounds(leftPos + x, topPos + y, w, 18).build());
  }

  private void changed() {
    dirty = true;
    rejected = false;
  }

  private void toggle(CompoundTag tag, String field) {
    tag.putBoolean(field, !tag.getBoolean(field));
    changed();
    rebuild();
  }

  private void cycle(CompoundTag tag, String field, String[] values) {
    int i = Arrays.asList(values).indexOf(tag.getString(field));
    tag.putString(field, values[(i + 1) % values.length]);
    changed();
    rebuild();
  }

  private String[] modes(boolean first) {
    return first
        ? new String[] {"SIGNAL", "CONSTANT", "EACH", "ANYTHING", "EVERYTHING"}
        : new String[] {"SIGNAL", "CONSTANT", "EACH"};
  }

  private void number(CompoundTag tag, String key, int x, int y, int w) {
    var box = new EditBox(font, leftPos + x, topPos + y, w, 18, Component.empty());
    box.setValue(Integer.toString(tag.getInt(key)));
    box.setMaxLength(11);
    box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?[0-9]{1,10}"));
    box.setResponder(
        s -> {
          try {
            tag.putInt(key, Integer.parseInt(s));
            changed();
          } catch (NumberFormatException ignored) {
          }
        });
    addRenderableWidget(box);
  }

  private void key(CompoundTag tag, int x, int y, int w) {
    SignalKey signal;
    try {
      signal = SignalProgramData.key(tag);
    } catch (RuntimeException e) {
      signal = SignalKey.A;
    }
    var current = signal;
    button(
        x,
        y,
        w,
        SignalIcons.name(current),
        () ->
            pick(
                current.type(),
                chosen -> {
                  tag.putString("Type", chosen.type().name());
                  tag.putString("Id", chosen.id());
                  changed();
                }));
  }

  protected void init() {
    super.init();
    if (draft.isEmpty() && !menu.clientState.isEmpty()) {
      draft.put("Settings", menu.clientState.getCompound("Settings").copy());
      draft.put("Program", menu.clientState.getCompound("Program").copy());
    }
    rebuild();
  }

  private void rebuild() {
    clearWidgets();
    if (draft.isEmpty()) return;
    if (picker != null) {
      pickerWidgets();
      return;
    }
    if (!editor.isEmpty()) {
      editorWidgets();
      return;
    }
    for (int i = 0; i < 4; i++) {
      final int selected = i;
      var b =
          button(
              8 + i * 79,
              25,
              76,
              tr("tab." + i),
              () -> {
                tab = selected;
                page = 0;
                rebuild();
              });
      b.active = tab != i;
    }
    save = button(242, 210, 80, tr(waiting ? "waiting" : "save"), this::submit);
    save.active = !waiting;
    if (tab == 0) deviceWidgets();
    else if (tab == 1) colorWidgets();
    else if (tab == 2) programWidgets();
    else signalWidgets();
  }

  private void submit() {
    if (waiting) return;
    PacketDistributor.sendToServer(
        new CircuitNetwork.Configure(menu.containerId, menu.identity, draft.copy()));
    waiting = true;
    rebuild();
  }

  private void deviceWidgets() {
    if (menu.clientState.getBoolean("Combinator")) {
      button(
          8,
          52,
          312,
          tr("publish", program().getBoolean("Enabled") ? "✓" : "−"),
          () -> toggle(program(), "Enabled"));
      return;
    }
    String[] fields = {
      "Contents",
      "State",
      "Ingredients",
      "Results",
      "Research",
      "Gate",
      "Filters",
      "Hand",
      "Recipe",
      "SelectResearch",
      "Pulse"
    };
    for (int i = 0; i < fields.length; i++) {
      String field = fields[i];
      boolean available =
          switch (field) {
            case "Contents" -> menu.clientState.getBoolean("CanRead");
            case "State" -> menu.clientState.getBoolean("CanState");
            case "Ingredients", "Results" -> menu.clientState.getBoolean("CanIngredients");
            case "Research", "SelectResearch" -> menu.clientState.getBoolean("CanResearch");
            case "Gate" -> menu.clientState.getBoolean("CanGate");
            case "Filters", "Hand" -> menu.clientState.getBoolean("CanFilter");
            case "Recipe" -> menu.clientState.getBoolean("CanRecipe");
            case "Pulse" -> menu.clientState.getBoolean("CanPulse");
            default -> true;
          };
      var b =
          button(
              8 + (i / 6) * 159,
              49 + (i % 6) * 23,
              153,
              tr("option." + field, settings().getBoolean(field) ? "✓" : "−"),
              () -> toggle(settings(), field));
      b.active = available;
    }
    if (menu.clientState.getBoolean("CanResearch"))
      button(
                  8,
                  187,
                  312,
                  tr(
                      menu.clientState.getBoolean("ResearchControlled")
                          ? "research_circuit"
                          : "research_manual"),
                  () -> {})
              .active =
          false;
    if (menu.clientState.getBoolean("CanFilter"))
      button(
          167,
          187,
          153,
          tr("hand_signal"),
          () ->
              pick(
                  SignalKey.Type.VIRTUAL,
                  k -> {
                    settings().put("HandSignal", SignalProgramData.key(k));
                    changed();
                  }));
  }

  private void colorWidgets() {
    String[] fields = {"InputColors", "OutputColors"};
    String[] labels = {"R", "G", "B", "W"};
    for (int row = 0; row < 2; row++) {
      String field = fields[row];
      for (int c = 0; c < 4; c++) {
        int mask = 1 << c;
        var b =
            button(
                95 + c * 52,
                62 + row * 63,
                45,
                Component.literal(
                    ((settings().getInt(field) & mask) != 0 ? "✓ " : "− ") + labels[c]),
                () -> {
                  settings().putInt(field, settings().getInt(field) ^ mask);
                  changed();
                  rebuild();
                });
        b.setTooltip(Tooltip.create(tr("color." + c)));
      }
    }
  }

  private ListTag rows(String name) {
    return program().getList(name, 10);
  }

  private void programWidgets() {
    if (!menu.clientState.getBoolean("Combinator")) {
      ruleList(settings(), "Conditions", true);
      return;
    }
    String kind = program().getString("Kind");
    switch (kind) {
      case "CONSTANT" -> constantRows();
      case "ARITHMETIC" -> arithmetic();
      case "DECIDER" -> {
        for (int i = 0; i < 3; i++) {
          int selected = i;
          button(
              8 + i * 105,
              49,
              101,
              tr("rules." + i),
              () -> {
                section = selected;
                page = 0;
                rebuild();
              });
        }
        ruleList(program(), new String[] {"Conditions", "Outputs", "Else"}[section], section == 0);
      }
      case "SELECTOR" -> selector();
      default -> {}
    }
  }

  private void pages(int count, Runnable add) {
    button(
        8,
        184,
        30,
        Component.literal("<"),
        () -> {
          page = Math.max(0, page - 1);
          rebuild();
        });
    button(
        43,
        184,
        30,
        Component.literal(">"),
        () -> {
          page = Math.min(Math.max(0, (count - 1) / 4), page + 1);
          rebuild();
        });
    button(80, 184, 103, tr("add"), add);
  }

  private void constantRows() {
    var rows = rows("Rows");
    int start = page * 4;
    for (int i = 0; i < 4 && start + i < rows.size(); i++) {
      int index = start + i, y = 53 + i * 30;
      var row = rows.getCompound(index);
      key(row, 8, y, 145);
      number(row, "Value", 159, y, 59);
      button(
          224,
          y,
          34,
          Component.literal("G" + row.getInt("Group")),
          () -> {
            row.putInt("Group", (row.getInt("Group") + 1) % 16);
            changed();
            rebuild();
          });
      button(
          263,
          y,
          25,
          Component.literal(row.getBoolean("Enabled") ? "✓" : "−"),
          () -> toggle(row, "Enabled"));
      button(
          293,
          y,
          27,
          Component.literal("×"),
          () -> {
            rows.remove(index);
            changed();
            rebuild();
          });
    }
    pages(
        rows.size(),
        () -> {
          if (rows.size() >= 64) return;
          var row = SignalProgramData.key(SignalKey.A);
          row.putInt("Value", 1);
          row.putBoolean("Enabled", true);
          rows.add(row);
          program().put("Rows", rows);
          changed();
          page = (rows.size() - 1) / 4;
          rebuild();
        });
    button(
        192,
        184,
        128,
        tr("publish", program().getBoolean("Enabled") ? "✓" : "−"),
        () -> toggle(program(), "Enabled"));
  }

  private void arithmetic() {
    operand(program().getCompound("Left"), 52, false);
    button(
        8,
        80,
        312,
        tr("operation." + program().getString("Arithmetic")),
        () ->
            cycle(
                program(),
                "Arithmetic",
                Arrays.stream(SignalMath.Arithmetic.values())
                    .map(Enum::name)
                    .toArray(String[]::new)));
    operand(program().getCompound("Right"), 110, false);
    button(8, 144, 82, tr("output"), () -> {}).active = false;
    key(program().getCompound("Result"), 96, 144, 136);
    boolean each =
        program().getCompound("Left").getString("Mode").equals("EACH")
            || program().getCompound("Right").getString("Mode").equals("EACH");
    button(
        238,
        144,
        82,
        tr("mode." + program().getCompound("Result").getString("Mode")),
        () ->
            cycle(
                program().getCompound("Result"),
                "Mode",
                each ? new String[] {"SIGNAL", "EACH"} : new String[] {"SIGNAL"}));
  }

  private void operand(CompoundTag op, int y, boolean condition) {
    String[] available =
        condition
            ? modes(true)
            : op == program().getCompound("Selector").getCompound("Index")
                ? new String[] {"SIGNAL", "CONSTANT"}
                : new String[] {"SIGNAL", "CONSTANT", "EACH"};
    button(8, y, 90, tr("mode." + op.getString("Mode")), () -> cycle(op, "Mode", available));
    if (op.getString("Mode").equals("CONSTANT")) number(op, "Value", 104, y, 126);
    else key(op, 104, y, 126);
    colorButtons(op, "Colors", 236, y, 84);
  }

  private void colorButtons(CompoundTag tag, String field, int x, int y, int width) {
    for (int c = 0; c < 4; c++) {
      int color = c, mask = 1 << c;
      boolean checked = (tag.getInt(field) & mask) != 0;
      var label =
          Component.literal((width / 4 >= 35 ? (checked ? "✓ " : "− ") : "") + "RGBW".charAt(c))
              .withStyle(
                  checked
                      ? net.minecraft.ChatFormatting.AQUA
                      : net.minecraft.ChatFormatting.DARK_GRAY);
      button(
              x + c * width / 4,
              y,
              width / 4 - 1,
              label,
              () -> {
                tag.putInt(field, tag.getInt(field) ^ mask);
                changed();
                rebuild();
              })
          .setTooltip(Tooltip.create(tr("color." + color)));
    }
  }

  private String colorName(int mask) {
    var s = new StringBuilder();
    for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0) s.append("RGBW".charAt(i));
    return s.isEmpty() ? "∅" : s.toString();
  }

  private void ruleList(CompoundTag parent, String field, boolean conditions) {
    var list = parent.getList(field, 10);
    int start = page * 4;
    for (int i = 0; i < 4 && start + i < list.size(); i++) {
      int index = start + i, y = 76 + i * 25;
      var row = list.getCompound(index);
      Component label =
          conditions
              ? Component.literal(
                  (row.getBoolean("Or") ? "OR " : "AND ")
                      + operandName(row.getCompound("Left"))
                      + " "
                      + compare(row.getString("Compare"))
                      + " "
                      + operandName(row.getCompound("Right")))
              : Component.literal(outputName(row));
      button(
          8,
          y,
          277,
          label,
          () -> {
            editing = row;
            editor = conditions ? "condition" : "output";
            rebuild();
          });
      button(
          291,
          y,
          29,
          Component.literal("×"),
          () -> {
            list.remove(index);
            changed();
            rebuild();
          });
    }
    pages(
        list.size(),
        () -> {
          if (list.size() >= 16) return;
          CompoundTag row;
          if (conditions) {
            row = new CompoundTag();
            row.put("Left", SignalProgramData.operand(SignalProgram.Operand.signal(SignalKey.A)));
            row.put("Right", SignalProgramData.operand(SignalProgram.Operand.number(0)));
            row.putString("Compare", "GREATER");
          } else row = SignalProgramData.output(SignalProgram.Output.single(SignalKey.A, 1));
          list.add(row);
          parent.put(field, list);
          changed();
          editing = row;
          editor = conditions ? "condition" : "output";
          rebuild();
        });
  }

  private String operandName(CompoundTag t) {
    if (t.getString("Mode").equals("CONSTANT")) return Integer.toString(t.getInt("Value"));
    if (!t.getString("Mode").equals("SIGNAL")) return tr("mode." + t.getString("Mode")).getString();
    try {
      return SignalIcons.name(SignalProgramData.key(t)).getString();
    } catch (RuntimeException e) {
      return "?";
    }
  }

  private String outputName(CompoundTag row) {
    return operandName(row)
        + " ← "
        + (row.getBoolean("Count") ? tr("input_count").getString() : row.getInt("Value"));
  }

  private String compare(String value) {
    return switch (value) {
      case "GREATER" -> ">";
      case "LESS" -> "<";
      case "NOT_EQUAL" -> "≠";
      case "AT_LEAST" -> "≥";
      case "AT_MOST" -> "≤";
      default -> "=";
    };
  }

  private void comparison(CompoundTag t, int y) {
    button(
        8,
        y,
        153,
        Component.literal(compare(t.getString("Compare"))),
        () ->
            cycle(
                t,
                "Compare",
                Arrays.stream(SignalMath.Compare.values()).map(Enum::name).toArray(String[]::new)));
  }

  private void editorWidgets() {
    button(
        8,
        25,
        312,
        tr("back"),
        () -> {
          editor = "";
          changed();
          rebuild();
        });
    if (editor.equals("condition")) {
      operand(editing.getCompound("Left"), 57, true);
      comparison(editing, 86);
      button(
          167,
          86,
          153,
          Component.literal(editing.getBoolean("Or") ? "OR" : "AND"),
          () -> toggle(editing, "Or"));
      operand(editing.getCompound("Right"), 117, false);
    } else {
      key(editing, 8, 58, 221);
      String[] modes =
          hasEach()
              ? new String[] {"SIGNAL", "EACH", "ANYTHING"}
              : new String[] {"SIGNAL", "EVERYTHING", "ANYTHING"};
      button(
          235,
          58,
          85,
          tr("mode." + editing.getString("Mode")),
          () -> cycle(editing, "Mode", modes));
      button(
          8,
          88,
          153,
          tr(editing.getBoolean("Count") ? "input_count" : "constant"),
          () -> toggle(editing, "Count"));
      number(editing, "Value", 168, 88, 152);
      colorButtons(editing, "Colors", 8, 118, 312);
    }
  }

  private boolean hasEach() {
    return program().getList("Conditions", 10).stream()
        .map(t -> (CompoundTag) t)
        .anyMatch(
            t ->
                t.getCompound("Left").getString("Mode").equals("EACH")
                    || t.getCompound("Right").getString("Mode").equals("EACH"));
  }

  private void selector() {
    var s = program().getCompound("Selector");
    button(
        8,
        50,
        312,
        tr("selector." + s.getString("Mode")),
        () -> cycle(s, "Mode", new String[] {"SELECT", "COUNT", "RANDOM", "STACK_SIZE", "TIME"}));
    switch (s.getString("Mode")) {
      case "SELECT" -> {
        operand(s.getCompound("Index"), 78, false);
        comparison(s, 108);
        button(
            167,
            108,
            153,
            tr(s.getBoolean("Highest") ? "highest" : "lowest"),
            () -> toggle(s, "Highest"));
      }
      case "COUNT" -> key(s.getCompound("Count"), 8, 85, 312);
      case "RANDOM" -> number(s, "Interval", 104, 85, 126);
      case "TIME" -> {
        key(s.getCompound("Tick"), 104, 78, 216);
        key(s.getCompound("Day"), 104, 106, 216);
        key(s.getCompound("Length"), 104, 134, 216);
      }
      default -> {}
    }
  }

  private void signalWidgets() {
    int current = menu.clientState.getInt("Page");
    button(8, 184, 45, Component.literal("<"), () -> inspect(Math.max(0, current - 1)));
    button(59, 184, 45, Component.literal(">"), () -> inspect(current + 1));
  }

  private void inspect(int page) {
    var tag = new CompoundTag();
    tag.putInt("InspectPage", page);
    PacketDistributor.sendToServer(
        new CircuitNetwork.Configure(menu.containerId, menu.identity, tag));
  }

  private void pick(SignalKey.Type type, Consumer<SignalKey> target) {
    picker = target;
    pickType = type;
    query = "";
    pickPage = 0;
    choices = SignalIcons.choices(type);
    rebuild();
  }

  private void choose(SignalKey key) {
    var callback = picker;
    picker = null;
    callback.accept(key);
    rebuild();
  }

  private List<SignalKey> filtered() {
    if (choices == cachedChoices && query.equals(cachedQuery)) return filteredChoices;
    cachedChoices = choices;
    cachedQuery = query;
    String q = query.toLowerCase(Locale.ROOT);
    filteredChoices =
        choices.stream()
            .filter(
                k ->
                    q.isEmpty()
                        || k.id().toLowerCase(Locale.ROOT).contains(q)
                        || SignalIcons.name(k).getString().toLowerCase(Locale.ROOT).contains(q))
            .toList();
    return filteredChoices;
  }

  private void pickerWidgets() {
    button(
        8,
        25,
        120,
        tr("type." + pickType.name()),
        () -> {
          pickType =
              SignalKey.Type.values()[(pickType.ordinal() + 1) % SignalKey.Type.values().length];
          choices = SignalIcons.choices(pickType);
          query = "";
          pickPage = 0;
          rebuild();
        });
    var search = new EditBox(font, leftPos + 135, topPos + 25, 185, 18, tr("search"));
    search.setValue(query);
    search.setMaxLength(128);
    search.setResponder(
        s -> {
          query = s;
          pickPage = 0;
        });
    addRenderableWidget(search);
    for (int i = 0; i < 6; i++) {
      int row = i;
      button(
          8,
          51 + row * 23,
          312,
          Component.empty(),
          () -> {
            var list = filtered();
            int index = pickPage * 6 + row;
            if (index < list.size()) choose(list.get(index));
          });
    }
    button(
        8,
        193,
        35,
        Component.literal("<"),
        () -> {
          pickPage = Math.max(0, pickPage - 1);
        });
    button(
        49,
        193,
        35,
        Component.literal(">"),
        () -> {
          pickPage = Math.min(Math.max(0, (filtered().size() - 1) / 6), pickPage + 1);
        });
    button(
        90,
        193,
        108,
        tr("use_id"),
        () -> {
          String id = query.contains(":") ? query : "factorycraft:" + query;
          try {
            choose(new SignalKey(pickType, id));
          } catch (IllegalArgumentException ignored) {
          }
        });
    button(
        204,
        193,
        116,
        tr("back"),
        () -> {
          picker = null;
          rebuild();
        });
  }

  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff233541);
    g.fill(leftPos + 5, topPos + 46, leftPos + 325, topPos + 207, 0xff15232d);
    if (picker == null && editor.isEmpty())
      for (var slot : menu.slots) {
        g.fill(
            leftPos + slot.x - 1,
            topPos + slot.y - 1,
            leftPos + slot.x + 17,
            topPos + slot.y + 17,
            0xff718591);
        g.fill(
            leftPos + slot.x,
            topPos + slot.y,
            leftPos + slot.x + 16,
            topPos + slot.y + 16,
            0xff14222b);
      }
  }

  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 8, 8, 0xffffff, false);
    g.drawString(
        font,
        tr(
            rejected
                ? "rejected"
                : waiting
                    ? "waiting"
                    : dirty
                        ? "modified"
                        : menu.clientState.getBoolean("Limited")
                            ? "limited"
                            : menu.clientState.getBoolean("Ready") ? "ready" : "unknown"),
        220,
        8,
        rejected ? 0xff8c70 : 0xa0e0dd,
        false);
    if (picker != null) {
      var list = filtered();
      int visible = 0;
      for (var widget : children())
        if (widget instanceof Button b && b.getY() >= topPos + 51 && b.getY() < topPos + 190) {
          int index = pickPage * 6 + visible++;
          b.setMessage(index < list.size() ? SignalIcons.name(list.get(index)) : Component.empty());
          b.active = index < list.size();
        }
      return;
    }
    if (!editor.isEmpty()) return;
    if (tab == 1) {
      g.drawString(font, tr("input_colors"), 8, 68, 0xffffff, false);
      g.drawString(font, tr("output_colors"), 8, 131, 0xffffff, false);
      g.drawWordWrap(font, tr("mask_hint"), 8, 166, 308, 0x9ac9d1);
    }
    if (tab == 2 && !menu.clientState.getBoolean("Combinator"))
      g.drawString(font, tr("gate_rules"), 8, 54, 0xffffff, false);
    if (tab == 2
        && program().getString("Kind").equals("SELECTOR")
        && menu.clientState.getBoolean("Combinator")) {
      var mode = program().getCompound("Selector").getString("Mode");
      if (mode.equals("TIME")) {
        g.drawString(font, tr("game_tick"), 8, 83, 0xffffff, false);
        g.drawString(font, tr("day_tick"), 8, 111, 0xffffff, false);
        g.drawString(font, tr("day_length"), 8, 139, 0xffffff, false);
      }
      if (mode.equals("RANDOM")) g.drawString(font, tr("interval"), 8, 90, 0xffffff, false);
      g.drawWordWrap(font, tr("quality_later"), 8, 161, 308, 0x9ac9d1);
    }
    if (tab == 3) {
      g.drawString(font, tr("input"), 8, 50, 0xffffff, false);
      g.drawString(font, tr("output"), 167, 50, 0xffffff, false);
      for (int col = 0; col < 2; col++) {
        var list = menu.clientState.getList(col == 0 ? "Input" : "Output", 10);
        for (int i = 0; i < Math.min(6, list.size()); i++) {
          var row = list.getCompound(i);
          String label;
          try {
            label = SignalIcons.name(SignalProgramData.key(row)).getString();
          } catch (RuntimeException bad) {
            label = "?";
          }
          String value = Integer.toString(row.getInt("Value"));
          int valueX = 155 + 159 * col - font.width(value);
          int labelWidth = Math.max(24, valueX - (8 + 159 * col) - 8);
          if (font.width(label) > labelWidth)
            label = font.plainSubstrByWidth(label, labelWidth - font.width("…")) + "…";
          g.drawString(font, label, 8 + 159 * col, 67 + i * 17, 0xb4dfe4, false);
          g.drawString(font, value, valueX, 67 + i * 17, 0xffffff, false);
        }
      }
      g.drawString(
          font,
          tr(
              "page",
              menu.clientState.getInt("Page") + 1,
              menu.clientState.getInt("InputCount"),
              menu.clientState.getInt("OutputCount")),
          111,
          189,
          0xffffff,
          false);
    }
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    if (menu.lastAccepted != null) {
      boolean accepted = menu.lastAccepted;
      menu.lastAccepted = null;
      if (waiting) {
        waiting = false;
        dirty = false;
        rejected = !accepted;
        draft.put("Settings", menu.clientState.getCompound("Settings").copy());
        draft.put("Program", menu.clientState.getCompound("Program").copy());
        rebuild();
      }
    }
    super.render(g, mx, my, partial);
    renderTooltip(g, mx, my);
  }

  @Override
  public boolean keyPressed(int key, int scan, int mods) {
    if (getFocused() instanceof EditBox box && box.isFocused() && key != 256)
      return box.keyPressed(key, scan, mods);
    if (key == 256 && picker != null) {
      picker = null;
      rebuild();
      return true;
    }
    if (key == 256 && !editor.isEmpty()) {
      editor = "";
      rebuild();
      return true;
    }
    return super.keyPressed(key, scan, mods);
  }
}
