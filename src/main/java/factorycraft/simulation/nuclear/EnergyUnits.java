package factorycraft.simulation.nuclear;

/**
 * 25 joules per integral quantum. All native FE, steam enthalpies and heat capacities are exact.
 */
public final class EnergyUnits {
  public static final int JOULES = 25, FE = 15;
  public static final int COLD_STEAM = 120, HOT_STEAM = 388;
  public static final int REACTOR_PER_TICK = 80_000, EXCHANGER_PER_TICK = 20_000;
  public static final int TURBINE_PER_TICK = 11_640;

  private EnergyUnits() {}
}
