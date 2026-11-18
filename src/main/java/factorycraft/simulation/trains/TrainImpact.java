package factorycraft.simulation.trains;

/**
 * Bounded 3D impact adaptation. Energy is source mass * source-speed squared / 2. Public prototypes
 * specify 5 movement-energy/HP (locomotive/cargo), 6 (fluid). The closed-source engine's exact
 * collision solver is not reproduced here.
 */
public final class TrainImpact {
  public record Damage(double vehicle, double obstacle) {}

  public static Damage damage(double weight, double speed, StockSpec contact, boolean entity) {
    if (!Double.isFinite(weight + speed) || weight <= 0 || speed < 0)
      throw new IllegalArgumentException("Impact state");
    double energy = .5 * weight * speed * speed;
    double hp = energy / (contact == StockSpec.FLUID ? 6 : 5);
    // Entity collisions share the finite kinetic budget; an immutable world wall absorbs its
    // half without block destruction. No invented fuel, gravity or per-tick collision credits.
    return entity ? new Damage(hp / 2, hp / 2) : new Damage(hp, 0);
  }

  private TrainImpact() {}
}
