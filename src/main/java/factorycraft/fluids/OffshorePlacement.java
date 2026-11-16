package factorycraft.fluids;

import factorycraft.blockentity.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Land anchor/outlet faces inland; the only other physical cell hangs above source water. */
public final class OffshorePlacement {
  public static BlockPos intake(BlockPos controller, Direction facing) {
    return controller.relative(facing.getOpposite());
  }

  public static List<BlockPos> invalid(Level level, BlockPos controller, Direction facing) {
    var blocked = new ArrayList<BlockPos>();
    var land = controller.below();
    var overWater = intake(controller, facing);
    var water = overWater.below();
    if (!level.hasChunkAt(land)
        || !level.getBlockState(land).isFaceSturdy(level, land, Direction.UP)) blocked.add(land);
    if (!level.hasChunkAt(water)
        || !level.getBlockState(water).is(Blocks.WATER)
        || !level.getFluidState(water).isSource()) blocked.add(water);
    if (!level.hasChunkAt(overWater)) blocked.add(overWater);
    else if (!level.getBlockState(overWater).isAir()) {
      var part = FluidConnections.at(level, overWater);
      var owner = FluidConnections.at(level, controller);
      if (!(part instanceof CasingBlockEntity casing)
          || !(owner instanceof FluidBlockEntity pump)
          || !casing.boundTo(controller, pump.assembly().identity())) blocked.add(overWater);
    }
    return List.copyOf(blocked);
  }

  private OffshorePlacement() {}
}
