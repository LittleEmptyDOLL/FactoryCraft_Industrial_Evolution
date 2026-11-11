package factorycraft.simulation.petroleum;

/** 1/1500 percentage-point units: five units per successful cycle, exactly 1% per 300 cycles. */
public final class OilYield {
  public static final long SCALE = 1500, MIN = 20 * SCALE, MAX = 100000 * SCALE;
  private long initial, current, cycles, generated;
  private int residue;

  public OilYield(int hundredths) {
    restore(hundredths * 15L, hundredths * 15L, 0, 0, 0);
  }

  public long initial() {
    return initial;
  }

  public long current() {
    return current;
  }

  public long cycles() {
    return cycles;
  }

  public long generated() {
    return generated;
  }

  public int residue() {
    return residue;
  }

  public long floor() {
    return Math.max(MIN, initial / 5);
  }

  public int nextAmount() {
    return (int) ((Math.min(current, 10000 * SCALE) + residue) / SCALE);
  }

  /** Called only after output reservation and energy payment, before notifying observers. */
  public int commit() {
    long numerator = Math.min(current, 10000 * SCALE) + residue;
    int amount = (int) (numerator / SCALE);
    residue = (int) (numerator % SCALE);
    current = Math.max(floor(), current - 5);
    cycles = Math.addExact(cycles, 1);
    generated = Math.addExact(generated, amount);
    return amount;
  }

  public void restore(long initial, long current, long cycles, int residue, long generated) {
    if (initial < MIN
        || initial > MAX
        || current < Math.max(MIN, initial / 5)
        || current > initial
        || cycles < 0
        || residue < 0
        || residue >= SCALE
        || generated < 0) throw new IllegalArgumentException("Invalid percentage oil state");
    this.initial = initial;
    this.current = current;
    this.cycles = cycles;
    this.residue = residue;
    this.generated = generated;
  }
}
