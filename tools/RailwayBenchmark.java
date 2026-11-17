import factorycraft.simulation.rail.*;
import java.util.*;

/** Bounded standalone topology/metric profile. No Minecraft TPS claim. */
public final class RailwayBenchmark {
  static RailGraph.Edge edge(int i) {
    return new RailGraph.Edge(
        new UUID(30, i + 1),
        RailCurve.plan(
            new RailEnd(0, 70, -i * 8, new RailHeading(0)),
            new RailEnd(0, 70, -(i + 1) * 8, new RailHeading(0))));
  }

  public static void main(String[] args) {
    for (int size : new int[] {100, 1000, 4000}) {
      var g = new RailGraph();
      long begin = System.nanoTime();
      for (int i = 0; i < size; i++) g.add(edge(i));
      long setup = System.nanoTime() - begin;
      var first = g.edges().iterator().next();
      long revision = g.revisions();
      long[] times = new long[10000];
      long total = 0;
      for (int i = -1000; i < times.length; i++) {
        long t = System.nanoTime();
        var result = g.successors(new RailGraph.Directed(first.id(), false));
        if (result.size() != 1) throw new AssertionError("real connected continuation");
        long elapsed = System.nanoTime() - t;
        if (i >= 0) {
          times[i] = elapsed;
          total += elapsed;
        }
      }
      if (g.revisions() != revision) throw new AssertionError("read-only work rebuilt topology");
      var change = g.edges().stream().skip(size / 2).findFirst().orElseThrow();
      begin = System.nanoTime();
      g.remove(change.id());
      g.add(change);
      long edit = System.nanoTime() - begin;
      if (g.revisions() != revision + 2) throw new AssertionError("local revision invariant");
      Arrays.sort(times);
      System.out.printf(
          java.util.Locale.ROOT,
          "edges=%d nodes=%d setup_ms=%.3f query_mean_us=%.3f query_p50_us=%.3f query_p95_us=%.3f"
              + " query_p99_us=%.3f query_max_us=%.3f local_remove_rejoin_us=%.3f"
              + " idle_rebuilds=0%n",
          size,
          g.nodes(),
          setup / 1e6,
          total / (times.length * 1000.0),
          times[5000] / 1000.0,
          times[9500] / 1000.0,
          times[9900] / 1000.0,
          times[9999] / 1000.0,
          edit / 1000.0);
    }
  }
}
