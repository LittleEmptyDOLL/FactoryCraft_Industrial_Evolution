package factorycraft.multiblock;

import factorycraft.blocks.FactoryBlock;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Identical loaded-footprint validation for server item placement and client preview. */
public record PlacementPlan(
    MachineProfile profile,
    BlockPos anchor,
    net.minecraft.core.Direction facing,
    List<BlockPos> cells,
    List<BlockPos> blocked,
    BlockPos target) {
  public BlockPos origin() {
    return profile.origin(anchor, facing);
  }

  public static BlockPos controllerFor(BlockPlaceContext context, BlockState state) {
    if (context instanceof CenteredPlacementContext) return context.getClickedPos();
    var profile = Objects.requireNonNull(MachineProfile.of(state));
    var facing = profile.facing(state);
    var target = context.getClickedPos();
    int x = profile.layout.width() / 2, z = profile.layout.depth() / 2;
    if (profile.layout.width() <= 2 && profile.layout.depth() <= 2) {
      x = profile.controllerLocal().getX();
      z = profile.controllerLocal().getZ();
    }
    if ((profile.layout.width() >= 5 || profile.layout.depth() >= 5)
        && !context.isSecondaryUseActive()) {
      var eye =
          context.getPlayer() == null
              ? target.getCenter().relative(facing, 4)
              : context.getPlayer().position();
      var delta = eye.subtract(target.getCenter());
      var right = facing.getClockWise();
      double r = delta.x * right.getStepX() + delta.z * right.getStepZ(),
          b = -delta.x * facing.getStepX() - delta.z * facing.getStepZ();
      if (Math.abs(r) > Math.abs(b)) x = r < 0 ? 0 : profile.layout.width() - 1;
      else z = b < 0 ? 0 : profile.layout.depth() - 1;
    }
    return profile.controllerAt(target, facing, new BlockPos(x, 0, z));
  }

  public BlockPos center() {
    return profile.center(anchor, facing);
  }

  public boolean valid() {
    return blocked.isEmpty();
  }

  public static PlacementPlan inspect(BlockPlaceContext context, BlockState state) {
    var profile = Objects.requireNonNull(MachineProfile.of(state));
    var level = context.getLevel();
    var player = context.getPlayer();
    var anchor = controllerFor(context, state);
    var cells = profile.cells(anchor, profile.facing(state));
    var blocked = new ArrayList<BlockPos>();
    var collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
    var part =
        Content.FACTORY
            .get(factorycraft.blocks.FactoryKind.CASING)
            .get()
            .defaultBlockState()
            .setValue(FactoryBlock.PROFILE, profile);
    for (var pos : cells) {
      var shape = pos.equals(anchor) ? state : part;
      if (level.isOutsideBuildHeight(pos)
          || !level.getWorldBorder().isWithinBounds(pos)
          || !level.hasChunkAt(pos)
          || level.getBlockEntity(pos) != null
          || !level
              .getBlockState(pos)
              .canBeReplaced(BlockPlaceContext.at(context, pos, context.getClickedFace()))
          || !level.isUnobstructed(shape, pos, collision)
          || player != null
              && (!level.mayInteract(player, pos)
                  || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())))
        blocked.add(pos);
    }
    return new PlacementPlan(
        profile,
        anchor.immutable(),
        profile.facing(state),
        cells,
        List.copyOf(blocked),
        context instanceof CenteredPlacementContext c
            ? c.placementTarget
            : context.getClickedPos());
  }
}
