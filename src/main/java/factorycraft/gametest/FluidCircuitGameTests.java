package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.circuits.*;
import factorycraft.diagnostics.FluidCircuitFactory;
import factorycraft.fluids.*;
import factorycraft.items.FluidCanisterItem;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class FluidCircuitGameTests {
  private static FluidStack water(int amount, int grade) {
    var f = new FluidStack(Fluids.WATER, amount);
    var data = new CompoundTag();
    data.putInt("grade", grade);
    f.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    return f;
  }

  private static CircuitNodeBlockEntity reader(GameTestHelper h) {
    return CircuitGameTests.node(h, FactoryKind.CIRCUIT_READER, 4, 5, Direction.EAST);
  }

  private static FluidBlockEntity tank(GameTestHelper h, BlockPos p) {
    return FluidCircuitFactory.fluid(h.getLevel(), p, FluidKind.TANK);
  }

  private static void fill(FluidBlockEntity t, FluidStack f) {
    t.cell().fill(new FluidKey(f), f.getAmount(), false);
    t.setChanged();
  }

  @GameTest(template = "empty", timeoutTicks = 110)
  public static void fluidReaderMatchesComponentsWithoutMutatingSampleOrTank(GameTestHelper h) {
    var n = reader(h);
    var tank = tank(h, n.targetPosition());
    fill(tank, water(1237, 7));
    h.runAfterDelay(5, () -> n.configureReader(ReaderSettings.fluid(water(99, 8))));
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(n.readerReady && n.lastOutput == 0, "different component matched");
          var sample = water(77, 7);
          var settings = ReaderSettings.fluid(sample);
          sample.remove(DataComponents.CUSTOM_DATA);
          n.configureReader(settings);
        });
    h.runAfterDelay(
        35,
        () -> {
          h.assertTrue(
              n.readerReady && n.lastOutput == 1237 && tank.cell().amount() == 1237,
              "sample aliasing/read mutation");
          n.configureReader(ReaderSettings.fluid(FluidStack.EMPTY));
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(n.lastOutput == 1237, "all-fluid total wrong");
          tank.onChunkUnloaded();
        });
    h.runAfterDelay(
        65,
        () -> {
          h.assertTrue(
              !n.readerReady && tank.cell().amount() == 1237, "unloaded tank was valid zero");
          tank.onLoad();
        });
    h.runAfterDelay(
        85,
        () -> {
          h.assertTrue(n.readerReady && n.lastOutput == 1237, "tank reload lost reading");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void readerMenuKeepsFluidComponentsAndRejectsUnauthorizedModeAndGhostEdits(
      GameTestHelper h) {
    var n = reader(h);
    h.runAfterDelay(
        5,
        () -> {
          var p = h.makeMockPlayer(GameType.SURVIVAL);
          p.setPos(n.getBlockPos().getCenter());
          var m = new CircuitMenu(73, p.getInventory(), n);
          p.containerMenu = m;
          h.assertTrue(m.clickMenuButton(p, 2) && m.fluidReader(), "mode not synchronized");
          var canister = new ItemStack(Content.FLUID_CANISTER.get());
          FluidCanisterItem.handler(canister).fill(water(137, 7), FluidAction.EXECUTE);
          m.setCarried(canister);
          m.clicked(0, 0, ClickType.PICKUP, p);
          h.assertTrue(
              n.readerSettings().accepts(water(1, 7)) && !n.readerSettings().accepts(water(1, 8)),
              "fluid selector erased components");
          h.assertTrue(
              FluidCanisterItem.handler(m.getCarried()).getFluid().getAmount() == 137
                  && m.getCarried().getCount() == 1,
              "selector consumed fluid/sample");
          h.assertTrue(
              FluidCanisterItem.handler(m.slots.getFirst().getItem()).getFluid().getAmount() == 1,
              "ghost owns actual source volume");
          p.setPos(n.getBlockPos().getCenter().add(100, 0, 0));
          h.assertTrue(!m.clickMenuButton(p, 2), "distant mode changed");
          m.clicked(0, 1, ClickType.PICKUP, p);
          h.assertTrue(!n.readerSettings().accepts(water(1, 8)), "distant ghost changed");
          p.setPos(n.getBlockPos().getCenter());
          p.containerMenu = p.inventoryMenu;
          h.assertTrue(!m.clickMenuButton(p, 2), "stale menu changed mode");
          p.containerMenu = m;
          m.clickMenuButton(p, 2);
          h.assertTrue(!m.fluidReader(), "item mode did not return");
          m.clickMenuButton(p, 2);
          h.assertTrue(
              m.fluidReader()
                  && n.readerSettings().accepts(water(1, 7))
                  && !n.readerSettings().accepts(water(1, 8)),
              "mode round trip lost exact sample");
          p.containerMenu = m;
          m.setCarried(new ItemStack(Items.DIAMOND));
          m.clicked(0, 0, ClickType.PICKUP, p);
          h.assertTrue(!n.readerSettings().accepts(water(1, 8)), "dry sample cleared filter");
          m.setCarried(ItemStack.EMPTY);
          m.clicked(0, 1, ClickType.PICKUP, p);
          h.assertTrue(n.readerSettings().accepts(water(1, 8)), "explicit any-fluid reset failed");
          var spectator = h.makeMockPlayer(GameType.SPECTATOR);
          spectator.setPos(n.getBlockPos().getCenter());
          spectator.containerMenu = new CircuitMenu(74, spectator.getInventory(), n);
          h.assertTrue(
              !spectator.containerMenu.clickMenuButton(spectator, 2), "spectator changed mode");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void missingFluidHandlerClosesEqualityButRealEmptyTankEnablesIt(GameTestHelper h) {
    var n = reader(h);
    var gate = CircuitGameTests.node(h, FactoryKind.CIRCUIT_CONTROLLER, 9, 4, Direction.SOUTH);
    var armPos = gate.targetPosition();
    h.getLevel()
        .setBlockAndUpdate(
            armPos, Content.FACTORY.get(FactoryKind.FAST_INSERTER).get().defaultBlockState());
    var arm = (InserterBlockEntity) h.getLevel().getBlockEntity(armPos);
    h.runAfterDelay(
        5,
        () -> {
          n.configureReader(ReaderSettings.fluid(FluidStack.EMPTY));
          CircuitGameTests.program(h, gate, 2, 0);
          CircuitGameTests.link(h, n, 0, gate, 0, 0);
        });
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              !n.readerReady && !CircuitBinding.allowed(arm), "missing handler opened equality");
          tank(h, n.targetPosition());
        });
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              n.readerReady && gate.gate && CircuitBinding.allowed(arm),
              "real empty tank not valid zero");
          var t = (FluidBlockEntity) h.getLevel().getBlockEntity(n.targetPosition());
          fill(t, water(20, 7));
        });
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              n.lastOutput == 20 && !CircuitBinding.allowed(arm),
              "fluid threshold did not close gate");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "circuit_sensor_lifecycle", timeoutTicks = 90)
  public static void adjacentTankChunkLossImmediatelyQuarantinesPhysicalSensor(GameTestHelper h) {
    var origin = h.absolutePos(new BlockPos(2, 3, 5));
    var target = new BlockPos((origin.getX() & ~15) + 15, origin.getY(), origin.getZ());
    h.getLevel().getChunkAt(target.east(2));
    StructurePlacement.place(
        h.getLevel(),
        target.west(),
        Content.FACTORY
            .get(FactoryKind.CIRCUIT_READER)
            .get()
            .defaultBlockState()
            .setValue(FactoryBlock.FACING, Direction.EAST));
    var n = (CircuitNodeBlockEntity) h.getLevel().getBlockEntity(target.west());
    var t = tank(h, target);
    fill(t, water(400, 7));
    var gatePos = target.west().north(3);
    StructurePlacement.place(
        h.getLevel(),
        gatePos,
        Content.FACTORY.get(FactoryKind.CIRCUIT_CONTROLLER).get().defaultBlockState());
    var gate = (CircuitNodeBlockEntity) h.getLevel().getBlockEntity(gatePos);
    h.runAfterDelay(
        5,
        () -> {
          n.configureReader(ReaderSettings.fluid(FluidStack.EMPTY));
          CircuitGameTests.link(h, n, 0, gate, 0, 0);
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(CircuitSimulation.inputReady(gate), "initial sensor bus not ready");
          var part =
              t.assembly().positions().stream()
                  .filter(p -> (p.getX() >> 4) != (n.getBlockPos().getX() >> 4))
                  .findFirst()
                  .orElseThrow();
          long c = new net.minecraft.world.level.ChunkPos(part).toLong();
          CircuitSimulation.tickets(
              new net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent(
                  h.getLevel(), c, 31, 33, null));
          h.assertTrue(
              !n.readerReady && !CircuitSimulation.inputReady(gate),
              "remote tank part ticket demotion left bus open");
          CircuitSimulation.tickets(
              new net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent(
                  h.getLevel(), c, 33, 31, null));
        });
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(n.readerReady && n.lastOutput == 400, "sensor failed to recover");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void malformedFluidSampleIsUnknownAndLegacyDefaultsOmitReaderNbt(GameTestHelper h) {
    var n = reader(h);
    tank(h, n.targetPosition());
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(
              !n.saveWithoutMetadata(h.getLevel().registryAccess())
                  .getCompound("Circuit")
                  .contains("Reader"),
              "default reader changed old format");
          var bad = new CompoundTag();
          bad.putInt("Mode", 1);
          bad.put("Filter", new CompoundTag());
          n.configureReader(ReaderSettings.load(bad, h.getLevel().registryAccess()));
        });
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(!n.readerReady, "malformed exact sample became all fluids");
          n.configureReader(ReaderSettings.fluid(FluidStack.EMPTY));
        });
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(n.readerReady && n.lastOutput == 0, "explicit reset did not recover");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 500)
  public static void finiteCoalPoweredTankFeedbackStopsPumpAndConservesEveryMillibucket(
      GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    FluidCircuitFactory.build(h.getLevel(), o);
    h.runAfterDelay(
        280,
        () -> {
          var source = (FluidBlockEntity) h.getLevel().getBlockEntity(o.offset(2, 0, 5));
          var target = (FluidBlockEntity) h.getLevel().getBlockEntity(o.offset(9, 0, 5));
          var pump = (FluidBlockEntity) h.getLevel().getBlockEntity(o.offset(8, 0, 5));
          var reader = (CircuitNodeBlockEntity) h.getLevel().getBlockEntity(o.offset(12, 0, 6));
          var gate = (CircuitNodeBlockEntity) h.getLevel().getBlockEntity(o.offset(8, 0, 4));
          int total = source.cell().amount() + target.cell().amount() + pump.cell().amount();
          for (int x = 5; x <= 6; x++)
            total +=
                ((FluidBlockEntity) h.getLevel().getBlockEntity(o.offset(x, 0, 5))).cell().amount();
          h.assertTrue(
              target.cell().amount() >= 1000
                  && target.cell().amount() < 1100
                  && reader.lastOutput == target.cell().amount()
                  && !gate.gate
                  && !CircuitBinding.allowed(pump),
              "feedback failed: target=" + target.cell().amount() + " read=" + reader.lastOutput);
          h.assertTrue(
              total == 4000 && pump.power.getEnergyStored() > 0,
              "finite volume lost or no real grid power");
          System.out.println(
              "FACTORYCRAFT_FLUID_CIRCUIT_FACTORY: VERIFIED target="
                  + target.cell().amount()
                  + " mB / 4000 mB total coal-powered");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void portableReaderRetainsExactSampleButNotOldPhysicalWires(GameTestHelper h) {
    var n = reader(h);
    var source = CircuitGameTests.node(h, FactoryKind.CONSTANT, 8, 5, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          n.configureReader(ReaderSettings.fluid(water(137, 7)));
          CircuitGameTests.link(h, source, 0, n, 0, 1);
          var identity = n.assembly().identity();
          h.getLevel().destroyBlock(n.getBlockPos(), true);
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new net.minecraft.world.phys.AABB(n.getBlockPos()).inflate(3));
          h.assertTrue(drops.size() == 1, "reader demolition duplicated owner");
          var stack = drops.getFirst().getItem().copy();
          drops.getFirst().discard();
          var p = h.makeMockPlayer(GameType.SURVIVAL);
          p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
          var position = h.absolutePos(new BlockPos(14, 3, 5));
          var ctx =
              new net.minecraft.world.item.context.UseOnContext(
                  p,
                  net.minecraft.world.InteractionHand.MAIN_HAND,
                  new net.minecraft.world.phys.BlockHitResult(
                      position.getCenter(), Direction.UP, position, false));
          var placement = new net.minecraft.world.item.context.BlockPlaceContext(ctx);
          var state =
              Content.FACTORY.get(FactoryKind.CIRCUIT_READER).get().getStateForPlacement(placement);
          var plan = factorycraft.multiblock.PlacementPlan.inspect(placement, state);
          h.assertTrue(stack.useOn(ctx).consumesAction(), "portable reader placement failed");
          var copy = (CircuitNodeBlockEntity) h.getLevel().getBlockEntity(plan.anchor());
          h.assertTrue(
              copy.readerSettings().fluids()
                  && copy.readerSettings().accepts(water(1, 7))
                  && !copy.readerSettings().accepts(water(1, 8))
                  && copy.links.isEmpty()
                  && source.links.isEmpty()
                  && !copy.assembly().identity().equals(identity),
              "portable sample/identity/links changed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void combinatorPartUnloadImmediatelyPausesWithoutInventoryLookup(GameTestHelper h) {
    var a = CircuitGameTests.node(h, FactoryKind.CONSTANT, 4, 5, Direction.NORTH);
    var b = CircuitGameTests.node(h, FactoryKind.ARITHMETIC, 8, 5, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          CircuitGameTests.program(h, a, 0, 7);
          CircuitGameTests.program(h, b, 0, 3);
          CircuitGameTests.link(h, a, 0, b, 0, 0);
        });
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(b.active && b.lastOutput == 10, "initial combinator inactive");
          var part = (CasingBlockEntity) h.getLevel().getBlockEntity(b.portPosition(0));
          part.onChunkUnloaded();
          h.assertTrue(
              !b.active && !CircuitSimulation.inputReady(b),
              "part unload did not invalidate circuit topology immediately");
        });
    h.runAfterDelay(
        30, () -> ((CasingBlockEntity) h.getLevel().getBlockEntity(b.portPosition(0))).onLoad());
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(
              b.active && b.lastOutput == 10 && CircuitSimulation.inputReady(b),
              "part reload did not restore circuit");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void nativeFluidSampleAndCrossChunkTankRetainExactDiskState(GameTestHelper h) {
    var level = h.getLevel();
    var p = new BlockPos(16015, 70, 16015);
    var r = p.west();
    var profile = factorycraft.multiblock.MachineProfile.TANK;
    var cells = new ArrayList<>(profile.cells(p, Direction.NORTH));
    cells.add(r);
    for (var cell : cells) level.getChunkAt(cell);
    boolean fresh = cells.stream().allMatch(c -> level.getBlockState(c).isAir());
    if (fresh) {
      var tank = tank(h, p);
      fill(tank, water(1237, 7));
      StructurePlacement.place(
          level,
          r,
          Content.FACTORY
              .get(FactoryKind.CIRCUIT_READER)
              .get()
              .defaultBlockState()
              .setValue(FactoryBlock.FACING, Direction.EAST));
      var n = (CircuitNodeBlockEntity) level.getBlockEntity(r);
      var tag = n.saveWithoutMetadata(level.registryAccess());
      tag.getCompound("Circuit")
          .put("Reader", ReaderSettings.fluid(water(1, 7)).save(level.registryAccess()));
      tag.getCompound("Circuit").putInt("Output", 1237);
      n.loadWithComponents(tag, level.registryAccess());
      n.setChanged();
    }
    h.assertTrue(
        level.getBlockEntity(p) instanceof FluidBlockEntity
            && level.getBlockEntity(r) instanceof CircuitNodeBlockEntity,
        "partial sensor fixture; refusing refill");
    var tank = (FluidBlockEntity) level.getBlockEntity(p);
    var n = (CircuitNodeBlockEntity) level.getBlockEntity(r);
    h.assertTrue(
        tank.cell().amount() == 1237
            && tank.stored().get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 7
            && n.lastOutput == 1237
            && n.readerSettings().fluids()
            && n.readerSettings().accepts(water(1, 7))
            && !n.readerSettings().accepts(water(1, 8)),
        "sensor/fluid disk values changed");
    for (var part : tank.assembly().positions())
      h.assertTrue(
          level.getBlockEntity(part) instanceof CasingBlockEntity c
              && c.boundTo(p, tank.assembly().identity()),
          "part identity changed");
    tank.onChunkUnloaded();
    n.onChunkUnloaded();
    for (var cell : cells) level.getChunkAt(cell).setUnsaved(true);
    System.out.println(
        "FACTORYCRAFT_FLUID_SENSOR_DISK: "
            + (fresh ? "SEEDED" : "VERIFIED")
            + " component grade=7 filter, 1237 mB, four-chunk tank");
    h.succeed();
  }
}
