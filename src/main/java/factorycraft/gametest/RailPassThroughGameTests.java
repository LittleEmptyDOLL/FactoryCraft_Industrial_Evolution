package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.rail.automation.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

/** Finite actual movement, not a zero-duration station wait masquerading as passage. */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class RailPassThroughGameTests {
  @GameTest(template = "empty", batch = "rail_pass_forward", timeoutTicks = 1200)
  public static void consecutiveWaypointsInsideChainPassWithSpeedAndRetainTailClaims(
      GameTestHelper h) {
    run(h, 1210, false, false, false);
  }

  @GameTest(template = "empty", batch = "rail_pass_reverse", timeoutTicks = 1200)
  public static void rearLeadingLocomotivePassesWaypointWithoutReversingOrWaiting(
      GameTestHelper h) {
    run(h, 1211, true, false, false);
  }

  @GameTest(template = "empty", batch = "rail_pass_full", timeoutTicks = 1200)
  public static void fullContinuationBrakesAndKeepsActualOriginThenResumesOnOpening(
      GameTestHelper h) {
    run(h, 1212, false, true, false);
  }

  @GameTest(template = "empty", batch = "rail_pass_disk", timeoutTicks = 1600)
  public static void preparedPathAndFutureLeasesRestoreExactInIndependentJvmThenReallyPass(
      GameTestHelper h) {
    run(h, 1213, false, false, true);
  }

  private static List<RailSchedule.Entry> entries(boolean reverse, boolean full) {
    var rows = new ArrayList<RailSchedule.Entry>();
    rows.add(new RailSchedule.Entry("Waypoint", List.of()));
    if (!reverse && !full) rows.add(new RailSchedule.Entry("Waypoint two", List.of()));
    rows.add(
        new RailSchedule.Entry(
            "Terminal",
            List.of(
                new RailWait.Condition(
                    RailWait.Type.TIME, "", RailWait.Comparison.GE, 100000, false))));
    return rows;
  }

  private static ItemStack load() {
    var stack = new ItemStack(Items.IRON_INGOT, 7);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal("v0320-pass finite cargo"));
    return stack;
  }

  private static void run(
      GameTestHelper h, int arena, boolean reverse, boolean full, boolean persist) {
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var disk =
        persist
            ? h.getLevel()
                .getDataStorage()
                .computeIfAbsent(
                    new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                        RailAutomationGameTests.ServiceDisk::new,
                        RailAutomationGameTests.ServiceDisk::load),
                    "factorycraft_v0320_pass")
            : null;
    if (persist && disk.data.getInt("Stage") == 2) {
      h.assertTrue(
          disk.data.getBoolean("Replayed") && d.train(disk.data.getUUID("Train")) == null,
          "completed passage restored retired owner");
      h.succeed();
      return;
    }
    boolean replay = persist && disk.data.getInt("Stage") == 1;
    var devices = new ArrayList<RailAutomationData.Device>();
    TrainV31GameTests.Site site;
    TrainData.Train[] train = {null};
    if (replay) {
      train[0] = d.train(disk.data.getUUID("Train"));
      h.assertTrue(
          train[0] != null
              && disk.data
                  .getCompound("State")
                  .equals(RailSignalSafetyGameTests.saved(h, train[0].id)),
          "prepared train exact JVM replay");
      h.assertTrue(train[0].automation.upcoming.size() == 2, "saved waypoint window lost");
      for (var w : train[0].automation.upcoming)
        h.assertTrue(
            s.slots.planned(train[0].id, w.destination()) && s.slots.count(w.destination()) == 1,
            "future lease missing/duplicated on restart");
      var edge = RailwayData.get(h.getLevel()).segment(disk.data.getUUID("Edge"));
      h.assertTrue(edge != null, "paid approach missing on restart");
      var plan = new RailPlan(edge.curve);
      var player = RailwayV30GameTests.player(h, plan, true);
      // Same explicit diagnostic cleanup authority as other cross-JVM railway fixtures.
      // Fresh random test-player UUID must not bypass ordinary ownership checks in production.
      player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
      var pins = new HashSet<Long>();
      for (long pin : disk.data.getLongArray("Pins")) {
        pins.add(pin);
        h.getLevel().setChunkForced((int) pin, (int) (pin >> 32), true);
        h.getLevel().getChunk((int) pin, (int) (pin >> 32));
      }
      site = new TrainV31GameTests.Site(plan, edge.id, player, pins);
      for (var raw : disk.data.getList("Devices", Tag.TAG_COMPOUND))
        devices.add(s.device(((CompoundTag) raw).getUUID("Id")));
      disk.data.putBoolean("Replayed", true);
      disk.setDirty();
      System.out.println(
          "FACTORYCRAFT_V0320_PASS_DISK: VERIFIED exact owner/path/phase/2future leases");
    } else site = TrainV31GameTests.rail(h, arena);
    boolean[] done = {false}, passed = {false}, held = {false}, seeded = {false};
    int[] stopped = {0}, frozenTicks = {0};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              if (!replay) {
                devices.add(
                    RailAutomationGameTests.device(
                        h, site, TracksideKind.STOP, reverse ? 44 : 40, reverse, "Waypoint"));
                if (!reverse && !full)
                  devices.add(
                      RailAutomationGameTests.device(
                          h, site, TracksideKind.STOP, 48, false, "Waypoint two"));
                devices.add(
                    RailAutomationGameTests.device(
                        h, site, TracksideKind.STOP, reverse ? 12 : 84, reverse, "Terminal"));
                if (!reverse && !full) {
                  devices.add(
                      RailAutomationGameTests.device(
                          h, site, TracksideKind.CHAIN, 32, false, "Chain before waypoint"));
                  devices.add(
                      RailAutomationGameTests.device(
                          h, site, TracksideKind.SIGNAL, 56, false, "Safe exit"));
                }
                if (full) {
                  var terminal = devices.getLast();
                  var n = terminal.settings();
                  n.putInt("Limit", 0);
                  s.configure(terminal, n);
                }
                var loco =
                    TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, reverse ? 76 : 20);
                TrainV31GameTests.stock(h, site, StockSpec.CARGO, reverse ? 69 : 13);
                if (reverse) {
                  var rear = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 62);
                  d.car(rear.getUUID()).reversed = true;
                }
                train[0] = d.owner(loco.getUUID());
                for (var car : train[0].cars) {
                  if (car.kind == StockSpec.LOCOMOTIVE)
                    car.items.setStackInSlot(0, new ItemStack(Items.COAL, 16));
                  if (car.kind == StockSpec.CARGO) car.items.setStackInSlot(0, load());
                }
                train[0].automation.schedule = new RailSchedule(entries(reverse, full), List.of());
              }
              site.player()
                  .setPos(
                      site.plan().curve.start.x() + 10,
                      site.plan().curve.start.y() + 3,
                      site.plan().curve.start.z() + 10);
            })
        .thenIdle(15)
        .thenExecute(
            () -> {
              if (!replay)
                h.assertTrue(TrainAutomation.automatic(d, train[0]), "automatic waypoint mode");
              h.onEachTick(
                  () -> {
                    if (done[0]) return;
                    var t = train[0];
                    var a = t.automation;
                    if (persist && !replay) {
                      if (!seeded[0]) {
                        // No scheduling-map mutation from a running onEachTick callback.
                        TrainAutomation.prepare(d, t);
                        if (a.upcoming.size() != 2) return;
                        seeded[0] = true;
                        for (long pin : site.pins()) {
                          d.unloadChunk(pin);
                          h.getLevel().setChunkForced((int) pin, (int) (pin >> 32), false);
                        }
                        return;
                      }
                      if (++frozenTicks[0] < 50) return;
                      done[0] = true;
                      var n = disk.data;
                      n.putInt("Stage", 1);
                      n.putUUID("Train", t.id);
                      n.putUUID("Edge", site.edge());
                      n.put("State", RailSignalSafetyGameTests.saved(h, t.id));
                      n.putLongArray(
                          "Pins", site.pins().stream().mapToLong(Long::longValue).toArray());
                      var rows = new ListTag();
                      for (var device : devices) {
                        var row = new CompoundTag();
                        row.putUUID("Id", device.id);
                        rows.add(row);
                      }
                      n.put("Devices", rows);
                      disk.setDirty();
                      System.out.println(
                          "FACTORYCRAFT_V0320_PASS_DISK: SEEDED exact future owners frozen");
                      h.succeed();
                      return;
                    }
                    h.assertTrue(
                        a.phase != AutoTrainState.Phase.INVALID_CONFIG, "waypoint runtime invalid");
                    if (!full && a.entry > 0 && !passed[0]) {
                      h.assertTrue(
                          t.speed > 0 && !a.arrived && a.stationTicks == 0,
                          "waypoint artificially stopped or became station wait");
                      passed[0] = true;
                      h.assertTrue(
                          s.slots.count(devices.getFirst().id) == 0, "passed incoming slot leaked");
                      if (!reverse) {
                        h.assertTrue(t.rear() < 32, "fixture did not straddle chain at passage");
                        h.assertTrue(
                            !a.reserved.isEmpty(), "passage surrendered entered chain exit claims");
                        held[0] = true;
                      }
                    }
                    if (full
                        && a.departure != null
                        && a.phase == AutoTrainState.Phase.DESTINATION_FULL) {
                      h.assertTrue(
                          t.speed == 0 && s.slots.count(devices.getFirst().id) == 1 && a.entry == 1,
                          "blocked waypoint lost actual origin");
                      h.assertTrue(
                          TrainAutomation.station(t, s).equals(devices.getFirst().id),
                          "blocked origin cargo unavailable");
                      if (++stopped[0] == 25) {
                        held[0] = true;
                        var target = devices.getLast();
                        var n = target.settings();
                        n.putInt("Limit", 1);
                        s.configure(target, n);
                      }
                    }
                    if (!a.arrived) return;
                    h.assertTrue(
                        (full || passed[0])
                            && (reverse || held[0])
                            && t.speed == 0
                            && a.entry == a.schedule.entries().size() - 1,
                        "final genuine station arrival");
                    long budget = reverse ? 128_000_000L : 64_000_000L, balance = 0, spent = 0;
                    for (var car : t.cars) {
                      if (car.kind == StockSpec.LOCOMOTIVE) {
                        balance +=
                            car.fuelJ
                                + car.spentJ
                                + 4_000_000L * car.items.getStackInSlot(0).getCount();
                        spent += car.spentJ;
                      }
                      if (car.kind == StockSpec.CARGO)
                        h.assertTrue(
                            ItemStack.matches(load(), car.items.getStackInSlot(0)),
                            "typed cargo changed");
                    }
                    h.assertTrue(balance == budget && spent > 0, "finite fuel paid exactly once");
                    done[0] = true;
                    TrainAutomation.manual(d, t);
                    TrainV31GameTests.cleanup(h, site);
                    RailAutomationGameTests.removeDevices(h, site, devices);
                    if (persist) {
                      disk.data.putInt("Stage", 2);
                      disk.setDirty();
                    }
                    System.out.println(
                        "FACTORYCRAFT_V0320_PASS: PASS reverse="
                            + reverse
                            + " full="
                            + full
                            + " replay="
                            + replay
                            + " budget="
                            + budget
                            + " spent="
                            + spent);
                    h.succeed();
                  });
            });
  }

  @GameTest(template = "empty", batch = "rail_pass_edit", timeoutTicks = 250)
  public static void scheduleMenuAllowsWaypointsButRejectsStaleEditsAndRenameClearsFutureOnly(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 1214);
    var d = TrainData.get(h.getLevel());
    TrainData.Train[] train = {null};
    RailAutomationData.Device[] stops = {null, null};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              stops[0] =
                  RailAutomationGameTests.device(
                      h, site, TracksideKind.STOP, 40, false, "Waypoint");
              stops[1] =
                  RailAutomationGameTests.device(
                      h, site, TracksideKind.STOP, 84, false, "Terminal");
              var loco = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 20);
              var t = train[0] = d.owner(loco.getUUID());
              var player = site.player();
              player.setPos(loco.getX() + 2, loco.getY(), loco.getZ());
              var menu =
                  new RailAutomationMenu(
                      33, player.getInventory(), t.id, true, player.blockPosition());
              var edit = new CompoundTag();
              edit.putString("Action", "schedule");
              var schedule = new RailSchedule(entries(false, true), List.of());
              edit.put("Schedule", RailScheduleCodec.save(schedule));
              h.assertTrue(!menu.edit(player, edit), "stale menu accepted waypoint edit");
              var old = player.containerMenu;
              player.containerMenu = menu;
              h.assertTrue(
                  menu.edit(player, edit) && t.automation.schedule.equals(schedule),
                  "current menu rejected no-wait schedule");
              player.setPos(loco.getX() + 20, loco.getY(), loco.getZ());
              h.assertTrue(!menu.edit(player, edit), "remote waypoint edit accepted");
              player.containerMenu = old;
              player.setPos(loco.getX() + 10, loco.getY() + 3, loco.getZ() + 10);
            })
        .thenIdle(15)
        .thenExecute(
            () -> {
              var t = train[0];
              var one = stops[0];
              var end = stops[1];
              h.assertTrue(TrainAutomation.automatic(d, t), "waypoint mode");
              boolean[] done = {false};
              h.onEachTick(
                  () -> {
                    if (done[0] || t.automation.upcoming.isEmpty()) return;
                    var before =
                        RailSignalSafetyGameTests.saved(h, t.id)
                            .getList("Cars", Tag.TAG_COMPOUND)
                            .copy();
                    var s = RailAutomationData.get(h.getLevel());
                    var n = end.settings();
                    n.putString("Name", "Renamed terminal");
                    s.configure(end, n);
                    h.assertTrue(
                        t.automation.upcoming.isEmpty()
                            && s.slots.count(end.id) == 0
                            && one.id.equals(t.automation.destination)
                            && s.slots.count(one.id) == 1,
                        "rename did not selectively invalidate future commitment");
                    TrainAutomation.manual(d, t);
                    h.assertTrue(
                        s.slots.count(one.id) == 0
                            && s.slots.count(end.id) == 0
                            && before.equals(
                                RailSignalSafetyGameTests.saved(h, t.id)
                                    .getList("Cars", Tag.TAG_COMPOUND)),
                        "manual waypoint cancellation changed resources or leaked slots");
                    done[0] = true;
                    TrainV31GameTests.cleanup(h, site);
                    RailAutomationGameTests.removeDevices(h, site, List.of(one, end));
                    h.succeed();
                  });
            });
  }

  @GameTest(template = "empty", batch = "rail_pass_metadata", timeoutTicks = 20)
  public static void waypointMetadataRejectsMalformedOrderAndPreservesDefaultFormat(
      GameTestHelper h) {
    var a = new AutoTrainState();
    a.schedule = new RailSchedule(entries(false, false), List.of());
    a.automatic = a.routed = true;
    a.destination = UUID.randomUUID();
    a.targetHead = 32;
    a.upcoming.add(new AutoTrainState.Waypoint(1, UUID.randomUUID(), 40));
    a.upcoming.add(new AutoTrainState.Waypoint(2, UUID.randomUUID(), 76));
    var saved = a.save();
    h.assertTrue(AutoTrainState.load(saved).save().equals(saved), "canonical waypoint codec");
    saved.getList("Upcoming", Tag.TAG_COMPOUND).getCompound(0).putDouble("Head", 31);
    h.assertTrue(
        AutoTrainState.load(saved).invalid != null, "malformed waypoint should quarantine");
    var normal = new AutoTrainState().save();
    h.assertTrue(
        !normal.contains("Upcoming")
            && !normal.contains("WaypointDirection")
            && !normal.contains("DeparturePass"),
        "default record gained tags");
    h.succeed();
  }
}
