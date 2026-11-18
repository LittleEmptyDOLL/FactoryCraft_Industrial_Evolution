package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class TrainV31GameTests {
  record Site(RailPlan plan, UUID edge, ServerPlayer player, Set<Long> pins) {}

  static Site rail(GameTestHelper h, int arena) {
    var o = PowerV20GameTests.site(h, arena);
    var p =
        new RailPlan(
            RailCurve.plan(
                RailwayV30GameTests.end(o, 0, 0, 0, 0), RailwayV30GameTests.end(o, 0, 0, -96, 0)));
    RailwayV30GameTests.prepare(h, p);
    var pins = new HashSet<Long>(p.footprint.chunks());
    for (long key : List.copyOf(pins))
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++)
          pins.add(
              net.minecraft.world.level.ChunkPos.asLong((int) key + dx, (int) (key >> 32) + dz));
    for (long key : pins) {
      h.getLevel().getChunk((int) key, (int) (key >> 32));
      h.getLevel().setChunkForced((int) key, (int) (key >> 32), true);
    }
    var player = RailwayV30GameTests.player(h, p, true);
    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    var edge = RailwayV30GameTests.placed(h, p, player);
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    return new Site(p, edge, player, pins);
  }

  static StockEntity stock(GameTestHelper h, Site site, StockSpec kind, double at) {
    return stock(h, site, kind, at, new ItemStack(TrainContent.ITEMS.get(kind).get(), 2));
  }

  static StockEntity stock(GameTestHelper h, Site site, StockSpec kind, double at, ItemStack held) {
    var point = site.plan.curve.sample(at, false).position();
    var hit = new Vec3(point.x(), point.y() + .2, point.z());
    var pos = BlockPos.containing(hit);
    var player = site.player;
    player.setPos(point.x() + 2.5, point.y() + .5, point.z());
    player.setYRot(180);
    player.setXRot(0);
    if (kind == StockSpec.FLUID)
      ResearchTestSupport.grant(
          h.getLevel(),
          factorycraft.research.ResearchTeams.resolve(player).orElseThrow(),
          "fluid_wagon_technology");
    player.setItemInHand(InteractionHand.MAIN_HAND, held);
    int before = held.getCount();
    String result =
        StockPlacement.place(
            player,
            new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, pos, false)),
            kind);
    h.assertTrue(result.equals("placed"), "actual stock placement: " + result);
    h.assertTrue(
        player.getMainHandItem().getCount() == before - 1, "stock item not paid exactly once");
    return h
        .getLevel()
        .getEntitiesOfClass(StockEntity.class, new AABB(hit, hit).inflate(2))
        .stream()
        .filter(e -> e.kind() == kind)
        .findFirst()
        .orElseThrow();
  }

  static TrainCar car(GameTestHelper h, StockEntity stock) {
    return TrainData.get(h.getLevel()).car(stock.getUUID());
  }

  static void cleanup(GameTestHelper h, Site site) {
    var data = TrainData.get(h.getLevel());
    var ids = new ArrayList<UUID>();
    for (var train : data.trains())
      for (var c : train.cars)
        if (train.path.legs().stream().anyMatch(e -> e.edge().edge().equals(site.edge)))
          ids.add(c.id);
    for (var id : ids) {
      var t = data.owner(id);
      var view = h.getLevel().getEntity(id);
      if (view != null) {
        site.player.stopRiding();
        site.player.setPos(view.getX() + 2, view.getY(), view.getZ());
      }
      StockPlacement.pack(data, t, id, site.player);
    }
    h.getLevel()
        .getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            new AABB(
                    new BlockPos(
                        site.plan.curve.start.x(),
                        site.plan.curve.start.y(),
                        site.plan.curve.start.z()))
                .inflate(110))
        .forEach(net.minecraft.world.entity.Entity::discard);
    if (RailwayData.get(h.getLevel()).segment(site.edge) != null)
      RailwayData.get(h.getLevel()).remove(site.edge);
    for (long key : site.pins) h.getLevel().setChunkForced((int) key, (int) (key >> 32), false);
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 100)
  public static void sourceStockRecipesResearchAndEmptyFluidInputs(GameTestHelper h) {
    var l = h.getLevel();
    var tech =
        factorycraft.research.ResearchCatalog.get(l)
            .definition(
                net.minecraft.resources.ResourceLocation.parse(
                    "factorycraft:fluid_wagon_technology"))
            .recipe();
    h.assertTrue(
        tech.units() == 200 && tech.ticks() == 600 && tech.prerequisites().size() == 2,
        "source fluid wagon gate");
    for (var kind : StockSpec.values()) {
      var holder =
          l.getRecipeManager()
              .byKey(
                  net.minecraft.resources.ResourceLocation.parse("factorycraft:craft_" + kind.id))
              .orElseThrow();
      var r = (factorycraft.recipes.CountedCraftingRecipe) holder.value();
      var inventory =
          new net.minecraft.world.inventory.TransientCraftingContainer(
              net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                      l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TrainRecipeQA"))
                  .inventoryMenu,
              3,
              3);
      int i = 0;
      for (var input : r.inputs())
        inventory.setItem(i++, input.ingredient().getItems()[0].copyWithCount(input.count()));
      h.assertTrue(r.plan(inventory.asCraftInput()) != null, "source craft plan " + kind);
      if (kind == StockSpec.FLUID) {
        var tank = inventory.getItem(3);
        tank.set(DataComponents.CUSTOM_NAME, Component.literal("filled portable tank"));
        h.assertTrue(
            r.plan(inventory.asCraftInput()) == null,
            "resource-owning tank consumed by wagon recipe");
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 120)
  public static void actualPlacementCouplingSplitAndProtectedOccupiedTrack(GameTestHelper h) {
    var site = rail(h, 310);
    h.runAfterDelay(
        8,
        () -> {
          var a = stock(h, site, StockSpec.LOCOMOTIVE, 30);
          var b = stock(h, site, StockSpec.CARGO, 23);
          var data = TrainData.get(h.getLevel());
          h.assertTrue(
              data.owner(a.getUUID()) == data.owner(b.getUUID())
                  && data.owner(a.getUUID()).cars.size() == 2,
              "adjacent ends did not couple");
          h.assertTrue(
              RailPlacement.remove(site.player, site.edge).equals("occupied"),
              "occupied track removed");
          var cargo = car(h, b);
          cargo.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 17));
          h.assertTrue(StockPlacement.split(data, data.owner(a.getUUID()), 0), "safe uncouple");
          h.assertTrue(
              data.owner(a.getUUID()) != data.owner(b.getUUID())
                  && car(h, b) == cargo
                  && cargo.items.getStackInSlot(0).getCount() == 17,
              "split copied/lost resource owner");
          h.assertTrue(
              StockPlacement.couple(data, data.owner(a.getUUID()), data.owner(b.getUUID())),
              "recoupling failed");
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 120)
  public static void nativeCargoFiltersBarsComponentsAndStaleMovingCapability(GameTestHelper h) {
    var site = rail(h, 311);
    h.runAfterDelay(
        8,
        () -> {
          var entity = stock(h, site, StockSpec.CARGO, 30);
          var car = car(h, entity);
          var handler = entity.getCapability(Capabilities.ItemHandler.ENTITY);
          h.assertTrue(handler != null && handler.getSlots() == 40, "40 cargo slots");
          var typed = new ItemStack(Items.IRON_INGOT, 19);
          typed.set(DataComponents.CUSTOM_NAME, Component.literal("train typed cargo"));
          h.assertTrue(
              handler.insertItem(0, typed, true).isEmpty() && car.items.getStackInSlot(0).isEmpty(),
              "SIMULATE changed cargo");
          handler.insertItem(0, typed, false);
          car.filters[1] = "minecraft:copper_ingot";
          car.bar = 2;
          h.assertTrue(
              handler.insertItem(1, typed, false).getCount() == 19
                  && handler.insertItem(2, typed, false).getCount() == 19,
              "filter/bar bypass");
          var train = TrainData.get(h.getLevel()).owner(car.id);
          train.speed = .2;
          h.assertTrue(
              handler.extractItem(0, 19, false).isEmpty()
                  && car.items.getStackInSlot(0).getCount() == 19,
              "moving stale handler extracted");
          train.speed = 0;
          var taken = handler.extractItem(0, 3, false);
          h.assertTrue(
              taken.getCount() == 3
                  && taken.getHoverName().getString().equals("train typed cargo")
                  && car.items.getStackInSlot(0).getCount() == 16,
              "partial/component cargo ledger");
          cleanup(h, site);
          h.assertTrue(handler.getSlots() == 0, "retired handler gained a new owner");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 120)
  public static void nativeFluidWagonCapacitySidedSimulationAndTwoViewLedger(GameTestHelper h) {
    var site = rail(h, 312);
    h.runAfterDelay(
        8,
        () -> {
          var entity = stock(h, site, StockSpec.FLUID, 30);
          var west = entity.getCapability(Capabilities.FluidHandler.ENTITY, Direction.WEST);
          var east = entity.getCapability(Capabilities.FluidHandler.ENTITY, Direction.EAST);
          h.assertTrue(
              west.getTankCapacity(0) == 500000
                  && entity
                          .getCapability(Capabilities.FluidHandler.ENTITY, Direction.UP)
                          .getTankCapacity(0)
                      == 0,
              "source capacity/physical side");
          var water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 123457);
          water.set(DataComponents.CUSTOM_NAME, Component.literal("typed wagon water"));
          h.assertTrue(
              west.fill(water, IFluidHandler.FluidAction.SIMULATE) == 123457
                  && car(h, entity).fluid.amount() == 0,
              "fluid simulate wrote owner");
          west.fill(water, IFluidHandler.FluidAction.EXECUTE);
          h.assertTrue(
              east.fill(
                      new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1),
                      IFluidHandler.FluidAction.EXECUTE)
                  == 0,
              "incompatible fluid components mixed");
          int first = west.drain(100000, IFluidHandler.FluidAction.EXECUTE).getAmount(),
              second = east.drain(100000, IFluidHandler.FluidAction.EXECUTE).getAmount();
          h.assertTrue(
              first + second == 123457 && car(h, entity).fluid.amount() == 0,
              "two capabilities extracted duplicate shared liquid");
          cleanup(h, site);
          h.assertTrue(
              west.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0,
              "retired fluid owner accessible");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 120)
  public static void paidRidingMotionBrakingAndFuelExhaustionKeepCanonicalPose(GameTestHelper h) {
    var site = rail(h, 313);
    h.runAfterDelay(
        8,
        () -> {
          var entity = stock(h, site, StockSpec.LOCOMOTIVE, 30);
          var car = car(h, entity);
          var d = TrainData.get(h.getLevel());
          var train = d.owner(car.id);
          double initial = train.head;
          var driver =
              new ServerPlayer(
                  h.getLevel().getServer(),
                  h.getLevel(),
                  site.player.getGameProfile(),
                  ClientInformation.createDefault());
          driver.connection =
              new net.minecraft.server.network.ServerGamePacketListenerImpl(
                  h.getLevel().getServer(),
                  new net.minecraft.network.Connection(
                      net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                  driver,
                  net.minecraft.server.network.CommonListenerCookie.createInitial(
                      driver.getGameProfile(), false)) {
                public void send(net.minecraft.network.protocol.Packet<?> packet) {}

                public void teleport(double x, double y, double z, float yaw, float pitch) {}
              };
          h.assertTrue(driver.startRiding(entity), "rider failed");
          TrainNetwork.control(driver, entity, 1, false, 0);
          TrainSimulation.tick(d, train);
          h.assertTrue(
              train.head == initial && train.speed == 0 && car.spentJ == 0,
              "zero fuel produced motion");
          car.items.setStackInSlot(0, new ItemStack(Items.COAL, 1));
          TrainNetwork.control(driver, entity, 1, false, 0);
          TrainSimulation.tick(d, train);
          h.assertTrue(
              train.head > initial
                  && train.speed > 0
                  && car.spentJ == 30000
                  && car.fuelJ == 3970000
                  && car.items.getStackInSlot(0).isEmpty(),
              "source 600kW/one coal exact debit");
          TrainNetwork.control(driver, entity, 0, true, 0);
          for (int i = 0; i < 5; i++) TrainSimulation.tick(d, train);
          h.assertTrue(
              train.speed == 0 && car.spentJ == 30000,
              "braking consumed free extra fuel or failed to stop");
          driver.stopRiding();
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 120)
  public static void environmentalTrackLossKeepsRecoverablePortableCargoAndIdentity(
      GameTestHelper h) {
    var site = rail(h, 314);
    h.runAfterDelay(
        8,
        () -> {
          var entity = stock(h, site, StockSpec.CARGO, 30);
          var d = TrainData.get(h.getLevel());
          var car = car(h, entity);
          car.items.setStackInSlot(0, new ItemStack(Items.EMERALD, 23));
          var t = d.owner(car.id);
          var handler = entity.getCapability(Capabilities.ItemHandler.ENTITY);
          RailwayData.get(h.getLevel()).remove(site.edge);
          h.assertTrue(
              d.car(car.id) == car
                  && t.status == TrainData.Status.BROKEN_TRACK
                  && handler.extractItem(0, 23, false).isEmpty(),
              "broken track erased or exposed cargo");
          site.player.setPos(entity.getX() + 2, entity.getY(), entity.getZ());
          h.assertTrue(
              StockPlacement.pack(d, t, car.id, site.player), "broken stock cannot be recovered");
          h.assertTrue(
              d.car(car.id) == null && !StockPlacement.pack(d, t, car.id, site.player),
              "portable cargo duplicated");
          boolean found = false;
          for (var stack : site.player.getInventory().items)
            if (stack.is(TrainContent.ITEMS.get(StockSpec.CARGO).get())
                && stack.has(DataComponents.CUSTOM_DATA)) {
              var tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
              var restored =
                  TrainCar.load(
                      tag.getCompound("FactoryTrainStock"),
                      h.getLevel().registryAccess(),
                      () -> {},
                      true);
              found = restored.items.getStackInSlot(0).getCount() == 23;
            }
          h.assertTrue(found, "portable exact resource state missing");
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 350)
  public static void stoppedCargoUnloadsThroughActualBurnerInserterWithFiniteFuel(
      GameTestHelper h) {
    var site = rail(h, 316);
    var stock = new StockEntity[1];
    var arm = new factorycraft.blockentity.InserterBlockEntity[1];
    var chest = new net.minecraft.world.level.block.entity.ChestBlockEntity[1];
    h.runAfterDelay(
        8,
        () -> {
          stock[0] = stock(h, site, StockSpec.CARGO, 30);
          car(h, stock[0]).items.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 3));
          var origin =
              new BlockPos(
                  site.plan.curve.start.x(),
                  site.plan.curve.start.y(),
                  site.plan.curve.start.z() - 30);
          var armPos = origin.east(2);
          h.getLevel()
              .setBlockAndUpdate(
                  armPos,
                  factorycraft.registry.Content.FACTORY
                      .get(factorycraft.blocks.FactoryKind.BURNER_INSERTER)
                      .get()
                      .defaultBlockState()
                      .setValue(factorycraft.blocks.FactoryBlock.FACING, Direction.EAST));
          arm[0] =
              (factorycraft.blockentity.InserterBlockEntity) h.getLevel().getBlockEntity(armPos);
          arm[0].onLoad();
          arm[0].fuel.setStackInSlot(0, new ItemStack(Items.COAL, 1));
          h.getLevel()
              .setBlockAndUpdate(
                  origin.east(3), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
          chest[0] =
              (net.minecraft.world.level.block.entity.ChestBlockEntity)
                  h.getLevel().getBlockEntity(origin.east(3));
        });
    h.onEachTick(
        () -> {
          if (chest[0] == null) return;
          int delivered = 0;
          for (int i = 0; i < chest[0].getContainerSize(); i++)
            if (chest[0].getItem(i).is(Items.COPPER_INGOT))
              delivered += chest[0].getItem(i).getCount();
          int held = arm[0].held().is(Items.COPPER_INGOT) ? arm[0].held().getCount() : 0;
          int remaining = car(h, stock[0]).items.getStackInSlot(0).getCount();
          h.assertTrue(delivered + held + remaining == 3, "real wagon/inserter/chest item ledger");
          if (delivered == 3) {
            h.assertTrue(
                arm[0].fuelEnergy() > 0 && arm[0].fuel.getStackInSlot(0).isEmpty(),
                "inserter moved without paying finite coal");
            arm[0].onChunkUnloaded();
            cleanup(h, site);
            h.succeed();
          }
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 250)
  public static void stoppedTankerLoadsOnlyThroughPhysicalPoweredPumpAndKeepsExactWater(
      GameTestHelper h) {
    var site = rail(h, 317);
    var stock = new StockEntity[1];
    var pump = new factorycraft.blockentity.FluidBlockEntity[1];
    h.runAfterDelay(
        8,
        () -> {
          stock[0] = stock(h, site, StockSpec.FLUID, 30);
          var origin =
              new BlockPos(
                  site.plan.curve.start.x(),
                  site.plan.curve.start.y(),
                  site.plan.curve.start.z() - 30);
          var at = origin.east(2);
          var state =
              factorycraft.registry.Content.FLUID_BLOCKS
                  .get(factorycraft.fluids.FluidKind.PUMP)
                  .get()
                  .defaultBlockState()
                  .setValue(factorycraft.blocks.FluidDeviceBlock.FACING, Direction.WEST);
          factorycraft.multiblock.StructurePlacement.place(h.getLevel(), at, state);
          pump[0] = (factorycraft.blockentity.FluidBlockEntity) h.getLevel().getBlockEntity(at);
          for (var part : pump[0].assembly().positions())
            h.getLevel().getBlockEntity(part).onLoad();
          pump[0].onLoad();
          var key =
              new factorycraft.fluids.FluidKey(
                  new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1));
          pump[0].localCell().fill(key, 1234, false);
          pump[0].power.receiveEnergy(4000, false);
          factorycraft.fluids.FluidSimulation.activatePump(pump[0]);
          h.assertTrue(
              factorycraft.fluids.FluidConnections.input(
                      h.getLevel(), origin.east(1), Direction.EAST, key)
                  != null,
              "aligned physical pump nozzle did not discover wagon");
          h.assertTrue(
              factorycraft.fluids.FluidConnections.input(
                      h.getLevel(), origin.west(1), Direction.WEST, key)
                  == null,
              "passive/unrelated point obtained wagon fluid adapter");
        });
    h.onEachTick(
        () -> {
          if (pump[0] == null) return;
          int remaining = pump[0].localCell().amount(), delivered = car(h, stock[0]).fluid.amount();
          h.assertTrue(remaining + delivered == 1234, "pump/tanker liquid ledger");
          if (delivered == 1234) {
            h.assertTrue(pump[0].power.getEnergyStored() < 4000, "pump transfer was free");
            pump[0].onChunkUnloaded();
            cleanup(h, site);
            h.succeed();
          }
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 120)
  public static void chunkUnavailableQuarantinesOldCapabilitiesWithoutCopyingResources(
      GameTestHelper h) {
    var site = rail(h, 318);
    h.runAfterDelay(
        8,
        () -> {
          var entity = stock(h, site, StockSpec.CARGO, 30);
          var d = TrainData.get(h.getLevel());
          var t = d.owner(entity.getUUID());
          var car = car(h, entity);
          car.items.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 11));
          var port = entity.getCapability(Capabilities.ItemHandler.ENTITY);
          long key = net.minecraft.world.level.ChunkPos.asLong(entity.blockPosition());
          d.unloadChunk(key);
          h.assertTrue(
              port.extractItem(0, 11, false).isEmpty()
                  && car.items.getStackInSlot(0).getCount() == 11,
              "unavailable chunk exposed cargo");
          d.wakeChunk(key);
          TrainSimulation.tick(d, t);
          h.assertTrue(
              port.extractItem(0, 4, false).getCount() == 4
                  && car.items.getStackInSlot(0).getCount() == 7,
              "loaded canonical owner failed exact reattachment");
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 120)
  public static void nativeMenuPickupGhostSampleReturnAndStaleMenuPreserveEveryItem(
      GameTestHelper h) {
    var site = rail(h, 319);
    h.runAfterDelay(
        8,
        () -> {
          var entity = stock(h, site, StockSpec.CARGO, 30);
          var car = car(h, entity);
          var stack = new ItemStack(Items.IRON_INGOT, 31);
          stack.set(DataComponents.CUSTOM_NAME, Component.literal("native menu cargo"));
          car.items.setStackInSlot(0, stack);
          var menu = new TrainMenu(29, site.player.getInventory(), entity);
          site.player.containerMenu = menu;
          menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, site.player);
          h.assertTrue(
              menu.getCarried().getCount() == 31 && car.items.getStackInSlot(0).isEmpty(),
              "native menu pickup lost/duplicated cargo");
          menu.clickMenuButton(site.player, 0);
          menu.clicked(1, 0, net.minecraft.world.inventory.ClickType.PICKUP, site.player);
          h.assertTrue(
              car.filters[1].equals("minecraft:iron_ingot")
                  && menu.getCarried().getCount() == 31
                  && car.items.getStackInSlot(1).isEmpty(),
              "ghost sample consumed actual cursor cargo");
          menu.clicked(1, 1, net.minecraft.world.inventory.ClickType.QUICK_CRAFT, site.player);
          h.assertTrue(
              car.filters[1].equals("minecraft:iron_ingot") && menu.getCarried().getCount() == 31,
              "quick-craft phase was mistaken for right-click filter clear");
          menu.clickMenuButton(site.player, 0);
          menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, site.player);
          h.assertTrue(
              menu.getCarried().isEmpty()
                  && car.items.getStackInSlot(0).getCount() == 31
                  && car.items
                      .getStackInSlot(0)
                      .getHoverName()
                      .getString()
                      .equals("native menu cargo"),
              "native slot return lost components");
          site.player.containerMenu = site.player.inventoryMenu;
          menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, site.player);
          h.assertTrue(
              menu.getCarried().isEmpty() && car.items.getStackInSlot(0).getCount() == 31,
              "stale menu extracted actual contents");
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 120)
  public static void filledPortablePlacementConsumesItsSoleOwnerEvenInCreative(GameTestHelper h) {
    var site = rail(h, 320);
    h.runAfterDelay(
        8,
        () -> {
          var original = stock(h, site, StockSpec.CARGO, 30);
          var car = car(h, original);
          car.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 17));
          var data = TrainData.get(h.getLevel());
          site.player.setPos(original.getX() + 2, original.getY(), original.getZ());
          h.assertTrue(
              StockPlacement.pack(data, data.owner(car.id), car.id, site.player),
              "portable packing failed");
          ItemStack portable = ItemStack.EMPTY;
          for (int slot = 0; slot < site.player.getInventory().getContainerSize(); slot++) {
            var candidate = site.player.getInventory().getItem(slot);
            if (candidate.is(TrainContent.ITEMS.get(StockSpec.CARGO).get())
                && candidate.has(DataComponents.CUSTOM_DATA)) {
              portable = site.player.getInventory().removeItemNoUpdate(slot);
              break;
            }
          }
          h.assertTrue(!portable.isEmpty(), "one portable resource owner not returned");
          site.player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
          var restored = stock(h, site, StockSpec.CARGO, 40, portable);
          h.assertTrue(
              site.player.getMainHandItem().isEmpty()
                  && car(h, restored).items.getStackInSlot(0).getCount() == 17
                  && data.car(car.id) == null,
              "creative placement copied the filled portable owner");
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 140)
  public static void nonowningProjectionRemovalRestoresOneHandleAndKeepsCanonicalCargo(
      GameTestHelper h) {
    var site = rail(h, 321);
    var stockId = new UUID[1];
    var owner = new TrainCar[1];
    h.runAfterDelay(
        8,
        () -> {
          var entity = stock(h, site, StockSpec.CARGO, 30);
          stockId[0] = entity.getUUID();
          owner[0] = car(h, entity);
          owner[0].items.setStackInSlot(0, new ItemStack(Items.EMERALD, 13));
          entity.discard();
        });
    h.runAfterDelay(
        16,
        () -> {
          var entity = h.getLevel().getEntity(stockId[0]);
          var data = TrainData.get(h.getLevel());
          h.assertTrue(
              entity instanceof StockEntity
                  && !entity.isRemoved()
                  && data.car(stockId[0]) == owner[0]
                  && owner[0].items.getStackInSlot(0).getCount() == 13,
              "projection removal lost, copied or orphaned canonical cargo");
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 120)
  public static void staleBrokenConsistCannotRetireItsNewRemainderOrRecreateRecoveredCar(
      GameTestHelper h) {
    var site = rail(h, 322);
    h.runAfterDelay(
        8,
        () -> {
          var a = stock(h, site, StockSpec.CARGO, 30);
          var b = stock(h, site, StockSpec.CARGO, 23);
          var d = TrainData.get(h.getLevel());
          var old = d.owner(a.getUUID());
          car(h, a).items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 17));
          car(h, b).items.setStackInSlot(0, new ItemStack(Items.EMERALD, 19));
          RailwayData.get(h.getLevel()).remove(site.edge);
          site.player.setPos(b.getX() + 2, b.getY(), b.getZ());
          h.assertTrue(
              StockPlacement.pack(d, old, b.getUUID(), site.player),
              "first broken-car recovery failed");
          var remaining = d.owner(a.getUUID());
          site.player.setPos(a.getX() + 2, a.getY(), a.getZ());
          h.assertTrue(
              remaining != null
                  && remaining != old
                  && !StockPlacement.pack(d, old, a.getUUID(), site.player)
                  && d.owner(a.getUUID()) == remaining
                  && d.car(b.getUUID()) == null
                  && car(h, a).items.getStackInSlot(0).getCount() == 17,
              "stale broken aggregate replaced its canonical remainder or recreated an item owner");
          TrainSimulation.tick(d, remaining);
          cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "train_v0310", timeoutTicks = 160)
  public static void manualTurnoutIntentReplacesParkedLookaheadBeforeAnyCarEnters(
      GameTestHelper h) {
    var site = rail(h, 323);
    var origin = site.plan.curve.start;
    var start = new RailEnd(origin.x(), origin.y(), origin.z() - 96, new RailHeading(0));
    var straight =
        new RailPlan(
            RailCurve.plan(
                start, new RailEnd(origin.x(), origin.y(), origin.z() - 128, new RailHeading(0))));
    var right =
        new RailPlan(
            RailCurve.plan(
                start,
                new RailEnd(origin.x() + 24, origin.y(), origin.z() - 120, new RailHeading(4))));
    site.player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    var ids = new ArrayList<UUID>();
    for (var plan : List.of(straight, right)) {
      RailwayV30GameTests.prepare(h, plan);
      for (long key : plan.footprint.chunks()) {
        site.pins.add(key);
        h.getLevel().getChunk((int) key, (int) (key >> 32));
        h.getLevel().setChunkForced((int) key, (int) (key >> 32), true);
      }
      ids.add(RailwayV30GameTests.placed(h, plan, site.player));
    }
    site.player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    h.runAfterDelay(
        8,
        () -> {
          var entity = stock(h, site, StockSpec.LOCOMOTIVE, 90);
          var d = TrainData.get(h.getLevel());
          var t = d.owner(entity.getUUID());
          TrainSimulation.tick(d, t);
          h.assertTrue(
              t.path.legs().getLast().edge().edge().equals(ids.get(0)),
              "idle setup did not create straight lookahead");
          var driver =
              new ServerPlayer(
                  h.getLevel().getServer(),
                  h.getLevel(),
                  site.player.getGameProfile(),
                  ClientInformation.createDefault());
          driver.connection =
              new net.minecraft.server.network.ServerGamePacketListenerImpl(
                  h.getLevel().getServer(),
                  new net.minecraft.network.Connection(
                      net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                  driver,
                  net.minecraft.server.network.CommonListenerCookie.createInitial(
                      driver.getGameProfile(), false)) {
                public void send(net.minecraft.network.protocol.Packet<?> packet) {}

                public void teleport(double x, double y, double z, float yaw, float pitch) {}
              };
          h.assertTrue(
              driver.startRiding(entity) && TrainNetwork.control(driver, entity, 0, true, 1),
              "actual driver intent rejected");
          TrainSimulation.tick(d, t);
          h.assertTrue(
              t.path.legs().getLast().edge().edge().equals(ids.get(1))
                  && t.path.legs().getFirst().edge().edge().equals(site.edge)
                  && t.head == 90
                  && car(h, entity).spentJ == 0,
              "turnout intent changed occupied history, paid free fuel or retained the obsolete"
                  + " branch");
          driver.stopRiding();
          cleanup(h, site);
          for (var id : ids) RailwayData.get(h.getLevel()).remove(id);
          h.succeed();
        });
  }

  public static final class Disk extends SavedData {
    CompoundTag data = new CompoundTag();

    static Disk load(CompoundTag n, HolderLookup.Provider r) {
      var d = new Disk();
      d.data = n.getCompound("Checkpoint");
      return d;
    }

    public CompoundTag save(CompoundTag n, HolderLookup.Provider r) {
      n.put("Checkpoint", data);
      return n;
    }
  }

  @GameTest(template = "empty", batch = "train_v0310_disk", timeoutTicks = 120)
  public static void multiChunkCanonicalCarsFuelFluidComponentsSurviveIndependentJvm(
      GameTestHelper h) {
    var l = h.getLevel();
    var disk =
        l.getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(Disk::new, Disk::load),
                "factorycraft_v0310_train_checkpoint");
    var data = TrainData.get(l);
    if (!disk.data.hasUUID("Train")) {
      var site = rail(h, 315);
      h.runAfterDelay(
          8,
          () -> {
            var locomotive = stock(h, site, StockSpec.LOCOMOTIVE, 40);
            var cargo = stock(h, site, StockSpec.CARGO, 33);
            var fluid = stock(h, site, StockSpec.FLUID, 26);
            var train = data.owner(locomotive.getUUID());
            h.assertTrue(train.cars.size() == 3, "disk consist coupling");
            var a = car(h, locomotive);
            a.items.setStackInSlot(0, new ItemStack(Items.COAL, 7));
            a.fuelJ = 1234567;
            a.spentJ = 765433;
            var typed = new ItemStack(Items.DIAMOND, 31);
            typed.set(DataComponents.CUSTOM_NAME, Component.literal("v0310 canonical cargo"));
            car(h, cargo).items.setStackInSlot(0, typed);
            var water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 234567);
            water.set(DataComponents.CUSTOM_NAME, Component.literal("v0310 canonical water"));
            car(h, fluid).fluid.restore(new factorycraft.fluids.FluidKey(water), water.getAmount());
            train.speed = .03125;
            train.distance = 17.125;
            disk.data.putUUID("Train", train.id);
            disk.data.put(
                "State",
                data
                    .save(new CompoundTag(), l.registryAccess())
                    .getList("Trains", Tag.TAG_COMPOUND)
                    .stream()
                    .map(x -> (CompoundTag) x)
                    .filter(x -> x.getUUID("Id").equals(train.id))
                    .findFirst()
                    .orElseThrow()
                    .copy());
            disk.setDirty();
            data.setDirty();
            // Save a paid moving pose while unavailable; no offline physics or implicit refuel.
            data.unloadChunk(net.minecraft.world.level.ChunkPos.asLong(locomotive.blockPosition()));
            for (long key : site.pins) l.setChunkForced((int) key, (int) (key >> 32), false);
            System.out.println(
                "FACTORYCRAFT_V0310_TRAIN_DISK: SEEDED "
                    + train.id
                    + " cars=3 cargo=31 fluid=234567 fuel=1234567 spent=765433");
            h.succeed();
          });
    } else {
      var t = data.train(disk.data.getUUID("Train"));
      h.assertTrue(t != null && t.cars.size() == 3, "saved train owner missing");
      var expected = disk.data.getCompound("State");
      var saved =
          data
              .save(new CompoundTag(), l.registryAccess())
              .getList("Trains", Tag.TAG_COMPOUND)
              .stream()
              .map(x -> (CompoundTag) x)
              .filter(x -> x.getUUID("Id").equals(t.id))
              .findFirst()
              .orElseThrow();
      h.assertTrue(
          expected.equals(saved), "independent JVM altered canonical fuel/cargo/fluid/pose/IDs");
      h.assertTrue(
          t.cars.get(0).items.getStackInSlot(0).getCount() == 7
              && t.cars
                  .get(1)
                  .items
                  .getStackInSlot(0)
                  .getHoverName()
                  .getString()
                  .equals("v0310 canonical cargo")
              && t.cars.get(2).fluid.amount() == 234567,
          "component/quantity reload");
      System.out.println(
          "FACTORYCRAFT_V0310_TRAIN_DISK: VERIFIED " + t.id + " canonical state exact");
      h.succeed();
    }
  }

  private TrainV31GameTests() {}
}
