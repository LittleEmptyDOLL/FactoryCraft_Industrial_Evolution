package factorycraft.rail;

import factorycraft.FactoryCraft;
import factorycraft.simulation.rail.RailFootprint;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Placement and post-placement hooks share actual collision and safe replacement rules. */
public final class RailObstacles {
  public static final TagKey<Block> REPLACEABLE =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "rail_replaceable"));

  public static boolean replaceable(Level level, BlockPos pos, BlockState state, Player player) {
    if (state.isAir()) return true;
    if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
    var ctx =
        new BlockPlaceContext(
            level,
            player,
            net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(RailContent.RAIL.get()),
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    return (state.canBeReplaced(ctx) || state.is(REPLACEABLE))
        && (state.getCollisionShape(level, pos).isEmpty()
            || state.getBlock() instanceof SnowLayerBlock);
  }

  public static boolean clear(Level level, BlockPos p, BlockState state, RailFootprint footprint) {
    if (state.isAir() || state.is(RailContent.BED.get())) return true;
    var cell = RailwayData.cell(p);
    if (!state.getFluidState().isEmpty()
        && footprint.intersects(cell, new RailFootprint.Box(0, 0, 0, 1, 1, 1))) return false;
    for (var box : state.getCollisionShape(level, p).toAabbs())
      if (footprint.intersects(
          cell, new RailFootprint.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)))
        return false;
    return true;
  }

  private RailObstacles() {}
}
