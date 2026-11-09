package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.diagnostics.SortingFactory;
import factorycraft.fluids.FluidKey;
import factorycraft.logistics.*;
import factorycraft.multiblock.*;
import factorycraft.petroleum.PetroleumFluids;
import factorycraft.registry.Content;
import factorycraft.research.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class TieredSplitterGameTests {
  private static ResourceLocation id(String name) {
    return ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, name);
  }

  private static Item item(String name) {
    return BuiltInRegistries.ITEM.get(id(name));
  }

  private static BeltBlockEntity place(GameTestHelper h, FactoryKind kind, BlockPos relative) {
    var p = h.absolutePos(relative);
    StructurePlacement.place(h.getLevel(), p, Content.FACTORY.get(kind).get().defaultBlockState());
    return (BeltBlockEntity) h.getLevel().getBlockEntity(p);
  }

  private static void seed(BeltBlockEntity b) {
    for (int lane = 0; lane < 4; lane++)
      for (int n = 0; n < 4; n++)
        b.lane(lane)
            .restore(new ItemStack(n % 2 == 0 ? Items.RAW_IRON : Items.RAW_COPPER), 1 - n * .25);
    b.sync();
    b.wakeTransport();
  }

  private static int count(BeltBlockEntity b) {
    int n = 0;
    for (int i = 0; i < b.laneCount(); i++) n += b.lane(i).carriers().size();
    return n;
  }

  private static SplitterSettings settings() {
    return new SplitterSettings(2, 1, ResourceLocation.withDefaultNamespace("raw_iron"));
  }

  private static void settingsBeforeLoad(BeltBlockEntity b) {
    var tag = b.saveWithoutMetadata(b.getLevel().registryAccess());
    tag.put("Splitter", settings().save());
    b.loadWithComponents(tag, b.getLevel().registryAccess());
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void allThreeFiniteSortingFactoriesDeliverExactOutputs(GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    SortingFactory.buildTiered(h.getLevel(), o);
    h.runAfterDelay(
        1500,
        () -> {
          for (int tier = 0; tier < 3; tier++) {
            var base = o.offset(tier * 8 - 6, 0, 0);
            for (int side = 0; side < 2; side++) {
              var chest =
                  (ChestBlockEntity)
                      h.getLevel().getBlockEntity(base.offset(side == 0 ? 6 : 11, 0, 4));
              h.assertTrue(
                  chest.getItem(0).is(side == 0 ? Items.RAW_IRON : Items.RAW_COPPER)
                      && chest.getItem(0).getCount() == 32,
                  "tiered sorting output incorrect: " + tier + "/" + side);
              for (int s = 1; s < chest.getContainerSize(); s++)
                h.assertTrue(chest.getItem(s).isEmpty(), "unexpected output stack");
              var source =
                  (ChestBlockEntity) h.getLevel().getBlockEntity(base.offset(8 + side, 0, 8));
              h.assertTrue(source.isEmpty(), "source did not drain");
            }
            var b = (BeltBlockEntity) h.getLevel().getBlockEntity(base.offset(8, 0, 5));
            h.assertTrue(
                b.kind().beltTier == tier + 1 && count(b) == 0,
                "splitter has residual cargo or wrong tier");
          }
          System.out.println(
              "FACTORYCRAFT_TIERED_SORTING: VERIFIED 32 iron + 32 copper per tier from real source"
                  + " chests");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void highTierPortsMenusAndPartialUnloadRetainSingleOwnership(GameTestHelper h) {
    var kinds = new FactoryKind[] {FactoryKind.FAST_SPLITTER, FactoryKind.EXPRESS_SPLITTER};
    var owners = new ArrayList<BeltBlockEntity>();
    for (int i = 0; i < kinds.length; i++)
      owners.add(place(h, kinds[i], new BlockPos(4 + i * 6, 3, 4)));
    h.runAfterDelay(
        5,
        () -> {
          for (var b : owners) {
            h.assertTrue(
                b.laneCount() == 4 && b.tracks() == 2 && b.configureSplitter(settings()),
                "high tier has no four-lane settings");
            var p = h.makeMockPlayer(GameType.SURVIVAL);
            p.setPos(Vec3.atCenterOf(b.getBlockPos()));
            var menu = new SplitterMenu(42, p.getInventory(), b);
            p.containerMenu = menu;
            menu.setCarried(new ItemStack(Items.DIAMOND, 3));
            menu.clicked(0, 0, ClickType.PICKUP, p);
            h.assertTrue(
                menu.getCarried().getCount() == 3
                    && b.splitterSettings()
                        .filter()
                        .equals(ResourceLocation.withDefaultNamespace("diamond")),
                "ghost consumed item or ignored high tier");
            h.assertTrue(menu.clickMenuButton(p, 0), "high-tier priority button rejected");
            b.configureSplitter(settings());
            seed(b);
            var a =
                h.getLevel()
                    .getCapability(Capabilities.ItemHandler.BLOCK, b.getBlockPos(), Direction.UP);
            var c =
                h.getLevel()
                    .getCapability(
                        Capabilities.ItemHandler.BLOCK, b.trackPosition(1), Direction.UP);
            h.assertTrue(
                a != null && c != null && a.getSlots() == 8 && c.getSlots() == 8,
                "wrong per-track capabilities");
            var part = (CasingBlockEntity) h.getLevel().getBlockEntity(b.trackPosition(1));
            h.assertTrue(
                part.getBlockState()
                        .getShape(h.getLevel(), part.getBlockPos())
                        .max(Direction.Axis.Y)
                    == .1875,
                "high-tier casing has full-block collision");
            part.onChunkUnloaded();
            h.assertTrue(
                a.extractItem(0, 1, false).isEmpty() && c.extractItem(0, 1, false).isEmpty(),
                "cached capability bypassed unavailable structure");
          }
          h.runAfterDelay(
              12,
              () -> {
                for (var b : owners) {
                  h.assertTrue(count(b) == 16, "partial unload lost cargo");
                  for (int lane = 0; lane < 4; lane++)
                    h.assertTrue(
                        b.lane(lane).carriers().getFirst().positionUnits() == 96,
                        "inactive tier moved");
                  var part = (CasingBlockEntity) h.getLevel().getBlockEntity(b.trackPosition(1));
                  part.onLoad();
                  h.assertTrue(
                      b.structureReady() && b.splitterSettings().equals(settings()),
                      "settings/readiness failed to resume");
                }
                h.succeed();
              });
        });
  }

  private static void portable(GameTestHelper h, FactoryKind kind) {
    var b = place(h, kind, new BlockPos(4, 3, 4));
    h.runAfterDelay(
        5,
        () -> {
          b.configureSplitter(settings());
          seed(b);
          var identity = b.assembly().identity();
          ((CasingBlockEntity) h.getLevel().getBlockEntity(b.trackPosition(1))).dropContents();
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new AABB(b.getBlockPos()).inflate(3));
          h.assertTrue(
              drops.size() == 1
                  && drops.getFirst().getItem().is(Content.FACTORY.get(kind).get().asItem()),
              "demolition did not return one correct-tier device");
          var stack = drops.getFirst().getItem().copy();
          drops.getFirst().discard();
          var p = h.makeMockPlayer(GameType.SURVIVAL);
          p.setYRot(Direction.NORTH.toYRot());
          p.setItemInHand(InteractionHand.MAIN_HAND, stack);
          var target = h.absolutePos(new BlockPos(11, 3, 4));
          h.assertTrue(
              PlacementTestSupport.place(p, target).consumesAction()
                  && p.getMainHandItem().isEmpty(),
              "portable high-tier placement failed");
          var copy = (BeltBlockEntity) h.getLevel().getBlockEntity(target);
          h.assertTrue(
              copy.kind() == kind
                  && count(copy) == 16
                  && copy.splitterSettings().equals(settings())
                  && !identity.equals(copy.assembly().identity()),
              "portable lost tier/settings/cargo or reused identity");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void fastSplitterDemolitionAndPortablePlacement(GameTestHelper h) {
    portable(h, FactoryKind.FAST_SPLITTER);
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void expressSplitterDemolitionAndPortablePlacement(GameTestHelper h) {
    portable(h, FactoryKind.EXPRESS_SPLITTER);
  }

  private static MachineBlockEntity assembler(GameTestHelper h, FactoryKind kind) {
    var p = h.absolutePos(new BlockPos(4, 2, 4));
    StructurePlacement.place(h.getLevel(), p, Content.FACTORY.get(kind).get().defaultBlockState());
    return (MachineBlockEntity) h.getLevel().getBlockEntity(p);
  }

  private static void inputs(MachineBlockEntity m, boolean express) {
    m.items.setStackInSlot(0, new ItemStack(item(express ? "fast_splitter" : "splitter")));
    m.items.setStackInSlot(1, new ItemStack(item("iron_gear"), 10));
    m.items.setStackInSlot(
        2, new ItemStack(item(express ? "advanced_circuit" : "electronic_circuit"), 10));
  }

  private static void unlock(GameTestHelper h, String scope, String name) {
    var catalog = ResearchCatalog.get(h.getLevel());
    var entry = catalog.entry(id(name));
    h.assertTrue(entry != null, "missing logistics research");
    var ledger = ResearchData.get(h.getLevel().getServer()).team(scope);
    var levels = new HashMap<String, Integer>();
    for (var e : catalog.entries()) levels.put(e.id().toString(), 1);
    levels.remove(id(name).toString());
    ledger.restore("", levels, Map.of());
    ledger.select(entry.logic());
    for (int i = 0; i < entry.recipe().units(); i++)
      h.assertTrue(ledger.completeUnit(entry.logic(), () -> {}), "research failed to complete");
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void fastSplitterManufactureWaitsForResearchThenCommitsExactInputsAndFe(
      GameTestHelper h) {
    var m = assembler(h, FactoryKind.ASSEMBLER);
    String scope = ResearchGameTests.personal(h, "fast-splitter");
    m.bindResearch(scope);
    h.runAfterDelay(
        5,
        () -> {
          inputs(m, false);
          m.power.restore(5000);
        });
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).isEmpty()
                  && m.items.getStackInSlot(1).getCount() == 10
                  && m.power.getEnergyStored() == 5000,
              "locked manufacture spent resources");
          unlock(h, scope, "logistics_2");
        });
    h.runAfterDelay(
        110,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).is(item("fast_splitter"))
                  && m.items.getStackInSlot(4).getCount() == 1
                  && m.items.getStackInSlot(0).isEmpty()
                  && m.items.getStackInSlot(1).isEmpty()
                  && m.items.getStackInSlot(2).isEmpty()
                  && m.power.getEnergyStored() == 4040,
              "fast splitter input/result/FE ledger failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 220)
  public static void expressSplitterReservesLubricantAndResearchBeforeManufacturing(
      GameTestHelper h) {
    var m = assembler(h, FactoryKind.ADVANCED_ASSEMBLER);
    String scope = ResearchGameTests.personal(h, "express-splitter");
    m.bindResearch(scope);
    h.runAfterDelay(
        5,
        () -> {
          inputs(m, true);
          m.power.restore(5000);
          m.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.LUBRICANT.stack(1)), 80);
          h.assertTrue(
              m.petroleum().selectRecipe(id("express_splitter")), "missing express recipe");
        });
    h.runAfterDelay(
        35,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).isEmpty()
                  && m.power.getEnergyStored() == 5000
                  && m.petroleum().cells.get(0).amount() == 80,
              "locked express manufacture spent resources");
          unlock(h, scope, "logistics_3");
          m.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.LUBRICANT.stack(1)), 79);
        });
    h.runAfterDelay(
        75,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).isEmpty()
                  && m.power.getEnergyStored() == 5000
                  && m.petroleum().cells.get(0).amount() == 79,
              "short lubricant was consumed");
          m.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.LUBRICANT.stack(1)), 80);
        });
    h.runAfterDelay(
        145,
        () -> {
          h.assertTrue(
              m.items.getStackInSlot(4).is(item("express_splitter"))
                  && m.items.getStackInSlot(4).getCount() == 1
                  && m.items.getStackInSlot(0).isEmpty()
                  && m.items.getStackInSlot(1).isEmpty()
                  && m.items.getStackInSlot(2).isEmpty()
                  && m.petroleum().cells.get(0).amount() == 0
                  && m.power.getEnergyStored() == 3400,
              "express splitter item/fluid/FE ledger failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void upgradeRecipesRejectPortablePayloadsAndKeepStrictnessOnWire(GameTestHelper h) {
    var recipes = h.getLevel().getRecipeManager();
    var fast =
        (factorycraft.recipes.FactoryRecipe)
            recipes.byKey(id("fast_splitter")).orElseThrow().value();
    var express =
        (factorycraft.recipes.ChemicalRecipe)
            recipes.byKey(id("express_splitter")).orElseThrow().value();
    var types =
        List.of(fast.inputs().getFirst().ingredient(), express.items().getFirst().ingredient());
    for (int i = 0; i < types.size(); i++) {
      var plain = new ItemStack(item(i == 0 ? "splitter" : "fast_splitter"));
      var portable = plain.copy();
      var payload = new net.minecraft.nbt.CompoundTag();
      payload.putString("id", "factorycraft:belt");
      payload.putInt("Schema", 3);
      payload.put("Splitter", settings().save());
      portable.set(
          net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
          net.minecraft.world.item.component.CustomData.of(payload));
      h.assertTrue(
          types.get(i).test(plain) && !types.get(i).test(portable),
          "upgrade accepts a resource-owning portable item");
      var buf =
          new net.minecraft.network.RegistryFriendlyByteBuf(
              io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
      try {
        net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.encode(
            buf, types.get(i));
        var restored =
            net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
        h.assertTrue(
            restored.test(plain) && !restored.test(portable),
            "viewer/network sync lost strict component predicate");
      } finally {
        buf.release();
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void highTierSplittersPersistAcrossSeparateServerProcesses(GameTestHelper h) {
    var kinds = new FactoryKind[] {FactoryKind.FAST_SPLITTER, FactoryKind.EXPRESS_SPLITTER};
    var owners = new ArrayList<BeltBlockEntity>();
    var freshFlags = new ArrayList<Boolean>();
    for (int i = 0; i < kinds.length; i++) {
      var p = new BlockPos(13007 + i * 32, 70, 13007);
      var profile = MachineProfile.byId(kinds[i].id);
      var cells = profile.cells(p, Direction.NORTH);
      for (var c : cells) h.getLevel().getChunkAt(c);
      boolean fresh =
          cells.stream()
              .allMatch(
                  c ->
                      h.getLevel().getBlockEntity(c) == null
                          && h.getLevel().getBlockState(c).isAir());
      if (fresh) {
        StructurePlacement.place(
            h.getLevel(), p, Content.FACTORY.get(kinds[i]).get().defaultBlockState());
        var b = (BeltBlockEntity) h.getLevel().getBlockEntity(p);
        settingsBeforeLoad(b);
        seed(b);
      }
      h.assertTrue(
          h.getLevel().getBlockEntity(p) instanceof BeltBlockEntity,
          "partial tiered disk fixture; refusing refill");
      owners.add((BeltBlockEntity) h.getLevel().getBlockEntity(p));
      freshFlags.add(fresh);
    }
    h.runAfterDelay(
        4,
        () -> {
          for (int i = 0; i < owners.size(); i++) {
            var b = owners.get(i);
            h.assertTrue(
                b.kind() == kinds[i] && count(b) == 16 && b.splitterSettings().equals(settings()),
                "disk lost high-tier settings or cargo");
            for (int lane = 0; lane < 4; lane++)
              for (int n = 0; n < 4; n++)
                h.assertTrue(
                    b.lane(lane).carriers().get(n).positionUnits() == 96 - 24 * n
                        && b.lane(lane)
                            .carriers()
                            .get(n)
                            .value
                            .is(n % 2 == 0 ? Items.RAW_IRON : Items.RAW_COPPER),
                    "disk changed carrier type/order/coordinate");
            var part = (CasingBlockEntity) h.getLevel().getBlockEntity(b.trackPosition(1));
            h.assertTrue(
                part.boundTo(b.getBlockPos(), b.assembly().identity())
                    && part.getBlockState().getValue(FactoryBlock.PROFILE)
                        == MachineProfile.byId(kinds[i].id),
                "disk lost part identity/profile");
            System.out.println(
                "FACTORYCRAFT_TIERED_SPLITTER_DISK: "
                    + (freshFlags.get(i) ? "SEEDED " : "VERIFIED ")
                    + kinds[i].id
                    + " sixteen exact carriers, settings and cross-chunk identity");
            for (var c : MachineProfile.byId(kinds[i].id).cells(b.getBlockPos(), Direction.NORTH)) {
              h.getLevel().getBlockEntity(c).onChunkUnloaded();
              h.getLevel().getChunkAt(c).setUnsaved(true);
            }
          }
          h.succeed();
        });
  }
}
