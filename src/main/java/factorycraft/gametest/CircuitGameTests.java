package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.circuits.*;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class CircuitGameTests {
  static CircuitNodeBlockEntity node(
      GameTestHelper h, FactoryKind kind, int x, int z, Direction facing) {
    var p = h.absolutePos(new BlockPos(x, 3, z));
    StructurePlacement.place(
        h.getLevel(),
        p,
        Content.FACTORY.get(kind).get().defaultBlockState().setValue(FactoryBlock.FACING, facing));
    return (CircuitNodeBlockEntity) h.getLevel().getBlockEntity(p);
  }

  static void program(GameTestHelper h, CircuitNodeBlockEntity n, int op, int operand) {
    h.assertTrue(
        n.configure(new CircuitProgram(CircuitProgram.A, CircuitProgram.A, op, operand, false)),
        "program rejected");
  }

  static void link(
      GameTestHelper h,
      CircuitNodeBlockEntity a,
      int pa,
      CircuitNodeBlockEntity b,
      int pb,
      int color) {
    h.assertTrue(CircuitSimulation.connect(a, pa, b, pb, color), "wire rejected");
  }

  static ChestBlockEntity chest(GameTestHelper h, BlockPos p) {
    h.getLevel().setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
    return (ChestBlockEntity) h.getLevel().getBlockEntity(p);
  }

  static int count(ChestBlockEntity c) {
    int total = 0;
    for (int i = 0; i < c.getContainerSize(); i++) total += c.getItem(i).getCount();
    return total;
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void stagedArithmeticDeciderAndColorSumUseSeparatePhysicalPorts(GameTestHelper h) {
    var a = node(h, FactoryKind.CONSTANT, 4, 5, Direction.NORTH);
    var b = node(h, FactoryKind.ARITHMETIC, 8, 5, Direction.NORTH);
    var c = node(h, FactoryKind.DECIDER, 12, 5, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          program(h, a, 0, 7);
          program(h, b, 0, 3);
          program(h, c, 0, 16);
          link(h, a, 0, b, 0, 0);
          link(h, a, 0, b, 0, 1);
          link(h, b, 1, c, 0, 0);
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              b.inputValue == 14 && b.lastOutput == 17 && c.inputValue == 17 && c.lastOutput == 1,
              "wrong staged/color sum: " + b.inputValue + "/" + b.lastOutput + "/" + c.lastOutput);
          h.assertTrue(
              b.portAt(b.getBlockPos()) == 1 && b.portAt(b.assembly().positions().getFirst()) == 0,
              "physical ports reversed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void reciprocalWireRemovalSplitsAndRejoinsWithoutStaleSignals(GameTestHelper h) {
    var a = node(h, FactoryKind.CONSTANT, 4, 5, Direction.NORTH);
    var b = node(h, FactoryKind.ARITHMETIC, 8, 5, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          program(h, a, 0, 9);
          program(h, b, 2, 2);
          link(h, a, 0, b, 0, 0);
        });
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(b.lastOutput == 18, "arithmetic missing");
          link(h, a, 0, b, 0, 0);
          h.assertTrue(a.links.isEmpty() && b.links.isEmpty(), "wire removal not reciprocal");
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(!CircuitSimulation.inputReady(b), "unwired input stayed ready");
          link(h, a, 0, b, 0, 0);
          program(h, a, 0, 11);
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(b.lastOutput == 22, "rejoined signal stale");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void unavailableSourceImmediatelyQuarantinesThenRestoresBus(GameTestHelper h) {
    var a = node(h, FactoryKind.CONSTANT, 4, 5, Direction.NORTH);
    var b = node(h, FactoryKind.CIRCUIT_CONTROLLER, 8, 5, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          program(h, a, 0, 7);
          program(h, b, 0, 0);
          link(h, a, 0, b, 0, 0);
        });
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(CircuitSimulation.inputReady(b), "bus not ready");
          a.onChunkUnloaded();
          h.assertTrue(!CircuitSimulation.inputReady(b), "removed graph port remained valid");
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              !CircuitSimulation.inputReady(b) && b.links.size() == 1,
              "unload erased link or opened bus");
          a.onLoad();
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(
              CircuitSimulation.inputReady(b) && b.inputValue == 7,
              "reload did not restore UUID link");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void inventoryFeedbackStopsPaidInserterAtExactCountAndPreservesCargo(
      GameTestHelper h) {
    var armPos = h.absolutePos(new BlockPos(8, 3, 5));
    h.getLevel()
        .setBlockAndUpdate(
            armPos,
            Content.FACTORY
                .get(FactoryKind.FAST_INSERTER)
                .get()
                .defaultBlockState()
                .setValue(FactoryBlock.FACING, Direction.NORTH));
    var arm = (InserterBlockEntity) h.getLevel().getBlockEntity(armPos);
    var source = chest(h, arm.sourcePosition());
    var target = chest(h, arm.targetPosition());
    source.setItem(0, new ItemStack(Items.RAW_IRON, 32));
    var reader = node(h, FactoryKind.CIRCUIT_READER, 9, 4, Direction.WEST);
    var controller = node(h, FactoryKind.CIRCUIT_CONTROLLER, 9, 5, Direction.WEST);
    h.runAfterDelay(
        5,
        () -> {
          program(h, controller, 1, 8);
          link(h, reader, 0, controller, 0, 0);
        });
    h.runAfterDelay(15, () -> arm.power.restore(1000));
    h.runAfterDelay(
        170,
        () -> {
          h.assertTrue(
              count(target) == 8 && count(source) == 24 && arm.held().isEmpty(),
              "feedback lost/overshot items: " + count(target));
          h.assertTrue(
              arm.circuitBinding != null && !controller.gate, "device was not bound/disabled");
        });
    h.runAfterDelay(
        180,
        () -> {
          int fe = arm.power.getEnergyStored(), phase = arm.phase();
          for (int i = 0; i < 50; i++) arm.tick();
          h.assertTrue(
              arm.power.getEnergyStored() == fe && arm.phase() == phase,
              "closed gate billed or advanced arm");
          controller.onChunkUnloaded();
          h.assertTrue(!CircuitBinding.allowed(arm), "unloaded controller opened device");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void missingReaderInventoryFailsClosedEvenForZeroEqualsZero(GameTestHelper h) {
    var reader = node(h, FactoryKind.CIRCUIT_READER, 5, 5, Direction.NORTH);
    var gate = node(h, FactoryKind.CIRCUIT_CONTROLLER, 9, 5, Direction.NORTH);
    var p = gate.targetPosition();
    h.getLevel()
        .setBlockAndUpdate(
            p, Content.FACTORY.get(FactoryKind.FAST_INSERTER).get().defaultBlockState());
    var arm = (InserterBlockEntity) h.getLevel().getBlockEntity(p);
    h.runAfterDelay(
        5,
        () -> {
          program(h, gate, 2, 0);
          link(h, reader, 0, gate, 0, 0);
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              !gate.gate && !CircuitBinding.allowed(arm),
              "missing inventory was treated as valid zero");
          chest(h, reader.targetPosition());
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(
              gate.gate && CircuitBinding.allowed(arm),
              "empty real inventory did not enable equality");
          h.getLevel().removeBlock(reader.targetPosition(), false);
        });
    h.runAfterDelay(
        75,
        () -> {
          h.assertTrue(
              !gate.gate && !CircuitBinding.allowed(arm), "removed inventory stayed ready");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void menusKeepGhostSamplesAndRejectStaleSpectatorAndFarEdits(GameTestHelper h) {
    var n = node(h, FactoryKind.ARITHMETIC, 5, 5, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          var p = h.makeMockPlayer(GameType.SURVIVAL);
          p.setPos(n.getBlockPos().getCenter());
          var m = new CircuitMenu(41, p.getInventory(), n);
          p.containerMenu = m;
          m.setCarried(new ItemStack(Items.DIAMOND, 13));
          m.clicked(0, 0, ClickType.PICKUP, p);
          h.assertTrue(
              m.getCarried().getCount() == 13
                  && n.program().input().toString().equals("minecraft:diamond"),
              "ghost consumed sample");
          h.assertTrue(m.operand(p, Integer.MIN_VALUE), "signed operand rejected");
          p.containerMenu = p.inventoryMenu;
          h.assertTrue(!m.operand(p, 7), "stale payload accepted");
          m.clicked(0, 1, ClickType.PICKUP, p);
          h.assertTrue(
              n.program().input().toString().equals("minecraft:diamond"), "stale click accepted");
          p.containerMenu = m;
          p.setPos(n.getBlockPos().getCenter().add(20, 0, 0));
          h.assertTrue(!m.operand(p, 8), "far edit accepted");
          var spectator = h.makeMockPlayer(GameType.SPECTATOR);
          spectator.setPos(n.getBlockPos().getCenter());
          var s = new CircuitMenu(42, spectator.getInventory(), n);
          spectator.containerMenu = s;
          h.assertTrue(!s.operand(spectator, 9), "spectator edited program");
          h.succeed();
        });
  }

  @LegacyAssemblyFixture(template = "empty", timeoutTicks = 120)
  public static void pumpGateStopsBillingAndResumesFiniteFluidWithoutLoss(GameTestHelper h) {
    var pp = h.absolutePos(new BlockPos(8, 3, 5));
    StructurePlacement.place(
        h.getLevel(),
        pp,
        Content.FLUID_BLOCKS
            .get(factorycraft.fluids.FluidKind.PUMP)
            .get()
            .defaultBlockState()
            .setValue(FluidDeviceBlock.FACING, Direction.EAST));
    var pump = (FluidBlockEntity) h.getLevel().getBlockEntity(pp);
    var tp = pp.east();
    StructurePlacement.place(
        h.getLevel(),
        tp,
        Content.FLUID_BLOCKS.get(factorycraft.fluids.FluidKind.TANK).get().defaultBlockState());
    var tank = (FluidBlockEntity) h.getLevel().getBlockEntity(tp);
    var a = node(h, FactoryKind.CONSTANT, 4, 7, Direction.NORTH);
    var b = node(h, FactoryKind.CIRCUIT_CONTROLLER, 8, 6, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          program(h, a, 0, 0);
          program(h, b, 0, 0);
          link(h, a, 0, b, 0, 0);
        });
    h.runAfterDelay(
        15,
        () -> {
          pump.port(Direction.WEST)
              .fill(
                  new net.neoforged.neoforge.fluids.FluidStack(
                      net.minecraft.world.level.material.Fluids.WATER, 500),
                  net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
          pump.power.restore(200);
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              pump.cell().amount() == 500
                  && tank.cell().amount() == 0
                  && pump.power.getEnergyStored() == 200,
              "disabled pump moved or billed");
          program(h, a, 0, 1);
        });
    h.runAfterDelay(
        55,
        () -> {
          h.assertTrue(
              tank.cell().amount() == 500
                  && pump.cell().amount() == 0
                  && pump.power.getEnergyStored() < 200,
              "enabled finite pump failed");
          program(h, a, 0, 0);
        });
    h.runAfterDelay(
        70,
        () -> {
          pump.port(Direction.WEST)
              .fill(
                  new net.neoforged.neoforge.fluids.FluidStack(
                      net.minecraft.world.level.material.Fluids.WATER, 400),
                  net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
          pump.power.restore(100);
        });
    h.runAfterDelay(
        95,
        () -> {
          h.assertTrue(
              pump.cell().amount() == 400
                  && tank.cell().amount() == 500
                  && pump.power.getEnergyStored() == 100,
              "reclosed pump lost volume or billed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void nativeWireToolSelectsPhysicalPortsWithoutConsumption(GameTestHelper h) {
    var a = node(h, FactoryKind.CONSTANT, 4, 5, Direction.NORTH);
    var b = node(h, FactoryKind.ARITHMETIC, 8, 5, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          var player = h.makeMockPlayer(GameType.SURVIVAL);
          player.setItemInHand(
              InteractionHand.MAIN_HAND, new ItemStack(Content.RED_CIRCUIT_WIRE.get()));
          for (var p : List.of(a.getBlockPos(), b.portPosition(0)))
            h.assertTrue(
                player
                    .getMainHandItem()
                    .useOn(
                        new net.minecraft.world.item.context.UseOnContext(
                            player,
                            InteractionHand.MAIN_HAND,
                            new net.minecraft.world.phys.BlockHitResult(
                                p.getCenter(), Direction.UP, p, false)))
                    .consumesAction(),
                "native wire click rejected");
          h.assertTrue(
              a.links.size() == 1
                  && b.links.getFirst().localPort() == 0
                  && player.getMainHandItem().getCount() == 1,
              "native wire consumed item or wrong physical port");
          player.setShiftKeyDown(true);
          var p = b.portPosition(0);
          player
              .getMainHandItem()
              .useOn(
                  new net.minecraft.world.item.context.UseOnContext(
                      player,
                      InteractionHand.MAIN_HAND,
                      new net.minecraft.world.phys.BlockHitResult(
                          p.getCenter(), Direction.UP, p, false)));
          h.assertTrue(a.links.isEmpty() && b.links.isEmpty(), "shift clear not reciprocal");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void portableCircuitKeepsProgramButClearsLinksAndOldIdentity(GameTestHelper h) {
    var a = node(h, FactoryKind.CONSTANT, 4, 5, Direction.NORTH);
    var b = node(h, FactoryKind.ARITHMETIC, 8, 5, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          program(h, b, 2, -123);
          link(h, a, 0, b, 0, 0);
          var old = b.assembly().identity();
          h.getLevel().destroyBlock(b.portPosition(0), true);
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new net.minecraft.world.phys.AABB(b.getBlockPos()).inflate(3));
          h.assertTrue(drops.size() == 1, "portable combinator returned multiple owners");
          var stack = drops.getFirst().getItem().copy();
          drops.getFirst().discard();
          var player = h.makeMockPlayer(GameType.SURVIVAL);
          player.setItemInHand(InteractionHand.MAIN_HAND, stack);
          var pos = h.absolutePos(new BlockPos(15, 3, 5));
          var ctx =
              new net.minecraft.world.item.context.UseOnContext(
                  player,
                  InteractionHand.MAIN_HAND,
                  new net.minecraft.world.phys.BlockHitResult(
                      pos.getCenter(), Direction.UP, pos, false));
          var placement = new net.minecraft.world.item.context.BlockPlaceContext(ctx);
          var state =
              Content.FACTORY.get(FactoryKind.ARITHMETIC).get().getStateForPlacement(placement);
          var plan = factorycraft.multiblock.PlacementPlan.inspect(placement, state);
          h.assertTrue(stack.useOn(ctx).consumesAction(), "portable placement rejected");
          var copy = (CircuitNodeBlockEntity) h.getLevel().getBlockEntity(plan.anchor());
          h.assertTrue(
              copy.program().operand() == -123
                  && copy.program().operation() == 2
                  && copy.links.isEmpty()
                  && !copy.assembly().identity().equals(old)
                  && a.links.isEmpty(),
              "portable program/links/identity invalid");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void finitePolePoweredExampleStopsAtEightAndEvaluatesBothColors(GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    factorycraft.diagnostics.CircuitFactory.build(h.getLevel(), o);
    h.runAfterDelay(
        340,
        () -> {
          var arm = (InserterBlockEntity) h.getLevel().getBlockEntity(o.offset(8, 0, 5));
          var target = (ChestBlockEntity) h.getLevel().getBlockEntity(arm.targetPosition());
          var source = (ChestBlockEntity) h.getLevel().getBlockEntity(arm.sourcePosition());
          var arithmetic = (CircuitNodeBlockEntity) h.getLevel().getBlockEntity(o.offset(20, 0, 4));
          var decider = (CircuitNodeBlockEntity) h.getLevel().getBlockEntity(o.offset(24, 0, 4));
          h.assertTrue(
              count(target) == 8
                  && count(source) == 24
                  && arm.held().isEmpty()
                  && arm.power.getEnergyStored() > 0
                  && arithmetic.lastOutput == 7
                  && decider.lastOutput == 1,
              "finite real-powered circuit example failed: " + count(target));
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void circuitProgramsAndReciprocalLinksRetainExactDiskState(GameTestHelper h) {
    var level = h.getLevel();
    var a = new BlockPos(15015, 70, 15015);
    var b = new BlockPos(15017, 70, 15015);
    for (var p : List.of(a, b, b.south())) level.getChunkAt(p);
    boolean fresh =
        level.getBlockState(a).isAir()
            && level.getBlockState(b).isAir()
            && level.getBlockState(b.south()).isAir();
    if (fresh) {
      StructurePlacement.place(
          level, a, Content.FACTORY.get(FactoryKind.CONSTANT).get().defaultBlockState());
      StructurePlacement.place(
          level,
          b,
          Content.FACTORY
              .get(FactoryKind.ARITHMETIC)
              .get()
              .defaultBlockState()
              .setValue(FactoryBlock.FACING, Direction.NORTH));
      var ca = (CircuitNodeBlockEntity) level.getBlockEntity(a);
      var cb = (CircuitNodeBlockEntity) level.getBlockEntity(b);
      var ta = ca.saveWithoutMetadata(level.registryAccess());
      ta.getCompound("Circuit")
          .put(
              "Program",
              new CircuitProgram(CircuitProgram.A, CircuitProgram.A, 0, 7, false).save());
      ta.getCompound("Circuit").putInt("Output", 7);
      ca.loadWithComponents(ta, level.registryAccess());
      var tb = cb.saveWithoutMetadata(level.registryAccess());
      tb.getCompound("Circuit")
          .put(
              "Program",
              new CircuitProgram(CircuitProgram.A, CircuitProgram.A, 0, 3, false).save());
      tb.getCompound("Circuit").putInt("Output", 10);
      tb.getCompound("Circuit").putInt("InputValue", 7);
      cb.loadWithComponents(tb, level.registryAccess());
      ca.links.add(new CircuitLink(b, cb.assembly().identity(), 0, 0, 0));
      cb.links.add(new CircuitLink(a, ca.assembly().identity(), 0, 0, 0));
      ca.setChanged();
      cb.setChanged();
    }
    h.assertTrue(
        level.getBlockEntity(a) instanceof CircuitNodeBlockEntity
            && level.getBlockEntity(b) instanceof CircuitNodeBlockEntity,
        "partial circuit checkpoint; refusing refill");
    var ca = (CircuitNodeBlockEntity) level.getBlockEntity(a);
    var cb = (CircuitNodeBlockEntity) level.getBlockEntity(b);
    h.assertTrue(
        ca.lastOutput == 7
            && cb.lastOutput == 10
            && cb.inputValue == 7
            && ca.program().operand() == 7
            && cb.program().operand() == 3,
        "disk signal/program changed");
    h.assertTrue(
        ca.links.equals(List.of(new CircuitLink(b, cb.assembly().identity(), 0, 0, 0)))
            && cb.links.equals(List.of(new CircuitLink(a, ca.assembly().identity(), 0, 0, 0))),
        "disk reciprocal UUID links changed");
    h.assertTrue(
        level.getBlockEntity(b.south()) instanceof CasingBlockEntity part
            && part.boundTo(b, cb.assembly().identity()),
        "disk output/input assembly ownership changed");
    ca.onChunkUnloaded();
    cb.onChunkUnloaded();
    for (var p : List.of(a, b, b.south())) level.getChunkAt(p).setUnsaved(true);
    System.out.println(
        "FACTORYCRAFT_CIRCUIT_DISK: "
            + (fresh ? "SEEDED" : "VERIFIED")
            + " 7 -> ADD 3 -> 10 reciprocal UUID ports");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void explicitControllerBindingAndOwnedHandSurviveDiskReload(GameTestHelper h) {
    var level = h.getLevel();
    var p = new BlockPos(15119, 70, 15119);
    var c = p.west();
    level.getChunkAt(p);
    level.getChunkAt(c);
    boolean fresh = level.getBlockState(p).isAir() && level.getBlockState(c).isAir();
    if (fresh) {
      StructurePlacement.place(
          level,
          c,
          Content.FACTORY
              .get(FactoryKind.CIRCUIT_CONTROLLER)
              .get()
              .defaultBlockState()
              .setValue(FactoryBlock.FACING, Direction.EAST));
      level.setBlockAndUpdate(
          p, Content.FACTORY.get(FactoryKind.FAST_INSERTER).get().defaultBlockState());
      var arm = (InserterBlockEntity) level.getBlockEntity(p);
      var node = (CircuitNodeBlockEntity) level.getBlockEntity(c);
      var tag = arm.saveWithoutMetadata(level.registryAccess());
      tag.getCompound("Actuator").putBoolean("Enabled", false);
      arm.loadWithComponents(tag, level.registryAccess());
      arm.hand.setStackInSlot(0, new ItemStack(Items.DIAMOND));
      arm.power.restore(731);
      CircuitBinding.set(arm, new CircuitBinding(c, node.assembly().identity()));
      arm.setChanged();
      node.setChanged();
    }
    h.assertTrue(
        level.getBlockEntity(p) instanceof InserterBlockEntity
            && level.getBlockEntity(c) instanceof CircuitNodeBlockEntity,
        "partial binding checkpoint; refusing refill");
    var arm = (InserterBlockEntity) level.getBlockEntity(p);
    var node = (CircuitNodeBlockEntity) level.getBlockEntity(c);
    h.assertTrue(
        new CircuitBinding(c, node.assembly().identity()).equals(arm.circuitBinding)
            && arm.held().is(Items.DIAMOND)
            && arm.held().getCount() == 1
            && arm.power.getEnergyStored() == 731
            && !arm.enabled()
            && !CircuitBinding.allowed(arm),
        "binding/hand/FE disk ledger changed");
    arm.onChunkUnloaded();
    node.onChunkUnloaded();
    level.getChunkAt(p).setUnsaved(true);
    level.getChunkAt(c).setUnsaved(true);
    System.out.println(
        "FACTORYCRAFT_CIRCUIT_BINDING_DISK: "
            + (fresh ? "SEEDED" : "VERIFIED")
            + " UUID binding diamond=1 FE=731 disabled");
    h.succeed();
  }
}
