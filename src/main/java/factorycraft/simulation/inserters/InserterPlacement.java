package factorycraft.simulation.inserters;

/** Physical far-lane rule; reflection only resolves the ambiguous parallel/antiparallel case. */
public final class InserterPlacement {
  public static int lane(int armX, int armZ, int beltX, int beltZ, boolean reflected) {
    int side = armX * -beltZ + armZ * beltX;
    return side == 0 ? (reflected ? 0 : 1) : side > 0 ? 1 : 0;
  }

  private InserterPlacement() {}
}
