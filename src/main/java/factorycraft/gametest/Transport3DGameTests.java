package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.belts.*;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.registry.Content;
import factorycraft.simulation.BeltSimulation;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class Transport3DGameTests {
  private static BeltBlockEntity belt(
      GameTestHelper h, FactoryKind kind, BlockPos p, Direction facing) {
    var absolute = h.absolutePos(p);
    h.getLevel()
        .setBlockAndUpdate(
            absolute,
            Content.FACTORY
                .get(kind)
                .get()
                .defaultBlockState()
                .setValue(FactoryBlock.FACING, facing));
    var be = (BeltBlockEntity) h.getLevel().getBlockEntity(absolute);
    if (!be.loaded()) be.onLoad();
    return be;
  }

  private static int count(BeltBlockEntity b) {
    int n = 0;
    for (int i = 0; i < b.laneCount(); i++) n += b.lane(i).carriers().size();
    return n;
  }

  private static void seed(BeltBlockEntity b) {
    for (int lane = 0; lane < 2; lane++)
      for (int i = 0; i < 4; i++)
        b.lane(lane)
            .restore(
                new ItemStack(lane == 0 ? Items.RAW_IRON : Items.RAW_COPPER),
                (96 - i * 24.0) / b.lane(lane).length());
    b.sync();
    b.wakeTransport();
  }

  private static BeltBlockEntity lift(
      GameTestHelper h, FactoryKind kind, BlockPos p, int height, int turn) {
    var b = belt(h, kind, p, Direction.NORTH);
    h.assertTrue(
        b.initializeRoute(new BeltRoute(BeltRoute.Shape.LIFT, height, turn)),
        "lift geometry rejected");
    h.assertTrue(TransportPlan.buildParts(b), "lift formation failed");
    for (var cell : TransportPlan.cells(b.getBlockPos(), b.route()))
      h.assertTrue(
          h.getLevel()
                  .getBlockState(cell)
                  .getCollisionShape(h.getLevel(), cell)
                  .max(Direction.Axis.Y)
              > .95,
          "lift collision remained flat");
    BeltSimulation.topologyChanged(b);
    return b;
  }

  private static ItemStack click(
      GameTestHelper h, net.minecraft.world.entity.player.Player player, BlockPos target) {
    var at = h.absolutePos(target);
    player
        .getMainHandItem()
        .useOn(
            new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
    return player.getMainHandItem();
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void curvesPreserveBothLanesAtEveryTier(GameTestHelper h) {
    var all = new ArrayList<BeltBlockEntity>();
    var ends = new ArrayList<BeltBlockEntity>();
    int row = 0;
    for (var kind : List.of(FactoryKind.BELT, FactoryKind.FAST_BELT, FactoryKind.EXPRESS_BELT)) {
      var p = new BlockPos(2, 3, 2 + row++ * 4);
      var a = belt(h, kind, p, Direction.EAST);
      var b = belt(h, kind, p.east(), Direction.SOUTH);
      var c = belt(h, kind, p.east().south(), Direction.SOUTH);
      all.addAll(List.of(a, b, c));
      ends.add(c);
      seed(a);
      h.assertTrue(
          b.route().shape == BeltRoute.Shape.RIGHT, "directional input did not form curve");
      h.assertTrue(b.lane(0).length() != b.lane(1).length(), "curve lacks independent radii");
    }
    h.runAfterDelay(
        120,
        () -> {
          h.assertTrue(
              all.stream().mapToInt(Transport3DGameTests::count).sum() == 24,
              "curves changed item count");
          for (var c : ends) {
            h.assertTrue(count(c) == 8, "curve bottleneck/stalled");
            h.assertTrue(
                c.lane(0).carriers().stream().allMatch(v -> v.value.is(Items.RAW_IRON))
                    && c.lane(1).carriers().stream().allMatch(v -> v.value.is(Items.RAW_COPPER)),
                "curve swapped lanes");
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void unrelatedNeighborsDoNotFormCurve(GameTestHelper h) {
    var p = new BlockPos(4, 3, 4);
    var b = belt(h, FactoryKind.BELT, p, Direction.NORTH);
    belt(h, FactoryKind.BELT, p.west(), Direction.WEST);
    belt(h, FactoryKind.BELT, p.east(), Direction.EAST);
    BeltSimulation.topologyChanged(b);
    h.assertTrue(b.route().shape == BeltRoute.Shape.FLAT, "non-input neighbors bent belt");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void slopesMoveBothLanesAcrossHeight(GameTestHelper h) {
    var p = new BlockPos(3, 4, 3);
    var a = belt(h, FactoryKind.BELT, p, Direction.NORTH);
    var b = belt(h, FactoryKind.FAST_BELT, p.north().above(), Direction.NORTH);
    var c = belt(h, FactoryKind.EXPRESS_BELT, p.north(2), Direction.NORTH);
    BeltSimulation.topologyChanged(a);
    BeltSimulation.topologyChanged(b);
    h.assertTrue(
        a.route().shape == BeltRoute.Shape.UP && b.route().shape == BeltRoute.Shape.DOWN,
        "slopes did not form: " + a.route().shape + "/" + b.route().shape);
    h.assertTrue(a.lane(0).length() == 136, "incline uses wrong travel distance");
    seed(a);
    h.runAfterDelay(
        140,
        () -> {
          h.assertTrue(
              count(a) + count(b) + count(c) == 8 && count(c) == 8,
              "height transition lost or stalled carriers");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void slopeClearanceAndFlatPreference(GameTestHelper h) {
    var p = new BlockPos(3, 4, 3);
    h.setBlock(p.above(), Blocks.STONE);
    var a = belt(h, FactoryKind.BELT, p, Direction.NORTH);
    belt(h, FactoryKind.BELT, p.north().above(), Direction.NORTH);
    BeltSimulation.topologyChanged(a);
    h.assertTrue(a.route().shape == BeltRoute.Shape.FLAT, "incline formed through solid block");
    h.setBlock(p.above(), Blocks.AIR);
    belt(h, FactoryKind.BELT, p.north(), Direction.NORTH);
    BeltSimulation.topologyChanged(a);
    h.assertTrue(
        a.route().shape == BeltRoute.Shape.FLAT, "elevated neighbor overrode horizontal path");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void liftContinuousDelayAndBackpressure(GameTestHelper h) {
    var p = new BlockPos(4, 3, 4);
    var b = lift(h, FactoryKind.LIFT, p, 4, 1);
    seed(b);
    h.runAfterDelay(
        5,
        () ->
            h.assertTrue(
                b.lane(0).carriers().getFirst().positionUnits() < b.lane(0).length(),
                "lift teleported to output"));
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(count(b) == 8, "blocked lift changed item count");
          var out = belt(h, FactoryKind.BELT, p.above(4).east(), Direction.EAST);
          BeltSimulation.topologyChanged(b);
          h.runAfterDelay(
              50,
              () -> {
                h.assertTrue(
                    count(b) + count(out) == 8 && count(out) == 8, "lift exit jam did not recover");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void downwardLiftMovesAtExpressSpeed(GameTestHelper h) {
    var p = new BlockPos(4, 8, 4);
    var b = lift(h, FactoryKind.EXPRESS_LIFT, p, -4, 3);
    var out = belt(h, FactoryKind.EXPRESS_BELT, p.below(4).west(), Direction.WEST);
    seed(b);
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              count(b) + count(out) == 8 && count(out) == 8,
              "downward lift lost items or wrong exit");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void liftPartUnloadFreezesSoleOwner(GameTestHelper h) {
    var p = new BlockPos(4, 3, 4);
    var b = lift(h, FactoryKind.LIFT, p, 3, 0);
    seed(b);
    var part = (TransportPartBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(p.above(2)));
    h.runAfterDelay(
        3,
        () -> {
          part.onChunkUnloaded();
          int before = b.lane(0).carriers().getFirst().positionUnits();
          h.runAfterDelay(
              12,
              () -> {
                h.assertTrue(
                    b.lane(0).carriers().getFirst().positionUnits() == before && count(b) == 8,
                    "incomplete lift moved/lost items");
                part.onLoad();
                h.runAfterDelay(
                    12,
                    () -> {
                      h.assertTrue(
                          b.lane(0).carriers().getFirst().positionUnits() > before,
                          "lift failed to resume");
                      h.succeed();
                    });
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void liftNbtAndPartDemolitionHaveOneOwner(GameTestHelper h) {
    var p = new BlockPos(4, 3, 4);
    var b = lift(h, FactoryKind.FAST_LIFT, p, 3, 2);
    seed(b);
    var r = h.getLevel().registryAccess();
    var tag = b.saveWithFullMetadata(r);
    var copy = (BeltBlockEntity) BlockEntity.loadStatic(b.getBlockPos(), b.getBlockState(), tag, r);
    h.assertTrue(
        count(copy) == 8
            && copy.route().equals(b.route())
            && copy.lane(0).length() == b.lane(0).length(),
        "lift NBT failed");
    var part = (TransportPartBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(p.above(2)));
    h.assertTrue(!part.saveWithFullMetadata(r).contains("Lane0"), "part copied inventory");
    h.getLevel().destroyBlock(part.getBlockPos(), true);
    var drops =
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(p)).inflate(5));
    h.assertTrue(
        drops.size() == 1 && drops.getFirst().getItem().has(DataComponents.BLOCK_ENTITY_DATA),
        "part break did not yield one portable owner");
    var saved = drops.getFirst().getItem().get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
    h.assertTrue(
        saved.getList("Lane0", 10).size() + saved.getList("Lane1", 10).size() == 8,
        "portable lift lost contents");
    h.assertTrue(
        h.getLevel().getBlockState(h.absolutePos(p.above())).isAir(), "column not dismantled");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void liftOneItemTwoClickPlacementAndBlockedColumn(GameTestHelper h) {
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setYRot(180);
    var p = new BlockPos(4, 3, 4);
    player.setPos(Vec3.atCenterOf(h.absolutePos(p)));
    player.setItemInHand(
        InteractionHand.MAIN_HAND,
        new ItemStack(Content.FACTORY.get(FactoryKind.LIFT).get().asItem(), 2));
    click(h, player, p);
    h.assertTrue(
        player.getMainHandItem().getCount() == 2
            && h.getLevel().getBlockState(h.absolutePos(p)).isAir(),
        "first click consumed/placed lift");
    h.setBlock(p.above(2), Blocks.STONE);
    click(h, player, p.above(4));
    h.assertTrue(
        player.getMainHandItem().getCount() == 2
            && h.getLevel().getBlockState(h.absolutePos(p)).isAir(),
        "blocked placement partially wrote structure");
    h.setBlock(p.above(2), Blocks.AIR);
    player.setPos(Vec3.atCenterOf(h.absolutePos(p.east(3))));
    click(h, player, p.above(4));
    h.assertTrue(
        player.getMainHandItem().getCount() == 1
            && h.getLevel().getBlockEntity(h.absolutePos(p)) instanceof BeltBlockEntity,
        "two clicks did not place one lift");
    var b = (BeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(p));
    h.runAfterDelay(
        3,
        () -> {
          h.assertTrue(
              b.route().span == 4 && b.structureReady(), "selected height/parts incorrect");
          h.succeed();
        });
  }

  private static BeltBlockEntity[] underground(
      GameTestHelper h, FactoryKind kind, BlockPos p, int distance) {
    var a = belt(h, kind, p, Direction.EAST);
    var b = belt(h, kind, p.east(distance), Direction.EAST);
    h.assertTrue(b.toggleUnderground(), "cannot set output terminal");
    BeltSimulation.topologyChanged(a);
    h.assertTrue(a.paired() == b && a.route().span == distance, "underground pairing failed");
    return new BeltBlockEntity[] {a, b};
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void undergroundVirtualRouteDelayAndSolidInteriors(GameTestHelper h) {
    var p = new BlockPos(2, 4, 3);
    for (int x = 1; x < 5; x++) h.setBlock(p.east(x), Blocks.STONE);
    var pair = underground(h, FactoryKind.UNDERGROUND, p, 5);
    var a = pair[0];
    var out = belt(h, FactoryKind.BELT, p.east(6), Direction.EAST);
    a.inventory().insertItem(0, new ItemStack(Items.DIAMOND), false);
    h.runAfterDelay(
        40, () -> h.assertTrue(count(a) == 1 && count(out) == 0, "virtual route teleported"));
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              count(a) + count(out) == 1 && count(out) == 1,
              "underground transport failed: input="
                  + count(a)
                  + ", destination="
                  + count(out)
                  + ", paired="
                  + (a.paired() != null));
          for (int x = 1; x < 5; x++)
            h.assertTrue(
                h.getLevel().getBlockState(h.absolutePos(p.east(x))).is(Blocks.STONE),
                "virtual route modified interior");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void undergroundOutputUnloadAndDestroyRetainsInputContents(GameTestHelper h) {
    var p = new BlockPos(2, 4, 3);
    var pair = underground(h, FactoryKind.FAST_UNDERGROUND, p, 7);
    var a = pair[0];
    var b = pair[1];
    a.inventory().insertItem(0, new ItemStack(Items.DIAMOND), false);
    h.runAfterDelay(
        3,
        () -> {
          b.onChunkUnloaded();
          int before = a.lane(0).carriers().getFirst().positionUnits();
          h.runAfterDelay(
              12,
              () -> {
                h.assertTrue(
                    a.lane(0).carriers().getFirst().positionUnits() == before && count(a) == 1,
                    "unloaded endpoint did not freeze owner");
                b.onLoad();
                h.runAfterDelay(
                    12,
                    () -> {
                      h.assertTrue(
                          a.lane(0).carriers().getFirst().positionUnits() > before,
                          "pair not restored on reload");
                      h.getLevel().destroyBlock(b.getBlockPos(), true);
                      h.assertTrue(
                          count(a) == 1 && a.paired() == null,
                          "output demolition lost/dropped input items");
                      var replacement =
                          belt(h, FactoryKind.FAST_UNDERGROUND, p.east(7), Direction.EAST);
                      replacement.toggleUnderground();
                      h.assertTrue(
                          a.paired() == replacement && count(a) == 1,
                          "replacement failed to rebind");
                      h.succeed();
                    });
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void undergroundLengthsRolesAndCrossingsAreUnambiguous(GameTestHelper h) {
    var p = new BlockPos(2, 4, 3);
    var first = underground(h, FactoryKind.UNDERGROUND, p, 4);
    var far = belt(h, FactoryKind.UNDERGROUND, p.east(5), Direction.EAST);
    far.toggleUnderground();
    BeltSimulation.topologyChanged(first[0]);
    h.assertTrue(
        first[0].paired() == first[1] && far.paired() == null,
        "ambiguous pair claimed two outputs");
    var cross = underground(h, FactoryKind.EXPRESS_UNDERGROUND, p.south(3), 9);
    h.assertTrue(cross[0].lane(0).length() == 960, "express hidden-block model wrong");
    h.assertTrue(
        first[1].lane(0).carriers().isEmpty() && first[1].lane(1).carriers().isEmpty(),
        "output terminal owns resource copies");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void undergroundNbtRetainsLongPositionsAndCount(GameTestHelper h) {
    var p = new BlockPos(2, 4, 3);
    var a = underground(h, FactoryKind.EXPRESS_UNDERGROUND, p, 9)[0];
    for (int lane = 0; lane < 2; lane++)
      for (int i = 0; i < 30; i++)
        a.lane(lane)
            .restore(new ItemStack(Items.COPPER_INGOT), 1 - i * 24.0 / a.lane(lane).length());
    var tag = a.saveWithFullMetadata(h.getLevel().registryAccess());
    var copy =
        (BeltBlockEntity)
            BlockEntity.loadStatic(
                a.getBlockPos(), a.getBlockState(), tag, h.getLevel().registryAccess());
    h.assertTrue(
        count(copy) == 60 && copy.lane(0).carriers().get(29).positionUnits() == 264,
        "long route NBT truncated/normalized positions");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void metricTransportPersistsAcrossServerProcesses(GameTestHelper h) {
    var level = h.getLevel();
    var u = new BlockPos(1007, 70, 1007);
    var v = u.east(9);
    var l = new BlockPos(1007, 70, 1023);
    var m = new BlockPos(1023, 78, 1023);
    var cells = new ArrayList<BlockPos>();
    cells.add(u);
    cells.add(v);
    cells.addAll(TransportPlan.cells(l, new BeltRoute(BeltRoute.Shape.LIFT, 4, 1)));
    cells.addAll(TransportPlan.cells(m, new BeltRoute(BeltRoute.Shape.LIFT, -4, 3)));
    for (var p : cells) level.getChunkAt(p);
    boolean fresh =
        cells.stream()
            .allMatch(p -> level.getBlockEntity(p) == null && level.getBlockState(p).isAir());
    if (fresh) {
      for (var p : List.of(u, v)) {
        level.setBlockAndUpdate(
            p,
            Content.FACTORY
                .get(FactoryKind.EXPRESS_UNDERGROUND)
                .get()
                .defaultBlockState()
                .setValue(FactoryBlock.FACING, Direction.EAST));
        var b = (BeltBlockEntity) level.getBlockEntity(p);
        if (!b.loaded()) b.onLoad();
      }
      ((BeltBlockEntity) level.getBlockEntity(v)).toggleUnderground();
      for (var p : List.of(l, m)) {
        var kind = p.equals(l) ? FactoryKind.FAST_LIFT : FactoryKind.EXPRESS_LIFT;
        level.setBlockAndUpdate(p, Content.FACTORY.get(kind).get().defaultBlockState());
        var b = (BeltBlockEntity) level.getBlockEntity(p);
        b.initializeRoute(
            new BeltRoute(BeltRoute.Shape.LIFT, p.equals(l) ? 4 : -4, p.equals(l) ? 1 : 3));
        TransportPlan.buildParts(b);
      }
      for (var p : List.of(u, l, m)) {
        var b = (BeltBlockEntity) level.getBlockEntity(p);
        for (int lane = 0; lane < 2; lane++)
          for (int j = 0; j < b.lane(lane).capacity(); j++)
            b.lane(lane)
                .restore(
                    new ItemStack(lane == 0 ? Items.DIAMOND : Items.EMERALD),
                    (double) (b.lane(lane).length() - j * 24) / b.lane(lane).length());
        b.sync();
      }
      System.out.println(
          "FACTORYCRAFT_TRANSPORT_DISK: SEEDED saturated underground and up/down lifts across four"
              + " chunks");
    }
    int total = 0;
    for (var p : List.of(u, l, m)) {
      h.assertTrue(
          level.getBlockEntity(p) instanceof BeltBlockEntity,
          "partial transport checkpoint; refusing to reseed");
      var b = (BeltBlockEntity) level.getBlockEntity(p);
      var expected =
          p.equals(u)
              ? new BeltRoute(BeltRoute.Shape.UNDERGROUND, 9, 0)
              : new BeltRoute(BeltRoute.Shape.LIFT, p.equals(l) ? 4 : -4, p.equals(l) ? 1 : 3);
      h.assertTrue(b.route().equals(expected), "disk route changed");
      for (int lane = 0; lane < 2; lane++) {
        var list = b.lane(lane).carriers();
        h.assertTrue(list.size() == b.lane(lane).capacity(), "disk lost long-route carriers");
        for (int j = 0; j < list.size(); j++)
          h.assertTrue(
              list.get(j).positionUnits() == b.lane(lane).length() - j * 24
                  && list.get(j).value.is(lane == 0 ? Items.DIAMOND : Items.EMERALD),
              "disk metric position/item changed");
        total += list.size();
      }
      for (var cell : TransportPlan.cells(p, b.route()))
        if (!cell.equals(p))
          h.assertTrue(
              level.getBlockEntity(cell) instanceof TransportPartBlockEntity part
                  && part.boundTo(p, b.transportIdentity()),
              "disk lift identity changed");
    }
    h.assertTrue(
        level.getBlockEntity(v) instanceof BeltBlockEntity output
            && output.undergroundOutput()
            && count(output) == 0,
        "output owns duplicate inventory");
    if (!fresh)
      System.out.println(
          "FACTORYCRAFT_TRANSPORT_DISK: VERIFIED exact "
              + total
              + " items, metric coordinates, routes and sole owners");
    for (var p : cells) {
      level.getBlockEntity(p).onChunkUnloaded();
      level.getChunkAt(p).setUnsaved(true);
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 420)
  public static void insertersExtractFromUndergroundOutputAndLiftHead(GameTestHelper h) {
    var p = new BlockPos(2, 3, 3);
    var a = underground(h, FactoryKind.UNDERGROUND, p, 5)[0];
    seed(a);
    belt(
        h,
        FactoryKind.BELT,
        p.south(5),
        Direction.NORTH); // unrelated line must not claim either endpoint
    var lift = lift(h, FactoryKind.FAST_LIFT, new BlockPos(3, 3, 10), 3, 0);
    seed(lift);
    var ports = List.of(p.east(5), new BlockPos(3, 6, 10));
    for (var port : ports) {
      h.getLevel()
          .setBlockAndUpdate(
              h.absolutePos(port.east()),
              Content.FACTORY
                  .get(FactoryKind.BURNER_INSERTER)
                  .get()
                  .defaultBlockState()
                  .setValue(FactoryBlock.FACING, Direction.EAST));
      h.setBlock(port.east(2), Blocks.CHEST);
    }
    for (var port : ports)
      ((factorycraft.blockentity.InserterBlockEntity)
              h.getLevel().getBlockEntity(h.absolutePos(port.east())))
          .fuel.setStackInSlot(0, new ItemStack(Items.COAL));
    h.runAfterDelay(
        390,
        () -> {
          for (var port : ports) {
            var chest =
                (net.minecraft.world.level.block.entity.ChestBlockEntity)
                    h.getLevel().getBlockEntity(h.absolutePos(port.east(2)));
            int n = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++)
              n += chest.getItem(slot).getCount();
            h.assertTrue(n == 8, "inserter failed to sample physical output: " + n);
          }
          h.assertTrue(count(a) == 0 && count(lift) == 0, "inserter left duplicated owners");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void staleUndergroundOutputPortCannotExtractAfterUnload(GameTestHelper h) {
    var p = new BlockPos(2, 3, 3);
    var pair = underground(h, FactoryKind.EXPRESS_UNDERGROUND, p, 5);
    var a = pair[0];
    var b = pair[1];
    seed(a);
    h.runAfterDelay(
        100,
        () -> {
          var cap =
              h.getLevel()
                  .getCapability(
                      net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                      b.getBlockPos(),
                      Direction.UP);
          h.assertTrue(
              !cap.extractItem(0, 1, true).isEmpty(),
              "output capability did not expose arriving items");
          b.onChunkUnloaded();
          h.assertTrue(
              cap.extractItem(0, 1, false).isEmpty() && count(a) == 8,
              "stale output capability stole sole-owner items");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void portableLiftRebindsFreshPartsWithoutLosingContents(GameTestHelper h) {
    var p = new BlockPos(3, 3, 3);
    var source = lift(h, FactoryKind.LIFT, p, 3, 0);
    seed(source);
    var previous = source.transportIdentity();
    var stack = source.portable();
    source.removeTransport(false);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setYRot(180);
    player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 3))));
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    var target = new BlockPos(10, 3, 3);
    click(h, player, target);
    click(h, player, target.above(3));
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockEntity(h.absolutePos(target)) instanceof BeltBlockEntity,
              "portable lift placement failed");
          var b = (BeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(target));
          h.assertTrue(
              count(b) == 8 && b.structureReady() && !b.transportIdentity().equals(previous),
              "portable lift lost contents or imported stale identity");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 2300)
  public static void finiteChestRouteTraversesCurvesSlopeUndergroundAndLift(GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    var destination = factorycraft.diagnostics.TransportFactory.build(h.getLevel(), o);
    h.runAfterDelay(
        2200,
        () -> {
          int iron = 0, copper = 0;
          for (int slot = 0; slot < destination.getContainerSize(); slot++) {
            var stack = destination.getItem(slot);
            if (stack.is(Items.RAW_IRON)) iron += stack.getCount();
            if (stack.is(Items.RAW_COPPER)) copper += stack.getCount();
          }
          h.assertTrue(
              iron == 32 && copper == 32,
              "finite transport route output differs: " + iron + "/" + copper);
          System.out.println(
              "FACTORYCRAFT_TRANSPORT_FACTORY: VERIFIED 32 iron + 32 copper through curves, slope,"
                  + " virtual underground and lift");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void protectedLiftPlacementRollsBackEntireColumn(GameTestHelper h) {
    var p = new BlockPos(4, 3, 4);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setYRot(180);
    player.setPos(Vec3.atCenterOf(h.absolutePos(p.east(3))));
    player.setItemInHand(
        InteractionHand.MAIN_HAND,
        new ItemStack(Content.FACTORY.get(FactoryKind.LIFT).get().asItem(), 2));
    click(h, player, p);
    int[] events = {0};
    java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent>
        cancel =
            event -> {
              if (event.getEntity() == player) {
                events[0]++;
                event.setCanceled(true);
              }
            };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
    try {
      click(h, player, p.above(4));
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
    }
    h.assertTrue(
        events[0] == 1 && player.getMainHandItem().getCount() == 2,
        "lift skipped multi-place protection/refund");
    for (int y = 0; y <= 4; y++)
      h.assertTrue(
          h.getLevel().getBlockState(h.absolutePos(p.above(y))).isAir(),
          "cancelled lift left physical cells");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void nativeUndergroundPlacementSelectsOutputWithoutDuplicateNodes(
      GameTestHelper h) {
    var p = new BlockPos(2, 3, 3);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setYRot(270);
    player.setPos(Vec3.atCenterOf(h.absolutePos(p.south(3))));
    player.setItemInHand(
        InteractionHand.MAIN_HAND,
        new ItemStack(Content.FACTORY.get(FactoryKind.UNDERGROUND).get().asItem(), 2));
    click(h, player, p);
    click(h, player, p.east(5));
    h.runAfterDelay(
        4,
        () -> {
          var a = (BeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(p));
          var b = (BeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(p.east(5)));
          h.assertTrue(
              a != null
                  && b != null
                  && !a.undergroundOutput()
                  && b.undergroundOutput()
                  && a.paired() == b,
              "second native terminal did not choose output");
          h.assertTrue(player.getMainHandItem().isEmpty(), "terminal item cost wrong");
          a.inventory().insertItem(0, new ItemStack(Items.DIAMOND), false);
          h.runAfterDelay(
              12,
              () -> {
                h.assertTrue(
                    count(a) == 1 && count(b) == 0,
                    "role setup registered duplicate simulation owners");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 140)
  public static void crossingVirtualLinesRemainIndependent(GameTestHelper h) {
    var p = new BlockPos(2, 3, 5);
    var east = underground(h, FactoryKind.UNDERGROUND, p, 5)[0];
    var q = new BlockPos(5, 3, 8);
    var north = belt(h, FactoryKind.UNDERGROUND, q, Direction.NORTH);
    var northOut = belt(h, FactoryKind.UNDERGROUND, q.north(5), Direction.NORTH);
    northOut.toggleUnderground();
    BeltSimulation.topologyChanged(north);
    east.inventory().insertItem(0, new ItemStack(Items.DIAMOND), false);
    north.inventory().insertItem(0, new ItemStack(Items.EMERALD), false);
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              count(east) == 1
                  && east.lane(0).carriers().getFirst().value.is(Items.DIAMOND)
                  && count(north) == 1
                  && north.lane(0).carriers().getFirst().value.is(Items.EMERALD),
              "crossing virtual paths mixed or duplicated resources");
          h.assertTrue(
              h.getLevel().getBlockState(h.absolutePos(new BlockPos(5, 3, 5))).isAir(),
              "crossing created a tunnel block");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void everyLiftCellDemolishesIntoOnePortableOwner(GameTestHelper h) {
    var p = new BlockPos(4, 3, 4);
    for (int hit = 0; hit <= 3; hit++) {
      var b = lift(h, FactoryKind.LIFT, p, 3, 0);
      seed(b);
      h.getLevel().destroyBlock(h.absolutePos(p.above(hit)), true);
      var entities =
          h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(p)).inflate(5));
      h.assertTrue(
          entities.size() == 1
              && entities
                  .getFirst()
                  .getItem()
                  .is(Content.FACTORY.get(FactoryKind.LIFT).get().asItem()),
          "lift cell created duplicate/loose drops: " + hit);
      var tag = entities.getFirst().getItem().get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
      h.assertTrue(
          tag.getList("Lane0", 10).size() + tag.getList("Lane1", 10).size() == 8,
          "demolition lost owned contents: " + hit);
      for (int y = 0; y <= 3; y++)
        h.assertTrue(
            h.getLevel().getBlockState(h.absolutePos(p.above(y))).isAir(),
            "lift demolition left cell " + y);
      entities.forEach(ItemEntity::discard);
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void destroyingUndergroundInputDropsTransitExactlyOnce(GameTestHelper h) {
    var p = new BlockPos(2, 3, 3);
    var pair = underground(h, FactoryKind.UNDERGROUND, p, 5);
    seed(pair[0]);
    var output = pair[1].inventory();
    h.getLevel().destroyBlock(pair[0].getBlockPos(), true);
    var entities =
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(p)).inflate(5));
    int iron = 0, copper = 0, terminal = 0;
    for (var e : entities) {
      var stack = e.getItem();
      if (stack.is(Items.RAW_IRON)) iron += stack.getCount();
      if (stack.is(Items.RAW_COPPER)) copper += stack.getCount();
      if (stack.is(Content.FACTORY.get(FactoryKind.UNDERGROUND).get().asItem()))
        terminal += stack.getCount();
    }
    h.assertTrue(
        iron == 4 && copper == 4 && terminal == 1, "input demolition changed transit ledger");
    h.assertTrue(
        output.extractItem(0, 1, false).isEmpty() && count(pair[1]) == 0,
        "output retained duplicate transit");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void liftRejectsEntityInUpperClearanceWithoutSpendingItem(GameTestHelper h) {
    var p = new BlockPos(4, 3, 4);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setYRot(180);
    player.setPos(Vec3.atCenterOf(h.absolutePos(p.east(3))));
    player.setItemInHand(
        InteractionHand.MAIN_HAND,
        new ItemStack(Content.FACTORY.get(FactoryKind.LIFT).get().asItem(), 2));
    click(h, player, p);
    var rabbit = net.minecraft.world.entity.EntityType.RABBIT.create(h.getLevel());
    var at = h.absolutePos(p.above(2));
    rabbit.setPos(at.getX() + .5, at.getY() + .4, at.getZ() + .5);
    rabbit.setNoGravity(true);
    h.getLevel().addFreshEntity(rabbit);
    click(h, player, p.above(4));
    h.assertTrue(
        player.getMainHandItem().getCount() == 2
            && h.getLevel().getBlockState(h.absolutePos(p)).isAir(),
        "lift body formed through a living entity");
    rabbit.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void rotatingForcedSlopeRefreshesAuxiliaryGeometry(GameTestHelper h) {
    var p = new BlockPos(4, 3, 4);
    var b = belt(h, FactoryKind.BELT, p, Direction.NORTH);
    b.correctForm();
    h.assertTrue(b.correctForm(), "forced up form failed");
    var absolute = h.absolutePos(p);
    h.getLevel()
        .setBlockAndUpdate(
            absolute, b.getBlockState().setValue(FactoryBlock.FACING, Direction.EAST));
    var part = (TransportPartBlockEntity) h.getLevel().getBlockEntity(absolute.above());
    h.assertTrue(
        b.route().shape == BeltRoute.Shape.UP
            && part != null
            && part.cell().facing() == Direction.EAST,
        "rotated slope retained old part mesh");
    h.assertTrue(
        b.getBlockState().getCollisionShape(h.getLevel(), absolute).max(Direction.Axis.Y) > .95,
        "incline collision remained flat");
    h.succeed();
  }
}
