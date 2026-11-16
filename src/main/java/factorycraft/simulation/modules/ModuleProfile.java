package factorycraft.simulation.modules;

/** Normal-quality base-game modules. Registry identity selects this immutable profile. */
public record ModuleProfile(Family family, int tier, int speed, int energy, int productivity) {
  public enum Family {
    SPEED,
    EFFICIENCY,
    PRODUCTIVITY
  }

  public static ModuleProfile of(Family family, int tier) {
    if (tier < 1 || tier > 3) throw new IllegalArgumentException("Module tier must be 1–3");
    return switch (family) {
      case SPEED ->
          new ModuleProfile(
              family,
              tier,
              new int[] {2000, 3000, 5000}[tier - 1],
              new int[] {5000, 6000, 7000}[tier - 1],
              0);
      case EFFICIENCY ->
          new ModuleProfile(family, tier, 0, new int[] {-3000, -4000, -5000}[tier - 1], 0);
      case PRODUCTIVITY ->
          new ModuleProfile(
              family,
              tier,
              new int[] {-500, -1000, -1500}[tier - 1],
              new int[] {4000, 6000, 8000}[tier - 1],
              new int[] {400, 600, 1000}[tier - 1]);
    };
  }
}
