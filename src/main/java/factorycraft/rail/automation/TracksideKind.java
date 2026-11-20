package factorycraft.rail.automation;

import factorycraft.simulation.rail.RailEdgePoints;

/** Normal-quality Wube2.1.24 devices, with original FactoryCraft block geometry. */
public enum TracksideKind {
  SIGNAL("rail_signal", RailEdgePoints.Kind.SIGNAL, 1, 2, 100),
  CHAIN("rail_chain_signal", RailEdgePoints.Kind.CHAIN, 1, 2, 100),
  STOP("train_stop", RailEdgePoints.Kind.STOP, 2, 3, 250);
  public final String id;
  public final RailEdgePoints.Kind point;
  public final int width, height, health;

  TracksideKind(String id, RailEdgePoints.Kind point, int width, int height, int health) {
    this.id = id;
    this.point = point;
    this.width = width;
    this.height = height;
    this.health = health;
  }

  public int parts() {
    return width * width * height;
  }
}
