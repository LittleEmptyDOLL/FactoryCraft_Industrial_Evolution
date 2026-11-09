package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.multiblock.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class MachineFootprintGameTests {
  static void verify(GameTestHelper h, String id) {
    var profile = MachineProfile.byId(id);
    var anchor = h.absolutePos(new BlockPos(10, 2, 10));
    var block =
        BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, id));
    for (var direction : Direction.Plane.HORIZONTAL) {
      var player = h.makeMockPlayer(GameType.SURVIVAL);
      player.getAbilities().instabuild = false;
      player.setYRot(
          (profile.splitter() || profile.circuit() ? direction : direction.getOpposite()).toYRot());
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block, 2));
      var context =
          new UseOnContext(
              player,
              InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false));
      var placement = new BlockPlaceContext(context);
      var state = block.getStateForPlacement(placement);
      var preview = PlacementPlan.inspect(placement, state);
      h.assertTrue(
          preview.valid()
              && preview.cells().size()
                  == profile.layout.width() * profile.layout.height() * profile.layout.depth(),
          "preview volume differs from profile");
      h.assertTrue(
          player.getMainHandItem().useOn(context).consumesAction()
              && player.getMainHandItem().getCount() == 1,
          "one-item formation failed: " + id + " " + direction);
      var controller = preview.anchor();
      h.assertTrue(
          preview.center().equals(anchor), "preview is not centered on selected base cell");
      var owner = AssemblyAccess.owner(h.getLevel().getBlockEntity(controller));
      h.assertTrue(owner != null && owner.assembly().formed(), "controller failed to form");
      for (var cell : preview.cells())
        h.assertTrue(
            cell.equals(controller)
                || h.getLevel().getBlockEntity(cell) instanceof CasingBlockEntity part
                    && part.boundTo(controller, owner.assembly().identity()),
            "preview does not match actual parts");
      for (var cell : preview.cells())
        if (!cell.equals(controller)) {
          var data = h.getLevel().getBlockEntity(cell).getModelData().get(MachineModelData.CELL);
          var delta = cell.subtract(controller);
          var right = direction.getClockWise();
          int r = delta.getX() * right.getStepX() + delta.getZ() * right.getStepZ(),
              back = -delta.getX() * direction.getStepX() - delta.getZ() * direction.getStepZ();
          h.assertTrue(
              data != null
                  && data.profile() == profile
                  && data.right() == r
                  && data.up() == delta.getY()
                  && data.back() == back,
              "part mesh coordinates disagree with rotated placement");
        }
      for (var cell : preview.cells()) h.getLevel().getBlockEntity(cell).onLoad();
      h.assertTrue(owner.assembly().ready(), "full structure failed availability check");
      var part =
          (CasingBlockEntity) h.getLevel().getBlockEntity(owner.assembly().positions().getLast());
      part.onChunkUnloaded();
      h.assertTrue(!owner.assembly().ready(), "unloaded cell did not suspend machine");
      part.onLoad();
      h.assertTrue(owner.assembly().ready(), "reloaded cell did not resume machine");
      owner.assembly().removeWhole(false);
      for (var cell : preview.cells())
        h.assertTrue(h.getLevel().getBlockState(cell).isAir(), "demolition left a generated block");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void arithmeticCombinatorMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "arithmetic_combinator");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void deciderCombinatorMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "decider_combinator");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void electricDrillMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "electric_drill");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void electricFurnaceMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "electric_furnace");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void assemblingMachineMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "assembling_machine");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void fluidAssemblerMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "assembling_machine_2");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void coalGeneratorMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "coal_generator");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void industrialFurnaceMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "industrial_furnace");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void pumpjackMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "pumpjack");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void oilRefineryMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "oil_refinery");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void chemicalPlantMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "chemical_plant");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void boilerMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "boiler");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void steamEngineMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "steam_engine");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void fluidTankMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "fluid_tank");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void electricPumpMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "electric_pump");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void splitterMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "splitter");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void fastSplitterMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "fast_splitter");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void expressSplitterMatchesPreviewInEveryOrientation(GameTestHelper h) {
    verify(h, "express_splitter");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void survivalRefineryFitsAtNormalReachAndIsCenteredWithoutIntersectingThePlayer(
      GameTestHelper h) {
    var anchor = h.absolutePos(new BlockPos(10, 2, 10));
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.getAbilities().instabuild = false;
    player.moveTo(anchor.getX() + .5, anchor.getY(), anchor.getZ() - 3.5, 0, 25);
    player.setItemInHand(
        InteractionHand.MAIN_HAND,
        new ItemStack(
            BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "oil_refinery"))));
    // A real collidable body at the player's feet catches a structure extending back through them.
    var body = net.minecraft.world.entity.EntityType.COW.create(h.getLevel());
    body.moveTo(player.getX(), player.getY(), player.getZ(), 0, 0);
    h.getLevel().addFreshEntity(body);
    var hit = new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false);
    var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
    h.assertTrue(
        player.getMainHandItem().useOn(context).consumesAction()
            && player.getMainHandItem().isEmpty(),
        "5x5 refinery could not be placed from normal survival reach");
    var controller = MachineProfile.REFINERY.anchorAtCenter(anchor, Direction.NORTH);
    var owner = AssemblyAccess.owner(h.getLevel().getBlockEntity(controller));
    h.assertTrue(
        owner != null && owner.assembly().positions().size() == 99, "wrong refinery volume");
    body.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void centeredPlacementRejectsAnUnloadedCornerWithoutLoadingIt(GameTestHelper h) {
    var center = new BlockPos(60015, 75, 60015);
    var level = h.getLevel();
    level.getChunkAt(center);
    var outside = center.east();
    h.assertTrue(!level.hasChunkAt(outside), "test corner already loaded");
    level.setBlock(
        center.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 18);
    h.assertTrue(!level.hasChunkAt(outside), "fixture floor loaded outside chunk");
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.getAbilities().instabuild = false;
    player.setYRot(Direction.SOUTH.toYRot());
    player.setItemInHand(
        InteractionHand.MAIN_HAND,
        new ItemStack(
            factorycraft.registry.Content.FACTORY
                .get(factorycraft.blocks.FactoryKind.ASSEMBLER)
                .get()));
    var floor = center.below();
    var result =
        player
            .getMainHandItem()
            .useOn(
                new UseOnContext(
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
    h.assertTrue(
        !result.consumesAction()
            && player.getMainHandItem().getCount() == 1
            && !level.hasChunkAt(outside),
        "centered footprint consumed item or loaded corner");
    h.succeed();
  }
}
