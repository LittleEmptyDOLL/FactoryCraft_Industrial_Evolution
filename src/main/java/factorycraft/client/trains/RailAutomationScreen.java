package factorycraft.client.trains;

import factorycraft.rail.automation.*;
import factorycraft.simulation.rail.*;
import factorycraft.trains.RailScheduleCodec;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native bounded editor. No resource slots and no pretend groups/interrupt controls. */
public final class RailAutomationScreen extends AbstractContainerScreen<RailAutomationMenu> {
  private final ArrayList<RailSchedule.Entry> entries = new ArrayList<>();
  private CompoundTag settings;
  private EditBox name, value, filter, limit, priority;
  private int selected, condition, type, comparison;
  private boolean or;
  private String error = "";

  public RailAutomationScreen(RailAutomationMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 340;
    imageHeight = 270;
    settings = menu.initial.copy();
    if (menu.train)
      entries.addAll(RailScheduleCodec.load(menu.initial.getCompound("Schedule")).entries());
  }

  private net.minecraft.network.chat.MutableComponent text(String key) {
    return Component.translatable("rail.factorycraft." + key);
  }

  private Button button(String key, int x, int y, int width, Runnable run) {
    return addRenderableWidget(
        Button.builder(text(key), b -> run.run())
            .bounds(leftPos + x, topPos + y, width, 20)
            .build());
  }

  private EditBox box(int x, int y, int width, int max, String initial) {
    var box =
        addRenderableWidget(
            new EditBox(font, leftPos + x, topPos + y, width, 18, Component.empty()));
    box.setMaxLength(max);
    box.setValue(initial);
    return box;
  }

