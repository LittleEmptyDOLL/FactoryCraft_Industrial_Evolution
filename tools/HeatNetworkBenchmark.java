import factorycraft.simulation.nuclear.HeatStore;
import java.util.*;

/** Bounded heat-kernel CPU/ownership sample, not a Minecraft TPS claim. */
public final class HeatNetworkBenchmark {
  static void sample(int count, boolean report) {
    HeatStore[] nodes = new HeatStore[count];
    for (int i = 0; i < count; i++) {
      nodes[i] = new HeatStore(40000);
      nodes[i].add(i % 17 == 0 ? 39400000 : 0);
    }
    long total = Arrays.stream(nodes).mapToLong(HeatStore::units).sum();
    long[] times = new long[1000];
    long visits = 0;
    int cursor = 0;
    for (int t = 0; t < times.length; t++) {
      long start = System.nanoTime();
      for (int i = 0; i < Math.min(2048, count - 1); i++) {
        int a = cursor++ % (count - 1);
        HeatStore.transfer(nodes[a], nodes[a + 1], 2000000);
        visits++;
      }
      times[t] = System.nanoTime() - start;
    }
    if (Arrays.stream(nodes).mapToLong(HeatStore::units).sum() != total)
      throw new AssertionError("Heat balance");
    if (report) {
      long sum = Arrays.stream(times).sum();
      Arrays.sort(times);
      System.out.printf(
          Locale.ROOT,
          "nodes=%d visits=%d mean_us=%.3f p50_us=%.3f p95_us=%.3f p99_us=%.3f max_us=%.3f"
              + " conserved_quanta=%d%n",
          count,
          visits,
          sum / 1000000.0,
          times[500] / 1000.0,
          times[950] / 1000.0,
          times[990] / 1000.0,
          times[999] / 1000.0,
          total);
    }
  }

  public static void main(String[] args) {
    for (int n : new int[] {128, 1024, 4096}) {
      for (int warm = 0; warm < 5; warm++) sample(n, false);
      for (int repeat = 0; repeat < 5; repeat++) sample(n, true);
    }
  }
}
