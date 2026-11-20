package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.combat.Combat;
import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class TrainCompletionGameTests {
  static ServerPlayer rider(ServerLevel l, StockEntity view, ServerPlayer owner) {
    var p =
        new ServerPlayer(
            l.getServer(), l, owner.getGameProfile(), ClientInformation.createDefault());
    p.connection =
        new net.minecraft.server.network.ServerGamePacketListenerImpl(
            l.getServer(),
            new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
            p,
            net.minecraft.server.network.CommonListenerCookie.createInitial(
                p.getGameProfile(), false)) {
          public void send(net.minecraft.network.protocol.Packet<?> packet) {}

          public void teleport(double x, double y, double z, float yaw, float pitch) {}
        };
    if (!p.startRiding(view)) throw new IllegalStateException("Native rider refused");
    return p;
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 120)
  public static void movingHeadOnTrainsBrakeWithoutOverlapOrUnpaidDamage(GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 340);
    h.runAfterDelay(
        8,
        () -> {
          var a = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 20);
          var b = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 76);
          var d = TrainData.get(h.getLevel());
          var ta = d.owner(a.getUUID());
          var tb = d.owner(b.getUUID());
          var ca = d.car(a.getUUID());
          var cb = d.car(b.getUUID());
          ca.items.setStackInSlot(0, new ItemStack(Items.COAL, 4));
          cb.items.setStackInSlot(0, new ItemStack(Items.COAL, 4));
          var pa = rider(h.getLevel(), a, site.player());
          var pb = rider(h.getLevel(), b, site.player());
          for (int step = 0; step < 220; step++) {
            TrainNetwork.control(pa, a, 1, false, 0);
            TrainNetwork.control(pb, b, -1, false, 0);
            TrainSimulation.tick(d, ta);
            TrainSimulation.tick(d, tb);
            h.assertTrue(ta.front() <= tb.rear() + 1e-7, "head-on claim/body overlap");
            h.assertTrue(
                !a.getBoundingBox().intersects(b.getBoundingBox()), "native train bodies crossed");
          }
          h.assertTrue(
              ta.head > 20 && tb.head < 76 && ta.speed == 0 && tb.speed == 0,
              "head-on motion/braking not exercised");
          h.assertTrue(
              ca.health == 1000 && cb.health == 1000,
              "prevented collision inflicted invented impact damage");
          for (var c : List.of(ca, cb))
            h.assertTrue(
                c.fuelJ + c.spentJ + c.items.getStackInSlot(0).getCount() * 4000000L == 16000000L,
                "head-on finite coal ledger");
          pa.stopRiding();
          pb.stopRiding();
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 120)
  public static void unexpectedWallStopsAtLastClearPoseAndAbsorbsKineticEnergyOnce(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 341);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 30);
          var d = TrainData.get(h.getLevel());
          var t = d.owner(view.getUUID());
          var car = d.car(view.getUUID());
          car.items.setStackInSlot(0, new ItemStack(Items.COAL, 1));
          var wall =
              BlockPos.containing(TrainWorld.vec(site.plan().curve.sample(36, false).position()))
                  .above();
          h.getLevel().setBlockAndUpdate(wall, Blocks.OBSIDIAN.defaultBlockState());
          t.speed = .9;
          TrainSimulation.tick(d, t);
          h.assertTrue(
              t.speed == 0
                  && t.status == TrainData.Status.BLOCKED
                  && car.health < 1000
                  && car.health > 0,
              "wall impact missing");
          h.assertTrue(
              TrainWorld.inspect(d, t, t.head, t.head, null).equals("ready"),
              "impact clipped into wall");
          h.assertTrue(
              car.spentJ == 0 && car.items.getStackInSlot(0).getCount() == 1,
              "coasting collision consumed unrequested fuel");
          double health = car.health, head = t.head;
          TrainSimulation.tick(d, t);
          h.assertTrue(
              car.health == health && t.head == head,
              "stopped train repeatedly invented kinetic damage");
          h.getLevel().removeBlock(wall, false);
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 120)
  public static void mobImpactUsesNativeDamageAttributionAndPreservesTrainCargo(GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 342);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 30);
          var d = TrainData.get(h.getLevel());
          var t = d.owner(view.getUUID());
          var car = d.car(view.getUUID());
          var point = TrainWorld.vec(site.plan().curve.sample(36, false).position());
          var mob = EntityType.COW.create(h.getLevel());
          mob.setNoAi(true);
          mob.setNoGravity(true);
          mob.moveTo(point.x, point.y + .2, point.z);
          h.getLevel().addFreshEntity(mob);
          t.speed = 1.1;
          TrainSimulation.tick(d, t);
          h.assertTrue(
              t.speed > 0
                  && t.head > 30
                  && Math.abs(
                          car.health
                              - (1000
                                  - factorycraft.simulation.combat.Resistance.apply(50, 50, .6)))
                      < 1e-8
                  && mob.getHealth() <= 0,
              "lethal weak mob contact lost momentum, durability or native damage");
          h.assertTrue(
              mob.getLastDamageSource() != null
                  && mob.getLastDamageSource().getDirectEntity() == view,
              "impact lost direct train attribution");
          double health = car.health;
          TrainSimulation.tick(d, t);
          h.assertTrue(car.health == health, "dead mob caused repeated collision energy");
          mob.discard();
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 120)
  public static void destroyedStockReturnsOneComponentExactWreckAndStaleHitsCannotDuplicate(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 343);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.CARGO, 30);
          var d = TrainData.get(h.getLevel());
          var car = d.car(view.getUUID());
          var cargo = new ItemStack(Items.DIAMOND, 31);
          cargo.set(DataComponents.CUSTOM_NAME, Component.literal("wreck exact cargo"));
          car.items.setStackInSlot(0, cargo);
          var port = view.getCapability(Capabilities.ItemHandler.ENTITY);
          var source = h.getLevel().damageSources().explosion(null, null);
          var clone = TrainContent.TYPES.get(StockSpec.CARGO).get().create(h.getLevel());
          clone.setUUID(view.getUUID());
          h.assertTrue(
              !StockPlacement.damage(d, clone, source, 10000)
                  && !StockPlacement.damage(d, view, source, Float.NaN)
                  && car.health == 600,
              "noncanonical/invalid damage reached the real owner");
          h.assertTrue(StockPlacement.damage(d, view, source, 10000), "lethal damage refused");
          h.assertTrue(
              !StockPlacement.damage(d, view, source, 10000)
                  && port.extractItem(0, 64, false).isEmpty(),
              "stale owner duplicated death/cargo");
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      ItemEntity.class,
                      view.getBoundingBox().inflate(2),
                      e -> e.getItem().is(TrainContent.ITEMS.get(StockSpec.CARGO).get()));
          h.assertTrue(
              drops.size() == 1 && d.car(car.id) == null, "death did not retire exactly one owner");
          var restored =
              TrainCar.load(
                  drops
                      .getFirst()
                      .getItem()
                      .get(DataComponents.CUSTOM_DATA)
                      .copyTag()
                      .getCompound("FactoryTrainStock"),
                  h.getLevel().registryAccess(),
                  () -> {},
                  true);
          h.assertTrue(
              restored.health == 0 && ItemStack.matches(restored.items.getStackInSlot(0), cargo),
              "wreck lost health/cargo/components");
          drops.getFirst().discard();
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 120)
  public static void nativeHitscanCanDamageCanonicalWagonsWithoutBypassingRetiredOwners(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 344);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.CARGO, 30);
          var d = TrainData.get(h.getLevel());
          var car = d.car(view.getUUID());
          var from = view.position().add(6, 2, 0);
          var profile = new factorycraft.combat.CombatProfile("bullet", "", 1, 20, 100, 1, 0, 0);
          Combat.hitscan(
              h.getLevel(),
              from,
              new Vec3(-1, 0, 0),
              site.player(),
              d.owner(car.id).scope,
              profile,
              100,
              false);
          h.assertTrue(car.health < 600, "FactoryCraft hitscan ignored nonliving stock");
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  static UUID extra(GameTestHelper h, TrainV31GameTests.Site site, RailPlan plan) {
    RailwayV30GameTests.prepare(h, plan);
    for (long key : plan.footprint.chunks())
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++) {
          long c =
              net.minecraft.world.level.ChunkPos.asLong((int) key + dx, (int) (key >> 32) + dz);
          site.pins().add(c);
          h.getLevel().getChunk((int) c, (int) (c >> 32));
          h.getLevel().setChunkForced((int) c, (int) (c >> 32), true);
        }
    site.player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    var id = RailwayV30GameTests.placed(h, plan, site.player());
    site.player().setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    return id;
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 180)
  public static void movingThreeCarTurnoutKeepsRearHistoryWhenDriverChangesBranch(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 345);
    var o = site.plan().curve.end;
    var straight =
        extra(
            h,
            site,
            new RailPlan(
                RailCurve.plan(o, new RailEnd(o.x(), o.y(), o.z() - 96, new RailHeading(0)))));
    var turnEnd = new RailEnd(o.x() + 24, o.y(), o.z() - 24, new RailHeading(4));
    var right = extra(h, site, new RailPlan(RailCurve.plan(o, turnEnd)));
    var tail =
        extra(
            h,
            site,
            new RailPlan(
                RailCurve.plan(
                    turnEnd, new RailEnd(o.x() + 120, o.y(), o.z() - 24, new RailHeading(4)))));
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 90);
          TrainV31GameTests.stock(h, site, StockSpec.CARGO, 83);
          TrainV31GameTests.stock(h, site, StockSpec.CARGO, 76);
          var d = TrainData.get(h.getLevel());
          var t = d.owner(view.getUUID());
          h.assertTrue(t.cars.size() == 3, "actual three-car coupling");
          d.car(view.getUUID()).items.setStackInSlot(0, new ItemStack(Items.COAL, 8));
          var player = rider(h.getLevel(), view, site.player());
          boolean revised = false, crossed = false;
          for (int step = 0; step < 240; step++) {
            int branch = t.head > 103 ? -1 : 1;
            TrainNetwork.control(player, view, 1, false, branch);
            TrainSimulation.tick(d, t);
            if (t.head > 103) {
              revised = true;
              // Trimming may forget a leg only after the entire consist has left it.
              if (t.rear() < 96 + RailwayData.get(h.getLevel()).segment(right).curve.length())
                h.assertTrue(
                    t.path.legs().stream().anyMatch(l -> l.edge().edge().equals(right)),
                    "driver rewrote occupied turnout history");
            }
            for (int car = 0; car < t.cars.size(); car++) {
              var pose =
                  BogiePose.sample(
                      t.path, RailwayData.get(h.getLevel()).graph, t.center(car), false);
              var nativeView = (StockEntity) h.getLevel().getEntity(t.cars.get(car).id);
              h.assertTrue(
                  nativeView.position().distanceTo(TrainWorld.vec(pose.center())) < 1e-6,
                  "rear bogie detached from historical route");
            }
            if (t.rear() > 140) {
              crossed = true;
              break;
            }
          }
          h.assertTrue(
              revised && crossed,
              "moving fork/rear crossing never occurred: " + t.head + " " + t.status);
          player.stopRiding();
          t.speed = 0;
          t.desired = 0;
          TrainSimulation.tick(d, t);
          for (var c : List.copyOf(t.cars)) {
            var e = h.getLevel().getEntity(c.id);
            site.player().setPos(e.getX() + 2, e.getY(), e.getZ());
            h.assertTrue(
                StockPlacement.pack(d, d.owner(c.id), c.id, site.player()),
                "turnout packing failed");
          }
          for (var id : List.of(straight, right, tail)) RailwayData.get(h.getLevel()).remove(id);
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 180)
  public static void longPushPullConsistRetainsEveryOwnerAcrossChunkQuarantineAndSerialization(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 346);
    var rails = RailwayData.get(h.getLevel());
    var ids = new ArrayList<UUID>();
    ids.add(site.edge());
    var o = site.plan().curve.start;
    for (int part = 1; part < 4; part++)
      ids.add(
          extra(
              h,
              site,
              new RailPlan(
                  RailCurve.plan(
                      new RailEnd(o.x(), o.y(), o.z() - 96 * part, new RailHeading(0)),
                      new RailEnd(o.x(), o.y(), o.z() - 96 * (part + 1), new RailHeading(0))))));
    h.runAfterDelay(
        8,
        () -> {
          var data = TrainData.get(h.getLevel());
          var legs = new ArrayList<TrainPath.Leg>();
          double at = 0;
          for (var id : ids) {
            double len = rails.segment(id).curve.length();
            legs.add(new TrainPath.Leg(new RailGraph.Directed(id, false), at, len));
            at += len;
          }
          var train =
              new TrainData.Train(
                  UUID.randomUUID(),
                  site.player().getUUID(),
                  factorycraft.research.ResearchTeams.resolve(site.player()).orElseThrow(),
                  new TrainPath(legs),
                  300);
          for (int i = 0; i < 32; i++) {
            var kind =
                i == 0 || i == 31
                    ? StockSpec.LOCOMOTIVE
                    : i % 2 == 0 ? StockSpec.FLUID : StockSpec.CARGO;
            var c = new TrainCar(UUID.randomUUID(), kind, data::setDirty);
            if (kind == StockSpec.LOCOMOTIVE) {
              c.items.setStackInSlot(0, new ItemStack(Items.COAL, 2));
              c.reversed = i == 31;
            }
            if (kind == StockSpec.CARGO) {
              var item = new ItemStack(Items.EMERALD, 13);
              item.set(DataComponents.CUSTOM_NAME, Component.literal("long train exact"));
              c.items.setStackInSlot(0, item);
            }
            if (kind == StockSpec.FLUID) {
              var f = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 234567);
              f.set(DataComponents.CUSTOM_NAME, Component.literal("long train typed water"));
              c.fluid.restore(new factorycraft.fluids.FluidKey(f), f.getAmount());
            }
            train.cars.add(c);
          }
          data.add(train);
          TrainWorld.views(data, train);
          var view = (StockEntity) h.getLevel().getEntity(train.cars.getFirst().id);
          var player = rider(h.getLevel(), view, site.player());
          for (int i = 0; i < 25; i++) {
            TrainNetwork.control(player, view, 1, false, 0);
            TrainSimulation.tick(data, train);
          }
          h.assertTrue(
              train.head > 300 && train.cars.getFirst().spentJ > 0,
              "long train never moved with paid source traction");
          var before =
              data.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
          var stale = view.getCapability(Capabilities.ItemHandler.ENTITY);
          long chunk = net.minecraft.world.level.ChunkPos.asLong(view.blockPosition());
          data.unloadChunk(chunk);
          h.assertTrue(
              stale.extractItem(0, 64, false).isEmpty(), "quarantined long train exposed fuel");
          h.assertTrue(
              before.equals(
                  data.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess())),
              "quarantine changed paid ownership/pose");
          var restored = TrainData.load(before, h.getLevel().registryAccess());
          h.assertTrue(
              restored
                  .save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess())
                  .equals(before),
              "long train serialized owners changed");
          data.wakeChunk(chunk);
          player.stopRiding();
          TrainSimulation.tick(data, train);
          for (int i = 0; i < 80; i++) TrainSimulation.tick(data, train);
          h.assertTrue(train.speed == 0, "unattended push-pull train failed to brake after reload");
          for (var c : train.cars) {
            if (c.kind == StockSpec.CARGO)
              h.assertTrue(c.items.getStackInSlot(0).getCount() == 13, "long cargo ledger");
            if (c.kind == StockSpec.FLUID)
              h.assertTrue(c.fluid.amount() == 234567, "long fluid ledger");
          }
          for (var c : List.copyOf(train.cars)) {
            var e = h.getLevel().getEntity(c.id);
            site.player().setPos(e.getX() + 2, e.getY(), e.getZ());
            h.assertTrue(
                StockPlacement.pack(data, data.owner(c.id), c.id, site.player()),
                "long train packing failed");
          }
          for (var id : ids) if (rails.segment(id) != null) rails.remove(id);
          for (long c : site.pins()) h.getLevel().setChunkForced((int) c, (int) (c >> 32), false);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 160)
  public static void opposingMovingForkApproachesKeepOneConflictOwnerWithoutClipping(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 347);
    var o = site.plan().curve.end;
    var branch =
        extra(
            h,
            site,
            new RailPlan(
                RailCurve.plan(o, new RailEnd(o.x() + 24, o.y(), o.z() - 24, new RailHeading(4)))));
    h.runAfterDelay(
        8,
        () -> {
          var a = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 78);
          var d = TrainData.get(h.getLevel());
          var ta = d.owner(a.getUUID());
          var c = new TrainCar(UUID.randomUUID(), StockSpec.LOCOMOTIVE, d::setDirty);
          c.items.setStackInSlot(0, new ItemStack(Items.COAL, 4));
          var tb =
              new TrainData.Train(
                  UUID.randomUUID(),
                  site.player().getUUID(),
                  ta.scope,
                  new TrainPath(
                      RailwayData.get(h.getLevel()).graph, new RailGraph.Directed(branch, true)),
                  10);
          tb.cars.add(c);
          d.add(tb);
          TrainWorld.views(d, tb);
          var b = (StockEntity) h.getLevel().getEntity(c.id);
          d.car(a.getUUID()).items.setStackInSlot(0, new ItemStack(Items.COAL, 4));
          var pa = rider(h.getLevel(), a, site.player());
          var pb = rider(h.getLevel(), b, site.player());
          boolean movedA = false, movedB = false;
          for (int step = 0; step < 200; step++) {
            TrainNetwork.control(pa, a, 1, false, 1);
            TrainNetwork.control(pb, b, 1, false, 0);
            TrainSimulation.tick(d, ta);
            TrainSimulation.tick(d, tb);
            movedA |= ta.head > 78;
            movedB |= tb.head > 10;
            h.assertTrue(
                !a.getBoundingBox().intersects(b.getBoundingBox()), "opposing fork bodies clipped");
            h.assertTrue(
                d.occupancy.canReserve(ta.id, TrackOccupancy.span(ta.path, ta.rear(), ta.front())),
                "fork interval owners overlap");
          }
          h.assertTrue(
              movedA && movedB && ta.speed == 0 && tb.speed == 0,
              "moving fork conflict was not exercised safely");
          h.assertTrue(
              d.car(a.getUUID()).health == 1000 && c.health == 1000,
              "prevented fork collision created damage");
          pa.stopRiding();
          pb.stopRiding();
          site.player().setPos(b.getX() + 2, b.getY(), b.getZ());
          h.assertTrue(StockPlacement.pack(d, d.owner(c.id), c.id, site.player()), "fork recovery");
          TrainV31GameTests.cleanup(h, site);
          RailwayData.get(h.getLevel()).remove(branch);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 120)
  public static void cancelledWreckSpawnKeepsOnePersistentClaimThenRetriesExactlyOnce(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 348);
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.CARGO, 30);
          var d = TrainData.get(h.getLevel());
          d.car(view.getUUID()).items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 23));
          var listener =
              new java.util.function.Consumer<
                  net.neoforged.neoforge.event.entity.EntityJoinLevelEvent>() {
                public void accept(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
                  if (event.getEntity() instanceof ItemEntity item
                      && item.position().distanceToSqr(view.position()) < 4
                      && item.getItem().is(TrainContent.ITEMS.get(StockSpec.CARGO).get()))
                    event.setCanceled(true);
                }
              };
          net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
          try {
            h.assertTrue(StockPlacement.hurt(d, view.getUUID(), 10000), "canonical lethal hit");
            h.assertTrue(
                d.car(view.getUUID()) == null && d.pendingWrecks() == 1,
                "cancelled native join erased/doubled wreck");
            var save = d.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
            var loaded = TrainData.load(save, h.getLevel().registryAccess());
            h.assertTrue(
                loaded
                    .save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess())
                    .equals(save),
                "pending wreck owner does not roundtrip");
            d.tick();
            h.assertTrue(d.pendingWrecks() == 1, "cancelled retry lost claim");
          } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
          }
          d.tick();
          d.tick();
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      ItemEntity.class,
                      view.getBoundingBox().inflate(2),
                      e -> e.getItem().is(TrainContent.ITEMS.get(StockSpec.CARGO).get()));
          h.assertTrue(
              d.pendingWrecks() == 0 && drops.size() == 1,
              "retry did not transfer exactly one persistent wreck");
          var c =
              TrainCar.load(
                  drops
                      .getFirst()
                      .getItem()
                      .get(DataComponents.CUSTOM_DATA)
                      .copyTag()
                      .getCompound("FactoryTrainStock"),
                  h.getLevel().registryAccess(),
                  () -> {},
                  true);
          h.assertTrue(
              c.items.getStackInSlot(0).getCount() == 23 && c.health == 0,
              "cancelled/retried wreck content ledger");
          drops.getFirst().discard();
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 120)
  public static void manualFlameFlightDamagesStockWithoutASecondCargoOwnerOrFictitiousSlowdown(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 349);
    var view = new StockEntity[1];
    h.runAfterDelay(
        8,
        () -> {
          view[0] = TrainV31GameTests.stock(h, site, StockSpec.CARGO, 30);
          var d = TrainData.get(h.getLevel());
          var car = d.car(view[0].getUUID());
          car.items.setStackInSlot(0, new ItemStack(Items.EMERALD, 17));
          var profile = new factorycraft.combat.CombatProfile("fire", "", 1, 18, 2, 1, 0, 0);
          factorycraft.combat.FlameSimulation.launch(
              h.getLevel(),
              view[0].position().add(7, 2, 0),
              new Vec3(-1, 0, 0),
              site.player(),
              d.owner(car.id).scope,
              profile,
              2,
              false);
        });
    h.runAfterDelay(
        48,
        () -> {
          var d = TrainData.get(h.getLevel());
          var car = d.car(view[0].getUUID());
          h.assertTrue(
              car.health < 600 && car.health > 0 && car.items.getStackInSlot(0).getCount() == 17,
              "real flame flight/burn ignored stock or changed cargo");
          h.assertTrue(
              d.owner(car.id).speed == 0 && car.kind.weight == 1000,
              "fire added fictitious native living-entity traction/weight");
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_completion", timeoutTicks = 120)
  public static void deletedCachedLookaheadCannotCrashBogieSamplingOrChargePhantomFuel(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 350);
    var o = site.plan().curve.end;
    var next =
        extra(
            h,
            site,
            new RailPlan(
                RailCurve.plan(o, new RailEnd(o.x(), o.y(), o.z() - 96, new RailHeading(0)))));
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 91.375);
          var d = TrainData.get(h.getLevel());
          var t = d.owner(view.getUUID());
          var c = d.car(view.getUUID());
          c.items.setStackInSlot(0, new ItemStack(Items.COAL, 3));
          TrainSimulation.tick(d, t);
          h.assertTrue(
              t.path.legs().stream().anyMatch(l -> l.edge().edge().equals(next)),
              "fixture did not cache a future leg");
          double head = t.head;
          RailwayData.get(h.getLevel()).remove(next);
          t.speed = 1;
          TrainSimulation.tick(d, t);
          h.assertTrue(
              t.head == head
                  && t.speed == 0
                  && t.status == TrainData.Status.BLOCKED
                  && d.car(c.id) == c,
              "deleted lookahead advanced/lost stock");
          h.assertTrue(
              c.items.getStackInSlot(0).getCount() == 3 && c.spentJ == 0 && c.health == 1000,
              "unknown track charged fuel/impact damage");
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  private TrainCompletionGameTests() {}
}
