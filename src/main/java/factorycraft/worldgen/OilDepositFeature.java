package factorycraft.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

/** Region writes register BE-bearing reservoirs; vanilla OreFeature's direct section writes do not. */
public final class OilDepositFeature extends Feature<OreConfiguration> {
    public OilDepositFeature() { super(OreConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        var level=context.level();var origin=context.origin();var random=context.random();int placed=0;
        int size=Math.min(64,context.config().size);
        for(int i=0;i<size;i++) {
            BlockPos pos=origin.offset(random.nextInt(5)-2,random.nextInt(5)-2,random.nextInt(5)-2);
            if(level.isOutsideBuildHeight(pos))continue;
            if(context.config().discardChanceOnAirExposure>0 && random.nextFloat()<context.config().discardChanceOnAirExposure) {
                boolean exposed=false;for(var direction:net.minecraft.core.Direction.values())if(level.getBlockState(pos.relative(direction)).isAir()) { exposed=true;break; }
                if(exposed)continue;
            }
            var existing=level.getBlockState(pos);
            for(var target:context.config().targetStates)if(target.target.test(existing,random)) {
                if(level.setBlock(pos,target.state,2))placed++;break;
            }
        }
        return placed>0;
    }
}
