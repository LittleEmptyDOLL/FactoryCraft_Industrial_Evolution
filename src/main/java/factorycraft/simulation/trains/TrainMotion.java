package factorycraft.simulation.trains;

/** Source-speed units are tiles/60-Hz tick. Three exact ordered substeps per Minecraft tick. */
public final class TrainMotion {
  public record Parameters(
      double weight, double friction, double brake, double air, double traction, double speedCap) {
    public Parameters {
      if (!(weight > 0)
          || friction < 0
          || brake < 0
          || air < 0
          || traction < 0
          || speedCap < 0
          || !Double.isFinite(weight + friction + brake + air + traction + speedCap))
        throw new IllegalArgumentException("Train physics");
    }
  }

  public record Step(double speed, double distance) {}

  /** Positive speed along the requested travel direction; direction changes require a full stop. */
  public static Step tick(double speed, Parameters p, boolean throttle, boolean brake) {
    if (!Double.isFinite(speed) || speed < 0) throw new IllegalArgumentException("Train speed");
    double distance = 0;
    for (int i = 0; i < 3; i++) {
      if (brake) speed = Math.max(0, speed - p.brake / p.weight);
      else {
        speed = Math.max(0, speed - p.friction / p.weight);
        if (throttle) speed += p.traction / p.weight;
        speed *= Math.max(0, 1 - p.air / (p.weight / 1000));
      }
      speed = Math.min(speed, p.speedCap);
      if (speed < 1e-10) speed = 0;
      distance += speed;
    }
    return new Step(speed, distance);
  }

  /** Conservative bound without air/friction assistance, used before selecting a reserved path. */
  public static double stoppingDistance(double speed, double weight, double brake) {
    if (speed <= 0) return 0;
    if (!(brake > 0) || !(weight > 0)) return Double.POSITIVE_INFINITY;
    return speed * speed * weight / (2 * brake) + speed * 3;
  }

  private TrainMotion() {}
}
