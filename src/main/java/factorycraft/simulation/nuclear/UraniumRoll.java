package factorycraft.simulation.nuclear;

/** Persisted categorical roll: seven U-235 outcomes per 1000 equally likely buckets. */
public final class UraniumRoll {
  private long seed, serial;
  private int pending = -1;

  public UraniumRoll(long seed) {
    this.seed = seed;
  }

  public static int outcome(long seed, long serial) {
    long z = seed + 0x9e3779b97f4a7c15L * (serial + 1);
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    z ^= z >>> 31;
    return Long.remainderUnsigned(z, 1000) < 7 ? 0 : 1;
  }

  public int beginPaidWork() {
    if (pending < 0) pending = outcome(seed, serial++);
    return pending;
  }

  public int pending() {
    return pending;
  }

  public long seed() {
    return seed;
  }

  public long serial() {
    return serial;
  }

  public void completed() {
    if (pending < 0) throw new IllegalStateException("No paid roll");
    pending = -1;
  }

  public void restore(long s, long n, int p) {
    if (n < 0 || p < -1 || p > 1) throw new IllegalArgumentException("Uranium RNG state");
    seed = s;
    serial = n;
    pending = p;
  }
}
