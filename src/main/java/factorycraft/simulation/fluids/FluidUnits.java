package factorycraft.simulation.fluids;

/**
 * All storage, recipes, circuits and NeoForge transactions use native mB. Convert source units
 * once.
 */
public final class FluidUnits {
  public static final int MB_PER_UNIT = 10,
      FACTORIO_TPS = 60,
      MINECRAFT_TPS = 20,
      MAX_HORIZONTAL_SPAN = 320;
  public static final int PIPE_CAPACITY = mb(100),
      TANK_CAPACITY = mb(25000),
      PUMP_CAPACITY = mb(400),
      PUMPJACK_CAPACITY = mb(1000),
      MACHINE_CAPACITY = mb(4000),
      PUMP_PER_TICK = mb(1200) / MINECRAFT_TPS;

  public static int mb(int units) {
    return Math.multiplyExact(units, MB_PER_UNIT);
  }

  public static int boundaryRate(int capacity) {
    return (int) Math.min(Integer.MAX_VALUE, (long) capacity * FACTORIO_TPS / MINECRAFT_TPS);
  }

  private FluidUnits() {}
}
