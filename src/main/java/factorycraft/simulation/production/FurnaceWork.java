package factorycraft.simulation.production;

/** Furnace power is per active MC tick; work is recipe-independent in 1/10000 ticks. */
public final class FurnaceWork {
  public static final int UNIT = 10000, ELECTRIC_FE = 24;

  public record Charge(int energy, int remainder) {}

  public static Charge charge(int energyMultiplier, int remainder) {
    if (energyMultiplier < 2000 || energyMultiplier > 100000 || remainder < 0 || remainder >= UNIT)
      throw new IllegalArgumentException("Invalid furnace power state");
    int total = ELECTRIC_FE * energyMultiplier + remainder;
    return new Charge(total / UNIT, total % UNIT);
  }

  public static int budget(int baseSpeed, int speedMultiplier) {
    if (baseSpeed < 1 || baseSpeed > 2 || speedMultiplier < 2000 || speedMultiplier > 50000)
      throw new IllegalArgumentException("Invalid furnace speed");
    return baseSpeed * speedMultiplier;
  }

  private FurnaceWork() {}
}
