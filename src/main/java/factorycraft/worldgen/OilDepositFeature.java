package factorycraft.worldgen;

import factorycraft.blockentity.OilDepositBlockEntity;
import factorycraft.config.ResourceWorldgenConfig;
import factorycraft.registry.Content;
import factorycraft.simulation.resources.OilFields;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Surface fields, never underground finite reservoirs or neighbor writes. */
public final class OilDepositFeature extends Feature<NoneFeatureConfiguration> {
  public OilDepositFeature() {
    super(NoneFeatureConfiguration.CODEC);
  }

  public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> c) {
    var level = c.level();
    int cx = c.origin().getX() >> 4, cz = c.origin().getZ() >> 4, n = 0;
    var decorating = level.getChunk(cx, cz);
    if (level instanceof net.minecraft.server.level.WorldGenRegion)
      ResourceTerrain.capture(decorating);
    if (ResourceTerrain.protectedStructure(decorating)) return false;
    for (var p :
        OilFields.inChunk(
            level.getSeed(),
            cx,
            cz,
            ResourceWorldgenConfig.OIL_SPACING.get(),
            ResourceWorldgenConfig.OIL_RADIUS.get(),
            ResourceWorldgenConfig.OIL_POINTS_MIN.get(),
            ResourceWorldgenConfig.OIL_POINTS_MAX.get(),
            ResourceWorldgenConfig.OIL_MIN_YIELD.get(),
            ResourceWorldgenConfig.OIL_MAX_YIELD.get())) {
      var pos = new BlockPos(p.x(), ResourceTerrain.surface(level, p.x(), p.z()), p.z());
      if (level.isOutsideBuildHeight(pos)
          || !level.getFluidState(pos.above()).isEmpty()
          || !level.getBlockState(pos.above()).isAir()
          || !ResourceTerrain.replaceable(level.getBlockState(pos))
          || level.getBlockEntity(pos) != null) continue;
      if (level.setBlock(pos, Content.OIL_DEPOSIT.get().defaultBlockState(), 2)) {
        ((OilDepositBlockEntity) level.getBlockEntity(pos))
            .initializeGeneratedYield(p.yieldHundredths());
        n++;
      }
    }
    return n > 0;
  }
}
