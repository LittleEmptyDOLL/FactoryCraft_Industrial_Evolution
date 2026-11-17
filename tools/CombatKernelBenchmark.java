import factorycraft.simulation.combat.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Bounded cadence/resistance microprofile. Not a Minecraft/TPS benchmark. */
public final class CombatKernelBenchmark {
  static long run() {
    long shots = 0;
    for (int type : new int[] {1, 6, 15, 30, 40, 60}) {
      var c = new ShotClock();
      for (int tick = 0; tick < 10000; tick++)
        for (int request = 0; request < 20; request++)
          if (c.fire(tick, type)) {
            shots++;
            double damage = Resistance.apply(5 + (tick % 10), 6, .3);
            if (!(damage > 0)) throw new AssertionError();
          }
    }
    return shots;
  }

  public static void main(String[] args) {
    for (int i = 0; i < 10; i++) run();
    var raw = ManagementFactory.getThreadMXBean();
    var bean = raw instanceof com.sun.management.ThreadMXBean b ? b : null;
    if (bean != null && bean.isThreadAllocatedMemorySupported())
      bean.setThreadAllocatedMemoryEnabled(true);
    long[] samples = new long[21];
    long allocation = 0, count = 0;
    for (int i = 0; i < samples.length; i++) {
      long before =
          bean == null ? 0 : bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
      long t = System.nanoTime();
      count = run();
      samples[i] = System.nanoTime() - t;
      allocation +=
          bean == null
              ? 0
              : bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
    }
    Arrays.sort(samples);
    System.out.printf(
        Locale.ROOT,
        "COMBAT_KERNEL: requests=1200000/round shots=%d rounds=21 median_ms=%.3f p95_ms=%.3f"
            + " p99_ms=%.3f max_ms=%.3f mean_alloc_bytes=%.0f%n",
        count,
        samples[10] / 1e6,
        samples[19] / 1e6,
        samples[20] / 1e6,
        samples[20] / 1e6,
        allocation / 21.0);
  }
}
