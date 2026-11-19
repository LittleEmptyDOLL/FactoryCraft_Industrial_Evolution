package factorycraft.simulation.trains;

/** Pinned Wube2.1.24 normal-quality stock. Inventories do not add fictitious vehicle mass. */
public enum StockSpec {
  LOCOMOTIVE("locomotive", 2000, 1000, 10, .0075, 3),
  CARGO("cargo_wagon", 1000, 600, 3, .01, 40),
  FLUID("fluid_wagon", 1000, 600, 3, .01, 0);
  public static final double SPACING = 7,
      BOGIE_OFFSET = 2,
      HALF_LENGTH = 3.5,
      HALF_WIDTH = StockGeometry.HALF_WIDTH,
      HEIGHT = StockGeometry.TOP;
  public static final int FLUID_CAPACITY = 500_000;
  public final String id;
  public final int weight, health, slots;
  public final double brake, air;

  StockSpec(String id, int weight, int health, double brake, double air, int slots) {
    this.id = id;
    this.weight = weight;
    this.health = health;
    this.brake = brake;
    this.air = air;
    this.slots = slots;
  }
}
