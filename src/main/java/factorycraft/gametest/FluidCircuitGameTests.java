package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.circuits.*;
import factorycraft.diagnostics.*;
import factorycraft.fluids.*;
import factorycraft.items.FluidCanisterItem;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.simulation.circuits.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

/** Former remote reader scenarios now exercise physical direct sensors and controls. */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class FluidCircuitGameTests {
  private static final SignalKey WATER = SignalKey.fluid("minecraft:water");

  private static FluidStack water(int amount, int grade) {
    var f = new FluidStack(Fluids.WATER, amount);
    var t = new CompoundTag();
    t.putInt("grade", grade);
    f.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    return f;
  }

  private static FluidBlockEntity tank(GameTestHelper h, int x) {
    return FluidCircuitFactory.fluid(
        h.getLevel(), h.absolutePos(new BlockPos(x, 3, 7)), FluidKind.TANK);
  }

  private static InserterBlockEntity arm(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(11, 3, 7));
    h.getLevel()
        .setBlockAndUpdate(
            p, Content.FACTORY.get(FactoryKind.FAST_INSERTER).get().defaultBlockState());
    return (InserterBlockEntity) h.getLevel().getBlockEntity(p);
  }

  private static CircuitAttachment sensor(FluidBlockEntity tank) {
    return DirectGateFixture.sensor(tank, 1);
  }

  private static void fill(FluidBlockEntity t, FluidStack f) {
    t.cell().fill(new FluidKey(f), f.getAmount(), false);
    t.setChanged();
  }

  private static void wireGate(FluidBlockEntity t, InserterBlockEntity arm) {
    DirectGateFixture.wire(
        sensor(t), DirectGateFixture.gate(arm, WATER, SignalMath.Compare.EQUAL, 0, 1), 0);
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void fluidReaderMatchesComponentsWithoutMutatingSampleOrTank(GameTestHelper h) {
    var t = tank(h, 5);
    fill(t, water(1237, 7));
    var n = sensor(t);
    var sample = water(77, 8);
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              n.sensorReady && n.output.get(WATER) == 1237 && t.cell().amount() == 1237,
              "direct observation mutated tank/mass");
          h.assertTrue(
              !new FluidKey(sample).equals(t.cell().key())
                  && t.cell().fill(new FluidKey(sample), 77, true) == 0,
              "components mixed");
          sample.remove(DataComponents.CUSTOM_DATA);
          h.assertTrue(
              t.stored().get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 7,
              "sample alias changed owner");
          t.onChunkUnloaded();
          h.assertTrue(!n.available(), "unloaded tank was valid zero");
          t.onLoad();
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(n.sensorReady && n.output.get(WATER) == 1237, "sensor reload lost reading");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void readerMenuKeepsFluidComponentsAndRejectsUnauthorizedModeAndGhostEdits(
      GameTestHelper h) {
    var t = tank(h, 5);
    var n = sensor(t);
    h.runAfterDelay(
        5,
        () -> {
          var p = h.makeMockPlayer(GameType.SURVIVAL);
          p.setPos(t.getBlockPos().getCenter());
          var m = new LogicMenu(73, p.getInventory(), n, t.getBlockPos());
          p.containerMenu = m;
          var canister = new ItemStack(Content.FLUID_CANISTER.get());
          FluidCanisterItem.handler(canister).fill(water(137, 7), FluidAction.EXECUTE);
          m.setCarried(canister);
          var config = new CompoundTag();
          config.put("Settings", n.settings().save());
          config.put("Program", SignalProgramData.save(n.program()));
          h.assertTrue(m.apply(p, n.identity(), config), "direct menu apply rejected");
          h.assertTrue(
              FluidCanisterItem.handler(m.getCarried()).getFluid().getAmount() == 137,
              "read-only setup consumed sample");
          p.setPos(p.position().add(100, 0, 0));
          h.assertTrue(!m.apply(p, n.identity(), config), "distant edit accepted");
          p.setPos(t.getBlockPos().getCenter());
          p.containerMenu = p.inventoryMenu;
          h.assertTrue(!m.apply(p, n.identity(), config), "stale edit accepted");
          var spectator = h.makeMockPlayer(GameType.SPECTATOR);
          spectator.setPos(t.getBlockPos().getCenter());
          spectator.containerMenu = new LogicMenu(74, spectator.getInventory(), n, t.getBlockPos());
          h.assertTrue(
              !((LogicMenu) spectator.containerMenu).apply(spectator, n.identity(), config),
              "spectator edited sensor");
          h.assertTrue(
              FluidStack.matches(
                  FluidCanisterItem.handler(m.getCarried()).getFluid(), water(137, 7)),
              "menu lost sample components");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void missingFluidHandlerClosesEqualityButRealEmptyTankEnablesIt(GameTestHelper h) {
    var t = tank(h, 5);
    var a = arm(h);
    wireGate(t, a);
    h.runAfterDelay(5, t::onChunkUnloaded);
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(!CircuitBinding.allowed(a), "unknown fluid owner became valid zero");
          t.onLoad();
        });
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(CircuitBinding.allowed(a), "actual empty tank failed equality");
          fill(t, water(20, 7));
        });
    h.runAfterDelay(
        70,
        () -> {
          h.assertTrue(
              !CircuitBinding.allowed(a) && t.cell().amount() == 20,
              "nonzero fluid did not close direct gate");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void adjacentTankChunkLossImmediatelyQuarantinesPhysicalSensor(GameTestHelper h) {
    var t = tank(h, 5);
    var a = arm(h);
    wireGate(t, a);
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(CircuitBinding.allowed(a), "initial bus not ready");
          var part =
              (CasingBlockEntity) h.getLevel().getBlockEntity(t.assembly().positions().getLast());
          part.onChunkUnloaded();
          h.assertTrue(
              !CircuitBinding.allowed(a),
              "part unload did not quarantine direct sensor immediately");
          part.onLoad();
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(CircuitBinding.allowed(a), "part reload failed to recover bus");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void malformedFluidSampleIsUnknownAndLegacyDefaultsOmitReaderNbt(GameTestHelper h) {
    var t = tank(h, 5);
    var a = arm(h);
    wireGate(t, a);
    h.runAfterDelay(
        10,
        () -> {
          var n = t.circuit(false);
          var raw = n.save();
          raw.putInt("Schema", 999);
          n.load(raw);
          CircuitSimulation.programChanged(n);
          h.assertTrue(!CircuitBinding.allowed(a), "malformed direct sensor failed open");
          var c = CircuitGameTests.node(h, FactoryKind.CONSTANT, 16, 7, Direction.NORTH);
          h.assertTrue(
              !c.saveWithoutMetadata(h.getLevel().registryAccess())
                  .getCompound("Circuit")
                  .contains("Reader"),
              "retired reader NBT leaked into current combinator");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 700)
  public static void finiteCoalPoweredTankFeedbackStopsPumpAndConservesEveryMillibucket(
      GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    FluidCircuitFactory.build(h.getLevel(), o);
    var source = (FluidBlockEntity) h.getLevel().getBlockEntity(o.offset(3, 0, 5));
    var pump = (FluidBlockEntity) h.getLevel().getBlockEntity(o.offset(8, 0, 6));
    var target = (FluidBlockEntity) h.getLevel().getBlockEntity(o.offset(11, 0, 7));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              !CircuitBinding.allowed(pump) && FluidSimulation.view(target).amount() >= 1000,
              "finite direct feedback has not stopped");
          long total = source.cell().amount() + pump.cell().amount() + target.cell().amount();
          for (var p : List.of(o.offset(5, 0, 6), o.offset(6, 0, 6), o.offset(9, 0, 6)))
            total += ((FluidBlockEntity) h.getLevel().getBlockEntity(p)).cell().amount();
          h.assertTrue(total == 4000, "feedback lost/duplicated finite water");
          h.assertTrue(
              pump.power.getEnergyStored() > 0, "finite steam grid did not actually power pump");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void portableReaderRetainsExactSampleButNotOldPhysicalWires(GameTestHelper h) {
    var t = tank(h, 5);
    fill(t, water(731, 7));
    var n = sensor(t);
    var c = CircuitGameTests.node(h, FactoryKind.CONSTANT, 11, 12, Direction.NORTH);
    DirectGateFixture.wire(n, c.circuit(true), 2);
    h.runAfterDelay(
        5,
        () -> {
          var before = n.identity();
          h.getLevel().destroyBlock(t.assembly().positions().getLast(), true);
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new net.minecraft.world.phys.AABB(t.getBlockPos()).inflate(4));
          h.assertTrue(drops.size() == 1, "portable tank duplicated source");
          var p = h.makeMockPlayer(GameType.SURVIVAL);
          p.setItemInHand(InteractionHand.MAIN_HAND, drops.getFirst().getItem().copy());
          drops.getFirst().discard();
          var at = h.absolutePos(new BlockPos(15, 3, 7));
          h.assertTrue(
              PlacementTestSupport.place(p, at).consumesAction(),
              "portable sensor tank placement rejected");
          var copy = (FluidBlockEntity) h.getLevel().getBlockEntity(at);
          h.assertTrue(
              FluidStack.matches(copy.stored(), water(731, 7))
                  && copy.circuit(false).links.isEmpty()
                  && !copy.circuit(false).identity().equals(before)
                  && copy.circuit(false).settings().contents(),
              "portable direct sensor copied old physical wire or lost content/settings");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void combinatorPartUnloadImmediatelyPausesWithoutInventoryLookup(GameTestHelper h) {
    var t = tank(h, 5);
    var a = arm(h);
    var n = sensor(t);
    var b =
        CircuitFactory.node(
            h.getLevel(),
            h.absolutePos(new BlockPos(8, 3, 11)),
            FactoryKind.ARITHMETIC,
            SignalProgram.defaults(SignalProgram.Kind.ARITHMETIC),
            1,
            1);
    DirectGateFixture.wire(n, b.circuit(true), 0);
    DirectGateFixture.wire(
        b.circuit(true), DirectGateFixture.gate(a, SignalKey.A, SignalMath.Compare.EQUAL, 0, 1), 0);
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(CircuitBinding.allowed(a), "initial combinator bus incomplete");
          var part =
              (CasingBlockEntity) h.getLevel().getBlockEntity(b.assembly().positions().getFirst());
          part.onChunkUnloaded();
          h.assertTrue(!CircuitBinding.allowed(a), "unloaded input contact left gate live");
          part.onLoad();
        });
    h.runAfterDelay(
        55,
        () -> {
          h.assertTrue(CircuitBinding.allowed(a), "physical contact recovery failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void nativeFluidSampleAndCrossChunkTankRetainExactDiskState(GameTestHelper h) {
    var level = h.getLevel();
    var p = new BlockPos(18159, 70, 18159);
    var c = p.east(6);
    var cells = new ArrayList<>(MachineProfile.TANK.cells(p, Direction.NORTH));
    cells.add(c);
    for (var pos : cells) level.getChunkAt(pos);
    boolean fresh = cells.stream().allMatch(pos -> level.getBlockState(pos).isAir());
    if (fresh) {
      StructurePlacement.place(
          level, p, Content.FLUID_BLOCKS.get(FluidKind.TANK).get().defaultBlockState());
      StructurePlacement.place(
          level, c, Content.FACTORY.get(FactoryKind.CONSTANT).get().defaultBlockState());
      var t = (FluidBlockEntity) level.getBlockEntity(p);
      t.localCell().restore(new FluidKey(water(731, 7)), 731);
      var n = sensor(t);
      var peer = ((CircuitNodeBlockEntity) level.getBlockEntity(c)).circuit(true);
      for (int color = 0; color < 4; color++) DirectGateFixture.wire(n, peer, color);
    }
    h.assertTrue(
        level.getBlockEntity(p) instanceof FluidBlockEntity
            && level.getBlockEntity(c) instanceof CircuitNodeBlockEntity,
        "partial native sensor disk checkpoint; refusing refill");
    var t = (FluidBlockEntity) level.getBlockEntity(p);
    var n = t.circuit(false);
    var peer = ((CircuitNodeBlockEntity) level.getBlockEntity(c)).circuit(true);
    h.assertTrue(
        FluidStack.matches(t.localCell().key().stack(t.localCell().amount()), water(731, 7))
            && n.settings().contents()
            && n.links.size() == 4,
        "native components/direct settings changed");
    for (int color = 0; color < 4; color++)
      h.assertTrue(
          n.links.contains(new CircuitLink(c, peer.identity(), 0, 0, color))
              && peer.links.contains(new CircuitLink(p, n.identity(), 0, 0, color)),
          "disk direct color/owner UUID changed");
    for (var pos : cells) {
      level.getBlockEntity(pos).onChunkUnloaded();
      level.getChunkAt(pos).setUnsaved(true);
    }
    System.out.println(
        "FACTORYCRAFT_FLUID_CIRCUIT_DISK: "
            + (fresh ? "SEEDED" : "VERIFIED")
            + " direct four-color native 731 mB, exact components and owner");
    h.succeed();
  }
}
