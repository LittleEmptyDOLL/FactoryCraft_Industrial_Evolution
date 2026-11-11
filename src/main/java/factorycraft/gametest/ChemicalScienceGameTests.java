package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.*;
import factorycraft.items.FluidCanisterItem;
import factorycraft.multiblock.*;
import factorycraft.petroleum.*;
import factorycraft.registry.Content;
import factorycraft.research.*;
import factorycraft.simulation.research.ResearchLedger;
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
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class ChemicalScienceGameTests {
  private static ResourceLocation id(String name) {
    return ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, name);
  }

  private static Item item(String name) {
    return BuiltInRegistries.ITEM.get(id(name));
  }

  private static MachineBlockEntity machine(GameTestHelper h, FactoryKind kind, int x) {
    var pos = h.absolutePos(new BlockPos(x, 2, 4));
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(kind).get().defaultBlockState());
    return (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
  }

  private static FluidStack acid(int amount) {
    var fluid = PetroleumFluids.ACID.stack(amount);
    var data = new CompoundTag();
    data.putInt("grade", 7);
    fluid.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    return fluid;
  }

  private static void processorInput(MachineBlockEntity machine) {
    machine.items.setStackInSlot(0, new ItemStack(item("electronic_circuit"), 20));
    machine.items.setStackInSlot(1, new ItemStack(item("advanced_circuit"), 2));
    machine.petroleum().cells.get(0).restore(new FluidKey(acid(1)), 50);
    if (!machine.petroleum().selectRecipe(id("processing_unit"))
        && machine.recipeAllowed(id("processing_unit")))
      throw new IllegalStateException("Missing processor recipe");
  }

  private static PetroleumMenu menu(
      GameTestHelper h,
      MachineBlockEntity machine,
      net.minecraft.world.entity.player.Player player) {
    player.setPos(
        machine.getBlockPos().getX() + .5,
        machine.getBlockPos().getY() + 1,
        machine.getBlockPos().getZ() - 1);
    var menu = new PetroleumMenu(4, player.getInventory(), machine, machine.petroleum().data);
    player.containerMenu = menu;
    menu.setCarried(new ItemStack(Content.FLUID_CANISTER.get()));
    return menu;
  }

  private static void loadCells(GameTestHelper h, MachineBlockEntity machine) {
    for (var p :
        MachineProfile.of(machine.getBlockState()).cells(machine.getBlockPos(), Direction.NORTH))
      h.getLevel().getBlockEntity(p).onLoad();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void everyMachineProfileHasBundledGeometryIncludingNumericTierNames(
      GameTestHelper h) {
    try (var stream =
        ChemicalScienceGameTests.class.getResourceAsStream(
            "/assets/factorycraft/models/block/machine_shell.json")) {
      if (stream == null) throw new IllegalStateException("Missing machine geometry");
      var json =
          com.google.gson.JsonParser.parseReader(
                  new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
      var names = new HashSet<String>();
      for (var machine : json.getAsJsonArray("machines"))
        names.add(machine.getAsJsonObject().get("profile").getAsString());
      for (var profile : MachineProfile.values())
        h.assertTrue(names.contains(profile.id), "No bundled geometry for " + profile.id);
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 380)
  public static void steelSmeltingConsumesFivePlatesAndExactEnergy(GameTestHelper h) {
    var furnace = machine(h, FactoryKind.FURNACE, 4);
    h.runAfterDelay(
        5,
        () -> {
          furnace.items.setStackInSlot(0, new ItemStack(item("iron_plate"), 5));
          furnace.power.restore(5000);
        });
    h.runAfterDelay(
        340,
        () -> {
          h.assertTrue(
              furnace.items.getStackInSlot(0).isEmpty()
                  && furnace.items.getStackInSlot(4).is(Content.STEEL_PLATE.get())
                  && furnace.items.getStackInSlot(4).getCount() == 1
                  && furnace.power.getEnergyStored() == 1160,
              "steel material/FE ledger failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void secondAssemblerAlsoExecutesOriginalDryRecipes(GameTestHelper h) {
    var m = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 4);
    h.runAfterDelay(
        5,
        () -> {
          m.items.setStackInSlot(0, new ItemStack(item("iron_plate"), 2));
          m.power.restore(1600);
          m.petroleum().selectRecipe(id("iron_gear"));
        });
    h.runAfterDelay(
        65,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).is(item("iron_gear"))
                  && m.items.getStackInSlot(0).isEmpty(),
              "tier two lost dry assembler recipes");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 280)
  public static void fluidAssemblerProducesElectricEnginesAndProcessors(GameTestHelper h) {
    var engines = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 3);
    var processors = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 10);
    h.runAfterDelay(
        5,
        () -> {
          engines.items.setStackInSlot(0, new ItemStack(Content.ENGINE_UNIT.get()));
          engines.items.setStackInSlot(1, new ItemStack(item("electronic_circuit"), 2));
          engines
              .petroleum()
              .cells
              .get(0)
              .restore(new FluidKey(PetroleumFluids.LUBRICANT.stack(1)), 150);
          engines.petroleum().selectRecipe(id("electric_engine_unit"));
          engines.power.restore(7000);
          processorInput(processors);
          processors.power.restore(9000);
        });
    h.runAfterDelay(
        230,
        () -> {
          h.assertTrue(
              engines.items.getStackInSlot(4).is(Content.ELECTRIC_ENGINE_UNIT.get())
                  && engines.power.getEnergyStored() == 1000
                  && processors.items.getStackInSlot(4).is(Content.PROCESSING_UNIT.get())
                  && processors.power.getEnergyStored() == 1000,
              "fluid assembler result/FE mismatch");
          for (var m : List.of(engines, processors))
            h.assertTrue(
                m.items.getStackInSlot(0).isEmpty()
                    && m.items.getStackInSlot(1).isEmpty()
                    && m.petroleum().cells.get(0).amount() == 0,
                "mixed assembly did not consume exact inputs");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 380)
  public static void fluidAssemblyPausesForEnergyFullOutputAndPartUnload(GameTestHelper h) {
    var m = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 4);
    h.runAfterDelay(5, () -> processorInput(m));
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              m.petroleum().data.get(0) == 0 && m.petroleum().cells.get(0).amount() == 50,
              "unpowered assembly consumed resources");
          for (int s = 4; s < 8; s++)
            m.items.setStackInSlot(s, new ItemStack(Items.COBBLESTONE, 64));
          m.power.restore(8000);
        });
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(
              m.power.getEnergyStored() == 8000 && m.petroleum().data.get(0) == 0,
              "blocked output spent FE");
          m.items.setStackInSlot(4, ItemStack.EMPTY);
        });
    h.runAfterDelay(
        65,
        () -> {
          var part =
              (CasingBlockEntity) h.getLevel().getBlockEntity(m.assembly().positions().getLast());
          var fluid =
              h.getLevel()
                  .getCapability(
                      Capabilities.FluidHandler.BLOCK,
                      m.petroleum().nozzle(0),
                      m.petroleum().direction(0));
          part.onChunkUnloaded();
          int work = m.petroleum().data.get(0), energy = m.power.getEnergyStored();
          h.assertTrue(
              fluid.fill(PetroleumFluids.ACID.stack(300), FluidAction.EXECUTE) == 0
                  && m.automation(null).extractItem(4, 1, false).isEmpty(),
              "cached unloaded capability escaped guard");
          h.runAfterDelay(
              25,
              () -> {
                h.assertTrue(
                    m.petroleum().data.get(0) == work && m.power.getEnergyStored() == energy,
                    "unloaded assembler advanced");
                part.onLoad();
              });
        });
    h.runAfterDelay(
        315,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).is(Content.PROCESSING_UNIT.get())
                  && m.power.getEnergyStored() == 0,
              "paused assembler failed to resume canonical work");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void processorProductionWaitsForActualTeamUnlock(GameTestHelper h) {
    var m = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 4);
    var scope = ResearchGameTests.personal(h, "processor-gate");
    m.bindResearch(scope);
    h.runAfterDelay(
        5,
        () -> {
          processorInput(m);
          m.power.restore(8000);
        });
    h.runAfterDelay(
        35,
        () -> {
          h.assertTrue(
              m.power.getEnergyStored() == 8000
                  && m.items.getStackInSlot(0).getCount() == 20
                  && m.petroleum().cells.get(0).amount() == 50,
              "locked processor spent resources");
          var ledger = ResearchData.get(h.getLevel().getServer()).team(scope);
          var tech = ResearchCatalog.get(h.getLevel()).entry(id("advanced_electronics_2")).logic();
          ledger.restore(
              "",
              Map.of(id("chemical_science").toString(), 1, id("automation_2").toString(), 1),
              Map.of());
          h.assertTrue(ledger.select(tech), "blue research prerequisites rejected");
          for (int n = 0; n < tech.cost(0); n++)
            h.assertTrue(ledger.completeUnit(tech, () -> {}), "blue research unit rejected");
          ResearchData.get(h.getLevel().getServer()).setDirty();
        });
    h.runAfterDelay(
        260,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).is(Content.PROCESSING_UNIT.get())
                  && m.power.getEnergyStored() == 0,
              "unlocked processor failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 240)
  public static void twoLabsSpendOnlyOneSetOfThreePacksForLastBlueUnit(GameTestHelper h) {
    var scope = ResearchGameTests.personal(h, "last-blue");
    var catalog = ResearchCatalog.get(h.getLevel());
    var tech = catalog.entry(id("advanced_electronics_2")).logic();
    var ledger = ResearchData.get(h.getLevel().getServer()).team(scope);
    ledger.restore(
        tech.id(),
        Map.of(id("chemical_science").toString(), 1, id("automation_2").toString(), 1),
        Map.of(tech.id(), new ResearchLedger.Progress(tech.signature(), 49)));
    var labs = new ArrayList<LabBlockEntity>();
    for (int x : new int[] {3, 9}) {
      var p = h.absolutePos(new BlockPos(x, 2, 4));
      StructurePlacement.place(
          h.getLevel(), p, Content.FACTORY.get(FactoryKind.LAB).get().defaultBlockState());
      var lab = (LabBlockEntity) h.getLevel().getBlockEntity(p);
      lab.bind(scope);
      labs.add(lab);
    }
    h.runAfterDelay(
        5,
        () -> {
          for (var lab : labs) {
            lab.items.setStackInSlot(0, new ItemStack(Content.SCIENCE_RED.get()));
            lab.items.setStackInSlot(1, new ItemStack(Content.SCIENCE_GREEN.get()));
            lab.items.setStackInSlot(2, new ItemStack(Content.SCIENCE_BLUE.get()));
            lab.power.restore(3000);
          }
        });
    h.runAfterDelay(
        155,
        () -> {
          h.assertTrue(ledger.level(tech.id()) == 1, "three-pack technology did not complete");
          for (int slot = 0; slot < 3; slot++)
            h.assertTrue(
                labs.get(0).items.getStackInSlot(slot).getCount()
                        + labs.get(1).items.getStackInSlot(slot).getCount()
                    == 1,
                "concurrent labs overspent final three-pack unit");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void recoveryKeepsPartialVolumeComponentsAndBothMenuOwners(GameTestHelper h) {
    var m = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 4);
    loadCells(h, m);
    var a = h.makeMockPlayer(GameType.SURVIVAL);
    var b = h.makeMockPlayer(GameType.SURVIVAL);
    var first = menu(h, m, a);
    var second = menu(h, m, b);
    m.petroleum().cells.get(0).fill(new FluidKey(acid(1)), 37, false);
    h.assertTrue(
        first.clickMenuButton(a, 1) && !second.clickMenuButton(b, 1),
        "two menus duplicated recovered fluid");
    var fluid = FluidCanisterItem.handler(first.getCarried()).getFluid();
    h.assertTrue(
        FluidStack.matches(fluid, acid(37))
            && m.petroleum().cells.get(0).amount() == 0
            && FluidCanisterItem.handler(second.getCarried()).getFluid().isEmpty(),
        "partial recovery lost fluid components or changed another cursor");
    var encoded = first.getCarried().save(h.getLevel().registryAccess());
    var restored = ItemStack.parse(h.getLevel().registryAccess(), encoded).orElseThrow();
    h.assertTrue(
        FluidStack.matches(
            FluidUtil.getFluidHandler(restored).orElseThrow().getFluidInTank(0), acid(37)),
        "native item persistence lost fluid content");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void recoveryPreservesCapacityRemaindersOverflowAndMismatchedFluid(
      GameTestHelper h) {
    var m = machine(h, FactoryKind.CHEMICAL_PLANT, 4);
    loadCells(h, m);
    var p = h.makeMockPlayer(GameType.SURVIVAL);
    var menu = menu(h, m, p);
    var source = m.petroleum().cells.get(0);
    source.restore(new FluidKey(acid(1)), 37);
    FluidCanisterItem.handler(menu.getCarried()).fill(acid(3995), FluidAction.EXECUTE);
    h.assertTrue(
        menu.clickMenuButton(p, 1)
            && source.amount() == 32
            && FluidCanisterItem.handler(menu.getCarried()).getFluid().getAmount() == 4000,
        "canister capacity remainder lost");
    h.assertTrue(
        !menu.clickMenuButton(p, 1) && source.amount() == 32, "full canister drained source");
    menu.setCarried(new ItemStack(Content.FLUID_CANISTER.get()));
    FluidCanisterItem.handler(menu.getCarried())
        .fill(PetroleumFluids.LUBRICANT.stack(1), FluidAction.EXECUTE);
    h.assertTrue(
        !menu.clickMenuButton(p, 1) && source.amount() == 32, "mismatched canister mixed fluids");
    menu.setCarried(new ItemStack(Content.FLUID_CANISTER.get()));
    source.restore(new FluidKey(acid(1)), 40001);
    h.assertTrue(
        menu.clickMenuButton(p, 1)
            && source.amount() == 36001
            && FluidStack.matches(
                FluidCanisterItem.handler(menu.getCarried()).getFluid(), acid(4000)),
        "restored overflow truncated");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void staleDistantSpectatorAndUnloadedRecoveryCannotMutate(GameTestHelper h) {
    var m = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 4);
    loadCells(h, m);
    var p = h.makeMockPlayer(GameType.SURVIVAL);
    var menu = menu(h, m, p);
    m.petroleum().cells.get(0).restore(new FluidKey(acid(1)), 37);
    p.containerMenu = p.inventoryMenu;
    h.assertTrue(!menu.clickMenuButton(p, 1), "stale menu recovered fluid");
    p.containerMenu = menu;
    p.setPos(p.getX() + 30, p.getY(), p.getZ());
    h.assertTrue(!menu.clickMenuButton(p, 1), "distant menu recovered fluid");
    var observer = h.makeMockPlayer(GameType.SPECTATOR);
    var spectatorMenu = menu(h, m, observer);
    h.assertTrue(!spectatorMenu.clickMenuButton(observer, 1), "spectator recovered fluid");
    menu = menu(h, m, p);
    var part = (CasingBlockEntity) h.getLevel().getBlockEntity(m.assembly().positions().getLast());
    part.onChunkUnloaded();
    h.assertTrue(
        !menu.clickMenuButton(p, 1) && sourceUnchanged(m), "unloaded menu recovered fluid");
    m.items.setStackInSlot(4, new ItemStack(Content.PROCESSING_UNIT.get()));
    menu.clicked(4, 0, ClickType.PICKUP, p);
    h.assertTrue(
        m.items.getStackInSlot(4).getCount() == 1
            && menu.getCarried().is(Content.FLUID_CANISTER.get()),
        "unloaded ordinary slot click escaped guard");
    part.onLoad();
    h.assertTrue(menu.clickMenuButton(p, 1), "reloaded recovery did not resume");
    h.succeed();
  }

  private static boolean sourceUnchanged(MachineBlockEntity m) {
    return m.petroleum().cells.get(0).amount() == 37;
  }

  @GameTest(template = "empty", timeoutTicks = 4400)
  public static void poweredComponentLineBuildsEnginesThenChemicalScienceWithoutManualTransfers(
      GameTestHelper h) {
    var scope = ResearchGameTests.personal(h, "chemical-line");
    var ledger = ResearchData.get(h.getLevel().getServer()).team(scope);
    ledger.restore(
        "", Map.of(id("engine").toString(), 1, id("chemical_science").toString(), 1), Map.of());
    var origin = h.absolutePos(new BlockPos(1, 2, 1));
    var chest = factorycraft.diagnostics.ChemicalScienceFactory.build(h.getLevel(), origin, scope);
    h.runAfterDelay(
        4200,
        () -> {
          int delivered = 0;
          for (int i = 0; i < chest.getContainerSize(); i++)
            if (chest.getItem(i).is(Content.SCIENCE_BLUE.get()))
              delivered += chest.getItem(i).getCount();
          h.assertTrue(
              delivered == 4,
              "component-fed engine/science line did not deliver all four blue packs: "
                  + delivered);
          for (var pos : List.of(origin.offset(4, 0, 0), origin.offset(11, 0, 0))) {
            var input = (ChestBlockEntity) h.getLevel().getBlockEntity(pos);
            h.assertTrue(input.isEmpty(), "finite input chest still holds unprocessed components");
          }
          for (var pos : List.of(origin.offset(4, 0, 2), origin.offset(11, 0, 2))) {
            var machine = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
            for (int i = 0; i < 8; i++)
              h.assertTrue(
                  machine.items.getStackInSlot(i).isEmpty(),
                  "component census found residual intermediate");
          }
          var tracked =
              Set.of(
                  item("steel_plate"),
                  item("iron_gear"),
                  item("fluid_pipe"),
                  item("advanced_circuit"),
                  item("sulfur"),
                  Content.ENGINE_UNIT.get(),
                  Content.SCIENCE_BLUE.get());
          var census = new HashMap<Item, Integer>();
          for (var pos : BlockPos.betweenClosed(origin, origin.offset(29, 7, 12))) {
            var be = h.getLevel().getBlockEntity(pos);
            if (be instanceof ChestBlockEntity container) {
              for (int slot = 0; slot < container.getContainerSize(); slot++)
                countTracked(census, tracked, container.getItem(slot));
            } else if (be instanceof FactoryBlockEntity factory
                && !(be instanceof CasingBlockEntity)) {
              var inventory = factory.inventory();
              if (inventory != null)
                for (int slot = 0; slot < inventory.getSlots(); slot++)
                  countTracked(census, tracked, inventory.getStackInSlot(slot));
            }
          }
          h.assertTrue(
              census.equals(Map.of(Content.SCIENCE_BLUE.get(), 4)),
              "component census found duplicated/held intermediates: " + census);
          System.out.println(
              "FACTORYCRAFT_CHEMICAL_SCIENCE_LINE: VERIFIED six engines -> four blue packs;"
                  + " complete component/belt/hand ledger");
          h.succeed();
        });
  }

  private static void countTracked(Map<Item, Integer> census, Set<Item> tracked, ItemStack stack) {
    if (!stack.isEmpty() && tracked.contains(stack.getItem()))
      census.merge(stack.getItem(), stack.getCount(), Integer::sum);
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void recoveredSubBucketFluidCanReturnThroughAPhysicalInput(GameTestHelper h) {
    var m = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 4);
    loadCells(h, m);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    var menu = menu(h, m, player);
    m.petroleum().cells.get(0).restore(new FluidKey(acid(1)), 37);
    h.assertTrue(menu.clickMenuButton(player, 1), "could not recover input");
    player.setItemInHand(InteractionHand.MAIN_HAND, menu.getCarried());
    menu.setCarried(ItemStack.EMPTY);
    h.assertTrue(
        FluidUtil.interactWithFluidHandler(
            player,
            InteractionHand.MAIN_HAND,
            h.getLevel(),
            m.petroleum().nozzle(0),
            m.petroleum().direction(0)),
        "native canister interaction failed");
    h.assertTrue(
        FluidStack.matches(m.petroleum().stored(0), acid(37))
            && FluidCanisterItem.handler(player.getMainHandItem()).getFluid().isEmpty(),
        "returning 37 mB lost volume/components");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 280)
  public static void physicalPipeFeedsFluidAssemblerAndKeepsUnusedLubricant(GameTestHelper h) {
    var m = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 4);
    var pos = m.petroleum().nozzle(0).relative(m.petroleum().direction(0));
    h.getLevel()
        .setBlockAndUpdate(pos, Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    h.runAfterDelay(
        5,
        () -> {
          ((FluidBlockEntity) h.getLevel().getBlockEntity(pos))
              .cell()
              .fill(new FluidKey(PetroleumFluids.LUBRICANT.stack(1)), 520, false);
          m.items.setStackInSlot(0, new ItemStack(Content.ENGINE_UNIT.get()));
          m.items.setStackInSlot(1, new ItemStack(item("electronic_circuit"), 2));
          m.power.restore(6000);
        });
    h.runAfterDelay(
        235,
        () -> {
          var pipe = (FluidBlockEntity) h.getLevel().getBlockEntity(pos);
          h.assertTrue(
              m.items.getStackInSlot(4).is(Content.ELECTRIC_ENGINE_UNIT.get())
                  && m.power.getEnergyStored() == 0
                  && m.petroleum().cells.get(0).amount() + pipe.cell().amount() == 370,
              "physical fluid assembly lost unused lubricant");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 280)
  public static void liveFluidAssemblerReconstructionKeepsCanonicalPartialWork(GameTestHelper h) {
    var m = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 4);
    var pos = m.getBlockPos();
    h.runAfterDelay(
        5,
        () -> {
          processorInput(m);
          m.power.restore(8000);
        });
    h.runAfterDelay(
        35,
        () -> {
          var tag = m.saveWithFullMetadata(h.getLevel().registryAccess());
          h.assertTrue(
              tag.getCompound("Petroleum").getInt("Progress") > 0, "no partial work captured");
          var restored =
              (MachineBlockEntity)
                  BlockEntity.loadStatic(
                      pos, m.getBlockState(), tag, h.getLevel().registryAccess());
          m.setRemoved();
          h.getLevel().setBlockEntity(restored);
          restored.onLoad();
        });
    h.runAfterDelay(
        235,
        () -> {
          var restored = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
          h.assertTrue(
              restored.items.getStackInSlot(4).is(Content.PROCESSING_UNIT.get())
                  && restored.power.getEnergyStored() == 0
                  && restored.petroleum().cells.get(0).amount() == 0,
              "live reconstruction restarted/duplicated assembly");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void portableFluidAssemblerKeepsCanisterFluidAndFreshPartIdentity(
      GameTestHelper h) {
    var m = machine(h, FactoryKind.ADVANCED_ASSEMBLER, 4);
    h.runAfterDelay(
        5,
        () -> {
          processorInput(m);
          m.power.restore(7312);
          m.petroleum().cells.get(0).restore(new FluidKey(acid(1)), 37);
          var canister = new ItemStack(Content.FLUID_CANISTER.get());
          FluidCanisterItem.handler(canister).fill(acid(71), FluidAction.EXECUTE);
          m.items.setStackInSlot(7, canister);
          var old = m.assembly().identity();
          h.getLevel().destroyBlock(m.assembly().positions().getLast(), true);
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new AABB(m.getBlockPos()).inflate(4));
          h.assertTrue(drops.size() == 1, "portable fluid assembler copied resources");
          var player = h.makeMockPlayer(GameType.SURVIVAL);
          player.setYRot(Direction.SOUTH.toYRot());
          player.setItemInHand(InteractionHand.MAIN_HAND, drops.getFirst().getItem().copy());
          drops.getFirst().discard();
          var target = h.absolutePos(new BlockPos(11, 2, 4));
          h.assertTrue(
              PlacementTestSupport.place(player, target).consumesAction(),
              "portable fluid assembler would not place");
          var restored = (MachineBlockEntity) h.getLevel().getBlockEntity(target);
          h.assertTrue(
              !old.equals(restored.assembly().identity())
                  && restored.items.getStackInSlot(0).getCount() == 20
                  && restored.items.getStackInSlot(1).getCount() == 2
                  && restored.power.getEnergyStored() == 7312
                  && FluidStack.matches(restored.petroleum().stored(0), acid(37))
                  && FluidStack.matches(
                      FluidCanisterItem.handler(restored.items.getStackInSlot(7)).getFluid(),
                      acid(71)),
              "portable machine changed resources/identity");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void canisterComponentsAndNewMachineRecipeRoundTripOnNativeWire(GameTestHelper h) {
    var stack = new ItemStack(Content.FLUID_CANISTER.get());
    FluidCanisterItem.handler(stack).fill(acid(370), FluidAction.EXECUTE);
    var recipe =
        h.getLevel().getRecipeManager().getAllRecipesFor(Content.CHEMISTRY.get()).stream()
            .filter(r -> r.id().equals(id("processing_unit")))
            .findFirst()
            .orElseThrow()
            .value();
    var ops =
        net.minecraft.resources.RegistryOps.create(
            com.mojang.serialization.JsonOps.INSTANCE, h.getLevel().registryAccess());
    var json =
        factorycraft.recipes.ChemicalRecipe.CODEC.codec().encodeStart(ops, recipe).getOrThrow();
    h.assertTrue(
        factorycraft.recipes.ChemicalRecipe.CODEC
            .codec()
            .parse(ops, json)
            .getOrThrow()
            .machine()
            .equals("assembling_machine_2"),
        "new mixed recipe JSON rejected");
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      ItemStack.STREAM_CODEC.encode(buffer, stack);
      var copy = ItemStack.STREAM_CODEC.decode(buffer);
      h.assertTrue(
          FluidStack.matches(FluidCanisterItem.handler(copy).getFluid(), acid(370)),
          "native item wire lost component fluid");
      var codec = Content.CHEMISTRY_SERIALIZER.get().streamCodec();
      codec.encode(buffer, recipe);
      var restored = codec.decode(buffer);
      h.assertTrue(
          restored.machine().equals("assembling_machine_2")
              && restored.fluids().getFirst().amount() == 50
              && restored.items().getFirst().count() == 20,
          "new mixed recipe wire changed");
    } finally {
      buffer.release();
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void fluidAssemblerAndCanisterPersistAcrossIndependentServerProcesses(
      GameTestHelper h) {
    var pos = new BlockPos(687, 70, 687);
    var cells = MachineProfile.ADVANCED_ASSEMBLER.cells(pos, Direction.NORTH);
    for (var p : cells) h.getLevel().getChunkAt(p);
    if (cells.stream().allMatch(p -> h.getLevel().getBlockEntity(p) == null)) {
      StructurePlacement.place(
          h.getLevel(),
          pos,
          Content.FACTORY.get(FactoryKind.ADVANCED_ASSEMBLER).get().defaultBlockState());
      var m = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
      loadCells(h, m);
      processorInput(m);
      m.petroleum().cells.get(0).restore(new FluidKey(acid(1)), 731);
      m.power.restore(7992);
      var canister = new ItemStack(Content.FLUID_CANISTER.get());
      FluidCanisterItem.handler(canister).fill(acid(37), FluidAction.EXECUTE);
      m.items.setStackInSlot(7, canister);
      for (int tick = 0; tick < 17; tick++) m.petroleum().tick();
      m.setChanged();
      h.assertTrue(
          m.petroleum().data.get(0) == 17 && m.power.getEnergyStored() == 7312,
          "disk seed did not execute actual partial recipe");
      System.out.println(
          "FACTORYCRAFT_CHEMICAL_SCIENCE_DISK: SEEDED canonical partial assembly and component"
              + " canister across four chunks");
    } else {
      h.assertTrue(
          h.getLevel().getBlockEntity(pos) instanceof MachineBlockEntity,
          "partial chemical fixture; refusing to reseed");
      var m = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
      var tag = m.saveWithFullMetadata(h.getLevel().registryAccess()).getCompound("Petroleum");
      h.assertTrue(
          m.items.getStackInSlot(0).getCount() == 20
              && m.items.getStackInSlot(1).getCount() == 2
              && m.power.getEnergyStored() == 7312
              && FluidStack.matches(m.petroleum().stored(0), acid(731))
              && tag.getInt("Progress") == 17
              && !tag.getString("Active").isEmpty()
              && tag.getString("SelectedRecipe").equals(id("processing_unit").toString())
              && FluidStack.matches(
                  FluidCanisterItem.handler(m.items.getStackInSlot(7)).getFluid(), acid(37)),
          "disk assembly/canister state changed");
      for (var p : m.assembly().positions())
        h.assertTrue(
            h.getLevel().getBlockEntity(p) instanceof CasingBlockEntity part
                && part.boundTo(pos, m.assembly().identity()),
            "disk four-chunk ownership changed");
      System.out.println(
          "FACTORYCRAFT_CHEMICAL_SCIENCE_DISK: VERIFIED exact partial recipe, items, native"
              + " components, canister, FE and part identity");
    }
    for (var p : cells) {
      h.getLevel().getBlockEntity(p).onChunkUnloaded();
      h.getLevel().getChunkAt(p).setUnsaved(true);
    }
    h.succeed();
  }
}
