package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class FurnaceV251GameTests {
  private static Item item(String id) {
    return BuiltInRegistries.ITEM.get(ResourceLocation.parse("factorycraft:" + id));
  }

  private static MachineBlockEntity place(GameTestHelper h, FactoryKind kind, int x) {
    var pos = h.absolutePos(new BlockPos(x, 3, 8));
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(kind).get().defaultBlockState());
    var m = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
    for (var p : m.assembly().profile().cells(pos, Direction.NORTH))
      h.getLevel().getBlockEntity(p).onLoad();
    return m;
  }

  private static void ticks(MachineBlockEntity m, int count) {
    for (int i = 0; i < count; i++) m.tick();
  }

  private static int products(MachineBlockEntity m) {
    int n = 0;
    for (int s = 4; s < 8; s++) n += m.items.getStackInSlot(s).getCount();
    return n;
  }

  @GameTest(template = "empty", batch = "furnace_v0251", timeoutTicks = 100)
  public static void actualFurnaceFamiliesHaveOriginalFootprintsSpeedsAndCapabilities(
      GameTestHelper h) {
    for (var kind :
        List.of(FactoryKind.STONE_FURNACE, FactoryKind.STEEL_FURNACE, FactoryKind.FURNACE)) {
      var m =
          place(
              h,
              kind,
              kind == FactoryKind.STONE_FURNACE ? 5 : kind == FactoryKind.STEEL_FURNACE ? 14 : 24);
      var p = m.assembly().profile();
      h.assertTrue(
          p.layout.width() == (kind.burnerFurnace() ? 2 : 3)
              && p.layout.depth() == (kind.burnerFurnace() ? 2 : 3),
          "incorrect Factorio footprint");
      h.assertTrue(
          m.modules.items.getSlots() == (kind.burnerFurnace() ? 0 : 2),
          "incorrect module inventory");
      if (kind.burnerFurnace()) {
        var energy =
            h.getLevel()
                .getCapability(Capabilities.EnergyStorage.BLOCK, m.getBlockPos(), Direction.UP);
        h.assertTrue(
            energy == null || !energy.canReceive() && energy.receiveEnergy(1000, false) == 0,
            "burner exposed working FE input");
        h.assertTrue(
            m.receiveGridEnergy(1000, false) == 0
                && m.modules.effects() == factorycraft.simulation.modules.ModuleEffects.NONE,
            "burner joined electric/beacon supply");
      }
      var iron =
          m.recipes().stream()
              .filter(r -> r.id().equals(ResourceLocation.parse("factorycraft:iron_plate")))
              .findFirst()
              .orElseThrow()
              .value();
      var steel =
          m.recipes().stream()
              .filter(r -> r.id().equals(ResourceLocation.parse("factorycraft:steel_plate")))
              .findFirst()
              .orElseThrow()
              .value();
      h.assertTrue(iron.ticks() == 64 && steel.ticks() == 320, "base time was machine-specific");
      h.assertTrue(
          m.recipes().stream()
              .anyMatch(r -> r.id().equals(ResourceLocation.parse("factorycraft:stone_brick"))),
          "missing brick smelting");
      m.items.setStackInSlot(0, new ItemStack(item("iron_plate"), 5));
      h.assertTrue(
          m.selectRecipe(ResourceLocation.parse("factorycraft:steel_plate")),
          "steel selection rejected");
      if (kind.burnerFurnace()) m.items.setStackInSlot(8, new ItemStack(Items.COAL));
      else m.power.restore(8000);
      int duration = 320 / kind.craftingSpeed();
      ticks(m, duration - 1);
      h.assertTrue(
          products(m) == 0 && m.data.get(0) == 320 - kind.craftingSpeed(),
          "steel completed at wrong base duration");
      m.tick();
      h.assertTrue(
          products(m) == 1 && m.items.getStackInSlot(0).isEmpty(),
          "five plates did not produce exactly one steel");
      h.assertTrue(
          kind.burnerFurnace()
              ? m.fuelTicks() == 1600 - duration
              : m.power.getEnergyStored() == 8000 - 24 * duration,
          "steel power scaled per craft instead of active tick");
    }
    h.assertTrue(
        !BuiltInRegistries.BLOCK.containsKey(
            ResourceLocation.parse("factorycraft:industrial_furnace")),
        "retired furnace still registered");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "furnace_v0251", timeoutTicks = 100)
  public static void burnersNeedActualFuelAndUseIdenticalFuelPerActiveTick(GameTestHelper h) {
    var stone = place(h, FactoryKind.STONE_FURNACE, 5);
    var steel = place(h, FactoryKind.STEEL_FURNACE, 16);
    for (var m : List.of(stone, steel)) {
      m.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON));
      ticks(m, 80);
      h.assertTrue(m.data.get(0) == 0 && products(m) == 0, "burner moved without fuel");
      m.items.setStackInSlot(8, new ItemStack(Items.COAL));
    }
    ticks(stone, 32);
    ticks(steel, 32);
    h.assertTrue(
        stone.data.get(0) == 32 && products(stone) == 0 && products(steel) == 1,
        "wrong 1:2 crafting speeds");
    h.assertTrue(
        stone.fuelTicks() == 1568 && steel.fuelTicks() == 1568, "burner active powers differ");
    ticks(stone, 32);
    ticks(steel, 32);
    h.assertTrue(
        products(stone) == 1 && stone.fuelTicks() == 1536 && steel.fuelTicks() == 1568,
        "idle burner consumed fuel");
    h.assertTrue(
        stone.power.getEnergyStored() == 0 && steel.power.getEnergyStored() == 0,
        "fuel converted into a fake FE buffer");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "furnace_v0251", timeoutTicks = 100)
  public static void burnerFuelRemainderAndSidedAutomationPreserveActualItems(GameTestHelper h) {
    var m = place(h, FactoryKind.STONE_FURNACE, 5);
    var port = m.automation(Direction.NORTH);
    h.assertTrue(
        !port.isItemValid(8, new ItemStack(Items.DIAMOND))
            && port.insertItem(8, new ItemStack(Items.DIAMOND), false).getCount() == 1,
        "nonfuel accepted");
    h.assertTrue(
        port.insertItem(0, new ItemStack(Items.RAW_IRON), false).isEmpty()
            && port.insertItem(8, new ItemStack(Items.LAVA_BUCKET), false).isEmpty(),
        "valid automated input rejected");
    m.tick();
    h.assertTrue(
        m.fuelTicks() == 19999 && m.items.getStackInSlot(8).is(Items.BUCKET),
        "lava remainder or paid burn lost");
    h.assertTrue(
        port.extractItem(8, 1, true).is(Items.BUCKET) && m.items.getStackInSlot(8).is(Items.BUCKET),
        "SIMULATE consumed remainder");
    h.assertTrue(
        port.extractItem(8, 1, false).is(Items.BUCKET) && m.items.getStackInSlot(8).isEmpty(),
        "remainder cannot be unloaded");
    ticks(m, 63);
    h.assertTrue(products(m) == 1 && m.fuelTicks() == 19936, "lava fuel counted incorrectly");
    h.assertTrue(
        port.extractItem(4, 1, false).is(item("iron_plate")) && products(m) == 0,
        "automated output failed");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "furnace_v0251", timeoutTicks = 100)
  public static void fullOutputAndAbsentInputNeverIgniteOrConsumeFuel(GameTestHelper h) {
    var m = place(h, FactoryKind.STEEL_FURNACE, 5);
    m.items.setStackInSlot(8, new ItemStack(Items.COAL));
    ticks(m, 40);
    h.assertTrue(m.fuelTicks() == 0 && m.items.getStackInSlot(8).getCount() == 1, "idle ignition");
    m.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON));
    for (int s = 4; s < 8; s++) m.items.setStackInSlot(s, new ItemStack(item("iron_plate"), 64));
    ticks(m, 40);
    h.assertTrue(
        m.fuelTicks() == 0 && m.data.get(0) == 0 && products(m) == 256, "blocked burner advanced");
    var carried = m.items.extractItem(4, 64, false);
    ticks(m, 32);
    h.assertTrue(
        carried.getCount() + products(m) == 257
            && m.items.getStackInSlot(0).isEmpty()
            && m.fuelTicks() == 1568,
        "blocked output recovery lost or duplicated items/fuel");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "furnace_v0251", timeoutTicks = 100)
  public static void electricFurnaceSpeedAndPowerComposeWithModulesAndPersistFractions(
      GameTestHelper h) {
    var plain = place(h, FactoryKind.FURNACE, 5);
    plain.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON));
    plain.power.restore(2400);
    ticks(plain, 32);
    h.assertTrue(
        products(plain) == 1 && plain.power.getEnergyStored() == 1632,
        "electric speed/power per active tick incorrect");
    var speed = place(h, FactoryKind.FURNACE, 15);
    speed.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON));
    speed.modules.items.setStackInSlot(0, new ItemStack(item("speed_module")));
    speed.power.restore(2400);
    ticks(speed, 27);
    h.assertTrue(
        products(speed) == 1 && speed.power.getEnergyStored() == 1428,
        "speed module cost or base speed double-applied");
    var efficient = place(h, FactoryKind.FURNACE, 25);
    efficient.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON));
    efficient.modules.items.setStackInSlot(0, new ItemStack(item("efficiency_module")));
    efficient.power.restore(2400);
    ticks(efficient, 9);
    var tag = efficient.saveWithFullMetadata(h.getLevel().registryAccess());
    var copy =
        (MachineBlockEntity)
            BlockEntity.loadStatic(
                efficient.getBlockPos(),
                efficient.getBlockState(),
                tag,
                h.getLevel().registryAccess());
    efficient.setRemoved();
    h.getLevel().setBlockEntity(copy);
    copy.onLoad();
    ticks(copy, 23);
    h.assertTrue(
        products(copy) == 1
            && copy.power.getEnergyStored() == 1863
            && copy.saveWithFullMetadata(h.getLevel().registryAccess())
                    .getInt("FurnaceEnergyRemainder")
                == 6000,
        "fractional FE or interrupted work lost");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "furnace_v0251", timeoutTicks = 100)
  public static void electricityLossPausesExactFractionalWorkAndOutputComponents(GameTestHelper h) {
    var m = place(h, FactoryKind.FURNACE, 5);
    var input = new ItemStack(Items.COBBLESTONE);
    input.set(
        DataComponents.CUSTOM_NAME,
        net.minecraft.network.chat.Component.literal("component-exact source"));
    m.items.setStackInSlot(0, input.copy());
    m.power.restore(24);
    m.selectRecipe(ResourceLocation.parse("minecraft:stone"));
    m.tick();
    int phase = m.data.get(0);
    ticks(m, 10);
    h.assertTrue(
        phase == 2
            && m.data.get(0) == phase
            && ItemStack.isSameItemSameComponents(input, m.items.getStackInSlot(0)),
        "unpaid phase or input mutation");
    m.power.receiveEnergy(2376, false);
    ticks(m, 99);
    h.assertTrue(
        products(m) == 1
            && m.items.getStackInSlot(4).is(Items.STONE)
            && m.items.getStackInSlot(0).isEmpty()
            && m.power.getEnergyStored() == 0,
        "power reconnection lost exact debit or inputs");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "furnace_v0251", timeoutTicks = 100)
  public static void productiveFurnaceCountsActualPaidWorkAndBonusOutput(GameTestHelper h) {
    var m = place(h, FactoryKind.FURNACE, 5);
    m.modules.items.setStackInSlot(0, new ItemStack(item("productivity_module")));
    m.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 25));
    int bank = 40000;
    for (int tick = 0; tick < 843; tick++) {
      int accepted = m.power.receiveEnergy(bank, false);
      bank -= accepted;
      m.tick();
    }
    h.assertTrue(
        products(m) == 26 && m.items.getStackInSlot(0).isEmpty(),
        "productive furnace lost base or bonus batches");
    h.assertTrue(
        40000 - bank - m.power.getEnergyStored() == 28324
            && m.saveWithFullMetadata(h.getLevel().registryAccess())
                    .getInt("FurnaceEnergyRemainder")
                == 8000,
        "productive speed/power ledger incorrect");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "furnace_v0251", timeoutTicks = 100)
  public static void onlyElectricFurnaceReceivesRealPoweredBeaconEffects(GameTestHelper h) {
    var stone = place(h, FactoryKind.STONE_FURNACE, 5);
    var steel = place(h, FactoryKind.STEEL_FURNACE, 13);
    var electric = place(h, FactoryKind.FURNACE, 21);
    var beacons = new ArrayList<BeaconBlockEntity>();
    for (int x : new int[] {9, 25}) {
      var p = h.absolutePos(new BlockPos(x, 3, 8));
      StructurePlacement.place(
          h.getLevel(), p, Content.FACTORY.get(FactoryKind.BEACON).get().defaultBlockState());
      var b = (BeaconBlockEntity) h.getLevel().getBlockEntity(p);
      for (var c : MachineProfile.BEACON.cells(p, Direction.NORTH))
        h.getLevel().getBlockEntity(c).onLoad();
      b.modules.setStackInSlot(0, new ItemStack(item("speed_module")));
      b.power.restore(8000);
      beacons.add(b);
    }
    h.runAfterDelay(
        5,
        () -> {
          for (var b : beacons) b.payForTick();
          h.assertTrue(
              beacons.stream().allMatch(BeaconBlockEntity::broadcasting),
              "fixture beacons not paid");
          h.assertTrue(
              stone.modules.effects() == factorycraft.simulation.modules.ModuleEffects.NONE
                  && steel.modules.effects() == factorycraft.simulation.modules.ModuleEffects.NONE,
              "burners accepted beacon effects");
          h.assertTrue(
              electric.modules.effects().speed() == 13000,
              "electric furnace missing original beacon speed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "furnace_v0251", timeoutTicks = 100)
  public static void burnerItemsPlaceEveryRotationRejectObstaclesAndRetainFuelWhenPacked(
      GameTestHelper h) {
    int index = 0;
    for (var kind : List.of(FactoryKind.STONE_FURNACE, FactoryKind.STEEL_FURNACE))
      for (var direction : Direction.Plane.HORIZONTAL) {
        var pos = h.absolutePos(new BlockPos(4 + (index % 4) * 7, 3, 5 + (index / 4) * 10));
        index++;
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setYRot(direction.getOpposite().toYRot());
        player.setItemInHand(
            net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(Content.FACTORY.get(kind).get(), 2));
        var profile = MachineProfile.byId(kind.id);
        var cells = profile.cells(pos, direction);
        var blocked = cells.getLast();
        h.getLevel().setBlockAndUpdate(blocked, Blocks.DIAMOND_BLOCK.defaultBlockState());
        h.assertTrue(
            !PlacementTestSupport.place(player, pos).consumesAction()
                && player.getMainHandItem().getCount() == 2
                && h.getLevel().getBlockState(pos).isAir(),
            "blocked burner placement was not atomic");
        h.getLevel().removeBlock(blocked, false);
        h.assertTrue(
            PlacementTestSupport.place(player, pos).consumesAction()
                && player.getMainHandItem().getCount() == 1,
            "burner item did not form exactly one structure");
        var m = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
        for (var c : cells) h.getLevel().getBlockEntity(c).onLoad();
        h.assertTrue(
            m.assembly().ready() && cells.size() == (kind == FactoryKind.STONE_FURNACE ? 8 : 12),
            "wrong burner cells/rotation");
        m.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON));
        m.items.setStackInSlot(8, new ItemStack(Items.COAL));
        ticks(m, 9);
        var oldIdentity = m.assembly().identity();
        var cached = m.automation(Direction.UP);
        h.getLevel().destroyBlock(m.assembly().positions().getFirst(), true);
        var drops =
            h.getLevel()
                .getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(3));
        var owners =
            drops.stream()
                .filter(e -> e.getItem().is(Content.FACTORY.get(kind).get().asItem()))
                .toList();
        h.assertTrue(
            owners.size() == 1
                && owners.getFirst().getItem().getCount() == 1
                && drops.stream()
                    .noneMatch(e -> e.getItem().is(Items.COAL) || e.getItem().is(Items.RAW_IRON)),
            "demolition duplicated/lost contents");
        var packed = owners.getFirst().getItem().copy();
        owners.getFirst().discard();
        h.assertTrue(
            cached.extractItem(4, 1, false).isEmpty(),
            "removed controller retained capability access");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, packed);
        h.assertTrue(
            PlacementTestSupport.place(player, pos).consumesAction(),
            "filled burner item did not place");
        var copy = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
        h.assertTrue(
            copy.fuelTicks() == 1591
                && copy.data.get(0) == 9 * kind.craftingSpeed()
                && copy.items.getStackInSlot(0).is(Items.RAW_IRON)
                && !oldIdentity.equals(copy.assembly().identity()),
            "portable state or fresh ownership lost");
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "furnace_v0251", timeoutTicks = 100)
  public static void burnerInterruptedFuelAndElectricEnergyResidueSurviveIndependentJvm(
      GameTestHelper h) {
    var level = h.getLevel();
    int index = 0;
    for (var kind :
        List.of(FactoryKind.STONE_FURNACE, FactoryKind.STEEL_FURNACE, FactoryKind.FURNACE)) {
      var pos = new BlockPos(-1860000 + index++ * 16, 70, -1860000);
      var marker = pos.offset(6, 4, 6);
      var profile = MachineProfile.byId(kind.id);
      for (var p : profile.cells(pos, Direction.NORTH)) level.getChunkAt(p);
      level.getChunkAt(marker);
      if (level.getBlockState(marker).isAir()) {
        h.assertTrue(
            profile.cells(pos, Direction.NORTH).stream()
                .allMatch(p -> level.getBlockState(p).isAir() && level.getBlockEntity(p) == null),
            "partial furnace fixture; refusing reset");
        StructurePlacement.place(level, pos, Content.FACTORY.get(kind).get().defaultBlockState());
        for (var p : profile.cells(pos, Direction.NORTH)) level.getBlockEntity(p).onLoad();
        var m = (MachineBlockEntity) level.getBlockEntity(pos);
        m.items.setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
        m.selectRecipe(ResourceLocation.parse("minecraft:stone"));
        if (kind.burnerFurnace()) m.items.setStackInSlot(8, new ItemStack(Items.LAVA_BUCKET));
        else {
          m.power.restore(2400);
          m.modules.items.setStackInSlot(0, new ItemStack(item("efficiency_module")));
        }
        ticks(m, 9);
        level.setBlockAndUpdate(marker, Blocks.GOLD_BLOCK.defaultBlockState());
        System.out.println("FACTORYCRAFT_FURNACE_251_DISK: CREATED " + kind.id);
      } else System.out.println("FACTORYCRAFT_FURNACE_251_DISK: VERIFYING " + kind.id);
      var m = (MachineBlockEntity) level.getBlockEntity(pos);
      h.assertTrue(
          m.selectedRecipeId().equals("minecraft:stone")
              && m.items.getStackInSlot(0).is(Items.COBBLESTONE)
              && products(m) == 0
              && m.data.get(0) == 9 * kind.craftingSpeed(),
          "saved recipe/input/work changed");
      if (kind.burnerFurnace())
        h.assertTrue(
            m.fuelTicks() == 19991
                && m.items.getStackInSlot(8).is(Items.BUCKET)
                && m.power.getEnergyStored() == 0,
            "saved fuel/remainder changed");
      else
        h.assertTrue(
            m.power.getEnergyStored() == 2249
                && m.saveWithFullMetadata(level.registryAccess()).getInt("FurnaceEnergyRemainder")
                    == 2000,
            "saved fractional FE changed");
      for (var p : profile.cells(pos, Direction.NORTH)) {
        level.getBlockEntity(p).onChunkUnloaded();
        level.getChunkAt(p).setUnsaved(true);
      }
    }
    h.succeed();
  }
}
