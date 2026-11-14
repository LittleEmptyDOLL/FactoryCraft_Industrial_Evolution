package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.IndustrialAssembly;
import factorycraft.registry.Content;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class AutomaticMultiblockGameTests {
  private static final BlockPos ORIGIN = new BlockPos(3, 2, 3);

  private static Item machineItem() {
    return Content.FACTORY.get(FactoryKind.FURNACE).get().asItem();
  }

  private static Player player(GameTestHelper h, boolean creative, Direction direction) {
    var player = h.makeMockPlayer(creative ? GameType.CREATIVE : GameType.SURVIVAL);
    player.getAbilities().instabuild = creative;
    player.setYRot(direction.getOpposite().toYRot());
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(machineItem(), 3));
    return player;
  }

  private static UseOnContext context(Player player, BlockPos pos) {
    return new UseOnContext(
        player,
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
  }

  private static InteractionResult place(Player player, BlockPos pos) {
    return PlacementTestSupport.place(player, pos);
  }

  private static MachineBlockEntity machine(GameTestHelper h, BlockPos pos) {
    return (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
  }

  private static List<BlockPos> cells(BlockPos pos, Direction direction) {
    var cells = new ArrayList<>(IndustrialAssembly.positions(pos, direction));
    cells.addFirst(pos);
    return cells;
  }

  private static int dropped(GameTestHelper h, BlockPos pos, Item item) {
    return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
        .filter(e -> e.getItem().is(item))
        .mapToInt(e -> e.getItem().getCount())
        .sum();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void oneSurvivalItemPlacesAndFormsAllTwentySevenBlocks(GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    var player = player(h, false, Direction.NORTH);
    h.assertTrue(
        place(player, pos).consumesAction() && player.getMainHandItem().getCount() == 2,
        "placement did not consume exactly one item");
    var machine = machine(h, pos);
    h.assertTrue(
        machine.assembly().formed() && machine.assembly().automatic(),
        "item did not automatically form structure");
    for (var p : machine.assembly().positions()) {
      var part = (CasingBlockEntity) h.getLevel().getBlockEntity(p);
      h.assertTrue(
          part.virtualPart() && part.boundTo(pos, machine.assembly().identity()),
          "generated part lost its identity");
    }
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(machine.assembly().ready(), "automatic structure never became ready");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void creativePlacementWorksInAllFourOrientationsWithoutConsumingItems(
      GameTestHelper h) {
    int i = 0;
    for (var d : Direction.Plane.HORIZONTAL) {
      var pos = h.absolutePos(new BlockPos(3 + i % 2 * 7, 2, 3 + i / 2 * 7));
      var player = player(h, true, d);
      i++;
      h.assertTrue(place(player, pos).consumesAction(), "rotated placement failed: " + d);
      h.assertTrue(
          player.getMainHandItem().getCount() == 3
              && machine(h, pos).getBlockState().getValue(FactoryBlock.FACING) == d,
          "creative cost or orientation changed");
      for (var p : IndustrialAssembly.positions(pos, d))
        h.assertTrue(
            h.getLevel().getBlockEntity(p) instanceof CasingBlockEntity, "rotated part missing");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void everyObstructedPartRejectsPlacementWithoutWorldOrItemChanges(
      GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    var player = player(h, false, Direction.NORTH);
    for (var blocked : IndustrialAssembly.positions(pos, Direction.NORTH)) {
      h.getLevel().setBlockAndUpdate(blocked, Blocks.DIAMOND_BLOCK.defaultBlockState());
      h.assertTrue(!place(player, pos).consumesAction(), "occupied footprint accepted");
      h.assertTrue(
          player.getMainHandItem().getCount() == 3 && h.getLevel().getBlockState(pos).isAir(),
          "failed placement spent item or placed controller");
      for (var p : IndustrialAssembly.positions(pos, Direction.NORTH))
        h.assertTrue(
            h.getLevel().getBlockState(p).is(p.equals(blocked) ? Blocks.DIAMOND_BLOCK : Blocks.AIR),
            "failed placement changed another cell");
      h.getLevel().removeBlock(blocked, false);
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void livingEntityInAnUpperPartRejectsPlacement(GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    var blocked = pos.offset(1, 1, 1);
    var cow = EntityType.COW.create(h.getLevel());
    cow.moveTo(blocked.getX() + .5, blocked.getY(), blocked.getZ() + .5, 0, 0);
    h.getLevel().addFreshEntity(cow);
    var player = player(h, false, Direction.NORTH);
    h.assertTrue(
        !place(player, pos).consumesAction() && player.getMainHandItem().getCount() == 3,
        "machine placed through a living entity");
    h.assertTrue(
        h.getLevel().getBlockState(pos).isAir(), "entity rejection left controller behind");
    cow.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void protectedPlacementRollsBackAllPartsAndReplaceableBlocks(GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    var player = player(h, false, Direction.NORTH);
    var plant = pos.offset(1, 0, 1);
    h.getLevel().setBlockAndUpdate(plant.below(), Blocks.DIRT.defaultBlockState());
    h.getLevel().setBlockAndUpdate(plant, Blocks.SHORT_GRASS.defaultBlockState());
    var shape = cells(pos, Direction.NORTH);
    var before = shape.stream().map(h.getLevel()::getBlockState).toList();
    int[] eventCount = {0};
    Consumer<BlockEvent.EntityMultiPlaceEvent> cancel =
        event -> {
          if (event.getEntity() == player) {
            eventCount[0]++;
            event.setCanceled(true);
          }
        };
    NeoForge.EVENT_BUS.addListener(cancel);
    try {
      h.assertTrue(
          !place(player, pos).consumesAction(), "protection event did not reject placement");
    } finally {
      NeoForge.EVENT_BUS.unregister(cancel);
    }
    h.assertTrue(
        eventCount[0] == 1 && player.getMainHandItem().getCount() == 3,
        "placement did not use normal multi-place protection/refund");
    for (int i = 0; i < shape.size(); i++)
      h.assertTrue(
          h.getLevel().getBlockState(shape.get(i)) == before.get(i),
          "cancelled placement changed footprint");
    h.assertTrue(
        dropped(h, pos, machineItem()) == 0
            && dropped(h, pos, Content.FACTORY.get(FactoryKind.CASING).get().asItem()) == 0,
        "rollback created free machine/casing drops");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void breakingAnyCellReturnsOnePortableMachineWithoutLooseCopies(GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    var shape = cells(pos, Direction.NORTH);
    for (int hit = 0; hit < shape.size(); hit++) {
      var player = player(h, false, Direction.NORTH);
      h.assertTrue(place(player, pos).consumesAction(), "test placement failed");
      var machine = machine(h, pos);
      machine.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 3));
      machine.items.setStackInSlot(4, new ItemStack(Items.DIAMOND, 5));
      h.getLevel().destroyBlock(shape.get(hit), true);
      for (var p : shape)
        h.assertTrue(
            h.getLevel().getBlockState(p).isAir(),
            "demolition left cell "
                + p
                + " after hitting "
                + hit
                + ": "
                + h.getLevel().getBlockState(p));
      h.assertTrue(
          dropped(h, pos, machineItem()) == 1
              && dropped(h, pos, Items.RAW_IRON) == 0
              && dropped(h, pos, Items.DIAMOND) == 0,
          "demolition duplicated or lost machine/inventory at cell " + hit);
      h.assertTrue(
          dropped(h, pos, Content.FACTORY.get(FactoryKind.CASING).get().asItem()) == 0,
          "generated parts dropped craftable resources");
      h.getLevel()
          .getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3))
          .forEach(ItemEntity::discard);
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void creativeBreakingAPartRemovesStructureWithoutMachineDrops(GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    var player = player(h, true, Direction.NORTH);
    place(player, pos);
    var part = machine(h, pos).assembly().positions().getFirst();
    var state = h.getLevel().getBlockState(part);
    state.getBlock().playerWillDestroy(h.getLevel(), part, state, player);
    h.getLevel().destroyBlock(part, false);
    for (var p : cells(pos, Direction.NORTH))
      h.assertTrue(h.getLevel().getBlockState(p).isAir(), "creative demolition left a part");
    h.assertTrue(dropped(h, pos, machineItem()) == 0, "creative breaking produced a machine item");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void generatedPortsAreInvalidatedAndLateOrphansRemoveThemselves(GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    place(player(h, false, Direction.NORTH), pos);
    var machine = machine(h, pos);
    var part = machine.assembly().positions().getFirst();
    h.runAfterDelay(
        5,
        () -> {
          machine.items.setStackInSlot(4, new ItemStack(Items.DIAMOND, 4));
          var port =
              h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, part, Direction.NORTH);
          var old = h.getLevel().getBlockEntity(part);
          var state = old.getBlockState();
          var tag = old.saveWithFullMetadata(h.getLevel().registryAccess());
          h.getLevel().destroyBlock(pos, true);
          h.assertTrue(
              port.extractItem(4, 4, false).isEmpty(),
              "cached port kept accessing a destroyed inventory");
          h.getLevel().setBlockAndUpdate(part, state);
          h.getLevel().removeBlockEntity(part);
          h.getLevel()
              .setBlockEntity(
                  BlockEntity.loadStatic(part, state, tag, h.getLevel().registryAccess()));
          h.runAfterDelay(
              5,
              () -> {
                h.assertTrue(
                    h.getLevel().getBlockState(part).isAir(),
                    "late loaded generated orphan became a free casing");
                h.assertTrue(
                    dropped(h, pos, machineItem()) == 1 && dropped(h, pos, Items.DIAMOND) == 0,
                    "late cleanup duplicated inventory or machine");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void sneakWrenchPacksAutomaticStructureAsOneMachine(GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    var player = player(h, false, Direction.NORTH);
    place(player, pos);
    var machine = machine(h, pos);
    machine.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 3));
    h.runAfterDelay(
        5,
        () -> {
          player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.WRENCH.get()));
          player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
          player.setShiftKeyDown(true);
          var part = machine.assembly().positions().getFirst();
          player.getMainHandItem().onItemUseFirst(context(player, part));
          for (var p : cells(pos, Direction.NORTH))
            h.assertTrue(h.getLevel().getBlockState(p).isAir(), "wrench left part of the machine");
          h.assertTrue(
              dropped(h, pos, machineItem()) == 1 && dropped(h, pos, Items.RAW_IRON) == 0,
              "wrench lost or duplicated machine/input");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void buildHeightOverflowRejectsWithoutSpendingAnItem(GameTestHelper h) {
    var pos =
        new BlockPos(
            h.absolutePos(ORIGIN).getX(),
            h.getLevel().getMaxBuildHeight() - 1,
            h.absolutePos(ORIGIN).getZ());
    var player = player(h, false, Direction.NORTH);
    h.assertTrue(
        !place(player, pos).consumesAction()
            && player.getMainHandItem().getCount() == 3
            && h.getLevel().getBlockState(pos).isAir(),
        "height rejection was not atomic");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void unloadedNeighborRejectsWithoutForceLoadingItsChunk(GameTestHelper h) {
    var pos = new BlockPos(50015, 75, 50015);
    h.getLevel().getChunkAt(pos);
    var neighbor = pos.east();
    h.assertTrue(!h.getLevel().hasChunkAt(neighbor), "unloaded fixture neighbor already loaded");
    var player = player(h, false, Direction.NORTH);
    h.assertTrue(
        !place(player, pos).consumesAction() && player.getMainHandItem().getCount() == 3,
        "unloaded footprint accepted");
    h.assertTrue(
        !h.getLevel().hasChunkAt(neighbor) && h.getLevel().getBlockState(pos).isAir(),
        "placement force-loaded neighbor or left controller");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 1200)
  public static void anItemPlacedFurnaceRunsTheCompleteAutomatedLine(GameTestHelper h) {
    var chest =
        factorycraft.core.FactoryCommands.ironLine(
            h.getLevel(), h.absolutePos(new BlockPos(1, 2, 1)));
    h.succeedWhen(
        () ->
            h.assertTrue(
                chest.getItem(0).getCount() >= 3,
                "full-size electric furnace line has not delivered gears"));
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void automaticStructureResumesSerializedPartialProduction(GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    place(player(h, false, Direction.NORTH), pos);
    var machine = machine(h, pos);
    machine.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 2));
    machine.power.restore(10000);
    h.runAfterDelay(
        10,
        () -> {
          var shape = cells(pos, Direction.NORTH);
          var copies = new ArrayList<BlockEntity>();
          var r = h.getLevel().registryAccess();
          for (var p : shape) {
            var be = h.getLevel().getBlockEntity(p);
            copies.add(
                BlockEntity.loadStatic(p, be.getBlockState(), be.saveWithFullMetadata(r), r));
          }
          for (var p : shape) h.getLevel().removeBlockEntity(p);
          for (var be : copies) h.getLevel().setBlockEntity(be);
        });
    h.runAfterDelay(
        110,
        () -> {
          var restored = machine(h, pos);
          int output = 0;
          for (int slot = 4; slot < 8; slot++)
            output += restored.items.getStackInSlot(slot).getCount();
          h.assertTrue(
              restored.assembly().automatic()
                  && restored.assembly().ready()
                  && output == 2
                  && restored.power.getEnergyStored() == 8464,
              "restoration lost automatic structure or partial production");
          h.getLevel().destroyBlock(restored.assembly().positions().getFirst(), true);
          for (var p : cells(pos, Direction.NORTH))
            h.assertTrue(
                h.getLevel().getBlockState(p).isAir(),
                "restored automatic structure lost its demolition semantics");
          h.assertTrue(
              dropped(h, pos, machineItem()) == 1,
              "restored automatic structure returned wrong machine count");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void automaticIdentityAndGeneratedFlagPersistAcrossServerProcesses(
      GameTestHelper h) {
    var level = h.getLevel();
    var pos = new BlockPos(143, 70, 95);
    var shape = cells(pos, Direction.NORTH);
    for (var p : shape) level.getChunkAt(p);
    if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity)) {
      h.assertTrue(
          place(player(h, false, Direction.NORTH), pos).consumesAction(),
          "disk fixture did not place from one item");
      machine(h, pos).items.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 3));
      System.out.println("FACTORYCRAFT_AUTOMATIC_DISK: SEEDED one-item cross-chunk assembly");
    } else {
      var machine = machine(h, pos);
      h.assertTrue(
          machine.assembly().formed()
              && machine.assembly().automatic()
              && machine.items.getStackInSlot(0).getCount() == 3,
          "disk controller lost automatic identity/state");
      for (var p : IndustrialAssembly.positions(pos, Direction.NORTH))
        h.assertTrue(
            level.getBlockEntity(p) instanceof CasingBlockEntity part
                && part.virtualPart()
                && part.boundTo(pos, machine.assembly().identity()),
            "disk generated part lost its flag or controller");
      System.out.println(
          "FACTORYCRAFT_AUTOMATIC_DISK: VERIFIED generated identity and inventory across four"
              + " chunks");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void explosionAcrossAllCellsCannotDuplicateMachineOrParts(GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    place(player(h, false, Direction.NORTH), pos);
    machine(h, pos).items.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 3));
    var explosion =
        new net.minecraft.world.level.Explosion(
            h.getLevel(),
            null,
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            2,
            false,
            net.minecraft.world.level.Explosion.BlockInteraction.DESTROY);
    var shape = cells(pos, Direction.NORTH);
    Collections.rotate(shape, 3);
    for (var p : shape)
      h.getLevel()
          .getBlockState(p)
          .onExplosionHit(
              h.getLevel(),
              p,
              explosion,
              (stack, where) -> Block.popResource(h.getLevel(), where, stack));
    for (var p : shape)
      h.assertTrue(h.getLevel().getBlockState(p).isAir(), "explosion left generated parts");
    h.assertTrue(
        dropped(h, pos, machineItem()) == 1
            && dropped(h, pos, Items.RAW_IRON) == 0
            && dropped(h, pos, Content.FACTORY.get(FactoryKind.CASING).get().asItem()) == 0,
        "explosion duplicated or lost resources");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void pickingAGeneratedPartReturnsTheMachineInsteadOfACasing(GameTestHelper h) {
    var pos = h.absolutePos(ORIGIN);
    place(player(h, false, Direction.NORTH), pos);
    var part = machine(h, pos).assembly().positions().getFirst();
    var state = h.getLevel().getBlockState(part);
    var picked =
        state
            .getBlock()
            .getCloneItemStack(
                state,
                new BlockHitResult(Vec3.atCenterOf(part), Direction.UP, part, false),
                h.getLevel(),
                part,
                player(h, true, Direction.NORTH));
    h.assertTrue(
        picked.is(machineItem()) && picked.getCount() == 1,
        "pick block exposed a generated structural item");
    h.succeed();
  }
}
