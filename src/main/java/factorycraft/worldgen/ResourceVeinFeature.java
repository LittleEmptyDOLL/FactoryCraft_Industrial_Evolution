package factorycraft.worldgen;

import factorycraft.blockentity.ResourceDepositBlockEntity;
import factorycraft.config.ResourceWorldgenConfig;
import factorycraft.simulation.resources.ResourceVeins;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.*;

/** Irregular half ellipsoids, safe region reads and decorating-chunk-only BE-aware writes. */
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
    if (level instanceof net.minecraft.server.level.WorldGenRegion)
      ResourceTerrain.capture(decorating);
    if (ResourceTerrain.protectedStructure(decorating)) return false;
    var patches = ResourceVeins.inChunk(level.getSeed(), config.salt(), cx, cz, settings);
    if (patches.isEmpty()) return false;
    var pos = new BlockPos.MutableBlockPos();
    factorycraft.simulation.resources.SurfaceWindow terrain = null;
    var columns = new ResourceVeins.Column[patches.size()];
    for (int x = cx * 16; x < cx * 16 + 16; x++)
      for (int z = cz * 16; z < cz * 16 + 16; z++) {
        int depth = 0;
        for (int i = 0; i < patches.size(); i++) {
          columns[i] = patches.get(i).column(x, z);
          depth = Math.max(depth, columns[i].depth());
        }
        if (depth == 0) continue;
        int natural = ResourceTerrain.surface(level, x, z);
        if (!level.getFluidState(pos.set(x, natural + 1, z)).isEmpty()) continue;
        if (terrain == null)
          terrain = ResourceTerrain.window(level, cx, cz, ResourceWorldgenConfig.SMOOTHING.get());
        int top = terrain.height(x, z);
        for (int down = 0; down < depth; down++) {
          int y = top - down;
          if (y > natural || level.isOutsideBuildHeight(y)) continue;
          pos.set(x, y, z);
          var old = level.getBlockState(pos);
          if (!ResourceTerrain.replaceable(old) || level.getBlockEntity(pos) != null) continue;
          int stock = 0;
          for (var column : columns) stock = Math.max(stock, column.stock(down));
          if (stock == 0) continue;
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
