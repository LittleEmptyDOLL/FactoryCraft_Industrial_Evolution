package factorycraft.simulation.multiblock;

/** Pure reach predicate shared with native preview tests; MISS is not a block target. */
public final class PlacementAim {
  public static boolean valid(
      boolean blockHit, boolean air, boolean loaded, double squared, double reach) {
    return blockHit
        && !air
        && loaded
        && Double.isFinite(squared)
        && Double.isFinite(reach)
        && squared >= 0
        && reach >= 0
        && squared <= reach * reach + 1e-7;
  }

  private PlacementAim() {}
}
