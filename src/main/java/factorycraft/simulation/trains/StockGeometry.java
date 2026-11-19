package factorycraft.simulation.trains;

/**
 * Original mesh audit: body X±1.09, Z±3.5; wheel bottom .02; highest roof3.42. 3.5cm side /3cm roof
 * margin. Coupler length stays exactly seven to preserve source spacing.
 */
public final class StockGeometry {
  public static final double HALF_WIDTH = 1.125, HALF_LENGTH = 3.5, BOTTOM = .005, TOP = 3.45;

  // Bounded section-discovery padding for a full body at the allowed1:4 grade.
  // Minecraft's original2m horizontal margin misses couplers across section boundaries.
  public static final double DISCOVERY_HORIZONTAL = 4.5, DISCOVERY_ABOVE = 4.5, DISCOVERY_BELOW = 1;

  public static double top(StockSpec kind) {
    return switch (kind) {
      case LOCOMOTIVE -> TOP;
      case CARGO -> 2.76;
      case FLUID -> 3.20;
    };
  }

  private StockGeometry() {}
}
