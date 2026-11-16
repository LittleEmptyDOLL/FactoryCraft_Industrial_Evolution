package factorycraft.fluids;

import factorycraft.multiblock.MachineProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Shore width is the actual two-tile intake, not an invented lake-size/biome restriction. */
public final class OffshorePlacement {
  public static List<BlockPos> invalid(Level level, BlockPos controller, Direction facing) {
    var blocked = new ArrayList<BlockPos>();
    var profile = MachineProfile.OFFSHORE;
    for (int x = 0; x < 2; x++) {
      var land = profile.offset(controller, facing, x, 0, 0).below();
      var water = land.relative(facing.getOpposite());
      if (!level.hasChunkAt(land)
          || !level.getBlockState(land).isFaceSturdy(level, land, Direction.UP)) blocked.add(land);
      if (!level.hasChunkAt(water)
          || !level.getBlockState(water).is(Blocks.WATER)
          || !level.getFluidState(water).isSource()) blocked.add(water);
      var clearance = water.above();
      if (!level.hasChunkAt(clearance) || !level.getBlockState(clearance).isAir())
        blocked.add(clearance);
    }
    return List.copyOf(blocked);
  }

  private OffshorePlacement() {}
}
