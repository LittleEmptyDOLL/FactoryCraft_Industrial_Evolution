package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.logistics.*;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import factorycraft.simulation.BeltSimulation;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class LogisticsControlGameTests {
  private static final BlockPos P = new BlockPos(4, 3, 4);
  private static final ResourceLocation IRON = ResourceLocation.withDefaultNamespace("raw_iron");

  private static BeltBlockEntity place(GameTestHelper h, FactoryKind kind, BlockPos p) {
    StructurePlacement.place(
        h.getLevel(), h.absolutePos(p), Content.FACTORY.get(kind).get().defaultBlockState());
    return (BeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(p));
  }

  private static int count(BeltBlockEntity b) {
    int result = 0;
    for (int i = 0; i < b.laneCount(); i++) result += b.lane(i).carriers().size();
    return result;
  }

  private static void seed(BeltBlockEntity b) {
    for (int lane = 0; lane < b.laneCount(); lane++)
      for (int j = 0; j < 4; j++)
        b.lane(lane)
            .restore(new ItemStack(j % 2 == 0 ? Items.RAW_IRON : Items.RAW_COPPER), 1 - j * .25);
    b.sync();
    b.wakeTransport();
  }

  private static Player player(GameTestHelper h, BlockPos p) {
    var player =
        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LogisticsTest"));
    player.setPos(Vec3.atCenterOf(h.absolutePos(p)));
    player.setYRot(180);
    return player;
  }

  private static SplitterMenu menu(Player p, BeltBlockEntity b, int id) {
    var m = new SplitterMenu(id, p.getInventory(), b);
    p.containerMenu = m;
    return m;
  }

  private static int inventory(Player p, Item item) {
    int n = 0;
    for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++)
      if (p.getInventory().getItem(slot).is(item)) n += p.getInventory().getItem(slot).getCount();
    return n;
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void splitterFilterSortsBothPhysicalInputsAndLanes(GameTestHelper h) {
    var b = place(h, FactoryKind.SPLITTER, P);
    var l = place(h, FactoryKind.BELT, P.north());
    var r = place(h, FactoryKind.BELT, P.east().north());
    h.runAfterDelay(
        4,
        () -> {
          h.assertTrue(
              b.configureSplitter(new SplitterSettings(0, 1, IRON)), "filter not configured");
          seed(b);
          h.runAfterDelay(
              130,
              () -> {
                h.assertTrue(
                    count(b) == 0 && count(l) == 8 && count(r) == 8, "sorter changed item ledger");
                for (int lane = 0; lane < 2; lane++) {
                  h.assertTrue(
                      l.lane(lane).carriers().stream().allMatch(v -> v.value.is(Items.RAW_IRON)),
                      "nonmatching item leaked into filtered exit");
                  h.assertTrue(
                      r.lane(lane).carriers().stream().allMatch(v -> v.value.is(Items.RAW_COPPER)),
                      "matching item leaked into other exit");
                }
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 240)
  public static void blockedFilterOutputRetainsMatchesAndResumes(GameTestHelper h) {
    var b = place(h, FactoryKind.SPLITTER, P);
    var l = place(h, FactoryKind.BELT, P.north());
    var r = place(h, FactoryKind.BELT, P.east().north());
    h.runAfterDelay(
        4,
        () -> {
          b.configureSplitter(new SplitterSettings(0, 1, IRON));
          for (int lane = 0; lane < 2; lane++)
            for (int j = 0; j < 4; j++)
              l.lane(lane).restore(new ItemStack(Items.DIRT), 1 - j * .25);
          l.wakeTransport();
          seed(b);
          h.runAfterDelay(
              110,
              () -> {
                h.assertTrue(
                    count(b) == 16 && count(l) == 8 && count(r) == 0,
                    "blocked filter failed FIFO/conservation: "
                        + count(b)
                        + "/"
                        + count(l)
                        + "/"
                        + count(r));
                for (int lane = 0; lane < 2; lane++) {
                  l.lane(lane).clear();
                }
                l.sync();
                l.wakeTransport();
                h.runAfterDelay(
                    80,
                    () -> {
                      h.assertTrue(
                          count(b) == 0 && count(l) == 8 && count(r) == 8,
                          "filtered output did not resume");
                      h.succeed();
                    });
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void splitterInputPriorityWinsFirstContestedExit(GameTestHelper h) {
    var b = place(h, FactoryKind.SPLITTER, P);
    var l = place(h, FactoryKind.BELT, P.north());
    place(h, FactoryKind.BELT, P.east().north());
    h.runAfterDelay(
        4,
        () -> {
          b.configureSplitter(new SplitterSettings(2, 1, null));
          b.lane(0).restore(new ItemStack(Items.RAW_IRON), .5);
          b.lane(2).restore(new ItemStack(Items.RAW_COPPER), .5);
          b.wakeTransport();
          h.runAfterDelay(
              80,
              () -> {
                h.assertTrue(
                    !l.lane(0).carriers().isEmpty()
                        && l.lane(0).carriers().getFirst().value.is(Items.RAW_COPPER),
                    "preferred physical input lost first contested output");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void filterGhostSlotDoesNotConsumeOrCreateItems(GameTestHelper h) {
    var b = place(h, FactoryKind.SPLITTER, P);
    var p = player(h, P.south(2));
    h.runAfterDelay(
        4,
        () -> {
          var m = menu(p, b, 8);
          m.setCarried(new ItemStack(Items.DIAMOND, 3));
          m.clicked(0, 0, ClickType.PICKUP, p);
          h.assertTrue(
              m.getCarried().getCount() == 3
                  && m.slots.getFirst().getItem().getCount() == 1
                  && b.splitterSettings()
                      .filter()
                      .equals(BuiltInRegistries.ITEM.getKey(Items.DIAMOND)),
              "ghost filter moved inventory items");
          for (var type :
              List.of(
                  ClickType.SWAP,
                  ClickType.THROW,
                  ClickType.QUICK_MOVE,
                  ClickType.CLONE,
                  ClickType.PICKUP_ALL)) m.clicked(0, 0, type, p);
          h.assertTrue(
              m.getCarried().getCount() == 3, "ghost special click duplicated/removed items");
          m.clicked(0, 1, ClickType.PICKUP, p);
          h.assertTrue(
              b.splitterSettings().filter() == null && m.getCarried().getCount() == 3,
              "clearing filter changed resource ownership");
          m.removed(p);
          h.assertTrue(inventory(p, Items.DIAMOND) == 3, "closing filter menu created/lost items");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void twoMenusShareSettingsWithoutMovingEachOthersCarriedItems(GameTestHelper h) {
    var b = place(h, FactoryKind.SPLITTER, P);
    var a = player(h, P.south());
    var c = player(h, P.south(2));
    h.runAfterDelay(
        4,
        () -> {
          var m = menu(a, b, 8);
          var n = menu(c, b, 9);
          m.setCarried(new ItemStack(Items.DIAMOND, 3));
          n.setCarried(new ItemStack(Items.EMERALD, 4));
          m.clicked(0, 0, ClickType.PICKUP, a);
          n.clicked(0, 0, ClickType.PICKUP, c);
          m.broadcastChanges();
          h.assertTrue(
              m.slots.getFirst().getItem().is(Items.EMERALD)
                  && m.getCarried().getCount() == 3
                  && n.getCarried().getCount() == 4,
              "concurrent menus copied resources or stale filter");
          m.removed(a);
          n.removed(c);
          h.assertTrue(
              inventory(a, Items.DIAMOND) == 3 && inventory(c, Items.EMERALD) == 4,
              "menu close changed item ledger");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void staleDistantSpectatorAndUnloadedMenusCannotChangeSettings(GameTestHelper h) {
    var b = place(h, FactoryKind.SPLITTER, P);
    var p = player(h, P.south());
    h.runAfterDelay(
        4,
        () -> {
          var m = menu(p, b, 8);
          p.containerMenu = p.inventoryMenu;
          h.assertTrue(!m.clickMenuButton(p, 0), "stale menu changed settings");
          p.containerMenu = m;
          p.setPos(Vec3.atCenterOf(h.absolutePos(P.south(30))));
          h.assertTrue(!m.clickMenuButton(p, 0), "distant menu changed settings");
          p.setPos(Vec3.atCenterOf(h.absolutePos(P.south())));
          h.assertTrue(!m.clickMenuButton(p, 99), "invalid action accepted");
          ((net.minecraft.server.level.ServerPlayer) p).setGameMode(GameType.SPECTATOR);
          h.assertTrue(!m.clickMenuButton(p, 0), "spectator changed settings");
          ((net.minecraft.server.level.ServerPlayer) p).setGameMode(GameType.SURVIVAL);
          b.onChunkUnloaded();
          m.setCarried(new ItemStack(Items.DIAMOND, 2));
          m.clicked(0, 0, ClickType.PICKUP, p);
          h.assertTrue(
              !m.clickMenuButton(p, 0)
                  && b.splitterSettings().equals(SplitterSettings.DEFAULT)
                  && m.getCarried().getCount() == 2,
              "unloaded menu mutated settings/resources");
          b.onLoad();
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void configuredSplitterPortableItemRetainsAllSixteenCarriers(GameTestHelper h) {
    var b = place(h, FactoryKind.SPLITTER, P);
    h.runAfterDelay(
        4,
        () -> {
          var setting = new SplitterSettings(2, 2, IRON);
          b.configureSplitter(setting);
          seed(b);
          var identity = b.assembly().identity();
          var stack = b.portable();
          b.assembly().removeWhole(false);
          var target = new BlockPos(10, 3, 4);
          var p = player(h, target.south(2));
          p.setItemInHand(InteractionHand.MAIN_HAND, stack);
          h.assertTrue(
              PlacementTestSupport.place(p, h.absolutePos(target)).consumesAction(),
              "portable splitter placement failed");
          h.runAfterDelay(
              5,
              () -> {
                var copy = (BeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(target));
                h.assertTrue(
                    copy.splitterSettings().equals(setting)
                        && count(copy) == 16
                        && copy.structureReady()
                        && !copy.assembly().identity().equals(identity),
                    "portable settings/inventory/identity failed");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void defaultSplitterOmitsNewNbtAndUnknownFilterRemainsClosed(GameTestHelper h) {
    var b = place(h, FactoryKind.SPLITTER, P);
    h.runAfterDelay(
        4,
        () -> {
          h.assertTrue(
              !b.saveWithoutMetadata(h.getLevel().registryAccess()).contains("Splitter"),
              "default splitter changed legacy NBT");
          var unknown = ResourceLocation.fromNamespaceAndPath("missing_mod", "ore");
          b.configureSplitter(new SplitterSettings(1, 2, unknown));
          var copy =
              (BeltBlockEntity)
                  BlockEntity.loadStatic(
                      b.getBlockPos(),
                      b.getBlockState(),
                      b.saveWithFullMetadata(h.getLevel().registryAccess()),
                      h.getLevel().registryAccess());
          h.assertTrue(
              copy.splitterSettings().filter().equals(unknown)
                  && !copy.splitterSettings().matches(new ItemStack(Items.RAW_IRON)),
              "missing item silently removed filter");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "logistics_lifecycle", timeoutTicks = 60)
  public static void ticketDemotionPausesAndPromotionResumesLoadedBelt(GameTestHelper h) {
    var b = place(h, FactoryKind.BELT, P);
    h.runAfterDelay(
        4,
        () -> {
          b.inventory().insertItem(0, new ItemStack(Items.DIAMOND), false);
          long chunk = new net.minecraft.world.level.ChunkPos(b.getBlockPos()).toLong();
          BeltSimulation.ticketLevel(
              new ChunkTicketLevelUpdatedEvent(h.getLevel(), chunk, 31, 33, null));
          int before = b.lane(0).carriers().getFirst().positionUnits();
          h.runAfterDelay(
              4,
              () -> {
                h.assertTrue(
                    b.lane(0).carriers().getFirst().positionUnits() == before,
                    "merely loaded chunk kept moving belt");
                BeltSimulation.ticketLevel(
                    new ChunkTicketLevelUpdatedEvent(h.getLevel(), chunk, 33, 31, null));
                h.runAfterDelay(
                    4,
                    () -> {
                      h.assertTrue(
                          b.lane(0).carriers().getFirst().positionUnits() > before,
                          "ticket promotion failed to resume");
                      h.succeed();
                    });
              });
        });
  }

  @GameTest(template = "empty", batch = "logistics_lifecycle", timeoutTicks = 60)
  public static void tickFreezeDoesNotAdvanceTransport(GameTestHelper h) {
    var b = place(h, FactoryKind.BELT, P);
    h.runAfterDelay(
        4,
        () -> {
          b.inventory().insertItem(0, new ItemStack(Items.DIAMOND), false);
          int before = b.lane(0).carriers().getFirst().positionUnits();
          var tick = h.getLevel().getServer().tickRateManager();
          boolean frozen = tick.isFrozen();
          try {
            tick.setFrozen(true);
            tick.tick();
            BeltSimulation.tick(
                new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(
                    () -> true, h.getLevel()));
            h.assertTrue(
                b.lane(0).carriers().getFirst().positionUnits() == before,
                "frozen tick moved resources");
          } finally {
            tick.setFrozen(frozen);
            tick.tick();
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void configuredSplitterPersistsAcrossSeparateServerProcesses(GameTestHelper h) {
    var p = new BlockPos(12111, 70, 12111);
    var profile = factorycraft.multiblock.MachineProfile.SPLITTER;
    var cells = profile.cells(p, Direction.NORTH);
    for (var cell : cells) h.getLevel().getChunkAt(cell);
    boolean fresh =
        cells.stream()
            .allMatch(
                cell ->
                    h.getLevel().getBlockEntity(cell) == null
                        && h.getLevel().getBlockState(cell).isAir());
    if (fresh) {
      StructurePlacement.place(
          h.getLevel(), p, Content.FACTORY.get(FactoryKind.SPLITTER).get().defaultBlockState());
      var owner = (BeltBlockEntity) h.getLevel().getBlockEntity(p);
      var tag = owner.saveWithoutMetadata(h.getLevel().registryAccess());
      tag.put("Splitter", new SplitterSettings(2, 1, IRON).save());
      owner.loadWithComponents(tag, h.getLevel().registryAccess());
      seed(owner);
    }
    h.runAfterDelay(
        4,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockEntity(p) instanceof BeltBlockEntity,
              "partial priority fixture; refusing to reseed");
          var b = (BeltBlockEntity) h.getLevel().getBlockEntity(p);
          var settings = new SplitterSettings(2, 1, IRON);
          if (fresh) {
            h.assertTrue(
                b.splitterSettings().equals(settings) && count(b) == 16,
                "initial disk setup failed");
            System.out.println(
                "FACTORYCRAFT_SPLITTER_CONTROL_DISK: SEEDED exact settings, 16 carriers and"
                    + " cross-chunk identity");
          } else {
            h.assertTrue(
                b.splitterSettings().equals(settings) && count(b) == 16,
                "configured splitter disk ledger changed");
            for (int lane = 0; lane < 4; lane++)
              for (int i = 0; i < 4; i++)
                h.assertTrue(
                    b.lane(lane).carriers().get(i).positionUnits() == 96 - i * 24,
                    "disk carrier position changed");
            for (var cell : cells)
              if (!cell.equals(p))
                h.assertTrue(
                    h.getLevel().getBlockEntity(cell) instanceof CasingBlockEntity part
                        && part.boundTo(p, b.assembly().identity()),
                    "disk part identity changed");
            System.out.println(
                "FACTORYCRAFT_SPLITTER_CONTROL_DISK: VERIFIED exact settings, 16 carriers and"
                    + " cross-chunk identity");
          }
          for (var cell : cells) {
            h.getLevel().getBlockEntity(cell).onChunkUnloaded();
            h.getLevel().getChunkAt(cell).setUnsaved(true);
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void retainUninitializedV1SplitterProbeWithoutAddingResources(GameTestHelper h) {
    var p = new BlockPos(12015, 70, 12015);
    h.getLevel().getChunkAt(p);
    h.getLevel().getChunkAt(p.east());
    var be = h.getLevel().getBlockEntity(p);
    if (be == null) {
      h.succeed();
      return;
    }
    h.assertTrue(be instanceof BeltBlockEntity, "v1 evidence was replaced");
    var b = (BeltBlockEntity) be;
    h.assertTrue(
        count(b) == 0 && b.splitterSettings().equals(SplitterSettings.DEFAULT),
        "v1 failed setup was refilled or changed");
    for (var cell : factorycraft.multiblock.MachineProfile.SPLITTER.cells(p, Direction.NORTH)) {
      h.getLevel().getBlockEntity(cell).onChunkUnloaded();
      h.getLevel().getChunkAt(cell).setUnsaved(true);
    }
    System.out.println(
        "FACTORYCRAFT_SPLITTER_V1_EVIDENCE: retained uninitialized empty controller; no"
            + " refill/reset");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void finiteTwoInputSorterDeliversExactIronAndCopperChests(GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    factorycraft.diagnostics.SortingFactory.build(h.getLevel(), o);
    h.runAfterDelay(
        1500,
        () -> {
          for (int x : new int[] {6, 11}) {
            var chest =
                (net.minecraft.world.level.block.entity.ChestBlockEntity)
                    h.getLevel().getBlockEntity(o.offset(x, 0, 4));
            int n = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
              var stack = chest.getItem(slot);
              if (stack.isEmpty()) continue;
              h.assertTrue(
                  stack.is(x == 6 ? Items.RAW_IRON : Items.RAW_COPPER),
                  "sorter contaminated output chest");
              n += stack.getCount();
            }
            h.assertTrue(n == 32, "sorter delivery differs: " + x + "=" + n);
          }
          System.out.println(
              "FACTORYCRAFT_SORTING_FACTORY: VERIFIED two real inputs -> 32 iron left / 32 copper"
                  + " right");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void committedOutputSurvivesLiveConfigurationAndBlockEntityReload(
      GameTestHelper h) {
    var b = place(h, FactoryKind.SPLITTER, P);
    var left = place(h, FactoryKind.BELT, P.north());
    var right = place(h, FactoryKind.BELT, P.east().north());
    h.runAfterDelay(
        4,
        () -> {
          b.configureSplitter(new SplitterSettings(0, 2, null));
          b.inventory().insertItem(0, new ItemStack(Items.DIAMOND), false);
          h.runAfterDelay(
              9,
              () -> {
                h.assertTrue(
                    b.hasItems() && b.lane(0).carriers().getFirst().positionUnits() > 72,
                    "head did not reserve old output");
                b.configureSplitter(
                    new SplitterSettings(0, 1, BuiltInRegistries.ITEM.getKey(Items.DIAMOND)));
                var tag = b.saveWithFullMetadata(h.getLevel().registryAccess());
                h.assertTrue(
                    tag.getCompound("Splitter").getIntArray("CommittedOutputs")[0] == 2,
                    "committed exit not serialized");
                var pos = b.getBlockPos();
                var state = b.getBlockState();
                b.onChunkUnloaded();
                h.getLevel().removeBlockEntity(pos);
                var copy =
                    (BeltBlockEntity)
                        BlockEntity.loadStatic(pos, state, tag, h.getLevel().registryAccess());
                h.getLevel().setBlockEntity(copy);
                copy.onLoad();
                h.runAfterDelay(
                    25,
                    () -> {
                      h.assertTrue(
                          count(left) == 0 && count(right) == 1 && count(copy) == 0,
                          "reload rerouted already committed item");
                      copy.inventory().insertItem(0, new ItemStack(Items.DIAMOND), false);
                      h.runAfterDelay(
                          25,
                          () -> {
                            h.assertTrue(
                                count(left) == 1 && count(right) == 1 && count(copy) == 0,
                                "next item ignored new filter");
                            h.succeed();
                          });
                    });
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void rightSplitterOutputCountsAsRearInputWhenSideLoading(GameTestHelper h) {
    place(h, FactoryKind.SPLITTER, P);
    var output = place(h, FactoryKind.BELT, P.east().north());
    var side = P.east(2).north();
    h.getLevel()
        .setBlockAndUpdate(
            h.absolutePos(side),
            Content.FACTORY
                .get(FactoryKind.BELT)
                .get()
                .defaultBlockState()
                .setValue(FactoryBlock.FACING, Direction.WEST));
    h.runAfterDelay(
        5,
        () -> {
          BeltSimulation.topologyChanged(output);
          h.assertTrue(
              output.route().shape == factorycraft.belts.BeltRoute.Shape.FLAT,
              "right track was ignored and side feed bent the output");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void rightSplitterEntryPreventsAccidentalIncline(GameTestHelper h) {
    place(h, FactoryKind.SPLITTER, P);
    var input = place(h, FactoryKind.BELT, P.east().south());
    place(h, FactoryKind.BELT, P.east().above());
    h.runAfterDelay(
        5,
        () -> {
          BeltSimulation.topologyChanged(input);
          h.assertTrue(
              input.route().shape == factorycraft.belts.BeltRoute.Shape.FLAT,
              "valid right-track horizontal entry was replaced by incline");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void elevatedOutputOnlyTerminalCannotCreateAnIncline(GameTestHelper h) {
    var input = place(h, FactoryKind.BELT, P);
    var output = place(h, FactoryKind.UNDERGROUND, P.north().above());
    h.runAfterDelay(
        5,
        () -> {
          output.toggleUnderground();
          BeltSimulation.topologyChanged(input);
          h.assertTrue(
              input.route().shape == factorycraft.belts.BeltRoute.Shape.FLAT,
              "incline connected to output-only terminal");
          h.succeed();
        });
  }
}
