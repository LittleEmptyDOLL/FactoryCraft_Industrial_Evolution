package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.circuits.*;
import factorycraft.logistics.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class SplitterCircuitGameTests {
  private static final FactoryKind[] KINDS = {
    FactoryKind.SPLITTER, FactoryKind.FAST_SPLITTER, FactoryKind.EXPRESS_SPLITTER
  };

  private static BeltBlockEntity split(GameTestHelper h, int tier, BlockPos p) {
    StructurePlacement.place(
        h.getLevel(), p, Content.FACTORY.get(KINDS[tier]).get().defaultBlockState());
    return (BeltBlockEntity) h.getLevel().getBlockEntity(p);
  }

  private static List<Integer> positions(BeltBlockEntity b) {
    var out = new ArrayList<Integer>();
    for (int i = 0; i < 4; i++) for (var c : b.lane(i).carriers()) out.add(c.positionUnits());
    return out;
  }

  private static void seed(BeltBlockEntity b) {
    for (int i = 0; i < 4; i++)
      for (int j = 0; j < 4; j++)
        b.lane(i)
            .restore(new ItemStack(j % 2 == 0 ? Items.RAW_IRON : Items.RAW_COPPER), 1 - j * .25);
    b.wakeTransport();
    b.sync();
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void fourColorsAreIndependentAtSharedPhysicalPortsAndSumAtInput(GameTestHelper h) {
    var a = CircuitGameTests.node(h, FactoryKind.CONSTANT, 3, 5, Direction.NORTH);
    var b = CircuitGameTests.node(h, FactoryKind.CONSTANT, 3, 9, Direction.NORTH);
    var sum = CircuitGameTests.node(h, FactoryKind.ARITHMETIC, 7, 5, Direction.NORTH);
    var blue = CircuitGameTests.node(h, FactoryKind.ARITHMETIC, 11, 5, Direction.NORTH);
    var white = CircuitGameTests.node(h, FactoryKind.ARITHMETIC, 11, 9, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          CircuitGameTests.program(h, a, 0, 2);
          CircuitGameTests.program(h, b, 0, 3);
          for (int c = 0; c < 4; c++) CircuitGameTests.link(h, c % 2 == 0 ? a : b, 0, sum, 0, c);
          CircuitGameTests.link(h, sum, 0, blue, 0, 2);
          CircuitGameTests.link(h, sum, 0, white, 0, 3);
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              sum.lastOutput == 10 && blue.lastOutput == 2 && white.lastOutput == 3,
              "colors mixed or not summed: "
                  + sum.lastOutput
                  + "/"
                  + blue.lastOutput
                  + "/"
                  + white.lastOutput);
          CircuitSimulation.clear(blue, 0, 2);
          CircuitGameTests.program(h, a, 0, 7);
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(
              sum.lastOutput == 20 && white.lastOutput == 3 && !CircuitSimulation.inputReady(blue),
              "color-specific removal leaked");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void nativeBlueWhiteToolsToggleOnlyTheirOwnColorWithoutConsumption(
      GameTestHelper h) {
    var a = CircuitGameTests.node(h, FactoryKind.CONSTANT, 4, 5, Direction.NORTH);
    var b = CircuitGameTests.node(h, FactoryKind.ARITHMETIC, 8, 5, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          var p = h.makeMockPlayer(GameType.SURVIVAL);
          for (var item :
              List.of(Content.BLUE_CIRCUIT_WIRE.get(), Content.WHITE_CIRCUIT_WIRE.get())) {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            for (var pos : List.of(a.getBlockPos(), b.portPosition(0)))
              h.assertTrue(
                  p.getMainHandItem()
                      .useOn(
                          new net.minecraft.world.item.context.UseOnContext(
                              p,
                              InteractionHand.MAIN_HAND,
                              new net.minecraft.world.phys.BlockHitResult(
                                  pos.getCenter(), Direction.UP, pos, false)))
                      .consumesAction(),
                  "wire click failed");
            h.assertTrue(p.getMainHandItem().getCount() == 1, "wire consumed");
          }
          h.assertTrue(
              a.links.size() == 2
                  && a.links.stream().anyMatch(l -> l.color() == 2)
                  && a.links.stream().anyMatch(l -> l.color() == 3),
              "new colors absent");
          CircuitSimulation.clear(a, 0, 2);
          h.assertTrue(
              a.links.size() == 1 && a.links.getFirst().color() == 3 && b.links.size() == 1,
              "blue clear erased white");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void allSplitterTiersPauseEveryLaneAndCloseImmediatelyOnUnknownInput(
      GameTestHelper h) {
    var belts = new ArrayList<BeltBlockEntity>();
    var gates = new ArrayList<CircuitNodeBlockEntity>();
    var sources = new ArrayList<CircuitNodeBlockEntity>();
    for (int i = 0; i < 3; i++) {
      var p = h.absolutePos(new BlockPos(4 + 8 * i, 3, 5));
      belts.add(split(h, i, p));
      gates.add(
          CircuitGameTests.node(h, FactoryKind.CIRCUIT_CONTROLLER, 3 + 8 * i, 5, Direction.EAST));
      sources.add(CircuitGameTests.node(h, FactoryKind.CONSTANT, 4 + 8 * i, 9, Direction.NORTH));
    }
    h.runAfterDelay(
        5,
        () -> {
          for (int i = 0; i < 3; i++) {
            CircuitGameTests.program(h, sources.get(i), 0, 0);
            CircuitGameTests.link(h, sources.get(i), 0, gates.get(i), 0, i + 1);
          }
        });
    h.runAfterDelay(
        20,
        () -> {
          for (var b : belts) {
            h.assertTrue(
                b.circuitBinding != null && !b.circuitRunning(), "closed splitter not bound");
            seed(b);
          }
        });
    h.runAfterDelay(
        40,
        () -> {
          for (var b : belts) {
            h.assertTrue(
                positions(b)
                    .equals(
                        List.of(96, 72, 48, 24, 96, 72, 48, 24, 96, 72, 48, 24, 96, 72, 48, 24)),
                "closed lanes moved");
            h.assertTrue(
                !b.inventory().insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty(),
                "closed port accepted item");
          }
          for (var n : sources) CircuitGameTests.program(h, n, 0, 1);
        });
    h.runAfterDelay(
        65,
        () -> {
          for (int i = 0; i < 3; i++) {
            var b = belts.get(i);
            h.assertTrue(b.circuitRunning(), "gate failed to open");
            sources.get(i).onChunkUnloaded();
            h.assertTrue(
                !b.circuitRunning(), "unknown source did not close cached gate immediately");
          }
        });
    h.runAfterDelay(
        75,
        () -> {
          for (int i = 0; i < 3; i++) {
            h.assertTrue(!belts.get(i).circuitRunning(), "unloaded source opened gate");
            sources.get(i).onLoad();
          }
        });
    h.runAfterDelay(
        100,
        () -> {
          for (var b : belts) h.assertTrue(b.circuitRunning(), "reload did not restore gate");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void controllerCanBindSplitterPartAndRemovalRestoresUnboundOperation(
      GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(5, 3, 5));
    var b = split(h, 0, p);
    var a = CircuitGameTests.node(h, FactoryKind.CONSTANT, 10, 8, Direction.NORTH);
    var gate = CircuitGameTests.node(h, FactoryKind.CIRCUIT_CONTROLLER, 7, 5, Direction.WEST);
    h.runAfterDelay(
        5,
        () -> {
          CircuitGameTests.program(h, a, 0, 0);
          CircuitGameTests.link(h, a, 0, gate, 0, 3);
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              b.circuitBinding != null
                  && !b.circuitRunning()
                  && gate.targetPosition().equals(b.trackPosition(1)),
              "part did not control whole splitter");
          h.getLevel().destroyBlock(gate.getBlockPos(), true);
          h.assertTrue(
              b.circuitBinding == null && b.circuitRunning(),
              "destroyed controller left stale stop");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void portablePausedSplitterKeepsCargoPolicyAndClearsControllerCoordinates(
      GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(5, 3, 5));
    var b = split(h, 2, p);
    var gate = CircuitGameTests.node(h, FactoryKind.CIRCUIT_CONTROLLER, 4, 5, Direction.EAST);
    h.runAfterDelay(
        5,
        () -> {
          CircuitBinding.set(b, new CircuitBinding(gate.getBlockPos(), gate.assembly().identity()));
          h.assertTrue(
              b.configureSplitter(
                  new SplitterSettings(
                      2,
                      1,
                      net.minecraft.resources.ResourceLocation.withDefaultNamespace("raw_iron"))),
              "portable policy rejected");
          seed(b);
          var identity = b.assembly().identity();
          h.getLevel().destroyBlock(b.trackPosition(1), true);
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new net.minecraft.world.phys.AABB(p).inflate(3));
          h.assertTrue(drops.size() == 1, "multiple portable owners");
          var stack = drops.getFirst().getItem().copy();
          drops.getFirst().discard();
          h.assertTrue(
              !stack
                  .get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
                  .copyTag()
                  .contains("CircuitControl"),
              "portable copied control coordinates");
          var player = h.makeMockPlayer(GameType.SURVIVAL);
          player.setItemInHand(InteractionHand.MAIN_HAND, stack);
          var pos = h.absolutePos(new BlockPos(15, 3, 5));
          var ctx =
              new net.minecraft.world.item.context.UseOnContext(
                  player,
                  InteractionHand.MAIN_HAND,
                  new net.minecraft.world.phys.BlockHitResult(
                      pos.getCenter(), Direction.UP, pos, false));
          var place = new net.minecraft.world.item.context.BlockPlaceContext(ctx);
          var state =
              Content.FACTORY.get(FactoryKind.EXPRESS_SPLITTER).get().getStateForPlacement(place);
          var plan = PlacementPlan.inspect(place, state);
          h.assertTrue(stack.useOn(ctx).consumesAction(), "portable placement failed");
          var copy = (BeltBlockEntity) h.getLevel().getBlockEntity(plan.anchor());
          h.assertTrue(
              copy.circuitBinding == null
                  && copy.splitterSettings()
                      .equals(
                          new SplitterSettings(
                              2,
                              1,
                              net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                                  "raw_iron")))
                  && copy.circuitRunning()
                  && positions(copy).size() == 16
                  && !copy.assembly().identity().equals(identity),
              "portable cargo/identity/control changed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 2800)
  public static void finiteThreeTierCircuitSortingStopsThenDeliversBothProducts(GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    factorycraft.diagnostics.CircuitSortingFactory.build(h.getLevel(), o);
    h.runAfterDelay(
        80,
        () -> {
          for (int i = 0; i < 3; i++) {
            var b = (BeltBlockEntity) h.getLevel().getBlockEntity(o.offset(2 + 8 * i, 0, 5));
            h.assertTrue(!b.circuitRunning(), "example started open");
            for (var dest : List.of(o.offset(8 * i, 0, 4), o.offset(5 + 8 * i, 0, 4)))
              h.assertTrue(
                  CircuitGameTests.count((ChestBlockEntity) h.getLevel().getBlockEntity(dest)) == 0,
                  "closed example delivered items");
            CircuitGameTests.program(
                h,
                (CircuitNodeBlockEntity) h.getLevel().getBlockEntity(o.offset(2 + 8 * i, 0, 1)),
                0,
                1);
          }
        });
    h.succeedWhen(
        () -> {
          for (int i = 0; i < 3; i++) {
            var left = (ChestBlockEntity) h.getLevel().getBlockEntity(o.offset(8 * i, 0, 4));
            var right = (ChestBlockEntity) h.getLevel().getBlockEntity(o.offset(5 + 8 * i, 0, 4));
            h.assertTrue(
                CircuitGameTests.count(left) == 32 && CircuitGameTests.count(right) == 32,
                "sorting line not drained");
            for (int slot = 0; slot < left.getContainerSize(); slot++)
              h.assertTrue(
                  left.getItem(slot).isEmpty() || left.getItem(slot).is(Items.RAW_IRON),
                  "left wrong product");
            for (int slot = 0; slot < right.getContainerSize(); slot++)
              h.assertTrue(
                  right.getItem(slot).isEmpty() || right.getItem(slot).is(Items.RAW_COPPER),
                  "right wrong product");
          }
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void fourColorBindingsAndPausedSplittersRetainExactDiskState(GameTestHelper h) {
    var level = h.getLevel();
    for (int tier = 0; tier < 3; tier++) {
      var p = new BlockPos(17015 + tier * 48, 70, 17015);
      var c = p.south();
      var a = p.south(4);
      var cells = new ArrayList<>(MachineProfile.SPLITTER.cells(p, Direction.NORTH));
      cells.add(c);
      cells.add(a);
      for (var cell : cells) level.getChunkAt(cell);
      boolean fresh = cells.stream().allMatch(cell -> level.getBlockState(cell).isAir());
      if (fresh) {
        var b = split(h, tier, p);
        StructurePlacement.place(
            level,
            c,
            Content.FACTORY.get(FactoryKind.CIRCUIT_CONTROLLER).get().defaultBlockState());
        StructurePlacement.place(
            level, a, Content.FACTORY.get(FactoryKind.CONSTANT).get().defaultBlockState());
        var controller = (CircuitNodeBlockEntity) level.getBlockEntity(c);
        var source = (CircuitNodeBlockEntity) level.getBlockEntity(a);
        for (int color = 0; color < 4; color++) {
          source.links.add(new CircuitLink(c, controller.assembly().identity(), 0, 0, color));
          controller.links.add(new CircuitLink(a, source.assembly().identity(), 0, 0, color));
        }
        CircuitBinding.set(b, new CircuitBinding(c, controller.assembly().identity()));
        seed(b);
        source.setChanged();
        controller.setChanged();
      }
      h.assertTrue(
          level.getBlockEntity(p) instanceof BeltBlockEntity
              && level.getBlockEntity(c) instanceof CircuitNodeBlockEntity
              && level.getBlockEntity(a) instanceof CircuitNodeBlockEntity,
          "partial gated fixture; refusing refill");
      var b = (BeltBlockEntity) level.getBlockEntity(p);
      var controller = (CircuitNodeBlockEntity) level.getBlockEntity(c);
      var source = (CircuitNodeBlockEntity) level.getBlockEntity(a);
      h.assertTrue(
          new CircuitBinding(c, controller.assembly().identity()).equals(b.circuitBinding)
              && !b.circuitRunning()
              && positions(b)
                  .equals(List.of(96, 72, 48, 24, 96, 72, 48, 24, 96, 72, 48, 24, 96, 72, 48, 24)),
          "gated cargo/binding disk values changed");
      for (int lane = 0; lane < 4; lane++)
        for (int item = 0; item < 4; item++)
          h.assertTrue(
              b.lane(lane)
                  .carriers()
                  .get(item)
                  .value
                  .is(item % 2 == 0 ? Items.RAW_IRON : Items.RAW_COPPER),
              "gated cargo item identity changed");
      for (int color = 0; color < 4; color++)
        h.assertTrue(
            source.links.contains(new CircuitLink(c, controller.assembly().identity(), 0, 0, color))
                && controller.links.contains(
                    new CircuitLink(a, source.assembly().identity(), 0, 0, color)),
            "color/UUID link lost");
      for (var part : b.assembly().positions())
        h.assertTrue(
            level.getBlockEntity(part) instanceof CasingBlockEntity partBe
                && partBe.boundTo(p, b.assembly().identity()),
            "splitter part identity changed");
      b.onChunkUnloaded();
      controller.onChunkUnloaded();
      source.onChunkUnloaded();
      for (var cell : cells) level.getChunkAt(cell).setUnsaved(true);
      System.out.println(
          "FACTORYCRAFT_SPLITTER_CIRCUIT_DISK: "
              + (fresh ? "SEEDED" : "VERIFIED")
              + " tier="
              + (tier + 1)
              + " 16 carriers, binding, four colors");
    }
    h.succeed();
  }
}
