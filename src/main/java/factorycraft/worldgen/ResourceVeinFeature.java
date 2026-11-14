package factorycraft.worldgen;

import factorycraft.blockentity.ResourceDepositBlockEntity;
import factorycraft.config.ResourceWorldgenConfig;
import factorycraft.simulation.resources.ResourceVeins;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.*;

/** Full canonical ellipsoids, clipped only by existing natural solids in the decorating chunk. */
public final class ResourceVeinFeature extends Feature<ResourceVeinConfiguration> {
  public ResourceVeinFeature() {
    super(ResourceVeinConfiguration.CODEC);
  }

  public boolean place(FeaturePlaceContext<ResourceVeinConfiguration> context) {
    var level = context.level();
    var config = context.config();
    var settings =
        config.useWorldSettings() ? ResourceWorldgenConfig.settings() : config.settings();
    int cx = context.origin().getX() >> 4, cz = context.origin().getZ() >> 4, placed = 0;
    var decorating = level.getChunk(cx, cz);
    if (ResourceTerrain.protectedStructure(decorating)) return false;
    var patches = ResourceVeins.inChunk(level.getSeed(), config.salt(), cx, cz, settings);
    if (patches.isEmpty()) return false;
    var bodies =
        patches.stream()
            .map(p -> p.atHeight(GeneratorTerrain.anchor(context, p.x(), p.z())))
            .toList();
    var columns = new ResourceVeins.Column[bodies.size()];
    var pos = new BlockPos.MutableBlockPos();
    for (int x = cx * 16; x < cx * 16 + 16; x++)
      for (int z = cz * 16; z < cz * 16 + 16; z++) {
        int bottom = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
        for (int i = 0; i < bodies.size(); i++) {
          var body = bodies.get(i);
          columns[i] = body.column(x, z);
          if (columns[i].horizontal() < 1) {
            bottom = Math.min(bottom, body.minY());
            top = Math.max(top, body.maxY());
          }
        }
        if (bottom > top) continue;
        for (int y = Math.max(bottom, level.getMinBuildHeight());
            y <= Math.min(top, level.getMaxBuildHeight() - 1);
            y++) {
          int stock = 0;
          for (var column : columns) stock = Math.max(stock, column.stock(y));
          if (stock == 0) continue;
          pos.set(x, y, z);
          var old = level.getBlockState(pos);
          if (!ResourceTerrain.replaceable(old) || level.getBlockEntity(pos) != null) continue;
          if (level.setBlock(pos, config.state(), 2)) {
            if (!(level.getBlockEntity(pos) instanceof ResourceDepositBlockEntity be))
              throw new IllegalStateException("Missing resource owner");
            be.initializeGeneratedReserve(stock);
            placed++;
          }
        }
      }
    return placed > 0;
  }
}
