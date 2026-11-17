package factorycraft.simulation.rail;

/** Independent immutable geometry; no Level access in this package. */
public record RailVector(double x, double y, double z) {
  public RailVector {
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
      throw new IllegalArgumentException("Nonfinite rail point");
  }

  public RailVector add(RailVector b) {
    return new RailVector(x + b.x, y + b.y, z + b.z);
  }

  public RailVector subtract(RailVector b) {
    return new RailVector(x - b.x, y - b.y, z - b.z);
  }

  public RailVector scale(double n) {
    return new RailVector(x * n, y * n, z * n);
  }

  public double dot(RailVector b) {
    return x * b.x + y * b.y + z * b.z;
  }

  public RailVector cross(RailVector b) {
    return new RailVector(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x);
  }

  public double length() {
    return Math.sqrt(dot(this));
  }

  public RailVector unit() {
    double d = length();
    if (d < 1e-10) throw new IllegalArgumentException("Zero tangent");
    return scale(1 / d);
  }

  public double distance(RailVector b) {
    return subtract(b).length();
  }
}
