import factorycraft.simulation.fluids.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Isolated kernel comparison. Compile into a separate /tmp directory; never a running game's
 * output.
 */
public final class FluidSegmentBenchmark {
  static final int W = 40, H = 32, N = W * H, TICKS = 400;

  record Edge(int a, int b) {}

  static List<Edge> edges() {
    var e = new ArrayList<Edge>();
    for (int y = 0; y < H; y++)
      for (int x = 0; x < W; x++) {
        int a = y * W + x;
        if (x + 1 < W) e.add(new Edge(a, a + 1));
        if (y + 1 < H) e.add(new Edge(a, a + W));
      }
    return e;
  }

  static FluidCell<String> cell(int i, int amount) {
    var c = new FluidCell<String>(i % 160 == 0 ? 16000 : 1000, () -> {});
    if (amount > 0) c.restore("water", amount);
    return c;
  }

  static long allocated() {
    var bean = ManagementFactory.getThreadMXBean();
    return bean instanceof com.sun.management.ThreadMXBean b
        ? b.getThreadAllocatedBytes(Thread.currentThread().threadId())
        : 0;
  }

  static void legacy(boolean report) {
    var graph = new FluidTransport<String>();
    var cells = new ArrayList<FluidCell<String>>();
    var nodes = new ArrayList<FluidTransport.Node<String>>();
    var edges = edges();
    var active = new boolean[N];
    Arrays.fill(active, true);
    long memory = allocated(), start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      var c = cell(i, i % 160 == 0 ? 8000 : 0);
      cells.add(c);
      final int at = i;
      nodes.add(graph.add(c, () -> active[at]));
    }
    for (var e : edges) graph.connect(nodes.get(e.a), nodes.get(e.b));
    long build = System.nanoTime() - start;
    int first = -1;
    long consumed = 0, tickStart = System.nanoTime(), peak = 0;
    for (int t = 0; t < TICKS; t++) {
      long one = System.nanoTime();
      if (t == 80)
        for (var e : edges)
          if (e.a % W == 19 && e.b == e.a + 1) graph.disconnect(nodes.get(e.a), nodes.get(e.b));
      if (t == 160)
        for (var e : edges)
          if (e.a % W == 19 && e.b == e.a + 1) graph.connect(nodes.get(e.a), nodes.get(e.b));
      if (t == 240)
        for (int i = 0; i < W; i++) {
          active[15 * W + i] = false;
          graph.wake(nodes.get(15 * W + i));
        }
      if (t == 260)
        for (int i = 0; i < W; i++) {
          active[15 * W + i] = true;
          graph.wake(nodes.get(15 * W + i));
        }
      graph.tick(100000, 100);
      for (int i = W - 1; i < N; i += 160) {
        int n = cells.get(i).drain(10, false);
        if (n > 0 && first < 0) first = t;
        consumed += n;
      }
      peak = Math.max(peak, System.nanoTime() - one);
    }
    long elapsed = System.nanoTime() - tickStart,
        total = consumed + cells.stream().mapToLong(FluidCell::amount).sum();
    if (total != 64000) throw new AssertionError("Legacy mass " + total);
    long allocation = allocated() - memory;
    var copies = cells.stream().map(c -> cell(cells.indexOf(c), c.amount())).toList();
    if (copies.stream().mapToLong(FluidCell::amount).sum() + consumed != 64000)
      throw new AssertionError("Legacy restore mass");
    if (report)
      System.out.printf(
          Locale.ROOT,
          "legacy nodes=%d edges=%d setup_ms=%.3f mean_tick_us=%.3f max_tick_us=%.3f edge_visits=%d"
              + " consumed=%d first_far_tick=%d allocated_bytes=%d ledger=%d%n",
          N,
          edges.size(),
          build / 1e6,
          elapsed / 1e3 / TICKS,
          peak / 1e3,
          graph.visits(),
          consumed,
          first,
          allocation,
          total);
  }

  static void segmented(boolean report) {
    long memory = allocated(), start = System.nanoTime();
    var graph = new PipelineNetwork<String>();
    var nodes = new ArrayList<PipelineNetwork<String>.Node>();
    var edges = edges();
    for (int i = 0; i < N; i++)
      nodes.add(
          graph.add(
              new PipelineNetwork.Site(i, i % W, i % W, 0, 0, i / W, i / W),
              cell(i, i % 160 == 0 ? 8000 : 0)));
    for (var e : edges) graph.connect(nodes.get(e.a), nodes.get(e.b));
    settle(graph);
    long build = System.nanoTime() - start, visits = graph.visits(), hotVisits = visits;
    int first = -1;
    long consumed = 0, tickStart = System.nanoTime(), peak = 0;
    for (int t = 0; t < TICKS; t++) {
      long one = System.nanoTime();
      if (t == 80)
        for (var e : edges)
          if (e.a % W == 19 && e.b == e.a + 1) graph.disconnect(nodes.get(e.a), nodes.get(e.b));
      if (t == 160)
        for (var e : edges)
          if (e.a % W == 19 && e.b == e.a + 1) graph.connect(nodes.get(e.a), nodes.get(e.b));
      if (t == 240) for (int i = 0; i < W; i++) graph.active(nodes.get(15 * W + i), false);
      if (t == 260) for (int i = 0; i < W; i++) graph.active(nodes.get(15 * W + i), true);
      graph.advance(4096);
      for (int i = W - 1; i < N; i += 160) {
        int n = nodes.get(i).port(0).drain(10, false);
        if (n > 0 && first < 0) first = t;
        consumed += n;
      }
      if (t == 79) {
        if (graph.visits() != hotVisits) throw new AssertionError("Hot flow rebuilt topology");
      }
      peak = Math.max(peak, System.nanoTime() - one);
    }
    long elapsed = System.nanoTime() - tickStart,
        total = consumed + nodes.stream().mapToLong(n -> n.owned().amount()).sum();
    if (total != 64000) throw new AssertionError("Segment mass " + total);
    long allocation = allocated() - memory;
    var saved = nodes.stream().map(n -> n.owned().amount()).toList();
    var restored = new PipelineNetwork<String>();
    var re = new ArrayList<PipelineNetwork<String>.Node>();
    for (int i = 0; i < N; i++)
      re.add(
          restored.add(
              new PipelineNetwork.Site(i, i % W, i % W, 0, 0, i / W, i / W),
              cell(i, saved.get(i))));
    for (var e : edges) restored.connect(re.get(e.a), re.get(e.b));
    settle(restored);
    if (restored.segments().stream().mapToLong(s -> s.amount()).sum() + consumed != 64000)
      throw new AssertionError("Restore mass");
    if (report)
      System.out.printf(
          Locale.ROOT,
          "segment nodes=%d edges=%d setup_ms=%.3f mean_tick_us=%.3f max_tick_us=%.3f"
              + " topology_visits=%d additional_visits=%d consumed=%d first_far_tick=%d"
              + " allocated_bytes=%d ledger=%d restored=true%n",
          N,
          edges.size(),
          build / 1e6,
          elapsed / 1e3 / TICKS,
          peak / 1e3,
          graph.visits(),
          graph.visits() - visits,
          consumed,
          first,
          allocation,
          total);
  }

  static void settle(PipelineNetwork<?> g) {
    while (g.pending() > 0) g.advance(4096);
  }

  public static void main(String[] args) {
    legacy(false);
    segmented(false);
    for (int i = 0; i < 3; i++) {
      legacy(true);
      segmented(true);
    }
  }
}
