package factorycraft.simulation.combat;

/**
 * Factorio flat resistance followed by percentage. Values must be expressed in the same HP unit.
 */
public final class Resistance {
  public static double apply(double damage, double decrease, double fraction) {
    if (damage < 0
        || decrease < 0
        || fraction < 0
        || fraction > 1
        || !Double.isFinite(damage)
        || !Double.isFinite(decrease)
        || !Double.isFinite(fraction)) throw new IllegalArgumentException();
    if (damage == 0) return 0;
    double after =
        damage > decrease + 1
            ? damage - decrease
            : damage > 1 ? 1 / (decrease - damage + 2) : 1 / (decrease + 1);
    return after * (1 - fraction);
  }

  private Resistance() {}
}
