import factorycraft.simulation.rail.*;
import java.util.*;

/** Deterministic work/progress probe, not a timer, TPS measurement or native traffic test. */
public final class RailSignalPublicationProbe {
  static RailGraph.Edge edge(RailGraph graph, int x, int from, int to, int id) {
    var e =
        new RailGraph.Edge(
            new UUID(0, id),
            RailCurve.plan(
                new RailEnd(x, 70, from, new RailHeading(0)),
                new RailEnd(x, 70, to, new RailHeading(0))));
    graph.add(e);
    return e;
  }

  public static void main(String[] args) {
    for (int count : new int[] {80, 1000}) {
      var graph = new RailGraph();
      var main = new ArrayList<RailGraph.Edge>();
      for (int i = 0; i < count; i++) main.add(edge(graph, 0, -i * 16, -(i + 1) * 16, i + 1));
      var noise = edge(graph, 32, 0, -32, count + 1);
      var blocks = new RailSignalBlocks(graph, new RailEdgePoints());
      for (var e : main) blocks.changed(e.id());
      blocks.advance(1);
      int firstReady = -1, work = 0;
      for (int step = 0; step < count * 4; step++) {
        blocks.changed(noise.id());
        int spent = blocks.advance(4);
        work += spent;
        if (spent > 4) throw new AssertionError("budget overspend");
        if (blocks.at(main.getFirst().id(), 4, true) != null) {
          firstReady = step + 1;
          break;
        }
      }
      System.out.printf(
          Locale.ROOT,
          "{\"profile\":\"deterministic-local-publication-progress\",\"mainEdges\":%d,\"budgetPerAdvance\":4,\"readyDuringUnrelatedEdits\":%s,\"advancesToReady\":%d,\"visited\":%d,\"work\":%d}%n",
          count,
          firstReady > 0,
          firstReady,
          blocks.visited(),
          work);
    }
  }
}
