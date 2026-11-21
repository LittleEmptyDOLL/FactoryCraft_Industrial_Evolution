package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.rail.automation.*;
import factorycraft.simulation.rail.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.gametest.*;

/** Source costs are observations only; actual detours use native paid motion and claims. */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class RailRoutingGameTests {
  @GameTest(template = "empty", batch = "rail_routing_prices", timeoutTicks = 240)
  public static void sourceCostsExcludeTargetAndPriceStationAndStoppedManualTrain(
      GameTestHelper h) {
    price(h, 1180, false);
  }

  @GameTest(template = "empty", batch = "rail_routing_prices", timeoutTicks = 240)
  public static void oneOccupiedLogicalRegionAcrossTwoPaidEdgesIsPricedOnce(GameTestHelper h) {
    price(h, 1181, true);
  }

  private static void price(GameTestHelper h, int arena, boolean twoEdges) {
    var base = TrainV31GameTests.rail(h, arena);
    var sites = new ArrayList<TrainV31GameTests.Site>(List.of(base));
    if (twoEdges) {
      var p = base.plan().curve.end;
      sites.add(
          RailSignalSafetyGameTests.extend(
              h, base, p, new RailEnd(p.x(), p.y(), p.z() - 96, new RailHeading(0))));
    }
    var devices = new ArrayList<RailAutomationData.Device>();
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    TrainData.Train[] owner = {null}, parked = {null};
    RailAutomationData.Device[] target = {null};
    boolean[] ready = {false}, done = {false};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h,
                      base,
                      TracksideKind.SIGNAL,
                      twoEdges ? 80 : 40,
                      false,
                      "Pricing boundary"));
              if (!twoEdges) {
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h, base, TracksideKind.SIGNAL, 60, false, "Occupied boundary"));
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h, base, TracksideKind.STOP, 48, false, "Intermediate station"));
              }
              target[0] =
                  RailSignalSafetyGameTests.device(
                      h, sites.getLast(), TracksideKind.STOP, 84, false, "Price target");
              devices.add(target[0]);
              owner[0] = RailSignalSafetyGameTests.train(h, base, 3, 26, false);
              parked[0] = RailSignalSafetyGameTests.train(h, sites.getLast(), 1, 70, false);
              base.player()
                  .setPos(
                      base.plan().curve.start.x() + 15,
                      base.plan().curve.start.y() + 3,
                      base.plan().curve.start.z() + 12);
            })
        .thenIdle(15)
        .thenExecute(() -> ready[0] = true);
    h.onEachTick(
        () -> {
          if (!ready[0] || done[0] || !s.runtimeReady(owner[0]) || !s.runtimeReady(parked[0]))
            return;
          var before = RailSignalSafetyGameTests.saved(h, owner[0].id);
          var other = RailSignalSafetyGameTests.saved(h, parked[0].id);
          var legacy = owner[0].automation.save();
          h.assertTrue(
              !legacy.contains("SignalWaitTicks")
                  && AutoTrainState.load(legacy).save().equals(legacy),
              "default-omitted old automatic metadata changed on round trip");
          var timed = legacy.copy();
          timed.putInt("SignalWaitTicks", 17);
          var restored = AutoTrainState.load(timed);
          h.assertTrue(
              !restored.empty() && restored.save().equals(timed), "nonzero additive wait was lost");
          timed.putInt("SignalWaitTicks", -1);
          var invalid = AutoTrainState.load(timed);
          h.assertTrue(
              invalid.invalid != null && invalid.save().equals(timed),
              "malformed wait metadata was silently discarded instead of quarantined");
          var job =
              new TrainRouteJob(
                  d,
                  owner[0],
                  s,
                  List.of(new RailPathSearch.Target(target[0].id, target[0].point, 50)));
          for (int n = 0; !job.done && n < 100; n++)
            h.assertTrue(job.advance(owner[0], s, 1) <= 1, "route expansion exceeded node budget");
          var result = job.result(owner[0], s.rails.graph);
          h.assertTrue(result != null, "native priced path missing");
          double distance = target[0].point.offset() + (twoEdges ? 96 : 0) - owner[0].front();
          double extra = twoEdges ? 7000 + 112 : 2000 + 7000 + 24;
          h.assertTrue(
              Math.abs(result.cost() - distance - extra) < 1e-6,
              "source metric price wrong: "
                  + result.cost()
                  + " distance="
                  + distance
                  + " extra="
                  + extra);
          h.assertTrue(
              before.equals(RailSignalSafetyGameTests.saved(h, owner[0].id))
                  && other.equals(RailSignalSafetyGameTests.saved(h, parked[0].id)),
              "path search changed resource/pose/claims owner");
          RailSignalSafetyGameTests.pack(h, owner[0], base);
          RailSignalSafetyGameTests.pack(h, parked[0], sites.getLast());
          RailSignalSafetyGameTests.cleanup(h, sites, devices);
          done[0] = true;
          System.out.println(
              "FACTORYCRAFT_V0320_ROUTE_PRICE: PASS twoEdges="
                  + twoEdges
                  + " distance="
                  + distance
                  + " sourceExtra="
                  + extra
                  + " targetPenalty=0");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_routing_detour", timeoutTicks = 1800)
  public static void heldBlueChainRepathsToRealSameNameFreeExitWithoutLosingOwners(
      GameTestHelper h) {
    detour(h, 1182, false);
  }

  @GameTest(template = "empty", batch = "rail_routing_disk", timeoutTicks = 1800)
  public static void heldChainTimerAndDestinationRestoreThenCompleteDetourInIndependentJvm(
      GameTestHelper h) {
    detour(h, 1183, true);
  }

  private static void detour(GameTestHelper h, int arena, boolean persist) {
    var disk =
        persist
            ? h.getLevel()
                .getDataStorage()
                .computeIfAbsent(
                    new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                        RailAutomationGameTests.ServiceDisk::new,
                        RailAutomationGameTests.ServiceDisk::load),
                    "factorycraft_v0320_route_detour")
            : null;
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var rails = RailwayData.get(h.getLevel());
    if (persist && disk.data.getInt("Stage") == 2) {
      h.assertTrue(
          disk.data.getBoolean("Replayed")
              && d.train(disk.data.getUUID("Train")) == null
              && d.train(disk.data.getUUID("Parked")) == null,
          "completed detour resurrected owner");
      h.succeed();
      return;
    }
    boolean replay = persist && disk.data.getInt("Stage") == 1;
    var sites = new ArrayList<TrainV31GameTests.Site>();
    var devices = new ArrayList<RailAutomationData.Device>();
    TrainData.Train[] t = {null}, parked = {null};
    RailAutomationData.Device[] chain = {null}, original = {null}, alternate = {null};
    boolean[] ready = {false}, inserted = {false}, done = {false}, saving = {false};
    double[] heldHead = {Double.NaN};
    long[] heldSpent = {0};
    if (replay) {
      t[0] = d.train(disk.data.getUUID("Train"));
      parked[0] = d.train(disk.data.getUUID("Parked"));
      h.assertTrue(t[0] != null && parked[0] != null, "saved native detour owners absent");
      h.assertTrue(
          disk.data.getCompound("State").equals(RailSignalSafetyGameTests.saved(h, t[0].id))
              && disk.data
                  .getCompound("ParkedState")
                  .equals(RailSignalSafetyGameTests.saved(h, parked[0].id)),
          "independent JVM changed exact route/claims/phase/cargo/fuel/wait timer");
      h.assertTrue(
          t[0].automation.signalWaitTicks >= 20 && t[0].automation.signalWaitTicks < 100,
          "persisted partial chain wait missing");
      var first =
          rails.segment(disk.data.getList("Edges", Tag.TAG_COMPOUND).getCompound(0).getUUID("Id"));
      var player = RailwayV30GameTests.player(h, new RailPlan(first.curve), true);
      player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
      var pins = new HashSet<Long>();
      for (long pin : disk.data.getLongArray("Pins")) pins.add(pin);
      for (var raw : disk.data.getList("Edges", Tag.TAG_COMPOUND)) {
        var id = ((CompoundTag) raw).getUUID("Id");
        h.assertTrue(rails.segment(id) != null, "paid detour rail missing");
        sites.add(
            new TrainV31GameTests.Site(new RailPlan(rails.segment(id).curve), id, player, pins));
      }
      for (var raw : disk.data.getList("Devices", Tag.TAG_COMPOUND)) {
        var expected = (CompoundTag) raw;
        var device = s.device(expected.getUUID("Id"));
        h.assertTrue(
            device != null && device.settings().equals(expected.getCompound("Settings")),
            "saved physical device identity/settings changed");
        devices.add(device);
      }
      chain[0] = s.device(disk.data.getUUID("Chain"));
      original[0] = s.device(disk.data.getUUID("Original"));
      alternate[0] = s.device(disk.data.getUUID("Alternate"));
      h.assertTrue(
          Objects.equals(s.slots.destination(t[0].id), original[0].id)
              && s.slots.count(original[0].id) == 1
              && s.slots.count(alternate[0].id) == 0,
          "incoming destination slot changed on restart");
      for (long pin : pins) {
        h.getLevel().setChunkForced((int) pin, (int) (pin >> 32), true);
        h.getLevel().getChunk((int) pin, (int) (pin >> 32));
      }
      player.setPos(
          first.curve.start.x() + 14, first.curve.start.y() + 3, first.curve.start.z() + 12);
      inserted[0] = true;
      ready[0] = true;
      heldHead[0] = t[0].head;
      heldSpent[0] = spent(t[0]);
      disk.data.putBoolean("Replayed", true);
      disk.setDirty();
      System.out.println(
          "FACTORYCRAFT_V0320_ROUTE_DISK: VERIFIED exact canonical owners, wait="
              + t[0].automation.signalWaitTicks
              + " oldSlot=1 alternateSlot=0");
    } else {
      var base = TrainV31GameTests.rail(h, arena);
      sites.add(base);
      var p = base.plan().curve.end;
      sites.add(
          RailSignalSafetyGameTests.extend(
              h, base, p, new RailEnd(p.x(), p.y(), p.z() - 96, new RailHeading(0))));
      sites.add(
          RailSignalSafetyGameTests.extend(
              h, base, p, new RailEnd(p.x() + 32, p.y(), p.z() - 96, new RailHeading(0))));
      h.startSequence()
          .thenIdle(12)
          .thenExecute(
              () -> {
                chain[0] =
                    RailSignalSafetyGameTests.device(
                        h, base, TracksideKind.CHAIN, 70, false, "Repath chain");
                devices.add(chain[0]);
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h, sites.get(1), TracksideKind.SIGNAL, 40, false, "Short exit"));
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h, sites.get(2), TracksideKind.SIGNAL, 40, false, "Free exit"));
                original[0] =
                    RailSignalSafetyGameTests.device(
                        h, sites.get(1), TracksideKind.STOP, 84, false, "Detour destination");
                alternate[0] =
                    RailSignalSafetyGameTests.device(
                        h, sites.get(2), TracksideKind.STOP, 90, false, "Detour destination");
                devices.add(original[0]);
                devices.add(alternate[0]);
                t[0] = RailSignalSafetyGameTests.train(h, base, 3, 28, false);
                base.player().setPos(p.x() + 15, p.y() + 3, p.z() + 12);
              })
          .thenIdle(15)
          .thenExecute(
              () -> {
                RailSignalSafetyGameTests.schedule(d, t[0], "Detour destination");
                ready[0] = true;
              });
    }
    h.onEachTick(
        () -> {
          if (!ready[0] || done[0] || saving[0]) return;
          if (!inserted[0]) {
            if (!t[0].automation.routed) return;
            h.assertTrue(
                original[0].id.equals(t[0].automation.destination),
                "initial shortest target not selected");
            parked[0] = RailSignalSafetyGameTests.train(h, sites.get(1), 1, 55, false);
            sites
                .getFirst()
                .player()
                .setPos(
                    sites.getFirst().plan().curve.end.x() + 16,
                    sites.getFirst().plan().curve.end.y() + 3,
                    sites.getFirst().plan().curve.end.z() + 12);
            inserted[0] = true;
          }
          RailSignalSafetyGameTests.disjoint(h, d, t[0], parked[0]);
          if (original[0].id.equals(t[0].automation.destination)) {
            h.assertTrue(
                s.slots.count(original[0].id) == 1 && s.slots.count(alternate[0].id) == 0,
                "search surrendered or duplicated incoming commitment");
            h.assertTrue(t[0].front() < chain[0].point.offset(), "blocked route crossed chain");
            if (t[0].speed == 0 && t[0].automation.heldSignal != null) {
              if (Double.isNaN(heldHead[0])) {
                heldHead[0] = t[0].head;
                heldSpent[0] = spent(t[0]);
              }
              h.assertTrue(
                  t[0].head == heldHead[0] && spent(t[0]) == heldSpent[0],
                  "chain wait bought creep");
              if (persist && !replay && t[0].automation.signalWaitTicks >= 20) {
                saving[0] = true;
                for (long pin : sites.getFirst().pins()) {
                  d.unloadChunk(pin);
                  h.getLevel().setChunkForced((int) pin, (int) (pin >> 32), false);
                }
                h.runAfterDelay(
                    50,
                    () -> {
                      disk.data.putInt("Stage", 1);
                      disk.data.putUUID("Train", t[0].id);
                      disk.data.putUUID("Parked", parked[0].id);
                      disk.data.putUUID("Chain", chain[0].id);
                      disk.data.putUUID("Original", original[0].id);
                      disk.data.putUUID("Alternate", alternate[0].id);
                      disk.data.put("State", RailSignalSafetyGameTests.saved(h, t[0].id));
                      disk.data.put(
                          "ParkedState", RailSignalSafetyGameTests.saved(h, parked[0].id));
                      var edges = new ListTag();
                      for (var site : sites) {
                        var row = new CompoundTag();
                        row.putUUID("Id", site.edge());
                        edges.add(row);
                      }
                      disk.data.put("Edges", edges);
                      var physical = new ListTag();
                      for (var device : devices) {
                        var row = new CompoundTag();
                        row.putUUID("Id", device.id);
                        row.put("Settings", device.settings());
                        physical.add(row);
                      }
                      disk.data.put("Devices", physical);
                      disk.data.putLongArray(
                          "Pins",
                          sites.getFirst().pins().stream().mapToLong(Long::longValue).toArray());
                      disk.setDirty();
                      System.out.println(
                          "FACTORYCRAFT_V0320_ROUTE_DISK: SEEDED exact held-chain wait="
                              + t[0].automation.signalWaitTicks
                              + " cars=4 cargo=7 finiteEnergy=96MJ");
                      h.succeed();
                    });
              }
            }
            return;
          }
          h.assertTrue(
              alternate[0].id.equals(t[0].automation.destination)
                  && s.slots.count(original[0].id) == 0
                  && s.slots.count(alternate[0].id) == 1,
              "repath did not atomically transfer single destination slot");
          h.assertTrue(!Double.isNaN(heldHead[0]), "no actual stopped chain wait before detour");
          if (!t[0].automation.arrived) return;
          done[0] = true;
          RailSignalSafetyGameTests.ledger(h, t[0]);
          RailSignalSafetyGameTests.ledger(h, parked[0]);
          RailSignalSafetyGameTests.pack(h, t[0], sites.getFirst());
          RailSignalSafetyGameTests.pack(h, parked[0], sites.get(1));
          RailSignalSafetyGameTests.cleanup(h, sites, devices);
          if (persist) {
            disk.data.putInt("Stage", 2);
            disk.setDirty();
          }
          System.out.println(
              "FACTORYCRAFT_V0320_ROUTE_DETOUR: PASS replay="
                  + replay
                  + " physicalArrival=true oldSlot=0 newSlot=1 cars=4 cargo=7 energy=96MJ");
          h.succeed();
        });
  }

  private static long spent(TrainData.Train t) {
    return t.cars.stream().mapToLong(c -> c.spentJ).sum();
  }

  @GameTest(template = "empty", batch = "rail_routing_long_wait", timeoutTicks = 1800)
  public static void singleDestinationChainRepathKeepsCommitmentAndNeverPurchasesStoppedCreep(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 1184);
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var devices = new ArrayList<RailAutomationData.Device>();
    TrainData.Train[] t = {null}, parked = {null};
    RailAutomationData.Device[] chain = {null}, stop = {null};
    boolean[] started = {false}, done = {false};
    double[] head = {Double.NaN};
    long[] fuel = {0}, nodes = {0};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              chain[0] =
                  RailSignalSafetyGameTests.device(
                      h, site, TracksideKind.CHAIN, 40, false, "Long wait");
              devices.add(chain[0]);
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, site, TracksideKind.SIGNAL, 60, false, "Occupied exit"));
              stop[0] =
                  RailSignalSafetyGameTests.device(
                      h, site, TracksideKind.STOP, 88, false, "Only destination");
              devices.add(stop[0]);
              t[0] = RailSignalSafetyGameTests.train(h, site, 3, 26, false);
              parked[0] = RailSignalSafetyGameTests.train(h, site, 1, 72, false);
              site.player()
                  .setPos(
                      site.plan().curve.start.x() + 14,
                      site.plan().curve.start.y() + 3,
                      site.plan().curve.start.z() + 12);
            })
        .thenIdle(15)
        .thenExecute(
            () -> {
              RailSignalSafetyGameTests.schedule(d, t[0], "Only destination");
              started[0] = true;
            });
    h.onEachTick(
        () -> {
          if (!started[0] || done[0] || !t[0].automation.routed) return;
          if (parked[0] != null) {
            RailSignalSafetyGameTests.disjoint(h, d, t[0], parked[0]);
            h.assertTrue(
                stop[0].id.equals(t[0].automation.destination) && s.slots.count(stop[0].id) == 1,
                "repath surrendered committed slot");
            h.assertTrue(t[0].front() < chain[0].point.offset(), "blocked chain entered");
            if (t[0].speed != 0 || t[0].automation.heldSignal == null) return;
            if (Double.isNaN(head[0])) {
              head[0] = t[0].head;
              fuel[0] = spent(t[0]);
              nodes[0] = s.pathNodes;
            }
            h.assertTrue(
                t[0].head == head[0] && spent(t[0]) == fuel[0], "periodic repath bought creep");
            if (t[0].automation.signalWaitTicks < 620) return;
            h.assertTrue(
                s.pathNodes > nodes[0]
                    && t[0].automation.repathAt >= 1200
                    && !t[0].automation.repathing
                    && t[0].automation.routeJob == null,
                "single-name 30-second repath did not finish");
            RailSignalSafetyGameTests.pack(h, parked[0], site);
            parked[0] = null;
          } else if (t[0].automation.arrived) {
            done[0] = true;
            RailSignalSafetyGameTests.ledger(h, t[0]);
            RailSignalSafetyGameTests.pack(h, t[0], site);
            RailSignalSafetyGameTests.cleanup(h, List.of(site), devices);
            System.out.println(
                "FACTORYCRAFT_V0320_ROUTE_LONG_WAIT: PASS stoppedTicks>=620 noPaidCreep=true"
                    + " actualArrival=true");
            h.succeed();
          }
        });
  }
}
