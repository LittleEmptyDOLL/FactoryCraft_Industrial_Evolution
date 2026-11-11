package factorycraft.simulation.fluids;

/**
 * Explicit Minecraft adaptation: recipe quantities retain one Factorio unit per mB; physical
 * buffers are adapted.
 */
public final class FluidUnits {
  public static final int MB_PER_UNIT = 1,
      FACTORIO_TPS = 60,
      MINECRAFT_TPS = 20,
      MAX_HORIZONTAL_SPAN = 320;
  public static final int PUMP_PER_TICK = 1200 * MB_PER_UNIT / MINECRAFT_TPS;

  public static int boundaryRate(int capacity) {
    return (int) Math.min(Integer.MAX_VALUE, (long) capacity * FACTORIO_TPS / MINECRAFT_TPS);
  }

  private FluidUnits() {}
}
