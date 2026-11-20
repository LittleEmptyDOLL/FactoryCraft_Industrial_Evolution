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
      speed = substep(speed, p, throttle, brake);
      distance += speed;
    }
    return new Step(speed, distance);
  }

  private static double substep(double speed, Parameters p, boolean throttle, boolean brake) {
    if (brake) speed = Math.max(0, speed - p.brake / p.weight);
    else {
      speed = Math.max(0, speed - p.friction / p.weight);
      if (throttle) speed += p.traction / p.weight;
      speed *= Math.max(0, 1 - p.air / (p.weight / 1000));
    }
    speed = Math.min(speed, p.speedCap);
    return speed < 1e-10 ? 0 : speed;
  }

  /**
   * Allocated only on contact. The interrupted 60Hz step retains its remaining time; later steps
   * reapply the original force recurrence to the post-impact velocity.
   */
  public static final class Remainder {
    private final Parameters p;
    private final boolean throttle, brake;
    private final double[] speeds = new double[3], times = {1, 1, 1};
    private int cursor;

    public Remainder(double speed, Parameters p, boolean throttle, boolean brake) {
      this.p = p;
      this.throttle = throttle;
      this.brake = brake;
      for (int i = 0; i < 3; i++) speeds[i] = speed = substep(speed, p, throttle, brake);
    }

    public double consume(double distance) {
      if (!Double.isFinite(distance) || distance < 0 || distance > distance() + 1e-6)
        throw new IllegalArgumentException("Impact distance");
      while (cursor < 3) {
        if (times[cursor] <= 1e-12) {
          cursor++;
          continue;
        }
        double available = speeds[cursor] * times[cursor];
        if (distance <= available + 1e-9) {
          if (speeds[cursor] > 0)
            times[cursor] = Math.max(0, times[cursor] - distance / speeds[cursor]);
          return speeds[cursor];
        }
        distance -= available;
        times[cursor++] = 0;
      }
      cursor = 2;
      return speeds[2];
    }

    public void impact(double speed) {
      if (!Double.isFinite(speed) || speed < 0) throw new IllegalArgumentException("Impact speed");
      speeds[cursor] = speed;
      for (int i = cursor + 1; i < 3; i++) speeds[i] = speed = substep(speed, p, throttle, brake);
    }

    public double speed() {
      return speeds[2];
    }

    public double distance() {
      double result = 0;
      for (int i = cursor; i < 3; i++) result += speeds[i] * times[i];
      return result;
    }
  }

  /** Conservative bound without air/friction assistance, used before selecting a reserved path. */
  public static double stoppingDistance(double speed, double weight, double brake) {
    if (speed <= 0) return 0;
    if (!(brake > 0) || !(weight > 0)) return Double.POSITIVE_INFINITY;
    return speed * speed * weight / (2 * brake) + speed * 3;
  }

  private TrainMotion() {}
}
