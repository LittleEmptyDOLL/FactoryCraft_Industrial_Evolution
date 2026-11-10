package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.petroleum.*;
import factorycraft.recipes.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class AdvancedPetroleumGameTests {
  private static final IFluidHandler.FluidAction EXEC = IFluidHandler.FluidAction.EXECUTE;

  private static MachineBlockEntity machine(GameTestHelper h, FactoryKind kind, int x, int z) {
    var pos = h.absolutePos(new BlockPos(x, 2, z));
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(kind).get().defaultBlockState());
    return (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
  }

  private static Item item(String id) {
    return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, id));
  }

  private static ChemicalRecipe recipe(GameTestHelper h, String id) {
    return h.getLevel().getRecipeManager().getAllRecipesFor(Content.CHEMISTRY.get()).stream()
        .filter(r -> r.id().getPath().equals(id))
        .findFirst()
        .orElseThrow()
        .value();
  }

  private static void select(MachineBlockEntity be, String id) {
    if (!be.petroleum().selectRecipe(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, id)))
      throw new IllegalStateException("Missing recipe " + id);
  }

  private static void advancedInput(MachineBlockEntity r) {
    r.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.CRUDE.stack(1)), 100);
    r.petroleum()
        .cells
        .get(1)
        .restore(
            new FluidKey(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1)), 50);
  }

  private static void assertProducts(GameTestHelper h, MachineBlockEntity r) {
    var p = r.petroleum();
    h.assertTrue(
        p.cells.size() == 5
            && p.cells.get(0).amount() == 0
            && p.cells.get(1).amount() == 0
            && p.cells.get(2).amount() == 55
            && p.cells.get(3).amount() == 45
            && p.cells.get(4).amount() == 25,
        "advanced refining volume ledger incorrect");
  }

  @GameTest(template = "empty", timeoutTicks = 190)
  public static void advancedRefiningCommitsAllThreeOutputs(GameTestHelper h) {
    var r = machine(h, FactoryKind.REFINERY, 5, 5);
    h.runAfterDelay(
        5,
        () -> {
          advancedInput(r);
          r.power.restore(4000);
        });
    h.runAfterDelay(
        130,
        () -> {
          assertProducts(h, r);
          h.assertTrue(r.power.getEnergyStored() == 1000, "advanced FE ledger incorrect");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 230)
  public static void fullOrMismatchedThirdOutputPausesWithoutSpendingInputsOrEnergy(
      GameTestHelper h) {
    var r = machine(h, FactoryKind.REFINERY, 5, 5);
    h.runAfterDelay(
        5,
        () -> {
          advancedInput(r);
          r.power.restore(4000);
          r.petroleum().cells.get(4).restore(new FluidKey(PetroleumFluids.HEAVY.stack(1)), 3990);
        });
    h.runAfterDelay(
        35,
        () -> {
          h.assertTrue(
              r.power.getEnergyStored() == 4000
                  && r.petroleum().cells.get(0).amount() == 100
                  && r.petroleum().data.get(0) == 0,
              "third-output backpressure spent resources");
          r.petroleum().cells.get(4).restore(new FluidKey(PetroleumFluids.LIGHT.stack(1)), 1);
        });
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              r.power.getEnergyStored() == 4000 && r.petroleum().cells.get(1).amount() == 50,
              "wrong third-output fluid spent resources");
          r.petroleum().cells.get(4).drain(4000, false);
        });
    h.runAfterDelay(
        185,
        () -> {
          assertProducts(h, r);
          h.assertTrue(r.power.getEnergyStored() == 1000, "unblocked refining did not resume");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void advancedRefiningStopsWhenEnergyRunsOutAndKeepsProgress(GameTestHelper h) {
    var r = machine(h, FactoryKind.REFINERY, 5, 5);
    h.runAfterDelay(
        5,
        () -> {
          advancedInput(r);
          r.power.restore(300);
        });
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              r.petroleum().data.get(0) == 10
                  && r.petroleum().cells.get(0).amount() == 100
                  && r.petroleum().cells.get(4).amount() == 0
                  && r.power.getEnergyStored() == 0,
              "partial refining spent inputs or lost progress");
          r.power.restore(2700);
        });
    h.runAfterDelay(
        155,
        () -> {
          assertProducts(h, r);
          h.assertTrue(r.power.getEnergyStored() == 0, "resumed refining FE incorrect");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void thirdNozzleMovesIntoARealPipeAndCachedPortStopsOnUnload(GameTestHelper h) {
    var r = machine(h, FactoryKind.REFINERY, 5, 5);
    var pos = r.petroleum().nozzle(4).relative(r.petroleum().direction(4));
    h.getLevel()
        .setBlockAndUpdate(pos, Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    h.runAfterDelay(
        5,
        () -> {
          advancedInput(r);
          r.power.restore(3000);
        });
    h.runAfterDelay(
        140,
        () -> {
          var pipe = (FluidBlockEntity) h.getLevel().getBlockEntity(pos);
          h.assertTrue(
              pipe.cell().amount() == 25
                  && pipe.cell().key().equals(new FluidKey(PetroleumFluids.HEAVY.stack(1))),
              "third physical outlet failed to feed pipe");
          var port =
              h.getLevel()
                  .getCapability(
                      Capabilities.FluidHandler.BLOCK,
                      r.petroleum().nozzle(4),
                      r.petroleum().direction(4));
          h.assertTrue(port != null, "missing controller third nozzle capability");
          r.petroleum().cells.get(4).restore(new FluidKey(PetroleumFluids.HEAVY.stack(1)), 41);
          var part =
              (CasingBlockEntity) h.getLevel().getBlockEntity(r.assembly().positions().getLast());
          part.onChunkUnloaded();
          h.assertTrue(
              port.drain(100, EXEC).isEmpty(),
              "cached third-output capability escaped assembly suspension");
          part.onLoad();
          h.assertTrue(port.drain(100, EXEC).getAmount() == 41, "cached nozzle did not resume");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void refineryNozzlesRemainDistinctInAllFourFacings(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(8, 2, 8));
    for (var facing : Direction.Plane.HORIZONTAL) {
      StructurePlacement.place(
          h.getLevel(),
          pos,
          Content.FACTORY
              .get(FactoryKind.REFINERY)
              .get()
              .defaultBlockState()
              .setValue(FactoryBlock.FACING, facing));
      var r = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
      for (var cell : MachineProfile.REFINERY.cells(pos, facing))
        h.getLevel().getBlockEntity(cell).onLoad();
      var ports = new HashSet<BlockPos>();
      for (int i = 0; i < 5; i++) {
        var p = r.petroleum().nozzle(i);
        h.assertTrue(ports.add(p), "nozzles overlap");
        var cap =
            h.getLevel()
                .getCapability(Capabilities.FluidHandler.BLOCK, p, r.petroleum().direction(i));
        h.assertTrue(cap != null && cap.getTanks() == 1, "rotated physical nozzle missing");
        if (i >= 2) {
          var fluid =
              List.of(PetroleumFluids.GAS, PetroleumFluids.LIGHT, PetroleumFluids.HEAVY).get(i - 2);
          r.petroleum().cells.get(i).restore(new FluidKey(fluid.stack(1)), 23 + i);
          h.assertTrue(
              cap.fill(fluid.stack(50), EXEC) == 0 && cap.drain(100, EXEC).getAmount() == 23 + i,
              "nozzle addressed another tank");
        }
      }
      h.assertTrue(
          r.petroleum().port(pos, null) == null, "controller exposed unsided fluid bypass");
      r.assembly().removeWhole(false);
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void oldRefineryFourCellStateKeepsPortOrderAndDoesNotInventOil(GameTestHelper h) {
    var r = machine(h, FactoryKind.REFINERY, 5, 5);
    var registry = h.getLevel().registryAccess();
    r.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.CRUDE.stack(1)), 731);
    r.petroleum().cells.get(2).restore(new FluidKey(PetroleumFluids.GAS.stack(1)), 389);
    r.petroleum().cells.get(3).restore(new FluidKey(PetroleumFluids.GAS.stack(1)), 17);
    var tag = r.saveWithFullMetadata(registry);
    tag.getCompound("Petroleum").putInt("Schema", 1);
    tag.getCompound("Petroleum").remove("Fluid4");
    var copy =
        (MachineBlockEntity)
            BlockEntity.loadStatic(r.getBlockPos(), r.getBlockState(), tag, registry);
    h.assertTrue(
        copy.petroleum().cells.get(0).amount() == 731
            && copy.petroleum().cells.get(2).amount() == 389
            && copy.petroleum().cells.get(3).amount() == 17
            && copy.petroleum().cells.get(4).amount() == 0,
        "legacy tank order or resources changed");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void threeOutputRecipeJsonAndWireRoundTripAndRejectOversizedLists(
      GameTestHelper h) {
    var r = recipe(h, "advanced_oil_processing");
    var ops =
        net.minecraft.resources.RegistryOps.create(
            com.mojang.serialization.JsonOps.INSTANCE, h.getLevel().registryAccess());
    var json = ChemicalRecipe.CODEC.codec().encodeStart(ops, r).getOrThrow();
    var decoded = ChemicalRecipe.CODEC.codec().parse(ops, json).getOrThrow();
    h.assertTrue(
        decoded.fluidResults().size() == 3 && decoded.fluidResults().get(2).getAmount() == 25,
        "JSON lost third result");
    var b =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      var codec = Content.CHEMISTRY_SERIALIZER.get().streamCodec();
      codec.encode(b, r);
      h.assertTrue(
          codec.decode(b).fluidResults().get(2).getFluid() == PetroleumFluids.HEAVY.source.get(),
          "wire lost third fluid");
    } finally {
      b.release();
    }
    json.getAsJsonObject()
        .getAsJsonArray("fluid_results")
        .add(json.getAsJsonObject().getAsJsonArray("fluid_results").get(0).deepCopy());
    h.assertTrue(
        ChemicalRecipe.CODEC.codec().parse(ops, json).error().isPresent(),
        "four fluid outputs accepted");
    boolean rejected = false;
    try {
      new ChemicalRecipe(
          "chemical_plant",
          r.items(),
          r.fluids(),
          r.results(),
          r.fluidResults(),
          r.ticks(),
          r.energy());
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    h.assertTrue(rejected, "two-outlet plant accepted three outputs");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void crackingLubricantAcidAndBatteryUseActualRecipesAndExactBalances(
      GameTestHelper h) {
    var ids =
        List.of(
            "heavy_oil_cracking", "light_oil_cracking", "lubricant", "sulfuric_acid", "battery");
    var machines = new ArrayList<MachineBlockEntity>();
    for (int i = 0; i < ids.size(); i++)
      machines.add(machine(h, FactoryKind.CHEMICAL_PLANT, 2 + i % 3 * 5, 2 + i / 3 * 5));
    h.runAfterDelay(
        5,
        () -> {
          for (int i = 0; i < ids.size(); i++) {
            var m = machines.get(i);
            var recipe = recipe(h, ids.get(i));
            select(m, ids.get(i));
            m.power.restore(10000);
            for (int f = 0; f < recipe.fluids().size(); f++) {
              var ingredient = recipe.fluids().get(f);
              m.petroleum()
                  .cells
                  .get(f)
                  .restore(new FluidKey(ingredient.getFluids()[0]), ingredient.amount());
            }
            for (int s = 0; s < recipe.items().size(); s++) {
              var ingredient = recipe.items().get(s);
              m.items.setStackInSlot(
                  s, ingredient.ingredient().getItems()[0].copyWithCount(ingredient.count()));
            }
          }
        });
    h.runAfterDelay(
        130,
        () -> {
          for (int i = 0; i < ids.size(); i++) {
            var m = machines.get(i);
            var r = recipe(h, ids.get(i));
            h.assertTrue(
                m.power.getEnergyStored() == 10000 - r.energy() * r.ticks(),
                ids.get(i) + " FE mismatch");
            for (int f = 0; f < r.fluids().size(); f++)
              h.assertTrue(
                  m.petroleum().cells.get(f).amount() == 0,
                  ids.get(i) + " fluid input not consumed");
            for (int s = 0; s < 4; s++)
              h.assertTrue(
                  m.items.getStackInSlot(s).isEmpty(),
                  ids.get(i) + " item inputs not consumed exactly once");
            for (int f = 0; f < r.fluidResults().size(); f++)
              h.assertTrue(
                  FluidStack.matches(m.petroleum().stored(f + 2), r.fluidResults().get(f)),
                  ids.get(i) + " output fluid mismatch");
            if (!r.results().isEmpty())
              h.assertTrue(
                  ItemStack.matches(m.items.getStackInSlot(4), r.results().getFirst()),
                  "battery output mismatch");
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void batteryItemBackpressureReservesAcidAndBothPlates(GameTestHelper h) {
    var m = machine(h, FactoryKind.CHEMICAL_PLANT, 5, 5);
    h.runAfterDelay(
        5,
        () -> {
          m.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.ACID.stack(1)), 20);
          m.items.setStackInSlot(0, new ItemStack(item("iron_plate")));
          m.items.setStackInSlot(1, new ItemStack(item("copper_plate")));
          for (int i = 4; i < 8; i++)
            m.items.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
          m.power.restore(4000);
        });
    h.runAfterDelay(
        35,
        () -> {
          h.assertTrue(
              m.power.getEnergyStored() == 4000
                  && m.petroleum().cells.get(0).amount() == 20
                  && m.items.getStackInSlot(0).getCount() == 1
                  && m.items.getStackInSlot(1).getCount() == 1,
              "blocked battery spent ingredients");
          m.items.setStackInSlot(4, ItemStack.EMPTY);
        });
    h.runAfterDelay(
        145,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).is(item("battery"))
                  && m.items.getStackInSlot(4).getCount() == 1
                  && m.petroleum().cells.get(0).amount() == 0
                  && m.power.getEnergyStored() == 2400,
              "unblocked battery failed exact conversion");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void partiallyWorkedRefineryReconstructsWithItsCanonicalRecipe(GameTestHelper h) {
    var r = machine(h, FactoryKind.REFINERY, 5, 5);
    var pos = r.getBlockPos();
    h.runAfterDelay(
        5,
        () -> {
          advancedInput(r);
          r.power.restore(3000);
          select(r, "advanced_oil_processing");
        });
    h.runAfterDelay(
        35,
        () -> {
          int before = r.petroleum().data.get(0);
          h.assertTrue(
              !r.petroleum()
                      .selectRecipe(
                          ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "missing_recipe"))
                  && r.petroleum().data.get(0) == before,
              "unknown recipe selection destroyed progress");
          var tag = r.saveWithFullMetadata(h.getLevel().registryAccess());
          h.assertTrue(
              tag.getCompound("Petroleum").getInt("Progress") > 0
                  && !tag.getCompound("Petroleum").getString("Active").isEmpty(),
              "fixture has no canonical partial work");
          var copy =
              (MachineBlockEntity)
                  BlockEntity.loadStatic(
                      pos, r.getBlockState(), tag, h.getLevel().registryAccess());
          r.setRemoved();
          h.getLevel().setBlockEntity(copy);
          copy.onLoad();
        });
    h.runAfterDelay(
        145,
        () -> {
          var restored = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
          assertProducts(h, restored);
          h.assertTrue(
              restored.power.getEnergyStored() == 0, "reconstruction restarted or duplicated work");
          h.succeed();
        });
  }

  private static FluidStack componentHeavy(int n) {
    var fluid = PetroleumFluids.HEAVY.stack(n);
    var data = new net.minecraft.nbt.CompoundTag();
    data.putInt("grade", 7);
    fluid.set(
        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
        net.minecraft.world.item.component.CustomData.of(data));
    return fluid;
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void portableRefineryPreservesAllFiveCellsOverflowComponentsAndFreshIdentity(
      GameTestHelper h) {
    var r = machine(h, FactoryKind.REFINERY, 5, 5);
    h.runAfterDelay(
        5,
        () -> {
          advancedInput(r);
          r.petroleum().cells.get(2).restore(new FluidKey(PetroleumFluids.GAS.stack(1)), 389);
          r.petroleum().cells.get(3).restore(new FluidKey(PetroleumFluids.LIGHT.stack(1)), 211);
          r.petroleum().cells.get(4).restore(new FluidKey(componentHeavy(1)), 5001);
          r.power.restore(7312);
          select(r, "advanced_oil_processing");
          var old = r.assembly().identity();
          h.getLevel().destroyBlock(r.assembly().positions().getLast(), true);
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(ItemEntity.class, new AABB(r.getBlockPos()).inflate(6));
          h.assertTrue(
              drops.size() == 1
                  && drops
                      .getFirst()
                      .getItem()
                      .is(Content.FACTORY.get(FactoryKind.REFINERY).get().asItem()),
              "refinery demolition copied resources");
          var player = h.makeMockPlayer(GameType.SURVIVAL);
          player.setYRot(Direction.SOUTH.toYRot());
          player.setItemInHand(InteractionHand.MAIN_HAND, drops.getFirst().getItem().copy());
          drops.getFirst().discard();
          var target = h.absolutePos(new BlockPos(12, 2, 5));
          h.assertTrue(
              PlacementTestSupport.place(player, target).consumesAction(),
              "portable refinery would not place");
          var restored = (MachineBlockEntity) h.getLevel().getBlockEntity(target);
          var p = restored.petroleum();
          h.assertTrue(
              p.cells.size() == 5
                  && p.cells.get(0).amount() == 100
                  && p.cells.get(1).amount() == 50
                  && p.cells.get(2).amount() == 389
                  && p.cells.get(3).amount() == 211
                  && p.cells.get(4).amount() == 5001
                  && p.cells.get(4).key().equals(new FluidKey(componentHeavy(1)))
                  && restored.power.getEnergyStored() == 7312,
              "portable five-cell state changed");
          h.assertTrue(
              !old.equals(restored.assembly().identity()), "portable placement reused identity");
          for (var cell : restored.assembly().positions())
            h.assertTrue(
                ((CasingBlockEntity) h.getLevel().getBlockEntity(cell))
                    .boundTo(target, restored.assembly().identity()),
                "portable import orphaned a new part");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void advancedFiveCellStatePersistsAcrossIndependentServerProcesses(
      GameTestHelper h) {
    var level = h.getLevel();
    var pos = new BlockPos(527, 70, 527);
    var cells = MachineProfile.REFINERY.cells(pos, Direction.NORTH);
    for (var cell : cells) level.getChunkAt(cell);
    if (cells.stream().allMatch(p -> level.getBlockEntity(p) == null)) {
      StructurePlacement.place(
          level, pos, Content.FACTORY.get(FactoryKind.REFINERY).get().defaultBlockState());
      var r = (MachineBlockEntity) level.getBlockEntity(pos);
      r.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.CRUDE.stack(1)), 731);
      r.petroleum()
          .cells
          .get(1)
          .restore(
              new FluidKey(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1)), 83);
      r.petroleum().cells.get(2).restore(new FluidKey(PetroleumFluids.GAS.stack(1)), 389);
      r.petroleum().cells.get(3).restore(new FluidKey(PetroleumFluids.LIGHT.stack(1)), 211);
      r.petroleum().cells.get(4).restore(new FluidKey(componentHeavy(1)), 5001);
      r.power.restore(7312);
      select(r, "advanced_oil_processing");
      var tag = r.saveWithFullMetadata(level.registryAccess());
      tag.getCompound("Petroleum").putInt("Progress", 17);
      r.loadWithComponents(tag, level.registryAccess());
      System.out.println(
          "FACTORYCRAFT_ADVANCED_OIL_DISK: SEEDED five cells, overflow/components, selected recipe"
              + " and partial work");
    } else {
      h.assertTrue(
          level.getBlockEntity(pos) instanceof MachineBlockEntity,
          "partial advanced oil fixture; refusing to reseed");
      var r = (MachineBlockEntity) level.getBlockEntity(pos);
      int[] amounts = {731, 83, 389, 211, 5001};
      for (int i = 0; i < 5; i++)
        h.assertTrue(
            r.petroleum().cells.get(i).amount() == amounts[i], "disk fifth-cell volume changed");
      var tag = r.saveWithFullMetadata(level.registryAccess()).getCompound("Petroleum");
      h.assertTrue(
          r.petroleum().cells.get(4).key().equals(new FluidKey(componentHeavy(1)))
              && r.power.getEnergyStored() == 7312
              && tag.getInt("Progress") == 17
              && tag.getString("SelectedRecipe").equals("factorycraft:advanced_oil_processing"),
          "disk components, FE or recipe state changed");
      for (var cell : r.assembly().positions())
        h.assertTrue(
            level.getBlockEntity(cell) instanceof CasingBlockEntity part
                && part.boundTo(pos, r.assembly().identity()),
            "four-chunk refinery identity changed");
      System.out.println(
          "FACTORYCRAFT_ADVANCED_OIL_DISK: VERIFIED all five cells, components, FE, progress,"
              + " recipe and four-chunk identity");
    }
    for (var cell : cells) {
      var be = level.getBlockEntity(cell);
      if (be != null) be.onChunkUnloaded();
      level.getChunkAt(cell).setUnsaved(true);
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void newFluidBucketsRetainIdentityAndRejectLossyComponents(GameTestHelper h) {
    for (var kind :
        List.of(
            PetroleumFluids.HEAVY,
            PetroleumFluids.LIGHT,
            PetroleumFluids.LUBRICANT,
            PetroleumFluids.ACID)) {
      h.assertTrue(
          kind.stack(1000).getFluidType().getBucket(kind.stack(1000)).is(kind.bucket.get()),
          "plain bucket missing for " + kind.id);
    }
    h.assertTrue(
        componentHeavy(1000).getFluidType().getBucket(componentHeavy(1000)).isEmpty(),
        "bucket erased fluid components");
    h.succeed();
  }

  @GameTest(template = "industrial", timeoutTicks = 5600)
  public static void finiteOilBecomesBatteriesThroughPoweredPipesAndInserters(GameTestHelper h) {
    var origin = h.absolutePos(new BlockPos(1, 2, 1));
    factorycraft.diagnostics.AdvancedOilFactory.build(h.getLevel(), origin);
    h.runAfterDelay(
        5000,
        () -> {
          var level = h.getLevel();
          var chest = (ChestBlockEntity) level.getBlockEntity(origin.offset(25, 0, 9));
          int delivered = 0;
          for (int i = 0; i < chest.getContainerSize(); i++)
            if (chest.getItem(i).is(item("battery"))) delivered += chest.getItem(i).getCount();
          h.assertTrue(
              delivered >= 4,
              "full petrochemical line has not delivered batteries: "
                  + delivered
                  + PetroleumGameTests.diagnose(level, origin, 29, 10, 15));
          var heavyTank = (FluidBlockEntity) level.getBlockEntity(origin.offset(20, 0, 1));
          var lightTank = (FluidBlockEntity) level.getBlockEntity(origin.offset(20, 0, 6));
          h.assertTrue(
              heavyTank.cell().amount() > 0
                  && heavyTank.cell().key().equals(new FluidKey(PetroleumFluids.HEAVY.stack(1)))
                  && lightTank.cell().amount() > 0
                  && lightTank.cell().key().equals(new FluidKey(PetroleumFluids.LIGHT.stack(1))),
              "byproduct tanks: heavy=" + heavyTank.stored() + ", light=" + lightTank.stored());
          var counts = new HashMap<String, Long>();
          for (var pos :
              BlockPos.betweenClosed(origin.offset(0, -1, 0), origin.offset(29, 12, 15))) {
            var be = level.getBlockEntity(pos);
            if (be instanceof MachineBlockEntity m) {
              if (m.petroleum() != null)
                for (var cell : m.petroleum().cells)
                  if (cell.amount() > 0)
                    add(
                        counts,
                        BuiltInRegistries.FLUID.getKey(cell.key().stack(1).getFluid()).getPath(),
                        cell.amount());
              for (int i = 0; i < 9; i++) countItem(counts, m.items.getStackInSlot(i));
            } else if (be instanceof FluidBlockEntity fluid && fluid.cell().amount() > 0)
              add(
                  counts,
                  BuiltInRegistries.FLUID.getKey(fluid.cell().key().stack(1).getFluid()).getPath(),
                  fluid.cell().amount());
            else if (be instanceof OilDepositBlockEntity oil)
              add(counts, "crude_oil", oil.remaining());
            else if (be instanceof InserterBlockEntity arm) countItem(counts, arm.held());
            else if (be instanceof ChestBlockEntity container)
              for (int i = 0; i < container.getContainerSize(); i++)
                countItem(counts, container.getItem(i));
          }
          long crude = counts.getOrDefault("crude_oil", 0L),
              gas = counts.getOrDefault("petroleum_gas", 0L),
              sulfur = counts.getOrDefault("sulfur", 0L),
              acid = counts.getOrDefault("sulfuric_acid", 0L),
              battery = counts.getOrDefault("battery", 0L),
              initial = factorycraft.config.FactoryConfig.OIL_RICHNESS.get();
          h.assertTrue(
              110 * initial
                  == 110 * crude + 200 * gas + 3000 * sulfur + 300 * acid + 6000 * battery,
              "crude/gas/sulfur/acid/battery ledger failed: " + counts);
          h.assertTrue(
              (initial - crude) % 100 == 0
                  && counts.getOrDefault("heavy_oil", 0L) == (initial - crude) / 100 * 25
                  && counts.getOrDefault("light_oil", 0L) == (initial - crude) / 100 * 45,
              "refinery byproducts lost or mixed: " + counts);
          long acidBatches = (acid + 20 * battery) / 50;
          h.assertTrue(
              (acid + 20 * battery) % 50 == 0
                  && counts.getOrDefault("iron_plate", 0L) + battery + acidBatches == 24
                  && counts.getOrDefault("copper_plate", 0L) + battery == 8,
              "plate/battery ledger failed: " + counts);
          h.assertTrue(
              level
                  .getEntitiesOfClass(ItemEntity.class, new AABB(origin).expandTowards(29, 12, 15))
                  .isEmpty(),
              "factory leaked loose resources");
          System.out.println(
              "FACTORYCRAFT_PETROCHEMISTRY_LINE: VERIFIED delivered="
                  + delivered
                  + ", ledger="
                  + counts);
          h.succeed();
        });
  }

  private static void add(Map<String, Long> totals, String id, long n) {
    totals.merge(id, n, Long::sum);
  }

  private static void countItem(Map<String, Long> totals, ItemStack stack) {
    if (!stack.isEmpty())
      add(totals, BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount());
  }

  private AdvancedPetroleumGameTests() {}
}
