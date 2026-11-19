package factorycraft.simulation.rail;

/** Factorio 2.x's sixteen connection families, including actual 1:2 half diagonals. */
public record RailHeading(int index) {
  private static final int[][] DIRECTIONS = {
    {0, -1}, {1, -2}, {1, -1}, {2, -1}, {1, 0}, {2, 1}, {1, 1}, {1, 2}, {0, 1}, {-1, 2}, {-1, 1},
    {-2, 1}, {-1, 0}, {-2, -1}, {-1, -1}, {-1, -2}
  };

  public RailHeading {
    index = Math.floorMod(index, 16);
  }

  public RailVector vector() {
    var d = DIRECTIONS[index];
    return new RailVector(d[0], 0, d[1]).unit();
  }

  public RailHeading reverse() {
    return new RailHeading(index + 8);
  }

  public static RailHeading fromYaw(double yaw) {
    double angle = Math.toRadians(yaw);
    return nearest(-Math.sin(angle), Math.cos(angle));
  }

  public static RailHeading nearest(double x, double z) {
    double best = -Double.MAX_VALUE;
    int chosen = 0;
    for (int i = 0; i < 16; i++) {
      var v = new RailHeading(i).vector();
      double score = x * v.x() + z * v.z();
      if (score > best) {
        best = score;
        chosen = i;
      }
    }
    return new RailHeading(chosen);
  }
}
