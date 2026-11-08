package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.modules.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class ModuleGameTests {
  private static MachineBlockEntity machine(GameTestHelper h, int x) {
    var p = h.absolutePos(new BlockPos(x, 2, 4));
    StructurePlacement.place(
        h.getLevel(), p, Content.FACTORY.get(FactoryKind.ASSEMBLER).get().defaultBlockState());
    return (MachineBlockEntity) h.getLevel().getBlockEntity(p);
  }

  private static Item plate() {
    return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
        ResourceLocation.fromNamespaceAndPath("factorycraft", "iron_plate"));
  }

  private static Item gear() {
    return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
        ResourceLocation.fromNamespaceAndPath("factorycraft", "iron_gear"));
  }

  private static BeaconBlockEntity beacon(GameTestHelper h, int x, int z) {
    var p = h.absolutePos(new BlockPos(x, 2, z));
    StructurePlacement.place(
        h.getLevel(), p, Content.FACTORY.get(FactoryKind.BEACON).get().defaultBlockState());
    return (BeaconBlockEntity) h.getLevel().getBlockEntity(p);
  }

  private static void load(GameTestHelper h, AssemblyOwner owner) {
    for (var p :
        MachineProfile.of(owner.factoryEntity().getBlockState())
            .cells(owner.factoryEntity().getBlockPos(), Direction.NORTH))
      h.getLevel().getBlockEntity(p).onLoad();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void emptyModuleStateKeepsLegacyNbtAndFixedSlots(GameTestHelper h) {
    var m = machine(h, 4);
    var tag = m.saveWithFullMetadata(h.getLevel().registryAccess());
    h.assertTrue(!tag.contains("Modules"), "empty modules changed legacy NBT");
    var copy =
        (MachineBlockEntity)
            BlockEntity.loadStatic(
                m.getBlockPos(), m.getBlockState(), tag, h.getLevel().registryAccess());
    h.assertTrue(copy.modules.items.getSlots() == 2, "legacy load erased fixed module slots");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void speedAndEfficiencyChangeRealProductionAndFe(GameTestHelper h) {
    var speed = machine(h, 3);
    var efficient = machine(h, 10);
    h.runAfterDelay(
        5,
        () -> {
          for (var m : List.of(speed, efficient)) {
            m.items.setStackInSlot(0, new ItemStack(plate(), 2));
            m.power.restore(5000);
          }
          speed.modules.items.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
          efficient.modules.items.setStackInSlot(0, new ItemStack(Content.EFFICIENCY_MODULE.get()));
        });
    h.runAfterDelay(
        65,
        () -> {
          h.assertTrue(
              speed.items.getStackInSlot(4).is(gear())
                  && efficient.items.getStackInSlot(4).is(gear()),
              "modules stopped production");
          var recipe =
              speed.recipes().stream()
                  .filter(r -> r.id().getPath().equals("iron_gear"))
                  .findFirst()
                  .orElseThrow()
                  .value();
          int expectedTicks = Math.ceilDiv(recipe.ticks() * 10000, 12000);
          h.assertTrue(
              speed.power.getEnergyStored()
                  == 5000 - expectedTicks * Math.ceilDiv(recipe.energy() * 15000, 10000),
              "speed FE ledger incorrect");
          h.assertTrue(
              efficient.power.getEnergyStored()
                  == 5000 - recipe.ticks() * Math.ceilDiv(recipe.energy() * 7000, 10000),
              "efficiency FE ledger incorrect");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 1800)
  public static void productivityMakesExactlyOneBonusPerTwentyFiveCycles(GameTestHelper h) {
    var m = machine(h, 4);
    h.runAfterDelay(
        5,
        () -> {
          m.items.setStackInSlot(0, new ItemStack(plate(), 50));
          m.modules.items.setStackInSlot(0, new ItemStack(Content.PRODUCTIVITY_MODULE.get()));
          m.power.restore(100000);
        });
    h.runAfterDelay(
        1450,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(0).isEmpty()
                  && m.items.getStackInSlot(4).is(gear())
                  && m.items.getStackInSlot(4).getCount() == 26
                  && m.modules.credit() == 0,
              "productivity ledger expected 50 plates -> 26 gears");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void moduleMenuRejectsStaleOrUnloadedClicks(GameTestHelper h) {
    var m = machine(h, 4);
    load(h, m);
    var p = h.makeMockPlayer(GameType.SURVIVAL);
    p.setPos(m.getBlockPos().getX(), m.getBlockPos().getY(), m.getBlockPos().getZ());
    var menu = new ModuleMenu(1, p.getInventory(), m);
    p.containerMenu = menu;
    menu.setCarried(new ItemStack(Content.SPEED_MODULE.get()));
    menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, p);
    h.assertTrue(
        m.modules.items.getStackInSlot(0).is(Content.SPEED_MODULE.get()),
        "module slot did not accept module");
    var part = (CasingBlockEntity) h.getLevel().getBlockEntity(m.assembly().positions().getLast());
    part.onChunkUnloaded();
    menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, p);
    h.assertTrue(
        m.modules.items.getStackInSlot(0).is(Content.SPEED_MODULE.get()),
        "unloaded menu extracted module");
    part.onLoad();
    p.containerMenu = p.inventoryMenu;
    menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, p);
    h.assertTrue(
        m.modules.items.getStackInSlot(0).is(Content.SPEED_MODULE.get()),
        "stale menu extracted module");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void poweredBeaconTransmitsAndPartUnloadStopsItImmediately(GameTestHelper h) {
    var m = machine(h, 4);
    var b = beacon(h, 4, 8);
    h.runAfterDelay(
        5,
        () -> {
          b.modules.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
          b.power.restore(400);
        });
    h.runAfterDelay(
        8,
        () -> {
          h.assertTrue(
              m.modules.effects().speed() == 13000 && b.power.getEnergyStored() < 400,
              "beacon did not pay/transmit");
          var part =
              (CasingBlockEntity) h.getLevel().getBlockEntity(b.assembly().positions().getLast());
          part.onChunkUnloaded();
          h.assertTrue(m.modules.effects().speed() == 10000, "unloaded beacon still transmitted");
          part.onLoad();
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              m.modules.effects().speed() == 10000 && b.power.getEnergyStored() == 0,
              "empty beacon kept free effects");
          h.assertTrue(
              !b.modules.isItemValid(0, new ItemStack(Content.PRODUCTIVITY_MODULE.get())),
              "beacon accepts productivity");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void beaconEffectsRespectDistanceAndTwoBeaconAttenuation(GameTestHelper h) {
    var m = machine(h, 4);
    var a = beacon(h, 4, 8);
    var b = beacon(h, 0, 4);
    var far = beacon(h, 18, 18);
    h.runAfterDelay(
        5,
        () -> {
          for (var beacon : List.of(a, b, far)) {
            beacon.modules.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
            beacon.power.restore(4000);
          }
        });
    h.runAfterDelay(
        10,
        () -> {
          h.assertTrue(
              m.modules.effects().speed() == 10000 + (int) Math.floor(4000 * 1.5 / Math.sqrt(2)),
              "beacon range/attenuation incorrect");
          b.assembly().removeWhole(false);
          b.payForTick();
          h.assertTrue(
              h.getLevel().getBlockState(b.getBlockPos()).isAir(),
              "stale beacon billing resurrected a removed block");
          h.assertTrue(
              m.modules.effects().speed() == 13000,
              "removing beacon did not invalidate local recipient");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 150)
  public static void moduleInventoryAndPartialWorkSurviveLiveReconstruction(GameTestHelper h) {
    var m = machine(h, 4);
    h.runAfterDelay(
        5,
        () -> {
          m.modules.items.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
          m.items.setStackInSlot(0, new ItemStack(plate(), 2));
          m.power.restore(5000);
        });
    h.runAfterDelay(
        15,
        () -> {
          var tag = m.saveWithFullMetadata(h.getLevel().registryAccess());
          var copy =
              (MachineBlockEntity)
                  BlockEntity.loadStatic(
                      m.getBlockPos(), m.getBlockState(), tag, h.getLevel().registryAccess());
          m.setRemoved();
          h.getLevel().setBlockEntity(copy);
          copy.onLoad();
        });
    h.runAfterDelay(
        70,
        () -> {
          var copy = (MachineBlockEntity) h.getLevel().getBlockEntity(m.getBlockPos());
          h.assertTrue(
              copy.items.getStackInSlot(4).is(gear())
                  && copy.modules.items.getStackInSlot(0).is(Content.SPEED_MODULE.get()),
              "module reconstruction lost work/items");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void beaconUsesOneItemAndCenteredPreviewInEveryFacing(GameTestHelper h) {
    MachineFootprintGameTests.verify(h, "beacon");
  }

  @GameTest(template = "empty", timeoutTicks = 150)
  public static void fullBonusOutputReservesBothBatchesBeforeEnergy(GameTestHelper h) {
    var m = machine(h, 4);
    h.runAfterDelay(
        5,
        () -> {
          m.modules.items.setStackInSlot(0, new ItemStack(Content.PRODUCTIVITY_MODULE.get()));
          var recipe =
              m.recipes().stream()
                  .filter(r -> r.id().getPath().equals("iron_gear"))
                  .findFirst()
                  .orElseThrow();
          String key = m.modules.identity(recipe);
          m.modules.advance(key, 0, 24, 1, 400);
          m.items.setStackInSlot(0, new ItemStack(plate(), 2));
          for (int slot = 4; slot < 8; slot++)
            m.items.setStackInSlot(slot, new ItemStack(gear(), 64));
          m.items.setStackInSlot(4, new ItemStack(gear(), 63));
          m.power.restore(4000);
        });
    h.runAfterDelay(
        35,
        () -> {
          h.assertTrue(
              m.power.getEnergyStored() == 4000
                  && m.modules.credit() == 9600
                  && m.items.getStackInSlot(0).getCount() == 2,
              "blocked bonus spent FE/input/credit");
          m.items.setStackInSlot(4, new ItemStack(gear(), 62));
        });
    h.runAfterDelay(
        65,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(0).isEmpty()
                  && m.items.getStackInSlot(4).getCount() == 64
                  && m.modules.credit() == 0,
              "bonus did not commit both results");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void creditCannotMoveToADifferentRecipeAndModulesCannotMultiplyThemselves(
      GameTestHelper h) {
    var m = machine(h, 4);
    m.modules.advance("cheap recipe", 0, 24, 1, 400);
    h.assertTrue(
        m.modules.batches("expensive recipe", 400, 1, 1) == 1
            && m.modules.batches("cheap recipe", 400, 1, 1) == 2,
        "productivity credit transferred across definitions");
    h.assertTrue(
        !m.modules.productive(new ItemStack(Content.PRODUCTIVITY_MODULE.get()))
            && !m.modules.productive(new ItemStack(Content.FACTORY.get(FactoryKind.BEACON).get())),
        "productive tag enables module/beacon self multiplication");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 250)
  public static void mixedFluidRecipeReceivesSpeedWithoutLosingAcid(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(4, 2, 4));
    StructurePlacement.place(
        h.getLevel(),
        pos,
        Content.FACTORY.get(FactoryKind.ADVANCED_ASSEMBLER).get().defaultBlockState());
    var m = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
    h.runAfterDelay(
        5,
        () -> {
          m.modules.items.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
          m.items.setStackInSlot(
              0,
              new ItemStack(
                  net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                      ResourceLocation.fromNamespaceAndPath("factorycraft", "electronic_circuit")),
                  20));
          m.items.setStackInSlot(
              1,
              new ItemStack(
                  net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                      ResourceLocation.fromNamespaceAndPath("factorycraft", "advanced_circuit")),
                  2));
          m.petroleum()
              .cells
              .get(0)
              .restore(
                  new factorycraft.fluids.FluidKey(
                      factorycraft.petroleum.PetroleumFluids.ACID.stack(1)),
                  5);
          m.petroleum()
              .selectRecipe(
                  ResourceLocation.fromNamespaceAndPath("factorycraft", "processing_unit"));
          m.power.restore(16000);
        });
    h.runAfterDelay(
        190,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).is(Content.PROCESSING_UNIT.get())
                  && m.items.getStackInSlot(0).isEmpty()
                  && m.petroleum().cells.get(0).amount() == 0
                  && m.power.getEnergyStored() == 5980,
              "mixed module recipe lost item/fluid/FE ledger");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void portableModuleMachineAndBeaconKeepTheSoleInventory(GameTestHelper h) {
    var m = machine(h, 3);
    var b = beacon(h, 10, 4);
    h.runAfterDelay(
        5,
        () -> {
          m.modules.items.setStackInSlot(0, new ItemStack(Content.PRODUCTIVITY_MODULE.get()));
          m.modules.advance("saved recipe", 7500, 1, 1, 400);
          b.modules.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
          b.power.restore(7312);
          for (var owner : List.<AssemblyOwner>of(m, b)) {
            var p = owner.factoryEntity().getBlockPos();
            h.getLevel().destroyBlock(owner.assembly().positions().getLast(), true);
            var drops =
                h.getLevel()
                    .getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(p).inflate(3));
            h.assertTrue(drops.size() == 1, "demolition duplicated module inventory");
            var stack = drops.getFirst().getItem();
            var tag =
                stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag();
            if (owner == m)
              h.assertTrue(
                  tag.getCompound("Modules").getInt("Credit") == 400
                      && tag.getCompound("Modules").getInt("Fraction") == 7500,
                  "portable work credit lost");
            else h.assertTrue(tag.getInt("Energy") == 7312, "portable beacon energy lost");
            var oldId = owner.assembly().identity();
            var portable = stack.copy();
            drops.getFirst().discard();
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setYRot(Direction.SOUTH.toYRot());
            player.setItemInHand(InteractionHand.MAIN_HAND, portable);
            var target = h.absolutePos(new BlockPos(owner == m ? 3 : 10, 2, 12));
            h.assertTrue(
                PlacementTestSupport.place(player, target).consumesAction(),
                "portable module owner could not be replaced");
            var restored = AssemblyAccess.owner(h.getLevel().getBlockEntity(target));
            h.assertTrue(
                restored != null && !oldId.equals(restored.assembly().identity()),
                "portable module owner reused parts");
            if (restored instanceof MachineBlockEntity copy)
              h.assertTrue(
                  copy.modules.credit() == 400
                      && copy.modules.fraction() == 7500
                      && copy.modules.items.getStackInSlot(0).is(Content.PRODUCTIVITY_MODULE.get()),
                  "replaced machine lost module credit");
            else {
              var copy = (BeaconBlockEntity) restored;
              h.assertTrue(
                  copy.power.getEnergyStored() == 7312
                      && copy.modules.getStackInSlot(0).is(Content.SPEED_MODULE.get()),
                  "replaced beacon lost sole state");
            }
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 2400)
  public static void finitePoweredModuleFactoryDeliversExactProductiveAndBeaconResults(
      GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 2, 1));
    factorycraft.diagnostics.ModuleFactory.build(h.getLevel(), o);
    h.runAfterDelay(
        2200,
        () -> {
          int[] expected = {26, 10};
          int index = 0;
          for (int x : new int[] {9, 18}) {
            var chest =
                (net.minecraft.world.level.block.entity.ChestBlockEntity)
                    h.getLevel().getBlockEntity(o.offset(x, 0, 3));
            int total = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++)
              if (chest.getItem(slot).is(gear())) total += chest.getItem(slot).getCount();
            h.assertTrue(
                total == expected[index++], "powered module line delivered wrong output: " + total);
          }
          var beacon = (BeaconBlockEntity) h.getLevel().getBlockEntity(o.offset(12, 0, 6));
          h.assertTrue(beacon.broadcasting(), "real grid failed to keep beacon powered");
          System.out.println(
              "FACTORYCRAFT_MODULE_FACTORY: VERIFIED 50 plates -> 26 gears; 20 plates -> 10 gears"
                  + " with a powered beacon");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void moduleAndBeaconStatePersistAcrossServerProcesses(GameTestHelper h) {
    var p = new BlockPos(815, 70, 815);
    var q = new BlockPos(823, 70, 815);
    var cells = new ArrayList<BlockPos>();
    cells.addAll(MachineProfile.ASSEMBLER.cells(p, Direction.NORTH));
    cells.addAll(MachineProfile.BEACON.cells(q, Direction.NORTH));
    for (var cell : cells) h.getLevel().getChunkAt(cell);
    if (cells.stream().allMatch(cell -> h.getLevel().getBlockEntity(cell) == null)) {
      StructurePlacement.place(
          h.getLevel(), p, Content.FACTORY.get(FactoryKind.ASSEMBLER).get().defaultBlockState());
      StructurePlacement.place(
          h.getLevel(), q, Content.FACTORY.get(FactoryKind.BEACON).get().defaultBlockState());
      var m = (MachineBlockEntity) h.getLevel().getBlockEntity(p);
      var b = (BeaconBlockEntity) h.getLevel().getBlockEntity(q);
      m.modules.items.setStackInSlot(0, new ItemStack(Content.PRODUCTIVITY_MODULE.get()));
      m.modules.advance("checkpoint recipe", 4321, 7, 1, 400);
      m.power.restore(7312);
      b.modules.setStackInSlot(0, new ItemStack(Content.EFFICIENCY_MODULE.get()));
      b.power.restore(6312);
      System.out.println(
          "FACTORYCRAFT_MODULE_DISK_V2: SEEDED sole module inventories, fractional work and credit"
              + " across chunks");
    } else {
      h.assertTrue(
          h.getLevel().getBlockEntity(p) instanceof MachineBlockEntity
              && h.getLevel().getBlockEntity(q) instanceof BeaconBlockEntity,
          "partial module fixture; refusing to reseed");
      var m = (MachineBlockEntity) h.getLevel().getBlockEntity(p);
      var b = (BeaconBlockEntity) h.getLevel().getBlockEntity(q);
      h.assertTrue(
          m.modules.items.getStackInSlot(0).is(Content.PRODUCTIVITY_MODULE.get())
              && m.modules.fraction() == 4321
              && m.modules.credit() == 2800
              && m.power.getEnergyStored() == 7312
              && b.modules.getStackInSlot(0).is(Content.EFFICIENCY_MODULE.get())
              && b.power.getEnergyStored() == 6312,
          "module disk state changed: fraction="
              + m.modules.fraction()
              + " credit="
              + m.modules.credit()
              + " machineFE="
              + m.power.getEnergyStored()
              + " beaconFE="
              + b.power.getEnergyStored()
              + " modules="
              + m.modules.items.getStackInSlot(0)
              + "/"
              + b.modules.getStackInSlot(0));
      for (var owner : List.<AssemblyOwner>of(m, b))
        for (var cell : owner.assembly().positions())
          h.assertTrue(
              h.getLevel().getBlockEntity(cell) instanceof CasingBlockEntity part
                  && part.boundTo(owner.factoryEntity().getBlockPos(), owner.assembly().identity()),
              "module disk part identity changed");
      System.out.println(
          "FACTORYCRAFT_MODULE_DISK_V2: VERIFIED exact inventory, fraction, credit, FE and"
              + " ownership");
    }
    for (var cell : cells) {
      h.getLevel().getBlockEntity(cell).onChunkUnloaded();
      h.getLevel().getChunkAt(cell).setUnsaved(true);
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void lateProductivityInsertionOnlyCreditsRemainingWork(GameTestHelper h) {
    var m = machine(h, 4);
    load(h, m);
    m.items.setStackInSlot(0, new ItemStack(plate(), 2));
    m.power.restore(5000);
    for (int tick = 0; tick < 9; tick++) m.tick();
    m.modules.items.setStackInSlot(0, new ItemStack(Content.PRODUCTIVITY_MODULE.get()));
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).getCount() == 1 && m.modules.credit() == 40,
              "late module insertion received a whole cycle of productivity");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void inactiveLoadedBeaconCannotSpendEnergy(GameTestHelper h) {
    var pos = new BlockPos(9007, 70, 9007);
    var cells = MachineProfile.BEACON.cells(pos, Direction.NORTH);
    for (var cell : cells) h.getLevel().getChunkAt(cell);
    h.assertTrue(
        h.getLevel().getBlockEntity(pos) == null,
        "temporary inactive probe overlaps a saved structure");
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(FactoryKind.BEACON).get().defaultBlockState());
    var b = (BeaconBlockEntity) h.getLevel().getBlockEntity(pos);
    load(h, b);
    b.modules.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE.get()));
    b.power.restore(6312);
    h.assertTrue(
        !h.getLevel().shouldTickBlocksAt(new net.minecraft.world.level.ChunkPos(pos).toLong()),
        "inactive probe chunk unexpectedly ticking");
    for (int tick = 0; tick < 200; tick++) b.payForTick();
    h.assertTrue(
        b.power.getEnergyStored() == 6312 && !b.broadcasting(),
        "inactive loaded beacon consumed energy");
    b.assembly().removeWhole(false);
    h.succeed();
  }

  // This unreleased local v1 probe exposed the pre-tick/inactive-chunk bug. Never refill it.
  // Fresh CI has no v1 probe; v2 above is the strict cross-process checkpoint.
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void retainTheKnownPrevalidationProbeWithoutRefillingIt(GameTestHelper h) {
    var p = new BlockPos(751, 70, 751);
    var q = new BlockPos(759, 70, 751);
    h.getLevel().getChunkAt(p);
    h.getLevel().getChunkAt(q);
    if (h.getLevel().getBlockEntity(q) == null) {
      h.succeed();
      return;
    }
    var b = (BeaconBlockEntity) h.getLevel().getBlockEntity(q);
    var m = (MachineBlockEntity) h.getLevel().getBlockEntity(p);
    h.assertTrue(
        b.power.getEnergyStored() == 32
            && m.power.getEnergyStored() == 7312
            && m.modules.fraction() == 4321
            && m.modules.credit() == 2800,
        "prevalidation failure evidence changed");
    b.onChunkUnloaded();
    m.onChunkUnloaded();
    System.out.println(
        "FACTORYCRAFT_MODULE_V1_EVIDENCE: retained 32 FE after the known prevalidation debit; no"
            + " refill/reset");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 430)
  public static void moduleRecipeWaitsForTeamResearch(GameTestHelper h) {
    var m = machine(h, 4);
    var scope = ResearchGameTests.personal(h, "module-unlock");
    m.bindResearch(scope);
    h.runAfterDelay(
        5,
        () -> {
          var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
          m.items.setStackInSlot(
              0,
              new ItemStack(
                  registry.get(
                      ResourceLocation.fromNamespaceAndPath("factorycraft", "electronic_circuit")),
                  5));
          m.items.setStackInSlot(
              1,
              new ItemStack(
                  registry.get(
                      ResourceLocation.fromNamespaceAndPath("factorycraft", "advanced_circuit")),
                  5));
          m.power.restore(16000);
          for (int n = 0; n <= m.recipes().size(); n++) {
            int index = m.selectedIndex();
            if (index > 0 && m.recipes().get(index - 1).id().getPath().equals("speed_module"))
              break;
            m.cycleRecipe();
          }
        });
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              m.power.getEnergyStored() == 16000 && m.items.getStackInSlot(0).getCount() == 5,
              "locked module recipe consumed resources");
          var data = factorycraft.research.ResearchData.get(h.getLevel().getServer());
          var ledger = data.team(scope);
          var tech =
              factorycraft.research.ResearchCatalog.get(h.getLevel())
                  .entry(ResourceLocation.fromNamespaceAndPath("factorycraft", "modules"))
                  .logic();
          ledger.restore("", Map.of("factorycraft:automation_2", 1), Map.of());
          h.assertTrue(ledger.select(tech), "module prerequisites rejected");
          for (int n = 0; n < tech.units(); n++) ledger.completeUnit(tech, () -> {});
          data.setDirty();
        });
    h.runAfterDelay(
        365,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).is(Content.SPEED_MODULE.get())
                  && m.items.getStackInSlot(0).isEmpty()
                  && m.power.getEnergyStored() == 8800,
              "module unlock/result/FE ledger incorrect");
          h.succeed();
        });
  }
}
