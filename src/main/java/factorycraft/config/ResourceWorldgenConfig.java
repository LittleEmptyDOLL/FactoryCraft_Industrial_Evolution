package factorycraft.config;

import factorycraft.simulation.resources.ResourceVeins;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Common startup configuration is available to worldgen workers and the create-world pipeline. */
public final class ResourceWorldgenConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MIN_RADIUS,MAX_RADIUS,MIN_STOCK,MAX_STOCK,SPACING;
    static {
        var b=new ModConfigSpec.Builder();
        MIN_RADIUS=b.comment("Minimum nominal resource field radius, in blocks. Applies only in newly generated chunks.").worldRestart().defineInRange("minVeinRadius",8,4,48);
        MAX_RADIUS=b.comment("Maximum nominal radius; ragged edges remain within this radius. Inverted ranges are normalized.").worldRestart().defineInRange("maxVeinRadius",20,4,48);
        MIN_STOCK=b.comment("Minimum finite units in a generated resource tile; field edges are poorer than centers.").worldRestart().defineInRange("minTileStock",500,1,10000000);
        MAX_STOCK=b.comment("Maximum finite units per generated tile. Saved reserves never reset.").worldRestart().defineInRange("maxTileStock",5000,1,10000000);
        SPACING=b.comment("One candidate field per resource per square grid cell of this many chunks. Larger = rarer fields.").worldRestart().defineInRange("veinSpacingChunks",6,2,32);
        SPEC=b.build();
    }
    public static ResourceVeins.Settings settings() {
        return new ResourceVeins.Settings(Math.min(MIN_RADIUS.get(),MAX_RADIUS.get()),Math.max(MIN_RADIUS.get(),MAX_RADIUS.get()),
                Math.min(MIN_STOCK.get(),MAX_STOCK.get()),Math.max(MIN_STOCK.get(),MAX_STOCK.get()),SPACING.get());
    }
    private ResourceWorldgenConfig() {}
}
