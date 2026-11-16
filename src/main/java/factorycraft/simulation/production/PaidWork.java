package factorycraft.simulation.production;

/** Quarter-speed prototypes × basis-point modules, without recurring per-recipe rounding. */
public final class PaidWork {
  public static final int UNIT = 40000;

  public record Charge(int energy, int remainder) {}

  public static Charge charge(int base, int multiplier, int remainder) {
    if (base < 0 || multiplier < 2000 || multiplier > 100000 || remainder < 0 || remainder >= 10000)
      throw new IllegalArgumentException("Invalid paid energy");
    long n = (long) base * multiplier + remainder;
    return new Charge(Math.toIntExact(n / 10000), (int) (n % 10000));
  }

  public static int budget(int quarters, int multiplier) {
    if (quarters < 1 || quarters > 8 || multiplier < 2000 || multiplier > 50000)
      throw new IllegalArgumentException("Invalid paid work");
    return quarters * multiplier;
  }

  private PaidWork() {}
}
