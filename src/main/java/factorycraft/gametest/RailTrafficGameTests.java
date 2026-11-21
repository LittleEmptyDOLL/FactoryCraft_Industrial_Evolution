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
import net.neoforged.neoforge.gametest.*;

/** Real endpoint-only conflict and lifecycle matrix; all movement uses TrainSimulation. */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class RailTrafficGameTests {
  @GameTest(template = "empty", batch = "rail_traffic_junction", timeoutTicks = 1800)
  public static void simultaneousTwoApproachTwoExitTurnoutReservesOneRealJunctionAtATime(
      GameTestHelper h) {
    junction(h, 1120, false);
  }

  @GameTest(template = "empty", batch = "rail_traffic_claim_disk", timeoutTicks = 1800)
  public static void occupiedAndReservedTurnoutRestoresExactOwnersThenCompletesInIndependentJvm(
      GameTestHelper h) {
    junction(h, 1125, true);
  }

  private static void junction(GameTestHelper h, int arena, boolean persist) {
    var checkpoint =
        persist
            ? h.getLevel()
                .getDataStorage()
                .computeIfAbsent(
                    new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                        RailAutomationGameTests.ServiceDisk::new,
                        RailAutomationGameTests.ServiceDisk::load),
                    "factorycraft_v0320_claimed_turnout")
            : null;
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var rails = RailwayData.get(h.getLevel());
    if (persist && checkpoint.data.getInt("Stage") == 2) {
      h.assertTrue(
          checkpoint.data.getBoolean("ReplayedClaim")
              && d.train(checkpoint.data.getUUID("Train0")) == null
              && d.train(checkpoint.data.getUUID("Train1")) == null,
          "completed persisted turnout resurrected a resource owner");
      System.out.println(
          "FACTORYCRAFT_V0320_CLAIM_DISK: VERIFIED prior paid completion, no resurrected owners");
      h.succeed();
      return;
    }
    boolean replay = persist && checkpoint.data.getInt("Stage") == 1;
    var sites = new ArrayList<TrainV31GameTests.Site>();
    var devices = new ArrayList<RailAutomationData.Device>();
    TrainData.Train[] t = {null, null};
    if (replay) {
      var rows = checkpoint.data.getList("Sites", 10);
      var first = rails.segment(rows.getCompound(0).getUUID("Edge"));
      h.assertTrue(first != null, "saved paid rail missing");
      var player = RailwayV30GameTests.player(h, new RailPlan(first.curve), true);
      player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
      var pins = new HashSet<Long>();
      for (long key : checkpoint.data.getLongArray("Pins")) pins.add(key);
      for (var raw : rows) {
        var id = ((net.minecraft.nbt.CompoundTag) raw).getUUID("Edge");
        var segment = rails.segment(id);
        h.assertTrue(segment != null, "saved junction edge lost");
        sites.add(new TrainV31GameTests.Site(new RailPlan(segment.curve), id, player, pins));
      }
      for (int i = 0; i < 2; i++) {
        t[i] = d.train(checkpoint.data.getUUID("Train" + i));
        h.assertTrue(t[i] != null, "saved junction train lost");
        h.assertTrue(
            RailSignalSafetyGameTests.saved(h, t[i].id)
                .equals(checkpoint.data.getCompound("State" + i)),
            "independent JVM changed exact claimed route, pose, fuel, cargo or automation");
        h.assertTrue(
            Objects.equals(s.slots.destination(t[i].id), t[i].automation.destination)
                && s.slots.count(t[i].automation.destination) == 1,
            "saved incoming station slot duplicated/lost");
        RailSignalSafetyGameTests.ledger(h, t[i]);
      }
      for (var raw : checkpoint.data.getList("Devices", 10)) {
        var device = s.device(((net.minecraft.nbt.CompoundTag) raw).getUUID("Id"));
        h.assertTrue(device != null, "saved canonical junction device missing");
        devices.add(device);
      }
      for (long key : pins) {
        h.getLevel().setChunkForced((int) key, (int) (key >> 32), true);
        h.getLevel().getChunk((int) key, (int) (key >> 32));
      }
      player.setPos(
          first.curve.start.x() + 12, first.curve.start.y() + 2, first.curve.start.z() + 12);
      System.out.println(
          "FACTORYCRAFT_V0320_CLAIM_DISK: VERIFIED exact two canonical owners, resuming real paid"
              + " traffic");
    } else {
      var south = TrainV31GameTests.rail(h, arena);
      var joint = south.plan().curve.end;
      var east =
          RailSignalSafetyGameTests.extend(
              h,
              south,
              new RailEnd(joint.x() + 32, joint.y(), joint.z() + 48, new RailHeading(0)),
              joint);
      var north =
          RailSignalSafetyGameTests.extend(
              h,
              south,
              joint,
              new RailEnd(joint.x(), joint.y(), joint.z() - 96, new RailHeading(0)));
      var west =
          RailSignalSafetyGameTests.extend(
              h,
              south,
              joint,
              new RailEnd(joint.x() - 32, joint.y(), joint.z() - 48, new RailHeading(0)));
      sites.addAll(List.of(south, east, north, west));
    }
    var south = sites.get(0);
    var east = sites.get(1);
    var north = sites.get(2);
    var west = sites.get(3);
    var joint = south.plan().curve.end;
    boolean[] done = {false}, waited = {replay}, restored = {false};
    if (!replay)
      h.startSequence()
          .thenIdle(12)
          .thenExecute(
              () -> {
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h, south, TracksideKind.CHAIN, 72, false, "South throat"));
                double offset = east.plan().curve.length() - 18;
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h, east, TracksideKind.CHAIN, offset, false, "East throat"));
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h, north, TracksideKind.SIGNAL, 32, false, "North clearance"));
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h, west, TracksideKind.SIGNAL, 32, false, "West clearance"));
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h, north, TracksideKind.STOP, 84, false, "North"));
                devices.add(
                    RailSignalSafetyGameTests.device(
                        h,
                        west,
                        TracksideKind.STOP,
                        west.plan().curve.length() - 5,
                        false,
                        "West"));
                t[0] = RailSignalSafetyGameTests.train(h, south, 3, 55, false);
                t[1] = RailSignalSafetyGameTests.train(h, east, 3, offset - 17, false);
                south.player().setPos(joint.x() + 12, joint.y() + 2, joint.z() + 12);
              })
          .thenIdle(12)
          .thenExecute(
              () -> {
                RailSignalSafetyGameTests.schedule(d, t[0], "North");
                RailSignalSafetyGameTests.schedule(d, t[1], "West");
              });
    h.onEachTick(
        () -> {
          if (done[0] || t[0] == null || !t[0].automation.automatic) return;
          RailSignalSafetyGameTests.disjoint(h, d, t[0], t[1]);
          waited[0] |=
              t[0].automation.phase == AutoTrainState.Phase.SIGNAL_WAIT
                  || t[1].automation.phase == AutoTrainState.Phase.SIGNAL_WAIT;
          var throat = s.blocks.at(south.edge(), 95, false);
          if (throat != null)
            h.assertTrue(
                s.claims.occupants(throat.id).size() <= 1,
                "two approaches occupied one real endpoint junction");
          if (replay) {
            var id = checkpoint.data.getUUID("Region");
            var owner = s.claims.reservation(id);
            var original = d.train(checkpoint.data.getUUID("ClaimOwner"));
            if (owner != null && (!restored[0] || original.automation.reserved.contains(id)))
              h.assertTrue(
                  owner.equals(original.id), "restart hijacked active junction reservation");
            restored[0] |= Objects.equals(owner, original.id);
          } else if (persist
              && waited[0]
              && throat != null
              && s.claims.occupants(throat.id).size() == 1
              && s.claims.reservation(throat.id) != null) {
            var owner = s.claims.reservation(throat.id);
            h.assertTrue(
                s.claims.occupants(throat.id).contains(owner),
                "junction body/reservation mismatch before checkpoint");
            checkpoint.data.putUUID("Region", throat.id);
            checkpoint.data.putUUID("ClaimOwner", owner);
            done[0] = true;
            for (long key : south.pins()) {
              d.unloadChunk(key);
              h.getLevel().setChunkForced((int) key, (int) (key >> 32), false);
            }
            h.runAfterDelay(
                50,
                () -> {
                  for (int i = 0; i < 2; i++) {
                    checkpoint.data.putUUID("Train" + i, t[i].id);
                    checkpoint.data.put("State" + i, RailSignalSafetyGameTests.saved(h, t[i].id));
                  }
                  var ids = new net.minecraft.nbt.ListTag();
                  for (var site : sites) {
                    var row = new net.minecraft.nbt.CompoundTag();
                    row.putUUID("Edge", site.edge());
                    ids.add(row);
                  }
                  checkpoint.data.put("Sites", ids);
                  var ds = new net.minecraft.nbt.ListTag();
                  for (var device : devices) {
                    var row = new net.minecraft.nbt.CompoundTag();
                    row.putUUID("Id", device.id);
                    ds.add(row);
                  }
                  checkpoint.data.put("Devices", ds);
                  checkpoint.data.putLongArray(
                      "Pins", south.pins().stream().mapToLong(Long::longValue).toArray());
                  checkpoint.data.putInt("Stage", 1);
                  checkpoint.setDirty();
                  d.setDirty();
                  System.out.println(
                      "FACTORYCRAFT_V0320_CLAIM_DISK: SEEDED occupied and reserved junction, two"
                          + " finite trains, source phase retained");
                  h.succeed();
                });
            return;
          }
          if (!t[0].automation.arrived || !t[1].automation.arrived) return;
          done[0] = true;
          h.assertTrue(waited[0], "junction contention was not exercised");
          if (replay)
            h.assertTrue(restored[0], "canonical junction claim was never restored before release");
          RailSignalSafetyGameTests.pack(h, t[0], south);
          RailSignalSafetyGameTests.pack(h, t[1], east);
          RailSignalSafetyGameTests.cleanup(h, sites, devices);
          if (persist) {
            checkpoint.data.putInt("Stage", 2);
            checkpoint.data.putBoolean("ReplayedClaim", true);
            checkpoint.setDirty();
          }
          System.out.println(
              persist
                  ? "FACTORYCRAFT_V0320_CLAIM_DISK: PASS independent JVM claim restoration and"
                      + " actual two-train completion"
                  : "FACTORYCRAFT_V0320_TWO_BY_TWO: PASS trains=2 cars=6 realEndpointTurnout=true"
                      + " noDiamond=true");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_traffic_levels", timeoutTicks = 1300)
  public static void verticallySeparatedOpposingAutomaticsShareNoLogicalOrPhysicalClaims(
      GameTestHelper h) {
    var lower = TrainV31GameTests.rail(h, 1121);
    var c = lower.plan().curve;
    var upper =
        RailSignalSafetyGameTests.extend(
            h,
            lower,
            new RailEnd(c.start.x(), c.start.y() + 8, c.start.z(), new RailHeading(0)),
            new RailEnd(c.end.x(), c.end.y() + 8, c.end.z(), new RailHeading(0)));
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var devices = new ArrayList<RailAutomationData.Device>();
    TrainData.Train[] t = {null, null};
    boolean[] done = {false};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, lower, TracksideKind.CHAIN, 44, false, "Lower chain"));
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, lower, TracksideKind.SIGNAL, 58, false, "Lower exit"));
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, lower, TracksideKind.STOP, 84, false, "Lower terminal"));
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, upper, TracksideKind.CHAIN, 52, true, "Upper chain"));
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, upper, TracksideKind.SIGNAL, 30, true, "Upper exit"));
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, upper, TracksideKind.STOP, 8, true, "Upper terminal"));
              t[0] = RailSignalSafetyGameTests.train(h, lower, 3, 28, false);
              t[1] = RailSignalSafetyGameTests.train(h, upper, 3, 68, true);
              lower.player().setPos(c.start.x() + 12, c.start.y() + 2, c.start.z() + 12);
            })
        .thenIdle(12)
        .thenExecute(
            () -> {
              h.assertTrue(
                  Collections.disjoint(
                      s.blocks.covering(lower.edge(), 0, 96),
                      s.blocks.covering(upper.edge(), 0, 96)),
                  "overpass merged logical regions");
              RailSignalSafetyGameTests.schedule(d, t[0], "Lower terminal");
              RailSignalSafetyGameTests.schedule(d, t[1], "Upper terminal");
            });
    h.onEachTick(
        () -> {
          if (done[0] || t[0] == null || !t[0].automation.automatic) return;
          RailSignalSafetyGameTests.disjoint(h, d, t[0], t[1]);
          h.assertTrue(
              t[0].automation.phase != AutoTrainState.Phase.SIGNAL_WAIT
                  && t[1].automation.phase != AutoTrainState.Phase.SIGNAL_WAIT,
              "independent overpass falsely reserved other level");
          if (!t[0].automation.arrived || !t[1].automation.arrived) return;
          done[0] = true;
          RailSignalSafetyGameTests.pack(h, t[0], lower);
          RailSignalSafetyGameTests.pack(h, t[1], upper);
          RailSignalSafetyGameTests.cleanup(h, List.of(lower, upper), devices);
          System.out.println(
              "FACTORYCRAFT_V0320_OVERPASS: PASS trains=2 cars=6 separationY=8 independent=true");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_traffic_chunks", timeoutTicks = 1500)
  public static void realSignalChunkUnloadQuarantinesThenRestoresOneCanonicalAutomaticOwner(
      GameTestHelper h) {
    var base = TrainV31GameTests.rail(h, 1122);
    var c = base.plan().curve;
    var site =
        RailSignalSafetyGameTests.extend(
            h,
            base,
            new RailEnd(c.start.x() + 13, c.start.y(), c.start.z(), new RailHeading(0)),
            new RailEnd(c.end.x() + 13, c.end.y(), c.end.z(), new RailHeading(0)));
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var devices = new ArrayList<RailAutomationData.Device>();
    TrainData.Train[] t = {null};
    RailAutomationData.Device[] signal = {null};
    long[] chunk = {0};
    int[] stage = {-1}, ticks = {0};
    boolean[] done = {false};
    net.minecraft.nbt.CompoundTag[] frozen = {null};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              signal[0] =
                  RailSignalSafetyGameTests.device(
                      h, site, TracksideKind.SIGNAL, 52, false, "Unloadable head");
              devices.add(signal[0]);
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, site, TracksideKind.STOP, 84, false, "Reload terminal"));
              t[0] = RailSignalSafetyGameTests.train(h, site, 3, 26, false);
              chunk[0] = new net.minecraft.world.level.ChunkPos(signal[0].anchor).toLong();
              site.player().setPos(c.start.x() + 30, c.start.y() + 3, c.start.z() + 12);
            })
        .thenIdle(12)
        .thenExecute(
            () -> {
              RailSignalSafetyGameTests.schedule(d, t[0], "Reload terminal");
              for (long key : site.pins())
                h.getLevel().setChunkForced((int) key, (int) (key >> 32), false);
              stage[0] = 0;
            });
    h.onEachTick(
        () -> {
          if (done[0] || stage[0] < 0) return;
          ticks[0]++;
          if (stage[0] == 0) {
            if (!s.online(signal[0].point))
              h.assertTrue(
                  t[0].front() < signal[0].point.offset(),
                  "automatic passed unavailable signal during unload");
            if (h.getLevel().getChunkSource().getChunkNow((int) chunk[0], (int) (chunk[0] >> 32))
                != null) return;
            h.assertTrue(!s.online(signal[0].point), "unloaded controller remained available");
            frozen[0] = RailSignalSafetyGameTests.saved(h, t[0].id);
            stage[0] = 1;
            ticks[0] = 0;
          } else if (stage[0] == 1) {
            h.assertTrue(
                frozen[0].equals(RailSignalSafetyGameTests.saved(h, t[0].id)),
                "unavailable owner moved or paid work");
            if (ticks[0] < 20) return;
            for (long key : site.pins()) {
              h.getLevel().setChunkForced((int) key, (int) (key >> 32), true);
              h.getLevel().getChunk((int) key, (int) (key >> 32));
            }
            stage[0] = 2;
          } else if (t[0].automation.arrived) {
            done[0] = true;
            h.assertTrue(s.online(signal[0].point), "signal canonical UUID did not reload");
            RailSignalSafetyGameTests.ledger(h, t[0]);
            h.assertTrue(
                s.slots.count(t[0].automation.destination) == 1, "reload duplicated station slot");
            RailSignalSafetyGameTests.pack(h, t[0], site);
            RailSignalSafetyGameTests.cleanup(h, List.of(base, site), devices);
            System.out.println(
                "FACTORYCRAFT_V0320_SIGNAL_UNLOAD: PASS actualChunkUnload=true frozenTicks=20"
                    + " canonicalOwner=true");
            h.succeed();
          }
        });
  }

  @GameTest(template = "empty", batch = "rail_traffic_repath", timeoutTicks = 1200)
  public static void removingCommittedStopBrakesThenRepathsToRealSameNameAlternative(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 1123);
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var devices = new ArrayList<RailAutomationData.Device>();
    TrainData.Train[] t = {null};
    RailAutomationData.Device[] preferred = {null}, alternative = {null};
    boolean[] ready = {false}, removed = {false}, done = {false};
    double[] before = {0};
    int paid = RailwayData.get(h.getLevel()).segment(site.edge()).paidCount();
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              preferred[0] =
                  RailSignalSafetyGameTests.device(
                      h, site, TracksideKind.STOP, 84, false, "Recovery terminal");
              alternative[0] =
                  RailSignalSafetyGameTests.device(
                      h, site, TracksideKind.STOP, 64, false, "Recovery terminal");
              devices.add(preferred[0]);
              devices.add(alternative[0]);
              var settings = preferred[0].settings();
              settings.putInt("Priority", 200);
              settings.putInt("Limit", 1);
              s.configure(preferred[0], settings);
              t[0] = RailSignalSafetyGameTests.train(h, site, 3, 26, false);
              site.player()
                  .setPos(
                      site.plan().curve.start.x() + 12,
                      site.plan().curve.start.y() + 2,
                      site.plan().curve.start.z() + 12);
            })
        .thenIdle(12)
        .thenExecute(
            () -> {
              RailSignalSafetyGameTests.schedule(d, t[0], "Recovery terminal");
              ready[0] = true;
              before[0] = t[0].head;
            });
    h.onEachTick(
        () -> {
          if (done[0] || !ready[0]) return;
          RailSignalSafetyGameTests.ledger(h, t[0]);
          h.assertTrue(
              t[0].head >= before[0] - 1e-7, "destination deletion snapped train backward");
          before[0] = t[0].head;
          if (!removed[0] && t[0].speed > 0 && t[0].head > 35) {
            h.assertTrue(
                Objects.equals(t[0].automation.destination, preferred[0].id)
                    && s.slots.count(preferred[0].id) == 1,
                "preferred stop was not actually committed");
            site.player()
                .setPos(
                    preferred[0].anchor.getX() + .5,
                    preferred[0].anchor.getY(),
                    preferred[0].anchor.getZ() + 2.5);
            h.assertTrue(
                TracksidePlacement.remove(site.player(), preferred[0].anchor).equals("ready"),
                "real en-route station demolition refused");
            devices.remove(preferred[0]);
            removed[0] = true;
            h.assertTrue(
                s.device(preferred[0].id) == null && s.slots.count(preferred[0].id) == 0,
                "deleted station left a phantom slot");
            h.assertTrue(!t[0].automation.routed, "deleted destination kept stale routed intent");
          }
          if (!t[0].automation.arrived) return;
          done[0] = true;
          h.assertTrue(
              removed[0]
                  && Objects.equals(t[0].automation.destination, alternative[0].id)
                  && s.slots.count(alternative[0].id) == 1,
              "train failed to arrive at the actual alternate station");
          h.assertTrue(
              RailwayData.get(h.getLevel()).segment(site.edge()).paidCount() == paid,
              "repath changed physical paid rail claims");
          RailSignalSafetyGameTests.pack(h, t[0], site);
          RailSignalSafetyGameTests.cleanup(h, List.of(site), devices);
          System.out.println(
              "FACTORYCRAFT_V0320_STOP_RECOVERY: PASS actualDemolition=true sameNameRepath=true"
                  + " cars=3 finiteIron=7");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_traffic_congested_speed", timeoutTicks = 1800)
  public static void maximumSourceSpeedBrakesBeforeRealBlueTurnoutWithOccupiedSelectedExit(
      GameTestHelper h) {
    var approach = TrainV31GameTests.rail(h, 1124);
    var origin = approach.plan().curve.start;
    var joint = approach.plan().curve.end;
    var lead =
        RailSignalSafetyGameTests.extend(
            h,
            approach,
            new RailEnd(origin.x(), origin.y(), origin.z() + 128, new RailHeading(0)),
            origin);
    var staging =
        RailSignalSafetyGameTests.extend(
            h,
            approach,
            new RailEnd(origin.x(), origin.y(), origin.z() + 256, new RailHeading(0)),
            lead.plan().curve.start);
    var north =
        RailSignalSafetyGameTests.extend(
            h,
            approach,
            joint,
            new RailEnd(joint.x(), joint.y(), joint.z() - 96, new RailHeading(0)));
    var west =
        RailSignalSafetyGameTests.extend(
            h,
            approach,
            joint,
            new RailEnd(joint.x() - 32, joint.y(), joint.z() - 48, new RailHeading(0)));
    var sites = List.of(approach, lead, staging, north, west);
    var devices = new ArrayList<RailAutomationData.Device>();
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    TrainData.Train[] t = {null}, parked = {null};
    RailAutomationData.Device[] entry = {null};
    boolean[] ready = {false}, injected = {false}, done = {false};
    int[] held = {0};
    long[] spent = {0};
    double[] previous = {0};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              entry[0] =
                  RailSignalSafetyGameTests.device(
                      h, approach, TracksideKind.CHAIN, 72, false, "High speed throat");
              devices.add(entry[0]);
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, north, TracksideKind.SIGNAL, 20, false, "Blocked north exit"));
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, west, TracksideKind.SIGNAL, 32, false, "Free west exit"));
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, north, TracksideKind.STOP, 84, false, "Congested terminal"));
              parked[0] = RailSignalSafetyGameTests.train(h, north, 1, 55, false);
              t[0] = RailSignalSafetyGameTests.train(h, staging, 3, 26, false);
              approach.player().setPos(joint.x() + 12, joint.y() + 2, joint.z() + 12);
            })
        .thenIdle(12)
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.signals.view(entry[0].point).aspect() == RailSignals.Aspect.BLUE,
                  "native divergent turnout not blue");
              RailSignalSafetyGameTests.schedule(d, t[0], "Congested terminal");
              ready[0] = true;
            });
    h.onEachTick(
        () -> {
          if (done[0] || !ready[0] || !t[0].automation.routed) return;
          if (!injected[0]) {
            t[0].speed = 1.2;
            injected[0] = true;
            previous[0] = t[0].head;
          }
          h.assertTrue(t[0].head >= previous[0] - 1e-7, "high-speed turnout snapped backwards");
          previous[0] = t[0].head;
          RailSignalSafetyGameTests.ledger(h, t[0]);
          if (parked[0] != null) {
            RailSignalSafetyGameTests.disjoint(h, d, t[0], parked[0]);
            h.assertTrue(
                t[0].front() < 256 + entry[0].point.offset() + 1e-7,
                "source-max train crossed selected blocked junction entry");
            if (t[0].speed == 0 && t[0].automation.phase == AutoTrainState.Phase.SIGNAL_WAIT) {
              long debit = t[0].cars.stream().mapToLong(c -> c.spentJ).sum();
              if (held[0]++ == 0) spent[0] = debit;
              h.assertTrue(spent[0] == debit, "blocked junction retry purchased traction");
              if (held[0] == 20) {
                RailSignalSafetyGameTests.pack(h, parked[0], north);
                parked[0] = null;
              }
            }
          } else if (t[0].automation.arrived) {
            done[0] = true;
            h.assertTrue(held[0] >= 20, "actual maximum-speed selected-red wait not exercised");
            RailSignalSafetyGameTests.pack(h, t[0], staging);
            RailSignalSafetyGameTests.cleanup(h, sites, devices);
            System.out.println(
                "FACTORYCRAFT_V0320_CONGESTED_MAX: PASS declaredInitialKmh=259.2 realBranch=true"
                    + " paidWaitTicks=20 thenActualArrival=true");
            h.succeed();
          }
        });
  }

  @GameTest(template = "empty", batch = "rail_traffic_signal_recovery", timeoutTicks = 1500)
  public static void destroyedSignalFailsClosedThenReformsFromItsSingleRealRefund(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 1126);
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var devices = new ArrayList<RailAutomationData.Device>();
    RailAutomationData.Device[] signal = {null};
    TrainData.Train[] t = {null}, parked = {null};
    int[] stage = {-1}, held = {0};
    boolean[] done = {false};
    double[] head = {0};
    long[] spent = {0};
    factorycraft.rail.automation.TracksideBlockEntity[] stale = {null};
    boolean[] mergedConflict = {false};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              signal[0] =
                  RailSignalSafetyGameTests.device(
                      h, site, TracksideKind.SIGNAL, 52, false, "Recoverable signal");
              devices.add(signal[0]);
              devices.add(
                  RailSignalSafetyGameTests.device(
                      h, site, TracksideKind.STOP, 84, false, "Signal recovery terminal"));
              t[0] = RailSignalSafetyGameTests.train(h, site, 3, 26, false);
              parked[0] = RailSignalSafetyGameTests.train(h, site, 1, 76, false);
              site.player()
                  .setPos(
                      site.plan().curve.start.x() + 12,
                      site.plan().curve.start.y() + 2,
                      site.plan().curve.start.z() + 12);
            })
        .thenIdle(12)
        .thenExecute(
            () -> {
              RailSignalSafetyGameTests.schedule(d, t[0], "Signal recovery terminal");
              stage[0] = 0;
            });
    h.onEachTick(
        () -> {
          if (done[0] || stage[0] < 0) return;
          RailSignalSafetyGameTests.ledger(h, t[0]);
          if (parked[0] != null) {
            if (stage[0] != 1) RailSignalSafetyGameTests.disjoint(h, d, t[0], parked[0]);
            else {
              RailSignalSafetyGameTests.ledger(h, parked[0]);
              h.assertTrue(
                  d.occupancy.canReserve(
                          t[0].id, TrackOccupancy.span(t[0].path, t[0].rear(), t[0].front()))
                      && d.spatial.canReserveShapes(
                          t[0].id, TrainWorld.sweep(d, t[0], t[0].head, t[0].head)),
                  "signal loss overlapped real bodies");
              for (var id : s.claims.body(t[0].id))
                if (s.blocks.region(id) != null && s.claims.occupants(id).size() == 2) {
                  mergedConflict[0] = true;
                  h.assertTrue(
                      !s.claims.available(id, t[0].id), "merged occupied block authorized motion");
                }
            }
          }
          if (stage[0] == 0) {
            if (t[0].speed != 0 || t[0].automation.phase != AutoTrainState.Phase.SIGNAL_WAIT)
              return;
            var player = site.player();
            var old = signal[0];
            player.setPos(old.anchor.getX() + .5, old.anchor.getY(), old.anchor.getZ() + 2.5);
            h.assertTrue(
                TracksidePlacement.remove(player, old.anchor).equals("occupied"),
                "player removed an occupied signal cut");
            stale[0] = s.entity(old);
            head[0] = t[0].head;
            spent[0] = t[0].cars.stream().mapToLong(c -> c.spentJ).sum();
            // Real environmental block loss bypasses the guarded player operation deliberately.
            h.getLevel()
                .setBlockAndUpdate(
                    old.anchor, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            h.assertTrue(
                s.device(old.id) == null && !stale[0].ready(),
                "destroyed root kept a canonical access path");
            devices.remove(old);
            stage[0] = 1;
            held[0] = 0;
          } else if (stage[0] == 1) {
            h.assertTrue(
                t[0].speed == 0
                    && t[0].head == head[0]
                    && t[0].cars.stream().mapToLong(c -> c.spentJ).sum() == spent[0],
                "lost signal allowed motion or purchased traction");
            if (++held[0] < 20 || !mergedConflict[0]) return;
            var old = signal[0];
            var drops =
                h.getLevel()
                    .getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(old.anchor).inflate(6),
                        e ->
                            e.getItem().is(TracksideContent.ITEMS.get(TracksideKind.SIGNAL).get()));
            h.assertTrue(
                drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,
                "destroyed signal refund was lost or duplicated");
            var item = drops.getFirst().getItem().copy();
            drops.forEach(net.minecraft.world.entity.Entity::discard);
            site.player().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, item);
            var plan = TracksidePlacement.plan(h.getLevel(), old.anchor, TracksideKind.SIGNAL, 180);
            h.assertTrue(
                TracksidePlacement.place(
                            site.player(), net.minecraft.world.InteractionHand.MAIN_HAND, plan)
                        .equals("ready")
                    && site.player().getMainHandItem().isEmpty(),
                "real single signal refund did not reform one controller");
            signal[0] = s.at(old.anchor);
            devices.add(signal[0]);
            h.assertTrue(!signal[0].id.equals(old.id), "replacement reused retired identity");
            stage[0] = 2;
            held[0] = 0;
          } else if (stage[0] == 2) {
            if (!s.online(signal[0].point) || !s.runtimeReady(t[0])) return;
            h.assertTrue(
                s.blocks.spans(site.edge()).size() == 2,
                "replacement duplicated a physical/logical cut");
            if (++held[0] < 20) return;
            RailSignalSafetyGameTests.pack(h, parked[0], site);
            parked[0] = null;
            stage[0] = 3;
          } else if (t[0].automation.arrived) {
            done[0] = true;
            RailSignalSafetyGameTests.pack(h, t[0], site);
            RailSignalSafetyGameTests.cleanup(h, List.of(site), devices);
            System.out.println(
                "FACTORYCRAFT_V0320_SIGNAL_REFORM: PASS guardedRemoval=true environmentLoss=true"
                    + " singleRefund=true staleOwnerDenied=true thenActualArrival=true");
            h.succeed();
          }
        });
  }

  private RailTrafficGameTests() {}
}
