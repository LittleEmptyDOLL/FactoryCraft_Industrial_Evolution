package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.energy.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.simulation.power.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class PowerV20GameTests {
  private static final Map<net.minecraft.world.level.Level, BlockPos> RUNS = new WeakHashMap<>();

  /** Monotonic diagnostic allocator: fresh transient arenas without resetting old evidence. */
  private static final class Arenas extends net.minecraft.world.level.saveddata.SavedData {
    int next;

    static Arenas load(net.minecraft.nbt.CompoundTag tag, HolderLookup.Provider registry) {
      var value = new Arenas();
      value.next = tag.getInt("Next");
      return value;
    }

    public net.minecraft.nbt.CompoundTag save(
        net.minecraft.nbt.CompoundTag tag, HolderLookup.Provider registry) {
      tag.putInt("Next", next);
      return tag;
    }

    BlockPos claim() {
      if (next < 0 || next >= 100000)
        throw new IllegalStateException("Diagnostic arena range exhausted");
      int index = next++;
      setDirty();
      return new BlockPos(1000000, 70, 2000000 + index * 128);
    }
  }

  private static BlockPos site(GameTestHelper h, int id) {
    var base =
        RUNS.computeIfAbsent(
            h.getLevel(),
            level ->
                h.getLevel()
                    .getDataStorage()
                    .computeIfAbsent(
                        new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                            Arenas::new, Arenas::load),
                        "factorycraft_power_v0200_arenas")
                    .claim());
    return base.east(id * 4096);
  }

  private static FactoryBlockEntity place(
      GameTestHelper h, MachineProfile profile, BlockPos pos, Direction face) {
    var state =
        BuiltInRegistries.BLOCK
            .get(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, profile.id))
            .defaultBlockState()
            .setValue(FactoryBlock.FACING, face);
    for (var cell : profile.cells(pos, face)) h.getLevel().getChunkAt(cell);
    StructurePlacement.place(h.getLevel(), pos, state);
    for (var cell : profile.cells(pos, face)) h.getLevel().getBlockEntity(cell).onLoad();
    var be = (FactoryBlockEntity) h.getLevel().getBlockEntity(pos);
    h.assertTrue(AssemblyAccess.owner(be).assembly().ready(), "unready fixture " + profile);
    return be;
  }

  private static PoleBlockEntity pole(GameTestHelper h, MachineProfile profile, BlockPos pos) {
    return (PoleBlockEntity) place(h, profile, pos, Direction.NORTH);
  }

  private static net.minecraft.world.entity.player.Player player(
      GameTestHelper h, BlockPos cell, ItemStack held) {
    var p = h.makeMockPlayer(GameType.SURVIVAL);
    p.setPos(cell.getX() + .5, cell.getY(), cell.getZ() + 2);
    p.setItemInHand(InteractionHand.MAIN_HAND, held);
    return p;
  }

  private static InteractionResult click(net.minecraft.world.entity.player.Player p, BlockPos at) {
    p.setPos(at.getX() + .5, at.getY(), at.getZ() + 2);
    var c =
        new UseOnContext(
            p,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(at.getCenter(), Direction.SOUTH, at, false));
    return p.getMainHandItem().getItem().onItemUseFirst(p.getMainHandItem(), c);
  }

  private static void completeTechnology(
      factorycraft.research.ResearchCatalog catalog,
      factorycraft.simulation.research.ResearchLedger ledger,
      ResourceLocation id) {
    if (ledger.level(id.toString()) > 0) return;
    var entry = Objects.requireNonNull(catalog.entry(id), "Missing technology " + id);
    for (var parent : entry.recipe().prerequisites()) completeTechnology(catalog, ledger, parent);
    if (!ledger.select(entry.logic()))
      throw new IllegalStateException("Unselectable technology " + id);
    for (int n = 0; n < entry.recipe().units(); n++)
      if (!ledger.completeUnit(entry.logic(), () -> {}))
        throw new IllegalStateException("Rejected unit " + id);
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void actualPoleRecipesRespectResearchAndProduceAfterUnlock(GameTestHelper h) {
    var catalog = factorycraft.research.ResearchCatalog.get(h.getLevel());
    var dist1 =
        ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "electric_energy_distribution_1");
    var dist2 =
        ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "electric_energy_distribution_2");
    var first = catalog.entry(dist1).recipe();
    var second = catalog.entry(dist2).recipe();
    h.assertTrue(
        first.units() == 120
            && first.ticks() == 600
            && first.packs().size() == 2
            && second.units() == 100
            && second.ticks() == 900
            && second.packs().size() == 3,
        "distribution research costs/time differ from reference");
    var scope = ResearchGameTests.personal(h, "pole-production");
    var ledger = factorycraft.research.ResearchData.get(h.getLevel().getServer()).team(scope);
    var machines = new ArrayList<MachineBlockEntity>();
    var specs = PoleSpec.values();
    for (int i = 0; i < specs.length; i++) {
      var machine =
          (MachineBlockEntity)
              place(h, MachineProfile.ASSEMBLER, site(h, 22).east(i * 8), Direction.NORTH);
      machine.bindResearch(scope);
      var id = ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, specs[i].id);
      int target = 0;
      for (int j = 0; j < machine.recipes().size(); j++)
        if (machine.recipes().get(j).id().equals(id)) target = j + 1;
      h.assertTrue(target > 0, "missing assembler recipe " + id);
      for (int j = 0; j < target; j++) machine.cycleRecipe();
      var recipe = machine.recipes().get(target - 1).value();
      for (int j = 0; j < recipe.inputs().size(); j++) {
        var ingredient = recipe.inputs().get(j);
        var stack = ingredient.ingredient().getItems()[0].copy();
        stack.setCount(ingredient.count());
        machine.items.setStackInSlot(j, stack);
      }
      machine.power.restore(1000);
      machines.add(machine);
      if (i > 0) {
        machine.tick();
        h.assertTrue(
            machine.power.getEnergyStored() == 1000 && machine.items.getStackInSlot(4).isEmpty(),
            "locked pole spent resources");
      }
    }
    var small = machines.getFirst();
    for (int n = 0; n < 10; n++) small.tick();
    h.assertTrue(
        small.items.getStackInSlot(4).is(Content.FACTORY.get(FactoryKind.POLE).get().asItem())
            && small.items.getStackInSlot(4).getCount() == 2
            && small.power.getEnergyStored() == 880,
        "starter pole production failed");
    completeTechnology(catalog, ledger, dist1);
    for (int i = 1; i <= 2; i++) {
      var m = machines.get(i);
      for (int n = 0; n < 20; n++) m.tick();
      h.assertTrue(
          m.items
                  .getStackInSlot(4)
                  .is(
                      BuiltInRegistries.BLOCK
                          .get(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, specs[i].id))
                          .asItem())
              && m.items.getStackInSlot(4).getCount() == 1
              && m.power.getEnergyStored() == 760,
          "unlocked pole did not produce once with exact FE");
    }
    var sub = machines.getLast();
    sub.tick();
    h.assertTrue(
        sub.power.getEnergyStored() == 1000 && sub.items.getStackInSlot(4).isEmpty(),
        "distribution 1 bypassed substation gate");
    completeTechnology(catalog, ledger, dist2);
    for (int n = 0; n < 20; n++) sub.tick();
    h.assertTrue(
        sub.items.getStackInSlot(4).is(Content.FACTORY.get(FactoryKind.SUBSTATION).get().asItem())
            && sub.power.getEnergyStored() == 760,
        "distribution 2 did not unlock substation");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 120)
  public static void fourPoleTypesPlaceFromOneItemInEveryRotation(GameTestHelper h) {
    int n = 0;
    for (var profile :
        List.of(
            MachineProfile.SMALL_POLE,
            MachineProfile.MEDIUM_POLE,
            MachineProfile.BIG_POLE,
            MachineProfile.SUBSTATION))
      for (var face : Direction.Plane.HORIZONTAL) {
        var at = site(h, 0).offset((n++ % 4) * 10, 0, (n / 4) * 10);
        for (var p : profile.cells(at, face)) h.getLevel().getChunkAt(p);
        var p =
            player(
                h,
                at,
                new ItemStack(
                    BuiltInRegistries.BLOCK.get(
                        ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, profile.id)),
                    2));
        p.setYRot(face.getOpposite().toYRot());
        h.assertTrue(
            PlacementTestSupport.place(p, at).consumesAction()
                && p.getMainHandItem().getCount() == 1,
            "one-item rotated pole failed " + profile + face);
        var owner = (PoleBlockEntity) h.getLevel().getBlockEntity(at);
        h.assertTrue(
            owner != null
                && owner.assembly().positions().size()
                    == profile.layout.width() * profile.layout.depth() * profile.layout.height()
                        - 1,
            "wrong pole volume");
        h.assertTrue(
            Math.abs(PoleGeometry.attachment(owner).y - (at.getY() + owner.spec().height - .12))
                < 1e-6,
            "wire attachment is not at the model head");
        for (var part : owner.assembly().positions()) {
          var be = (CasingBlockEntity) h.getLevel().getBlockEntity(part);
          h.assertTrue(be.boundTo(at, owner.assembly().identity()), "part identity mismatch");
          h.assertTrue(
              be.saveWithFullMetadata(h.getLevel().registryAccess())
                  .getCompound("ElectricPole")
                  .isEmpty(),
              "part copied network state");
        }
        owner.assembly().removeWhole(false);
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void tallPoleObstacleRejectsWithoutSpendingItem(GameTestHelper h) {
    var at = site(h, 1);
    var profile = MachineProfile.BIG_POLE;
    for (var cell : profile.cells(at, Direction.NORTH)) h.getLevel().getChunkAt(cell);
    var blocked = at.offset(1, 4, 1);
    h.getLevel().setBlockAndUpdate(blocked, Blocks.DIAMOND_BLOCK.defaultBlockState());
    var p = player(h, at, new ItemStack(Content.FACTORY.get(FactoryKind.BIG_POLE).get(), 2));
    p.setYRot(Direction.SOUTH.toYRot());
    h.assertTrue(
        !PlacementTestSupport.place(p, at).consumesAction() && p.getMainHandItem().getCount() == 2,
        "blocked tall pole spent item");
    h.assertTrue(h.getLevel().getBlockState(at).isAir(), "blocked tall pole left controller");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void breakingEveryCellOfSubstationReturnsOneOwnerAndNoStructuralDrops(
      GameTestHelper h) {
    var at = site(h, 2);
    for (int hit = 0; hit < 16; hit++) {
      var p = pole(h, MachineProfile.SUBSTATION, at);
      var cells = MachineProfile.SUBSTATION.cells(at, Direction.NORTH);
      h.getLevel().destroyBlock(cells.get(hit), true);
      var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(6));
      h.assertTrue(
          drops.size() == 1
              && drops
                  .getFirst()
                  .getItem()
                  .is(Content.FACTORY.get(FactoryKind.SUBSTATION).get().asItem()),
          "pole demolition duplicated items");
      for (var cell : cells)
        h.assertTrue(h.getLevel().getBlockState(cell).isAir(), "pole demolition left a part");
      drops.forEach(ItemEntity::discard);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void automaticEdgesAreRealAndManualDisconnectionSurvivesReload(GameTestHelper h) {
    var at = site(h, 3);
    var a = pole(h, MachineProfile.BIG_POLE, at);
    var b = pole(h, MachineProfile.BIG_POLE, at.east(32));
    h.assertTrue(a.hasLink(b) && b.hasLink(a), "automatic boundary wire missing");
    PowerWires.disconnect(a, b);
    a.onChunkUnloaded();
    a.onLoad();
    b.onChunkUnloaded();
    b.onLoad();
    h.assertTrue(!a.hasLink(b) && !b.hasLink(a), "reload regenerated deliberately removed wire");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void mixedPoleReachUsesBothEndpoints(GameTestHelper h) {
    var at = site(h, 4);
    var a = pole(h, MachineProfile.BIG_POLE, at);
    var b = pole(h, MachineProfile.SMALL_POLE, at.east(8));
    h.assertTrue(
        !PowerWires.reachable(a, b) && !a.hasLink(b), "big pole bypassed small endpoint reach");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void copperToolCreatesRemovesAndClearsWithoutConsumption(GameTestHelper h) {
    var at = site(h, 5);
    var a = pole(h, MachineProfile.MEDIUM_POLE, at);
    var b = pole(h, MachineProfile.MEDIUM_POLE, at.east(8));
    PowerWires.clear(a);
    var p = player(h, at, new ItemStack(Content.POWER_WIRE_TOOL.get()));
    h.assertTrue(
        click(p, at.above(2)).consumesAction()
            && click(p, b.getBlockPos().above(2)).consumesAction()
            && a.hasLink(b),
        "tool did not wire through upper parts");
    h.assertTrue(p.getMainHandItem().getCount() == 1, "wire tool was consumed");
    click(p, at);
    click(p, b.getBlockPos());
    h.assertTrue(!a.hasLink(b), "tool did not toggle existing edge");
    PowerWires.connect(a, b);
    p.setShiftKeyDown(true);
    click(p, at);
    h.assertTrue(a.links().isEmpty() && b.links().isEmpty(), "clear action left a reciprocal edge");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void staleSelectionsAndOutOfReachClicksCannotChangeWires(GameTestHelper h) {
    var at = site(h, 6);
    var a = pole(h, MachineProfile.SMALL_POLE, at);
    var b = pole(h, MachineProfile.SMALL_POLE, at.east(6));
    PowerWires.clear(a);
    var p = player(h, at, new ItemStack(Content.POWER_WIRE_TOOL.get()));
    click(p, at);
    a.assembly().removeWhole(false);
    var replacement = pole(h, MachineProfile.SMALL_POLE, at);
    PowerWires.clear(replacement);
    h.assertTrue(
        !click(p, b.getBlockPos()).consumesAction() && !replacement.hasLink(b),
        "stale owner selection wired replacement");
    p.setPos(at.getX() + 100, at.getY(), at.getZ());
    var hit = new BlockHitResult(b.getBlockPos().getCenter(), Direction.UP, b.getBlockPos(), false);
    h.assertTrue(
        !p.getMainHandItem()
            .useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit))
            .consumesAction(),
        "remote wire edit accepted");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void poleRemovalRetiresReciprocalEdgesAndPortableIdentity(GameTestHelper h) {
    var at = site(h, 7);
    var a = pole(h, MachineProfile.MEDIUM_POLE, at);
    var b = pole(h, MachineProfile.MEDIUM_POLE, at.east(8));
    var id = a.assembly().identity();
    h.getLevel().destroyBlock(at.above(2), true);
    h.assertTrue(b.links().isEmpty(), "demolition left live reciprocal wire");
    var drop =
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(5)).getFirst();
    var stack = drop.getItem().copy();
    drop.discard();
    h.assertTrue(
        !stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTag().contains("ElectricPole"),
        "portable pole carried physical wires");
    var p = player(h, at, stack);
    p.setYRot(Direction.SOUTH.toYRot());
    h.assertTrue(
        PlacementTestSupport.place(p, at).consumesAction(), "portable pole placement failed");
    var fresh = (PoleBlockEntity) h.getLevel().getBlockEntity(at);
    h.assertTrue(!fresh.assembly().identity().equals(id), "pole reused old identity");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 120)
  public static void edgeCoveragePowersFactoryAndChunkLossImmediatelyStopsTransfer(
      GameTestHelper h) {
    var at = h.absolutePos(new BlockPos(6, 2, 8));
    var a = pole(h, MachineProfile.BIG_POLE, at);
    var middle = pole(h, MachineProfile.MEDIUM_POLE, at.east(8));
    var b = pole(h, MachineProfile.MEDIUM_POLE, at.east(16));
    var source =
        (MachineBlockEntity)
            place(h, MachineProfile.GENERATOR, at.offset(1, 0, 2), Direction.NORTH);
    var receiver =
        (MachineBlockEntity) place(h, MachineProfile.FURNACE, at.offset(17, 0, 2), Direction.NORTH);
    PowerWires.clear(a);
    PowerWires.clear(middle);
    PowerWires.clear(b);
    PowerWires.connect(a, middle);
    PowerWires.connect(middle, b);
    var part =
        (CasingBlockEntity) h.getLevel().getBlockEntity(middle.assembly().positions().getLast());
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    PowerGrid.hasDemand(source),
                    "footprint-edge source/consumer not supplied: "
                        + PowerGrid.statistics(h.getLevel())))
        .thenExecute(
            () -> {
              h.assertTrue(
                  PowerGrid.distribute(source, 80) == 80 && receiver.power.getEnergyStored() == 80,
                  "offered FE was not conserved");
              part.onChunkUnloaded();
              h.assertTrue(
                  !PowerGrid.hasDemand(source) && PowerGrid.distribute(source, 80) == 0,
                  "unavailable pole part kept conducting");
              part.onLoad();
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    PowerGrid.hasDemand(source), "reloaded explicit edges did not recover"))
        .thenExecute(
            () ->
                h.assertTrue(
                    PowerGrid.distribute(source, 80) == 80
                        && receiver.power.getEnergyStored() == 160,
                    "reloaded FE ledger changed"))
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void overlappingSupplyAreasDoNotBridgeIndependentNets(GameTestHelper h) {
    var at = h.absolutePos(new BlockPos(8, 2, 8));
    var a = pole(h, MachineProfile.SMALL_POLE, at);
    var b = pole(h, MachineProfile.SMALL_POLE, at.east(4));
    PowerWires.clear(a);
    PowerWires.clear(b);
    h.succeedWhen(
        () ->
            h.assertTrue(
                PowerGrid.info(a).poles() == 1 && PowerGrid.info(b).poles() == 1,
                "independent networks not published: "
                    + PowerGrid.info(a)
                    + " / "
                    + PowerGrid.info(b)));
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void machineInspectionMatchesControllerAndEveryPartWithoutBroaderCapabilities(
      GameTestHelper h) {
    int i = 0;
    for (var profile :
        List.of(
            MachineProfile.FURNACE,
            MachineProfile.ASSEMBLER,
            MachineProfile.ADVANCED_ASSEMBLER,
            MachineProfile.LAB,
            MachineProfile.REFINERY,
            MachineProfile.CHEMICAL,
            MachineProfile.TANK,
            MachineProfile.BOILER,
            MachineProfile.ENGINE,
            MachineProfile.BEACON)) {
      var be = place(h, profile, site(h, 10).offset(i++ * 12, 0, 0), Direction.NORTH);
      if (be instanceof MachineBlockEntity m) {
        m.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        m.items.setStackInSlot(4, new ItemStack(Items.IRON_INGOT, 7));
        m.power.restore(731);
        if (m.petroleum() != null)
          m.petroleum()
              .cells
              .get(0)
              .restore(
                  new factorycraft.fluids.FluidKey(
                      new net.neoforged.neoforge.fluids.FluidStack(
                          net.minecraft.world.level.material.Fluids.WATER, 1)),
                  137);
      }
      if (be instanceof FluidBlockEntity tank)
        tank.cell()
            .restore(
                new factorycraft.fluids.FluidKey(
                    new net.neoforged.neoforge.fluids.FluidStack(
                        net.minecraft.world.level.material.Fluids.WATER, 1)),
                1237);
      var player = player(h, be.getBlockPos(), ItemStack.EMPTY);
      var snapshot = MachineInspection.snapshot(h.getLevel(), be.getBlockPos(), player);
      h.assertTrue(!snapshot.isEmpty(), "controller inspection unavailable: " + profile);
      for (var part : AssemblyAccess.owner(be).assembly().positions()) {
        player.setPos(part.getX() + .5, part.getY(), part.getZ() + 2);
        h.assertTrue(
            snapshot.equals(MachineInspection.snapshot(h.getLevel(), part, player)),
            "part/controller inspection differs: " + profile);
        h.assertTrue(
            MachineInspection.name(h.getLevel(), part)
                .equals(MachineInspection.name(h.getLevel(), be.getBlockPos())),
            "part/controller names differ");
      }
      if (profile == MachineProfile.TANK)
        h.assertTrue(
            h.getLevel()
                    .getCapability(
                        Capabilities.FluidHandler.BLOCK, be.getBlockPos(), (Direction) null)
                == null,
            "inspection broadened tank automation");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void inspectionRejectsUnloadedOwnersAndReturnedDataNeverMutatesInventory(
      GameTestHelper h) {
    var be = (MachineBlockEntity) place(h, MachineProfile.FURNACE, site(h, 11), Direction.NORTH);
    be.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
    var p = player(h, be.getBlockPos(), ItemStack.EMPTY);
    var data = MachineInspection.snapshot(h.getLevel(), be.getBlockPos(), p);
    data.getList("Items", 10).clear();
    h.assertTrue(be.items.getStackInSlot(0).getCount() == 3, "readonly snapshot mutated inventory");
    var part =
        (CasingBlockEntity) h.getLevel().getBlockEntity(be.assembly().positions().getFirst());
    part.onChunkUnloaded();
    h.assertTrue(
        MachineInspection.snapshot(h.getLevel(), be.getBlockPos(), p).isEmpty(),
        "unloaded structure leaked inspection");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void refineryReceivesEnergyWhenOnlyOneFoundationCellIsCovered(GameTestHelper h) {
    var at = h.absolutePos(new BlockPos(6, 2, 6));
    var pole = pole(h, MachineProfile.SMALL_POLE, at);
    var source =
        (MachineBlockEntity)
            place(h, MachineProfile.GENERATOR, at.offset(-3, 0, -2), Direction.NORTH);
    var refinery =
        (MachineBlockEntity) place(h, MachineProfile.REFINERY, at.offset(4, 0, 4), Direction.NORTH);
    h.assertTrue(
        PowerGrid.footprint(refinery).suppliedBy(PowerGrid.footprint(pole), pole.spec(), 4),
        "refinery corner outside coverage");
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(PowerGrid.hasDemand(source), "edge-overlap refinery not attached"))
        .thenExecute(
            () ->
                h.assertTrue(
                    PowerGrid.distribute(source, 80) == 80
                        && refinery.power.getEnergyStored() == 80,
                    "refinery center was incorrectly required for supply"))
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void savedReciprocalWireSurvivesDelayedPeerTagAndLoadCallbacks(GameTestHelper h) {
    var at = site(h, 14);
    var a = pole(h, MachineProfile.MEDIUM_POLE, at);
    var b = pole(h, MachineProfile.MEDIUM_POLE, at.east(8));
    var r = h.getLevel().registryAccess();
    var saved = b.saveWithFullMetadata(r);
    b.onChunkUnloaded();
    var partial = saved.copy();
    partial.remove("ElectricPole");
    b.loadWithComponents(partial, r);
    a.onChunkUnloaded();
    a.onLoad();
    h.assertTrue(
        a.linkAt(b.getBlockPos()) != null, "unknown loading peer was treated as destroyed");
    b.loadWithComponents(saved, r);
    b.onLoad();
    h.assertTrue(
        a.hasLink(b) && b.hasLink(a) && PowerGrid.hasEdge(a, b),
        "reciprocal wire did not restore after peer data became ready");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void cancelledPolePlacementRollsBackPartsItemAndExternalWires(GameTestHelper h) {
    var at = site(h, 15);
    var peer = pole(h, MachineProfile.SMALL_POLE, at.east(6));
    var profile = MachineProfile.BIG_POLE;
    for (var cell : profile.cells(at, Direction.NORTH)) h.getLevel().getChunkAt(cell);
    var p = player(h, at, new ItemStack(Content.FACTORY.get(FactoryKind.BIG_POLE).get(), 2));
    p.setYRot(Direction.SOUTH.toYRot());
    int[] calls = {0};
    java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent>
        cancel =
            event -> {
              if (event.getEntity() == p) {
                calls[0]++;
                event.setCanceled(true);
              }
            };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
    try {
      h.assertTrue(
          !PlacementTestSupport.place(p, at).consumesAction(), "multi-place protection ignored");
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
    }
    h.assertTrue(
        calls[0] == 1 && p.getMainHandItem().getCount() == 2,
        "cancelled pole placement spent item");
    for (var cell : profile.cells(at, Direction.NORTH))
      h.assertTrue(h.getLevel().getBlockState(cell).isAir(), "cancelled pole left a physical part");
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(peer.links().isEmpty(), "cancelled pole leaked external wires");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void entityInAnUpperPoleCellPreventsPlacement(GameTestHelper h) {
    var at = site(h, 16);
    for (var cell : MachineProfile.BIG_POLE.cells(at, Direction.NORTH))
      h.getLevel().getChunkAt(cell);
    var cow = net.minecraft.world.entity.EntityType.COW.create(h.getLevel());
    cow.moveTo(at.getX() + .5, at.getY() + 3, at.getZ() + .5, 0, 0);
    h.getLevel().addFreshEntity(cow);
    var p = player(h, at, new ItemStack(Content.FACTORY.get(FactoryKind.BIG_POLE).get()));
    p.setYRot(Direction.SOUTH.toYRot());
    h.assertTrue(
        !PlacementTestSupport.place(p, at).consumesAction() && p.getMainHandItem().getCount() == 1,
        "pole placed through an upper-cell entity");
    cow.discard();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "electric_v0200", timeoutTicks = 100)
  public static void poleEdgesAndSingleOwnerResourcesPersistAcrossServerJvms(GameTestHelper h) {
    var at = new BlockPos(190015, 70, 190015);
    var other = at.east(32);
    var machine = other.offset(1, 0, 2);
    var level = h.getLevel();
    var cells = new ArrayList<BlockPos>();
    cells.addAll(MachineProfile.BIG_POLE.cells(at, Direction.NORTH));
    cells.addAll(MachineProfile.BIG_POLE.cells(other, Direction.NORTH));
    cells.addAll(MachineProfile.FURNACE.cells(machine, Direction.NORTH));
    for (var cell : cells) level.getChunkAt(cell);
    if (cells.stream().allMatch(p -> level.getBlockState(p).isAir())) {
      var a = pole(h, MachineProfile.BIG_POLE, at);
      var b = pole(h, MachineProfile.BIG_POLE, other);
      var m = (MachineBlockEntity) place(h, MachineProfile.FURNACE, machine, Direction.NORTH);
      m.items.setStackInSlot(4, new ItemStack(Items.DIAMOND, 3));
      m.power.restore(731);
      h.assertTrue(a.hasLink(b) && b.hasLink(a), "disk automatic wire missing");
      System.out.println(
          "FACTORYCRAFT_POWER_V20_DISK: SEEDED reciprocal UUID wire, 731 FE / 3 diamonds");
    } else {
      h.assertTrue(
          level.getBlockEntity(at) instanceof PoleBlockEntity
              && level.getBlockEntity(other) instanceof PoleBlockEntity
              && level.getBlockEntity(machine) instanceof MachineBlockEntity,
          "partial electric checkpoint; refusing reseed");
      var a = (PoleBlockEntity) level.getBlockEntity(at);
      var b = (PoleBlockEntity) level.getBlockEntity(other);
      var m = (MachineBlockEntity) level.getBlockEntity(machine);
      h.assertTrue(
          a.spec() == PoleSpec.BIG
              && a.hasLink(b)
              && b.hasLink(a)
              && a.links().size() == 1
              && b.links().size() == 1,
          "wire identity changed on disk");
      h.assertTrue(
          m.power.getEnergyStored() == 731 && m.items.getStackInSlot(4).getCount() == 3,
          "electric checkpoint lost resources");
      for (var owner : List.<AssemblyOwner>of(a, b, m))
        for (var p : owner.assembly().positions())
          h.assertTrue(
              level.getBlockEntity(p) instanceof CasingBlockEntity part
                  && part.boundTo(owner.factoryEntity().getBlockPos(), owner.assembly().identity()),
              "disk pole part identity lost");
      System.out.println(
          "FACTORYCRAFT_POWER_V20_DISK: VERIFIED reciprocal UUID wire, 731 FE / 3 diamonds");
    }
    for (var cell : cells) {
      level.getBlockEntity(cell).onChunkUnloaded();
      level.getChunkAt(cell).setUnsaved(true);
    }
    h.succeed();
  }
}
