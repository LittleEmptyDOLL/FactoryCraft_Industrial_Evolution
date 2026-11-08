package factorycraft.items;

import factorycraft.belts.*;
import factorycraft.config.FactoryConfig;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;

/** Server validates both clicks again; a selection is not a block or a resource owner. */
public final class LiftItem extends BeltItem {
  public LiftItem(Block b, Properties p) {
    super(b, p);
  }

  public static CompoundTag selection(ItemStack stack) {
    var data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null ? new CompoundTag() : data.copyTag().getCompound("FactoryCraftLift");
  }

  private static void select(ItemStack stack, CompoundTag selection) {
    var existing = stack.get(DataComponents.CUSTOM_DATA);
    var data = existing == null ? new CompoundTag() : existing.copyTag();
    if (selection.isEmpty()) data.remove("FactoryCraftLift");
    else data.put("FactoryCraftLift", selection);
    if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
    else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
  }

  public static final class Context extends BlockPlaceContext {
    private final Direction facing;
    public final BeltRoute route;

    public Context(BlockPlaceContext original, BlockPos anchor, Direction facing, BeltRoute route) {
      super(
          original.getLevel(),
          original.getPlayer(),
          original.getHand(),
          original.getItemInHand(),
          new BlockHitResult(Vec3.atCenterOf(anchor), original.getClickedFace(), anchor, false));
      replaceClicked = true;
      this.facing = facing;
      this.route = route;
    }

    @Override
    public Direction getHorizontalDirection() {
      return facing;
    }
  }

  public Context selectedContext(BlockPlaceContext original) {
    var data = selection(original.getItemInHand());
    if (!data.contains("Anchor")
        || !data.getString("Dimension")
            .equals(original.getLevel().dimension().location().toString())) return null;
    var anchor = BlockPos.of(data.getLong("Anchor"));
    int height = original.getClickedPos().getY() - anchor.getY();
    if (height == 0
        || Math.abs(height) > FactoryConfig.LIFT_HEIGHT.get()
        || original.getPlayer() != null
            && original.getPlayer().distanceToSqr(Vec3.atCenterOf(anchor)) > 24 * 24) return null;
    var facing = Direction.from3DDataValue(data.getInt("Facing"));
    if (!facing.getAxis().isHorizontal()) return null;
    var exit = original.getHorizontalDirection();
    int turn = 0;
    for (var d = facing; d != exit; d = d.getClockWise()) turn++;
    var route = new BeltRoute(BeltRoute.Shape.LIFT, height, turn);
    var portable = original.getItemInHand().get(DataComponents.BLOCK_ENTITY_DATA);
    if (portable != null) {
      var saved = TransportPlan.loadCell(portable.copyTag().getCompound("Route"));
      if (saved != null && (saved.route().span != height || saved.route().exitTurn != turn))
        return null;
    }
    return new Context(original, anchor, facing, route);
  }

  @Override
  public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
    return context;
  }

  @Override
  protected BeltRoute placementRoute(BlockPlaceContext context) {
    return context instanceof Context lift ? lift.route : super.placementRoute(context);
  }

  @Override
  public InteractionResult useOn(UseOnContext use) {
    var player = use.getPlayer();
    if (player == null) return InteractionResult.FAIL;
    var stack = use.getItemInHand();
    if (player.isShiftKeyDown()) {
      select(stack, new CompoundTag());
      return InteractionResult.sidedSuccess(use.getLevel().isClientSide);
    }
    var original = new BlockPlaceContext(use);
    var data = selection(stack);
    if (!data.contains("Anchor")) {
      var anchor = original.getClickedPos();
      if (!use.getLevel().hasChunkAt(anchor)
          || !use.getLevel().getBlockState(anchor).canBeReplaced(original))
        return InteractionResult.FAIL;
      data.putLong("Anchor", anchor.asLong());
      data.putString("Dimension", use.getLevel().dimension().location().toString());
      data.putInt("Facing", original.getHorizontalDirection().get3DDataValue());
      select(stack, data);
      player.displayClientMessage(
          Component.translatable("message.factorycraft.lift_select_height"), true);
      return InteractionResult.sidedSuccess(use.getLevel().isClientSide);
    }
    var context = selectedContext(original);
    if (context == null) {
      player.displayClientMessage(
          Component.translatable(
              "message.factorycraft.lift_invalid", FactoryConfig.LIFT_HEIGHT.get()),
          true);
      return InteractionResult.FAIL;
    }
    var result = place(context);
    if (result.consumesAction()) select(stack, new CompoundTag());
    return result;
  }
}
