package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.energy.*;
import factorycraft.inserters.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.simulation.inserters.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class InserterV21GameTests {
  private static final FactoryKind[] KINDS = {
    FactoryKind.BURNER_INSERTER,
    FactoryKind.INSERTER,
    FactoryKind.LONG_INSERTER,
    FactoryKind.FAST_INSERTER,
    FactoryKind.BULK_INSERTER,
    FactoryKind.STACK_INSERTER
  };

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, path);
  }

  private static ItemStack grade(Item item, int count, int grade) {
    var stack = new ItemStack(item, count);
    var data = new CompoundTag();
    data.putInt("grade", grade);
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    return stack;
  }

  private static ChestBlockEntity chest(GameTestHelper h, BlockPos absolute) {
    h.getLevel().setBlockAndUpdate(absolute, Blocks.CHEST.defaultBlockState());
    return (ChestBlockEntity) h.getLevel().getBlockEntity(absolute);
  }

  private static int count(ChestBlockEntity chest) {
    int n = 0;
    for (int i = 0; i < chest.getContainerSize(); i++) n += chest.getItem(i).getCount();
    return n;
  }

  private static InserterBlockEntity arm(
      GameTestHelper h, FactoryKind type, BlockPos local, Direction face) {
    var p = h.absolutePos(local);
    h.getLevel()
        .setBlockAndUpdate(
            p,
            Content.FACTORY
                .get(type)
                .get()
                .defaultBlockState()
                .setValue(FactoryBlock.FACING, face));
    return (InserterBlockEntity) h.getLevel().getBlockEntity(p);
  }

  private static void research(GameTestHelper h, InserterBlockEntity arm, int capacity, int belt) {
    var scope = ResearchGameTests.personal(h, "arm21-" + UUID.randomUUID());
    var data = factorycraft.research.ResearchData.get(h.getLevel().getServer());
    var levels = new HashMap<String, Integer>();
    for (int i = 1; i <= capacity; i++)
      levels.put(id("inserter_capacity_bonus_" + i).toString(), 1);
    for (int i = 1; i <= belt; i++) levels.put(id("transport_belt_capacity_" + i).toString(), 1);
    data.team(scope).restore("", levels, Map.of());
    data.setDirty();
    arm.bindResearch(scope);
  }

  private static int items(BeltBlockEntity b) {
    int n = 0;
    for (int i = 0; i < b.laneCount(); i++)
      for (var c : b.lane(i).carriers()) n += c.value.getCount();
    return n;
  }

  private static int carriers(BeltBlockEntity b) {
    int n = 0;
    for (int i = 0; i < b.laneCount(); i++) n += b.lane(i).carriers().size();
    return n;
  }

  private static BeltBlockEntity belt(
      GameTestHelper h, FactoryKind type, BlockPos local, Direction face) {
    var p = h.absolutePos(local);
    StructurePlacement.place(
        h.getLevel(),
        p,
        Content.FACTORY.get(type).get().defaultBlockState().setValue(FactoryBlock.FACING, face));
    return (BeltBlockEntity) h.getLevel().getBlockEntity(p);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 140)
  public static void allSixTypesTransferTheirRealBaseHandInAllDirections(GameTestHelper h) {
    var arms = new ArrayList<InserterBlockEntity>();
    var sources = new ArrayList<ChestBlockEntity>();
    var targets = new ArrayList<ChestBlockEntity>();
    int row = 0;
    for (var face : Direction.Plane.HORIZONTAL) {
      for (int i = 0; i < KINDS.length; i++) {
        var a = arm(h, KINDS[i], new BlockPos(3 + i * 5, 3, 3 + row * 5), face);
        arms.add(a);
        sources.add(chest(h, a.sourcePosition()));
        targets.add(chest(h, a.targetPosition()));
      }
      row++;
    }
    h.runAfterDelay(
        5,
        () -> {
          for (int i = 0; i < arms.size(); i++) {
            var a = arms.get(i);
            sources.get(i).setItem(0, grade(Items.DIAMOND, a.kind().armSpec().capacity(), i));
            if (a.burner()) a.fuel.setStackInSlot(0, new ItemStack(Items.COAL));
            else a.power.restore(2000);
          }
        });
    h.runAfterDelay(
        100,
        () -> {
          for (int i = 0; i < arms.size(); i++) {
            var a = arms.get(i);
            h.assertTrue(
                sources.get(i).isEmpty()
                    && a.held().isEmpty()
                    && count(targets.get(i)) == a.kind().armSpec().capacity(),
                "wrong hand/reach/direction " + a.kind());
            var stack = targets.get(i).getItem(0);
            h.assertTrue(
                stack.get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == i,
                "lost exact components");
            h.assertTrue(
                a.burner()
                    ? a.fuelEnergy() == 48000 - 700
                    : a.power.getEnergyStored() == 2000 - a.kind().armSpec().energyPerCycle(),
                "wrong paid cycle " + a.kind());
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 180)
  public static void burnerSelfFuelsOutsideItsFiltersAndNeverMovesUnpaid(GameTestHelper h) {
    var a = arm(h, FactoryKind.BURNER_INSERTER, new BlockPos(5, 3, 5), Direction.EAST);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    source.setItem(0, grade(Items.DIAMOND, 3, 7));
    h.runAfterDelay(
        5, () -> a.configure(true, false, ResourceLocation.withDefaultNamespace("diamond")));
    h.runAfterDelay(
        15,
        () -> {
          h.assertTrue(
              a.held().isEmpty() && count(target) == 0 && a.fuelEnergy() == 0,
              "unfueled burner worked");
          source.setItem(1, grade(Items.COAL, 4, 19));
        });
    h.runAfterDelay(
        140,
        () -> {
          h.assertTrue(
              count(target) == 3
                  && source.getItem(1).getCount() == 3
                  && source.getItem(1).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                      == 19
                  && a.fuelEnergy() == 45900,
              "self fuel/cargo/combustion ledger wrong");
          h.assertTrue(
              !a.fuel.isItemValid(0, new ItemStack(Items.LAVA_BUCKET)),
              "liquid fuel bypassed solid fuel rule");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 120)
  public static void burnerPreservesSubFuelQuantumAndResumesAnIncompleteSwing(GameTestHelper h) {
    var a = arm(h, FactoryKind.BURNER_INSERTER, new BlockPos(5, 3, 5), Direction.EAST);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    source.setItem(0, new ItemStack(Items.DIAMOND));
    h.runAfterDelay(
        5,
        () -> {
          var tag = a.saveWithoutMetadata(h.getLevel().registryAccess());
          tag.putLong("FuelEnergy", 9);
          a.loadWithComponents(tag, h.getLevel().registryAccess());
        });
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              a.held().getCount() == 1
                  && a.phase() == 37
                  && a.fuelEnergy() == 0
                  && target.isEmpty(),
              "burner advanced beyond paid unit");
          var copy =
              (InserterBlockEntity)
                  BlockEntity.loadStatic(
                      a.getBlockPos(),
                      a.getBlockState(),
                      a.saveWithFullMetadata(h.getLevel().registryAccess()),
                      h.getLevel().registryAccess());
          h.assertTrue(
              copy.phase() == 37 && copy.held().getCount() == 1,
              "partial burner phase lost in NBT");
          a.fuel.setStackInSlot(0, new ItemStack(Items.COAL));
        });
    h.runAfterDelay(
        90,
        () -> {
          h.assertTrue(
              a.held().isEmpty() && count(target) == 1 && a.fuelEnergy() == 47309,
              "resumed fuel phase lost energy/items");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 130)
  public static void partialEightItemDeliveryRetainsFiveAcrossUnloadPowerLossAndReconstruction(
      GameTestHelper h) {
    var a = arm(h, FactoryKind.STACK_INSERTER, new BlockPos(6, 3, 6), Direction.EAST);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    var overflow = chest(h, h.absolutePos(new BlockPos(10, 3, 9)));
    var live = new InserterBlockEntity[] {a};
    source.setItem(0, new ItemStack(Items.DIAMOND, 8));
    for (int i = 0; i < target.getContainerSize(); i++)
      target.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    target.setItem(0, new ItemStack(Items.DIAMOND, 61));
    h.runAfterDelay(
        5,
        () -> {
          research(h, a, 7, 2);
          a.configure(true, ItemFilters.empty(), 8);
          a.power.restore(1000);
        });
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              a.held().getCount() == 5 && target.getItem(0).getCount() == 64 && source.isEmpty(),
              "recipient did not accept exactly 3/8");
          h.assertTrue(a.power.getEnergyStored() == 720, "outbound was not exactly paid");
          a.power.spend(720);
          a.onChunkUnloaded();
          var port = a.inventory();
          h.assertTrue(port.getStackInSlot(0).isEmpty(), "unloaded hand exposed capability");
          overflow.setItem(0, target.removeItem(1, 64));
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              a.held().getCount() == 5 && target.getItem(1).isEmpty(), "unloaded remainder moved");
          var copy =
              (InserterBlockEntity)
                  BlockEntity.loadStatic(
                      a.getBlockPos(),
                      a.getBlockState(),
                      a.saveWithFullMetadata(h.getLevel().registryAccess()),
                      h.getLevel().registryAccess());
          h.getLevel().removeBlockEntity(a.getBlockPos());
          h.getLevel().setBlockEntity(copy);
          copy.onLoad();
          live[0] = copy;
        });
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              live[0].held().getCount() == 5 && target.getItem(1).isEmpty(),
              "zero-power reconstructed hand moved");
          live[0].power.receiveEnergy(1000, false);
        });
    h.runAfterDelay(
        90,
        () -> {
          h.assertTrue(
              live[0].held().isEmpty()
                  && count(target) == 26 * 64 + 5
                  && overflow.getItem(0).getCount() == 64,
              "partial remainder lost or duplicated");
          h.assertTrue(
              target.getItem(0).getCount() + target.getItem(1).getCount() == 69
                  && live[0].power.getEnergyStored() == 697,
              "partial item/FE ledger failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 140)
  public static void stackWaitsForFullHandAndFilterChangeFlushesOnlyItsOwnedPartialCargo(
      GameTestHelper h) {
    var a = arm(h, FactoryKind.STACK_INSERTER, new BlockPos(6, 3, 6), Direction.EAST);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    source.setItem(0, grade(Items.DIAMOND, 2, 7));
    source.setItem(1, grade(Items.EMERALD, 3, 8));
    h.runAfterDelay(
        5,
        () -> {
          a.configure(true, false, ResourceLocation.withDefaultNamespace("diamond"));
          a.power.restore(2000);
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              a.held().getCount() == 2 && target.isEmpty() && a.power.getEnergyStored() == 1977,
              "partial Stack did not wait without idle billing");
          a.configure(true, false, ResourceLocation.withDefaultNamespace("emerald"));
        });
    h.runAfterDelay(
        65,
        () -> {
          h.assertTrue(
              target.getItem(0).getCount() == 2
                  && target.getItem(0).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                      == 7
                  && a.held().getCount() == 3,
              "updated filter stranded or erased old hand");
          a.configure(true, new ItemFilters(a.filterIds(), false), 3);
        });
    h.runAfterDelay(
        110,
        () -> {
          h.assertTrue(
              source.isEmpty()
                  && a.held().isEmpty()
                  && count(target) == 5
                  && target.getItem(1).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                      == 8
                  && a.power.getEnergyStored() == 880,
              "partial Stack flush/material/energy ledger failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 110)
  public static void bulkDoesNotMixComponentsAndRespectsUnstackableItemLimit(GameTestHelper h) {
    var a = arm(h, FactoryKind.BULK_INSERTER, new BlockPos(6, 3, 6), Direction.EAST);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    source.setItem(0, grade(Items.DIAMOND, 1, 7));
    source.setItem(1, grade(Items.DIAMOND, 1, 8));
    var unstackable = grade(Items.EMERALD, 1, 9);
    unstackable.set(DataComponents.MAX_STACK_SIZE, 1);
    source.setItem(2, unstackable);
    h.runAfterDelay(
        5,
        () -> {
          research(h, a, 7, 2);
          a.power.restore(2000);
        });
    h.runAfterDelay(
        80,
        () -> {
          h.assertTrue(
              source.isEmpty() && a.held().isEmpty() && count(target) == 3,
              "partial inventory hands did not complete");
          for (int i = 0; i < 3; i++)
            h.assertTrue(
                target.getItem(i).getCount() == 1
                    && target.getItem(i).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                        == 7 + i,
                "incompatible components merged or changed");
          h.assertTrue(target.getItem(2).getMaxStackSize() == 1, "item stack size component lost");
          h.succeed();
        });
  }

  private static void outputToBelt(GameTestHelper h, boolean stack, int height) {
    var a =
        arm(
            h,
            stack ? FactoryKind.STACK_INSERTER : FactoryKind.BULK_INSERTER,
            new BlockPos(5, 3, 5),
            Direction.EAST);
    var source = chest(h, a.sourcePosition());
    var b = belt(h, FactoryKind.BELT, new BlockPos(6, 3, 5), Direction.NORTH);
    int amount = stack ? (height == 2 ? 6 : 12) : 2;
    source.setItem(0, grade(Items.DIAMOND, amount, 17));
    h.runAfterDelay(
        5,
        () -> {
          if (height > 2) research(h, a, 7, height - 2);
          a.configure(true, ItemFilters.empty(), stack ? (height == 2 ? 6 : 12) : 0);
          a.power.restore(2000);
        });
    h.runAfterDelay(
        85,
        () -> {
          h.assertTrue(
              source.isEmpty() && a.held().isEmpty() && items(b) == amount,
              "stacked belt count mismatch");
          h.assertTrue(
              carriers(b) == amount / (stack ? height : 1), "wrong physical carrier count");
          for (int lane = 0; lane < 2; lane++)
            for (var c : b.lane(lane).carriers())
              h.assertTrue(
                  c.value.getCount() == (stack ? height : 1)
                      && c.value.get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 17,
                  "wrong stack height/components");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 110)
  public static void bulkDepositsIndividualSpacedCarriers(GameTestHelper h) {
    outputToBelt(h, false, 1);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 110)
  public static void stackDepositsHeightTwo(GameTestHelper h) {
    outputToBelt(h, true, 2);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 110)
  public static void stackDepositsHeightThree(GameTestHelper h) {
    outputToBelt(h, true, 3);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 110)
  public static void stackDepositsHeightFour(GameTestHelper h) {
    outputToBelt(h, true, 4);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 100)
  public static void partiallyExtractingAStackKeepsItsPositionAndNeverChangesOtherComponents(
      GameTestHelper h) {
    var b = belt(h, FactoryKind.BELT, new BlockPos(6, 3, 6), Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          b.lane(0).restore(grade(Items.DIAMOND, 4, 17), 1);
          b.wakeTransport();
          int position = b.lane(0).carriers().getFirst().positionUnits();
          var port = b.inventory();
          var simulated = port.extractItem(0, 2, true);
          h.assertTrue(
              simulated.getCount() == 2 && items(b) == 4,
              "stack extraction simulation mutated owner");
          var two = port.extractItem(0, 2, false);
          h.assertTrue(
              two.getCount() == 2
                  && items(b) == 2
                  && carriers(b) == 1
                  && b.lane(0).carriers().getFirst().positionUnits() == position,
              "partial extraction moved or removed a physical carrier");
          h.assertTrue(
              two.get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 17,
              "partial extraction lost components");
          var remaining = port.extractItem(0, 64, false);
          h.assertTrue(
              remaining.getCount() == 2 && carriers(b) == 0,
              "final stack extraction duplicated quantity");
          h.succeed();
        });
  }

  private static void stackedSplitter(GameTestHelper h, int tier, int color) {
    var type =
        new FactoryKind[] {
              FactoryKind.SPLITTER, FactoryKind.FAST_SPLITTER, FactoryKind.EXPRESS_SPLITTER
            }
            [tier - 1];
    var beltType =
        new FactoryKind[] {FactoryKind.BELT, FactoryKind.FAST_BELT, FactoryKind.EXPRESS_BELT}
            [tier - 1];
    var split = belt(h, type, new BlockPos(6, 3, 6), Direction.NORTH);
    var left = belt(h, beltType, new BlockPos(6, 3, 5), Direction.NORTH);
    var right = belt(h, beltType, new BlockPos(7, 3, 5), Direction.NORTH);
    var signal = CircuitGameTests.node(h, FactoryKind.CONSTANT, 3, 7, Direction.NORTH);
    var gate = CircuitGameTests.node(h, FactoryKind.CIRCUIT_CONTROLLER, 6, 7, Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          var tag = split.saveWithoutMetadata(h.getLevel().registryAccess());
          tag.put(
              "Splitter",
              new factorycraft.logistics.SplitterSettings(
                      0, 1, ResourceLocation.withDefaultNamespace("diamond"))
                  .save());
          split.loadWithComponents(tag, h.getLevel().registryAccess());
          CircuitGameTests.program(h, signal, 0, 0);
          CircuitGameTests.program(h, gate, 0, 0);
          CircuitGameTests.link(h, signal, 0, gate, 0, color);
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(!split.circuitRunning(), "stacked splitter gate did not close");
          for (int lane = 0; lane < 4; lane++)
            split
                .lane(lane)
                .restore(
                    grade(lane % 2 == 0 ? Items.DIAMOND : Items.COPPER_INGOT, lane + 1, 17), 1);
          split.wakeTransport();
        });
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(
              items(split) == 10 && items(left) + items(right) == 0,
              "closed stacked carriers moved");
          for (int i = 0; i < 4; i++)
            h.assertTrue(
                split.lane(i).carriers().getFirst().positionUnits() == 96,
                "closed stack changed exact position");
          CircuitGameTests.program(h, signal, 0, 1);
        });
    h.runAfterDelay(
        130,
        () -> {
          h.assertTrue(
              items(split) == 0
                  && items(left) + items(right) == 10
                  && carriers(left) + carriers(right) == 4,
              "splitter stack gate lost/duplicated items or carriers");
          for (var out : List.of(left, right))
            for (int i = 0; i < 2; i++)
              for (var c : out.lane(i).carriers())
                h.assertTrue(
                    c.value.get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 17,
                    "splitter lost stack components");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 170)
  public static void basicSplitterRoutesStackedCarriersAndResumesRedGate(GameTestHelper h) {
    stackedSplitter(h, 1, 0);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 170)
  public static void fastSplitterRoutesStackedCarriersAndResumesBlueGate(GameTestHelper h) {
    stackedSplitter(h, 2, 2);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 170)
  public static void expressSplitterRoutesStackedCarriersAndResumesWhiteGate(GameTestHelper h) {
    stackedSplitter(h, 3, 3);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 100)
  public static void allFiveGhostFiltersAndReadonlyHandValidateAllTypesAndDistance(
      GameTestHelper h) {
    var arms = new ArrayList<InserterBlockEntity>();
    for (int i = 0; i < KINDS.length; i++)
      arms.add(arm(h, KINDS[i], new BlockPos(3 + i * 5, 3, 6), Direction.NORTH));
    h.runAfterDelay(
        5,
        () -> {
          for (var a : arms) {
            a.hand.setStackInSlot(0, grade(Items.DIAMOND, 1, 17));
            var p = h.makeMockPlayer(GameType.SURVIVAL);
            p.setPos(a.getBlockPos().getCenter());
            var menu = new InserterMenu(21, p.getInventory(), a);
            p.containerMenu = menu;
            menu.setCarried(grade(Items.EMERALD, 3, 8));
            for (int slot = 0; slot < 5; slot++) menu.clicked(slot, 0, ClickType.PICKUP, p);
            for (var click : ClickType.values()) menu.clicked(5, 0, click, p);
            h.assertTrue(
                menu.getCarried().getCount() == 3
                    && a.held().getCount() == 1
                    && a.filterIds().stream().allMatch(id -> id.equals("minecraft:emerald")),
                "ghost filter/hand dispensed items");
            p.setPos(a.getBlockPos().getCenter().add(100, 0, 0));
            h.assertTrue(!menu.clickMenuButton(p, 0), "distant menu edited arm");
            menu.clicked(0, 1, ClickType.PICKUP, p);
            h.assertTrue(a.filter() != null, "distant filter cleared");
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 1300)
  public static void stackedCargoTraversesCurvesRampsMixedSpeedsUndergroundAndLift(
      GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 2, 1));
    var target =
        factorycraft.diagnostics.TransportFactory.buildStacked(
            h.getLevel(), o, grade(Items.DIAMOND, 12, 17));
    var source = (ChestBlockEntity) h.getLevel().getBlockEntity(o.offset(1, 0, 0));
    var a = (InserterBlockEntity) h.getLevel().getBlockEntity(o.offset(2, 0, 0));
    StructurePlacement.place(
        h.getLevel(),
        o.offset(0, 0, 8),
        Content.FACTORY.get(FactoryKind.GENERATOR).get().defaultBlockState());
    ((MachineBlockEntity) h.getLevel().getBlockEntity(o.offset(0, 0, 8)))
        .items.setStackInSlot(8, new ItemStack(Items.COAL, 2));
    for (var p : List.of(o.offset(1, 0, 7), o.offset(2, 0, 2)))
      StructurePlacement.place(
          h.getLevel(), p, Content.FACTORY.get(FactoryKind.MEDIUM_POLE).get().defaultBlockState());
    h.runAfterDelay(
        1150,
        () -> {
          h.assertTrue(
              source.isEmpty() && a.held().isEmpty() && count(target) == 12,
              "3D stacked route stalled or lost quantity: " + count(target));
          for (int i = 0; i < target.getContainerSize(); i++)
            if (!target.getItem(i).isEmpty())
              h.assertTrue(
                  target.getItem(i).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 17,
                  "3D stack lost components");
          h.assertTrue(
              h.getLevel()
                  .getEntitiesOfClass(ItemEntity.class, new AABB(o).expandTowards(17, 9, 10))
                  .isEmpty(),
              "3D transport used loose entities");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 100)
  public static void allSixPortableDevicesOwnHandFuelEnergyAndPhaseExactlyOnce(GameTestHelper h) {
    var arms = new ArrayList<InserterBlockEntity>();
    for (int i = 0; i < KINDS.length; i++)
      arms.add(arm(h, KINDS[i], new BlockPos(3 + i * 5, 3, 6), Direction.NORTH));
    h.runAfterDelay(
        5,
        () -> {
          for (int i = 0; i < arms.size(); i++) {
            var a = arms.get(i);
            research(h, a, 7, 2);
            a.setReflected(true);
            a.configure(
                false,
                new ItemFilters(
                    List.of(
                        "minecraft:diamond",
                        "minecraft:emerald",
                        "missing:retained",
                        "minecraft:coal",
                        "minecraft:iron_ingot"),
                    true),
                1);
            var tag = a.saveWithoutMetadata(h.getLevel().registryAccess());
            tag.putInt("Stage", 2);
            tag.putInt("Phase", 3);
            tag.putInt("EnergyRemainder", 5);
            tag.putBoolean("DropCredit", false);
            tag.putLong("FuelEnergy", 731);
            tag.putInt("Energy", 891);
            a.loadWithComponents(tag, h.getLevel().registryAccess());
            a.hand.setStackInSlot(0, grade(Items.DIAMOND, i >= 4 ? 5 : 1, 17));
            if (a.burner()) a.fuel.setStackInSlot(0, grade(Items.COAL, 3, 19));
            var p = a.getBlockPos();
            h.getLevel().destroyBlock(p, true);
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(.9));
            h.assertTrue(drops.size() == 1, "portable arm produced multiple/loose drops");
            var item = drops.getFirst().getItem().copy();
            drops.getFirst().discard();
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(p.getCenter());
            player.setYRot(Direction.SOUTH.toYRot());
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
            h.getLevel().setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState());
            h.assertTrue(
                player
                    .getMainHandItem()
                    .useOn(
                        new net.minecraft.world.item.context.UseOnContext(
                            player,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(
                                p.below().getCenter(), Direction.UP, p.below(), false)))
                    .consumesAction(),
                "portable inserter replacement failed");
            var restored = (InserterBlockEntity) h.getLevel().getBlockEntity(p);
            h.assertTrue(
                restored.kind() == a.kind()
                    && restored.reflected()
                    && restored.held().getCount() == (i >= 4 ? 5 : 1)
                    && restored.held().get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                        == 17
                    && restored.stage() == 2
                    && restored.phase() == 3
                    && restored.fuelEnergy() == 731
                    && restored.power.getEnergyStored() == 891
                    && restored.filterIds().equals(a.filterIds())
                    && restored.scope().equals(a.scope())
                    && restored.manualLimit() == 1,
                "portable inserter lost its exact sole-owner state");
            if (restored.burner())
              h.assertTrue(
                  restored.fuel.getStackInSlot(0).getCount() == 3
                      && restored
                              .fuel
                              .getStackInSlot(0)
                              .get(DataComponents.CUSTOM_DATA)
                              .copyTag()
                              .getInt("grade")
                          == 19,
                  "portable fuel components lost");
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 80)
  public static void allSixNewSchemaHandsAndPhysicalStacksPersistAcrossServerProcesses(
      GameTestHelper h) {
    for (int i = 0; i < KINDS.length; i++) {
      var p = new BlockPos(21015 + 32 * i, 70, 21015);
      h.getLevel().getChunkAt(p);
      boolean fresh =
          h.getLevel().getBlockState(p).isAir() && h.getLevel().getBlockEntity(p) == null;
      if (fresh) {
        h.getLevel().setBlockAndUpdate(p, Content.FACTORY.get(KINDS[i]).get().defaultBlockState());
        var a = (InserterBlockEntity) h.getLevel().getBlockEntity(p);
        research(h, a, 7, 2);
        var t = a.saveWithoutMetadata(h.getLevel().registryAccess());
        t.putBoolean("Enabled", false);
        t.putBoolean("Reflected", true);
        t.putInt("Energy", 731);
        t.putLong("FuelEnergy", 1237);
        t.putInt("Stage", 2);
        t.putInt("Phase", 3);
        t.putInt("EnergyRemainder", 5);
        t.putInt("HandLimit", 1);
        t.putBoolean("DropCredit", false);
        t.putBoolean("Blacklist", true);
        var filters = new ListTag();
        for (String filter :
            List.of(
                "minecraft:diamond",
                "minecraft:emerald",
                "missing:retained",
                "minecraft:coal",
                "minecraft:iron_ingot")) filters.add(StringTag.valueOf(filter));
        t.put("Filters", filters);
        a.loadWithComponents(t, h.getLevel().registryAccess());
        a.hand.setStackInSlot(0, grade(Items.DIAMOND, i == 5 ? 13 : i == 4 ? 5 : 1, 17));
        if (a.burner()) a.fuel.setStackInSlot(0, grade(Items.COAL, 3, 19));
      }
      h.assertTrue(
          h.getLevel().getBlockEntity(p) instanceof InserterBlockEntity,
          "partial inserter checkpoint; refusing refill");
      var a = (InserterBlockEntity) h.getLevel().getBlockEntity(p);
      var t = a.saveWithoutMetadata(h.getLevel().registryAccess());
      h.assertTrue(
          a.kind() == KINDS[i]
              && !a.enabled()
              && a.reflected()
              && a.power.getEnergyStored() == 731
              && a.fuelEnergy() == 1237
              && a.stage() == 2
              && a.phase() == 3
              && t.getInt("EnergyRemainder") == 5
              && !t.getBoolean("DropCredit")
              && a.manualLimit() == 1
              && a.researchedMaximum() == (i == 5 ? 16 : i == 4 ? 12 : 4)
              && a.beltStackHeight() == 4
              && a.blacklist()
              && a.filterIds().size() == 5
              && a.filterIds().get(2).equals("missing:retained")
              && a.held().getCount() == (i == 5 ? 13 : i == 4 ? 5 : 1)
              && a.held().get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 17,
          "inserter new-schema disk state changed");
      if (a.burner())
        h.assertTrue(
            a.fuel.getStackInSlot(0).getCount() == 3
                && a.fuel
                        .getStackInSlot(0)
                        .get(DataComponents.CUSTOM_DATA)
                        .copyTag()
                        .getInt("grade")
                    == 19,
            "disk fuel changed");
      a.onChunkUnloaded();
      h.getLevel().getChunkAt(p).setUnsaved(true);
      System.out.println(
          "FACTORYCRAFT_INSERTER_V21_DISK: " + (fresh ? "SEEDED " : "VERIFIED ") + KINDS[i].id);
    }
    for (int height = 2; height <= 4; height++) {
      var p = new BlockPos(21015 + 32 * (height + 5), 70, 21015);
      h.getLevel().getChunkAt(p);
      boolean fresh =
          h.getLevel().getBlockState(p).isAir() && h.getLevel().getBlockEntity(p) == null;
      if (fresh) {
        h.getLevel()
            .setBlockAndUpdate(p, Content.FACTORY.get(FactoryKind.BELT).get().defaultBlockState());
        var b = (BeltBlockEntity) h.getLevel().getBlockEntity(p);
        b.lane(0).restore(grade(Items.DIAMOND, height, 17), .75);
        b.lane(1).restore(grade(Items.EMERALD, height, 19), .5);
      }
      h.assertTrue(
          h.getLevel().getBlockEntity(p) instanceof BeltBlockEntity,
          "partial stacked checkpoint; refusing refill");
      var b = (BeltBlockEntity) h.getLevel().getBlockEntity(p);
      h.assertTrue(
          items(b) == height * 2
              && carriers(b) == 2
              && b.lane(0).carriers().getFirst().positionUnits() == 72
              && b.lane(1).carriers().getFirst().positionUnits() == 48
              && b.lane(0)
                      .carriers()
                      .getFirst()
                      .value
                      .get(DataComponents.CUSTOM_DATA)
                      .copyTag()
                      .getInt("grade")
                  == 17
              && b.lane(1)
                      .carriers()
                      .getFirst()
                      .value
                      .get(DataComponents.CUSTOM_DATA)
                      .copyTag()
                      .getInt("grade")
                  == 19,
          "stack disk position/quantity/components changed");
      b.onChunkUnloaded();
      h.getLevel().getChunkAt(p).setUnsaved(true);
      System.out.println(
          "FACTORYCRAFT_STACK_V21_DISK: " + (fresh ? "SEEDED " : "VERIFIED ") + height);
    }
    h.succeed();
  }

  private static void grid(
      GameTestHelper h, FactoryKind poleKind, int secondX, int armX, int armZ) {
    var a = arm(h, FactoryKind.INSERTER, new BlockPos(armX, 3, armZ), Direction.EAST);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    var genPos = h.absolutePos(new BlockPos(4, 3, 3));
    StructurePlacement.place(
        h.getLevel(), genPos, Content.FACTORY.get(FactoryKind.GENERATOR).get().defaultBlockState());
    var gen = (MachineBlockEntity) h.getLevel().getBlockEntity(genPos);
    gen.items.setStackInSlot(8, new ItemStack(Items.COAL, 2));
    var poles = new ArrayList<PoleBlockEntity>();
    for (int x : new int[] {6, secondX}) {
      var p = h.absolutePos(new BlockPos(x, 3, 6));
      StructurePlacement.place(
          h.getLevel(), p, Content.FACTORY.get(poleKind).get().defaultBlockState());
      poles.add((PoleBlockEntity) h.getLevel().getBlockEntity(p));
    }
    h.runAfterDelay(
        10,
        () -> {
          for (var pole : poles) PowerWires.clear(pole);
          a.power.spend(a.power.getEnergyStored());
          source.setItem(0, grade(Items.DIAMOND, 3, 17));
        });
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              a.power.getEnergyStored() == 0
                  && source.getItem(0).getCount() == 3
                  && target.isEmpty(),
              "unwired supply areas moved cargo");
          h.assertTrue(
              PowerWires.connect(poles.get(0), poles.get(1)), "valid explicit power link refused");
        });
    h.runAfterDelay(
        160,
        () -> {
          h.assertTrue(
              source.isEmpty()
                  && a.held().isEmpty()
                  && count(target) == 3
                  && target.getItem(0).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                      == 17,
              "inserter did not resume through " + poleKind);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 200)
  public static void standardUsesSmallPoleNetworkOnlyWhenExplicitlyWired(GameTestHelper h) {
    grid(h, FactoryKind.POLE, 12, 14, 6);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 200)
  public static void standardUsesMediumPoleNetworkOnlyWhenExplicitlyWired(GameTestHelper h) {
    grid(h, FactoryKind.MEDIUM_POLE, 13, 16, 6);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 200)
  public static void standardUsesBigPoleNetworkOnlyWhenExplicitlyWired(GameTestHelper h) {
    grid(h, FactoryKind.BIG_POLE, 24, 26, 8);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 200)
  public static void standardUsesSubstationNetworkOnlyWhenExplicitlyWired(GameTestHelper h) {
    grid(h, FactoryKind.SUBSTATION, 20, 26, 6);
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 2100)
  public static void severedGridPausesAnAlreadyPaidHandThenReconnectsWithoutCreatingFeOrItems(
      GameTestHelper h) {
    var a = arm(h, FactoryKind.INSERTER, new BlockPos(16, 3, 6), Direction.EAST);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    source.setItem(0, grade(Items.DIAMOND, 64, 17));
    var gp = h.absolutePos(new BlockPos(4, 3, 3));
    StructurePlacement.place(
        h.getLevel(), gp, Content.FACTORY.get(FactoryKind.GENERATOR).get().defaultBlockState());
    ((MachineBlockEntity) h.getLevel().getBlockEntity(gp))
        .items.setStackInSlot(8, new ItemStack(Items.COAL, 2));
    var poles = new ArrayList<PoleBlockEntity>();
    for (int x : new int[] {6, 13}) {
      var p = h.absolutePos(new BlockPos(x, 3, 6));
      StructurePlacement.place(
          h.getLevel(), p, Content.FACTORY.get(FactoryKind.MEDIUM_POLE).get().defaultBlockState());
      poles.add((PoleBlockEntity) h.getLevel().getBlockEntity(p));
    }
    int[] frozen = new int[4];
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(count(target) > 0, "initial grid was not working");
          PowerWires.disconnect(poles.get(0), poles.get(1));
        });
    h.runAfterDelay(
        850,
        () -> {
          h.assertTrue(
              !source.isEmpty() && !a.held().isEmpty(),
              "cut grid lost hand or supplied infinite FE");
          frozen[0] = a.phase();
          frozen[1] = a.stage();
          frozen[2] = a.power.getEnergyStored();
          frozen[3] = count(target);
          h.assertTrue(
              count(source) + a.held().getCount() + count(target) == 64,
              "cut-grid quantity changed");
        });
    h.runAfterDelay(
        930,
        () -> {
          h.assertTrue(
              a.phase() == frozen[0]
                  && a.stage() == frozen[1]
                  && a.power.getEnergyStored() == frozen[2]
                  && count(target) == frozen[3],
              "starved hand moved after wire cut");
          h.assertTrue(PowerWires.connect(poles.get(0), poles.get(1)), "wire reconnect rejected");
        });
    h.runAfterDelay(
        1950,
        () -> {
          h.assertTrue(
              source.isEmpty()
                  && a.held().isEmpty()
                  && count(target) == 64
                  && target.getItem(0).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                      == 17,
              "reconnected line lost its exact cargo");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 750)
  public static void realLabFinalCapacityUnitConsumesPacksAndUpdatesOnlyItsBoundTeam(
      GameTestHelper h) {
    var scope = ResearchGameTests.personal(h, "capacity-final");
    var catalog = factorycraft.research.ResearchCatalog.get(h.getLevel());
    var entry = catalog.entry(id("inserter_capacity_bonus_2"));
    var ledger = factorycraft.research.ResearchData.get(h.getLevel().getServer()).team(scope);
    var levels = new HashMap<String, Integer>();
    levels.put(id("inserter_capacity_bonus_1").toString(), 1);
    ledger.restore(
        entry.id().toString(),
        levels,
        Map.of(
            entry.id().toString(),
            new factorycraft.simulation.research.ResearchLedger.Progress(
                entry.logic().signature(), entry.recipe().units() - 1)));
    var p = h.absolutePos(new BlockPos(4, 3, 4));
    StructurePlacement.place(
        h.getLevel(), p, Content.FACTORY.get(FactoryKind.LAB).get().defaultBlockState());
    var lab = (LabBlockEntity) h.getLevel().getBlockEntity(p);
    lab.bind(scope);
    var ordinary = arm(h, FactoryKind.INSERTER, new BlockPos(10, 3, 4), Direction.NORTH);
    var bulk = arm(h, FactoryKind.BULK_INSERTER, new BlockPos(15, 3, 4), Direction.NORTH);
    var isolated = arm(h, FactoryKind.INSERTER, new BlockPos(20, 3, 4), Direction.NORTH);
    ordinary.bindResearch(scope);
    bulk.bindResearch(scope);
    isolated.bindResearch(ResearchGameTests.personal(h, "capacity-isolated"));
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(
              ordinary.researchedMaximum() == 1 && bulk.researchedMaximum() == 3,
              "pre-research capacity wrong");
          lab.items.setStackInSlot(0, new ItemStack(Content.SCIENCE_RED.get()));
          lab.items.setStackInSlot(1, new ItemStack(Content.SCIENCE_GREEN.get()));
          lab.power.restore(12000);
        });
    h.runAfterDelay(
        670,
        () -> {
          h.assertTrue(
              ledger.level(entry.id().toString()) == 1
                  && lab.items.getStackInSlot(0).isEmpty()
                  && lab.items.getStackInSlot(1).isEmpty()
                  && lab.power.getEnergyStored() == 0,
              "real capacity unit did not conserve packs/FE");
          h.assertTrue(
              ordinary.researchedMaximum() == 2
                  && bulk.researchedMaximum() == 4
                  && isolated.researchedMaximum() == 1,
              "capacity did not respect shared team ownership");
          h.succeed();
        });
  }

  /** A real vanilla capability wrapping a container whose preview disagrees with execution. */
  private static final class PreviewChest extends ChestBlockEntity {
    Runnable afterExtract = () -> {};

    PreviewChest(BlockPos p, net.minecraft.world.level.block.state.BlockState state) {
      super(p, state);
    }

    @Override
    public ItemStack getItem(int slot) {
      var real = super.getItem(slot);
      return slot == 0 && !real.isEmpty() ? grade(real.getItem(), 1, 5) : real;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
      var real = super.removeItem(slot, Math.max(2, amount));
      afterExtract.run();
      return real;
    }
  }

  private static PreviewChest unusualChest(GameTestHelper h, BlockPos position) {
    chest(h, position);
    var state = h.getLevel().getBlockState(position);
    h.getLevel().removeBlockEntity(position);
    var unusual = new PreviewChest(position, state);
    h.getLevel().setBlockEntity(unusual);
    return unusual;
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 90)
  public static void actualExtractionMayExceedPreviewAndKeepsItsActualComponents(GameTestHelper h) {
    var a = arm(h, FactoryKind.BULK_INSERTER, new BlockPos(6, 3, 6), Direction.EAST);
    var source = unusualChest(h, a.sourcePosition());
    source.setItem(0, grade(Items.DIAMOND, 2, 17));
    var target = chest(h, a.targetPosition());
    h.runAfterDelay(5, () -> a.power.restore(1000));
    h.runAfterDelay(
        70,
        () -> {
          h.assertTrue(
              source.isEmpty()
                  && a.held().isEmpty()
                  && target.getItem(0).getCount() == 2
                  && target.getItem(0).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                      == 17,
              "actual extraction was truncated or replaced with preview components");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 90)
  public static void reentrantSourceCallbackDemolishesIntoOnePortableOwnerAfterActualExtraction(
      GameTestHelper h) {
    var a = arm(h, FactoryKind.BULK_INSERTER, new BlockPos(6, 3, 6), Direction.EAST);
    var source = unusualChest(h, a.sourcePosition());
    source.setItem(0, grade(Items.DIAMOND, 2, 17));
    chest(h, a.targetPosition());
    source.afterExtract = () -> h.getLevel().destroyBlock(a.getBlockPos(), true);
    h.runAfterDelay(5, () -> a.power.restore(1000));
    h.runAfterDelay(
        25,
        () -> {
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      ItemEntity.class,
                      new AABB(a.getBlockPos()).inflate(1.5).expandTowards(0, -10, 0));
          h.assertTrue(
              source.isEmpty()
                  && drops.size() == 1
                  && drops
                      .getFirst()
                      .getItem()
                      .is(Content.FACTORY.get(FactoryKind.BULK_INSERTER).get().asItem()),
              "reentrant demolition duplicated device/loose cargo");
          var payload = drops.getFirst().getItem().get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
          var copy =
              (InserterBlockEntity)
                  BlockEntity.loadStatic(
                      a.getBlockPos(), a.getBlockState(), payload, h.getLevel().registryAccess());
          h.assertTrue(
              copy != null
                  && copy.held().getCount() == 2
                  && copy.held().get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 17
                  && copy.power.getEnergyStored() == 989,
              "callback lost extracted cargo or paid energy");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 100)
  public static void changedFilterFlushesAnOwnedPartialStackEvenWhenItsSourceWasRemoved(
      GameTestHelper h) {
    var a = arm(h, FactoryKind.STACK_INSERTER, new BlockPos(6, 3, 6), Direction.EAST);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    source.setItem(0, grade(Items.DIAMOND, 2, 17));
    h.runAfterDelay(5, () -> a.power.restore(1000));
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              a.held().getCount() == 2 && source.isEmpty() && target.isEmpty(),
              "Stack did not gather its partial hand");
          h.getLevel().removeBlock(a.sourcePosition(), false);
          h.assertTrue(
              a.configure(true, false, ResourceLocation.withDefaultNamespace("emerald")),
              "new filter refused");
        });
    h.runAfterDelay(
        80,
        () -> {
          h.assertTrue(
              a.held().isEmpty()
                  && count(target) == 2
                  && target.getItem(0).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                      == 17
                  && a.power.getEnergyStored() == 440,
              "removed source stranded or duplicated the filtered partial hand");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "inserter_v0210", timeoutTicks = 220)
  public static void bulkCollectsPhysicalBeltCarriersSequentiallyBeforeDeliveringItsCountedHand(
      GameTestHelper h) {
    var a = arm(h, FactoryKind.BULK_INSERTER, new BlockPos(6, 3, 6), Direction.EAST);
    var source = belt(h, FactoryKind.BELT, new BlockPos(5, 3, 6), Direction.EAST);
    var target = chest(h, a.targetPosition());
    h.runAfterDelay(
        5,
        () -> {
          research(h, a, 7, 0);
          a.configure(true, ItemFilters.empty(), 4);
          for (int lane = 0; lane < 2; lane++)
            for (int i = 0; i < 4; i++)
              source.lane(lane).restore(grade(Items.DIAMOND, 1, 17), 1 - i * .25);
          source.wakeTransport();
          a.power.restore(2000);
          a.tick();
          h.assertTrue(
              a.held().getCount() == 1 && items(source) == 7 && target.isEmpty(),
              "Bulk teleported multiple physical pickups in one tick");
        });
    h.runAfterDelay(
        180,
        () -> {
          h.assertTrue(
              items(source) == 0
                  && a.held().isEmpty()
                  && count(target) == 8
                  && target.getItem(0).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                      == 17,
              "sequential Bulk pickup lost quantity/components or stalled");
          h.succeed();
        });
  }
}
