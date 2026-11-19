import factorycraft.simulation.rail.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Same bounded creation workload on both revisions; not Minecraft FPS/TPS. */
public final class RailPlacementProfile {
  static long sink;

  static void run(int length, boolean report) {
    var curve =
        RailCurve.plan(
            new RailEnd(0, 70, 0, new RailHeading(0)),
            new RailEnd(length == 128 ? 0 : 24, 70, -length, new RailHeading(0)));
    var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long thread = Thread.currentThread().threadId();
    long bytes = bean.getThreadAllocatedBytes(thread);
    long[] ns = new long[25];
    for (int i = 0; i < ns.length; i++) {
      long start = System.nanoTime();
      var f = new RailFootprint(curve);
      sink += f.clearance.size() + f.bed.size();
      ns[i] = System.nanoTime() - start;
    }
    bytes = bean.getThreadAllocatedBytes(thread) - bytes;
    long sum = Arrays.stream(ns).sum();
    Arrays.sort(ns);
    if (report)
      System.out.printf(
          Locale.ROOT,
          "length=%d mean_us=%.3f median_us=%.3f p95_us=%.3f max_us=%.3f"
              + " allocated_bytes_per_candidate=%d%n",
          length,
          sum / 25000.,
          ns[12] / 1000.,
          ns[23] / 1000.,
          ns[24] / 1000.,
          bytes / 25);
  }

  public static void main(String[] args) {
    for (int i = 0; i < 10; i++) {
      run(64, false);
      run(128, false);
    }
    for (int i = 0; i < 3; i++) {
      run(64, true);
      run(128, true);
    }
  }
}
