package factorycraft.simulation.production;

/** Direct drill drops touch the near lane; parallel/antiparallel belts use their right lane. */
public final class DrillChute {
  public static int beltLane(int outputX, int outputZ, int beltX, int beltZ) {
    int side = outputX * -beltZ + outputZ * beltX;
    return side > 0 ? 0 : 1;
  }

  private DrillChute() {}
}
