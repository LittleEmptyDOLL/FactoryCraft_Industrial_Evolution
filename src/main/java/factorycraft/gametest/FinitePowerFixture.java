package factorycraft.gametest;

import factorycraft.blockentity.ThermalBlockEntity;
import factorycraft.blocks.ThermalBlock;
import factorycraft.fluids.FluidKey;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import factorycraft.thermal.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;

/** Test-only declared finite steam stock; never a registered block/item or survival generator. */
public final class FinitePowerFixture {
  public static ThermalBlockEntity place(
      ServerLevel level, BlockPos formerFront, Direction facing) {
    return place(level, formerFront, facing, 40000);
  }

  public static ThermalBlockEntity place(
      ServerLevel level, BlockPos formerFront, Direction facing, int steam) {
    var pos = formerFront.relative(facing.getOpposite(), 2);
    StructurePlacement.place(
        level,
        pos,
        Content.THERMAL_BLOCKS
            .get(ThermalKind.ENGINE)
            .get()
            .defaultBlockState()
            .setValue(ThermalBlock.FACING, facing));
    var be = (ThermalBlockEntity) level.getBlockEntity(pos);
    for (var p : be.assembly().profile().cells(pos, facing)) level.getBlockEntity(p).onLoad();
    if (steam > 0)
      be.steam.restore(
          new FluidKey(new net.neoforged.neoforge.fluids.FluidStack(SteamFluids.STEAM.get(), 1)),
          steam);
    return be;
  }

  private FinitePowerFixture() {}
}
