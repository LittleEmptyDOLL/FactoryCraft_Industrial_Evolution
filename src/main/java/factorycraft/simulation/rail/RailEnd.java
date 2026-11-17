package factorycraft.simulation.rail;

/**
 * Integer base cell; centerline lies on its center, 1/8 block above base Y. Heading is forward
 * travel.
 */
public record RailEnd(int x, int y, int z, RailHeading heading) {
  public RailVector point() {
    return new RailVector(x + .5, y + .125, z + .5);
  }

  public RailEnd reverse() {
    return new RailEnd(x, y, z, heading.reverse());
  }

  public boolean samePosition(RailEnd e) {
    return x == e.x && y == e.y && z == e.z;
  }

  public String nodeKey() {
    return x + ":" + y + ":" + z + ":" + (heading.index() % 8);
  }
}
