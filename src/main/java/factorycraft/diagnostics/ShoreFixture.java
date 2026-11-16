package factorycraft.diagnostics;

import factorycraft.multiblock.MachineProfile;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Explicit fresh diagnostic shoreline. Does not create any stored working fluid or FE. */
public final class ShoreFixture {
  public static void prepare(ServerLevel level, BlockPos controller, Direction facing) {
    var profile = MachineProfile.OFFSHORE;
    for (int x = 0; x < 2; x++) {
      var land = profile.offset(controller, facing, x, 0, 0).below();
      var water = land.relative(facing.getOpposite());
      for (var p :
          new BlockPos[] {land, water, water.below(), water.relative(facing.getOpposite())})
        if (!level.hasChunkAt(p) || !level.getBlockState(p).isAir())
          throw new IllegalStateException("Occupied shore fixture " + p);
    }
    for (int x = 0; x < 2; x++) {
      var land = profile.offset(controller, facing, x, 0, 0).below();
      var water = land.relative(facing.getOpposite());
      level.setBlock(land, Blocks.STONE.defaultBlockState(), 2);
      level.setBlock(water.below(), Blocks.STONE.defaultBlockState(), 2);
      level.setBlock(water.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState(), 2);
      if (x == 0)
        level.setBlock(
            water.relative(facing.getCounterClockWise()), Blocks.STONE.defaultBlockState(), 2);
      if (x == 1)
        level.setBlock(water.relative(facing.getClockWise()), Blocks.STONE.defaultBlockState(), 2);
      level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
    }
  }

  private ShoreFixture() {}
}
