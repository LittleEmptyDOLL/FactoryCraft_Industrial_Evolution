package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.simulation.trains.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class TrainMomentumGameTests {
  private static TrainV31GameTests.Site longRail(GameTestHelper h, int id) {
    var site = TrainV31GameTests.rail(h, id);
    var end = site.plan().curve.end;
    var extra =
        TrainCompletionGameTests.extra(
            h,
            site,
            new factorycraft.rail.RailPlan(
                factorycraft.simulation.rail.RailCurve.plan(
                    end,
                    new factorycraft.simulation.rail.RailEnd(
                        end.x(), end.y(), end.z() - 96, end.heading()))));
    return site;
  }

  private static LivingEntity cow(GameTestHelper h, TrainV31GameTests.Site site, double at) {
    var point = TrainWorld.vec(site.plan().curve.sample(at, false).position());
    var mob = EntityType.COW.create(h.getLevel());
    mob.setNoAi(true);
    mob.setNoGravity(true);
    mob.moveTo(point.x, point.y + .2, point.z);
    h.getLevel().addFreshEntity(mob);
    return mob;
  }

  @GameTest(template = "empty", batch = "train_momentum", timeoutTicks = 160)
  public static void maximumSpeedKillsThreeMobsInTravelOrderWithOneFuelDebit(GameTestHelper h) {
    var site = longRail(h, 450);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 20);
          var d = TrainData.get(h.getLevel());
          var t = d.owner(view.getUUID());
          var car = d.car(view.getUUID());
          car.items.setStackInSlot(0, new ItemStack(Items.COAL, 2));
          var rider = TrainCompletionGameTests.rider(h.getLevel(), view, site.player());
          var far = cow(h, site, 25.8);
          var middle = cow(h, site, 25);
          var near = cow(h, site, 24.1);
          var order = new ArrayList<UUID>();
          java.util.function.Consumer<LivingDeathEvent> listener =
              e -> {
                if (List.of(far, middle, near).contains(e.getEntity()))
                  order.add(e.getEntity().getUUID());
              };
          NeoForge.EVENT_BUS.addListener(listener);
          try {
            t.speed = 1.2;
            TrainNetwork.control(rider, view, 1, false, 0);
            TrainSimulation.tick(d, t);
          } finally {
            NeoForge.EVENT_BUS.unregister(listener);
          }
          h.assertTrue(
              order.equals(List.of(near.getUUID(), middle.getUUID(), far.getUUID())),
              "collision order/missing kills: " + order);
          h.assertTrue(
              t.speed > 0
                  && t.speed < 1.2
                  && t.head > 21.8
                  && Math.abs(
                          car.health
                              - (1000
                                  - 3
                                      * factorycraft.simulation.combat.Resistance.apply(
                                          50, 50, .6)))
                      < 1e-8,
              "lethal crowd lost motion/durability: speed="
                  + t.speed
                  + " head="
                  + t.head
                  + " health="
                  + car.health);
          h.assertTrue(
              car.spentJ == 30_000
                  && car.fuelJ + car.spentJ + 4_000_000L * car.items.getStackInSlot(0).getCount()
                      == 8_000_000,
              "crowd paid fuel more than once");
          h.assertTrue(
              Math.abs(view.speed() - t.speed) < 1e-6,
              "temporary callback stop escaped the visual throttle: " + view.speed());
          h.assertTrue(
              near.getLastDamageSource().getDirectEntity() == view
                  && near.getLastDamageSource().getEntity() == rider,
              "native driver/stock attribution");
          double health = car.health;
          TrainSimulation.tick(d, t);
          h.assertTrue(car.health == health, "dead mob replayed impact");
          for (var mob : List.of(far, middle, near)) mob.discard();
          rider.stopRiding();
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_momentum", timeoutTicks = 160)
  public static void cancelledDamageAndSurvivingHeavyTargetsAreNotPassThroughKills(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 451);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 20);
          var d = TrainData.get(h.getLevel());
          var t = d.owner(view.getUUID());
          var mob = cow(h, site, 24.2);
          java.util.function.Consumer<LivingIncomingDamageEvent> listener =
              e -> {
                if (e.getEntity() == mob) e.setCanceled(true);
              };
          NeoForge.EVENT_BUS.addListener(listener);
          try {
            t.speed = 1.2;
            TrainSimulation.tick(d, t);
          } finally {
            NeoForge.EVENT_BUS.unregister(listener);
          }
          h.assertTrue(
              t.speed == 0 && mob.getHealth() == 10 && t.head < 21,
              "cancelled hurt became a kill or tunnelled");
          mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
              .setBaseValue(100);
          mob.setHealth(100); // Explicit test-only heavy health; no subsequent reset or refill.
          t.speed = .1;
          TrainSimulation.tick(d, t);
          h.assertTrue(
              t.speed == 0 && mob.isAlive() && mob.getHealth() > 90,
              "surviving heavy target was ignored");
          mob.discard();
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_momentum", timeoutTicks = 160)
  public static void deathCallbackWallIsRecheckedBeforeRemainingMotion(GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 452);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 20);
          var d = TrainData.get(h.getLevel());
          var t = d.owner(view.getUUID());
          var mob = cow(h, site, 24.1);
          var point = site.plan().curve.sample(26, false).position();
          var wall = BlockPos.containing(point.x(), point.y() + 1, point.z());
          java.util.function.Consumer<LivingDeathEvent> listener =
              e -> {
                if (e.getEntity() == mob)
                  h.getLevel().setBlock(wall, Blocks.OBSIDIAN.defaultBlockState(), 18);
              };
          NeoForge.EVENT_BUS.addListener(listener);
          try {
            t.speed = 1.2;
            TrainSimulation.tick(d, t);
          } finally {
            NeoForge.EVENT_BUS.unregister(listener);
          }
          h.assertTrue(
              mob.getHealth() <= 0 && t.speed == 0 && t.status == TrainData.Status.BLOCKED,
              "new wall was teleported over");
          h.assertTrue(
              TrainWorld.inspect(d, t, t.head, t.head, null).equals("ready"),
              "wall stop was not last clear pose");
          h.getLevel().removeBlock(wall, false);
          mob.discard();
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_momentum", timeoutTicks = 160)
  public static void deathCallbackRetiresSourceAndReentrantTickCannotDuplicateFuelOrWreck(
      GameTestHelper h) {
    var site = longRail(h, 453);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 20);
          var d = TrainData.get(h.getLevel());
          var t = d.owner(view.getUUID());
          var car = d.car(view.getUUID());
          car.items.setStackInSlot(0, new ItemStack(Items.COAL, 2));
          var rider = TrainCompletionGameTests.rider(h.getLevel(), view, site.player());
          var mob = cow(h, site, 24.1);
          java.util.function.Consumer<LivingDeathEvent> listener =
              e -> {
                if (e.getEntity() == mob) {
                  TrainSimulation.tick(d, t);
                  StockPlacement.hurt(d, car.id, 10000);
                }
              };
          NeoForge.EVENT_BUS.addListener(listener);
          try {
            t.speed = 1.2;
            TrainNetwork.control(rider, view, 1, false, 0);
            TrainSimulation.tick(d, t);
          } finally {
            NeoForge.EVENT_BUS.unregister(listener);
          }
          h.assertTrue(
              d.car(car.id) == null && car.spentJ == 30000,
              "retired source was moved or billed twice");
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new net.minecraft.world.phys.AABB(view.position(), view.position())
                          .inflate(12),
                      e -> e.getItem().is(TrainContent.ITEMS.get(StockSpec.LOCOMOTIVE).get()));
          h.assertTrue(
              drops.size() + d.pendingWrecks() == 1, "retirement created duplicate/lost wrecks");
          for (var drop : drops) drop.discard();
          mob.discard();
          rider.stopRiding();
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_impact_profile", timeoutTicks = 200)
  public static void boundedCollisionProfileRecordsActualSpeedVictimsReadsAndCallbacks(
      GameTestHelper h) {
    var site = longRail(h, 500);
    var at =
        new BlockPos(
            site.plan().curve.start.x() + 8,
            site.plan().curve.start.y(),
            site.plan().curve.start.z());
    h.getLevel().setBlock(at, Blocks.CHEST.defaultBlockState(), 18);
    var outputs =
        (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(at);
    h.runAfterDelay(
        8,
        () -> {
          var samples = new ArrayList<Long>();
          long reads = 0, callbacks = 0;
          int deaths = 0, continuing = 0;
          for (int trial = 0; trial < 24; trial++) {
            var view =
                TrainV31GameTests.stock(
                    h,
                    site,
                    StockSpec.LOCOMOTIVE,
                    20,
                    new ItemStack(TrainContent.ITEMS.get(StockSpec.LOCOMOTIVE).get(), 1));
            var d = TrainData.get(h.getLevel());
            var t = d.owner(view.getUUID());
            var car = d.car(view.getUUID());
            car.items.setStackInSlot(0, new ItemStack(Items.COAL, 1));
            var rider = TrainCompletionGameTests.rider(h.getLevel(), view, site.player());
            var targets = List.of(cow(h, site, 25.8), cow(h, site, 25), cow(h, site, 24.1));
            int[] count = {0};
            java.util.function.Consumer<LivingIncomingDamageEvent> observer =
                e -> {
                  if (targets.contains(e.getEntity())) count[0]++;
                };
            NeoForge.EVENT_BUS.addListener(observer);
            long before = d.worldReads, started = System.nanoTime();
            try {
              t.speed = 1.2;
              TrainNetwork.control(rider, view, 1, false, 0);
              TrainSimulation.tick(d, t);
            } finally {
              NeoForge.EVENT_BUS.unregister(observer);
            }
            long nanos = System.nanoTime() - started;
            h.assertTrue(
                car.spentJ == 30000 && car.fuelJ + car.spentJ == 4_000_000,
                "profile finite fuel ledger: " + t.status + " spent=" + car.spentJ);
            if (trial >= 8) {
              samples.add(nanos);
              reads += d.worldReads - before;
              callbacks += count[0];
              deaths += (int) targets.stream().filter(e -> e.getHealth() <= 0).count();
              if (t.speed > 0) continuing++;
            }
            System.out.println(
                "FACTORYCRAFT_IMPACT_SAMPLE trial="
                    + trial
                    + " nanos="
                    + nanos
                    + " reads="
                    + (d.worldReads - before)
                    + " callbacks="
                    + count[0]
                    + " sourceSpeed="
                    + t.speed
                    + " head="
                    + t.head);
            for (var target : targets) target.discard();
            rider.stopRiding();
            for (int brakeTick = 0; t.speed > 0 && brakeTick < 200; brakeTick++)
              TrainSimulation.tick(d, t);
            h.assertTrue(
                t.speed == 0 && car.spentJ == 30000,
                "profile brake altered the finite fuel ledger");
            site.player().setPos(view.getX() + 2, view.getY(), view.getZ());
            h.assertTrue(
                StockPlacement.pack(d, t, car.id, site.player()), "profile output packing failed");
            int slot = -1;
            for (int i = 0; i < site.player().getInventory().getContainerSize(); i++) {
              var stack = site.player().getInventory().getItem(i);
              var tag =
                  stack
                      .getOrDefault(
                          net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                          net.minecraft.world.item.component.CustomData.EMPTY)
                      .copyTag();
              if (tag.getCompound("FactoryTrainStock").hasUUID("Id")
                  && tag.getCompound("FactoryTrainStock").getUUID("Id").equals(car.id)) {
                slot = i;
                break;
              }
            }
            h.assertTrue(
                slot >= 0 && outputs.getItem(trial).isEmpty(), "portable result has no sole owner");
            outputs.setItem(trial, site.player().getInventory().removeItemNoUpdate(slot));
          }
          samples.sort(Long::compare);
          System.out.println(
              "FACTORYCRAFT_IMPACT_PROFILE samples=16 meanNs="
                  + samples.stream().mapToLong(Long::longValue).average().orElseThrow()
                  + " p95Ns="
                  + samples.get(15)
                  + " maxNs="
                  + samples.get(15)
                  + " reads="
                  + reads
                  + " callbacks="
                  + callbacks
                  + " deaths="
                  + deaths
                  + " continuing="
                  + continuing);
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_momentum", timeoutTicks = 160)
  public static void denseCrowdHasABoundedHitBudgetAndPreservesResidualMomentum(GameTestHelper h) {
    var site = longRail(h, 454);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 36);
          for (int i = 1; i < 4; i++)
            TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 36 - i * 7);
          var d = TrainData.get(h.getLevel());
          var train = d.owner(view.getUUID());
          h.assertTrue(train.cars.size() == 4, "actual four-engine consist missing");
          for (var car : train.cars) car.items.setStackInSlot(0, new ItemStack(Items.COAL, 1));
          var mobs = new ArrayList<LivingEntity>();
          for (int i = 0; i < 24; i++) mobs.add(cow(h, site, 40.1 + i * .05));
          var rider = TrainCompletionGameTests.rider(h.getLevel(), view, site.player());
          long before = d.impactCallbacks, scans = d.contactScans;
          train.speed = 1.2;
          TrainNetwork.control(rider, view, 1, false, 0);
          TrainSimulation.tick(d, train);
          h.assertTrue(
              d.impactCallbacks - before == 16 && d.contactScans - scans <= 16,
              "unbounded crowd callback/scan work");
          h.assertTrue(
              mobs.stream().filter(m -> m.getHealth() <= 0).count() == 16 && train.speed > 0,
              "budget erased momentum or skipped living blockers");
          h.assertTrue(
              train.cars.stream().mapToLong(c -> c.spentJ).sum() == 120000,
              "crowd repeated engine payment");
          for (var mob : mobs) mob.discard();
          rider.stopRiding();
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_momentum_disk", timeoutTicks = 160)
  public static void reverseRearWagonImpactAndExactPaidOwnersSurviveIndependentJvm(
      GameTestHelper h) {
    var level = h.getLevel();
    var d = TrainData.get(level);
    var disk =
        level
            .getDataStorage()
            .computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                    TrainV31GameTests.Disk::new, TrainV31GameTests.Disk::load),
                "factorycraft_momentum_checkpoint");
    if (disk.data.hasUUID("Train")) {
      var id = disk.data.getUUID("Train");
      var t = d.train(id);
      h.assertTrue(
          t != null && trainTag(d, id, level).equals(disk.data.getCompound("State")),
          "momentum UUID/route/paid phase/resources changed across JVMs");
      h.assertTrue(
          t.direction == -1 && t.speed > 0 && t.cars.size() == 3,
          "reverse impact phase not retained");
      System.out.println("FACTORYCRAFT_MOMENTUM_DISK: VERIFIED " + id);
      h.succeed();
      return;
    }
    var site = TrainV31GameTests.rail(h, 455);
    h.runAfterDelay(
        8,
        () -> {
          var loco = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 80);
          var cargo = TrainV31GameTests.stock(h, site, StockSpec.CARGO, 73);
          var tank = TrainV31GameTests.stock(h, site, StockSpec.FLUID, 66);
          var t = d.owner(loco.getUUID());
          var engine = d.car(loco.getUUID());
          engine.items.setStackInSlot(0, new ItemStack(Items.COAL, 3));
          var item = new ItemStack(Items.DIAMOND, 31);
          item.set(
              net.minecraft.core.component.DataComponents.CUSTOM_NAME,
              net.minecraft.network.chat.Component.literal("momentum cargo"));
          d.car(cargo.getUUID()).items.setStackInSlot(0, item);
          var fluid =
              new net.neoforged.neoforge.fluids.FluidStack(
                  net.minecraft.world.level.material.Fluids.WATER, 234567);
          fluid.set(
              net.minecraft.core.component.DataComponents.CUSTOM_NAME,
              net.minecraft.network.chat.Component.literal("momentum fluid"));
          d.car(tank.getUUID()).fluid.restore(new factorycraft.fluids.FluidKey(fluid), 234567);
          var mob = cow(h, site, 61.6);
          var rider = TrainCompletionGameTests.rider(level, loco, site.player());
          t.direction = -1;
          t.speed = .6;
          TrainNetwork.control(rider, loco, -1, false, 0);
          TrainSimulation.tick(d, t);
          h.assertTrue(
              mob.getHealth() <= 0 && t.speed > 0 && t.head < 80 && engine.spentJ == 30000,
              "reverse rear contact lost momentum/fuel: " + t.status);
          h.assertTrue(
              mob.getLastDamageSource().getDirectEntity() == tank,
              "rear impact attributed to wrong car");
          h.assertTrue(
              engine.fuelJ + engine.spentJ + 4_000_000L * engine.items.getStackInSlot(0).getCount()
                      == 12_000_000
                  && ItemStack.matches(d.car(cargo.getUUID()).items.getStackInSlot(0), item)
                  && d.car(tank.getUUID()).fluid.amount() == 234567,
              "reverse resource ledger");
          mob.discard();
          rider.stopRiding();
          d.unloadChunk(net.minecraft.world.level.ChunkPos.asLong(loco.blockPosition()));
          disk.data.putUUID("Train", t.id);
          disk.data.put("State", trainTag(d, t.id, level).copy());
          disk.setDirty();
          d.setDirty();
          for (long key : site.pins()) level.setChunkForced((int) key, (int) (key >> 32), false);
          System.out.println("FACTORYCRAFT_MOMENTUM_DISK: SEEDED " + t.id);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_momentum", timeoutTicks = 160)
  public static void maximumSpeedLethalContactOnACurvedGradeKeepsPhysicalPoseAndFuel(
      GameTestHelper h) {
    var base = TrainV31GameTests.rail(h, 457);
    var origin = base.plan().curve.end;
    var end =
        new factorycraft.simulation.rail.RailEnd(
            origin.x() + 48,
            origin.y() + 8,
            origin.z() - 48,
            new factorycraft.simulation.rail.RailHeading(4));
    var plan =
        new factorycraft.rail.RailPlan(factorycraft.simulation.rail.RailCurve.plan(origin, end));
    var ids = new ArrayList<UUID>();
    ids.add(base.edge());
    var curve = TrainCompletionGameTests.extra(h, base, plan);
    var last = end;
    for (int i = 0; i < 2; i++) {
      var next =
          new factorycraft.simulation.rail.RailEnd(
              last.x() + 96, last.y(), last.z(), last.heading());
      ids.add(
          TrainCompletionGameTests.extra(
              h,
              base,
              new factorycraft.rail.RailPlan(
                  factorycraft.simulation.rail.RailCurve.plan(last, next))));
      last = next;
    }
    var site = new TrainV31GameTests.Site(plan, curve, base.player(), base.pins());
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 20);
          var d = TrainData.get(h.getLevel());
          var train = d.owner(view.getUUID());
          var car = d.car(view.getUUID());
          car.items.setStackInSlot(0, new ItemStack(Items.COAL, 2));
          var rider = TrainCompletionGameTests.rider(h.getLevel(), view, site.player());
          var mob = cow(h, site, 26);
          train.speed = 1.2;
          TrainNetwork.control(rider, view, 1, false, 0);
          TrainSimulation.tick(d, train);
          h.assertTrue(
              mob.getHealth() <= 0 && train.speed > 0 && train.head > 20 && car.spentJ == 30000,
              "curved-grade lethal contact stalled or lost payment: " + train.status);
          var pose =
              BogiePose.sample(
                  train.path,
                  factorycraft.rail.RailwayData.get(h.getLevel()).graph,
                  train.head,
                  false);
          h.assertTrue(
              view.position().distanceTo(TrainWorld.vec(pose.center())) < 1e-6
                  && TrainWorld.inspect(d, train, train.head, train.head, null).equals("ready"),
              "curved-grade owner/projection clipped");
          mob.discard();
          rider.stopRiding();
          TrainV31GameTests.cleanup(h, site);
          for (var id : ids)
            if (factorycraft.rail.RailwayData.get(h.getLevel()).segment(id) != null)
              factorycraft.rail.RailwayData.get(h.getLevel()).remove(id);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_momentum", timeoutTicks = 160)
  public static void cancelledNativeDeathIsNotInferredFromZeroHealth(GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 456);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 20);
          var d = TrainData.get(h.getLevel());
          var train = d.owner(view.getUUID());
          var target = cow(h, site, 24.1);
          boolean[] seen = {false};
          java.util.function.Consumer<LivingDeathEvent> cancel =
              e -> {
                if (e.getEntity() == target) {
                  seen[0] = true;
                  e.setCanceled(true);
                }
              };
          NeoForge.EVENT_BUS.addListener(cancel);
          try {
            train.speed = 1.2;
            TrainSimulation.tick(d, train);
          } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
          }
          h.assertTrue(
              seen[0] && target.getHealth() == 0 && !target.isRemoved() && train.speed == 0,
              "cancellable death was guessed from attempted damage/zero health");
          h.assertTrue(
              TrainWorld.inspect(d, train, train.head, train.head + 1, null).equals("entity"),
              "zero-health canceled death disappeared from the next sweep");
          target.discard();
          cleanup(h, site);
          h.succeed();
        });
  }

  private static net.minecraft.nbt.CompoundTag trainTag(
      TrainData d, UUID id, net.minecraft.server.level.ServerLevel level) {
    return d
        .save(new net.minecraft.nbt.CompoundTag(), level.registryAccess())
        .getList("Trains", net.minecraft.nbt.Tag.TAG_COMPOUND)
        .stream()
        .map(x -> (net.minecraft.nbt.CompoundTag) x)
        .filter(x -> x.getUUID("Id").equals(id))
        .findFirst()
        .orElseThrow();
  }

  private static void cleanup(GameTestHelper h, TrainV31GameTests.Site site) {
    var rails = factorycraft.rail.RailwayData.get(h.getLevel());
    var extras =
        rails.graph.successors(
            new factorycraft.simulation.rail.RailGraph.Directed(site.edge(), false));
    TrainV31GameTests.cleanup(h, site);
    for (var edge : extras) if (rails.segment(edge.edge()) != null) rails.remove(edge.edge());
  }

  private TrainMomentumGameTests() {}
}
