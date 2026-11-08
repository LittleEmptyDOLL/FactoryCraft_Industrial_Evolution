package factorycraft.simulation.modules;

/** Integer basis points; energy rounds upward per active tick, never free work at empty FE. */
public record ModuleEffects(int speed, int energy, int productivity) {
  public static final int UNIT = 10000;
  public static final ModuleEffects NONE = new ModuleEffects(UNIT, UNIT, 0);

  public ModuleEffects {
    speed = Math.clamp(speed, 2000, 50000);
    energy = Math.clamp(energy, 2000, 100000);
    productivity = Math.clamp(productivity, 0, 10000);
  }

  public record Step(int work, int remainder, int energy) {}

  public Step plan(int baseEnergy, int baseStep, int remaining, int fraction) {
    if (baseEnergy < 0 || baseStep < 1 || remaining < 1 || fraction < 0 || fraction >= UNIT)
      throw new IllegalArgumentException("Invalid work");
    int ticks = Math.min(baseStep, remaining), raw = fraction + speed * ticks;
    int work = Math.min(remaining, raw / UNIT);
    int cost =
        (int) Math.min(Integer.MAX_VALUE, ((long) baseEnergy * ticks * energy + UNIT - 1) / UNIT);
    return new Step(work, work == remaining ? 0 : raw % UNIT, cost);
  }

  /** Factorio 2.0 normal-quality transmission: 1.5 / sqrt(active beacons). */
  public static ModuleEffects combine(
      int speedBonus,
      int energyBonus,
      int productivity,
      int beaconSpeed,
      int beaconEnergy,
      int beacons) {
    double factor = beacons == 0 ? 0 : 1.5 / Math.sqrt(beacons);
    return new ModuleEffects(
        UNIT + speedBonus + (int) Math.floor(beaconSpeed * factor),
        UNIT + energyBonus + (int) Math.floor(beaconEnergy * factor),
        productivity);
  }

  public record Earned(int credit, int remainder) {}

  public static Earned accrue(int rate, int work, int duration, int remainder) {
    if (rate < 0
        || rate > UNIT
        || work < 0
        || duration < 1
        || remainder < 0
        || remainder >= duration) throw new IllegalArgumentException("Invalid productive work");
    long total = (long) rate * work + remainder;
    return new Earned((int) (total / duration), (int) (total % duration));
  }

  public static int batches(int credit, int rate) {
    return 1 + (credit + rate) / UNIT;
  }

  public static int credit(int credit, int rate) {
    return (credit + rate) % UNIT;
  }
}
