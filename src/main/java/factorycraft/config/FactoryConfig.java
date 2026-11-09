package factorycraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FactoryConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CIRCUIT_PORTS,CIRCUIT_COMPONENT_WORK,CIRCUIT_DEVICE_WORK,CIRCUIT_SENSOR_WORK;
    public static final ModConfigSpec.IntValue LIFT_HEIGHT;
    public static final ModConfigSpec.IntValue GENERATOR_FE;
    public static final ModConfigSpec.IntValue POLE_RADIUS;
    public static final ModConfigSpec.IntValue DRILL_TICKS;
    public static final ModConfigSpec.IntValue POWER_GRAPH_WORK;
    public static final ModConfigSpec.IntValue FLUID_WORK;
    public static final ModConfigSpec.IntValue OIL_RICHNESS;
    public static final ModConfigSpec.IntValue ORE_RICHNESS;
    static {
        var b = new ModConfigSpec.Builder();
        LIFT_HEIGHT=b.comment("Maximum signed conveyor lift height selected with one item.").defineInRange("conveyorLiftMaxHeight",16,1,32);
        GENERATOR_FE = b.comment("FE produced per tick while burning coal; vanilla coal lasts 1600 ticks.")
                .defineInRange("generatorFePerTick", 80, 1, 10000);
        POLE_RADIUS = b.comment("Pole coverage and pole-to-pole connection radius in blocks.")
                .defineInRange("poleRadius", 6, 1, 16);
        DRILL_TICKS = b.comment("Powered ticks needed to extract one finite ore block beneath a drill.")
                .defineInRange("drillTicks", 60, 1, 12000);
        POWER_GRAPH_WORK = b.comment("Logical power-graph rebuild work units per level per tick, including empty spatial bucket probes.")
                .defineInRange("powerGraphWorkPerTick",4096,32,1000000);
        FLUID_WORK=b.comment("Maximum passive fluid edges and queued pumps processed per level tick; FIFO backlog never drops fluid.")
                .defineInRange("fluidWorkPerTick",16384,64,1048576);
        OIL_RICHNESS=b.comment("Initial mB in each newly initialized finite oil deposit. Existing reserves never reset.").defineInRange("oilDepositRichness",64000,1000,10000000);
        ORE_RICHNESS=b.comment("Units in each newly initialized solid resource tile; surviving tiles never refill.").defineInRange("oreTileRichness",1000,1,10000000);
        CIRCUIT_PORTS=b.comment("Maximum ports per individual red/green circuit component; startup setting.").defineInRange("circuitPortsPerNetwork",256,2,4096);
        CIRCUIT_COMPONENT_WORK=b.comment("Maximum bounded local circuit components rebuilt per tick.").defineInRange("circuitComponentWork",8,1,64);
        CIRCUIT_DEVICE_WORK=b.comment("Circuit devices evaluated per tick; queued changes are retained.").defineInRange("circuitDeviceWork",512,8,65536);
        CIRCUIT_SENSOR_WORK=b.comment("Inventory/fluid sensor and controller polls per level tick; FIFO backlog increases feedback latency.").defineInRange("circuitSensorWork",32,1,4096);
        SPEC = b.build();
    }
    private FactoryConfig() {}
}