  protected void init() {
    super.init();
    titleLabelX = 10;
    if (menu.train) {
      button("previous", 10, 31, 42, () -> select(-1));
      button("next", 55, 31, 42, () -> select(1));
      button(
          "add_stop",
          101,
          31,
          87,
          () -> {
            if (entries.size() < 64) {
              entries.add(
                  new RailSchedule.Entry(
                      name.getValue().isBlank() ? "Station" : name.getValue(), List.of(delay())));
              selected = entries.size() - 1;
              condition = 0;
              rebuild();
            }
          });
      button(
          "remove",
          192,
          31,
          64,
          () -> {
            if (!entries.isEmpty()) {
              entries.remove(selected);
              selected = Math.max(0, selected - 1);
              condition = 0;
              rebuild();
            }
          });
      button("up", 260, 31, 32, () -> move(-1));
      button("down", 296, 31, 32, () -> move(1));
      name =
          box(100, 61, 225, 128, entries.isEmpty() ? "Station" : entries.get(selected).station());
      button(
          "set_name",
          10,
          61,
          85,
          () -> {
            if (!entries.isEmpty() && !name.getValue().isBlank()) {
              var old = entries.get(selected);
              entries.set(selected, new RailSchedule.Entry(name.getValue(), old.waits()));
            }
          });
      var waits = entries.isEmpty() ? List.<RailWait.Condition>of() : entries.get(selected).waits();
      if (condition >= waits.size()) condition = 0;
      if (!waits.isEmpty()) {
        var c = waits.get(condition);
        type = c.type().ordinal();
        comparison = c.comparison().ordinal();
        or = c.or();
      }
      var kind = RailWait.Type.values()[type];
      button(
          "condition_previous",
          10,
          96,
          42,
          () -> {
            if (!waits.isEmpty()) {
              condition = Math.floorMod(condition - 1, waits.size());
              rebuild();
            }
          });
      button(
          "condition_next",
          55,
          96,
          42,
          () -> {
            if (!waits.isEmpty()) {
              condition = (condition + 1) % waits.size();
              rebuild();
            }
          });
      var typeButton =
          button(
              "type",
              101,
              96,
              154,
              () -> {
                type = (type + 1) % RailWait.Type.values().length;
                refreshType();
              });
      typeButton.setMessage(text("wait_" + kind.name().toLowerCase(Locale.ROOT)));
      var comparisonButton =
          button(
              "compare",
              260,
              96,
              67,
              () -> {
                comparison = (comparison + 1) % RailWait.Comparison.values().length;
                refreshComparison();
              });
      comparisonButton.setMessage(
          Component.literal(RailWait.Comparison.values()[comparison].name()));
      filter = box(10, 141, 211, 256, waits.isEmpty() ? "" : waits.get(condition).filter());
      long v = waits.isEmpty() ? 0 : waits.get(condition).value();
      if (kind == RailWait.Type.TIME || kind == RailWait.Type.INACTIVITY) v /= 20;
      value = box(230, 141, 97, 16, Long.toString(v));
      var logic =
          button(
              "and",
              10,
              165,
              64,
              () -> {
                or = !or;
                refreshLogic();
              });
      logic.setMessage(text(or ? "or" : "and"));
      button("add_condition", 78, 165, 116, () -> changeCondition(true));
      button("set_condition", 198, 165, 129, () -> changeCondition(false));
      button(
          "remove_condition",
          10,
          189,
          150,
          () -> {
            if (!entries.isEmpty() && !waits.isEmpty()) {
              var list = new ArrayList<>(waits);
              list.remove(condition);
              entries.set(selected, new RailSchedule.Entry(name.getValue(), list));
              condition = 0;
              rebuild();
            }
          });
      button(
          "save_schedule",
          10,
          239,
          150,
          () -> {
            var n = new CompoundTag();
            n.putString("Action", "schedule");
            n.put("Schedule", RailScheduleCodec.save(new RailSchedule(entries, List.of())));
            send(n);
          });
      button(
          "mode",
          170,
          239,
          157,
          () -> {
            var n = new CompoundTag();
            n.putString("Action", "mode");
            send(n);
          });
    } else if (!settings.getString("Kind").equals("STOP")) {
      button(
          "circuit",
          10,
          100,
          317,
          () -> {
            var n = new CompoundTag();
            n.putString("Action", "circuit");
            send(n);
          });
    } else {
      name = box(110, 42, 217, 128, settings.getString("Name"));
      limit = box(110, 74, 90, 6, Integer.toString(settings.getInt("Limit")));
      priority = box(235, 74, 92, 3, Integer.toString(settings.getInt("Priority")));
      button(
          "enabled",
          10,
          111,
          150,
          () -> {
            settings.putBoolean("Enabled", !settings.getBoolean("Enabled"));
            rebuild();
          });
      button(
          "color",
          170,
          111,
          157,
          () -> {
            int color = settings.getInt("Color");
            settings.putInt(
                "Color", color == 0x54a58d ? 0xd8a329 : color == 0xd8a329 ? 0x476caa : 0x54a58d);
          });
      button(
          "circuit_limit",
          10,
          143,
          150,
          () -> {
            settings.putBoolean("CircuitLimit", !settings.getBoolean("CircuitLimit"));
            rebuild();
          });
      button(
          "circuit_priority",
          170,
          143,
          157,
          () -> {
            settings.putBoolean("CircuitPriority", !settings.getBoolean("CircuitPriority"));
            rebuild();
          });
      button(
          "circuit",
          10,
          175,
          317,
          () -> {
            var n = new CompoundTag();
            n.putString("Action", "circuit");
            send(n);
          });
      button(
          "save_device",
          10,
          239,
          317,
          () -> {
            try {
              settings.putString("Name", name.getValue());
              settings.putInt("Limit", Integer.parseInt(limit.getValue()));
              settings.putInt("Priority", Integer.parseInt(priority.getValue()));
              var n = new CompoundTag();
              n.putString("Action", "device");
              n.put("Settings", settings);
              send(n);
            } catch (IllegalArgumentException e) {
              error = "invalid";
            }
          });
    }
  }

  private RailWait.Condition delay() {
    return new RailWait.Condition(RailWait.Type.TIME, "", RailWait.Comparison.GE, 100, false);
  }

  private void select(int direction) {
    if (!entries.isEmpty()) {
      selected = Math.floorMod(selected + direction, entries.size());
      condition = 0;
      rebuild();
    }
  }

  private void move(int direction) {
    int at = selected + direction;
    if (at >= 0 && at < entries.size()) {
      Collections.swap(entries, selected, at);
      selected = at;
      rebuild();
    }
  }

  private void rebuild() {
    if (!menu.train && name != null) {
      settings.putString("Name", name.getValue());
      try {
        settings.putInt("Limit", Integer.parseInt(limit.getValue()));
        settings.putInt("Priority", Integer.parseInt(priority.getValue()));
      } catch (NumberFormatException invalid) {
        error = "invalid";
      }
    }
    clearWidgets();
    init();
  }

  private void refreshType() {
    for (var widget : children())
      if (widget instanceof Button b && b.getX() == leftPos + 101 && b.getY() == topPos + 96)
        b.setMessage(text("wait_" + RailWait.Type.values()[type].name().toLowerCase(Locale.ROOT)));
  }

