package factorycraft.simulation.nuclear;

/** Absolute heat above 15 C, never a cached temperature copy. Specific capacity is quanta/C. */
public final class HeatStore {
  public final long specificHeat, maximum;
  private long units;

  public HeatStore(long specificHeat) {
    if (specificHeat < 1 || specificHeat > 1_000_000)
      throw new IllegalArgumentException("Heat capacity");
    this.specificHeat = specificHeat;
    maximum = 985 * specificHeat;
  }

  public long units() {
    return units;
  }

  public double temperature() {
    return 15 + units / (double) specificHeat;
  }

  public long add(long amount) {
    long n = Math.max(0, Math.min(amount, maximum - units));
    units += n;
    return n;
  }

  public long drainAbove(long amount, int minimum) {
    long n = Math.max(0, Math.min(amount, units - Math.max(0, minimum - 15) * specificHeat));
    units -= n;
    return n;
  }

  public void restore(long amount) {
    if (amount < 0 || amount > maximum) throw new IllegalArgumentException("Heat overflow");
    units = amount;
  }

  public static long transfer(HeatStore a, HeatStore b, long limit) {
    if (a == b || limit <= 0) return 0;
    long delta = a.units * b.specificHeat - b.units * a.specificHeat;
    if (delta < 0) return -transfer(b, a, limit);
    long n = Math.min(limit, delta / (a.specificHeat + b.specificHeat));
    n = Math.min(n, Math.min(a.units, b.maximum - b.units));
    a.units -= n;
    b.units += n;
    return n;
  }
}
