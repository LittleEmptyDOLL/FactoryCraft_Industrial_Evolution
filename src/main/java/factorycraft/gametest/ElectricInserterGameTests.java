package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.inserters.InserterMenu;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class ElectricInserterGameTests {
  private static final FactoryKind[] KINDS = {
    FactoryKind.FAST_INSERTER, FactoryKind.LONG_INSERTER, FactoryKind.FILTER_INSERTER
  };

  private static ResourceLocation id(String name) {
    return ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, name);
  }

  private static InserterBlockEntity arm(
      GameTestHelper h, FactoryKind kind, BlockPos p, Direction facing) {
    var a = h.absolutePos(p);
    h.getLevel()
        .setBlockAndUpdate(
            a,
            Content.FACTORY
                .get(kind)
                .get()
                .defaultBlockState()
                .setValue(FactoryBlock.FACING, facing));
    return (InserterBlockEntity) h.getLevel().getBlockEntity(a);
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

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void allElectricArmsTransferAtTheirReachAndSpendExactCycleEnergy(GameTestHelper h) {
    var arms = new ArrayList<InserterBlockEntity>();
    var sources = new ArrayList<ChestBlockEntity>();
    var targets = new ArrayList<ChestBlockEntity>();
    for (int i = 0; i < KINDS.length; i++) {
      var a = arm(h, KINDS[i], new BlockPos(4 + i * 7, 3, 5), Direction.NORTH);
      arms.add(a);
      sources.add(chest(h, a.sourcePosition()));
      targets.add(chest(h, a.targetPosition()));
      sources.get(i).setItem(0, new ItemStack(Items.DIAMOND));
    }
    h.runAfterDelay(
        5,
        () -> {
          for (var a : arms) a.power.restore(1000);
        });
    h.runAfterDelay(
        65,
        () -> {
          for (int i = 0; i < arms.size(); i++) {
            var a = arms.get(i);
            h.assertTrue(
                count(sources.get(i)) == 0 && count(targets.get(i)) == 1 && a.held().isEmpty(),
                "electric transfer lost/duplicated an item");
            h.assertTrue(
                a.power.getEnergyStored() == 1000 - a.kind().armSpec().energyPerCycle(),
                "incorrect FE per complete cycle");
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 130)
  public static void powerStarvationFreezesHeldPhaseAndResumesWithoutFreeDelivery(
      GameTestHelper h) {
    var a = arm(h, FactoryKind.FAST_INSERTER, new BlockPos(5, 3, 5), Direction.NORTH);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    source.setItem(0, new ItemStack(Items.EMERALD));
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(count(source) == 1 && a.held().isEmpty(), "unpowered arm extracted item");
          a.power.restore(4);
          a.tick();
          h.assertTrue(
              count(source) == 0
                  && a.held().is(Items.EMERALD)
                  && a.phase() == 3
                  && a.power.getEnergyStored() == 0,
              "pickup ownership/cost failed");
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              a.phase() == 3 && a.held().is(Items.EMERALD) && count(target) == 0,
              "starved hand moved or delivered");
          a.power.receiveEnergy(28, false);
        });
    h.runAfterDelay(
        75,
        () -> {
          h.assertTrue(
              a.held().isEmpty() && count(target) == 1 && a.power.getEnergyStored() == 0,
              "paid resume failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 150)
  public static void outputBackpressureAndDisableKeepHandAndStopBilling(GameTestHelper h) {
    var a = arm(h, FactoryKind.FILTER_INSERTER, new BlockPos(5, 3, 5), Direction.NORTH);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    source.setItem(0, new ItemStack(Items.DIAMOND));
    h.runAfterDelay(
        5,
        () -> {
          a.power.restore(1000);
          a.tick();
          for (int s = 0; s < target.getContainerSize(); s++)
            target.setItem(s, new ItemStack(Items.COBBLESTONE, 64));
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              a.held().is(Items.DIAMOND) && a.phase() == 0 && a.power.getEnergyStored() == 984,
              "blocked arm lost hand or billed idle ticks");
          a.configure(false, false, null);
          target.clearContent();
        });
    h.runAfterDelay(
        55,
        () -> {
          h.assertTrue(
              a.held().is(Items.DIAMOND) && count(target) == 0 && a.power.getEnergyStored() == 984,
              "disabled hand moved");
          a.configure(true, false, null);
        });
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              a.held().isEmpty() && count(target) == 1 && a.power.getEnergyStored() == 968,
              "unblocked final cycle cost changed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void allowDenyFiltersSkipNonmatchingSourceSlotsWithoutConsumingSamples(
      GameTestHelper h) {
    var a = arm(h, FactoryKind.FILTER_INSERTER, new BlockPos(5, 3, 5), Direction.NORTH);
    var source = chest(h, a.sourcePosition());
    var target = chest(h, a.targetPosition());
    source.setItem(0, new ItemStack(Items.RAW_COPPER, 2));
    source.setItem(1, new ItemStack(Items.RAW_IRON, 2));
    h.runAfterDelay(
        5,
        () -> {
          a.configure(true, false, ResourceLocation.withDefaultNamespace("raw_iron"));
          a.power.restore(1000);
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(
              source.getItem(0).getCount() == 2
                  && source.getItem(1).isEmpty()
                  && count(target) == 2
                  && target.getItem(0).is(Items.RAW_IRON),
              "allow-list picked wrong source slot");
          a.configure(true, true, ResourceLocation.withDefaultNamespace("raw_iron"));
        });
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              source.isEmpty() && count(target) == 4 && a.power.getEnergyStored() == 872,
              "deny-list material/FE ledger failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 90)
  public static void ghostMenusValidateTwoViewersAndNeverDispenseHand(GameTestHelper h) {
    var a = arm(h, FactoryKind.FILTER_INSERTER, new BlockPos(5, 3, 5), Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          a.hand.setStackInSlot(0, new ItemStack(Items.EMERALD));
          var p = h.makeMockPlayer(GameType.SURVIVAL);
          var q = h.makeMockPlayer(GameType.SURVIVAL);
          p.setPos(Vec3.atCenterOf(a.getBlockPos()));
          q.setPos(Vec3.atCenterOf(a.getBlockPos()));
          var first = new InserterMenu(1, p.getInventory(), a);
          p.containerMenu = first;
          first.setCarried(new ItemStack(Items.DIAMOND, 3));
          first.clicked(0, 0, ClickType.PICKUP, p);
          var second = new InserterMenu(2, q.getInventory(), a);
          q.containerMenu = second;
          second.setCarried(new ItemStack(Items.REDSTONE, 4));
          second.clicked(0, 0, ClickType.PICKUP, q);
          first.broadcastChanges();
          h.assertTrue(
              a.filter().equals(ResourceLocation.withDefaultNamespace("redstone"))
                  && first.getCarried().getCount() == 3
                  && second.getCarried().getCount() == 4,
              "shared filter consumed real samples");
          for (var mode : ClickType.values()) second.clicked(1, 0, mode, q);
          h.assertTrue(
              a.held().is(Items.EMERALD) && second.getCarried().is(Items.REDSTONE),
              "hand preview leaked owned cargo");
          p.setPos(a.getBlockPos().getX() + 100, 80, a.getBlockPos().getZ());
          h.assertTrue(!first.clickMenuButton(p, 0), "distant menu mutated actuator");
          p.containerMenu = p.inventoryMenu;
          first.clicked(0, 1, ClickType.PICKUP, p);
          h.assertTrue(a.filter() != null, "stale menu cleared filter");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void unloadAndStaleFeCapabilityCannotMoveOrBillAndReconstructionKeepsState(
      GameTestHelper h) {
    var a = arm(h, FactoryKind.LONG_INSERTER, new BlockPos(5, 3, 5), Direction.NORTH);
    chest(h, a.sourcePosition());
    chest(h, a.targetPosition());
    h.runAfterDelay(
        5,
        () -> {
          a.hand.setStackInSlot(0, new ItemStack(Items.DIAMOND));
          a.power.restore(731);
          var port =
              h.getLevel()
                  .getCapability(Capabilities.EnergyStorage.BLOCK, a.getBlockPos(), Direction.UP);
          h.assertTrue(port != null, "missing FE capability");
          a.onChunkUnloaded();
          var saved = a.saveWithFullMetadata(h.getLevel().registryAccess());
          a.tick();
          h.assertTrue(
              port.receiveEnergy(100, false) == 0
                  && a.power.getEnergyStored() == 731
                  && a.held().is(Items.DIAMOND),
              "unloaded cached FE or tick mutated owner");
          var copy =
              (InserterBlockEntity)
                  BlockEntity.loadStatic(
                      a.getBlockPos(), a.getBlockState(), saved, h.getLevel().registryAccess());
          a.setRemoved();
          h.getLevel().setBlockEntity(copy);
          copy.onLoad();
          copy.configure(false, false, null);
          h.assertTrue(
              copy.power.getEnergyStored() == 731 && copy.held().is(Items.DIAMOND),
              "live reconstruction lost owned FE/hand");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void portableDemolitionReturnsOneDeviceWithHandFeAndSettings(GameTestHelper h) {
    var a = arm(h, FactoryKind.FILTER_INSERTER, new BlockPos(5, 3, 5), Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          a.configure(false, true, ResourceLocation.withDefaultNamespace("diamond"));
          a.hand.setStackInSlot(0, new ItemStack(Items.EMERALD));
          a.power.restore(731);
          var port = a.energyPort;
          h.getLevel().destroyBlock(a.getBlockPos(), true);
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new AABB(a.getBlockPos()).inflate(2));
          h.assertTrue(
              drops.size() == 1
                  && drops
                      .getFirst()
                      .getItem()
                      .is(Content.FACTORY.get(FactoryKind.FILTER_INSERTER).get().asItem()),
              "demolition returned copied cargo or extra device");
          var stack = drops.getFirst().getItem().copy();
          drops.getFirst().discard();
          var p = h.makeMockPlayer(GameType.SURVIVAL);
          p.setItemInHand(InteractionHand.MAIN_HAND, stack);
          var at = h.absolutePos(new BlockPos(10, 3, 5));
          h.getLevel().setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
          h.assertTrue(
              p.getMainHandItem()
                  .useOn(
                      new net.minecraft.world.item.context.UseOnContext(
                          p,
                          InteractionHand.MAIN_HAND,
                          new BlockHitResult(
                              Vec3.atCenterOf(at.below()), Direction.UP, at.below(), false)))
                  .consumesAction(),
              "portable arm placement failed");
          var copy = (InserterBlockEntity) h.getLevel().getBlockEntity(at);
          h.assertTrue(
              copy.held().is(Items.EMERALD)
                  && copy.power.getEnergyStored() == 731
                  && !copy.enabled()
                  && copy.blacklist()
                  && copy.filter().equals(ResourceLocation.withDefaultNamespace("diamond"))
                  && port.receiveEnergy(100, false) == 0,
              "portable owner/settings/FE changed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void allDirectionsUseCachedRotatedReachWithoutTouchingAdjacentChests(
      GameTestHelper h) {
    var arms = new ArrayList<InserterBlockEntity>();
    for (int i = 0; i < KINDS.length; i++)
      arms.add(arm(h, KINDS[i], new BlockPos(5 + 8 * i, 3, 8), Direction.NORTH));
    h.runAfterDelay(
        5,
        () -> {
          for (var a : arms)
            for (var direction : Direction.Plane.HORIZONTAL) {
              h.getLevel()
                  .setBlockAndUpdate(
                      a.getBlockPos(), a.getBlockState().setValue(FactoryBlock.FACING, direction));
              var source = chest(h, a.sourcePosition());
              var target = chest(h, a.targetPosition());
              source.setItem(0, new ItemStack(Items.DIAMOND));
              a.power.restore(1000);
              for (int tick = 0; tick < 25; tick++) a.tick();
              h.assertTrue(
                  source.isEmpty() && count(target) == 1 && a.held().isEmpty(),
                  "rotated reach/cache failed: " + a.kind() + "/" + direction);
            }
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void longArmSamplesOnlyTheReachedSplitterPartAndPickupWindow(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(8, 3, 5));
    factorycraft.multiblock.StructurePlacement.place(
        h.getLevel(), p, Content.FACTORY.get(FactoryKind.FAST_SPLITTER).get().defaultBlockState());
    var b = (BeltBlockEntity) h.getLevel().getBlockEntity(p);
    var a = arm(h, FactoryKind.LONG_INSERTER, new BlockPos(9, 3, 7), Direction.SOUTH);
    chest(h, a.targetPosition());
    h.runAfterDelay(
        5,
        () -> {
          a.power.restore(1000);
          b.lane(0).restore(new ItemStack(Items.DIAMOND), 1);
          b.lane(2).restore(new ItemStack(Items.EMERALD), .2);
          a.tick();
          h.assertTrue(
              a.held().isEmpty() && a.power.getEnergyStored() == 1000,
              "arm sampled another track or early position");
          b.lane(2).clear();
          b.lane(2).restore(new ItemStack(Items.EMERALD), 1);
          a.tick();
          h.assertTrue(
              a.held().is(Items.EMERALD) && b.lane(0).carriers().size() == 1,
              "long arm did not pick exact physical part");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 1250)
  public static void polePoweredFiniteExampleMovesAllThreeArmsWithoutSeededFe(GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    factorycraft.diagnostics.InserterFactory.build(h.getLevel(), o);
    h.runAfterDelay(
        1150,
        () -> {
          for (int i = 0; i < 3; i++) {
            var a = (InserterBlockEntity) h.getLevel().getBlockEntity(o.offset(3 + 7 * i, 0, 5));
            var source = (ChestBlockEntity) h.getLevel().getBlockEntity(a.sourcePosition());
            var target = (ChestBlockEntity) h.getLevel().getBlockEntity(a.targetPosition());
            h.assertTrue(
                count(target) == (i == 2 ? 32 : 64)
                    && count(source) == (i == 2 ? 32 : 0)
                    && a.held().isEmpty()
                    && a.power.getEnergyStored() > 0,
                "powered finite actuator line failed: " + i + " output=" + count(target));
            if (i == 2)
              h.assertTrue(source.getItem(1).is(Items.RAW_COPPER), "filter moved rejected copper");
          }
          System.out.println(
              "FACTORYCRAFT_INSERTER_FACTORY: VERIFIED pole-fed fast/long/filter finite"
                  + " inventories");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void gatedAssemblerManufacturesArmFromPlainBaseAndExactComponents(
      GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(5, 3, 5));
    factorycraft.multiblock.StructurePlacement.place(
        h.getLevel(), p, Content.FACTORY.get(FactoryKind.ASSEMBLER).get().defaultBlockState());
    var m = (MachineBlockEntity) h.getLevel().getBlockEntity(p);
    var scope = ResearchGameTests.personal(h, "actuator" + UUID.randomUUID());
    m.bindResearch(scope);
    h.runAfterDelay(
        5,
        () -> {
          m.items.setStackInSlot(0, new ItemStack(Content.FACTORY.get(FactoryKind.INSERTER).get()));
          m.items.setStackInSlot(1, new ItemStack(BuiltInRegistries.ITEM.get(id("iron_gear")), 5));
          m.items.setStackInSlot(
              2, new ItemStack(BuiltInRegistries.ITEM.get(id("electronic_circuit")), 5));
          m.power.restore(2000);
          var selected = m.saveWithoutMetadata(h.getLevel().registryAccess());
          selected.putString("Selected", id("filter_inserter").toString());
          m.loadWithComponents(selected, h.getLevel().registryAccess());
        });
    h.runAfterDelay(
        35,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).isEmpty() && m.power.getEnergyStored() == 2000,
              "locked actuator recipe billed resources");
          var catalog = factorycraft.research.ResearchCatalog.get(h.getLevel());
          var tech = catalog.entry(id("electric_inserters"));
          var ledger = factorycraft.research.ResearchData.get(h.getLevel().getServer()).team(scope);
          var levels = new HashMap<String, Integer>();
          for (var e : catalog.entries())
            if (!e.id().equals(tech.id())) levels.put(e.id().toString(), 1);
          ledger.restore("", levels, Map.of());
          ledger.select(tech.logic());
          for (int n = 0; n < tech.recipe().units(); n++)
            ledger.completeUnit(tech.logic(), () -> {});
        });
    h.runAfterDelay(
        110,
        () -> {
          h.assertTrue(
              m.items
                      .getStackInSlot(4)
                      .is(Content.FACTORY.get(FactoryKind.FILTER_INSERTER).get().asItem())
                  && m.items.getStackInSlot(4).getCount() == 1
                  && m.items.getStackInSlot(0).isEmpty()
                  && m.items.getStackInSlot(1).isEmpty()
                  && m.items.getStackInSlot(2).isEmpty()
                  && m.power.getEnergyStored() == 1360,
              "actuator manufacture material/FE ledger failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 90)
  public static void frozenServerAndUnknownFiltersCannotPerformWork(GameTestHelper h) {
    var a = arm(h, FactoryKind.FILTER_INSERTER, new BlockPos(5, 3, 5), Direction.NORTH);
    var source = chest(h, a.sourcePosition());
    chest(h, a.targetPosition());
    source.setItem(0, new ItemStack(Items.DIAMOND));
    h.runAfterDelay(
        5,
        () -> {
          a.power.restore(1000);
          a.configure(
              true, false, ResourceLocation.fromNamespaceAndPath("missingmod", "retained_filter"));
          for (int i = 0; i < 20; i++) a.tick();
          h.assertTrue(
              a.held().isEmpty() && count(source) == 1 && a.power.getEnergyStored() == 1000,
              "unknown filter failed open");
          a.configure(true, false, null);
          var tick = h.getLevel().getServer().tickRateManager();
          boolean frozen = tick.isFrozen();
          try {
            tick.setFrozen(true);
            tick.tick();
            a.tick();
            h.assertTrue(
                a.held().isEmpty() && count(source) == 1 && a.power.getEnergyStored() == 1000,
                "frozen actuator billed or picked up");
          } finally {
            tick.setFrozen(frozen);
            tick.tick();
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void electricArmsRetainExactOwnedStateAcrossSeparateServerProcesses(
      GameTestHelper h) {
    for (int i = 0; i < KINDS.length; i++) {
      var p = new BlockPos(14015 + i * 32, 70, 14015);
      h.getLevel().getChunkAt(p);
      boolean fresh =
          h.getLevel().getBlockState(p).isAir() && h.getLevel().getBlockEntity(p) == null;
      if (fresh) {
        h.getLevel().setBlockAndUpdate(p, Content.FACTORY.get(KINDS[i]).get().defaultBlockState());
        var a = (InserterBlockEntity) h.getLevel().getBlockEntity(p);
        var t = a.saveWithoutMetadata(h.getLevel().registryAccess());
        var settings = new CompoundTag();
        settings.putInt("Schema", 1);
        settings.putInt("Energy", 731);
        settings.putBoolean("Enabled", false);
        if (i == 2) {
          settings.putBoolean("Blacklist", true);
          settings.putString("Filter", "minecraft:raw_iron");
        }
        t.put("Actuator", settings);
        t.putInt("Phase", 2);
        a.loadWithComponents(t, h.getLevel().registryAccess());
        var item = new ItemStack(Items.DIAMOND);
        var component = new CompoundTag();
        component.putInt("grade", 17);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(component));
        a.hand.setStackInSlot(0, item);
      }
      h.assertTrue(
          h.getLevel().getBlockEntity(p) instanceof InserterBlockEntity,
          "partial actuator checkpoint; refusing refill");
      var a = (InserterBlockEntity) h.getLevel().getBlockEntity(p);
      h.assertTrue(
          a.kind() == KINDS[i]
              && a.phase() == 2
              && a.power.getEnergyStored() == 731
              && !a.enabled()
              && a.held().is(Items.DIAMOND)
              && a.held().getCount() == 1
              && a.held().get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 17,
          "actuator disk ledger/components changed");
      if (i == 2)
        h.assertTrue(
            a.blacklist() && a.filter().equals(ResourceLocation.withDefaultNamespace("raw_iron")),
            "disk filter changed");
      a.onChunkUnloaded();
      h.getLevel().getChunkAt(p).setUnsaved(true);
      System.out.println(
          "FACTORYCRAFT_ACTUATOR_DISK: "
              + (fresh ? "SEEDED " : "VERIFIED ")
              + KINDS[i].id
              + " phase=2 FE=731 typed hand and settings");
    }
    h.succeed();
  }
}
