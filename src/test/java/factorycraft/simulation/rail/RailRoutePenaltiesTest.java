package factorycraft.simulation.rail;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RailRoutePenaltiesTest {
  @Test
  void documentedSourceCostsRetainMetricAndSixtyToTwentyHzUnits() {
    assertEquals(1000, RailRoutePenalties.CIRCUIT_RED);
    assertEquals(2000, RailRoutePenalties.INTERMEDIATE_STOP);
    assertEquals(512, RailRoutePenalties.occupied(256, 1));
    assertEquals(128, RailRoutePenalties.occupied(256, 4));
    double[] expected = {0, 500, 1000, 2000, 7000, 7000, 100, 160, 1000};
    int index = 0;
    for (var kind : RailRoutePenalties.Obstruction.values())
      assertEquals(expected[index++], RailRoutePenalties.train(kind, 200), 1e-9);
    assertEquals(100, RailRoutePenalties.chainRetryTicks(2));
    assertEquals(600, RailRoutePenalties.chainRetryTicks(1));
  }

  @Test
  void invalidDistancesAndWaitingDurationsCannotCreateNegativeOrInfiniteCosts() {
    for (double length : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
      assertThrows(IllegalArgumentException.class, () -> RailRoutePenalties.occupied(length, 1));
    assertThrows(IllegalArgumentException.class, () -> RailRoutePenalties.occupied(10, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> RailRoutePenalties.train(RailRoutePenalties.Obstruction.SIGNAL_WAIT, -1));
    assertThrows(IllegalArgumentException.class, () -> RailRoutePenalties.chainRetryTicks(0));
  }

  @Test
  void targetPricingCannotMutateFullTraversalPrefixOrChargeTheDestinationAsPassingStop() {
    var graph = new RailGraph();
    var a = edge(graph, 0, -32);
    var b = edge(graph, -32, -64);
    var near = target(a.id(), 20);
    var far = target(b.id(), 20);
    var seen = new ArrayList<RailPathSearch.Progress>();
    RailPathSearch.Rules rules =
        new RailPathSearch.Rules() {
          public double penalty(RailGraph.Directed e, double from, double to) {
            fail("contextual adapter must be called");
            return 0;
          }

          public RailPathSearch.Price price(
              RailGraph.Directed e,
              double from,
              double to,
              RailPathSearch.Progress progress,
              UUID destination) {
            seen.add(progress);
            // The first candidate is very expensive. Full-edge pricing must start from the
            // original prefix; the target-only state must never leak into the next edge.
            int next = progress.blocks() + (destination == null ? 1 : 100);
            return new RailPathSearch.Price(
                destination != null && destination.equals(near.id()) ? 2000 : 0,
                new RailPathSearch.Progress(e.edge(), next));
          }
        };
    var search =
        new RailPathSearch(
            graph, new RailGraph.Directed(a.id(), false), 4, List.of(near, far), rules, 128);
    while (search.status() == RailPathSearch.Status.SEARCHING) assertTrue(search.advance(1) <= 1);
    assertEquals(far.id(), search.result().target().id());
    assertEquals(48, search.result().distance(), 1e-8);
    assertEquals(48, search.result().cost(), 1e-8);
    assertEquals(RailPathSearch.Progress.START, seen.get(0));
    assertEquals(RailPathSearch.Progress.START, seen.get(1));
    assertTrue(seen.stream().anyMatch(p -> p.blocks() == 1 && a.id().equals(p.region())));
    assertTrue(seen.stream().noneMatch(p -> p.blocks() >= 100));
  }

  @Test
  void aForbiddenContextualResultCannotBecomeAnImplicitNegativeOrNanShortcut() {
    var graph = new RailGraph();
    var e = edge(graph, 0, -32);
    var target = target(e.id(), 20);
    for (double invalid : new double[] {-1, Double.NaN}) {
      var search =
          new RailPathSearch(
              graph,
              new RailGraph.Directed(e.id(), false),
              0,
              List.of(target),
              (travel, from, to) -> invalid,
              128);
      assertThrows(IllegalArgumentException.class, () -> search.advance(1));
    }
  }

  private static RailGraph.Edge edge(RailGraph graph, int start, int end) {
    var e =
        new RailGraph.Edge(
            UUID.randomUUID(),
            RailCurve.plan(
                new RailEnd(0, 70, start, new RailHeading(0)),
                new RailEnd(0, 70, end, new RailHeading(0))));
    graph.add(e);
    return e;
  }

  private static RailPathSearch.Target target(UUID edge, double at) {
    return new RailPathSearch.Target(
        UUID.randomUUID(),
        new RailEdgePoints.Point(UUID.randomUUID(), edge, at, false, RailEdgePoints.Kind.STOP),
        50);
  }
}
