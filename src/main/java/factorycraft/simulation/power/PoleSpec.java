package factorycraft.simulation.power;

/** Normal quality Factorio 2.0.77 metrics; heights are original Minecraft adaptations. */
public enum PoleSpec {
  SMALL("electric_pole", 1, 2, 5, 7.5),
  MEDIUM("medium_electric_pole", 1, 3, 7, 9),
  BIG("big_electric_pole", 2, 5, 4, 32),
  SUBSTATION("substation", 2, 4, 18, 18);
  public final String id;
  public final int width, height, supply;
  public final double wire;

  PoleSpec(String id, int width, int height, int supply, double wire) {
    this.id = id;
    this.width = width;
    this.height = height;
    this.supply = supply;
    this.wire = wire;
  }

  public static PoleSpec byId(String id) {
    for (var p : values()) if (p.id.equals(id)) return p;
    return null;
  }
}
