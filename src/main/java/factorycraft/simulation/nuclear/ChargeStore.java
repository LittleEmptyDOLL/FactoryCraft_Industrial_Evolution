package factorycraft.simulation.nuclear;

/**
 * One owned exact charge balance; a shared tick/direction lease covers every capability and grid.
 */
public final class ChargeStore {
  public static final long CAPACITY = 200_000;
  public static final int RATE = 600;
  private long units, tick = Long.MIN_VALUE;
  private int used, direction;

  private void advance(long now) {
    if (tick != now) {
      tick = now;
      used = direction = 0;
    }
  }

  public long units() {
    return units;
  }

  public long leaseTick() {
    return tick;
  }

  public int used() {
    return used;
  }

  public int direction() {
    return direction;
  }

  public long fill(long now, long offered, boolean simulate) {
    if (!simulate) advance(now);
    int mode = tick == now ? direction : 0, spent = tick == now ? used : 0;
    long n =
        mode < 0 ? 0 : Math.max(0, Math.min(Math.min(offered, CAPACITY - units), RATE - spent));
    if (n > 0 && !simulate) {
      units += n;
      used += (int) n;
      direction = 1;
    }
    return n;
  }

  public long drain(long now, long requested, boolean simulate) {
    if (!simulate) advance(now);
    int mode = tick == now ? direction : 0, spent = tick == now ? used : 0;
    long n = mode > 0 ? 0 : Math.max(0, Math.min(Math.min(requested, units), RATE - spent));
    if (n > 0 && !simulate) {
      units -= n;
      used += (int) n;
      direction = -1;
    }
    return n;
  }

  /** Only compensates an unaccepted part of the immediately preceding internal transfer. */
  public void refund(long amount) {
    if (amount < 0 || direction != -1 || amount > used || units + amount > CAPACITY)
      throw new IllegalArgumentException("Invalid charge compensation");
    units += amount;
    used -= (int) amount;
    if (used == 0) direction = 0;
  }

  public void restore(long charge, long leaseTick, int spent, int mode) {
    if (charge < 0 || charge > CAPACITY || spent < 0 || spent > RATE || mode < -1 || mode > 1)
      throw new IllegalArgumentException("Invalid accumulator state");
    units = charge;
    tick = leaseTick;
    used = spent;
    direction = mode;
  }
}
