package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.core.FactoryCommands;
import factorycraft.diagnostics.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.simulation.fluids.FluidUnits;
import factorycraft.thermal.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

/** New-format acceptance. Initial stocks are raw inputs; no finished products are injected. */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class ReleaseV26GameTests {
  private static ResourceLocation id(String s) {
    return ResourceLocation.fromNamespaceAndPath("factorycraft", s);
  }

  private static Item item(String s) {
    return BuiltInRegistries.ITEM.get(id(s));
  }

  private static void load(AssemblyOwner o) {
    var b = o.factoryEntity();
    for (var p :
        o.assembly()
            .profile()
            .cells(b.getBlockPos(), o.assembly().profile().facing(b.getBlockState())))
      b.getLevel().getBlockEntity(p).onLoad();
  }

  private static MachineBlockEntity machine(GameTestHelper h, FactoryKind k, int x, int z) {
    var p = h.absolutePos(new BlockPos(x, 3, z));
    StructurePlacement.place(h.getLevel(), p, Content.FACTORY.get(k).get().defaultBlockState());
    var m = (MachineBlockEntity) h.getLevel().getBlockEntity(p);
    load(m);
    return m;
  }

  private static FluidBlockEntity fluid(GameTestHelper h, FluidKind k, int x, int z, Direction f) {
    var p = h.absolutePos(new BlockPos(x, 3, z));
    var state = Content.FLUID_BLOCKS.get(k).get().defaultBlockState();
    if (state.hasProperty(FluidDeviceBlock.FACING))
      state = state.setValue(FluidDeviceBlock.FACING, f);
    StructurePlacement.place(h.getLevel(), p, state);
    var b = (FluidBlockEntity) h.getLevel().getBlockEntity(p);
    if (b.assembly() != null) load(b);
    else b.onLoad();
    return b;
  }

  private static void ticks(MachineBlockEntity m, int n) {
    for (int i = 0; i < n; i++) m.tick();
  }

  private static int products(MachineBlockEntity m) {
    int n = 0;
    for (int i = 4; i < 8; i++) n += m.items.getStackInSlot(i).getCount();
    return n;
  }

  private static ResourceDepositBlockEntity ore(GameTestHelper h, BlockPos p, int amount) {
    h.getLevel().setBlockAndUpdate(p, Content.IRON_ORE.get().defaultBlockState());
    var b = (ResourceDepositBlockEntity) h.getLevel().getBlockEntity(p);
    b.initializeReserve(amount);
    b.onLoad();
    return b;
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 120)
  public static void electricPumpNeverCreatesWaterFromWorldBlocks(GameTestHelper h) {
    var p = fluid(h, FluidKind.PUMP, 7, 7, Direction.EAST);
    fluid(h, FluidKind.PIPE, 8, 7, Direction.NORTH);
    p.power.restore(2000);
    var at = p.inputNozzle().west();
    var states =
        List.of(
            Blocks.WATER.defaultBlockState(),
            Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3),
            Blocks.LAVA.defaultBlockState(),
            Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.WATERLOGGED, true));
    for (int i = 0; i < states.size(); i++) {
      var state = states.get(i);
      h.runAfterDelay(5 + i * 20, () -> h.getLevel().setBlockAndUpdate(at, state));
      h.runAfterDelay(
          20 + i * 20,
          () ->
              h.assertTrue(
                  p.cell().amount() == 0 && p.power.getEnergyStored() == 2000,
                  "ordinary Pump generated fluid/paid dry work from " + state));
    }
    h.runAfterDelay(100, h::succeed);
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 100)
  public static void offshoreHasTwoRealIntakesAndStopsWhenShoreDisappears(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(7, 3, 7));
    ShoreFixture.prepare(h.getLevel(), p, Direction.EAST);
    var b = fluid(h, FluidKind.OFFSHORE, 7, 7, Direction.EAST);
    h.assertTrue(
        b.assembly().profile().layout.width() == 2 && b.assembly().profile().layout.depth() == 1,
        "wrong offshore footprint");
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              b.waterGenerated() == 4000
                  && b.cell().amount() == 4000
                  && b.power.getEnergyStored() == 0,
              "no-FE source did not stop at buffer space");
          var cap = b.structurePort(b.outputNozzle(), Direction.EAST);
          int n = FluidSimulation.manual(() -> cap.drain(4000, FluidAction.EXECUTE).getAmount());
          h.assertTrue(n == 4000, "source output inaccessible");
          var water = MachineProfile.OFFSHORE.offset(p, Direction.EAST, 0, 0, 0).below().west();
          h.getLevel().setBlockAndUpdate(water, Blocks.STONE.defaultBlockState());
        });
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(
              b.cell().amount() == 0 && b.waterGenerated() == 4000,
              "invalid shore generated water");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 100)
  public static void offshorePlacementRejectsFloatingAndDiagonalSourcesInEveryFacing(
      GameTestHelper h) {
    int n = 0;
    for (var f : Direction.Plane.HORIZONTAL) {
      var p = h.absolutePos(new BlockPos(5 + (n % 2) * 12, 3, 5 + (n++ / 2) * 12));
      h.assertTrue(
          !OffshorePlacement.invalid(h.getLevel(), p, f).isEmpty(), "floating source accepted");
      ShoreFixture.prepare(h.getLevel(), p, f);
      h.assertTrue(
          OffshorePlacement.invalid(h.getLevel(), p, f).isEmpty(), "valid rotated shore rejected");
      var water = MachineProfile.OFFSHORE.offset(p, f, 1, 0, 0).below().relative(f.getOpposite());
      h.getLevel().setBlockAndUpdate(water, Blocks.AIR.defaultBlockState());
      h.assertTrue(
          !OffshorePlacement.invalid(h.getLevel(), p, f).isEmpty(),
          "one/diagonal intake bypassed geometry");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 100)
  public static void assemblerTiersKeepExactPaidQuarterSpeedsAndOwnSlotCounts(GameTestHelper h) {
    int i = 0;
    for (var kind :
        List.of(FactoryKind.ASSEMBLER, FactoryKind.ADVANCED_ASSEMBLER, FactoryKind.ASSEMBLER_3)) {
      var m = machine(h, kind, 5 + 8 * i++, 8);
      m.items.setStackInSlot(0, new ItemStack(item("iron_plate"), 12));
      m.power.restore(16000);
      h.assertTrue(m.selectRecipe(id("iron_gear")), "gear selection missing");
      ticks(m, 80);
      int expected = kind == FactoryKind.ASSEMBLER ? 4 : 6;
      h.assertTrue(
          products(m) == expected
              && m.power.getEnergyStored()
                  == 16000 - (kind == FactoryKind.ASSEMBLER_3 ? 48 : 80) * kind.activeFE(),
          "tier speed/FE mismatch "
              + kind
              + ": "
              + products(m)
              + " / "
              + m.power.getEnergyStored());
      h.assertTrue(
          m.items.getSlots() == 21 && m.modules.items.getSlots() == kind.moduleSlots(),
          "physical module/input inventory mismatch");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 100)
  public static void assemblerReservesMoreThanAStackAcrossActualInputSlots(GameTestHelper h) {
    var m = machine(h, FactoryKind.ASSEMBLER, 6, 8);
    var r =
        new factorycraft.recipes.FactoryRecipe(
            "assembling_machine",
            List.of(
                new factorycraft.recipes.FactoryRecipe.CountedIngredient(
                    Ingredient.of(Items.IRON_INGOT), 100)),
            new ItemStack(Items.DIAMOND),
            20,
            10);
    m.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
    m.items.setStackInSlot(9, new ItemStack(Items.IRON_INGOT, 36));
    var plan = r.consumptionPlan(new factorycraft.recipes.FactoryInput(m.items));
    h.assertTrue(
        plan != null && plan[0] == 64 && plan[4] == 36,
        "large counted ingredient not backed by physical slots");
    executeTemporary(h, m, r, "fixture_large", 40);
    h.assertTrue(
        products(m) == 1
            && m.items.getStackInSlot(0).isEmpty()
            && m.items.getStackInSlot(9).isEmpty(),
        "large recipe did not commit real counts");
    m.items.setStackInSlot(4, ItemStack.EMPTY);

    var inputs = new ArrayList<factorycraft.recipes.FactoryRecipe.CountedIngredient>();
    var alternatives =
        List.of(
            Items.COAL,
            Items.COPPER_INGOT,
            Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.REDSTONE,
            Items.QUARTZ,
            Items.DIAMOND,
            Items.EMERALD,
            Items.LAPIS_LAZULI,
            Items.AMETHYST_SHARD,
            Items.FLINT,
            Items.STICK,
            Items.PAPER,
            Items.BRICK,
            Items.CLAY_BALL,
            Items.STRING);
    for (int i = 0; i < 16; i++) {
      var item = alternatives.get(i);
      inputs.add(new factorycraft.recipes.FactoryRecipe.CountedIngredient(Ingredient.of(item), 1));
      m.items.setStackInSlot(factorycraft.recipes.FactoryInput.realSlot(i), new ItemStack(item));
    }
    var all =
        new factorycraft.recipes.FactoryRecipe(
            "assembling_machine", inputs, new ItemStack(Items.DIAMOND), 20, 10);
    h.assertTrue(
        all.matches(new factorycraft.recipes.FactoryInput(m.items), h.getLevel()),
        "sixteen real ingredient types are uncraftable");
    m.items.setStackInSlot(20, ItemStack.EMPTY);
    h.assertTrue(
        !all.matches(new factorycraft.recipes.FactoryInput(m.items), h.getLevel()),
        "last ingredient was ignored");
    m.items.setStackInSlot(20, new ItemStack(Items.STRING));
    executeTemporary(h, m, all, "fixture_sixteen", 40);
    h.assertTrue(
        products(m) == 1
            && java.util.stream.IntStream.range(0, 16)
                .allMatch(
                    i ->
                        m.items
                            .getStackInSlot(factorycraft.recipes.FactoryInput.realSlot(i))
                            .isEmpty()),
        "sixteen-input recipe did not commit every slot exactly once");
    int tierX = 15;
    for (var kind : List.of(FactoryKind.ADVANCED_ASSEMBLER, FactoryKind.ASSEMBLER_3)) {
      var advanced = machine(h, kind, tierX, 8);
      tierX += 8;
      for (int i = 0; i < 16; i++)
        advanced.items.setStackInSlot(
            factorycraft.recipes.FactoryInput.realSlot(i), new ItemStack(alternatives.get(i)));
      executeTemporary(h, advanced, all, "fixture_sixteen_" + kind.id, 40);
      h.assertTrue(
          products(advanced) == 1
              && java.util.stream.IntStream.range(0, 16)
                  .allMatch(
                      i ->
                          advanced
                              .items
                              .getStackInSlot(factorycraft.recipes.FactoryInput.realSlot(i))
                              .isEmpty()),
          "mixed-production tier ignored physical inputs: " + kind);
    }
    h.succeed();
  }

  private static void executeTemporary(
      GameTestHelper h,
      MachineBlockEntity m,
      factorycraft.recipes.FactoryRecipe recipe,
      String name,
      int ticks) {
    var manager = h.getLevel().getRecipeManager();
    var saved = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
    var extended = new ArrayList<>(saved);
    extended.add(new RecipeHolder<>(id(name), recipe));
    try {
      manager.replaceRecipes(extended);
      factorycraft.recipes.RecipeSelectionNetwork.generation++;
      m.power.restore(16000);
      h.assertTrue(m.selectRecipe(id(name)), "temporary recipe selection failed");
      ticks(m, ticks);
    } finally {
      manager.replaceRecipes(saved);
      factorycraft.recipes.RecipeSelectionNetwork.generation++;
    }
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 100)
  public static void burnerAndElectricMinersPayExactFuelAndFiniteOre(GameTestHelper h) {
    int x = 5;
    for (var kind : List.of(FactoryKind.BURNER_DRILL, FactoryKind.DRILL)) {
      var m = machine(h, kind, x, 8);
      x += 10;
      var at = m.assembly().profile().center(m.getBlockPos(), Direction.NORTH).below();
      var stock = ore(h, at, 10);
      if (kind.burner()) m.items.setStackInSlot(8, new ItemStack(Items.COAL));
      else m.power.restore(2000);
      ticks(m, 80);
      h.assertTrue(
          products(m) == (kind.burner() ? 1 : 2) && stock.remaining() == (kind.burner() ? 9 : 8),
          "wrong finite mining speed " + kind);
      h.assertTrue(
          kind.burner()
              ? m.saveWithoutMetadata(h.getLevel().registryAccess()).getLong("MiningFuel") == 4400
              : m.power.getEnergyStored() == 1040,
          "wrong mining fuel/FE ledger");
      h.assertTrue(m.modules.items.getSlots() == (kind.burner() ? 0 : 3), "mining module slots");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 100)
  public static void miningPausesOnUnloadedTileAndResumesPaidFraction(GameTestHelper h) {
    var pos = PowerV20GameTests.site(h, 45);
    for (var cell : MachineProfile.DRILL.cells(pos, Direction.NORTH)) h.getLevel().getChunkAt(cell);
    h.getLevel().getChunkAt(pos.below(8));
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(FactoryKind.DRILL).get().defaultBlockState());
    var m = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
    load(m);
    var stock =
        ore(h, m.assembly().profile().center(m.getBlockPos(), Direction.NORTH).below(8), 10);
    m.power.restore(2000);
    ticks(m, 13);
    var tag = m.saveWithoutMetadata(h.getLevel().registryAccess());
    h.assertTrue(
        tag.getInt("Progress") == 6 && tag.getInt("MiningPhase") == 20000,
        "half-work residue missing");
    stock.onChunkUnloaded();
    ticks(m, 20);
    h.assertTrue(m.power.getEnergyStored() == 1844, "unloaded ore charged FE");
    stock.onLoad();
    var copy =
        (MachineBlockEntity)
            BlockEntity.loadStatic(
                m.getBlockPos(),
                m.getBlockState(),
                m.saveWithFullMetadata(h.getLevel().registryAccess()),
                h.getLevel().registryAccess());
    m.setRemoved();
    h.getLevel().setBlockEntity(copy);
    copy.onLoad();
    ticks(copy, 27);
    h.assertTrue(
        products(copy) == 1 && stock.remaining() == 9 && copy.power.getEnergyStored() == 1520,
        "paid mining fraction lost/duplicated after reconstruction");
    // A legitimate mid-cycle credit can exceed one bonus before that ore cycle commits.
    for (int slot = 0; slot < 3; slot++)
      copy.modules.items.setStackInSlot(slot, new ItemStack(Content.PRODUCTIVITY_MODULE.get()));
    copy.power.restore(16000);
    MachineBlockEntity current = copy;
    boolean reconstructed = false;
    for (int tick = 0; tick < 1000 && stock.remaining() > 0; tick++) {
      current.tick();
      var state = current.saveWithFullMetadata(h.getLevel().registryAccess());
      if (!reconstructed && state.getInt("MiningCredit") >= 10000) {
        var replacement =
            (MachineBlockEntity)
                BlockEntity.loadStatic(
                    current.getBlockPos(),
                    current.getBlockState(),
                    state,
                    h.getLevel().registryAccess());
        current.setRemoved();
        h.getLevel().setBlockEntity(replacement);
        replacement.onLoad();
        current = replacement;
        reconstructed = true;
      }
    }
    h.assertTrue(
        reconstructed && stock.remaining() == 0 && products(current) == 11,
        "mining bonus was duplicated/lost at mid-cycle reload");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 100)
  public static void boilerAndTwoEnginesHaveExactNominalSteamHeatLedger(GameTestHelper h) {
    var boiler = new factorycraft.simulation.thermal.BoilerProcess();
    var water = new factorycraft.simulation.fluids.FluidCell<String>(1000, () -> {});
    var steam = new factorycraft.simulation.fluids.FluidCell<String>(1000, () -> {});
    var a = new factorycraft.simulation.thermal.SteamEngine<>(steam, () -> {});
    var b = new factorycraft.simulation.thermal.SteamEngine<>(steam, () -> {});
    water.restore("water", 240);
    boiler.refuel(1600);
    int made = 0;
    for (int i = 0; i < 80; i++) {
      h.assertTrue(
          boiler.advance("canonical", 1, water, 3, steam, "steam", 30, 20), "valid boiler stopped");
      made += a.generate(true) + b.generate(true);
      a.extract(120, false);
      b.extract(120, false);
    }
    h.assertTrue(
        water.amount() == 0
            && steam.amount() == 0
            && boiler.fuel() == 0
            && made == 19200
            && FluidUnits.PUMP_PER_TICK / 3 == 200,
        "1:2 / 1:200 / heat ledger mismatch");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 100)
  public static void solidFuelHasThreeNativeMbRecipesAndThreeCoalHeat(GameTestHelper h) {
    var all = h.getLevel().getRecipeManager().getAllRecipesFor(Content.CHEMISTRY.get());
    for (var e : Map.of("petroleum_gas", 200, "light_oil", 100, "heavy_oil", 200).entrySet()) {
      var r =
          all.stream()
              .filter(v -> v.id().equals(id("solid_fuel_from_" + e.getKey())))
              .findFirst()
              .orElseThrow()
              .value();
      h.assertTrue(
          r.fluids().getFirst().amount() == e.getValue()
              && r.ticks() == 20
              && r.results().getFirst().is(Content.SOLID_FUEL.get()),
          "solid fuel recipe wrong");
    }
    h.assertTrue(
        new ItemStack(Content.SOLID_FUEL.get()).getBurnTime(RecipeType.SMELTING)
            == 3 * new ItemStack(Items.COAL).getBurnTime(RecipeType.SMELTING),
        "solid fuel heat is incoherent");
    h.succeed();
  }

  /** Cold world-water acceptance is also the successor to the old single-pump bootstrap case. */
  public static void coldBootstrap(GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(10, 3, 8));
    var engine = SteamPlant.buildReal(h.getLevel(), o, 64);
    var miner = machine(h, FactoryKind.DRILL, 6, 6);
    ore(h, miner.assembly().profile().center(miner.getBlockPos(), Direction.NORTH).below(), 4);
    var furnace = machine(h, FactoryKind.FURNACE, 6, 10);
    var first = machine(h, FactoryKind.ASSEMBLER, 6, 14);
    for (int z : new int[] {9, 13, 17})
      FactoryCommands.machine(
          h.getLevel(),
          h.absolutePos(new BlockPos(6, 3, z)),
          FactoryKind.INSERTER,
          Direction.SOUTH);
    var chest = h.absolutePos(new BlockPos(6, 3, 18));
    h.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
    first.selectRecipe(id("iron_gear"));
    var second = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 18, 5);
    var third = machine(h, FactoryKind.ASSEMBLER_3, 18, 12);
    for (var m : List.of(second, third)) {
      m.items.setStackInSlot(0, new ItemStack(item("iron_plate"), 2));
      m.selectRecipe(id("iron_gear"));
    }
    var refinery = machine(h, FactoryKind.REFINERY, 25, 21);
    refinery.selectRecipe(id("basic_oil_processing"));
    var oilPump = machine(h, FactoryKind.PUMPJACK, 25, 10);
    var oilPos = oilPump.petroleum().oilPoint();
    h.getLevel().setBlockAndUpdate(oilPos, Content.OIL_DEPOSIT.get().defaultBlockState());
    ((OilDepositBlockEntity) h.getLevel().getBlockEntity(oilPos)).initializeYield(200000);
    var pipe = Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState();
    for (int x = 26; x <= 28; x++) h.setBlock(new BlockPos(x, 3, 9), pipe);
    for (int z = 10; z <= 24; z++) h.setBlock(new BlockPos(28, 3, z), pipe);
    for (int x = 24; x < 28; x++) h.setBlock(new BlockPos(x, 3, 24), pipe);
    // Pumpjack's only outlet rotates to the east; use that physical outlet rather than its center.
    var output = oilPump.petroleum().nozzle(2).relative(oilPump.petroleum().direction(2));
    if (h.getLevel().getBlockState(output).isAir()) h.getLevel().setBlockAndUpdate(output, pipe);
    for (var p :
        List.of(
            new BlockPos(8, 3, 3),
            new BlockPos(12, 3, 3),
            new BlockPos(8, 3, 10),
            new BlockPos(12, 3, 9),
            new BlockPos(12, 3, 16),
            new BlockPos(8, 3, 16),
            new BlockPos(16, 3, 7),
            new BlockPos(16, 3, 14),
            new BlockPos(19, 3, 19),
            new BlockPos(26, 3, 18),
            new BlockPos(25, 3, 14)))
      FactoryCommands.machine(
          h.getLevel(), h.absolutePos(p), FactoryKind.MEDIUM_POLE, Direction.NORTH);
    h.succeedWhen(
        () -> {
          var result = (ChestBlockEntity) h.getLevel().getBlockEntity(chest);
          h.assertTrue(
              result.getItem(0).is(item("iron_gear"))
                  && result.getItem(0).getCount() == 2
                  && products(second) == 1
                  && products(third) == 1
                  && refinery.petroleum().cells.get(2).amount() > 0,
              "cold shore/steam/poles/mining/three tiers/refining not complete: "
                  + PetroleumGameTests.diagnose(
                      h.getLevel(), h.absolutePos(new BlockPos(3, 3, 3)), 27, 6, 27));
          h.assertTrue(engine.water.amount() == 0, "steam engine accepted water");
        });
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 100)
  public static void actualSourceHeatMiningAndTierThreeFractionsPersistAcrossJvm(GameTestHelper h) {
    var level = h.getLevel();
    var base = new BlockPos(2200015, 70, 2200015);
    var profiles =
        List.of(
            MachineProfile.OFFSHORE,
            MachineProfile.BURNER_DRILL,
            MachineProfile.ASSEMBLER_3,
            MachineProfile.BOILER,
            MachineProfile.ENGINE);
    var positions =
        java.util.stream.IntStream.range(0, 5).mapToObj(i -> base.east(12 * i)).toList();
    var cells = new ArrayList<BlockPos>();
    for (int i = 0; i < 5; i++)
      cells.addAll(profiles.get(i).cells(positions.get(i), Direction.NORTH));
    var marker = base.offset(0, 4, 4);
    var orePos = MachineProfile.BURNER_DRILL.center(positions.get(1), Direction.NORTH).below();
    for (var p : cells) level.getChunkAt(p);
    level.getChunkAt(marker);
    level.getChunkAt(orePos);
    if (level.getBlockState(marker).isAir()) {
      h.assertTrue(
          cells.stream()
              .allMatch(p -> level.getBlockState(p).isAir() && level.getBlockEntity(p) == null),
          "partial v0260 checkpoint; refusing reset/reseed");
      ShoreFixture.prepare(level, base, Direction.NORTH);
      for (int i = 0; i < 5; i++) {
        var state = BuiltInRegistries.BLOCK.get(id(profiles.get(i).id)).defaultBlockState();
        StructurePlacement.place(level, positions.get(i), state);
        load(AssemblyAccess.owner(level.getBlockEntity(positions.get(i))));
      }
      ore(h, orePos, 17);
      // Only the diagnostic harness pins chunks. Runtime sources never load a neighbor.
      var chunks = cells.stream().map(net.minecraft.world.level.ChunkPos::new).distinct().toList();
      for (var c : chunks) level.setChunkForced(c.x, c.z, true);
      h.runAfterDelay(
          20,
          () -> {
            var source = (FluidBlockEntity) level.getBlockEntity(base);
            var burner = (MachineBlockEntity) level.getBlockEntity(positions.get(1));
            var assembler = (MachineBlockEntity) level.getBlockEntity(positions.get(2));
            var boiler = (ThermalBlockEntity) level.getBlockEntity(positions.get(3));
            var engine = (ThermalBlockEntity) level.getBlockEntity(positions.get(4));
            h.assertTrue(
                source.waterGenerated() == 4000 && source.cell().amount() == 4000,
                "checkpoint did not use real shore water");
            int moved =
                FluidSimulation.manual(
                    () ->
                        net.neoforged.neoforge.fluids.FluidUtil.tryFluidTransfer(
                                boiler.structurePort(boiler.inputNozzle(), Direction.WEST),
                                source.structurePort(source.outputNozzle(), Direction.NORTH),
                                250,
                                true)
                            .getAmount());
            h.assertTrue(moved == 250, "real source-to-boiler capability transfer failed");
            boiler.items.setStackInSlot(0, new ItemStack(Items.COAL));
            for (int i = 0; i < 7; i++) boiler.tick();
            var steam = boiler.steam.key();
            int transfer = boiler.steam.transferTo(engine.steam, 70, false);
            h.assertTrue(transfer == 70, "finite steam transfer failed");
            engine.tick();
            burner.items.setStackInSlot(8, new ItemStack(Items.COAL));
            ticks(burner, 13);
            var plate = new ItemStack(item("iron_plate"), 2);
            var data = new CompoundTag();
            data.putInt("grade", 11);
            plate.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            assembler.items.setStackInSlot(0, plate);
            assembler.modules.items.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
            assembler.power.restore(16000);
            assembler.selectRecipe(id("iron_gear"));
            ticks(assembler, 3);
            level.setBlockAndUpdate(marker, Blocks.GOLD_BLOCK.defaultBlockState());
            verifyDisk(h, base, positions, orePos);
            freezeDisk(level, cells, orePos);
            for (var c : chunks) level.setChunkForced(c.x, c.z, false);
            System.out.println(
                "FACTORYCRAFT_V0260_DISK: SEEDED real shoreline water, 10x steam, paid fuel/mining"
                    + " and ASM3 components/fractions");
            h.succeed();
          });
    } else {
      h.assertTrue(
          level.getBlockState(marker).is(Blocks.GOLD_BLOCK), "v0260 checkpoint marker changed");
      verifyDisk(h, base, positions, orePos);
      freezeDisk(level, cells, orePos);
      System.out.println(
          "FACTORYCRAFT_V0260_DISK: VERIFIED source, heat/steam/FE, mining target UUID, thirds and"
              + " ASM3 components/fractions across JVM");
      h.succeed();
    }
  }

  private static void verifyDisk(
      GameTestHelper h, BlockPos base, List<BlockPos> positions, BlockPos orePos) {
    var level = h.getLevel();
    var source = (FluidBlockEntity) level.getBlockEntity(base);
    var burner = (MachineBlockEntity) level.getBlockEntity(positions.get(1));
    var assembler = (MachineBlockEntity) level.getBlockEntity(positions.get(2));
    var boiler = (ThermalBlockEntity) level.getBlockEntity(positions.get(3));
    var engine = (ThermalBlockEntity) level.getBlockEntity(positions.get(4));
    var b = burner.saveWithoutMetadata(level.registryAccess());
    var a = assembler.saveWithoutMetadata(level.registryAccess());
    h.assertTrue(
        source.waterGenerated() == 4000
            && source.localCell().amount() == 3750
            && boiler.water.amount() == 229
            && boiler.steam.amount() == 140
            && boiler.boiler.fuel() == 1460
            && engine.steam.amount() == 55
            && engine.engine.energy() == 120,
        "new-format source/thermal ledger changed");
    h.assertTrue(
        b.getLong("MiningFuel") == 4735
            && b.getInt("Progress") == 3
            && b.getInt("MiningPhase") == 10000
            && b.hasUUID("OreIdentity")
            && ((ResourceDepositBlockEntity) level.getBlockEntity(orePos)).remaining() == 17,
        "paid mining/target ledger changed");
    h.assertTrue(
        assembler.power.getEnergyStored() == 15775
            && a.getCompound("Petroleum").getInt("Progress") == 4
            && assembler.modules.fraction() == 20000
            && assembler.items.getStackInSlot(0).getCount() == 2
            && assembler
                    .items
                    .getStackInSlot(0)
                    .get(DataComponents.CUSTOM_DATA)
                    .copyTag()
                    .getInt("grade")
                == 11,
        "tier-three partial paid work/components changed");
    for (int i = 0; i < positions.size(); i++) {
      var owner = AssemblyAccess.owner(level.getBlockEntity(positions.get(i)));
      for (var p : owner.assembly().positions())
        h.assertTrue(
            level.getBlockEntity(p) instanceof CasingBlockEntity part
                && part.boundTo(positions.get(i), owner.assembly().identity()),
            "new checkpoint part owner changed");
    }
  }

  private static void freezeDisk(
      net.minecraft.server.level.ServerLevel level, List<BlockPos> cells, BlockPos ore) {
    for (var p : cells) {
      level.getBlockEntity(p).onChunkUnloaded();
      level.getChunkAt(p).setUnsaved(true);
    }
    level.getBlockEntity(ore).onChunkUnloaded();
    level.getChunkAt(ore).setUnsaved(true);
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 100)
  public static void currentRegistryHasNoRetiredMachinesAndEveryRecipeLoads(GameTestHelper h) {
    h.assertTrue(
        !h.getLevel().getRecipeManager().hadErrorsLoading(),
        "datapack recipe parsing failed; inspect server log");
    for (var name :
        List.of(
            "coal_generator",
            "circuit_reader",
            "circuit_controller",
            "industrial_furnace",
            "filter_inserter"))
      h.assertTrue(
          !BuiltInRegistries.ITEM.containsKey(id(name))
              && !BuiltInRegistries.BLOCK.containsKey(id(name)),
          "retired runtime ID remains: " + name);
    for (var name :
        List.of(
            "conveyor_lift",
            "fast_conveyor_lift",
            "express_conveyor_lift",
            "red_circuit_wire",
            "green_circuit_wire",
            "blue_circuit_wire",
            "white_circuit_wire",
            "offshore_pump",
            "burner_mining_drill",
            "assembling_machine_3"))
      h.assertTrue(
          BuiltInRegistries.ITEM.containsKey(id(name)), "approved/current item missing: " + name);
    for (var name :
        List.of(
            "craft_offshore_pump",
            "craft_boiler",
            "craft_steam_engine",
            "offshore_pump",
            "boiler",
            "steam_engine",
            "burner_mining_drill",
            "assembling_machine_2",
            "assembling_machine_3",
            "automation_3"))
      h.assertTrue(
          h.getLevel().getRecipeManager().byKey(id(name)).isPresent(),
          "required recipe is not loaded: " + name);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0260", timeoutTicks = 3000)
  public static void refineryFluidProductivityReservesBonusAndPreservesEnergyResidue(
      GameTestHelper h) {
    var m = machine(h, FactoryKind.REFINERY, 8, 10);
    m.petroleum().selectRecipe(id("basic_oil_processing"));
    m.petroleum()
        .cells
        .get(0)
        .restore(new FluidKey(factorycraft.petroleum.PetroleumFluids.CRUDE.stack(1)), 25000);
    m.modules.items.setStackInSlot(0, new ItemStack(Content.PRODUCTIVITY_MODULE.get()));
    var source =
        FinitePowerFixture.place(
            h.getLevel(), h.absolutePos(new BlockPos(25, 3, 8)), Direction.NORTH);
    for (int x : new int[] {12, 18, 24})
      FactoryCommands.machine(
          h.getLevel(),
          h.absolutePos(new BlockPos(x, 3, x == 24 ? 14 : 10)),
          FactoryKind.MEDIUM_POLE,
          Direction.NORTH);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              m.petroleum().cells.get(0).amount() == 0
                  && m.petroleum().cells.get(2).amount() == 11700,
              "productive refining has not finished its finite source");
          long held =
              8L * source.steam.amount() + source.engine.energy() + m.power.getEnergyStored();
          h.assertTrue(
              m.modules.credit() == 0 && held == 320000 - 206348,
              "productive refining lost finite steam/FE ledger: " + held);
        });
  }

  private ReleaseV26GameTests() {}
}
