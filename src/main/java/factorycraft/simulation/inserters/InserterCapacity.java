package factorycraft.simulation.inserters;

/** Exact cumulative 2.0 bonuses; the last normal-arm bonus comes from Space Age belt capacity 2. */
public final class InserterCapacity {
  private static final int[] BULK_BONUS = {0, 1, 2, 3, 4, 6, 8, 10};

  public static int maximum(ArmCycle.Spec spec, int research, int beltResearch) {
    int level = Math.clamp(research, 0, 7);
    return spec.bulk()
        ? spec.capacity() + BULK_BONUS[level]
        : 1 + (level >= 2 ? 1 : 0) + (level >= 7 ? 1 : 0) + (beltResearch >= 2 ? 1 : 0);
  }

  public static int handLimit(
      ArmCycle.Spec spec,
      int research,
      int beltResearch,
      int manual,
      int itemMax,
      boolean beltTarget) {
    int n = Math.min(Math.max(1, itemMax), maximum(spec, research, beltResearch));
    if (manual > 0) n = Math.min(n, manual);
    int height = Math.clamp(2 + beltResearch, 2, 4);
    return spec.stack() && beltTarget && n >= height ? n - n % height : n;
  }

  private InserterCapacity() {}
}
