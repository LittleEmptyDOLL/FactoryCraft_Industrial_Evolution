package factorycraft.simulation.combat;

/** Source milliseconds: 3000 phase units per Minecraft tick. Never catch up an idle backlog. */
public final class ShotClock {
  private long due = Long.MIN_VALUE, last = Long.MIN_VALUE;

  public boolean ready(long tick) {
    return due == Long.MIN_VALUE || Math.multiplyExact(tick, 3000) >= due;
  }

  public boolean fire(long tick, int sourceTicks) {
    return firePeriod(tick, Math.multiplyExact(sourceTicks, 1000));
  }

  public boolean firePeriod(long tick, int period) {
    if (period < 1 || !ready(tick)) return false;
    long now = Math.multiplyExact(tick, 3000);
    due = (due != Long.MIN_VALUE && now - due < 3000 ? due : now) + period;
    last = tick;
    return true;
  }

  public long due() {
    return due;
  }

  public long last() {
    return last;
  }

  public void restore(long due, long last) {
    this.due = due;
    this.last = last;
  }
}
