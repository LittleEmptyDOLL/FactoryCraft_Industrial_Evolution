package factorycraft.diagnostics;

import factorycraft.fluids.OffshorePlacement;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Fresh diagnostic land anchor and sealed basin, with no support required under the intake. */
public final class ShoreFixture {
  public static void prepare(ServerLevel level, BlockPos controller, Direction facing) {
    var land = controller.below();
    var water = OffshorePlacement.intake(controller, facing).below();
    for (var p :
        new BlockPos[] {
          land,
          water,
          water.below(),
          water.relative(facing.getOpposite()),
          water.relative(facing.getClockWise()),
          water.relative(facing.getCounterClockWise())
        })
      if (!level.hasChunkAt(p) || !level.getBlockState(p).isAir())
        throw new IllegalStateException("Occupied shore fixture " + p);
    level.setBlock(land, Blocks.STONE.defaultBlockState(), 2);
    level.setBlock(water.below(), Blocks.STONE.defaultBlockState(), 2);
    for (var d : Direction.Plane.HORIZONTAL)
      if (!water.relative(d).equals(land))
        level.setBlock(water.relative(d), Blocks.STONE.defaultBlockState(), 2);
    level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
  }

  private ShoreFixture() {}
}
