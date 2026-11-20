package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.rail.automation.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class RailAutomationGameTests {
  static RailAutomationData.Device device(
      GameTestHelper h,
      TrainV31GameTests.Site site,
      TracksideKind kind,
      double at,
      boolean reverse,
      String name) {
    var frame = site.plan().curve.sample(at, false);
    var q = frame.position();
    double side = reverse ? -3.5 : 3.5;
    var anchor =
        BlockPos.containing(
            q.x() - frame.tangent().z() * side - kind.width * .5,
            q.y(),
            q.z() + frame.tangent().x() * side - kind.width * .5);
    for (int x = 0; x < kind.width; x++)
      for (int z = 0; z < kind.width; z++) {
        h.getLevel().setBlock(anchor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        for (int y = 0; y < kind.height; y++)
          h.getLevel().setBlock(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
      }
    var player = site.player();
    player.setPos(anchor.getX() + .5, anchor.getY(), anchor.getZ() + 2.5);
    ResearchTestSupport.grant(
        h.getLevel(),
        factorycraft.research.ResearchTeams.resolve(player).orElseThrow(),
        "automated_rail_transportation");
    player.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(TracksideContent.ITEMS.get(kind).get(), 2));
    var plan = TracksidePlacement.plan(h.getLevel(), anchor, kind, reverse ? 0 : 180);
    h.assertTrue(
        plan.edge() != null && plan.edge().equals(site.edge()) && plan.reverse() == reverse,
        "physical side/edge binding " + plan);
    String result = TracksidePlacement.place(player, InteractionHand.MAIN_HAND, plan);
    h.assertTrue(
        result.equals("ready") && player.getMainHandItem().getCount() == 1,
        "paid physical device placement: " + result);
    var data = RailAutomationData.get(h.getLevel());
    var d = data.at(anchor);
    var n = d.settings();
    n.putString("Name", name);
    data.configure(d, n);
    return d;
  }

  static void removeDevices(
      GameTestHelper h, TrainV31GameTests.Site site, List<RailAutomationData.Device> devices) {
    var data = RailAutomationData.get(h.getLevel());
    for (var d : devices) {
      site.player().setPos(d.anchor.getX() + .5, d.anchor.getY(), d.anchor.getZ() + 2.5);
      for (int i = 0; data.blocks.busy() && i < 4096; i++) data.blocks.advance(512);
      String result = TracksidePlacement.remove(site.player(), d.anchor);
      h.assertTrue(result.equals("ready"), "safe trackside cleanup: " + result);
    }
  }

  @GameTest(template = "empty", batch = "rail_automation", timeoutTicks = 160)
  public static void sourceAutomationGateAndFractionalSignalsPreservePaidRail(GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 980);
    h.startSequence()
        .thenIdle(8)
        .thenExecute(
            () -> {
              var tech =
                  factorycraft.research.ResearchCatalog.get(h.getLevel())
                      .definition(
                          ResourceLocation.parse("factorycraft:automated_rail_transportation"))
                      .recipe();
              h.assertTrue(
                  tech.units() == 200
                      && tech.ticks() == 600
                      && tech.prerequisites()
                          .equals(List.of(ResourceLocation.parse("factorycraft:railway"))),
                  "source automated transportation gate");
              var data = RailAutomationData.get(h.getLevel());
              var count = RailwayData.get(h.getLevel()).segment(site.edge()).paidCount();
              var one = device(h, site, TracksideKind.SIGNAL, 42, false, "S1");
              var pair = device(h, site, TracksideKind.SIGNAL, 42, true, "S2");
              h.runAfterDelay(
                  15,
                  () -> {
                    h.assertTrue(
                        data.points.get(one.id).offset() == data.points.get(pair.id).offset(),
                        "opposing signals not paired at fractional cut");
                    h.assertTrue(
                        data.blocks.spans(site.edge()).size() == 2 && data.runtimeReady(),
                        "logical split not ready");
                    h.assertTrue(
                        data.aspect(one) == RailSignals.Aspect.GREEN
                            && data.aspect(pair) == RailSignals.Aspect.GREEN,
                        "free paired signals not green");
                    h.assertTrue(
                        RailwayData.get(h.getLevel()).segment(site.edge()).paidCount() == count,
                        "signal mutated paid rail owner");
                    removeDevices(h, site, List.of(one, pair));
                    TrainV31GameTests.cleanup(h, site);
                    h.succeed();
                  });
            });
  }

  @GameTest(template = "empty", batch = "rail_automation", timeoutTicks = 1100)
  public static void unoccupiedTrainBrakesAtNamedStopWithFiniteFuelAndCargo(GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 981);
    h.startSequence()
        .thenIdle(8)
        .thenExecute(
            () -> {
              var stop = device(h, site, TracksideKind.STOP, 76, false, "Iron delivery");
              var signal = device(h, site, TracksideKind.SIGNAL, 44, false, "Entry");
              var loco = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 20);
              System.out.println(
                  "FACTORYCRAFT_V0320_CARGO_PLACEMENT first="
                      + TrainData.get(h.getLevel()).owner(loco.getUUID()).head);
              var cargo = TrainV31GameTests.stock(h, site, StockSpec.CARGO, 13);
              var d = TrainData.get(h.getLevel());
              var train = d.owner(loco.getUUID());
              h.assertTrue(train.cars.size() == 2, "actual paid coupling");
              var car = d.car(loco.getUUID());
              car.items.setStackInSlot(0, new ItemStack(Items.COAL, 8));
              var load = new ItemStack(Items.IRON_INGOT, 19);
              load.set(
                  net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                  net.minecraft.network.chat.Component.literal("v0320 finite delivery"));
              h.assertTrue(
                  TrainPorts.inventory(cargo).insertItem(0, load, false).isEmpty(),
                  "stopped cargo loading");
              train.automation.schedule =
                  new RailSchedule(
                      List.of(
                          new RailSchedule.Entry(
                              "Iron delivery",
                              List.of(
                                  new RailWait.Condition(
                                      RailWait.Type.TIME,
                                      "",
                                      RailWait.Comparison.GE,
                                      10000,
                                      false)))),
                      List.of());
              site.player()
                  .setPos(stop.anchor.getX() + 8, stop.anchor.getY(), stop.anchor.getZ() + 8);
              h.assertTrue(TrainAutomation.automatic(d, train), "automatic mode refused");
              int[] elapsed = {0};
              boolean[] done = {false};
              h.onEachTick(
                  () -> {
                    if (done[0]) return;
                    elapsed[0]++;
                    if (elapsed[0] % 100 == 0)
                      System.out.println(
                          "FACTORYCRAFT_V0320_AUTO_PROGRESS head="
                              + train.head
                              + " speed="
                              + train.speed
                              + " phase="
                              + train.automation.phase
                              + " status="
                              + train.status);
                    h.assertTrue(
                        train.driver == null && loco.getPassengers().isEmpty(), "fake driver");
                    h.assertTrue(
                        train.head <= train.automation.targetHead + .001
                            || !train.automation.routed,
                        "station overshoot");
                    if (!train.automation.arrived) return;
                    done[0] = true;
                    h.assertTrue(
                        train.speed == 0
                            && Math.abs(train.head - train.automation.targetHead) <= .06,
                        "metric station pose");
                    h.assertTrue(
                        car.spentJ > 0
                            && car.fuelJ
                                    + car.spentJ
                                    + car.items.getStackInSlot(0).getCount() * 4_000_000L
                                == 32_000_000,
                        "finite source fuel settlement");
                    var received = TrainPorts.inventory(cargo).extractItem(0, 64, false);
                    h.assertTrue(
                        ItemStack.matches(load, received)
                            && TrainPorts.inventory(cargo).getStackInSlot(0).isEmpty(),
                        "finite component-exact unloading");
                    TrainAutomation.manual(d, train);
                    TrainV31GameTests.cleanup(h, site);
                    removeDevices(h, site, List.of(stop, signal));
                    System.out.println(
                        "FACTORYCRAFT_V0320_AUTO_DELIVERY: PASS ticks="
                            + elapsed[0]
                            + " spent="
                            + car.spentJ
                            + " cargo=19");
                    h.succeed();
                  });
            });
  }

  @GameTest(template = "empty", batch = "rail_automation_service", timeoutTicks = 1900)
  public static void twoNamedStationsUseRealBurnerUnloadingAndAnOpposingLocomotive(
      GameTestHelper h) {
    var checkpoint =
        h.getLevel()
            .getDataStorage()
            .computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                    ServiceDisk::new, ServiceDisk::load),
                "factorycraft_v0320_auto_service");
    if (checkpoint.data.hasUUID("Train")) {
      var t = TrainData.get(h.getLevel()).train(checkpoint.data.getUUID("Train"));
      h.assertTrue(t != null && t.automation.automatic, "saved automatic owner missing");
      h.assertTrue(
          checkpoint.data.getCompound("State").equals(savedTrain(h, t.id)),
          "independent JVM changed exact automatic resources/pose/claims/timers");
      var system = RailAutomationData.get(h.getLevel());
      h.assertTrue(
          Objects.equals(system.slots.destination(t.id), t.automation.destination)
              && system.slots.count(t.automation.destination) == 1,
          "saved single station commitment");
      var devices =
          system
              .save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess())
              .getList("Devices", 10);
      for (var raw : checkpoint.data.getList("Devices", 10))
        h.assertTrue(devices.contains(raw), "trackside identity/config not restored");
      System.out.println(
          "FACTORYCRAFT_V0320_SERVICE_DISK: VERIFIED exact train="
              + t.id
              + " automatic=true slot=1");
      h.succeed();
      return;
    }
    var site = TrainV31GameTests.rail(h, 982);
    h.startSequence()
        .thenIdle(8)
        .thenExecute(
            () -> {
              var b = device(h, site, TracksideKind.STOP, 78, false, "Unload iron");
              var a = device(h, site, TracksideKind.STOP, 8, true, "Return");
              var chain = device(h, site, TracksideKind.CHAIN, 40, false, "Chain entry");
              var reverseExit = device(h, site, TracksideKind.SIGNAL, 40, true, "Reverse exit");
              var exit = device(h, site, TracksideKind.SIGNAL, 54, false, "Exit");
              var reverseChain = device(h, site, TracksideKind.CHAIN, 54, true, "Reverse chain");
              var loco = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 30);
              System.out.println(
                  "FACTORYCRAFT_V0320_SERVICE_CARGO_PLACEMENT first="
                      + TrainData.get(h.getLevel()).owner(loco.getUUID()).head);
              var cargo = TrainV31GameTests.stock(h, site, StockSpec.CARGO, 23);
              var q = site.plan().curve.sample(16, false).position();
              var player = site.player();
              player.setPos(q.x() + 2.5, q.y() + .5, q.z());
              player.setYRot(0);
              player.setXRot(0);
              player.setItemInHand(
                  InteractionHand.MAIN_HAND,
                  new ItemStack(TrainContent.ITEMS.get(StockSpec.LOCOMOTIVE).get(), 2));
              String placed =
                  StockPlacement.place(
                      player,
                      new net.minecraft.world.item.context.UseOnContext(
                          player,
                          InteractionHand.MAIN_HAND,
                          new net.minecraft.world.phys.BlockHitResult(
                              TrainWorld.vec(q),
                              Direction.UP,
                              BlockPos.containing(q.x(), q.y(), q.z()),
                              false)),
                      StockSpec.LOCOMOTIVE);
              h.assertTrue(placed.equals("placed"), "reverse locomotive paid placement: " + placed);
              var d = TrainData.get(h.getLevel());
              var train = d.owner(loco.getUUID());
              h.assertTrue(
                  train.cars.size() == 3 && train.cars.getLast().reversed,
                  "actual reverse leading locomotive");
              for (var car : train.cars)
                if (car.kind == StockSpec.LOCOMOTIVE)
                  car.items.setStackInSlot(0, new ItemStack(Items.COAL, 8));
              var load = new ItemStack(Items.IRON_INGOT, 7);
              h.assertTrue(
                  TrainPorts.inventory(cargo).insertItem(0, load, false).isEmpty(),
                  "finite initial cargo");
              double destinationHead = b.point.offset() - StockSpec.HALF_LENGTH;
              var unload =
                  site.plan().curve.sample(destinationHead - StockSpec.SPACING, false).position();
              var armPos = BlockPos.containing(unload.x() + 2, unload.y(), unload.z());
              var chestPos = armPos.east();
              h.getLevel().setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
              var chest =
                  (net.minecraft.world.level.block.entity.ChestBlockEntity)
                      h.getLevel().getBlockEntity(chestPos);
              var state =
                  factorycraft.registry.Content.FACTORY
                      .get(factorycraft.blocks.FactoryKind.BURNER_INSERTER)
                      .get()
                      .defaultBlockState()
                      .setValue(factorycraft.blocks.FactoryBlock.FACING, Direction.EAST);
              factorycraft.multiblock.StructurePlacement.place(h.getLevel(), armPos, state);
              var arm =
                  (factorycraft.blockentity.InserterBlockEntity)
                      h.getLevel().getBlockEntity(armPos);
              arm.onLoad();
              arm.fuel.setStackInSlot(0, new ItemStack(Items.COAL, 3));
              train.automation.schedule =
                  new RailSchedule(
                      List.of(
                          new RailSchedule.Entry(
                              "Unload iron",
                              List.of(
                                  new RailWait.Condition(
                                      RailWait.Type.EMPTY, "", RailWait.Comparison.EQ, 1, false))),
                          new RailSchedule.Entry(
                              "Return",
                              List.of(
                                  new RailWait.Condition(
                                      RailWait.Type.TIME,
                                      "",
                                      RailWait.Comparison.GE,
                                      10000,
                                      false)))),
                      List.of());
              player.setPos(a.anchor.getX() + 8, a.anchor.getY(), a.anchor.getZ() + 8);
              h.assertTrue(TrainAutomation.automatic(d, train), "automatic service start");
              boolean[] done = {false}, visited = {false};
              int[] ticks = {0};
              h.onEachTick(
                  () -> {
                    if (done[0]) return;
                    ticks[0]++;
                    if (train.automation.arrived && b.id.equals(train.automation.destination))
                      visited[0] = true;
                    if (ticks[0] % 200 == 0)
                      System.out.println(
                          "FACTORYCRAFT_V0320_SERVICE_PROGRESS head="
                              + train.head
                              + " speed="
                              + train.speed
                              + " phase="
                              + train.automation.phase
                              + " entry="
                              + train.automation.entry
                              + " hand="
                              + arm.held()
                              + " chest="
                              + chest.getItem(0));
                    if (!train.automation.arrived || !a.id.equals(train.automation.destination))
                      return;
                    done[0] = true;
                    h.assertTrue(
                        visited[0] && train.direction == -1 && train.driver == null,
                        "real two-ended unoccupied service");
                    int count = 0;
                    for (int slot = 0; slot < chest.getContainerSize(); slot++)
                      count += chest.getItem(slot).getCount();
                    h.assertTrue(
                        count == 7
                            && arm.held().isEmpty()
                            && d.car(cargo.getUUID()).items.getStackInSlot(0).isEmpty(),
                        "real finite burner unloading: chest=" + count);
                    for (var car : train.cars)
                      if (car.kind == StockSpec.LOCOMOTIVE)
                        h.assertTrue(
                            car.fuelJ
                                    + car.spentJ
                                    + car.items.getStackInSlot(0).getCount() * 4_000_000L
                                == 32_000_000,
                            "two-ended exact fuel");
                    h.assertTrue(
                        train.cars.getFirst().spentJ > 0
                            && train.cars.getLast().spentJ > 0
                            && arm.fuelEnergy() > 0,
                        "both engines and burner must pay");
                    for (long key : site.pins()) {
                      d.unloadChunk(key);
                      h.getLevel().setChunkForced((int) key, (int) (key >> 32), false);
                    }
                    h.runAfterDelay(
                        50,
                        () -> {
                          checkpoint.data.putUUID("Train", train.id);
                          checkpoint.data.put("State", savedTrain(h, train.id));
                          var devices = new net.minecraft.nbt.ListTag();
                          for (var raw :
                              RailAutomationData.get(h.getLevel())
                                  .save(
                                      new net.minecraft.nbt.CompoundTag(),
                                      h.getLevel().registryAccess())
                                  .getList("Devices", 10)) {
                            var tag = (net.minecraft.nbt.CompoundTag) raw;
                            if (List.of(
                                    a.id, b.id, chain.id, reverseExit.id, exit.id, reverseChain.id)
                                .contains(tag.getUUID("Id"))) devices.add(tag.copy());
                          }
                          checkpoint.data.put("Devices", devices);
                          checkpoint.setDirty();
                          d.setDirty();
                          System.out.println(
                              "FACTORYCRAFT_V0320_SERVICE: PASS ticks="
                                  + ticks[0]
                                  + " finiteIron=7");
                          System.out.println(
                              "FACTORYCRAFT_V0320_SERVICE_DISK: SEEDED exact train="
                                  + train.id
                                  + " automatic=true slot=1");
                          h.succeed();
                        });
                  });
            });
  }

  @GameTest(template = "empty", batch = "rail_automation_profile", timeoutTicks = 1200)
  public static void fourAutonomousConsistsKeepFiniteCargoFluidsAndFuelAtNamedStops(
      GameTestHelper h) {
    var sites = new ArrayList<TrainV31GameTests.Site>();
    for (int i = 0; i < 4; i++) sites.add(TrainV31GameTests.rail(h, 990 + i));
    var trains = new ArrayList<TrainData.Train>();
    var devices = new ArrayList<List<RailAutomationData.Device>>();
    var samples = new ArrayList<Long>();
    var d = TrainData.get(h.getLevel());
    long[] prior = {0}, started = {0};
    boolean[] ready = {false}, done = {false};
    h.startSequence()
        .thenIdle(10)
        .thenExecute(
            () -> {
              for (var site : sites) {
                var stop = device(h, site, TracksideKind.STOP, 84, false, "Profile unload");
                var chain = device(h, site, TracksideKind.CHAIN, 44, false, "Profile chain");
                var exit = device(h, site, TracksideKind.SIGNAL, 52, false, "Profile exit");
                devices.add(List.of(stop, chain, exit));
                var loco = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 30);
                TrainV31GameTests.stock(h, site, StockSpec.CARGO, 23);
                TrainV31GameTests.stock(h, site, StockSpec.FLUID, 16);
                TrainV31GameTests.stock(h, site, StockSpec.CARGO, 9);
                var train = d.owner(loco.getUUID());
                h.assertTrue(train.cars.size() == 4, "profile paid four-car stock");
                for (var car : train.cars) {
                  if (car.kind == StockSpec.LOCOMOTIVE)
                    car.items.setStackInSlot(0, new ItemStack(Items.COAL, 8));
                  if (car.kind == StockSpec.CARGO)
                    car.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 11));
                  if (car.kind == StockSpec.FLUID)
                    car.fluid.fill(
                        new factorycraft.fluids.FluidKey(
                            new net.neoforged.neoforge.fluids.FluidStack(
                                net.minecraft.world.level.material.Fluids.WATER, 1)),
                        23457,
                        false);
                }
                train.automation.schedule =
                    new RailSchedule(
                        List.of(
                            new RailSchedule.Entry(
                                "Profile unload",
                                List.of(
                                    new RailWait.Condition(
                                        RailWait.Type.TIME,
                                        "",
                                        RailWait.Comparison.GE,
                                        10000,
                                        false)))),
                        List.of());
                site.player()
                    .setPos(stop.anchor.getX() + 8, stop.anchor.getY(), stop.anchor.getZ() + 8);
                h.assertTrue(TrainAutomation.automatic(d, train), "profile automatic dispatch");
                trains.add(train);
              }
              prior[0] = d.simulationNs;
              started[0] = h.getLevel().getGameTime();
              ready[0] = true;
            });
    h.onEachTick(
        () -> {
          if (!ready[0] || done[0]) return;
          samples.add(d.simulationNs - prior[0]);
          prior[0] = d.simulationNs;
          if (trains.stream().anyMatch(t -> !t.automation.arrived)) return;
          done[0] = true;
          long cargo = 0, fluid = 0, spent = 0;
          for (var t : trains) {
            h.assertTrue(t.driver == null && t.speed == 0, "profile uses no rider");
            for (var car : t.cars) {
              if (car.kind == StockSpec.CARGO)
                for (int slot = 0; slot < car.items.getSlots(); slot++)
                  cargo += car.items.getStackInSlot(slot).getCount();
              if (car.kind == StockSpec.FLUID) fluid += car.fluid.amount();
              if (car.kind == StockSpec.LOCOMOTIVE) {
                spent += car.spentJ;
                h.assertTrue(
                    car.spentJ > 0
                        && car.fuelJ
                                + car.spentJ
                                + car.items.getStackInSlot(0).getCount() * 4_000_000L
                            == 32_000_000,
                    "profile exact locomotive source fuel");
              }
            }
            TrainAutomation.manual(d, t);
          }
          h.assertTrue(cargo == 88 && fluid == 93828, "profile finite item/fluid ledger");
          System.out.println("FACTORYCRAFT_V0320_AUTO_PROFILE_SAMPLES_NS: " + samples);
          samples.sort(Long::compare);
          long sum = 0;
          for (long n : samples) sum += n;
          System.out.println(
              "FACTORYCRAFT_V0320_AUTO_PROFILE: trains=4 cars=16 ticks="
                  + (h.getLevel().getGameTime() - started[0])
                  + " cargo="
                  + cargo
                  + " fluid="
                  + fluid
                  + " spentJ="
                  + spent
                  + " trainTickMeanNs="
                  + (sum / Math.max(1, samples.size()))
                  + " p95Ns="
                  + samples.get((samples.size() - 1) * 95 / 100)
                  + " p99Ns="
                  + samples.get((samples.size() - 1) * 99 / 100)
                  + " maxNs="
                  + samples.getLast());
          for (int i = 0; i < sites.size(); i++) {
            TrainV31GameTests.cleanup(h, sites.get(i));
            removeDevices(h, sites.get(i), devices.get(i));
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_automation_circuit", timeoutTicks = 1000)
  public static void fourColorForceRedBrakesWithoutFuelAndReducedLimitKeepsCommittedArrival(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 984);
    var d = TrainData.get(h.getLevel());
    TrainData.Train[] train = {null};
    RailAutomationData.Device[] stop = {null}, signal = {null};
    factorycraft.circuits.CircuitNodeBlockEntity[] signalSource = {null}, stopSource = {null};
    int[] phase = {-1}, heldTicks = {0};
    long[] spent = {0};
    double[] head = {0};
    h.startSequence()
        .thenIdle(8)
        .thenExecute(
            () -> {
              stop[0] = device(h, site, TracksideKind.STOP, 76, false, "Circuit destination");
              signal[0] = device(h, site, TracksideKind.SIGNAL, 44, false, "Controlled entry");
              var system = RailAutomationData.get(h.getLevel());
              var root = system.entity(signal[0]);
              var stopRoot = system.entity(stop[0]);
              signalSource[0] =
                  factorycraft.diagnostics.CircuitFactory.node(
                      h.getLevel(),
                      signal[0].anchor.east(3),
                      factorycraft.blocks.FactoryKind.CONSTANT,
                      factorycraft.diagnostics.CircuitFactory.constant(
                          factorycraft.simulation.circuits.SignalKey.A, 1),
                      0,
                      15);
              stopSource[0] =
                  factorycraft.diagnostics.CircuitFactory.node(
                      h.getLevel(),
                      stop[0].anchor.east(3),
                      factorycraft.blocks.FactoryKind.CONSTANT,
                      factorycraft.diagnostics.CircuitFactory.constant(
                          factorycraft.simulation.circuits.SignalKey.virtual("signal_l"), 1),
                      0,
                      8);
              signalSource[0].onLoad();
              stopSource[0].onLoad();
              var settings =
                  factorycraft.diagnostics.CircuitFactory.settings(
                          false,
                          true,
                          List.of(
                              factorycraft.diagnostics.CircuitFactory.condition(
                                  factorycraft.simulation.circuits.SignalKey.A,
                                  factorycraft.simulation.circuits.SignalMath.Compare.GREATER,
                                  0)),
                          15,
                          15)
                      .save();
              settings.putBoolean("State", true);
              factorycraft.diagnostics.CircuitFactory.configure(
                  root.circuit(true),
                  factorycraft.circuits.CircuitSettings.load(settings),
                  factorycraft.simulation.circuits.SignalProgram.defaults(
                      factorycraft.simulation.circuits.SignalProgram.Kind.CONSTANT));
              var read =
                  factorycraft.diagnostics.CircuitFactory.settings(true, false, List.of(), 8, 15)
                      .save();
              read.putBoolean("State", true);
              factorycraft.diagnostics.CircuitFactory.configure(
                  stopRoot.circuit(true),
                  factorycraft.circuits.CircuitSettings.load(read),
                  factorycraft.simulation.circuits.SignalProgram.defaults(
                      factorycraft.simulation.circuits.SignalProgram.Kind.CONSTANT));
              for (int color = 0; color < 4; color++)
                factorycraft.diagnostics.CircuitFactory.wire(
                    signalSource[0].circuit(true), 0, root.circuit(true), 0, color);
              factorycraft.diagnostics.CircuitFactory.wire(
                  stopSource[0].circuit(true), 0, stopRoot.circuit(true), 0, 3);
              var config = stop[0].settings();
              config.putBoolean("CircuitLimit", true);
              system.configure(stop[0], config);
              h.assertTrue(
                  factorycraft.circuits.CircuitAccess.at(h.getLevel(), signal[0].anchor.above())
                      == root.circuit(false),
                  "signal upper part must expose the same sole circuit contact");
              var loco = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 20);
              train[0] = d.owner(loco.getUUID());
              train[0].cars.getFirst().items.setStackInSlot(0, new ItemStack(Items.COAL, 8));
              train[0].automation.schedule =
                  new RailSchedule(
                      List.of(
                          new RailSchedule.Entry(
                              "Circuit destination",
                              List.of(
                                  new RailWait.Condition(
                                      RailWait.Type.TIME,
                                      "",
                                      RailWait.Comparison.GE,
                                      10000,
                                      false)))),
                      List.of());
              site.player()
                  .setPos(
                      stop[0].anchor.getX() + 8, stop[0].anchor.getY(), stop[0].anchor.getZ() + 8);
              phase[0] = 0;
            });
    h.onEachTick(
        () -> {
          if (phase[0] < 0 || phase[0] == 4) return;
          var system = RailAutomationData.get(h.getLevel());
          var t = train[0];
          var root = system.entity(signal[0]);
          if (phase[0] == 0) {
            if (system.aspect(signal[0]) != RailSignals.Aspect.RED
                || system.slots.get(stop[0].id).limit() != 1
                || !root.circuit(false).inputReady) return;
            h.assertTrue(
                root.circuit(false).input.get(factorycraft.simulation.circuits.SignalKey.A) == 4,
                "four colors must sum independently");
            h.assertTrue(
                TrainAutomation.automatic(d, t), "circuit service starts with one station slot");
            phase[0] = 1;
          } else if (phase[0] == 1) {
            if (t.speed != 0 || signal[0].point.offset() - t.front() > .5) return;
            h.assertTrue(
                t.front() < signal[0].point.offset()
                    && t.automation.phase == AutoTrainState.Phase.SIGNAL_WAIT,
                "stop ahead of force-red boundary");
            head[0] = t.head;
            spent[0] = t.cars.getFirst().spentJ;
            h.assertTrue(spent[0] > 0, "real paid approach");
            phase[0] = 2;
          } else if (phase[0] == 2) {
            h.assertTrue(
                t.head == head[0] && t.speed == 0 && t.cars.getFirst().spentJ == spent[0],
                "force red changed stopped pose or charged waiting fuel");
            if (++heldTicks[0] < 25) return;
            factorycraft.diagnostics.CircuitFactory.configure(
                signalSource[0].circuit(true),
                signalSource[0].circuit(true).settings(),
                factorycraft.diagnostics.CircuitFactory.constant(
                    factorycraft.simulation.circuits.SignalKey.A, 0));
            factorycraft.diagnostics.CircuitFactory.configure(
                stopSource[0].circuit(true),
                stopSource[0].circuit(true).settings(),
                factorycraft.diagnostics.CircuitFactory.constant(
                    factorycraft.simulation.circuits.SignalKey.virtual("signal_l"), 0));
            phase[0] = 3;
          } else if (phase[0] == 3) {
            if (!t.automation.arrived) return;
            h.assertTrue(
                system.slots.get(stop[0].id).limit() == 0
                    && system.slots.count(stop[0].id) == 1
                    && !system.slots.available(stop[0].id, UUID.randomUUID()),
                "limit decrease lost the committed arrival or admitted a new one");
            var stopRoot = system.entity(stop[0]);
            var reading = RailCircuitAdapter.read(stopRoot, stopRoot.circuit(false));
            h.assertTrue(
                reading.ready()
                    && reading
                            .signals()
                            .get(factorycraft.simulation.circuits.SignalKey.virtual("train_id"))
                        == system.trainNumber(t),
                "station train ID must read the canonical docked owner");
            h.assertTrue(
                TrainWaitSamples.sample(
                        d,
                        t,
                        system,
                        new RailWait.Condition(
                            RailWait.Type.CIRCUIT, "", RailWait.Comparison.NE, 0, false))
                    .isEmpty(),
                "malformed circuit filter must be unknown rather than crash the server");
            h.assertTrue(
                h.getLevel()
                        .getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                            stop[0].anchor,
                            null)
                    == null,
                "station must not expose an inventory copy");
            phase[0] = 4;
            TrainAutomation.manual(d, t);
            for (var source : List.of(signalSource[0], stopSource[0]))
              factorycraft.multiblock.AssemblyAccess.owner(source).assembly().removeWhole(false);
            TrainV31GameTests.cleanup(h, site);
            removeDevices(h, site, List.of(stop[0], signal[0]));
            System.out.println(
                "FACTORYCRAFT_V0320_CIRCUIT: PASS colors=4 redWait=25 committedLimit=0");
            h.succeed();
          }
        });
  }

  @GameTest(template = "empty", batch = "rail_automation_protection", timeoutTicks = 140)
  public static void protectedTracksidePlacementRevalidatesCallbacksAndReturnsOneDevice(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 985);
    var l = h.getLevel();
    var p = site.player();
    var origin = site.plan().curve.start;
    var anchor = new BlockPos(origin.x() + 3, origin.y(), origin.z() - 32);
    l.setBlockAndUpdate(anchor.below(), Blocks.STONE.defaultBlockState());
    h.startSequence()
        .thenIdle(8)
        .thenExecute(
            () -> {
              p.setPos(anchor.getX() + .5, anchor.getY(), anchor.getZ() + 2.5);
              p.setYRot(180);
              var scope = factorycraft.research.ResearchTeams.resolve(p).orElseThrow();
              ResearchTestSupport.grant(l, scope, "automated_rail_transportation");
              p.setItemInHand(
                  InteractionHand.MAIN_HAND,
                  new ItemStack(TracksideContent.ITEMS.get(TracksideKind.SIGNAL).get(), 2));
              var plan = TracksidePlacement.plan(l, anchor, TracksideKind.SIGNAL, 180);
              var data = RailAutomationData.get(l);
              int count = data.devices.size();
              for (int mode = 0; mode < 2; mode++) {
                int current = mode;
                var position = p.position();
                java.util.function.Consumer<
                        net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent>
                    listener =
                        e -> {
                          if (e.getEntity() == p) {
                            if (current == 0) e.setCanceled(true);
                            else p.setPos(position.x + 1000, position.y, position.z);
                          }
                        };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
                String result;
                try {
                  result = TracksidePlacement.place(p, InteractionHand.MAIN_HAND, plan);
                } finally {
                  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
                  p.setPos(position.x, position.y, position.z);
                }
                h.assertTrue(
                    result.equals("permission")
                        && p.getMainHandItem().getCount() == 2
                        && data.devices.size() == count,
                    "protected/moved callback acquired or spent a device: " + result);
                for (var cell : plan.cells())
                  h.assertTrue(
                      l.getBlockState(cell).isAir() && l.getBlockEntity(cell) == null,
                      "rollback left a technical part");
              }
              h.assertTrue(
                  TracksidePlacement.place(p, InteractionHand.MAIN_HAND, plan).equals("ready")
                      && p.getMainHandItem().getCount() == 1,
                  "normal paid placement after rejected callbacks");
              for (int work = 0; data.blocks.busy() && work < 4096; work++)
                data.blocks.advance(512);
              var duplicateAnchor = anchor.east();
              l.setBlockAndUpdate(duplicateAnchor.below(), Blocks.STONE.defaultBlockState());
              var duplicate =
                  TracksidePlacement.plan(l, duplicateAnchor, TracksideKind.SIGNAL, 180);
              h.assertTrue(
                  TracksidePlacement.place(p, InteractionHand.MAIN_HAND, duplicate)
                          .equals("occupied")
                      && p.getMainHandItem().getCount() == 1,
                  "duplicate same-side point must not debit or crash");
              h.assertTrue(
                  TracksidePlacement.remove(p, anchor.above()).equals("ready"),
                  "remove from nonowner upper part");
              h.assertTrue(data.devices.size() == count, "removed identity remains live");
            })
        .thenIdle(3)
        .thenWaitUntil(
            () -> {
              int count =
                  l
                      .getEntitiesOfClass(
                          net.minecraft.world.entity.item.ItemEntity.class,
                          new net.minecraft.world.phys.AABB(anchor).inflate(4),
                          e ->
                              e.getItem()
                                  .is(TracksideContent.ITEMS.get(TracksideKind.SIGNAL).get()))
                      .stream()
                      .mapToInt(e -> e.getItem().getCount())
                      .sum();
              h.assertTrue(
                  count == 1,
                  "waiting for exactly one loaded recovery item through the bounded retry queue: "
                      + count);
            })
        .thenExecute(
            () -> {
              var drops =
                  l.getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new net.minecraft.world.phys.AABB(anchor).inflate(4),
                      e -> e.getItem().is(TracksideContent.ITEMS.get(TracksideKind.SIGNAL).get()));
              h.assertTrue(
                  drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,
                  "upper-part removal must return exactly one item");
              drops.forEach(net.minecraft.world.entity.Entity::discard);
              TrainV31GameTests.cleanup(h, site);
            })
        .thenSucceed();
  }

  private static net.minecraft.nbt.CompoundTag savedTrain(GameTestHelper h, UUID id) {
    return TrainData.get(h.getLevel())
        .save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess())
        .getList("Trains", 10)
        .stream()
        .map(n -> (net.minecraft.nbt.CompoundTag) n)
        .filter(n -> n.getUUID("Id").equals(id))
        .findFirst()
        .orElseThrow()
        .copy();
  }

  public static final class ServiceDisk extends net.minecraft.world.level.saveddata.SavedData {
    net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();

    static ServiceDisk load(net.minecraft.nbt.CompoundTag n, HolderLookup.Provider r) {
      var d = new ServiceDisk();
      d.data = n.getCompound("Checkpoint");
      return d;
    }

    public net.minecraft.nbt.CompoundTag save(
        net.minecraft.nbt.CompoundTag n, HolderLookup.Provider r) {
      n.put("Checkpoint", data);
      return n;
    }
  }

  private RailAutomationGameTests() {}
}
