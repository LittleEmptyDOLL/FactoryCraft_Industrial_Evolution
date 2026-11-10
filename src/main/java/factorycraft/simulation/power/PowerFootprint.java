package factorycraft.simulation.power;

/** Inclusive occupied X/Z cells. Doubled coordinates preserve half-block centers exactly. */
public record PowerFootprint(int minX, int maxX, int y, int minZ, int maxZ) {
  public PowerFootprint {
    if (maxX < minX || maxZ < minZ) throw new IllegalArgumentException("Invalid footprint");
  }

  public static PowerFootprint cell(PowerPoint p) {
    return new PowerFootprint(p.x(), p.x(), p.y(), p.z(), p.z());
  }

  public int centerX2() {
    return minX + maxX + 1;
  }

  public int centerZ2() {
    return minZ + maxZ + 1;
  }

  public PowerPoint indexPoint() {
    return new PowerPoint(Math.floorDiv(centerX2(), 2), y, Math.floorDiv(centerZ2(), 2));
  }

  public boolean suppliedBy(PowerFootprint pole, PoleSpec spec, int vertical) {
    return Math.abs((long) y - pole.y) <= vertical
        && 2L * (maxX + 1) > pole.centerX2() - spec.supply
        && 2L * minX < pole.centerX2() + spec.supply
        && 2L * (maxZ + 1) > pole.centerZ2() - spec.supply
        && 2L * minZ < pole.centerZ2() + spec.supply;
  }

  public double distanceSquared(PowerFootprint b) {
    double dx = (centerX2() - (long) b.centerX2()) * .5,
        dy = y - (long) b.y,
        dz = (centerZ2() - (long) b.centerZ2()) * .5;
    return dx * dx + dy * dy + dz * dz;
  }

  public boolean wireReach(PowerFootprint b, PoleSpec aSpec, PoleSpec bSpec) {
    double max = Math.min(aSpec.wire, bSpec.wire);
    return distanceSquared(b) <= max * max + 1e-9;
  }
}
