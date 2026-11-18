package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.nuclear.*;
import factorycraft.registry.Content;
import factorycraft.research.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class NuclearV29GameTests {
  static ResourceLocation id(String n) {
    return ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, n);
  }

  static ItemStack item(String n, int count) {
    return NuclearItems.stack(n, count);
  }

  static <T extends FactoryBlockEntity> T place(
      GameTestHelper h, FactoryKind kind, BlockPos at, Direction f) {
    var profile = MachineProfile.byId(kind.id);
    for (var p : profile.cells(at, f)) h.getLevel().getChunkAt(p);
    StructurePlacement.place(
        h.getLevel(),
        at,
        Content.FACTORY.get(kind).get().defaultBlockState().setValue(FactoryBlock.FACING, f));
    for (var p : profile.cells(at, f)) h.getLevel().getBlockEntity(p).onLoad();
    var be = (T) h.getLevel().getBlockEntity(at);
    h.assertTrue(AssemblyAccess.ready(be), "unformed nuclear fixture " + kind);
    return be;
  }

  static int count(MachineBlockEntity m) {
    int count = 0;
    for (int s = 4; s < 8; s++) count += m.items.getStackInSlot(s).getCount();
    return count;
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void allSevenProfilesFormRotateAndHaveSingleResourceOwner(GameTestHelper h) {
    var at = PowerV20GameTests.site(h, 181);
    for (var kind :
        List.of(
            FactoryKind.SOLAR_PANEL,
            FactoryKind.ACCUMULATOR,
            FactoryKind.CENTRIFUGE,
            FactoryKind.NUCLEAR_REACTOR,
            FactoryKind.HEAT_PIPE,
            FactoryKind.HEAT_EXCHANGER,
            FactoryKind.STEAM_TURBINE))
      for (var face : Direction.Plane.HORIZONTAL) {
        var be = place(h, kind, at, face);
        var owner = AssemblyAccess.owner(be);
        for (var p : owner.assembly().positions()) {
          var part = (CasingBlockEntity) h.getLevel().getBlockEntity(p);
          h.assertTrue(part.boundTo(at, owner.assembly().identity()), "wrong part binding");
          var tag = part.saveWithoutMetadata(h.getLevel().registryAccess());
          h.assertTrue(
              !tag.contains("Heat") && !tag.contains("Charge") && !tag.contains("FuelItems"),
              "part duplicated resource state");
        }
        owner.assembly().removeWhole(false);
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void accumulatorExactChargeSimulationAndSidedPartsShareOneLease(GameTestHelper h) {
    ElectricDeviceBlockEntity a =
        place(h, FactoryKind.ACCUMULATOR, PowerV20GameTests.site(h, 182), Direction.NORTH);
    a.battery.restore(123457, Long.MIN_VALUE, 0, 0);
    var part = a.assembly().positions().getFirst();
    var port = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, part, Direction.EAST);
    h.assertTrue(port != null && port.extractEnergy(100, true) == 40, "battery capability/rate");
    h.assertTrue(a.battery.units() == 123457, "simulate mutated charge");
    h.assertTrue(
        a.takeSupply(13) == 13 && port.extractEnergy(100, false) == 39, "grid/cap lease diverged");
    h.assertTrue(a.battery.units() == 122859 && a.takeSupply(100) == 2, "fractional FE lost");
    h.assertTrue(port.receiveEnergy(40, false) == 0, "same-tick charge/discharge ping pong");
    a.assembly().removeWhole(false);
    h.assertTrue(port.extractEnergy(40, false) == 0, "stale owner emitted charge");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void physicalNuclearFluidPortsRotateAndNeverShareHotAndWaterBoxes(
      GameTestHelper h) {
    var at = PowerV20GameTests.site(h, 183);
    for (var face : Direction.Plane.HORIZONTAL) {
      HeatDeviceBlockEntity x = place(h, FactoryKind.HEAT_EXCHANGER, at, face);
      for (var port : x.assembly().ports()) {
        var handler =
            h.getLevel()
                .getCapability(Capabilities.FluidHandler.BLOCK, port.position(), port.side());
        h.assertTrue(handler != null, "physical port missing " + port);
        if (port.port().input() == 0) {
          h.assertTrue(
              handler.fill(
                      new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 17),
                      IFluidHandler.FluidAction.EXECUTE)
                  == 17,
              "water port");
          h.assertTrue(
              handler.fill(
                      new FluidStack(HotSteam.STEAM.get(), 17), IFluidHandler.FluidAction.EXECUTE)
                  == 0,
              "hot steam in water channel");
        } else
          h.assertTrue(
              handler.fill(
                      new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 17),
                      IFluidHandler.FluidAction.EXECUTE)
                  == 0,
              "output port accepted water");
      }
      h.assertTrue(x.water.amount() == 34 && x.steam.amount() == 0, "water/output boxes merged");
      x.assembly().removeWhole(false);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void uraniumRequiresRealAcidAndSpendsExactlyOneHundredPerMinedOre(
      GameTestHelper h) {
    var at = PowerV20GameTests.site(h, 184);
    int offset = 0;
    for (var face : Direction.Plane.HORIZONTAL) {
      MachineBlockEntity d = place(h, FactoryKind.DRILL, at.east(8 * offset++), face);
      var oreAt = d.assembly().profile().center(d.getBlockPos(), face).below();
      h.getLevel()
          .setBlockAndUpdate(
              oreAt,
              Content.RESOURCES
                  .get(factorycraft.resources.ResourceKind.URANIUM)
                  .get()
                  .defaultBlockState());
      var ore = (ResourceDepositBlockEntity) h.getLevel().getBlockEntity(oreAt);
      ore.initializeReserve(3);
      ore.onLoad();
      d.power.restore(16000);
      for (int i = 0; i < 100; i++) d.tick();
      h.assertTrue(
          ore.remaining() == 3 && d.power.getEnergyStored() == 16000,
          "dry uranium work charged/depleted");
      var port = d.assembly().ports().getFirst();
      var cap =
          h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, port.position(), port.side());
      h.assertTrue(
          cap != null
              && cap.fill(
                      factorycraft.petroleum.PetroleumFluids.ACID.stack(100),
                      IFluidHandler.FluidAction.EXECUTE)
                  == 100,
          "rotated acid input");
      for (int i = 0; i < 80; i++) d.tick();
      h.assertTrue(
          ore.remaining() == 2
              && d.miningAcid.amount() == 0
              && count(d) == 1
              && d.power.getEnergyStored() == 15040,
          "finite uranium/acid/FE ledger");
      for (int i = 0; i < 100; i++) d.tick();
      h.assertTrue(
          ore.remaining() == 2 && count(d) == 1 && d.power.getEnergyStored() == 15040,
          "acid-free second ore");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void miningTriggerIsGenuineTeamScopedAndNotAnInventoryOrCreativeShortcut(
      GameTestHelper h) {
    String scope = ResearchGameTests.personal(h, "uranium29");
    ResearchTestSupport.grant(h.getLevel(), scope, "uranium_mining");
    var catalog = ResearchCatalog.get(h.getLevel());
    h.assertTrue(
        !catalog.allowed(scope, id("centrifuge"), h.getLevel()), "centrifuge initially unlocked");
    ResearchTriggers.crafted(h.getLevel(), scope, item("uranium_ore", 1), 1);
    h.assertTrue(
        !catalog.allowed(scope, id("centrifuge"), h.getLevel()), "handcraft bypassed mine trigger");
    MachineBlockEntity d =
        place(h, FactoryKind.DRILL, PowerV20GameTests.site(h, 185), Direction.NORTH);
    d.bindResearch(scope);
    var at = d.assembly().profile().center(d.getBlockPos(), Direction.NORTH).below();
    h.getLevel()
        .setBlockAndUpdate(
            at,
            Content.RESOURCES
                .get(factorycraft.resources.ResourceKind.URANIUM)
                .get()
                .defaultBlockState());
    var ore = (ResourceDepositBlockEntity) h.getLevel().getBlockEntity(at);
    ore.initializeReserve(1);
    ore.onLoad();
    d.power.restore(16000);
    d.miningAcid.restore(new FluidKey(factorycraft.petroleum.PetroleumFluids.ACID.stack(100)), 100);
    for (int i = 0; i < 80; i++) d.tick();
    h.assertTrue(
        catalog.allowed(scope, id("centrifuge"), h.getLevel())
            && catalog.allowed(scope, id("uranium_processing"), h.getLevel()),
        "actual mined uranium did not trigger research");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void centrifugePaidChoiceAndComponentEscrowSurviveNbtAndBlockedOutput(
      GameTestHelper h) {
    MachineBlockEntity m =
        place(h, FactoryKind.CENTRIFUGE, PowerV20GameTests.site(h, 186), Direction.NORTH);
    var ore = item("uranium_ore", 10);
    ore.set(DataComponents.CUSTOM_NAME, Component.literal("owned uranium"));
    m.items.setStackInSlot(0, ore);
    m.power.restore(16000);
    for (int i = 0; i < 17; i++) m.tick();
    h.assertTrue(
        m.uraniumWork.escrow.getCount() == 10 && m.uraniumWork.roll.serial() == 1,
        "paid escrow/RNG");
    var saved = m.saveWithFullMetadata(h.getLevel().registryAccess());
    var copy =
        (MachineBlockEntity)
            BlockEntity.loadStatic(
                m.getBlockPos(), m.getBlockState(), saved, h.getLevel().registryAccess());
    h.assertTrue(
        copy.uraniumWork.roll.pending() == m.uraniumWork.roll.pending()
            && copy.uraniumWork.roll.serial() == 1
            && ItemStack.isSameItemSameComponents(copy.uraniumWork.escrow, ore),
        "RNG/component checkpoint");
    int held = m.power.getEnergyStored();
    m.items.setStackInSlot(4, item("uranium_235", 64));
    for (int i = 0; i < 100; i++) m.tick();
    h.assertTrue(
        m.power.getEnergyStored() == held && m.uraniumWork.roll.serial() == 1,
        "blocked output paid/rerolled");
    m.items.setStackInSlot(4, ItemStack.EMPTY);
    for (int i = 0; i < 223; i++) m.tick();
    h.assertTrue(
        count(m) == 1
            && m.uraniumWork.escrow.isEmpty()
            && m.power.getEnergyStored() == 4800
            && m.uraniumWork.roll.serial() == 1,
        "240 paid ticks / 10 ore / one isotope");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void fuelCellManufacturingKeepsFullSourceBillAndProductCount(GameTestHelper h) {
    MachineBlockEntity m =
        place(h, FactoryKind.ASSEMBLER, PowerV20GameTests.site(h, 187), Direction.NORTH);
    m.items.setStackInSlot(0, item("iron_plate", 10));
    m.items.setStackInSlot(1, item("uranium_235", 1));
    m.items.setStackInSlot(2, item("uranium_238", 19));
    m.selectRecipe(id("uranium_fuel_cell"));
    m.power.restore(16000);
    for (int i = 0; i < 400; i++) m.tick();
    h.assertTrue(
        count(m) == 10
            && m.items.getStackInSlot(0).isEmpty()
            && m.items.getStackInSlot(1).isEmpty()
            && m.items.getStackInSlot(2).isEmpty()
            && m.power.getEnergyStored() == 12000,
        "10+1+19 ->10 fuel cells / speed .5");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void reactorCannotRestartAtCapAndKeepsOneFuelOwnerThroughSave(GameTestHelper h) {
    ReactorBlockEntity r =
        place(h, FactoryKind.NUCLEAR_REACTOR, PowerV20GameTests.site(h, 188), Direction.NORTH);
    r.items.setStackInSlot(0, item("uranium_fuel_cell", 2));
    r.heat.restore(r.heat.maximum);
    r.prepareFuel();
    h.assertTrue(
        r.burnTicks == 0 && r.items.getStackInSlot(0).getCount() == 2, "new fuel burned at cap");
    r.heat.drainAbove(1, 15);
    r.prepareFuel();
    r.snapshotActive();
    r.burnForTick();
    h.assertTrue(
        r.burnTicks == 3999 && r.items.getStackInSlot(0).getCount() == 1 && r.dumped == 79999,
        "active capped reactor refunded fuel/heat");
    var tag = r.saveWithFullMetadata(h.getLevel().registryAccess());
    var c =
        (ReactorBlockEntity)
            BlockEntity.loadStatic(
                r.getBlockPos(), r.getBlockState(), tag, h.getLevel().registryAccess());
    h.assertTrue(
        c.burnTicks == 3999
            && c.heat.units() == r.heat.maximum
            && c.items.getStackInSlot(0).getCount() == 1,
        "reactor restart ownership");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void exchangerConservesWaterHeatAndHotEnthalpyAndColdEngineRejectsIt(
      GameTestHelper h) {
    HeatDeviceBlockEntity x =
        place(h, FactoryKind.HEAT_EXCHANGER, PowerV20GameTests.site(h, 189), Direction.NORTH);
    x.heat.restore(x.heat.maximum);
    var water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 100);
    water.set(DataComponents.CUSTOM_NAME, Component.literal("water ledger"));
    x.water.restore(new FluidKey(water), 100);
    long before = x.heat.units();
    x.tick();
    h.assertTrue(
        x.water.amount() == 49
            && x.steam.amount() == 51
            && (before - x.heat.units()) == 51 * 388L + x.boilingCredit,
        "exchanger enthalpy ledger");
    h.assertTrue(
        Component.literal("water ledger")
            .equals(x.steam.key().stack(1).get(DataComponents.CUSTOM_NAME)),
        "steam components lost");
    x.tick();
    h.assertTrue(x.steam.amount() == 51, "same-tick exchanger replay");
    var engineAt = PowerV20GameTests.site(h, 190);
    for (var cell : MachineProfile.ENGINE.cells(engineAt.south(2), Direction.NORTH))
      h.getLevel().getChunkAt(cell);
    var engine = FinitePowerFixture.place(h.getLevel(), engineAt, Direction.NORTH, 0);
    var handler =
        MachineFluidAccess.port(
            engine,
            engine.assembly().ports().getFirst().position(),
            engine.assembly().ports().getFirst().side());
    h.assertTrue(
        handler != null
            && handler.fill(x.steam.key().stack(50), IFluidHandler.FluidAction.EXECUTE) == 0,
        "500C steam accepted by cold engine");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void turbineCannotUpgradeColdSteamAndSharesQuotaWithExternalFe(GameTestHelper h) {
    var at = PowerV20GameTests.site(h, 191);
    for (boolean hot : new boolean[] {false, true}) {
      ElectricDeviceBlockEntity t = place(h, FactoryKind.STEAM_TURBINE, at, Direction.NORTH);
      var fluid = hot ? HotSteam.STEAM.get() : factorycraft.thermal.SteamFluids.STEAM.get();
      t.steam.restore(new FluidKey(new FluidStack(fluid, 60)), 60);
      t.tick();
      t.tick();
      h.assertTrue(t.steam.amount() == 30, "same tick steam replay");
      int expected = hot ? 776 : 240;
      h.assertTrue(
          t.energy.extractEnergy(10000, true) == expected
              && t.energy.extractEnergy(10000, false) == expected
              && t.energy.extractEnergy(1, false) == 0,
          "turbine enthalpy/lease");
      t.assembly().removeWhole(false);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void sourceCostsAndFutureTechnologyGatesAreNotCompressedOrOpened(GameTestHelper h) {
    var manager = h.getLevel().getRecipeManager();
    var recipe =
        (factorycraft.recipes.FactoryRecipe)
            manager.byKey(id("nuclear_reactor")).orElseThrow().value();
    h.assertTrue(
        recipe.inputs().size() == 4
            && recipe.inputs().stream().allMatch(i -> i.count() == 500)
            && recipe.ticks() == 160,
        "reactor source bill");
    h.assertTrue(manager.byKey(id("craft_nuclear_reactor")).isEmpty(), "fake cheap manual reactor");
    for (var name : List.of("kovarex_enrichment_process", "nuclear_fuel_reprocessing")) {
      var t = ResearchCatalog.get(h.getLevel()).definition(id(name));
      h.assertTrue(
          t != null
              && t.recipe().packs().containsKey(id("production_science_pack"))
              && manager.byKey(id(name)).orElseThrow().value() instanceof TechnologyRecipe,
          "late nuclear gate");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290_solar", timeoutTicks = 240)
  public static void solarChargesOnlySurplusThenBatterySuppliesNightAcrossRealCopperEdges(
      GameTestHelper h) {
    var l = h.getLevel();
    var o = PowerV20GameTests.site(h, 192);
    long originalDay = l.getDayTime();
    var tickets = new HashSet<net.minecraft.world.level.ChunkPos>();
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) {
        var cp = new net.minecraft.world.level.ChunkPos(o.offset(x * 16, 0, z * 16));
        tickets.add(cp);
        l.setChunkForced(cp.x, cp.z, true);
        l.getChunk(cp.x, cp.z);
      }
    ElectricDeviceBlockEntity panel = place(h, FactoryKind.SOLAR_PANEL, o, Direction.NORTH);
    ElectricDeviceBlockEntity battery =
        place(h, FactoryKind.ACCUMULATOR, o.offset(7, 0, 1), Direction.NORTH);
    PoleBlockEntity a = place(h, FactoryKind.POLE, o.offset(3, 0, 3), Direction.NORTH);
    PoleBlockEntity b = place(h, FactoryKind.POLE, o.offset(9, 0, 3), Direction.NORTH);
    MachineBlockEntity load = place(h, FactoryKind.ASSEMBLER, o.offset(10, 0, 5), Direction.NORTH);
    load.power.restore(16000);
    factorycraft.energy.PowerWires.connect(a, b);
    l.setDayTime(6000);
    long[] held = {0};
    // Native chunk promotion is asynchronous. Wait for real, sufficient surplus,
    // never assume 70 wall-scheduled ticks have already generated 100 FE.
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    battery.battery.units() >= 1500 && load.power.getEnergyStored() == 16000,
                    "waiting for real sufficient solar surplus"))
        .thenExecute(
            () -> {
              held[0] = battery.battery.units();
              l.setDayTime(18000);
              load.power.spend(100);
            })
        .thenIdle(20)
        .thenExecute(
            () -> {
              h.assertTrue(
                  load.power.getEnergyStored() == 16000
                      && battery.battery.units() == held[0] - 1500,
                  "night tertiary ledger: " + battery.battery.units() + " initial " + held[0]);
              factorycraft.energy.PowerWires.disconnect(a, b);
              load.power.spend(100);
            })
        .thenIdle(20)
        .thenExecute(
            () -> {
              l.setDayTime(6000);
              held[0] = battery.battery.units();
              load.power.restore(16000);
            })
        .thenIdle(35)
        .thenExecute(
            () -> {
              h.assertTrue(battery.battery.units() == held[0], "unconnected solar charged battery");
              factorycraft.energy.PowerWires.connect(a, b);
              l.setBlockAndUpdate(o.south().above(3), Blocks.STONE.defaultBlockState());
            })
        .thenIdle(30)
        .thenExecute(() -> held[0] = battery.battery.units())
        .thenIdle(30)
        .thenExecute(
            () -> {
              h.assertTrue(
                  battery.battery.units() == held[0],
                  "shaded solar generated after visibility cache expiry");
              l.removeBlock(o.south().above(3), false);
              for (var owner : List.of(panel, battery, a, b, load))
                AssemblyAccess.owner(owner).assembly().removeWhole(false);
              for (var cp : tickets) l.setChunkForced(cp.x, cp.z, false);
              l.setDayTime(originalDay);
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290_disk", timeoutTicks = 100)
  public static void exactNuclearOwnersSurviveIndependentJvmsWithoutReseeding(GameTestHelper h) {
    var l = h.getLevel();
    var base = new BlockPos(2048, 72, 2048);
    var kinds =
        List.of(
            FactoryKind.ACCUMULATOR,
            FactoryKind.CENTRIFUGE,
            FactoryKind.DRILL,
            FactoryKind.NUCLEAR_REACTOR,
            FactoryKind.HEAT_EXCHANGER,
            FactoryKind.STEAM_TURBINE,
            FactoryKind.HEAT_PIPE);
    for (int i = 0; i < kinds.size(); i++) {
      var profile = MachineProfile.byId(kinds.get(i).id);
      for (var cell : profile.cells(base.east(i * 12), Direction.NORTH)) l.getChunkAt(cell);
    }
    boolean fresh = l.getBlockEntity(base) == null;
    var owners = new ArrayList<FactoryBlockEntity>();
    for (int i = 0; i < kinds.size(); i++) {
      var at = base.east(i * 12);
      if (fresh) owners.add(place(h, kinds.get(i), at, Direction.NORTH));
      else {
        h.assertTrue(
            l.getBlockEntity(at) instanceof FactoryBlockEntity,
            "partial nuclear checkpoint; refusing reseed");
        owners.add((FactoryBlockEntity) l.getBlockEntity(at));
      }
    }
    var battery = (ElectricDeviceBlockEntity) owners.get(0);
    var centrifuge = (MachineBlockEntity) owners.get(1);
    var drill = (MachineBlockEntity) owners.get(2);
    var reactor = (ReactorBlockEntity) owners.get(3);
    var exchanger = (HeatDeviceBlockEntity) owners.get(4);
    var turbine = (ElectricDeviceBlockEntity) owners.get(5);
    var pipe = (HeatDeviceBlockEntity) owners.get(6);
    if (fresh) {
      battery.battery.restore(123457, 123, 13, -1);
      centrifuge.power.restore(16000);
      centrifuge.items.setStackInSlot(0, item("uranium_ore", 10));
      centrifuge.uraniumWork.roll.restore(29, 0, -1);
      for (int t = 0; t < 17; t++) centrifuge.tick();
      var acid = factorycraft.petroleum.PetroleumFluids.ACID.stack(137);
      acid.set(DataComponents.CUSTOM_NAME, Component.literal("v0290 acid"));
      drill.miningAcid.restore(new FluidKey(acid), 137);
      reactor.items.setStackInSlot(0, item("uranium_fuel_cell", 2));
      reactor.prepareFuel();
      reactor.snapshotActive();
      reactor.burnForTick();
      exchanger.heat.restore(24000123);
      exchanger.boilingCredit = 17;
      exchanger.water.restore(
          new FluidKey(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 211)), 211);
      exchanger.steam.restore(new FluidKey(new FluidStack(HotSteam.STEAM.get(), 733)), 733);
      turbine.steam.restore(new FluidKey(new FluidStack(HotSteam.STEAM.get(), 419)), 419);
      pipe.heat.restore(14123456);
      for (var owner : owners) owner.setChanged();
    }
    h.assertTrue(
        battery.battery.units() == 123457
            && battery.battery.used() == 13
            && battery.battery.direction() == -1,
        "battery disk ledger");
    h.assertTrue(
        centrifuge.uraniumWork.roll.seed() == 29
            && centrifuge.uraniumWork.roll.serial() == 1
            && centrifuge.uraniumWork.roll.pending()
                == factorycraft.simulation.nuclear.UraniumRoll.outcome(29, 0)
            && centrifuge.uraniumWork.escrow.getCount() == 10
            && centrifuge.power.getEnergyStored() == 15207,
        "centrifuge paid RNG disk ledger");
    h.assertTrue(
        drill.miningAcid.amount() == 137
            && Component.literal("v0290 acid")
                .equals(drill.miningAcid.key().stack(1).get(DataComponents.CUSTOM_NAME)),
        "acid disk ledger");
    h.assertTrue(
        reactor.burnTicks == 3999
            && reactor.heat.units() == 80000
            && reactor.items.getStackInSlot(0).getCount() == 1,
        "reactor fuel/heat disk ledger");
    h.assertTrue(
        exchanger.heat.units() == 24000123
            && exchanger.boilingCredit == 17
            && exchanger.water.amount() == 211
            && exchanger.steam.amount() == 733
            && exchanger.steam.key().stack(1).getFluid() == HotSteam.STEAM.get()
            && turbine.steam.amount() == 419
            && pipe.heat.units() == 14123456,
        "heat/hot steam disk ledger");
    for (var owner : owners) owner.onChunkUnloaded();
    System.out.println(
        "FACTORYCRAFT_V0290_DISK: "
            + (fresh ? "SEEDED" : "VERIFIED")
            + " battery 123457q; centrifuge paid17/roll1; acid137; fuel3999; heat/hot steam exact");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290_heat", timeoutTicks = 100)
  public static void activeCardinalReactorsShareSourceNeighborBonusButNeverFuel(GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 194);
    var reactors = new ArrayList<ReactorBlockEntity>();
    var tickets = new HashSet<net.minecraft.world.level.ChunkPos>();
    for (var at : List.of(o, o.east(5), o.east(5).south(5))) {
      for (var cell : MachineProfile.NUCLEAR_REACTOR.cells(at, Direction.NORTH)) {
        var cp = new net.minecraft.world.level.ChunkPos(cell);
        tickets.add(cp);
        h.getLevel().setChunkForced(cp.x, cp.z, true);
        h.getLevel().getChunk(cp.x, cp.z);
      }
      ReactorBlockEntity r = place(h, FactoryKind.NUCLEAR_REACTOR, at, Direction.NORTH);
      r.items.setStackInSlot(0, item("uranium_fuel_cell", 1));
      reactors.add(r);
    }
    h.runAfterDelay(
        10,
        () -> {
          int i = 0;
          long heat = 0, generated = 0;
          for (var r : reactors) {
            int neighbors = i++ == 1 ? 2 : 1;
            h.assertTrue(
                r.neighbors == neighbors
                    && r.generated > 80000L * (4000 - r.burnTicks)
                    && r.burnTicks < 4000,
                "source neighbor generation/fuel: "
                    + r.neighbors
                    + " "
                    + r.generated
                    + " "
                    + r.burnTicks);
            heat += r.heat.units();
            generated += r.generated;
          }
          h.assertTrue(
              heat == generated, "neighbor bonuses/transfer did not conserve generated heat");
          for (var r : reactors) r.assembly().removeWhole(false);
          for (var cp : tickets) h.getLevel().setChunkForced(cp.x, cp.z, false);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "nuclear_v0290_heat", timeoutTicks = 100)
  public static void boundedLoadedHeatGraphKeepsExactMassWithoutTopologyWorkOnContentChanges(
      GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 195);
    var nodes = new ArrayList<HeatDeviceBlockEntity>();
    var tickets = new HashSet<net.minecraft.world.level.ChunkPos>();
    for (int i = 0; i < 128; i++) {
      var p = o.east(i);
      var cp = new net.minecraft.world.level.ChunkPos(p);
      tickets.add(cp);
      h.getLevel().setChunkForced(cp.x, cp.z, true);
      HeatDeviceBlockEntity pipe = place(h, FactoryKind.HEAT_PIPE, p, Direction.NORTH);
      nodes.add(pipe);
    }
    nodes.getFirst().heat.restore(nodes.getFirst().heat.maximum);
    long total = nodes.getFirst().heat.maximum;
    var before = HeatSimulation.snapshot(h.getLevel());
    h.runAfterDelay(
        30,
        () -> {
          var after = HeatSimulation.snapshot(h.getLevel());
          h.assertTrue(
              nodes.stream().mapToLong(n -> n.heat.units()).sum() == total,
              "heat graph created/lost joules");
          h.assertTrue(
              nodes.get(1).heat.units() > 0 && after.work() - before.work() <= 2048L * 32,
              "loaded contacts/budget");
          System.out.println(
              "FACTORYCRAFT_V0290_HEAT_PROFILE: 128 physical pipes; conserved="
                  + total
                  + "q; edge visits="
                  + (after.work() - before.work())
                  + "; graph CPU ns="
                  + (after.nanos() - before.nanos()));
          for (var node : nodes) node.assembly().removeWhole(false);
          for (var cp : tickets) h.getLevel().setChunkForced(cp.x, cp.z, false);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void fullReactorBillAccumulatesAsNormalStacksAndSurvivesPortableNbt(
      GameTestHelper h) {
    MachineBlockEntity m =
        place(h, FactoryKind.ASSEMBLER, PowerV20GameTests.site(h, 196), Direction.NORTH);
    m.power.restore(16000);
    m.selectRecipe(id("nuclear_reactor"));
    int index = 0;
    for (var name : List.of("concrete", "steel_plate", "advanced_circuit", "copper_plate")) {
      for (int remaining = 500; remaining > 0; ) {
        int count = Math.min(64, remaining);
        var rest =
            net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
                m.items, item(name, count), false);
        h.assertTrue(rest.isEmpty(), "source construction input rejected");
        remaining -= count;
        m.tick();
      }
      if (++index < 4)
        h.assertTrue(m.power.getEnergyStored() == 16000, "incomplete construction charged work");
    }
    int total = 0;
    for (int slot = 0; slot < 32; slot++) {
      var stack = m.construction.escrow.getStackInSlot(slot);
      h.assertTrue(stack.getCount() <= stack.getMaxStackSize(), "oversized saved ItemStack");
      total += stack.getCount();
    }
    h.assertTrue(total == 2000, "source bill compressed or lost");
    var saved = m.saveWithFullMetadata(h.getLevel().registryAccess());
    var copy =
        (MachineBlockEntity)
            BlockEntity.loadStatic(
                m.getBlockPos(), m.getBlockState(), saved, h.getLevel().registryAccess());
    int restored = 0;
    for (int slot = 0; slot < 32; slot++)
      restored += copy.construction.escrow.getStackInSlot(slot).getCount();
    h.assertTrue(restored == 2000, "portable construction escrow");
    for (int i = 0; i < 319; i++) m.tick();
    h.assertTrue(
        count(m) == 1
            && NuclearItems.is(m.items.getStackInSlot(4), "nuclear_reactor")
            && !m.construction.ownsMaterials()
            && m.power.getEnergyStored() == 12800,
        "full source construction/timing/FE");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void filledAccumulatorDropsOnePortableOwnerAndRestoresFractionalCharge(
      GameTestHelper h) {
    var at = PowerV20GameTests.site(h, 197);
    ElectricDeviceBlockEntity a = place(h, FactoryKind.ACCUMULATOR, at, Direction.NORTH);
    a.battery.restore(123457, h.getLevel().getGameTime(), 13, -1);
    var old = a.energy;
    h.getLevel().destroyBlock(a.assembly().positions().getLast(), true);
    var drops =
        h.getLevel()
            .getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(at).inflate(5),
                e -> e.getItem().is(Content.FACTORY.get(FactoryKind.ACCUMULATOR).get().asItem()));
    h.assertTrue(
        drops.size() == 1
            && drops.getFirst().getItem().getCount() == 1
            && old.extractEnergy(40, false) == 0,
        "portable battery duplicated/exposed stale owner");
    var stack = drops.getFirst().getItem();
    h.assertTrue(
        stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTag().getLong("Charge") == 123457,
        "portable fraction lost");
    drops.getFirst().discard();
    var p = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
    p.setPos(at.east(8).getCenter().add(0, 1, 2));
    p.setYRot(Direction.SOUTH.toYRot());
    var target = at.east(8);
    for (var cell : MachineProfile.ACCUMULATOR.cells(target, Direction.NORTH))
      h.getLevel().getChunkAt(cell);
    h.assertTrue(
        PlacementTestSupport.place(p, target).consumesAction() && p.getMainHandItem().isEmpty(),
        "filled battery item placement");
    var restored = (ElectricDeviceBlockEntity) h.getLevel().getBlockEntity(target);
    h.assertTrue(
        restored.battery.units() == 123457 && restored.battery.used() == 13,
        "battery placement reset charge/lease");
    restored.assembly().removeWhole(false);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "nuclear_v0290", timeoutTicks = 100)
  public static void newEnergyOwnersExposeReadOnlyFourColorCircuitDataAndRevokeOnUnload(
      GameTestHelper h) {
    var at = PowerV20GameTests.site(h, 198);
    ElectricDeviceBlockEntity device = place(h, FactoryKind.ACCUMULATOR, at, Direction.NORTH);
    var tickets = new HashSet<net.minecraft.world.level.ChunkPos>();
    for (var p : device.assembly().profile().cells(at, Direction.NORTH)) {
      var cp = new net.minecraft.world.level.ChunkPos(p);
      tickets.add(cp);
      h.getLevel().setChunkForced(cp.x, cp.z, true);
    }
    device.battery.restore(123457, Long.MIN_VALUE, 0, 0);
    var attachment = device.circuit(true);
    var settings = factorycraft.circuits.CircuitSettings.defaults(true).save();
    settings.putBoolean("State", true);
    h.runAfterDelay(
        10,
        () -> {
          h.assertTrue(
              attachment.configure(
                  factorycraft.circuits.CircuitSettings.load(settings), attachment.program()),
              "energy circuit read configuration");
          var reading = factorycraft.circuits.CircuitDevices.read(attachment);
          h.assertTrue(
              reading.ready()
                  && reading.signals().get(factorycraft.simulation.circuits.SignalKey.ENERGY)
                      == 8230
                  && reading.signals().get(factorycraft.simulation.circuits.SignalKey.PROGRESS)
                      == 61,
              "read-only battery circuit state");
          h.assertTrue(
              attachment.settings().inputColors() == 15
                  && attachment.settings().outputColors() == 15
                  && device.battery.units() == 123457,
              "circuit read changed charge/colors");
          device.onChunkUnloaded();
          h.assertTrue(
              !factorycraft.circuits.CircuitDevices.read(attachment).ready(),
              "unloaded circuit leaked resource data");
          for (var cp : tickets) h.getLevel().setChunkForced(cp.x, cp.z, false);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "nuclear_v0290_tickability", timeoutTicks = 100)
  public static void loadedNonTickingReactorPausesFuelAndHeatUntilItsRealChunkTicks(
      GameTestHelper h) {
    var at = PowerV20GameTests.site(h, 199);
    ReactorBlockEntity r = place(h, FactoryKind.NUCLEAR_REACTOR, at, Direction.NORTH);
    r.items.setStackInSlot(0, item("uranium_fuel_cell", 1));
    h.assertTrue(
        !h.getLevel().shouldTickBlocksAt(new net.minecraft.world.level.ChunkPos(at).toLong()),
        "fixture already block-ticking");
    for (int i = 0; i < 8; i++)
      HeatSimulation.tick(
          new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true, h.getLevel()));
    h.assertTrue(
        r.burnTicks == 0 && r.items.getStackInSlot(0).getCount() == 1 && r.heat.units() == 0,
        "loaded non-ticking reactor advanced fuel/heat");
    var tickets = new HashSet<net.minecraft.world.level.ChunkPos>();
    for (var cell : MachineProfile.NUCLEAR_REACTOR.cells(at, Direction.NORTH)) {
      var cp = new net.minecraft.world.level.ChunkPos(cell);
      tickets.add(cp);
      h.getLevel().setChunkForced(cp.x, cp.z, true);
    }
    h.runAfterDelay(
        15,
        () -> {
          h.assertTrue(
              r.simulating()
                  && r.burnTicks > 0
                  && r.burnTicks < 4000
                  && r.heat.units() == (4000L - r.burnTicks) * 80000,
              "reactor did not resume paid burn in ticking chunk");
          r.assembly().removeWhole(false);
          for (var cp : tickets) h.getLevel().setChunkForced(cp.x, cp.z, false);
          h.succeed();
        });
  }

  private NuclearV29GameTests() {}
}
