package factorycraft.multiblock;

import factorycraft.blocks.*;
import factorycraft.registry.Content;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Administrative fixtures only. Item placement uses MachineItem and normal multi-place protection.
 */
public final class StructurePlacement {
  public static void place(ServerLevel level, BlockPos anchor, BlockState state) {
    var profile = MachineProfile.of(state);
    if (profile != null)
      for (var p : profile.cells(anchor, profile.facing(state)))
        if (!level.hasChunkAt(p) || !level.getBlockState(p).isAir())
          throw new IllegalStateException("Occupied/unloaded structure cell: " + p);
    level.setBlockAndUpdate(anchor, state);
    if (profile == null) return;
    var part =
        Content.FACTORY
            .get(FactoryKind.CASING)
            .get()
            .defaultBlockState()
            .setValue(FactoryBlock.FACING, profile.facing(state))
            .setValue(FactoryBlock.PROFILE, profile);
    for (var p : profile.parts(anchor, profile.facing(state))) level.setBlockAndUpdate(p, part);
    if (!AssemblyAccess.owner(level.getBlockEntity(anchor)).assembly().formAutomatic())
      throw new IllegalStateException("Structure formation failed: " + anchor);
    if (level.getBlockEntity(anchor) instanceof factorycraft.blockentity.PoleBlockEntity pole)
      pole.requestAutoConnections();
  }

  public static BlockPos fromOrigin(ServerLevel level, BlockPos origin, BlockState state) {
    var p = MachineProfile.of(state);
    var controller = p == null ? origin : p.controllerAt(origin, p.facing(state), BlockPos.ZERO);
    place(level, controller, state);
    return controller;
  }

  private StructurePlacement() {}
}
