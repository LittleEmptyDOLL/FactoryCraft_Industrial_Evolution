package factorycraft.multiblock;

import factorycraft.simulation.multiblock.PlacementAim;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Common target validation, with no client-class linkage and no unloaded world reads. */
public final class PlacementTarget {
  public static boolean valid(Level level, Player player, BlockHitResult hit) {
    if (level == null
        || player == null
        || hit == null
        || hit.getType() != HitResult.Type.BLOCK
        || !level.hasChunkAt(hit.getBlockPos())) return false;
    return PlacementAim.valid(
        true,
        level.getBlockState(hit.getBlockPos()).isAir(),
        true,
        player.getEyePosition().distanceToSqr(hit.getLocation()),
        player.blockInteractionRange());
  }

  private PlacementTarget() {}
}
