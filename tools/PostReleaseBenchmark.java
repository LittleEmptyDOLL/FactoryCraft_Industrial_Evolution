import factorycraft.simulation.EnergyBuffer;
import factorycraft.simulation.combat.LaserStandby;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Opt-in bounded kernel profile, not a Minecraft TPS or mass-combat measurement. */
public final class PostReleaseBenchmark {
  static final int TICKS = 100_000, ROUNDS = 21;
  static long run() {
    var energy = new EnergyBuffer(2136, () -> {});
    var bill = new LaserStandby();
    long supplied = 0;
    for (int t = 0; t < TICKS; t++) {
      supplied += energy.receiveEnergy(16, false);
      if (!bill.begin(energy)) throw new AssertionError("supplied idle cannot stop");
      bill.finish(energy);
    }
    long spent = supplied - energy.getEnergyStored();
    if (spent != (long) TICKS * 16 / 5 || bill.remainder() != 0 || bill.debt() != 0)
      throw new AssertionError("exact idle conservation");
    return spent;
  }
  public static void main(String[] args) {
    for (int i = 0; i < 10; i++) run();
    var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long thread = Thread.currentThread().threadId();
    long[] times = new long[ROUNDS];
    long before = bean.getThreadAllocatedBytes(thread), checksum = 0;
    for (int i = 0; i < ROUNDS; i++) {
      long start = System.nanoTime();
      checksum += run();
      times[i] = System.nanoTime() - start;
    }
    long allocated = bean.getThreadAllocatedBytes(thread) - before;
    Arrays.sort(times);
    System.out.printf(java.util.Locale.ROOT,
        "LASER_STANDBY: rounds=%d ticks_per_round=%d p50_ns_tick=%.3f p95_ns_tick=%.3f max_ns_tick=%.3f allocated_bytes=%d exact_spent_FE=%d%n",
        ROUNDS, TICKS, times[ROUNDS / 2] / (double) TICKS,
        times[19] / (double) TICKS, times[20] / (double) TICKS, allocated, checksum);
  }
}
