package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.rail.automation.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;

/** Real finite station contention; only search work allowance differs, never movement rules. */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class RailDispatchGameTests {
  @GameTest(template = "empty", batch = "rail_dispatch_priority", timeoutTicks = 2000)
  public static void changedOriginPriorityWinsDespiteSlowerRealPathSearch(GameTestHelper h) {
    contention(h, 1200, false, false);
  }

  @GameTest(template = "empty", batch = "rail_dispatch_fifo_disk", timeoutTicks = 2000)
  public static void fullDestinationOriginLeasesAndFifoRestoreThenDispatchInIndependentJvm(
      GameTestHelper h) {
    contention(h, 1201, true, false);
  }

  @GameTest(template = "empty", batch = "rail_dispatch_no_path", timeoutTicks = 2000)
  public static void unreachableHighPriorityOriginCannotStarveReachableLowerTrain(
      GameTestHelper h) {
    contention(h, 1202, false, true);
  }

  private static RailAutomationGameTests.ServiceDisk disk(GameTestHelper h, String name) {
    return h.getLevel()
        .getDataStorage()
        .computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                RailAutomationGameTests.ServiceDisk::new,
                RailAutomationGameTests.ServiceDisk::load),
            name);
  }

  private record Fixture(
      List<TrainV31GameTests.Site> sites, List<RailAutomationData.Device> devices) {}

  private static Fixture reload(GameTestHelper h, CompoundTag n) {
    var rails = RailwayData.get(h.getLevel());
    var system = RailAutomationData.get(h.getLevel());
    var sites = new ArrayList<TrainV31GameTests.Site>();
    var first = rails.segment(n.getList("Edges", Tag.TAG_COMPOUND).getCompound(0).getUUID("Id"));
    h.assertTrue(first != null, "saved paid approach missing");
    var player = RailwayV30GameTests.player(h, new RailPlan(first.curve), true);
    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    var pins = new HashSet<Long>();
    for (long pin : n.getLongArray("Pins")) pins.add(pin);
    for (var raw : n.getList("Edges", Tag.TAG_COMPOUND)) {
      var id = ((CompoundTag) raw).getUUID("Id");
      h.assertTrue(rails.segment(id) != null, "saved paid rail identity missing");
      sites.add(
          new TrainV31GameTests.Site(new RailPlan(rails.segment(id).curve), id, player, pins));
    }
    var devices = new ArrayList<RailAutomationData.Device>();
    for (var raw : n.getList("Devices", Tag.TAG_COMPOUND)) {
      var row = (CompoundTag) raw;
      var device = system.device(row.getUUID("Id"));
      h.assertTrue(
          device != null && device.settings().equals(row.getCompound("Settings")),
          "saved physical stop/signal settings changed");
      devices.add(device);
    }
    for (long pin : pins) {
      h.getLevel().setChunkForced((int) pin, (int) (pin >> 32), true);
      h.getLevel().getChunk((int) pin, (int) (pin >> 32));
    }
    player.setPos(
        first.curve.start.x() + 15, first.curve.start.y() + 3, first.curve.start.z() + 12);
    return new Fixture(sites, devices);
  }

  private static void freeze(
      GameTestHelper h,
      RailAutomationGameTests.ServiceDisk disk,
      Fixture fixture,
      TrainData.Train... trains) {
    var d = TrainData.get(h.getLevel());
    for (long pin : fixture.sites().getFirst().pins()) {
      d.unloadChunk(pin);
      h.getLevel().setChunkForced((int) pin, (int) (pin >> 32), false);
    }
    h.runAfterDelay(
        50,
        () -> {
          var n = disk.data;
          n.putInt("Stage", 1);
          for (int i = 0; i < trains.length; i++) {
            n.putUUID("Train" + i, trains[i].id);
            n.put("State" + i, RailSignalSafetyGameTests.saved(h, trains[i].id));
          }
          var edges = new ListTag();
          for (var site : fixture.sites()) {
            var row = new CompoundTag();
            row.putUUID("Id", site.edge());
            edges.add(row);
          }
          n.put("Edges", edges);
          var devices = new ListTag();
          for (var device : fixture.devices()) {
            var row = new CompoundTag();
            row.putUUID("Id", device.id);
            row.put("Settings", device.settings());
            devices.add(row);
          }
          n.put("Devices", devices);
          n.putLongArray(
              "Pins",
              fixture.sites().getFirst().pins().stream().mapToLong(Long::longValue).toArray());
          disk.setDirty();
          System.out.println(
              "FACTORYCRAFT_V0320_DISPATCH_DISK: SEEDED trains="
                  + trains.length
                  + " exactOriginPoseAndOrder=true");
          h.succeed();
        });
  }

  private static void settings(
      RailAutomationData s, RailAutomationData.Device stop, int priority, int limit) {
    var n = stop.settings();
    n.putInt("Priority", priority);
    n.putInt("Limit", limit);
    s.configure(stop, n);
  }

  private static void schedule(TrainData d, TrainData.Train t, String origin, String target) {
    t.automation.schedule =
        new RailSchedule(
            List.of(
                new RailSchedule.Entry(
                    origin,
                    List.of(
                        new RailWait.Condition(
                            RailWait.Type.TIME, "", RailWait.Comparison.GE, 1, false))),
                new RailSchedule.Entry(
                    target,
                    List.of(
                        new RailWait.Condition(
                            RailWait.Type.TIME, "", RailWait.Comparison.GE, 100000, false)))),
            List.of());
    if (!TrainAutomation.automatic(d, t))
      throw new IllegalStateException("native scheduled dispatch");
  }

  private static void contention(GameTestHelper h, int arena, boolean persist, boolean noPath) {
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var checkpoint = persist ? disk(h, "factorycraft_v0320_dispatch_fifo") : null;
    if (persist && checkpoint.data.getInt("Stage") == 2) {
      h.assertTrue(
          checkpoint.data.getBoolean("Replayed")
              && d.train(checkpoint.data.getUUID("Train0")) == null
              && d.train(checkpoint.data.getUUID("Train1")) == null,
          "completed FIFO restored old owners");
      h.succeed();
      return;
    }
    boolean replay = persist && checkpoint.data.getInt("Stage") == 1;
    var sites = new ArrayList<TrainV31GameTests.Site>();
    var devices = new ArrayList<RailAutomationData.Device>();
    var fixture = new Fixture(sites, devices);
    TrainData.Train[] t = {null, null};
    RailAutomationData.Device[] origins = {null, null}, target = {null};
    int[] stage = {0}, winner = {0};
    boolean[] fastFinishedFirst = {false}, firstArrived = {false}, done = {false};
    boolean[] sawNoPath = {false}, unloaded = {false};
    boolean[] revalidated = {false};
    TrainRouteJob[] invalidatedJob = {null};
    int[] frozenTicks = {0};
    long[] originalOrders = {0, 0};
    CompoundTag[] frozen = {null, null};
    if (replay) {
      for (int i = 0; i < 2; i++) {
        t[i] = d.train(checkpoint.data.getUUID("Train" + i));
        h.assertTrue(
            t[i] != null
                && checkpoint
                    .data
                    .getCompound("State" + i)
                    .equals(RailSignalSafetyGameTests.saved(h, t[i].id)),
            "independent JVM changed pending owner/phase/cargo/fuel/order");
        origins[i] = s.device(checkpoint.data.getUUID("Origin" + i));
        h.assertTrue(
            origins[i].id.equals(s.slots.origin(t[i].id))
                && s.slots.destination(t[i].id) == null
                && s.slots.count(origins[i].id) == 1,
            "pending departure lease missing/duplicated after restart");
      }
      h.assertTrue(
          t[0].automation.dispatchOrder > 0
              && t[1].automation.dispatchOrder > 0
              && t[0].automation.dispatchOrder != t[1].automation.dispatchOrder,
          "saved FIFO order missing");
      target[0] = s.device(checkpoint.data.getUUID("Target"));
      h.assertTrue(s.slots.count(target[0].id) == 0, "full target got a phantom incoming lease");
      var restored = reload(h, checkpoint.data);
      sites.addAll(restored.sites());
      devices.addAll(restored.devices());
      checkpoint.data.putBoolean("Replayed", true);
      checkpoint.setDirty();
      System.out.println(
          "FACTORYCRAFT_V0320_DISPATCH_FIFO: VERIFIED exact two pending owners, origins=1/1"
              + " target=0 orders="
              + t[0].automation.dispatchOrder
              + "/"
              + t[1].automation.dispatchOrder);
    } else {
      var base = TrainV31GameTests.rail(h, arena);
      sites.add(base);
      var p = base.plan().curve.end;
      sites.add(
          RailSignalSafetyGameTests.extend(
              h, base, new RailEnd(p.x() + 32, p.y(), p.z() + 48, new RailHeading(0)), p));
      sites.add(
          RailSignalSafetyGameTests.extend(
              h, base, p, new RailEnd(p.x(), p.y(), p.z() - 96, new RailHeading(0))));
      h.startSequence()
          .thenIdle(12)
          .thenExecute(
              () -> {
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h,
                        base,
                        noPath ? TracksideKind.SIGNAL : TracksideKind.CHAIN,
                        72,
                        noPath,
                        "First approach boundary"));
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h,
                        sites.get(1),
                        TracksideKind.CHAIN,
                        sites.get(1).plan().curve.length() - 12,
                        false,
                        "Second approach chain"));
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h, sites.get(2), TracksideKind.SIGNAL, 32, false, "Shared safe exit"));
                for (int i = 0; i < 2; i++) {
                  origins[i] =
                      RailSignalSafetyGameTests.device(
                          h, sites.get(i), TracksideKind.STOP, 30, false, "Origin " + i);
                  settings(s, origins[i], persist ? 50 : i == 0 ? 20 : 200, 1);
                  devices.add(origins[i]);
                  t[i] = RailSignalSafetyGameTests.train(h, sites.get(i), 3, 20, false);
                }
                target[0] =
                    RailSignalSafetyGameTests.device(
                        h, sites.get(2), TracksideKind.STOP, 84, false, "Contested unload");
                settings(s, target[0], 50, 0);
                devices.add(target[0]);
                base.player().setPos(p.x() + 16, p.y() + 3, p.z() + 12);
              })
          .thenIdle(15)
          .thenExecute(
              () -> {
                for (int i = 0; i < 2; i++) schedule(d, t[i], "Origin " + i, "Contested unload");
              });
    }
    h.onEachTick(
        () -> {
          if (done[0]
              || t[0] == null
              || stage[0] == -1
              || stage[0] == 0 && !t[0].automation.automatic) return;
          if (stage[0] == -2) {
            for (int i = 0; i < 2; i++) {
              long key = new net.minecraft.world.level.ChunkPos(origins[i].anchor).toLong();
              if (h.getLevel().getChunkSource().getChunkNow((int) key, (int) (key >> 32)) != null
                  || TrainWorld.available(d, t[i])) return;
            }
            for (int i = 0; i < 2; i++) {
              h.assertTrue(
                  s.dispatch.get(t[i].id) == null, "unloaded request retained dispatch priority");
              var actual = RailSignalSafetyGameTests.saved(h, t[i].id);
              if (frozen[i] == null) frozen[i] = actual;
              h.assertTrue(
                  frozen[i].equals(actual),
                  "unloaded pending departure changed resources/pose/order");
            }
            if (++frozenTicks[0] < 20) return;
            for (long pin : sites.getFirst().pins()) {
              h.getLevel().setChunkForced((int) pin, (int) (pin >> 32), true);
              h.getLevel().getChunk((int) pin, (int) (pin >> 32));
            }
            stage[0] = -3;
            return;
          }
          if (stage[0] == -3) {
            for (int i = 0; i < 2; i++)
              if (!TrainWorld.available(d, t[i])
                  || !s.runtimeReady(t[i])
                  || !s.online(origins[i].point)
                  || s.dispatch.get(t[i].id) == null) return;
            for (int i = 0; i < 2; i++)
              h.assertTrue(
                  d.stopped(t[i]) && t[i].automation.dispatchOrder == originalOrders[i],
                  "reloaded idle origin stayed unavailable or lost FIFO order");
            unloaded[0] = true;
            System.out.println(
                "FACTORYCRAFT_V0320_DISPATCH_UNLOAD: PASS actualOriginChunksUnloaded=true"
                    + " frozenTicks=20 orderAndPortsRestored=true");
            stage[0] = 0;
            return;
          }
          if (d.train(t[0].id) != null && d.train(t[1].id) != null)
            RailSignalSafetyGameTests.disjoint(h, d, t[0], t[1]);
          if (stage[0] == 0) {
            for (int i = 0; i < 2; i++)
              if (!origins[i].id.equals(t[i].automation.departure)
                  || !TrainWorld.available(d, t[i])
                  || !s.runtimeReady(t[i])) return;
            for (int i = 0; i < 2; i++) {
              h.assertTrue(
                  t[i].speed == 0
                      && s.slots.count(origins[i].id) == 1
                      && s.slots.destination(t[i].id) == null
                      && origins[i].id.equals(TrainAutomation.station(t[i], s))
                      && !s.slots.available(origins[i].id, UUID.randomUUID()),
                  "Destination full freed actual docked origin");
            }
            if (persist && !replay) {
              for (int i = 0; i < 2; i++) checkpoint.data.putUUID("Origin" + i, origins[i].id);
              checkpoint.data.putUUID("Target", target[0].id);
              stage[0] = -1;
              freeze(h, checkpoint, fixture, t);
              return;
            }
            if (!persist && !noPath && !unloaded[0]) {
              for (int i = 0; i < 2; i++) originalOrders[i] = t[i].automation.dispatchOrder;
              for (long pin : sites.getFirst().pins()) {
                d.unloadChunk(pin);
                h.getLevel().setChunkForced((int) pin, (int) (pin >> 32), false);
              }
              stage[0] = -2;
              return;
            }
            if (persist)
              winner[0] = t[0].automation.dispatchOrder < t[1].automation.dispatchOrder ? 0 : 1;
            else {
              settings(s, origins[0], 200, 1);
              settings(s, origins[1], 20, 1);
              winner[0] = 0; // changed source priority must beat age and active iteration order
            }
            settings(s, target[0], 50, 1);
            if (!noPath) {
              var high = t[winner[0]];
              high.automation.routeJob =
                  new TrainRouteJob(
                      d,
                      high,
                      s,
                      List.of(new RailPathSearch.Target(target[0].id, target[0].point, 50)),
                      1);
              high.automation.phase = AutoTrainState.Phase.DISPATCHING;
            }
            stage[0] = 1;
            return;
          }
          int expected = noPath ? 1 : winner[0];
          sawNoPath[0] |= t[0].automation.phase == AutoTrainState.Phase.NO_PATH;
          int other = 1 - expected;
          if (!noPath
              && t[other].automation.routeJob != null
              && t[other].automation.routeJob.done
              && t[expected].automation.routeJob != null
              && !t[expected].automation.routeJob.done) fastFinishedFirst[0] = true;
          if (!persist && !noPath && fastFinishedFirst[0] && !revalidated[0]) {
            invalidatedJob[0] = t[expected].automation.routeJob;
            var changed = origins[expected].settings();
            changed.putInt("Color", changed.getInt("Color") ^ 1);
            s.configure(origins[expected], changed);
            // The lower-priority search starts on the new rules immediately. The higher
            // pending job must retain its rank even while its old frontier is invalidated.
            h.assertTrue(
                TrainAutomation.automatic(d, t[other])
                    && t[other].automation.routeJob == null
                    && t[other].automation.dispatchTarget == null,
                "repeated Automatic action retained an old selected route cache");
            revalidated[0] = true;
          }
          if (!firstArrived[0]) {
            if (s.slots.destination(t[other].id) != null && !noPath)
              h.assertTrue(
                  !target[0].id.equals(s.slots.destination(t[other].id)),
                  "short lower-ranked search stole station slot");
            if (!t[expected].automation.arrived) return;
            h.assertTrue(
                noPath || fastFinishedFirst[0],
                "unequal cooperative search duration not exercised");
            if (!persist && !noPath)
              h.assertTrue(
                  revalidated[0] && invalidatedJob[0].stale,
                  "priority-preserving stale frontier replacement was not exercised");
            h.assertTrue(
                s.slots.count(origins[expected].id) == 0 && s.slots.count(target[0].id) == 1,
                "real departure did not release only origin");
            RailSignalSafetyGameTests.ledger(h, t[expected]);
            RailSignalSafetyGameTests.pack(h, t[expected], sites.get(expected));
            firstArrived[0] = true;
            if (noPath) {
              h.assertTrue(
                  sawNoPath[0] && s.slots.count(origins[0].id) == 1,
                  "unreachable origin was not safely retained");
              RailSignalSafetyGameTests.pack(h, t[0], sites.getFirst());
            } else return;
          } else {
            if (!t[other].automation.arrived) return;
            RailSignalSafetyGameTests.ledger(h, t[other]);
            RailSignalSafetyGameTests.pack(h, t[other], sites.get(other));
          }
          done[0] = true;
          RailSignalSafetyGameTests.cleanup(h, sites, devices);
          if (persist) {
            checkpoint.data.putInt("Stage", 2);
            checkpoint.setDirty();
          }
          System.out.println(
              "FACTORYCRAFT_V0320_DISPATCH: PASS replay="
                  + replay
                  + " noPath="
                  + noPath
                  + " slowerWinner="
                  + fastFinishedFirst[0]
                  + " cargo=14 energy=128MJ physicalArrivals=true");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_dispatch_dual_disk", timeoutTicks = 1800)
  public static void unfueledTrainRetainsOriginAndIncomingAcrossJvmThenUsesOneRealCoal(
      GameTestHelper h) {
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var checkpoint = disk(h, "factorycraft_v0320_dispatch_dual");
    if (checkpoint.data.getInt("Stage") == 2) {
      h.assertTrue(
          checkpoint.data.getBoolean("Replayed")
              && d.train(checkpoint.data.getUUID("Train0")) == null,
          "completed dual lease resurrected owner");
      h.succeed();
      return;
    }
    boolean replay = checkpoint.data.getInt("Stage") == 1;
    var sites = new ArrayList<TrainV31GameTests.Site>();
    var devices = new ArrayList<RailAutomationData.Device>();
    var fixture = new Fixture(sites, devices);
    TrainData.Train[] t = {null};
    RailAutomationData.Device[] origin = {null}, target = {null};
    BlockPos[] chestPos = {null};
    int[] held = {0};
    boolean[] saving = {false}, fueled = {false}, done = {false};
    if (replay) {
      t[0] = d.train(checkpoint.data.getUUID("Train0"));
      h.assertTrue(
          t[0] != null
              && checkpoint
                  .data
                  .getCompound("State0")
                  .equals(RailSignalSafetyGameTests.saved(h, t[0].id)),
          "independent JVM changed unfueled dual-lease owner");
      origin[0] = s.device(checkpoint.data.getUUID("Origin"));
      target[0] = s.device(checkpoint.data.getUUID("Target"));
      h.assertTrue(
          origin[0].id.equals(s.slots.origin(t[0].id))
              && target[0].id.equals(s.slots.destination(t[0].id))
              && s.slots.count(origin[0].id) == 1
              && s.slots.count(target[0].id) == 1,
          "dual lease missing/duplicated after reduced-limit restart");
      chestPos[0] = BlockPos.of(checkpoint.data.getLong("Chest"));
      var restored = reload(h, checkpoint.data);
      sites.addAll(restored.sites());
      devices.addAll(restored.devices());
      checkpoint.data.putBoolean("Replayed", true);
      checkpoint.setDirty();
      System.out.println(
          "FACTORYCRAFT_V0320_DISPATCH_DUAL: VERIFIED exact owner, disabled limits0, origin=1"
              + " incoming=1");
    } else {
      var site = TrainV31GameTests.rail(h, 1203);
      sites.add(site);
      h.startSequence()
          .thenIdle(12)
          .thenExecute(
              () -> {
                origin[0] =
                    RailSignalSafetyGameTests.device(
                        h, site, TracksideKind.STOP, 30, false, "Unfueled origin");
                target[0] =
                    RailSignalSafetyGameTests.device(
                        h, site, TracksideKind.STOP, 84, false, "Unfueled destination");
                settings(s, origin[0], 50, 1);
                settings(s, target[0], 50, 1);
                devices.add(origin[0]);
                devices.add(target[0]);
                var train =
                    new TrainData.Train(
                        UUID.randomUUID(),
                        site.player().getUUID(),
                        factorycraft.research.ResearchTeams.resolve(site.player()).orElseThrow(),
                        new TrainPath(
                            RailwayData.get(h.getLevel()).graph,
                            new RailGraph.Directed(site.edge(), false)),
                        origin[0].point.offset() - StockSpec.HALF_LENGTH);
                for (int i = 0; i < 3; i++) {
                  var car =
                      new TrainCar(
                          UUID.randomUUID(),
                          i == 1 ? StockSpec.CARGO : StockSpec.LOCOMOTIVE,
                          d::setDirty);
                  car.reversed = i == 2;
                  if (i == 1) {
                    var stack = new ItemStack(Items.IRON_INGOT, 7);
                    stack.set(
                        net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal(
                            "finite unfueled station cargo"));
                    car.items.setStackInSlot(0, stack);
                  }
                  train.cars.add(car);
                }
                d.add(train);
                TrainWorld.views(d, train);
                t[0] = train;
                chestPos[0] =
                    BlockPos.containing(
                        site.plan().curve.start.x() + 12,
                        site.plan().curve.start.y(),
                        site.plan().curve.start.z() + 4);
                h.getLevel().setBlock(chestPos[0], Blocks.CHEST.defaultBlockState(), 3);
                ((ChestBlockEntity) h.getLevel().getBlockEntity(chestPos[0]))
                    .setItem(0, new ItemStack(Items.COAL));
                site.player()
                    .setPos(
                        site.plan().curve.start.x() + 16,
                        site.plan().curve.start.y() + 3,
                        site.plan().curve.start.z() + 12);
              })
          .thenIdle(15)
          .thenExecute(() -> schedule(d, t[0], "Unfueled origin", "Unfueled destination"));
    }
    h.onEachTick(
        () -> {
          if (done[0]
              || saving[0]
              || t[0] == null
              || !t[0].automation.automatic
              || !TrainWorld.available(d, t[0])
              || !s.runtimeReady(t[0])) return;
          var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(chestPos[0]);
          long energy = chest.getItem(0).getCount() * 4_000_000L;
          for (var car : t[0].cars) {
            energy += car.fuelJ + car.spentJ;
            if (car.kind == StockSpec.LOCOMOTIVE)
              energy += car.items.getStackInSlot(0).getCount() * 4_000_000L;
          }
          h.assertTrue(energy == 4_000_000L, "one-coal source energy was lost/duplicated");
          if (!fueled[0]) {
            if (!target[0].id.equals(t[0].automation.destination)) return;
            h.assertTrue(
                t[0].speed == 0
                    && t[0].head == t[0].automation.departureHead
                    && s.slots.count(origin[0].id) == 1
                    && s.slots.count(target[0].id) == 1,
                "unfueled outgoing commitment freed docked source");
            h.assertTrue(
                origin[0].id.equals(TrainAutomation.station(t[0], s)),
                "next target impersonated physical station");
            if (++held[0] < 20) return;
            assertStationRead(h, s, origin[0], 7, t[0].automation.number);
            assertStationRead(h, s, target[0], 0, 0);
            var metadata = t[0].automation.save();
            var roundTrip = AutoTrainState.load(metadata);
            h.assertTrue(
                roundTrip.invalid == null && roundTrip.save().equals(metadata),
                "origin metadata round trip changed canonical state");
            var malformed = metadata.copy();
            malformed.putInt("DepartureDirection", 0);
            h.assertTrue(
                AutoTrainState.load(malformed).invalid != null
                    && AutoTrainState.load(malformed).save().equals(malformed),
                "malformed departure pose was not quarantined intact");
            if (!replay) {
              for (var stop : List.of(origin[0], target[0])) {
                var n = stop.settings();
                n.putBoolean("Enabled", false);
                n.putInt("Limit", 0);
                s.configure(stop, n);
              }
              checkpoint.data.putUUID("Origin", origin[0].id);
              checkpoint.data.putUUID("Target", target[0].id);
              checkpoint.data.putLong("Chest", chestPos[0].asLong());
              saving[0] = true;
              freeze(h, checkpoint, fixture, t[0]);
              return;
            }
            var entity = (StockEntity) h.getLevel().getEntity(t[0].cars.getFirst().id);
            var port = TrainPorts.inventory(entity);
            h.assertTrue(
                port.insertItem(0, chest.getItem(0), true).isEmpty(),
                "real stopped fuel slot simulation");
            var fuel = chest.removeItem(0, 1);
            h.assertTrue(
                port.insertItem(0, fuel, false).isEmpty() && chest.getItem(0).isEmpty(),
                "actual one-owner coal handoff");
            checkpoint.data.putBoolean("RealCoalHandoff", true);
            checkpoint.setDirty();
            fueled[0] = true;
          } else {
            if (t[0].head != t[0].automation.departureHead)
              h.assertTrue(
                  s.slots.origin(t[0].id) == null && s.slots.count(origin[0].id) == 0,
                  "actual displacement retained old station slot");
            if (!t[0].automation.arrived) return;
            h.assertTrue(
                t[0].cars.getFirst().spentJ > 0 && s.slots.count(target[0].id) == 1,
                "no real paid arrival at grandfathered disabled destination");
            var load = t[0].cars.get(1).items.getStackInSlot(0);
            h.assertTrue(
                load.getCount() == 7
                    && load.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
                "typed finite cargo changed");
            done[0] = true;
            RailSignalSafetyGameTests.pack(h, t[0], sites.getFirst());
            RailSignalSafetyGameTests.cleanup(h, sites, devices);
            checkpoint.data.putInt("Stage", 2);
            checkpoint.setDirty();
            System.out.println(
                "FACTORYCRAFT_V0320_DISPATCH_DUAL: PASS actualPaidArrival=true cars=3 cargo=7"
                    + " coalPlusBufferPlusSpent=4MJ");
            h.succeed();
          }
        });
  }

  private static void assertStationRead(
      GameTestHelper h,
      RailAutomationData s,
      RailAutomationData.Device stop,
      int cargo,
      int trainNumber) {
    var root = s.entity(stop);
    var config =
        factorycraft.diagnostics.CircuitFactory.settings(true, false, List.of(), 15, 15).save();
    config.putBoolean("State", true);
    factorycraft.diagnostics.CircuitFactory.configure(
        root.circuit(true),
        factorycraft.circuits.CircuitSettings.load(config),
        factorycraft.simulation.circuits.SignalProgram.defaults(
            factorycraft.simulation.circuits.SignalProgram.Kind.CONSTANT));
    var reading = RailCircuitAdapter.read(root, root.circuit(false));
    h.assertTrue(
        reading.ready()
            && reading
                    .signals()
                    .get(factorycraft.simulation.circuits.SignalKey.item("minecraft:iron_ingot"))
                == cargo
            && reading.signals().get(factorycraft.simulation.circuits.SignalKey.virtual("train_id"))
                == trainNumber
            && reading.signals().get(factorycraft.simulation.circuits.SignalKey.virtual("signal_c"))
                == 1,
        "prospective destination read docked origin cargo/id or lost its own count");
  }
}
