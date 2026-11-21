package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.rail.automation.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

/** Moving, finite, native shared-network signal acceptance; no alternate motion engine. */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class RailSignalSafetyGameTests {
  static TrainV31GameTests.Site extend(
      GameTestHelper h, TrainV31GameTests.Site base, RailEnd from, RailEnd to) {
    var plan = new RailPlan(RailCurve.plan(from, to));
    var id = TrainCompletionGameTests.extra(h, base, plan);
    return new TrainV31GameTests.Site(plan, id, base.player(), base.pins());
  }

  static RailAutomationData.Device device(
      GameTestHelper h,
      TrainV31GameTests.Site site,
      TracksideKind kind,
      double at,
      boolean reverse,
      String name) {
    return RailAutomationGameTests.device(h, site, kind, at, reverse, name);
  }

  static void schedule(TrainData d, TrainData.Train t, String name) {
    t.automation.schedule =
        new RailSchedule(
            List.of(
                new RailSchedule.Entry(
                    name,
                    List.of(
                        new RailWait.Condition(
                            RailWait.Type.TIME, "", RailWait.Comparison.GE, 100000, false)))),
            List.of());
    if (!TrainAutomation.automatic(d, t)) throw new IllegalStateException("automatic dispatch");
  }

  /** Explicit finite test inputs. All motion, ownership, projection, claims and fuel are native. */
  static TrainData.Train train(
      GameTestHelper h, TrainV31GameTests.Site site, int cars, double head, boolean reverse) {
    var d = TrainData.get(h.getLevel());
    var t =
        new TrainData.Train(
            UUID.randomUUID(),
            site.player().getUUID(),
            factorycraft.research.ResearchTeams.resolve(site.player()).orElseThrow(),
            new TrainPath(
                RailwayData.get(h.getLevel()).graph, new RailGraph.Directed(site.edge(), false)),
            head);
    for (int i = 0; i < cars; i++) {
      var c =
          new TrainCar(
              UUID.randomUUID(),
              i == 0 || i == cars - 1 ? StockSpec.LOCOMOTIVE : StockSpec.CARGO,
              d::setDirty);
      c.reversed = cars == 1 ? reverse : i == cars - 1;
      if (c.kind == StockSpec.LOCOMOTIVE) c.items.setStackInSlot(0, new ItemStack(Items.COAL, 8));
      else {
        var stack = new ItemStack(Items.IRON_INGOT, 7);
        stack.set(
            net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            net.minecraft.network.chat.Component.literal("finite shared signal cargo"));
        c.items.setStackInSlot(0, stack);
      }
      t.cars.add(c);
    }
    t.direction = reverse ? -1 : 1;
    d.add(t);
    TrainWorld.views(d, t);
    return t;
  }

  static void ledger(GameTestHelper h, TrainData.Train t) {
    for (var car : t.cars) {
      h.assertTrue(car.health == car.kind.health, "signal protection caused kinetic damage");
      if (car.kind == StockSpec.LOCOMOTIVE)
        h.assertTrue(
            car.fuelJ + car.spentJ + car.items.getStackInSlot(0).getCount() * 4_000_000L
                == 32_000_000,
            "signal run changed finite fuel ledger");
      else
        h.assertTrue(
            car.items.getStackInSlot(0).getCount() == 7
                && car.items
                    .getStackInSlot(0)
                    .has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
            "signal run changed cargo/components");
    }
  }

  static void disjoint(GameTestHelper h, TrainData d, TrainData.Train a, TrainData.Train b) {
    h.assertTrue(
        d.occupancy.canReserve(a.id, TrackOccupancy.span(a.path, a.rear(), a.front())),
        "shared physical interval owner overlap");
    h.assertTrue(
        d.spatial.canReserveShapes(a.id, TrainWorld.sweep(d, a, a.head, a.head)),
        "shared oriented hull owner overlap");
    var s = RailAutomationData.get(h.getLevel());
    for (var t : List.of(a, b))
      for (var part : TrackOccupancy.span(t.path, t.rear(), t.front())) {
        var regions = s.blocks.covering(part.edge(), part.min(), part.max());
        if (regions != null)
          for (var region : regions)
            h.assertTrue(
                s.claims.occupants(region).size() <= 1, "two trains occupy the same logical block");
      }
    ledger(h, a);
    ledger(h, b);
  }

  static void pack(GameTestHelper h, TrainData.Train t, TrainV31GameTests.Site site) {
    var d = TrainData.get(h.getLevel());
    TrainAutomation.manual(d, t);
    h.assertTrue(t.speed == 0, "cleanup attempted moving train");
    for (var car : List.copyOf(t.cars)) {
      var entity = h.getLevel().getEntity(car.id);
      h.assertTrue(entity != null, "missing actual stock projection");
      site.player().setPos(entity.getX() + 2, entity.getY(), entity.getZ());
      h.assertTrue(
          StockPlacement.pack(d, d.owner(car.id), car.id, site.player()), "finite stock recovery");
    }
  }

  static void cleanup(
      GameTestHelper h,
      List<TrainV31GameTests.Site> sites,
      List<RailAutomationData.Device> devices) {
    RailAutomationGameTests.removeDevices(h, sites.getFirst(), devices);
    var rails = RailwayData.get(h.getLevel());
    for (var site : sites) if (rails.segment(site.edge()) != null) rails.remove(site.edge());
    for (long key : sites.getFirst().pins())
      h.getLevel().setChunkForced((int) key, (int) (key >> 32), false);
  }

  @GameTest(template = "empty", batch = "rail_safety", timeoutTicks = 1400)
  public static void blueChainAllowsSelectedOpenCurveWhileOtherExitContainsRealStock(
      GameTestHelper h) {
    fork(h, 1100, false);
  }

  @GameTest(template = "empty", batch = "rail_safety", timeoutTicks = 1600)
  public static void blueChainStopsSelectedOccupiedExitThenRecoversWithoutHijacking(
      GameTestHelper h) {
    fork(h, 1101, true);
  }

  private static void fork(GameTestHelper h, int arena, boolean chooseBlocked) {
    var entry = TrainV31GameTests.rail(h, arena);
    var start = entry.plan().curve.end;
    var straight =
        extend(
            h, entry, start, new RailEnd(start.x(), start.y(), start.z() - 96, new RailHeading(0)));
    var branch =
        extend(
            h,
            entry,
            start,
            new RailEnd(start.x() + 32, start.y() + 4, start.z() - 48, new RailHeading(4)));
    var sites = List.of(entry, straight, branch);
    var d = TrainData.get(h.getLevel());
    var s = RailAutomationData.get(h.getLevel());
    var ds = new ArrayList<RailAutomationData.Device>();
    TrainData.Train[] t = {null}, parked = {null};
    RailAutomationData.Device[] chain = {null};
    int[] held = {0};
    double[] last = {0};
    boolean[] done = {false}, used = {false};
    h.startSequence()
        .thenIdle(10)
        .thenExecute(
            () -> {
              chain[0] = device(h, entry, TracksideKind.CHAIN, 70, false, "Shared chain");
              ds.add(chain[0]);
              ds.add(device(h, straight, TracksideKind.SIGNAL, 40, false, "Occupied exit"));
              ds.add(device(h, branch, TracksideKind.SIGNAL, 32, false, "Free exit"));
              ds.add(device(h, straight, TracksideKind.STOP, 82, false, "Straight"));
              ds.add(
                  device(
                      h,
                      branch,
                      TracksideKind.STOP,
                      branch.plan().curve.length() - 5,
                      false,
                      "Curve"));
              parked[0] = train(h, straight, 1, 55, false);
              t[0] = train(h, entry, 3, 28, false);
              entry.player().setPos(start.x() + 12, start.y() + 1, start.z() + 12);
            })
        .thenIdle(12)
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.signals.view(chain[0].point).aspect() == RailSignals.Aspect.BLUE,
                  "actual diverging signal did not become blue");
              h.assertTrue(
                  s.aspect(chain[0]) == RailSignals.Aspect.BLUE,
                  "client-facing blue differs from server");
              schedule(d, t[0], chooseBlocked ? "Straight" : "Curve");
            });
    h.onEachTick(
        () -> {
          if (done[0] || t[0] == null || !t[0].automation.automatic) return;
          disjoint(h, d, t[0], parked[0] == null ? t[0] : parked[0]);
          if (chooseBlocked
              && parked[0] != null
              && t[0].speed == 0
              && t[0].automation.phase == AutoTrainState.Phase.SIGNAL_WAIT) {
            h.assertTrue(
                t[0].front() < chain[0].point.offset(), "nose entered occupied chain junction");
            if (held[0]++ == 0) last[0] = t[0].head;
            h.assertTrue(t[0].head == last[0], "held chain moved");
            if (held[0] == 20) {
              pack(h, parked[0], straight);
              parked[0] = null;
            }
          }
          used[0] |=
              t[0].path.legs().stream()
                  .anyMatch(
                      l -> l.edge().edge().equals(chooseBlocked ? straight.edge() : branch.edge()));
          if (!t[0].automation.arrived) return;
          done[0] = true;
          h.assertTrue(!chooseBlocked || held[0] >= 20, "selected blocked exit was not held");
          h.assertTrue(used[0], "wrong blue branch installed");
          pack(h, t[0], entry);
          if (parked[0] != null) pack(h, parked[0], straight);
          cleanup(h, sites, ds);
          System.out.println(
              "FACTORYCRAFT_V0320_SHARED_BLUE: PASS blockedSelection="
                  + chooseBlocked
                  + " grade=4 cargo=7");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_safety_passing", timeoutTicks = 2400)
  public static void opposingThreeCarTrainsPassOnRealDirectedLoopWithoutSharedBlockOverlap(
      GameTestHelper h) {
    var south = TrainV31GameTests.rail(h, 1102);
    var p = south.plan().curve.end;
    var mainA = extend(h, south, p, new RailEnd(p.x(), p.y(), p.z() - 96, new RailHeading(0)));
    var mainB =
        extend(
            h,
            south,
            mainA.plan().curve.end,
            new RailEnd(p.x(), p.y(), p.z() - 192, new RailHeading(0)));
    var eastA = extend(h, south, p, new RailEnd(p.x() + 16, p.y(), p.z() - 48, new RailHeading(0)));
    var eastB =
        extend(
            h,
            south,
            eastA.plan().curve.end,
            new RailEnd(p.x() + 16, p.y(), p.z() - 144, new RailHeading(0)));
    var eastC = extend(h, south, eastB.plan().curve.end, mainB.plan().curve.end);
    var north =
        extend(
            h,
            south,
            mainB.plan().curve.end,
            new RailEnd(p.x(), p.y(), p.z() - 288, new RailHeading(0)));
    var sites = List.of(south, mainA, mainB, eastA, eastB, eastC, north);
    var ds = new ArrayList<RailAutomationData.Device>();
    var d = TrainData.get(h.getLevel());
    TrainData.Train[] ts = {null, null};
    boolean[] done = {false}, used = {false, false};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              ds.add(device(h, south, TracksideKind.CHAIN, 72, false, "Northbound chain"));
              ds.add(device(h, south, TracksideKind.SIGNAL, 72, true, "South exit"));
              ds.add(device(h, eastA, TracksideKind.SIGNAL, 20, false, "Loop enter"));
              ds.add(device(h, eastC, TracksideKind.CHAIN, 20, false, "Loop leave"));
              ds.add(device(h, mainB, TracksideKind.SIGNAL, 72, true, "Main enter"));
              ds.add(device(h, mainA, TracksideKind.CHAIN, 20, true, "Main leave"));
              ds.add(device(h, north, TracksideKind.SIGNAL, 20, false, "North exit"));
              ds.add(device(h, north, TracksideKind.CHAIN, 20, true, "Southbound chain"));
              ds.add(device(h, north, TracksideKind.STOP, 84, false, "North terminal"));
              ds.add(device(h, south, TracksideKind.STOP, 10, true, "South terminal"));
              ts[0] = train(h, south, 3, 30, false);
              ts[1] = train(h, north, 3, 65, true);
              south.player().setPos(p.x() + 12, p.y() + 1, p.z() + 12);
            })
        .thenIdle(15)
        .thenExecute(
            () -> {
              schedule(d, ts[0], "North terminal");
              schedule(d, ts[1], "South terminal");
            });
    h.onEachTick(
        () -> {
          if (done[0] || ts[0] == null || !ts[0].automation.automatic) return;
          disjoint(h, d, ts[0], ts[1]);
          used[0] |= ts[0].path.legs().stream().anyMatch(l -> l.edge().edge().equals(eastB.edge()));
          used[1] |= ts[1].path.legs().stream().anyMatch(l -> l.edge().edge().equals(mainA.edge()));
          if (!ts[0].automation.arrived || !ts[1].automation.arrived) return;
          done[0] = true;
          h.assertTrue(used[0], "northbound bypass not used");
          h.assertTrue(used[1], "southbound main not used");
          pack(h, ts[0], south);
          pack(h, ts[1], north);
          cleanup(h, sites, ds);
          System.out.println(
              "FACTORYCRAFT_V0320_PASSING_LOOP: PASS actualOpposingTrains=2 cars=6 cargo=14");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_safety_local", timeoutTicks = 900)
  public static void unrelatedRebuildCannotPausePaidNativeMovement(GameTestHelper h) {
    var a = TrainV31GameTests.rail(h, 1103);
    var b = TrainV31GameTests.rail(h, 1104);
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              var stop = device(h, a, TracksideKind.STOP, 82, false, "Local terminal");
              var d = TrainData.get(h.getLevel());
              var s = RailAutomationData.get(h.getLevel());
              var t = train(h, a, 1, 20, false);
              a.player()
                  .setPos(
                      a.plan().curve.end.x() + 12,
                      a.plan().curve.end.y(),
                      a.plan().curve.end.z() + 12);
              h.runAfterDelay(
                  12,
                  () -> {
                    schedule(d, t, "Local terminal");
                    for (int i = 0; i < 12; i++) TrainSimulation.tick(d, t);
                    double before = t.head;
                    long fuel = t.cars.getFirst().spentJ;
                    s.blocks.changed(b.edge());
                    h.assertTrue(
                        !s.runtimeReady() && s.runtimeReady(t),
                        "unrelated edit did not isolate readiness");
                    TrainSimulation.tick(d, t);
                    h.assertTrue(
                        t.head > before && t.cars.getFirst().spentJ > fuel,
                        "unrelated rebuild paused actual paid movement");
                    h.runAfterDelay(
                        400,
                        () -> {
                          ledger(h, t);
                          h.assertTrue(t.automation.arrived, "local train failed to finish");
                          pack(h, t, a);
                          cleanup(h, List.of(a, b), List.of(stop));
                          h.succeed();
                        });
                  });
            });
  }

  @GameTest(template = "empty", batch = "rail_safety_speed", timeoutTicks = 1300)
  public static void sourceMaximumSpeedStopsBeforeOccupiedOrdinaryThenFinishesPaidRoute(
      GameTestHelper h) {
    maximumSpeed(h, false);
  }

  @GameTest(template = "empty", batch = "rail_safety_speed", timeoutTicks = 1300)
  public static void sourceMaximumSpeedChainRedAndLimitedDestinationSurviveIndependentJvm(
      GameTestHelper h) {
    maximumSpeed(h, true);
  }

  static net.minecraft.nbt.CompoundTag saved(GameTestHelper h, UUID id) {
    return TrainData.get(h.getLevel())
        .save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess())
        .getList("Trains", 10)
        .stream()
        .map(n -> (net.minecraft.nbt.CompoundTag) n)
        .filter(n -> n.getUUID("Id").equals(id))
        .findFirst()
        .orElseThrow()
        .copy();
  }

  private static void maximumSpeed(GameTestHelper h, boolean chain) {
    var checkpoint =
        h.getLevel()
            .getDataStorage()
            .computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                    RailAutomationGameTests.ServiceDisk::new,
                    RailAutomationGameTests.ServiceDisk::load),
                "factorycraft_v0320_safety_chain");
    if (chain && checkpoint.data.hasUUID("Train")) {
      var d = TrainData.get(h.getLevel());
      var t = d.train(checkpoint.data.getUUID("Train"));
      var parked = d.train(checkpoint.data.getUUID("Parked"));
      h.assertTrue(t != null && parked != null, "persisted chain red owners missing");
      h.assertTrue(
          saved(h, t.id).equals(checkpoint.data.getCompound("State"))
              && saved(h, parked.id).equals(checkpoint.data.getCompound("ParkedState")),
          "independent JVM altered held train, cargo, fuel or reservations");
      var sys = RailAutomationData.get(h.getLevel());
      h.assertTrue(
          t.automation.phase == AutoTrainState.Phase.SIGNAL_WAIT
              && t.speed == 0
              && Objects.equals(sys.slots.destination(t.id), t.automation.destination)
              && sys.slots.count(t.automation.destination) == 1,
          "chain red / limited destination commitment not restored exactly once");
      ledger(h, t);
      ledger(h, parked);
      System.out.println(
          "FACTORYCRAFT_V0320_CHAIN_DISK: VERIFIED exact held speed=0 slot=1 train=" + t.id);
      h.succeed();
      return;
    }
    var base = TrainV31GameTests.rail(h, chain ? 1106 : 1105);
    var sites = new ArrayList<TrainV31GameTests.Site>();
    sites.add(base);
    for (int i = 0; i < 4; i++) {
      var e = sites.getLast().plan().curve.end;
      sites.add(extend(h, base, e, new RailEnd(e.x(), e.y(), e.z() - 96, new RailHeading(0))));
    }
    var d = TrainData.get(h.getLevel());
    var ds = new ArrayList<RailAutomationData.Device>();
    TrainData.Train[] t = {null}, parked = {null};
    RailAutomationData.Device[] red = {null};
    boolean[] injected = {false}, done = {false};
    int[] held = {0};
    double[] previous = {0};
    long[] heldFuel = {0};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              red[0] =
                  device(
                      h,
                      sites.get(2),
                      chain ? TracksideKind.CHAIN : TracksideKind.SIGNAL,
                      20,
                      false,
                      "Maximum speed red");
              ds.add(red[0]);
              if (chain)
                ds.add(
                    device(
                        h, sites.get(2), TracksideKind.SIGNAL, 60, false, "Occupied chain exit"));
              var target =
                  device(
                      h, sites.getLast(), TracksideKind.STOP, 84, false, "Maximum speed terminal");
              ds.add(target);
              var sys = RailAutomationData.get(h.getLevel());
              var settings = target.settings();
              settings.putInt("Limit", 1);
              sys.configure(target, settings);
              parked[0] = train(h, sites.get(3), 1, 24, false);
              t[0] = train(h, base, 3, 25, false);
              base.player()
                  .setPos(
                      base.plan().curve.start.x() + 12,
                      base.plan().curve.start.y(),
                      base.plan().curve.start.z() + 12);
            })
        .thenIdle(12)
        .thenExecute(() -> schedule(d, t[0], "Maximum speed terminal"));
    h.onEachTick(
        () -> {
          if (done[0] || t[0] == null || !t[0].automation.routed) return;
          if (!injected[0]) {
            // Declared diagnostic initial kinetic state, not a claim of survival acceleration.
            t[0].speed = 1.2;
            injected[0] = true;
            previous[0] = t[0].head;
            h.assertTrue(t[0].speed * 216 == 259.2, "source maximum speed fixture");
          }
          h.assertTrue(t[0].head >= previous[0] - 1e-7, "red stop snapped backwards");
          previous[0] = t[0].head;
          if (parked[0] != null) {
            disjoint(h, d, t[0], parked[0]);
            h.assertTrue(
                t[0].front() < 192 + red[0].point.offset() + 1e-7,
                "maximum-speed nose crossed red");
            if (t[0].speed == 0 && t[0].automation.phase == AutoTrainState.Phase.SIGNAL_WAIT) {
              if (held[0]++ == 0)
                heldFuel[0] = t[0].cars.getFirst().spentJ + t[0].cars.getLast().spentJ;
              h.assertTrue(
                  heldFuel[0] == t[0].cars.getFirst().spentJ + t[0].cars.getLast().spentJ,
                  "red wait debited fuel");
              if (held[0] == 20) {
                if (chain) {
                  done[0] = true;
                  for (long key : base.pins()) {
                    d.unloadChunk(key);
                    h.getLevel().setChunkForced((int) key, (int) (key >> 32), false);
                  }
                  h.runAfterDelay(
                      50,
                      () -> {
                        checkpoint.data.putUUID("Train", t[0].id);
                        checkpoint.data.putUUID("Parked", parked[0].id);
                        checkpoint.data.put("State", saved(h, t[0].id));
                        checkpoint.data.put("ParkedState", saved(h, parked[0].id));
                        checkpoint.setDirty();
                        d.setDirty();
                        System.out.println(
                            "FACTORYCRAFT_V0320_CHAIN_DISK: SEEDED maxInitial=259.2 heldTicks=20"
                                + " slot=1 train="
                                + t[0].id);
                        h.succeed();
                      });
                } else {
                  pack(h, parked[0], sites.get(3));
                  parked[0] = null;
                }
              }
            }
          } else if (t[0].automation.arrived) {
            done[0] = true;
            ledger(h, t[0]);
            h.assertTrue(held[0] >= 20, "maximum-speed red wait not exercised");
            pack(h, t[0], base);
            cleanup(h, sites, ds);
            System.out.println(
                "FACTORYCRAFT_V0320_MAXIMUM_RED: PASS initial=259.2 ordinary stopped then arrived"
                    + " cargo=7");
            h.succeed();
          }
        });
  }

  @GameTest(template = "empty", batch = "rail_safety_long", timeoutTicks = 2400)
  public static void oneThreeSixteenAndSixtyFourCarsKeepEveryBodyBlockUntilTailClears(
      GameTestHelper h) {
    var base = TrainV31GameTests.rail(h, 1107);
    var sites = new ArrayList<TrainV31GameTests.Site>();
    sites.add(base);
    for (int i = 0; i < 5; i++) {
      var e = sites.getLast().plan().curve.end;
      sites.add(extend(h, base, e, new RailEnd(e.x(), e.y(), e.z() - 96, new RailHeading(0))));
    }
    var ds = new ArrayList<RailAutomationData.Device>();
    var d = TrainData.get(h.getLevel());
    var sys = RailAutomationData.get(h.getLevel());
    int[] sizes = {1, 3, 16, 64}, at = {0};
    TrainData.Train[] t = {null};
    boolean[] ready = {false}, straddled = {false};
    long[] ticks = {0}, totalNs = {0};
    h.startSequence()
        .thenIdle(12)
        .thenExecute(
            () -> {
              for (int i = 0; i < sites.size(); i++)
                ds.add(
                    device(
                        h,
                        sites.get(i),
                        TracksideKind.SIGNAL,
                        i == 5 ? 16 : 48,
                        false,
                        "Long body boundary"));
              ds.add(device(h, sites.getLast(), TracksideKind.STOP, 84, false, "Long terminal"));
              base.player()
                  .setPos(
                      base.plan().curve.start.x() + 12,
                      base.plan().curve.start.y(),
                      base.plan().curve.start.z() + 12);
            })
        .thenIdle(12)
        .thenExecute(() -> ready[0] = true);
    h.onEachTick(
        () -> {
          if (!ready[0]) return;
          if (t[0] == null) {
            var legs = new ArrayList<TrainPath.Leg>();
            double offset = 0;
            for (var site : sites) {
              legs.add(
                  new TrainPath.Leg(
                      new RailGraph.Directed(site.edge(), false),
                      offset,
                      site.plan().curve.length()));
              offset += site.plan().curve.length();
            }
            var owner =
                new TrainData.Train(
                    UUID.randomUUID(),
                    base.player().getUUID(),
                    factorycraft.research.ResearchTeams.resolve(base.player()).orElseThrow(),
                    new TrainPath(legs),
                    464);
            for (int i = 0; i < sizes[at[0]]; i++) {
              var car =
                  new TrainCar(
                      UUID.randomUUID(),
                      i == 0 || i == sizes[at[0]] - 1 || i % 8 == 0
                          ? StockSpec.LOCOMOTIVE
                          : StockSpec.CARGO,
                      d::setDirty);
              car.reversed = i == sizes[at[0]] - 1 && i > 0;
              if (car.kind == StockSpec.LOCOMOTIVE)
                car.items.setStackInSlot(0, new ItemStack(Items.COAL, 8));
              else {
                var stack = new ItemStack(Items.IRON_INGOT, 7);
                stack.set(
                    net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal("finite shared signal cargo"));
                car.items.setStackInSlot(0, stack);
              }
              owner.cars.add(car);
            }
            d.add(owner);
            TrainWorld.views(d, owner);
            schedule(d, owner, "Long terminal");
            t[0] = owner;
            straddled[0] = false;
            ticks[0] = 0;
            totalNs[0] = d.simulationNs;
            return;
          }
          var owner = t[0];
          ticks[0]++;
          if (owner.front() > 496 && owner.rear() < 496) straddled[0] = true;
          for (var span : TrackOccupancy.span(owner.path, owner.rear(), owner.front())) {
            var regions = sys.blocks.covering(span.edge(), span.min(), span.max());
            h.assertTrue(regions != null, "live body lost region availability");
            for (var region : regions) {
              h.assertTrue(
                  sys.claims.occupants(region).equals(Set.of(owner.id)),
                  "last wagon's block released at locomotive departure");
              h.assertTrue(
                  !sys.claims.reserve(UUID.randomUUID(), Set.of(region)),
                  "another train hijacked actual body block");
            }
          }
          ledger(h, owner);
          if (!owner.automation.arrived) return;
          h.assertTrue(straddled[0], "moving consist did not straddle boundary");
          var before = sys.blocks.at(sites.getLast().edge(), 16, false);
          if (owner.rear() > 496)
            h.assertTrue(
                !sys.claims.occupants(before.id).contains(owner.id),
                "cleared caboose left ghost block");
          else
            h.assertTrue(
                sys.claims.occupants(before.id).contains(owner.id),
                "long tail lost occupied block");
          System.out.println(
              "FACTORYCRAFT_V0320_LONG_BODY: PASS cars="
                  + owner.cars.size()
                  + " ticks="
                  + ticks[0]
                  + " batchAdapterNs="
                  + (d.simulationNs - totalNs[0]));
          pack(h, owner, base);
          t[0] = null;
          at[0]++;
          if (at[0] == sizes.length) {
            ready[0] = false;
            cleanup(h, sites, ds);
            h.succeed();
          }
        });
  }

  private RailSignalSafetyGameTests() {}
}
