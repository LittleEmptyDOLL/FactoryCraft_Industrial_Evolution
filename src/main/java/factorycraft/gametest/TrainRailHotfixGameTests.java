package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class TrainRailHotfixGameTests {
  static RailPlan diagonal(BlockPos at) {
    return new RailPlan(
        RailCurve.plan(
            new RailEnd(at.getX(), at.getY(), at.getZ(), new RailHeading(2)),
            new RailEnd(at.getX() + 32, at.getY(), at.getZ() - 32, new RailHeading(2))));
  }

  @GameTest(template = "empty", batch = "rail_v0311", timeoutTicks = 100)
  public static void nonCollidingBedPlantsAndOverheadTorchDoNotBlockButGlassStillDoes(
      GameTestHelper h) {
    for (int heading = 0; heading < 16; heading++) {
      var o = PowerV20GameTests.site(h, 410 + heading);
      var start = new RailEnd(o.getX(), o.getY(), o.getZ(), new RailHeading(heading));
      var plan = new RailPlan(RailPlanner.starter(start));
      RailwayV30GameTests.prepare(h, plan);
      var p = RailwayV30GameTests.player(h, plan, true);
      var cell =
          plan.footprint.bed.keySet().stream()
              .map(RailwayData::pos)
              .filter(x -> x.getY() == o.getY())
              .findFirst()
              .orElseThrow();
      h.getLevel().setBlockAndUpdate(cell.below(), Blocks.STONE.defaultBlockState());
      h.getLevel().setBlock(cell, Blocks.SHORT_GRASS.defaultBlockState(), 18);
      h.assertTrue(
          plan.inspect(h.getLevel(), p).equals("ready"),
          "replaceable grass blocked heading " + heading);
      h.getLevel().setBlock(cell, Blocks.GLASS.defaultBlockState(), 18);
      h.assertTrue(
          plan.inspect(h.getLevel(), p).equals("blocked"), "transparent solid glass accepted");
      h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 18);
      var high = BlockPos.containing(start.point().x(), start.point().y() + 1.2, start.point().z());
      h.getLevel().setBlock(high, Blocks.TORCH.defaultBlockState(), 18);
      h.assertTrue(
          plan.inspect(h.getLevel(), p).equals("ready"),
          "empty-shape overhead torch blocked heading " + heading);
      h.getLevel().setBlock(high, Blocks.AIR.defaultBlockState(), 18);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0311", timeoutTicks = 100)
  public static void protectedReplaceableTorchRollbackProducesNoDropsAndSuccessDropsOnce(
      GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 430);
    var plan = RailwayV30GameTests.plan(o, 0, 0, -16, 0);
    RailwayV30GameTests.prepare(h, plan);
    var pins = new java.util.HashSet<Long>();
    for (long key : plan.footprint.chunks())
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++) {
          long adjacent =
              net.minecraft.world.level.ChunkPos.asLong((int) key + dx, (int) (key >> 32) + dz);
          pins.add(adjacent);
          h.getLevel().getChunk((int) adjacent, (int) (adjacent >> 32));
          h.getLevel().setChunkForced((int) adjacent, (int) (adjacent >> 32), true);
        }
    h.startSequence()
        .thenWaitUntil(
            () -> {
              // Only the actual loot chunk owns this drop. Far padding pins do not
              // participate in the item transaction and may still be loading asynchronously.
              long key = new net.minecraft.world.level.ChunkPos(o.north(8)).toLong();
              h.assertTrue(
                  h.getLevel().areEntitiesLoaded(key) && h.getLevel().shouldTickBlocksAt(key),
                  "waiting for actual torch-drop chunk "
                      + key
                      + " entities="
                      + h.getLevel().areEntitiesLoaded(key)
                      + " ticking="
                      + h.getLevel().shouldTickBlocksAt(key));
            })
        .thenExecute(
            () -> {
              var p = RailwayV30GameTests.player(h, plan, true);
              var torch = new BlockPos(o.getX(), o.getY(), o.getZ() - 8);
              h.getLevel().setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
              h.getLevel().setBlock(torch, Blocks.TORCH.defaultBlockState(), 18);
              var cancel =
                  new java.util.function.Consumer<
                      net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent>() {
                    public void accept(
                        net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent e) {
                      if (e.getEntity() == p) e.setCanceled(true);
                    }
                  };
              net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
              try {
                h.assertTrue(
                    RailPlacement.place(p, plan).equals("permission"),
                    "claim cancellation ignored");
              } finally {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
              }
              h.assertTrue(
                  h.getLevel().getBlockState(torch).is(Blocks.TORCH)
                      && RailPlacement.count(p) == 64,
                  "rollback changed torch/material owner");
              var area = new AABB(torch).inflate(20);
              h.assertTrue(
                  h.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                  "cancelled replacement dropped resources");
              var id = RailwayV30GameTests.placed(h, plan, p);
              var drops =
                  h.getLevel()
                      .getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.TORCH));
              h.assertTrue(
                  drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,
                  "accepted torch replacement did not drop exactly once");
              for (var drop : drops) drop.discard();
              RailwayV30GameTests.remove(h, p, id);
              for (long key : pins)
                h.getLevel().setChunkForced((int) key, (int) (key >> 32), false);
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "rail_v0311", timeoutTicks = 100)
  public static void inventoriesAndWaterAreNeverDeletedByReplaceableRailPlacement(
      GameTestHelper h) {
    var absent = new BlockPos(28100000, 70, 28100000);
    h.assertTrue(
        !RailPlan.loaded(h.getLevel(), absent)
            && h.getLevel().getChunkSource().getChunkNow(absent.getX() >> 4, absent.getZ() >> 4)
                == null,
        "rail availability query forced an unseen chunk");
    var o = PowerV20GameTests.site(h, 431);
    var plan = RailwayV30GameTests.plan(o, 0, 0, -16, 0);
    RailwayV30GameTests.prepare(h, plan);
    var p = RailwayV30GameTests.player(h, plan, true);
    var at = o.north(8);
    h.getLevel().setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
    var chest =
        (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(at);
    chest.setItem(0, new ItemStack(Items.DIAMOND, 17));
    h.assertTrue(
        RailPlacement.place(p, plan).equals("blocked")
            && chest.getItem(0).getCount() == 17
            && RailPlacement.count(p) == 64,
        "resource-owning obstruction was erased");
    h.getLevel().setBlock(at, Blocks.WATER.defaultBlockState(), 18);
    h.assertTrue(
        RailPlacement.place(p, plan).equals("blocked")
            && h.getLevel().getBlockState(at).is(Blocks.WATER),
        "world fluid erased");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0311", timeoutTicks = 100)
  public static void fixedGroundHitRotatesDestinationWithCameraAndRetainsExplicitOverride(
      GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 432);
    h.getLevel().getChunkAt(o);
    h.getLevel().setBlockAndUpdate(o, Blocks.STONE.defaultBlockState());
    var hit = new BlockHitResult(Vec3.atCenterOf(o).add(0, .5, 0), Direction.UP, o, false);
    var ends = new HashSet<RailEnd>();
    for (int i = 0; i < 16; i++) {
      var v = new RailHeading(i).vector();
      var yaw = Math.toDegrees(Math.atan2(-v.x(), v.z()));
      var end = RailPlanner.destination(h.getLevel(), hit, RailHeading.fromYaw(yaw), -1);
      ends.add(end);
      h.assertTrue(
          end.heading().index() == i && end.x() == o.getX() && end.z() == o.getZ(),
          "camera/target disagreement");
      h.assertTrue(
          RailPlanner.destination(h.getLevel(), hit, RailHeading.fromYaw(yaw), 4).heading().index()
              == 4,
          "manual override ignored");
    }
    h.assertTrue(ends.size() == 16, "fixed-cell preview cache cannot distinguish camera headings");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0311", timeoutTicks = 150)
  public static void rotatedNativePickingAndPlayerCollisionIgnoreOnlyTheEmptyAabbCorner(
      GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 433);
    var plan = diagonal(o);
    RailwayV30GameTests.prepare(h, plan);
    var player = RailwayV30GameTests.player(h, plan, true);
    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    var id = RailwayV30GameTests.placed(h, plan, player);
    var pins = new HashSet<Long>();
    for (long key : plan.footprint.chunks())
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++) {
          long c =
              net.minecraft.world.level.ChunkPos.asLong((int) key + dx, (int) (key >> 32) + dz);
          pins.add(c);
          h.getLevel().getChunk((int) c, (int) (c >> 32));
          h.getLevel().setChunkForced((int) c, (int) (c >> 32), true);
        }
    h.runAfterDelay(
        8,
        () -> {
          var d = TrainData.get(h.getLevel());
          var car = new TrainCar(UUID.randomUUID(), StockSpec.LOCOMOTIVE, d::setDirty);
          var t =
              new TrainData.Train(
                  UUID.randomUUID(),
                  player.getUUID(),
                  "diagnostic",
                  new TrainPath(
                      RailwayData.get(h.getLevel()).graph, new RailGraph.Directed(id, false)),
                  20);
          t.cars.add(car);
          d.add(t);
          TrainWorld.views(d, t);
          var stock = (StockEntity) h.getLevel().getEntity(car.id);
          var c = stock.position();
          var cow = EntityType.COW.create(h.getLevel());
          cow.setNoAi(true);
          cow.setNoGravity(true);
          cow.moveTo(c.x + 2.7, c.y + .6, c.z + 2.7);
          h.getLevel().addFreshEntity(cow);
          h.assertTrue(
              stock.getBoundingBox().intersects(cow.getBoundingBox())
                  && h.getLevel().noCollision(cow, cow.getBoundingBox()),
              "empty rotated AABB corner blocks native collision");
          var emptyCorner = BlockPos.containing(c.add(2.5, 1, 2.5));
          h.getLevel().setBlock(emptyCorner, Blocks.STONE.defaultBlockState(), 18);
          h.assertTrue(
              TrainWorld.inspect(d, t, t.head, t.head, null).equals("ready"),
              "solid wall in empty rotated AABB corner blocks the body");
          h.getLevel().removeBlock(emptyCorner, false);
          var underneath = BlockPos.containing(c).below();
          h.getLevel().setBlock(underneath, Blocks.OAK_FENCE.defaultBlockState(), 18);
          h.assertTrue(
              TrainWorld.inspect(d, t, t.head, t.head, null).equals("blocked"),
              "fence extending from below was skipped");
          h.getLevel().removeBlock(underneath, false);
          var from = c.add(2.95, 1, 6);
          var end = c.add(2.95, 1, 2.95);
          var miss =
              net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                  player, from, end, new AABB(from, end).inflate(.1), e -> e == stock, 100);
          h.assertTrue(miss == null, "native entity picking hit empty AABB corner");
          h.assertTrue(
              net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                      h.getLevel(),
                      player,
                      from,
                      end,
                      new AABB(from, end).inflate(.1),
                      e -> e == stock,
                      .3f)
                  == null,
              "native projectile picking hit empty rotated corner");
          from = c.add(6, 1, 0);
          end = c.add(-6, 1, 0);
          var hit =
              net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                  player, from, end, new AABB(from, end).inflate(.1), e -> e == stock, 200);
          h.assertTrue(
              hit != null && hit.getEntity() == stock, "precise native picking lost a chassis hit");
          var projectileHit =
              net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                  h.getLevel(),
                  player,
                  from,
                  end,
                  new AABB(from, end).inflate(.1),
                  e -> e == stock,
                  .3f);
          h.assertTrue(
              projectileHit != null && projectileHit.getEntity() == stock,
              "native projectile picking lost a chassis hit");
          cow.moveTo(c.x + 6, c.y + .5, c.z);
          cow.move(MoverType.SELF, new Vec3(-10, 0, 0));
          h.assertTrue(
              cow.getX() > c.x && !TrainCollision.intersects(stock.hull(), cow.getBoundingBox()),
              "native movement tunnelled through chassis");
          cow.discard();
          player.setPos(c.x + 2, c.y, c.z);
          h.assertTrue(StockPlacement.pack(d, t, car.id, player), "rotated retrieval");
          RailwayData.get(h.getLevel()).remove(id);
          for (long key : pins) h.getLevel().setChunkForced((int) key, (int) (key >> 32), false);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_v0311", timeoutTicks = 150)
  public static void actualPaidMaximumSpeedCrossesEdgesAndStopsForARealThinObstacle(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 434);
    var ids = new ArrayList<UUID>();
    ids.add(site.edge());
    var o = site.plan().curve.start;
    for (int part = 1; part < 4; part++)
      ids.add(
          TrainCompletionGameTests.extra(
              h,
              site,
              new RailPlan(
                  RailCurve.plan(
                      new RailEnd(o.x(), o.y(), o.z() - part * 96, new RailHeading(0)),
                      new RailEnd(o.x(), o.y(), o.z() - (part + 1) * 96, new RailHeading(0))))));
    h.runAfterDelay(
        8,
        () -> {
          var view = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 91.375);
          var d = TrainData.get(h.getLevel());
          var car = d.car(view.getUUID());
          var t = d.owner(car.id);
          car.items.setStackInSlot(0, new ItemStack(Items.COAL, 3));
          var rider = TrainCompletionGameTests.rider(h.getLevel(), view, site.player());
          double initial = t.head;
          for (double speed : new double[] {60 / 216., 100 / 216., 120 / 216., 150 / 216., 1.2}) {
            t.speed = speed;
            for (int i = 0; i < 6; i++) {
              TrainNetwork.control(rider, view, 1, false, 0);
              TrainSimulation.tick(d, t);
              h.assertTrue(
                  d.car(car.id) == car && t.speed <= 1.2 && car.health == 1000,
                  "high-speed frame changed UUID/health/cap");
            }
          }
          h.assertTrue(
              t.head > initial + 45
                  && car.spentJ > 0
                  && car.fuelJ + car.spentJ + 4000000L * car.items.getStackInSlot(0).getCount()
                      == 12000000L,
              "high-speed finite fuel/pose ledger");
          var point = t.path.sample(RailwayData.get(h.getLevel()).graph, t.head + 5).position();
          var wall = BlockPos.containing(TrainWorld.vec(point)).above();
          h.getLevel().setBlock(wall, Blocks.OAK_FENCE.defaultBlockState(), 18);
          t.speed = 1.2;
          TrainSimulation.tick(d, t);
          h.assertTrue(
              t.speed == 0 && t.status == TrainData.Status.BLOCKED,
              "maximum-speed thin obstacle was skipped");
          rider.stopRiding();
          h.getLevel().removeBlock(wall, false);
          t.speed = 0;
          TrainV31GameTests.cleanup(h, site);
          for (var edge : ids)
            if (RailwayData.get(h.getLevel()).segment(edge) != null)
              RailwayData.get(h.getLevel()).remove(edge);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_v0311", timeoutTicks = 100)
  public static void doubleFlowerReplacementHasExactlyOneDropAndNoOrphanPart(GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 436);
    var plan = RailwayV30GameTests.plan(o, 0, 0, -16, 0);
    RailwayV30GameTests.prepare(h, plan);
    var pins = new java.util.HashSet<Long>();
    for (long key : plan.footprint.chunks())
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++) {
          long adjacent =
              net.minecraft.world.level.ChunkPos.asLong((int) key + dx, (int) (key >> 32) + dz);
          pins.add(adjacent);
          h.getLevel().getChunk((int) adjacent, (int) (adjacent >> 32));
          h.getLevel().setChunkForced((int) adjacent, (int) (adjacent >> 32), true);
        }
    h.startSequence()
        .thenWaitUntil(
            () -> {
              // Only the actual loot chunk owns this drop. Far padding pins do not
              // participate in the item transaction and may still be loading asynchronously.
              long key = new net.minecraft.world.level.ChunkPos(o.north(8)).toLong();
              h.assertTrue(
                  h.getLevel().areEntitiesLoaded(key) && h.getLevel().shouldTickBlocksAt(key),
                  "waiting for actual flower-drop chunk "
                      + key
                      + " entities="
                      + h.getLevel().areEntitiesLoaded(key)
                      + " ticking="
                      + h.getLevel().shouldTickBlocksAt(key));
            })
        .thenExecute(
            () -> {
              var p = RailwayV30GameTests.player(h, plan, true);
              var at = o.north(8);
              h.getLevel().setBlock(at.below(), Blocks.DIRT.defaultBlockState(), 18);
              h.getLevel()
                  .setBlock(
                      at,
                      Blocks.PEONY
                          .defaultBlockState()
                          .setValue(
                              net.minecraft.world.level.block.state.properties.BlockStateProperties
                                  .DOUBLE_BLOCK_HALF,
                              net.minecraft.world.level.block.state.properties.DoubleBlockHalf
                                  .LOWER),
                      18);
              h.getLevel()
                  .setBlock(
                      at.above(),
                      Blocks.PEONY
                          .defaultBlockState()
                          .setValue(
                              net.minecraft.world.level.block.state.properties.BlockStateProperties
                                  .DOUBLE_BLOCK_HALF,
                              net.minecraft.world.level.block.state.properties.DoubleBlockHalf
                                  .UPPER),
                      18);
              h.assertTrue(
                  plan.inspect(h.getLevel(), p).equals("ready"),
                  "tagged empty-shape flower blocked rail");
              var id = RailwayV30GameTests.placed(h, plan, p);
              var area = new AABB(at).inflate(8);
              var drops =
                  h.getLevel()
                      .getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.PEONY));
              h.assertTrue(
                  drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1
                      && !h.getLevel().getBlockState(at.above()).is(Blocks.PEONY),
                  "double flower count="
                      + drops.stream().mapToInt(e -> e.getItem().getCount()).sum()
                      + " upper="
                      + h.getLevel().getBlockState(at.above()));
              for (var drop : drops) drop.discard();
              RailwayV30GameTests.remove(h, p, id);
              for (long key : pins)
                h.getLevel().setChunkForced((int) key, (int) (key >> 32), false);
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "rail_v0311_disk", timeoutTicks = 120)
  public static void highSpeedPaidPoseAndExactOwnersSurviveIndependentJvm(GameTestHelper h) {
    var l = h.getLevel();
    var data = TrainData.get(l);
    var disk =
        l.getDataStorage()
            .computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                    TrainV31GameTests.Disk::new, TrainV31GameTests.Disk::load),
                "factorycraft_v0311_train_checkpoint");
    if (disk.data.hasUUID("Train")) {
      var id = disk.data.getUUID("Train");
      var train = data.train(id);
      h.assertTrue(train != null, "high-speed train owner missing after independent JVM");
      h.assertTrue(
          saved(data, id, l).equals(disk.data.getCompound("State")),
          "high-speed pose/fuel/components/route/UUID changed on disk");
      h.assertTrue(train.speed >= 120 / 216., "checkpoint did not retain paid high-speed phase");
      System.out.println(
          "FACTORYCRAFT_V0311_TRAIN_DISK: VERIFIED "
              + id
              + " exact paid high-speed canonical owners");
      h.succeed();
      return;
    }
    var site = TrainV31GameTests.rail(h, 435);
    h.runAfterDelay(
        8,
        () -> {
          var loco = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 40);
          var cargo = TrainV31GameTests.stock(h, site, StockSpec.CARGO, 33);
          var fluid = TrainV31GameTests.stock(h, site, StockSpec.FLUID, 26);
          var train = data.owner(loco.getUUID());
          var a = data.car(loco.getUUID());
          a.items.setStackInSlot(0, new ItemStack(Items.COAL, 7));
          var item = new ItemStack(Items.DIAMOND, 31);
          item.set(
              net.minecraft.core.component.DataComponents.CUSTOM_NAME,
              net.minecraft.network.chat.Component.literal("v0311 finite cargo"));
          data.car(cargo.getUUID()).items.setStackInSlot(0, item);
          var water =
              new net.neoforged.neoforge.fluids.FluidStack(
                  net.minecraft.world.level.material.Fluids.WATER, 234567);
          water.set(
              net.minecraft.core.component.DataComponents.CUSTOM_NAME,
              net.minecraft.network.chat.Component.literal("v0311 finite fluid"));
          data.car(fluid.getUUID())
              .fluid
              .restore(new factorycraft.fluids.FluidKey(water), water.getAmount());
          var driver = TrainCompletionGameTests.rider(l, loco, site.player());
          train.speed = 120 / 216.;
          TrainNetwork.control(driver, loco, 1, false, 0);
          TrainSimulation.tick(data, train);
          h.assertTrue(
              train.head > 40
                  && train.speed >= 120 / 216.
                  && a.spentJ > 0
                  && a.spentJ + a.fuelJ + 4000000L * a.items.getStackInSlot(0).getCount()
                      == 28000000,
              "paid moving checkpoint did not execute");
          driver.stopRiding();
          data.unloadChunk(net.minecraft.world.level.ChunkPos.asLong(loco.blockPosition()));
          disk.data.putUUID("Train", train.id);
          disk.data.put("State", saved(data, train.id, l).copy());
          disk.setDirty();
          data.setDirty();
          for (long key : site.pins()) l.setChunkForced((int) key, (int) (key >> 32), false);
          System.out.println(
              "FACTORYCRAFT_V0311_TRAIN_DISK: SEEDED "
                  + train.id
                  + " exact paid high-speed canonical owners");
          h.succeed();
        });
  }

  private static CompoundTag saved(
      TrainData data, UUID id, net.minecraft.server.level.ServerLevel level) {
    return data
        .save(new CompoundTag(), level.registryAccess())
        .getList("Trains", Tag.TAG_COMPOUND)
        .stream()
        .map(x -> (CompoundTag) x)
        .filter(x -> x.getUUID("Id").equals(id))
        .findFirst()
        .orElseThrow();
  }

  @GameTest(template = "empty", batch = "rail_v0311", timeoutTicks = 120)
  public static void fullCarriageIsDiscoverableAcrossEntitySectionBoundary(GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 437);
    h.runAfterDelay(
        8,
        () -> {
          var stock = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 17.5);
          var c = stock.position();
          var tail = new AABB(c.add(-.1, .7, 3.25), c.add(.1, .9, 3.4));
          h.assertTrue(
              ((int) Math.floor(c.z) >> 4) != ((int) Math.floor(tail.minZ) >> 4),
              "fixture did not straddle sections");
          h.assertTrue(
              TrainCollision.intersects(stock.hull(), tail)
                  && h.getLevel().getEntitiesOfClass(StockEntity.class, tail).contains(stock),
              "vanilla section search lost a real coupler outside its old2m margin");
          h.assertTrue(
              TrainCollision.occupied(h.getLevel(), null, tail),
              "native placement/movement cannot discover the tail");
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  private TrainRailHotfixGameTests() {}
}
