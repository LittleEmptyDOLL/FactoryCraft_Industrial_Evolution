package factorycraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FactoryConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue GENERATOR_FE;
    public static final ModConfigSpec.IntValue POLE_RADIUS;
    public static final ModConfigSpec.IntValue DRILL_TICKS;
    public static final ModConfigSpec.IntValue POWER_GRAPH_WORK;
    static {
        var b = new ModConfigSpec.Builder();
        GENERATOR_FE = b.comment("FE produced per tick while burning coal; vanilla coal lasts 1600 ticks.")
                .defineInRange("generatorFePerTick", 80, 1, 10000);
        POLE_RADIUS = b.comment("Pole coverage and pole-to-pole connection radius in blocks.")
                .defineInRange("poleRadius", 6, 1, 16);
        DRILL_TICKS = b.comment("Powered ticks needed to extract one finite ore block beneath a drill.")
                .defineInRange("drillTicks", 60, 1, 12000);
        POWER_GRAPH_WORK = b.comment("Logical power-graph rebuild work units per level per tick, including empty spatial bucket probes.")
                .defineInRange("powerGraphWorkPerTick",4096,32,1000000);
        SPEC = b.build();
    }
    private FactoryConfig() {}
}
