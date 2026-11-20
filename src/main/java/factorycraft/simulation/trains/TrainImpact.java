package factorycraft.simulation.trains;

/**
 * Bounded 3D impact adaptation. Energy is source mass * source-speed squared / 2. Public prototypes
 * specify 5 movement-energy/HP (locomotive/cargo), 6 (fluid). The closed-source engine's exact
 * collision solver is not reproduced here.
 */
public final class TrainImpact {
  public record Damage(double vehicle, double obstacle) {}

  public record LivingOutcome(boolean continues, double speed, double spent, double recoilHp) {}

  public static double energyPerHp(StockSpec car) {
    return car == StockSpec.FLUID ? 6 : 5;
  }

  public static double kinetic(double weight, double speed) {
    if (!Double.isFinite(weight + speed) || weight <= 0 || speed < 0)
      throw new IllegalArgumentException("Impact state");
    return .5 * weight * speed * speed;
  }

  /**
   * Minecraft adapter: charge actual health/absorption removed, with no overkill charge. Survivors
   * absorb the remaining motion. The exact Factorio solver is not public.
   */
  public static LivingOutcome living(
      double weight,
      double speed,
      StockSpec car,
      double removedHp,
      boolean accepted,
      boolean killed) {
    if (!Double.isFinite(removedHp) || removedHp < 0)
      throw new IllegalArgumentException("Impact health");
    double energy = kinetic(weight, speed);
    double spent = Math.min(energy, removedHp * energyPerHp(car));
    boolean through = accepted && killed && spent < energy;
    return new LivingOutcome(
        through,
        through ? Math.sqrt(2 * (energy - spent) / weight) : 0,
        through ? spent : energy,
        accepted ? (through ? spent : energy) / energyPerHp(car) : 0);
  }

  public static Damage damage(double weight, double speed, StockSpec contact, boolean entity) {
    double energy = kinetic(weight, speed);
    double hp = energy / energyPerHp(contact);
    // Other stock shares the finite budget. An immutable wall takes no block damage, and the
    // source receives the full structural impact. Living contacts use living() above.
    return entity ? new Damage(hp / 2, hp / 2) : new Damage(hp, 0);
  }

  private TrainImpact() {}
}
