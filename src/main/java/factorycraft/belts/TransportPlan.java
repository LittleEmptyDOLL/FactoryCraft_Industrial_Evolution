package factorycraft.belts;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;

public record TransportPlan(
    BlockPos anchor,
    Direction facing,
    BeltRoute route,
    List<BlockPos> cells,
    List<BlockPos> blocked) {
  public boolean valid() {
    return blocked.isEmpty();
  }

  public static List<BlockPos> cells(BlockPos anchor, BeltRoute route) {
    var result = new ArrayList<BlockPos>();
    result.add(anchor);
    int h = route.shape == BeltRoute.Shape.LIFT ? route.span : route.heightDelta();
    if (route.shape == BeltRoute.Shape.UP
        || route.shape == BeltRoute.Shape.DOWN
        || route.shape == BeltRoute.Shape.LIFT)
      for (int y = Integer.signum(h); Math.abs(y) <= Math.abs(h); y += Integer.signum(h))
        result.add(anchor.offset(0, y, 0));
    return List.copyOf(result);
  }

  public static TransportPlan inspect(BlockPlaceContext context, BeltRoute route) {
    var level = context.getLevel();
    var anchor = context.getClickedPos();
    var facing = context.getHorizontalDirection();
    var cells = cells(anchor, route);
    var blocked = new ArrayList<BlockPos>();
    var body =
        route.heightDelta() != 0
            ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
            : Content.FACTORY.get(FactoryKind.TRANSPORT_PART).get().defaultBlockState();
    for (var p : cells) {
      if (level.isOutsideBuildHeight(p)
          || !level.getWorldBorder().isWithinBounds(p)
          || !level.hasChunkAt(p)
          || level.getBlockEntity(p) != null
          || !level
              .getBlockState(p)
              .canBeReplaced(BlockPlaceContext.at(context, p, context.getClickedFace()))
          || !level.isUnobstructed(
              body,
              p,
              context.getPlayer() == null
                  ? CollisionContext.empty()
                  : CollisionContext.of(context.getPlayer()))
          || context.getPlayer() != null
              && (!level.mayInteract(context.getPlayer(), p)
                  || !context
                      .getPlayer()
                      .mayUseItemAt(p, context.getClickedFace(), context.getItemInHand())))
        blocked.add(p);
    }
    return new TransportPlan(anchor, facing, route, cells, List.copyOf(blocked));
  }

  public static void saveCell(CompoundTag tag, BeltModelData.Cell cell) {
    tag.putString("Shape", cell.route().shape.name());
    tag.putInt("Span", cell.route().span);
    tag.putInt("ExitTurn", cell.route().exitTurn);
    tag.putInt("Facing", cell.facing().get3DDataValue());
    tag.putInt("Tier", cell.tier());
    tag.putInt("OffsetY", cell.offsetY());
    tag.putBoolean("Output", cell.output());
  }

  public static BeltModelData.Cell loadCell(CompoundTag tag) {
    if (!tag.contains("Shape")) return null;
    try {
      var route =
          new BeltRoute(
              BeltRoute.Shape.valueOf(tag.getString("Shape")),
              tag.getInt("Span"),
              tag.getInt("ExitTurn"));
      var facing = Direction.from3DDataValue(tag.getInt("Facing"));
      if (!facing.getAxis().isHorizontal()) return null;
      int offset = tag.getInt("OffsetY");
      if (offset < Math.min(0, route.heightDelta()) || offset > Math.max(0, route.heightDelta()))
        return null;
      return new BeltModelData.Cell(
          route, facing, Math.clamp(tag.getInt("Tier"), 1, 3), offset, tag.getBoolean("Output"));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static boolean buildParts(BeltBlockEntity owner) {
    var level = owner.getLevel();
    for (var p : cells(owner.getBlockPos(), owner.route()))
      if (!p.equals(owner.getBlockPos())) {
        if (!level.setBlock(
            p, Content.FACTORY.get(FactoryKind.TRANSPORT_PART).get().defaultBlockState(), 2))
          return false;
        ((TransportPartBlockEntity) level.getBlockEntity(p)).bind(owner);
      }
    return true;
  }

  private static boolean free(Level level, BlockPos p, BlockPos owner) {
    var b = factorycraft.fluids.FluidConnections.at(level, p);
    return level.hasChunkAt(p)
        && (level.getBlockState(p).isAir()
            || b instanceof TransportPartBlockEntity part
                && part.ownerForRemoval() != null
                && part.ownerForRemoval().getBlockPos().equals(owner));
  }

  private static boolean source(Level level, BlockPos p, Direction facing) {
    var be = factorycraft.simulation.BeltSimulation.at(level, p);
    return be != null
        && be.loaded()
        && be.outputPosition().equals(p)
        && be.outputFacing() == facing;
  }

  public static BeltRoute automatic(Level level, BlockPos p, Direction facing) {
    var front = factorycraft.simulation.BeltSimulation.at(level, p.relative(facing));
    if (front != null && !front.trackPosition(0).equals(p.relative(facing))) front = null;
    if (front == null) {
      var high = factorycraft.simulation.BeltSimulation.at(level, p.relative(facing).above());
      if (high != null && high.facing() != facing.getOpposite() && free(level, p.above(), p))
        return new BeltRoute(BeltRoute.Shape.UP, 0, 0);
      var low = factorycraft.simulation.BeltSimulation.at(level, p.relative(facing).below());
      if (low != null && low.facing() != facing.getOpposite() && free(level, p.below(), p))
        return new BeltRoute(BeltRoute.Shape.DOWN, 0, 0);
    }
    boolean rear = source(level, p.relative(facing.getOpposite()), facing),
        left = source(level, p.relative(facing.getCounterClockWise()), facing.getClockWise()),
        right = source(level, p.relative(facing.getClockWise()), facing.getCounterClockWise());
    return !rear && left != right
        ? new BeltRoute(left ? BeltRoute.Shape.LEFT : BeltRoute.Shape.RIGHT, 0, 0)
        : BeltRoute.FLAT;
  }
}
