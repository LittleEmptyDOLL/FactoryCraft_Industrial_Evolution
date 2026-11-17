package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.diagnostics.SteamPlant;
import factorycraft.energy.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.nuclear.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

/**
 * Finite raw stocks only. Real world ticks, physical acid, chutes/arms and two isolated FE grids.
 */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class NuclearV29Chain {
  @GameTest(template = "empty", batch = "nuclear_v0290_chain", timeoutTicks = 34000)
  public static void finiteMinedUraniumBecomesFuelHeatHotSteamAndIsolatedElectricalProduction(
      GameTestHelper h) {
    var l = h.getLevel();
    var o = PowerV20GameTests.site(h, 193);
    var tickets = new ArrayList<net.minecraft.world.level.ChunkPos>();
    for (int x = -2; x <= 4; x++)
      for (int z = -3; z <= 2; z++) {
        var cp = new net.minecraft.world.level.ChunkPos(o.offset(x * 16, 0, z * 16));
        tickets.add(cp);
        l.setChunkForced(cp.x, cp.z, true);
        l.getChunk(cp.x, cp.z);
      }
    MachineBlockEntity centrifuge =
        NuclearV29GameTests.place(h, FactoryKind.CENTRIFUGE, o.offset(1, 0, 0), Direction.NORTH);
    // Stable diagnostic seed, same categorical kernel as normal play; no isotope stock/roll
    // override.
    centrifuge.uraniumWork.roll.restore(817, 0, -1);
    String scope = ResearchGameTests.personal(h, "nuclear29chain");
    ResearchTestSupport.grant(l, scope, "uranium_mining");
    centrifuge.bindResearch(scope);
    var miners = new ArrayList<MachineBlockEntity>();
    var deposits = new ArrayList<ResourceDepositBlockEntity>();
    for (var point :
        List.of(new BlockPos(-1, 0, 1), new BlockPos(3, 0, 1), new BlockPos(1, 0, 3))) {
      var facing =
          point.getX() < 0 ? Direction.EAST : point.getX() > 2 ? Direction.WEST : Direction.NORTH;
      MachineBlockEntity d =
          NuclearV29GameTests.place(h, FactoryKind.DRILL, o.offset(point), facing);
      miners.add(d);
      d.bindResearch(scope);
      var oreAt = d.assembly().profile().center(d.getBlockPos(), facing).below();
      l.setBlockAndUpdate(
          oreAt,
          Content.RESOURCES
              .get(factorycraft.resources.ResourceKind.URANIUM)
              .get()
              .defaultBlockState());
      var ore = (ResourceDepositBlockEntity) l.getBlockEntity(oreAt);
      ore.initializeReserve(300);
      ore.onLoad();
      deposits.add(ore);
      h.assertTrue(
          l.getBlockEntity(d.drillOutputPosition()) instanceof CasingBlockEntity
              || d.drillOutputPosition().equals(centrifuge.getBlockPos()),
          "chute does not meet centrifuge");
    }
    // Six real 250000-mB tanks, each initially 15000 mB acid. No refill or artificial products.
    var tanks = new ArrayList<FluidBlockEntity>();
    for (int i = 0; i < 6; i++) {
      var origin = o.offset(-4 + 4 * i, 8, 10);
      var controller =
          StructurePlacement.fromOrigin(
              l, origin, Content.FLUID_BLOCKS.get(FluidKind.TANK).get().defaultBlockState());
      var tank = (FluidBlockEntity) l.getBlockEntity(controller);
      for (var p : tank.assembly().profile().cells(controller, Direction.NORTH))
        l.getBlockEntity(p).onLoad();
      tank.cell()
          .restore(new FluidKey(factorycraft.petroleum.PetroleumFluids.ACID.stack(15000)), 15000);
      tanks.add(tank);
    }
    var pipePositions = new LinkedHashSet<BlockPos>();
    for (int x = -4; x <= 16; x++) pipePositions.add(o.offset(x, 8, 8));
    for (int z = 1; z <= 8; z++) pipePositions.add(o.offset(1, 8, z));
    for (int x = -4; x <= 6; x++) pipePositions.add(o.offset(x, 8, 1));
    for (int i = 0; i < 6; i++) pipePositions.add(o.offset(-4 + 4 * i, 8, 9));
    for (var d : miners) {
      var port =
          d.assembly().ports().stream()
              .filter(p -> p.port().name().equals("acid_rear"))
              .findFirst()
              .orElseThrow();
      var adjacent = port.adjacent();
      for (int y = 0; y <= 8; y++)
        pipePositions.add(new BlockPos(adjacent.getX(), o.getY() + y, adjacent.getZ()));
    }
    for (var p : pipePositions) {
      h.assertTrue(l.getBlockState(p).isAir(), "acid route obstacle " + p);
      l.setBlockAndUpdate(p, Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
      l.getBlockEntity(p).onLoad();
    }
    MachineBlockEntity fuel =
        NuclearV29GameTests.place(h, FactoryKind.ASSEMBLER, o.offset(1, 0, -4), Direction.NORTH);
    fuel.selectRecipe(NuclearV29GameTests.id("uranium_fuel_cell"));
    fuel.bindResearch(scope);
    fuel.items.setStackInSlot(0, NuclearV29GameTests.item("iron_plate", 10));
    var arms = new ArrayList<InserterBlockEntity>();
    for (int z : new int[] {-1, -5}) {
      var pos = o.offset(1, 0, z);
      l.setBlockAndUpdate(
          pos,
          Content.FACTORY
              .get(FactoryKind.FAST_INSERTER)
              .get()
              .defaultBlockState()
              .setValue(FactoryBlock.FACING, Direction.NORTH));
      var arm = (InserterBlockEntity) l.getBlockEntity(pos);
      arm.onLoad();
      arms.add(arm);
    }
    ReactorBlockEntity reactor =
        NuclearV29GameTests.place(
            h, FactoryKind.NUCLEAR_REACTOR, o.offset(1, 0, -10), Direction.NORTH);
    HeatDeviceBlockEntity heatPipe =
        NuclearV29GameTests.place(h, FactoryKind.HEAT_PIPE, o.offset(1, 0, -11), Direction.NORTH);
    HeatDeviceBlockEntity exchanger =
        NuclearV29GameTests.place(
            h, FactoryKind.HEAT_EXCHANGER, o.offset(1, 0, -13), Direction.NORTH);
    ElectricDeviceBlockEntity turbine =
        NuclearV29GameTests.place(
            h, FactoryKind.STEAM_TURBINE, o.offset(1, 0, -18), Direction.NORTH);
    var waterPort =
        exchanger.assembly().ports().stream()
            .filter(p -> p.port().input() == 0)
            .findFirst()
            .orElseThrow();
    var water =
        l.getCapability(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            waterPort.position(),
            waterPort.side());
    h.assertTrue(
        water.fill(
                new net.neoforged.neoforge.fluids.FluidStack(
                    net.minecraft.world.level.material.Fluids.WATER, 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
            == 1000,
        "finite raw water through physical port");
    MachineBlockEntity output =
        NuclearV29GameTests.place(h, FactoryKind.ASSEMBLER, o.offset(6, 0, -18), Direction.NORTH);
    output.selectRecipe(NuclearV29GameTests.id("iron_gear"));
    output.items.setStackInSlot(0, NuclearV29GameTests.item("iron_plate", 2));
    var poles = new ArrayList<PoleBlockEntity>();
    PoleBlockEntity main =
        NuclearV29GameTests.place(h, FactoryKind.SUBSTATION, o.offset(6, 0, -3), Direction.NORTH);
    poles.add(main);
    PoleBlockEntity bridge =
        NuclearV29GameTests.place(h, FactoryKind.BIG_POLE, o.offset(16, 0, -3), Direction.NORTH);
    poles.add(bridge);
    var boilers = new ArrayList<ThermalBlockEntity>();
    for (int x : new int[] {20, 32, 44}) {
      SteamPlant.buildReal(l, o.offset(x, 0, 0), 64);
      var boiler = (ThermalBlockEntity) l.getBlockEntity(o.offset(x + 1, 4, 7));
      boilers.add(boiler);
      PoleBlockEntity p =
          NuclearV29GameTests.place(
              h, FactoryKind.SUBSTATION, o.offset(x + 5, 4, -1), Direction.NORTH);
      poles.add(p);
    }
    PoleBlockEntity north =
        NuclearV29GameTests.place(h, FactoryKind.SUBSTATION, o.offset(7, 0, -13), Direction.NORTH);
    // Complete initial auto links, then intentionally isolate turbine/load from bootstrap plants.
    for (var p : poles) p.completeAutoConnections();
    north.completeAutoConnections();
    PowerWires.clear(north);
    for (int i = 0; i < poles.size() - 1; i++)
      h.assertTrue(
          PowerWires.connect(poles.get(i), poles.get(i + 1))
              || PowerGrid.hasEdge(poles.get(i), poles.get(i + 1)),
          "bootstrap copper edge");
    float oldRate = l.getServer().tickRateManager().tickrate();
    l.getServer().tickRateManager().setTickRate(1000);
    h.runAfterDelay(
        500,
        () -> {
          h.assertTrue(
              factorycraft.research.ResearchCatalog.get(l)
                  .allowed(scope, NuclearV29GameTests.id("centrifuge"), l),
              "actual mining must trigger uranium processing before nuclear research");
          ResearchTestSupport.grant(l, scope, "nuclear_power");
          h.assertTrue(
              fuel.selectRecipe(NuclearV29GameTests.id("uranium_fuel_cell")),
              "source gated fuel recipe");
        });
    h.runAfterDelay(
        200,
        () ->
            h.assertTrue(
                output.power.getEnergyStored() == 0 && NuclearV29GameTests.count(output) == 0,
                "bootstrap grid leaked into nuclear consumer"));
    h.onEachTick(
        () -> {
          if (l.getGameTime() % 2000 == 0)
            System.out.println(
                "FACTORYCRAFT_V0290_CHAIN_PROGRESS ore="
                    + deposits.stream().mapToInt(ResourceDepositBlockEntity::remaining).sum()
                    + " cycles="
                    + centrifuge.uraniumWork.roll.serial()
                    + " fuel="
                    + reactor.burnTicks
                    + " temperature="
                    + reactor.heat.temperature()
                    + " hot="
                    + turbine.steam.amount()
                    + " result="
                    + NuclearV29GameTests.count(output));
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              deposits.stream().allMatch(d -> d.remaining() == 0)
                  && centrifuge.uraniumWork.roll.serial() == 90
                  && centrifuge.uraniumWork.escrow.isEmpty(),
              "waiting for all 900 finite ores / 90 paid cycles");
          h.assertTrue(
              NuclearV29GameTests.count(output) == 1
                  && reactor.generated > 0
                  && turbine.energy.getEnergyStored() > 0,
              "waiting for real hot steam -> isolated FE -> gear");
          int acid = miners.stream().mapToInt(m -> m.miningAcid.amount()).sum();
          var networks = new HashSet<Long>();
          for (var tank : tanks) {
            var view = FluidSimulation.view(tank);
            if (networks.add(view.id())) acid += (int) view.amount();
          }
          h.assertTrue(acid == 0, "acid not exactly 90000 mB: " + acid);
          h.assertTrue(
              boilers.stream().anyMatch(b -> b.items.getStackInSlot(0).getCount() < 64),
              "bootstrap fuel was not consumed");
          h.assertTrue(
              reactor.items.getStackInSlot(0).getCount()
                      + reactor.items.getStackInSlot(1).getCount()
                      + (reactor.burnTicks > 0 ? 1 : 0)
                      + NuclearV29GameTests.count(fuel)
                      + arms.get(1).held().getCount()
                  == 10,
              "fuel/spent/hand conservation");
          System.out.println(
              "FACTORYCRAFT_V0290_CHAIN: 900 real finite uranium +90000 mB acid; 90 paid"
                  + " categorical cycles(seed817); actual iron+U235+19U238 ->10 cells; fueled"
                  + " reactor ->physical heat pipe ->finite water exchanger ->500C steam ->turbine"
                  + " ->isolated electrical gear; bootstrap <=192 coal, no isotope/FE/hot-steam"
                  + " injection");
          l.getServer().tickRateManager().setTickRate(oldRate);
          for (var cp : tickets) l.setChunkForced(cp.x, cp.z, false);
        });
  }

  private NuclearV29Chain() {}
}
