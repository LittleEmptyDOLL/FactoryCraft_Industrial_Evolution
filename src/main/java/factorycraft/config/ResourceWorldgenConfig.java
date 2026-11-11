package factorycraft.config;

import factorycraft.simulation.resources.ResourceVeins;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Immutable restart-only settings, available during chunk decoration. */
public final class ResourceWorldgenConfig {
  public static final ModConfigSpec SPEC;
  public static final ModConfigSpec.IntValue MIN_RADIUS,
      MAX_RADIUS,
      MIN_STOCK,
      MAX_STOCK,
      SPACING,
      DEPTH,
      SMOOTHING,
      OIL_SPACING,
      OIL_POINTS_MIN,
      OIL_POINTS_MAX,
      OIL_RADIUS,
      OIL_MIN_YIELD,
      OIL_MAX_YIELD;
  public static final ModConfigSpec.DoubleValue NOISE;

  static {
    var b = new ModConfigSpec.Builder();
    MIN_RADIUS = b.worldRestart().defineInRange("minVeinRadius", 8, 4, 48);
    MAX_RADIUS = b.worldRestart().defineInRange("maxVeinRadius", 20, 4, 48);
    MIN_STOCK = b.worldRestart().defineInRange("minTileStock", 500, 1, 10000000);
    MAX_STOCK = b.worldRestart().defineInRange("maxTileStock", 5000, 1, 10000000);
    SPACING =
        b.comment("Candidate field spacing, in chunks. New chunks only.")
            .worldRestart()
            .defineInRange("veinSpacingChunks", 16, 2, 32);
    DEPTH =
        b.comment("Maximum half-ellipsoid depth; edges taper naturally.")
            .worldRestart()
            .defineInRange("veinDepth", 12, 2, 64);
    SMOOTHING =
        b.comment(
                "Terrain smoothing radius within the available generation region; never loads"
                    + " chunks.")
            .worldRestart()
            .defineInRange("terrainSmoothing", 8, 1, 8);
    NOISE = b.worldRestart().defineInRange("veinShapeNoise", 0.12, 0.0, 0.3);
    OIL_SPACING = b.worldRestart().defineInRange("oilFieldSpacingChunks", 16, 2, 64);
    OIL_POINTS_MIN = b.worldRestart().defineInRange("minOilPoints", 4, 1, 32);
    OIL_POINTS_MAX = b.worldRestart().defineInRange("maxOilPoints", 12, 1, 32);
    OIL_RADIUS =
        b.comment("Point grid spacing is six blocks; field radius controls density.")
            .worldRestart()
            .defineInRange("oilFieldRadius", 24, 12, 48);
    OIL_MIN_YIELD =
        b.comment("Initial yield in hundredths of a percent; 10000 means 100%.")
            .worldRestart()
            .defineInRange("minOilYield", 10000, 2000, 10000000);
    OIL_MAX_YIELD = b.worldRestart().defineInRange("maxOilYield", 30000, 2000, 10000000);
    SPEC = b.build();
  }

  public static ResourceVeins.Settings settings() {
    return new ResourceVeins.Settings(
        Math.min(MIN_RADIUS.get(), MAX_RADIUS.get()),
        Math.max(MIN_RADIUS.get(), MAX_RADIUS.get()),
        Math.min(MIN_STOCK.get(), MAX_STOCK.get()),
        Math.max(MIN_STOCK.get(), MAX_STOCK.get()),
        SPACING.get(),
        DEPTH.get(),
        NOISE.get());
  }

  private ResourceWorldgenConfig() {}
}
