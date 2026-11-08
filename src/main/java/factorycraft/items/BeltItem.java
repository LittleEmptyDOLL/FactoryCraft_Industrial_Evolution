package factorycraft.items;

import factorycraft.belts.*;
import factorycraft.blockentity.BeltBlockEntity;
import factorycraft.blocks.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Uses native placement snapshots, including all generated clearance cells. */
public class BeltItem extends BlockItem {
  public BeltItem(Block block, Properties properties) {
    super(block, properties);
  }

  @Override
  public void appendHoverText(
      net.minecraft.world.item.ItemStack stack,
      TooltipContext context,
      java.util.List<net.minecraft.network.chat.Component> lines,
      net.minecraft.world.item.TooltipFlag flags) {
    super.appendHoverText(stack, context, lines, flags);
    lines.add(net.minecraft.network.chat.Component.translatable("tooltip.factorycraft.belt"));
    lines.add(
        net.minecraft.network.chat.Component.translatable(
            this instanceof LiftItem
                ? "tooltip.factorycraft.lift_controls"
                : "tooltip.factorycraft.transport_controls"));
  }

  protected BeltRoute placementRoute(BlockPlaceContext context) {
    var data =
        context.getItemInHand().get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
    if (data != null) {
      var cell = TransportPlan.loadCell(data.copyTag().getCompound("Route"));
      if (cell != null) return cell.route();
    }
    if (((FactoryBlock) getBlock()).kind.underground())
      return new BeltRoute(BeltRoute.Shape.UNDERGROUND, 1, 0);
    return ((FactoryBlock) getBlock()).kind.ordinaryBelt()
        ? TransportPlan.automatic(
            context.getLevel(), context.getClickedPos(), context.getHorizontalDirection())
        : BeltRoute.FLAT;
  }

  protected boolean canPlace(BlockPlaceContext context, BlockState state) {
    return super.canPlace(context, state)
        && TransportPlan.inspect(context, placementRoute(context)).valid();
  }

  protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
    if (context.getLevel().isClientSide) return super.placeBlock(context, state);
    var level = context.getLevel();
    var plan = TransportPlan.inspect(context, placementRoute(context));
    if (!plan.valid()) return false;
    var old = plan.cells().stream().map(level::getBlockState).toList();
    int snapshots = level.capturedBlockSnapshots.size();
    boolean success = false;
    try {
      if (!super.placeBlock(context, state)) return false;
      if (!(level.getBlockEntity(plan.anchor()) instanceof BeltBlockEntity owner)
          || !owner.initializeRoute(plan.route())) return false;
      owner.chooseUndergroundRole();
      success = TransportPlan.buildParts(owner);
      return success;
    } finally {
      if (!success) {
        boolean restoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
          for (int i = old.size() - 1; i >= 0; i--)
            level.setBlock(plan.cells().get(i), old.get(i), 3);
        } finally {
          level.restoringBlockSnapshots = restoring;
        }
        level
            .capturedBlockSnapshots
            .subList(snapshots, level.capturedBlockSnapshots.size())
            .clear();
      }
    }
  }
}
