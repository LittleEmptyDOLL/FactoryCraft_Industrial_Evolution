package factorycraft.worldgen;

import factorycraft.blockentity.ResourceDepositBlockEntity;
import factorycraft.config.ResourceWorldgenConfig;
import factorycraft.simulation.resources.ResourceVeins;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;

/** Large fields sampled in world coordinates, with writes confined to the decorating chunk.
 * Never loads a neighbor or mutates ServerLevel from worldgen: use the supplied WorldGenLevel only. */
public final class ResourceVeinFeature extends Feature<ResourceVeinConfiguration> {
    public ResourceVeinFeature() { super(ResourceVeinConfiguration.CODEC); }
    public boolean place(FeaturePlaceContext<ResourceVeinConfiguration> context) {
        var level=context.level();var config=context.config();var settings=config.useWorldSettings() ? ResourceWorldgenConfig.settings():config.settings();
        int chunkX=context.origin().getX()>>4,chunkZ=context.origin().getZ()>>4,placed=0;
        var patches=ResourceVeins.inChunk(level.getSeed(),config.salt(),chunkX,chunkZ,settings);if(patches.isEmpty())return false;
        for(int x=chunkX*16;x<chunkX*16+16;x++)for(int z=chunkZ*16;z<chunkZ*16+16;z++) {
            int units=0;for(var patch:patches)units=Math.max(units,patch.stock(x,z));if(units==0)continue;
            int y=level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;var pos=new BlockPos(x,y,z);
            if(level.isOutsideBuildHeight(pos) || !level.getFluidState(pos.above()).isEmpty())continue;
            var old=level.getBlockState(pos);
            if(!(old.is(BlockTags.DIRT) || old.is(BlockTags.SAND) || old.is(BlockTags.BASE_STONE_OVERWORLD) || old.is(Blocks.GRAVEL) || old.is(Blocks.CLAY)))continue;
            if(level.setBlock(pos,config.state(),2)) {
                var be=level.getBlockEntity(pos);
                if(!(be instanceof ResourceDepositBlockEntity deposit))throw new IllegalStateException("Missing generated resource owner at "+pos);
                deposit.initializeGeneratedReserve(units);placed++;
            }
        }
        return placed>0;
    }
}
