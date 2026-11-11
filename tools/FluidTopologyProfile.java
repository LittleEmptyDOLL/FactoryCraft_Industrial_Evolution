import factorycraft.simulation.fluids.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Comparable topology microprofile. Every timed operation includes completion; no CPU threshold.
 */
public final class FluidTopologyProfile {
  static final com.sun.management.ThreadMXBean THREAD =
      (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

  static long allocation() {
    return THREAD.getThreadAllocatedBytes(Thread.currentThread().threadId());
  }

  static final class Fixture {
    PipelineNetwork<String> g = new PipelineNetwork<>();
    ArrayList<PipelineNetwork<String>.Node> nodes = new ArrayList<>();

    Fixture(int size) {
      for (int i = 0; i < size; i++) {
        int x = i % 200, z = i / 200;
        var c = new FluidCell<String>(i % 1000 == 0 ? 16000 : 1000, () -> {});
        if (i == 0) c.restore("water", 8000);
        var n = g.add(new PipelineNetwork.Site(i, x, x, 0, 0, z, z), c);
        nodes.add(n);
        if (i > 0) g.connect(nodes.get(i - 1), n);
      }
      settle();
    }

    void settle() {
      int guard = 0;
      while (g.pending() > 0) {
        g.advance(256);
        if (++guard > 100000) throw new AssertionError("No progress");
      }
    }

    long mass() {
      return nodes.stream().mapToLong(n -> n.owned().amount()).sum();
    }
  }

  static final class Samples {
    ArrayList<Long> times = new ArrayList<>(), alloc = new ArrayList<>();
    long rebuilds, visits;

    void run(Fixture f, Runnable r) {
      long a = allocation(), v = f.g.visits(), b = f.g.rebuilds(), t = System.nanoTime();
      r.run();
      times.add(System.nanoTime() - t);
      alloc.add(allocation() - a);
      rebuilds += f.g.rebuilds() - b;
      visits += f.g.visits() - v;
    }

    void print(String label, int size, String op) {
      var t = times.stream().mapToLong(Long::longValue).sorted().toArray();
      double mean = Arrays.stream(t).average().orElse(0);
      System.out.printf(
          Locale.ROOT,
          "{\"label\":\"%s\",\"nodes\":%d,\"op\":\"%s\",\"samples\":%d,\"mean_ns\":%.1f,\"median_ns\":%d,\"p95_ns\":%d,\"p99_ns\":%d,\"max_ns\":%d,\"allocation_mean_bytes\":%.1f,\"rebuilds\":%d,\"visits\":%d}%n",
          label,
          size,
          op,
          t.length,
          mean,
          t[t.length / 2],
          t[(int) Math.ceil(t.length * .95) - 1],
          t[(int) Math.ceil(t.length * .99) - 1],
          t[t.length - 1],
          alloc.stream().mapToLong(Long::longValue).average().orElse(0),
          rebuilds,
          visits);
    }
  }

  static void round(int size, String label, boolean report) {
    var creation = new Samples();
    Fixture f;
    // Time network setup separately; warmed process, no world or save cost hidden here.
    long a = allocation(), t = System.nanoTime();
    f = new Fixture(size);
    creation.times.add(System.nanoTime() - t);
    creation.alloc.add(allocation() - a);
    creation.rebuilds = f.g.rebuilds();
    creation.visits = f.g.visits();
    if (report) creation.print(label, size, "create");
    var flow = new Samples();
    flow.run(
        f,
        () -> {
          var p = f.nodes.get(0).port(0);
          for (int i = 0; i < 10000; i++) {
            p.drain(1, false);
            p.fill("water", 1, false);
          }
        });
    if (f.mass() != 8000) throw new AssertionError("flow mass");
    if (report) flow.print(label, size, "10000_fill_drain");
    var split = new Samples();
    var merge = new Samples();
    var leafAdd = new Samples();
    var leafRemove = new Samples();
    var tankAdd = new Samples();
    var tankRemove = new Samples();
    var availability = new Samples();
    for (int i = 0; i < 24; i++) {
      int mid = size / 2;
      split.run(
          f,
          () -> {
            f.g.disconnect(f.nodes.get(mid - 1), f.nodes.get(mid));
            f.settle();
          });
      merge.run(
          f,
          () -> {
            f.g.connect(f.nodes.get(mid - 1), f.nodes.get(mid));
            f.settle();
          });
      final PipelineNetwork<String>.Node[] extra = new PipelineNetwork.Node[1];
      leafAdd.run(
          f,
          () -> {
            extra[0] =
                f.g.add(
                    new PipelineNetwork.Site(size, 0, 0, 1, 1, 0, 0),
                    new FluidCell<>(1000, () -> {}));
            f.g.connect(f.nodes.get(size - 1), extra[0]);
            f.settle();
          });
      final long[] packed = {0};
      leafRemove.run(
          f,
          () -> {
            f.g.remove(extra[0]);
            packed[0] = extra[0].owned().amount();
            f.settle();
          });
      if (f.mass() + packed[0] != 8000) throw new AssertionError("leaf mass");
      f.nodes.getFirst().port(0).fill("water", (int) packed[0], false);
      tankAdd.run(
          f,
          () -> {
            extra[0] =
                f.g.add(
                    new PipelineNetwork.Site(size, 0, 0, 1, 1, 0, 0),
                    new FluidCell<>(16000, () -> {}));
            f.g.connect(f.nodes.get(size - 1), extra[0]);
            f.settle();
          });
      tankRemove.run(
          f,
          () -> {
            f.g.remove(extra[0]);
            packed[0] = extra[0].owned().amount();
            f.settle();
          });
      if (f.mass() + packed[0] != 8000) throw new AssertionError("tank mass");
      f.nodes.getFirst().port(0).fill("water", (int) packed[0], false);
      availability.run(
          f,
          () -> {
            for (int j = mid; j < Math.min(size, mid + 16); j++) f.g.active(f.nodes.get(j), false);
            f.settle();
            for (int j = mid; j < Math.min(size, mid + 16); j++) f.g.active(f.nodes.get(j), true);
            f.settle();
          });
      if (f.mass() != 8000) throw new AssertionError("availability mass");
    }
    if (report) {
      split.print(label, size, "split");
      merge.print(label, size, "merge");
      leafAdd.print(label, size, "leaf_add");
      leafRemove.print(label, size, "leaf_remove");
      tankAdd.print(label, size, "tank_add");
      tankRemove.print(label, size, "tank_remove");
      availability.print(label, size, "availability_16");
    }
  }

  public static void main(String[] args) {
    String label = args.length > 0 ? args[0] : "unknown";
    if (args.length > 1 && args[1].equals("creation")) {
      for (int size : new int[] {100, 1000, 5000, 10000}) {
        for (int warm = 0; warm < 8; warm++) new Fixture(size);
        var sample = new Samples();
        for (int i = 0; i < 30; i++) {
          long memory = allocation(), start = System.nanoTime();
          var f = new Fixture(size);
          sample.times.add(System.nanoTime() - start);
          sample.alloc.add(allocation() - memory);
          sample.rebuilds += f.g.rebuilds();
          sample.visits += f.g.visits();
          if (f.mass() != 8000) throw new AssertionError("Creation ledger");
        }
        sample.print(label, size, "create_repeated");
      }
      return;
    }

    for (int i = 0; i < 3; i++) round(1000, label, false);
    for (int size : new int[] {100, 1000, 5000, 10000})
      for (int i = 0; i < 3; i++) round(size, label, true);
  }
}