  private void refreshComparison() {
    for (var widget : children())
      if (widget instanceof Button b && b.getX() == leftPos + 260 && b.getY() == topPos + 96)
        b.setMessage(Component.literal(RailWait.Comparison.values()[comparison].name()));
  }

  private void refreshLogic() {
    for (var widget : children())
      if (widget instanceof Button b && b.getX() == leftPos + 10 && b.getY() == topPos + 165)
        b.setMessage(text(or ? "or" : "and"));
  }

  private void changeCondition(boolean add) {
    if (entries.isEmpty()) return;
    try {
      var kind = RailWait.Type.values()[type];
      long amount = Long.parseLong(value.getValue());
      if (kind == RailWait.Type.TIME || kind == RailWait.Type.INACTIVITY)
        amount = Math.multiplyExact(amount, 20);
      var c =
          new RailWait.Condition(
              kind, filter.getValue(), RailWait.Comparison.values()[comparison], amount, or);
      var list = new ArrayList<>(entries.get(selected).waits());
      if (add) {
        if (list.size() >= 32) return;
        list.add(c);
        condition = list.size() - 1;
      } else if (!list.isEmpty()) list.set(condition, c);
      else return;
      entries.set(selected, new RailSchedule.Entry(name.getValue(), list));
      rebuild();
      error = "";
    } catch (IllegalArgumentException | ArithmeticException invalid) {
      error = "invalid";
    }
  }

  private void send(CompoundTag n) {
    String text = n.toString();
    if (text.length() > 16384) {
      error = "too_large";
      return;
    }
    PacketDistributor.sendToServer(new RailAutomationNetwork.Edit(menu.containerId, text));
    error = "sent";
  }

  protected void renderBg(GuiGraphics g, float partial, int x, int y) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff17242c);
    g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xff324752);
  }

  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 10, 10, 0xf1dab1, false);
    if (menu.train) {
      g.drawString(
          font,
          text("entry").append(" " + (selected + 1) + " / " + entries.size()),
          10,
          20,
          0xffffff,
          false);
      if (!entries.isEmpty() && entries.get(selected).waits().isEmpty())
        g.drawString(font, text("pass_through"), 168, 195, 0x8ee1be, false);
      g.drawString(font, text("filter_hint"), 10, 130, 0xccd8df, false);
      g.drawString(font, text("units"), 10, 215, 0xccd8df, false);
      int phase =
          Math.clamp(
              menu.state.get(1), 0, factorycraft.trains.AutoTrainState.Phase.values().length - 1);
      g.drawString(
          font,
          text("phase_"
                  + factorycraft.trains.AutoTrainState.Phase.values()[phase]
                      .name()
                      .toLowerCase(Locale.ROOT))
              .append(" | " + menu.state.get(5) + " km/h"),
          10,
          226,
          0x8ee1be,
          false);
    } else if (!settings.getString("Kind").equals("STOP")) {
      int aspect =
          Math.clamp(
              menu.state.get(0),
              0,
              factorycraft.simulation.rail.RailSignals.Aspect.values().length - 1);
      g.drawString(
          font,
          text(
              "aspect_"
                  + factorycraft.simulation.rail.RailSignals.Aspect.values()[aspect]
                      .name()
                      .toLowerCase(Locale.ROOT)),
          10,
          45,
          0xffffff,
          false);
      g.drawString(font, text("signal_controls"), 10, 70, 0xccd8df, false);
    } else {
      g.drawString(font, text("name"), 10, 46, 0xffffff, false);
      g.drawString(font, text("limit_priority"), 10, 96, 0xffffff, false);
      g.drawString(
          font, text(settings.getBoolean("Enabled") ? "on" : "off"), 10, 204, 0x8ee1be, false);
      g.drawString(
          font,
          Component.literal(
                  "L="
                      + settings.getBoolean("CircuitLimit")
                      + "  P="
                      + settings.getBoolean("CircuitPriority")
                      + "  ")
              .append(text("incoming"))
              .append(": " + menu.state.get(1)),
          10,
          217,
          0xffffff,
          false);
    }
    if (!error.isEmpty())
      g.drawString(
          font,
          text(
              error.equals("sent") && menu.state.get(6) != 0
                  ? (menu.state.get(6) > 0 ? "accepted" : "rejected")
                  : error),
          210,
          20,
          0xffd070,
          false);
  }

  public void render(GuiGraphics g, int x, int y, float partial) {
    super.render(g, x, y, partial);
    renderTooltip(g, x, y);
  }
}
