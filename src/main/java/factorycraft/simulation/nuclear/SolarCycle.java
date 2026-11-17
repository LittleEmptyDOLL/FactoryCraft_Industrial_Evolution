package factorycraft.simulation.nuclear;

/** Factorio's 2:5:2 dawn/full/dusk proportions fitted into Minecraft's actual sun-up half-day. */
public final class SolarCycle {
  private int remainder;
  private long last = Long.MIN_VALUE;

  public int remainder() {
    return remainder;
  }

  public long lastTick() {
    return last;
  }

  public int generate(long gameTick, long dayTick, boolean sunlight) {
    if (last == gameTick) return 0;
    last = gameTick;
    long t = Math.floorMod(dayTick, 24000);
    if (!sunlight || t >= 12000) return 0;
    long light = Math.min(24000, Math.min(t * 9, (12000 - t) * 9));
    long raw = remainder + 120 * light;
    remainder = (int) (raw % 24000);
    return (int) (raw / 24000);
  }

  public void restore(int fraction, long tick) {
    if (fraction < 0 || fraction >= 24000) throw new IllegalArgumentException("Solar remainder");
    remainder = fraction;
    last = tick;
  }
}
