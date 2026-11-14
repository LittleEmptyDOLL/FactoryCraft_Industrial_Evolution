package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.*;
import factorycraft.petroleum.*;
import factorycraft.recipes.*;
import factorycraft.registry.Content;
import factorycraft.simulation.fluids.FluidUnits;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class ReleaseV25GameTests {
  private static ResourceLocation id(String name) {
    return ResourceLocation.parse(name);
  }

  private static MachineBlockEntity machine(GameTestHelper h, FactoryKind kind, int x) {
    var pos = h.absolutePos(new BlockPos(x, 3, 8));
    var profile = MachineProfile.of(Content.FACTORY.get(kind).get().defaultBlockState());
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(kind).get().defaultBlockState());
    for (var p : profile.cells(pos, Direction.NORTH)) h.getLevel().getBlockEntity(p).onLoad();
    return (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
  }

  private static OilDepositBlockEntity point(
      GameTestHelper h, MachineBlockEntity m, int percentHundredths) {
    var p = m.petroleum().oilPoint();
    h.getLevel().setBlockAndUpdate(p, Content.OIL_DEPOSIT.get().defaultBlockState());
    var oil = (OilDepositBlockEntity) h.getLevel().getBlockEntity(p);
    oil.initializeYield(percentHundredths);
    oil.onLoad();
    return oil;
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 100)
  public static void allFluidSourcesRecipesAndCapacitiesUseNativeTenMbUnits(GameTestHelper h) {
    h.assertTrue(
        FluidUnits.mb(100) == 1000
            && FluidUnits.TANK_CAPACITY == 250000
            && FluidUnits.PUMP_CAPACITY == 4000
            && FluidUnits.PUMPJACK_CAPACITY == 10000
            && FluidUnits.PUMP_PER_TICK == 600,
        "global unit/capacity mismatch");
    var r =
        h.getLevel().getRecipeManager().getAllRecipesFor(Content.CHEMISTRY.get()).stream()
            .filter(e -> e.id().equals(id("factorycraft:advanced_oil_processing")))
            .findFirst()
            .orElseThrow()
            .value();
    h.assertTrue(
        r.fluids().get(0).amount() == 1000
            && r.fluids().get(1).amount() == 500
            && r.fluidResults().get(0).getAmount() == 550
            && r.fluidResults().get(1).getAmount() == 450
            && r.fluidResults().get(2).getAmount() == 250,
        "advanced refining is not exact 1:10");
    var input =
        new net.neoforged.neoforge.fluids.FluidStack(
            net.minecraft.world.level.material.Fluids.WATER, 1000);
    h.assertTrue(input.getAmount() == 1000, "foreign/native bucket volume was rescaled");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 100)
  public static void oilInspectionDoesNotConsumeNormalPumpjackItemPlacement(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(10, 2, 10));
    h.getLevel().setBlockAndUpdate(pos, Content.OIL_DEPOSIT.get().defaultBlockState());
    var oil = (OilDepositBlockEntity) h.getLevel().getBlockEntity(pos);
    oil.initializeYield(12500);
    oil.onLoad();
    var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
    var old = player.position();
    var hand = player.getMainHandItem();
    var stack = new ItemStack(Content.FACTORY.get(FactoryKind.PUMPJACK).get());
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
    player.moveTo(pos.getX() + .5, pos.getY() + 1, pos.getZ() - 2);
    try {
      var hit =
          new net.minecraft.world.phys.BlockHitResult(
              net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0, .5, 0),
              Direction.UP,
              pos,
              false);
      var placement =
          new net.minecraft.world.item.context.BlockPlaceContext(
              player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);
      var state = Content.FACTORY.get(FactoryKind.PUMPJACK).get().getStateForPlacement(placement);
      var anchor = PlacementPlan.controllerFor(placement, state);
      var result =
          player.gameMode.useItemOn(
              player, h.getLevel(), stack, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
      h.assertTrue(result.consumesAction(), "normal pumpjack placement failed");
      var owner = AssemblyAccess.owner(h.getLevel().getBlockEntity(anchor));
      h.assertTrue(
          owner instanceof MachineBlockEntity m
              && m.kind() == FactoryKind.PUMPJACK
              && m.petroleum().oilPoint().equals(pos)
              && owner.assembly().formed(),
          "inspection intercepted block item or formed at the wrong point");
      h.assertTrue(
          h.getLevel().getBlockEntity(pos) == oil
              && oil.yield().cycles() == 0
              && oil.yield().current() == 12500 * 15L,
          "placement replaced or debited the geological owner");
    } finally {
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hand);
      player.moveTo(old.x, old.y, old.z);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 100)
  public static void terrainSnapshotSurvivesDecoratedFullChunkAndIndependentJvm(GameTestHelper h) {
    var pos = new BlockPos(-1850080, 70, -1850000);
    var chunk = h.getLevel().getChunkAt(pos);
    if (chunk.getBlockState(pos).is(Blocks.STONE)) {
      h.assertTrue(
          chunk.getBlockState(pos.above(5)).is(Blocks.OAK_LOG), "terrain checkpoint changed");
      h.assertTrue(
          factorycraft.worldgen.ResourceTerrain.frozenSurface(chunk, pos.getX(), pos.getZ()) == 70,
          "saved pre-decoration surface changed after restart");
      System.out.println("FACTORYCRAFT_TERRAIN_SNAPSHOT_DISK: VERIFIED existing chunk attachment");
    } else {
      h.assertTrue(
          h.getLevel().getBlockState(pos).isAir()
              && h.getLevel().getBlockState(pos.above(5)).isAir(),
          "occupied terrain checkpoint");
      h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
      net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(
          chunk, EnumSet.of(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG));
      factorycraft.worldgen.ResourceTerrain.capture(chunk);
      h.getLevel().setBlockAndUpdate(pos.above(5), Blocks.OAK_LOG.defaultBlockState());
      System.out.println(
          "FACTORYCRAFT_TERRAIN_SNAPSHOT_DISK: CREATED pre-decoration height 70, decorated height"
              + " 75");
    }
    var wrapped = new net.minecraft.world.level.chunk.ImposterProtoChunk(chunk, false);
    h.assertTrue(
        wrapped.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                pos.getX() & 15,
                pos.getZ() & 15)
            == 75,
        "fixture did not exercise vanilla decorated-height remapping");
    h.assertTrue(
        factorycraft.worldgen.ResourceTerrain.frozenSurface(wrapped, pos.getX(), pos.getZ()) == 70,
        "completed neighbor decoration changed the original surface");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 260)
  public static void realVanillaSmeltingRunsInBothFurnaceTiersWithExactInputsAndFe(
      GameTestHelper h) {
    var a = machine(h, FactoryKind.FURNACE, 6);
    var b = machine(h, FactoryKind.STEEL_FURNACE, 18);
    var c = machine(h, FactoryKind.STONE_FURNACE, 30);
    for (var m : List.of(a, b, c)) {
      m.items.setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
      if (m.kind().burnerFurnace()) m.items.setStackInSlot(8, new ItemStack(Items.COAL));
      else m.power.restore(2400);
      h.assertTrue(m.selectRecipe(id("minecraft:stone")), "real vanilla smelting missing");
    }
    h.runAfterDelay(
        50,
        () -> {
          int progress = a.data.get(0), fe = a.power.getEnergyStored();
          h.assertTrue(
              progress > 0
                  && a.selectRecipe(id("minecraft:stone"))
                  && a.data.get(0) == progress
                  && a.power.getEnergyStored() == fe,
              "same-ID selection reset paid work");
          RecipeSelectionNetwork.generation++;
          a.tick();
          h.assertTrue(
              a.data.get(0) == progress + 2 && a.power.getEnergyStored() == fe - 24,
              "unchanged datapack recipe reset or granted work");
        });
    h.runAfterDelay(
        230,
        () -> {
          for (var m : List.of(a, b, c))
            h.assertTrue(
                m.items.getStackInSlot(0).isEmpty()
                    && m.items.getStackInSlot(4).is(Items.STONE)
                    && m.items.getStackInSlot(4).getCount() == 1
                    && m.power.getEnergyStored() == 0,
                "native smelting input/output/FE mismatch");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 100)
  public static void smeltingKeepsOutputComponentsAndNeverLeaksIntoAssemblers(GameTestHelper h) {
    var m = machine(h, FactoryKind.ASSEMBLER, 6);
    h.assertTrue(
        m.recipes().stream().noneMatch(r -> r.id().equals(id("minecraft:stone"))),
        "smelting leaked into assembler");
    var output = new ItemStack(Items.STONE);
    output.set(DataComponents.CUSTOM_NAME, Component.literal("exact smelted components"));
    var r =
        new RecipeHolder<>(
            id("factorycraft:test_component_smelt"),
            new SmeltingRecipe(
                "", CookingBookCategory.BLOCKS, Ingredient.of(Items.COBBLESTONE), output, 0, 2));
    var actual = SmeltingInterop.result(r, new ItemStack(Items.COBBLESTONE), h.getLevel());
    h.assertTrue(
        ItemStack.isSameItemSameComponents(output, actual), "smelting stripped output components");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 100)
  public static void recipeSelectionRejectsWrongMenuUnknownRecipeRangeAndResearch(
      GameTestHelper h) {
    var m = machine(h, FactoryKind.ASSEMBLER, 6);
    var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
    var previous = player.containerMenu;
    var old = player.position();
    player.moveTo(m.getBlockPos().getX() + .5, m.getBlockPos().getY(), m.getBlockPos().getZ() + .5);
    var menu = new factorycraft.production.FactoryMenu(91, player.getInventory(), m, m.data);
    player.containerMenu = menu;
    try {
      h.assertTrue(
          !RecipeSelectionNetwork.handle(
                  player,
                  new RecipeSelectionNetwork.Request(92, true, id("factorycraft:iron_gear")))
              && !RecipeSelectionNetwork.handle(
                  player,
                  new RecipeSelectionNetwork.Request(91, true, id("factorycraft:nonexistent"))),
          "untrusted selector accepted menu/id");
      h.assertTrue(
          RecipeSelectionNetwork.handle(
                  player,
                  new RecipeSelectionNetwork.Request(91, true, id("factorycraft:iron_gear")))
              && m.selectedRecipeId().equals("factorycraft:iron_gear"),
          "guarded stable ID failed");
      h.assertTrue(
          RecipeSelectionNetwork.handle(player, new RecipeSelectionNetwork.Request(91, true, null))
              && m.selectedRecipeId().isEmpty(),
          "Auto failed");
      m.bindResearch(ResearchGameTests.personal(h, "v0250-selection"));
      h.assertTrue(
          !m.selectRecipe(id("factorycraft:advanced_circuit")), "locked recipe selection accepted");
      player.moveTo(player.getX() + 100, player.getY(), player.getZ());
      h.assertTrue(
          !RecipeSelectionNetwork.handle(
              player, new RecipeSelectionNetwork.Request(91, true, id("factorycraft:iron_gear"))),
          "out-of-range recipe selection accepted");
    } finally {
      player.containerMenu = previous;
      player.moveTo(old.x, old.y, old.z);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 100)
  public static void lastPumpjackCycleTickWaitsForOutputBeforeChargingAndDepleting(
      GameTestHelper h) {
    var m = machine(h, FactoryKind.PUMPJACK, 6);
    var oil = point(h, m, 10000);
    m.power.restore(300);
    h.runAfterDelay(
        18,
        () -> {
          h.assertTrue(
              oil.yield().cycles() == 0 && m.power.getEnergyStored() == 300, "early cycle charged");
          m.petroleum()
              .cells
              .get(2)
              .fill(new factorycraft.fluids.FluidKey(PetroleumFluids.CRUDE.stack(1)), 10000, false);
        });
    h.runAfterDelay(
        35,
        () -> {
          h.assertTrue(
              oil.yield().cycles() == 0 && m.power.getEnergyStored() == 300,
              "blocked final cycle consumed resources");
          m.petroleum().cells.get(2).drain(10000, false);
        });
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              oil.yield().cycles() == 1
                  && oil.yield().generated() == 100
                  && m.petroleum().cells.get(2).amount() == 100
                  && m.power.getEnergyStored() == 0,
              "resumed cycle not exact");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 100)
  public static void pumpjackReconstructionKeepsUnpaidWorkAndSourceIdentity(GameTestHelper h) {
    var m = machine(h, FactoryKind.PUMPJACK, 6);
    var oil = point(h, m, 11550);
    m.power.restore(300);
    h.runAfterDelay(
        10,
        () -> {
          var tag = m.saveWithFullMetadata(h.getLevel().registryAccess());
          h.assertTrue(
              tag.getCompound("Petroleum").getInt("PumpWork") > 0 && oil.yield().generated() == 0,
              "no genuine partial cycle");
          var copy =
              (MachineBlockEntity)
                  net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                      m.getBlockPos(), m.getBlockState(), tag, h.getLevel().registryAccess());
          m.setRemoved();
          h.getLevel().setBlockEntity(copy);
          copy.onLoad();
        });
    h.runAfterDelay(
        40,
        () -> {
          var copy = (MachineBlockEntity) h.getLevel().getBlockEntity(m.getBlockPos());
          h.assertTrue(
              oil.yield().cycles() == 1
                  && oil.yield().generated() == 115
                  && oil.yield().residue() == 750
                  && copy.petroleum().cells.get(2).amount() == 115
                  && copy.power.getEnergyStored() == 0,
              "reload created unpaid work or lost fractional oil");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 100)
  public static void selectedVanillaSmeltingAndPaidPartialWorkSurviveIndependentJvm(
      GameTestHelper h) {
    var level = h.getLevel();
    var anchor = new BlockPos(-1850040, 70, -1850000);
    var profile = MachineProfile.FURNACE;
    var marker = anchor.offset(6, 4, 6);
    for (var cell : profile.cells(anchor, Direction.NORTH)) level.getChunkAt(cell);
    level.getChunkAt(marker);
    if (level.getBlockState(marker).isAir()) {
      h.assertTrue(
          profile.cells(anchor, Direction.NORTH).stream()
              .allMatch(p -> level.getBlockState(p).isAir() && level.getBlockEntity(p) == null),
          "partial smelting checkpoint; refusing reset");
      StructurePlacement.place(
          level, anchor, Content.FACTORY.get(FactoryKind.FURNACE).get().defaultBlockState());
      for (var cell : profile.cells(anchor, Direction.NORTH)) level.getBlockEntity(cell).onLoad();
      var m = (MachineBlockEntity) level.getBlockEntity(anchor);
      m.items.setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
      m.power.restore(2400);
      h.assertTrue(m.selectRecipe(id("minecraft:stone")), "selected smelting missing");
      for (int i = 0; i < 53; i++) m.tick();
      level.setBlockAndUpdate(marker, Blocks.GOLD_BLOCK.defaultBlockState());
      System.out.println(
          "FACTORYCRAFT_SMELTING_DISK: SEEDED selected vanilla ID, 106 work / 1272 paid FE");
    } else System.out.println("FACTORYCRAFT_SMELTING_DISK: VERIFYING existing checkpoint");
    var m = (MachineBlockEntity) level.getBlockEntity(anchor);
    h.assertTrue(
        m.selectedRecipeId().equals("minecraft:stone")
            && m.data.get(0) == 106
            && m.power.getEnergyStored() == 1128
            && m.items.getStackInSlot(0).is(Items.COBBLESTONE)
            && m.items.getStackInSlot(4).isEmpty()
            && !m.saveWithFullMetadata(level.registryAccess())
                .getString("ActiveDefinition")
                .isEmpty(),
        "selected smelting checkpoint lost ID, inputs or paid work");
    for (var cell : profile.cells(anchor, Direction.NORTH)) {
      level.getBlockEntity(cell).onChunkUnloaded();
      level.getChunkAt(cell).setUnsaved(true);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 100)
  public static void pumpjackSpeedAndLateBeaconUseActualWorkAndEnergy(GameTestHelper h) {
    var m = machine(h, FactoryKind.PUMPJACK, 6);
    var oil = point(h, m, 10000);
    m.modules.items.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
    h.assertTrue(
        m.modules.effects().speed() == 12000
            && !m.modules.items.isItemValid(1, new ItemStack(Content.PRODUCTIVITY_MODULE.get())),
        "pumpjack module constraints wrong");
    m.power.restore(391);
    for (int i = 0; i < 17; i++) m.tick();
    h.assertTrue(
        oil.yield().cycles() == 1
            && oil.yield().generated() == 100
            && m.petroleum().cells.get(2).amount() == 100
            && m.power.getEnergyStored() == 0,
        "speed module granted fake work/FE");
    var pos = h.absolutePos(new BlockPos(10, 3, 8));
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(FactoryKind.BEACON).get().defaultBlockState());
    for (var cell : MachineProfile.BEACON.cells(pos, Direction.NORTH))
      h.getLevel().getBlockEntity(cell).onLoad();
    var b = (BeaconBlockEntity) h.getLevel().getBlockEntity(pos);
    b.modules.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
    b.power.restore(16000);
    b.payForTick();
    h.assertTrue(
        b.broadcasting() && m.modules.effects().speed() > 12000,
        "late beacon failed to invalidate pumpjack effects");
    b.power.restore(0);
    b.payForTick();
    h.assertTrue(m.modules.effects().speed() == 12000, "unpowered beacon kept a fake boost");
    h.succeed();
  }

  private static final class TerrainRuns extends net.minecraft.world.level.saveddata.SavedData {
    int next;

    static TerrainRuns load(net.minecraft.nbt.CompoundTag t, HolderLookup.Provider r) {
      var d = new TerrainRuns();
      d.next = t.getInt("Next");
      return d;
    }

    public net.minecraft.nbt.CompoundTag save(
        net.minecraft.nbt.CompoundTag t, HolderLookup.Provider r) {
      t.putInt("Next", next);
      return t;
    }

    int claim() {
      if (next < 0 || next > 10000) throw new IllegalStateException("Terrain fixture exhausted");
      setDirty();
      return next++;
    }
  }

  @GameTest(template = "empty", batch = "release_v0250_terrain", timeoutTicks = 200)
  public static void actualVolumetricFieldsRespectFlatSlopeCliffCavesWaterAndProtectedBlocks(
      GameTestHelper h) {
    var runs =
        h.getLevel()
            .getDataStorage()
            .computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                    TerrainRuns::new, TerrainRuns::load),
                "factorycraft_v0250_terrain_runs");
    for (int mode = 0; mode < 5; mode++)
      for (boolean legacy : new boolean[] {true, false}) {
        var r =
            factorycraft.diagnostics.ResourceTerrainFixture.build(
                h.getLevel(), runs.claim(), mode, legacy);
        h.assertTrue(r.members() > 30 && r.deep() > 30, "terrain is missing real volumetric stock");
        if (mode == 4 && !legacy)
          h.assertTrue(r.aboveCenter() > 0, "higher solid terrain lost the upper hemisphere");
        System.out.println(
            "FACTORYCRAFT_TERRAIN_PROFILE: mode="
                + mode
                + " legacy0250="
                + legacy
                + " owners="
                + r.members()
                + " deep="
                + r.deep()
                + " chunks="
                + r.chunks()
                + " nanos="
                + r.nanos()
                + " allocated="
                + r.allocatedBytes());
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0250", timeoutTicks = 100)
  public static void percentageOilPointAndFractionalCycleStateSurviveSeparateServerJvm(
      GameTestHelper h) {
    var p = new BlockPos(-1850000, 70, -1850000);
    var level = h.getLevel();
    var profile = MachineProfile.PUMPJACK;
    var anchor = profile.anchorAtCenter(p.above(), Direction.NORTH);
    var marker = p.offset(6, 4, 6);
    for (var cell : profile.cells(anchor, Direction.NORTH)) level.getChunkAt(cell);
    level.getChunkAt(marker);
    if (level.getBlockState(marker).isAir()) {
      h.assertTrue(
          level.getBlockEntity(p) == null && level.getBlockEntity(anchor) == null,
          "partial percentage fixture; refusing reset");
      level.setBlockAndUpdate(p, Content.OIL_DEPOSIT.get().defaultBlockState());
      var oil = (OilDepositBlockEntity) level.getBlockEntity(p);
      oil.initializeYield(11550);
      oil.onLoad();
      StructurePlacement.place(
          level, anchor, Content.FACTORY.get(FactoryKind.PUMPJACK).get().defaultBlockState());
      for (var cell : profile.cells(anchor, Direction.NORTH)) level.getBlockEntity(cell).onLoad();
      var pump = (MachineBlockEntity) level.getBlockEntity(anchor);
      pump.power.restore(600);
      for (int i = 0; i < 28; i++) pump.tick();
      level.setBlockAndUpdate(marker, Blocks.GOLD_BLOCK.defaultBlockState());
      System.out.println(
          "FACTORYCRAFT_OIL_YIELD_DISK: SEEDED real paid cycle and partial unpaid work");
    } else System.out.println("FACTORYCRAFT_OIL_YIELD_DISK: VERIFYING existing checkpoint");
    var oil = (OilDepositBlockEntity) level.getBlockEntity(p);
    var pump = (MachineBlockEntity) level.getBlockEntity(anchor);
    var tag = pump.saveWithFullMetadata(level.registryAccess()).getCompound("Petroleum");
    h.assertTrue(
        oil.yield().initial() == 173250
            && oil.yield().current() == 173245
            && oil.yield().generated() == 115
            && oil.yield().residue() == 750
            && oil.yield().cycles() == 1
            && pump.petroleum().cells.get(2).amount() == 115
            && pump.power.getEnergyStored() == 300
            && tag.getInt("PumpWork") == 80000
            && tag.getInt("PumpCost") == 120
            && tag.getUUID("PumpPoint").equals(oil.identity()),
        "paid output, percentage or partial-work checkpoint changed");
    oil.onChunkUnloaded();
    for (var cell : profile.cells(anchor, Direction.NORTH)) {
      var be = level.getBlockEntity(cell);
      be.onChunkUnloaded();
      level.getChunkAt(cell).setUnsaved(true);
    }
    level.getChunkAt(p).setUnsaved(true);
    h.succeed();
  }
}
