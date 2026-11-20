package factorycraft.simulation.rail;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RailAutomationKernelTest {
  private static RailGraph.Edge edge(RailGraph g, int x, int y, int from, int to) {
    var edge =
        new RailGraph.Edge(
            UUID.randomUUID(),
            RailCurve.plan(
                new RailEnd(x, y, from, new RailHeading(0)),
                new RailEnd(x, y, to, new RailHeading(0))));
    g.add(edge);
    return edge;
  }

  private static RailEdgePoints.Point point(
      UUID edge, double offset, boolean reverse, RailEdgePoints.Kind kind) {
    return new RailEdgePoints.Point(UUID.randomUUID(), edge, offset, reverse, kind);
  }

  private static void finish(RailSignalBlocks blocks) {
    for (int i = 0; blocks.busy() && i < 10000; i++) assertTrue(blocks.advance(3) <= 3);
    assertFalse(blocks.busy());
  }

  @Test
  void orderedPointPortFindsStrictNextInBothDirectionsAndKeepsPairedCuts() {
    var graph = new RailGraph();
    var e = edge(graph, 0, 70, 0, -64);
    var index = new RailEdgePoints();
    var near = point(e.id(), 12, false, RailEdgePoints.Kind.SIGNAL);
    var pair = point(e.id(), 12, true, RailEdgePoints.Kind.CHAIN);
    var far = point(e.id(), 40, false, RailEdgePoints.Kind.STOP);
    for (var p : List.of(far, pair, near)) index.put(p, graph);
    assertEquals(
        List.of(12.0, 12.0, 40.0),
        index.on(e.id()).stream().map(RailEdgePoints.Point::offset).toList());
    assertEquals(near, index.next(new RailGraph.Directed(e.id(), false), 0, near.kind()));
    assertNull(index.next(new RailGraph.Directed(e.id(), false), 12, near.kind()));
    assertEquals(pair, index.next(new RailGraph.Directed(e.id(), true), 40, pair.kind()));
    assertEquals(3, index.ahead(new RailGraph.Directed(e.id(), true), 64, 0).size());
    assertEquals(1, index.cutsAhead(new RailGraph.Directed(e.id(), true), 64, 0).size());
  }

  @Test
  void failedPointRelocationCannotDeleteTheExistingPhysicalReference() {
    var g = new RailGraph();
    var a = edge(g, 0, 70, 0, -64);
    var b = edge(g, 8, 70, 0, -64);
    var index = new RailEdgePoints();
    var old = point(a.id(), 12, false, RailEdgePoints.Kind.STOP);
    index.put(old, g);
    for (int i = 0; i < 256; i++) index.put(point(b.id(), 12, false, old.kind()), g);
    var moved = new RailEdgePoints.Point(old.id(), b.id(), 12, false, old.kind());
    assertThrows(IllegalArgumentException.class, () -> index.put(moved, g));
    assertEquals(old, index.get(old.id()));
    assertEquals(List.of(old), index.on(a.id()));
  }

  @Test
  void twoOppositeSignalsSplitOnePhysicalEdgeWithoutDuplicatingOrRenumberingIt() {
    var g = new RailGraph();
    var e = edge(g, 0, 70, 0, -64);
    var index = new RailEdgePoints();
    var blocks = new RailSignalBlocks(g, index);
    blocks.changed(e.id());
    finish(blocks);
    assertEquals(blocks.at(e.id(), 4, true).id, blocks.at(e.id(), 60, true).id);
    for (boolean reverse : new boolean[] {false, true})
      index.put(point(e.id(), 32, reverse, RailEdgePoints.Kind.SIGNAL), g);
    blocks.changed(e.id());
    assertNull(blocks.at(e.id(), 4, true));
    assertTrue(blocks.advance(1) <= 1);
    assertNull(blocks.at(e.id(), 4, true));
    finish(blocks);
    assertEquals(2, blocks.spans(e.id()).size());
    assertNotEquals(blocks.at(e.id(), 32, false).id, blocks.at(e.id(), 32, true).id);
    assertEquals(1, g.edges().size());
    assertSame(e, g.edge(e.id()));
    assertEquals(2, blocks.covering(e.id(), 20, 48).size());
  }

  @Test
  void localFloodAndBudgetedPublicationPreserveUnrelatedAndVerticallySeparatedRegions() {
    var g = new RailGraph();
    var a = edge(g, 0, 70, 0, -32);
    var b = edge(g, 0, 70, -32, -64);
    var upper = edge(g, 0, 80, 0, -64);
    var index = new RailEdgePoints();
    var blocks = new RailSignalBlocks(g, index);
    for (var e : g.edges()) blocks.changed(e.id());
    finish(blocks);
    var original = blocks.at(a.id(), 4, true);
    assertSame(original, blocks.at(b.id(), 20, true));
    assertNotEquals(original.id, blocks.at(upper.id(), 4, true).id);
    blocks.takeInvalidatedEdges();
    long before = blocks.visited();
    index.put(point(upper.id(), 32, false, RailEdgePoints.Kind.SIGNAL), g);
    blocks.changed(upper.id());
    finish(blocks);
    assertSame(original, blocks.at(a.id(), 4, true));
    assertTrue(blocks.visited() - before <= 2);
    assertFalse(blocks.takeInvalidatedEdges().contains(a.id()));
  }

  @Test
  void interruptedRebuildNeverPublishesADeletedOrPartialComponent() {
    var g = new RailGraph();
    var a = edge(g, 0, 70, 0, -32);
    var b = edge(g, 0, 70, -32, -64);
    var blocks = new RailSignalBlocks(g, new RailEdgePoints());
    blocks.changed(a.id());
    blocks.changed(b.id());
    blocks.advance(1);
    g.remove(b.id());
    blocks.changed(b.id());
    finish(blocks);
    assertNotNull(blocks.at(a.id(), 4, true));
    assertNull(blocks.at(b.id(), 4, true));
  }

  @Test
  void blockReservationIsAtomicAndARearWagonKeepsItsBlockOccupied() {
    var claims = new RailBlockClaims();
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    claims.occupy(a, Set.of(first, second));
    assertFalse(claims.reserve(b, Set.of(first, second)));
    assertNull(claims.reservation(second));
    claims.occupy(a, Set.of(second));
    assertTrue(claims.reserve(b, Set.of(first)));
    assertFalse(claims.available(second, b));
    claims.remove(a);
    assertTrue(claims.available(second, b));
  }

  @Test
  void sourceDijkstraPortUsesFractionalMetricLengthsAndHigherStationPriority() {
    var g = new RailGraph();
    var a = edge(g, 0, 70, 0, -32);
    var b = edge(g, 0, 70, -32, -64);
    var c = edge(g, 0, 70, -64, -96);
    var near =
        new RailPathSearch.Target(
            UUID.randomUUID(), point(b.id(), 8, false, RailEdgePoints.Kind.STOP), 50);
    var far =
        new RailPathSearch.Target(
            UUID.randomUUID(), point(c.id(), 16, false, RailEdgePoints.Kind.STOP), 100);
    var search =
        new RailPathSearch(
            g,
            new RailGraph.Directed(a.id(), false),
            8,
            List.of(near, far),
            (e, from, to) -> 0,
            128);
    while (search.status() == RailPathSearch.Status.SEARCHING) assertTrue(search.advance(1) <= 1);
    assertEquals(RailPathSearch.Status.FOUND, search.status());
    assertEquals(far.id(), search.result().target().id());
    assertEquals(72, search.result().distance(), 1e-7);
    assertEquals(
        List.of(a.id(), b.id(), c.id()),
        search.result().edges().stream().map(RailGraph.Directed::edge).toList());
  }

  @Test
  void illegalSignalDirectionDoesNotBecomeAVirtualUturnAndGraphEditsCancelAJob() {
    var g = new RailGraph();
    var a = edge(g, 0, 70, 0, -32);
    var b = edge(g, 0, 70, -32, -64);
    var target =
        new RailPathSearch.Target(
            UUID.randomUUID(), point(b.id(), 16, false, RailEdgePoints.Kind.STOP), 50);
    var no =
        new RailPathSearch(
            g,
            new RailGraph.Directed(a.id(), false),
            8,
            List.of(target),
            (e, from, to) -> Double.POSITIVE_INFINITY,
            100);
    no.advance(10);
    assertEquals(RailPathSearch.Status.NO_PATH, no.status());
    var stale =
        new RailPathSearch(
            g, new RailGraph.Directed(a.id(), false), 8, List.of(target), (e, from, to) -> 0, 100);
    stale.advance(1);
    g.remove(b.id());
    stale.advance(1);
    assertEquals(RailPathSearch.Status.STALE, stale.status());
    assertNull(stale.result());
  }

  @Test
  void behindSourceTargetRequiresARealTurningLoopAndCanRevisitInitialEdge() {
    var g = new RailGraph();
    var routes = new ArrayList<RailGraph.Edge>();
    int[][] ends = {{0, 0, 0}, {32, -32, 4}, {64, 0, 8}, {32, 32, 12}};
    for (int i = 0; i < 4; i++) {
      var a = ends[i];
      var b = ends[(i + 1) % 4];
      var e =
          new RailGraph.Edge(
              UUID.randomUUID(),
              RailCurve.plan(
                  new RailEnd(a[0], 70, a[1], new RailHeading(a[2])),
                  new RailEnd(b[0], 70, b[1], new RailHeading(b[2]))));
      g.add(e);
      routes.add(e);
    }
    var target =
        new RailPathSearch.Target(
            UUID.randomUUID(),
            point(routes.getFirst().id(), 10, false, RailEdgePoints.Kind.STOP),
            50);
    var search =
        new RailPathSearch(
            g,
            new RailGraph.Directed(routes.getFirst().id(), false),
            20,
            List.of(target),
            (e, from, to) -> 0,
            100);
    search.advance(100);
    assertEquals(RailPathSearch.Status.FOUND, search.status());
    assertEquals(5, search.result().edges().size());
    assertEquals(search.result().edges().getFirst(), search.result().edges().getLast());
    assertTrue(search.result().distance() > 100);
  }

  @Test
  void stopSlotsCountEachTrainOnceAndRetainCommittedArrivalAfterLimitOrEnableChanges() {
    var slots = new RailStopSlots();
    var id = UUID.randomUUID();
    var train = UUID.randomUUID();
    slots.put(new RailStopSlots.Stop(id, "team", "Iron", true, 1, 50));
    assertTrue(slots.acquire(train, id));
    assertTrue(slots.acquire(train, id));
    assertEquals(1, slots.count(id));
    assertFalse(slots.acquire(UUID.randomUUID(), id));
    slots.put(new RailStopSlots.Stop(id, "team", "Iron", false, 0, 255));
    assertTrue(slots.available(id, train));
    assertFalse(slots.available(id, UUID.randomUUID()));
    assertEquals(
        Set.of(train), slots.put(new RailStopSlots.Stop(id, "team", "Copper", true, 1, 50)));
    assertNull(slots.destination(train));
    assertTrue(slots.named("team", "Iron").isEmpty());
  }

  @Test
  void restoredSlotsPreserveExistingClaimsEvenIfLimitWasReducedBeforeSaving() {
    var slots = new RailStopSlots();
    var id = UUID.randomUUID();
    var a = UUID.randomUUID();
    slots.put(new RailStopSlots.Stop(id, "team", "Oil", false, 0, 50));
    assertTrue(slots.restore(a, id));
    assertFalse(slots.restore(a, id));
    assertTrue(slots.available(id, a));
    assertEquals(1, slots.count(id));
  }

  @Test
  void conditionsUseDnfAndUnknownNeverSatisfiesNotEqual() {
    var a = new RailWait.Condition(RailWait.Type.TIME, "a", RailWait.Comparison.GE, 1, false);
    var b = new RailWait.Condition(RailWait.Type.TIME, "b", RailWait.Comparison.GE, 1, true);
    var c = new RailWait.Condition(RailWait.Type.TIME, "c", RailWait.Comparison.GE, 1, false);
    assertTrue(RailWait.ready(List.of(a, b, c), q -> OptionalLong.of(q == a ? 1 : 0)));
    assertFalse(RailWait.ready(List.of(a, b, c), q -> OptionalLong.of(q == b ? 1 : 0)));
    var unknown =
        new RailWait.Condition(RailWait.Type.CIRCUIT, "x", RailWait.Comparison.NE, 0, false);
    assertFalse(RailWait.ready(List.of(unknown), q -> OptionalLong.empty()));
    assertTrue(RailWait.ready(List.of(), q -> OptionalLong.empty()));
  }

  @Test
  void regularSignalsObserveWholeBodyClaimsAndReservationsAndForceRed() {
    var g = new RailGraph();
    var e = edge(g, 0, 70, 0, -64);
    var points = new RailEdgePoints();
    var signal = point(e.id(), 24, false, RailEdgePoints.Kind.SIGNAL);
    points.put(signal, g);
    var blocks = new RailSignalBlocks(g, points);
    blocks.changed(e.id());
    finish(blocks);
    var claims = new RailBlockClaims();
    var force = new HashSet<UUID>();
    var system = new RailSignals(g, points, blocks, claims, p -> true, p -> force.contains(p.id()));
    var target = blocks.at(e.id(), 24, true).id;
    var train = UUID.randomUUID();
    assertEquals(RailSignals.Aspect.GREEN, system.view(signal).aspect());
    claims.reserve(train, Set.of(target));
    assertEquals(RailSignals.Aspect.YELLOW, system.view(signal).aspect());
    claims.occupy(train, Set.of(target));
    assertEquals(RailSignals.Aspect.RED, system.view(signal).aspect());
    claims.remove(train);
    force.add(signal.id());
    assertEquals(RailSignals.Aspect.RED, system.view(signal).aspect());
    assertFalse(system.directionAllowed(new RailGraph.Directed(e.id(), true), 64, 0));
    assertTrue(system.directionAllowed(new RailGraph.Directed(e.id(), false), 0, 64));
  }

  @Test
  void linearChainRequiresARealRegularExitAndUnknownPublicationNeverGivesGreen() {
    var g = new RailGraph();
    var e = edge(g, 0, 70, 0, -96);
    var points = new RailEdgePoints();
    var chain = point(e.id(), 20, false, RailEdgePoints.Kind.CHAIN);
    var exit = point(e.id(), 56, false, RailEdgePoints.Kind.SIGNAL);
    points.put(chain, g);
    points.put(exit, g);
    var blocks = new RailSignalBlocks(g, points);
    blocks.changed(e.id());
    var claims = new RailBlockClaims();
    var signals = new RailSignals(g, points, blocks, claims, p -> true, p -> false);
    assertEquals(RailSignals.Aspect.INVALID, signals.view(chain).aspect());
    finish(blocks);
    assertEquals(RailSignals.Aspect.GREEN, signals.view(chain).aspect());
    var block = blocks.at(e.id(), 56, true).id;
    var other = UUID.randomUUID();
    claims.occupy(other, Set.of(block));
    assertEquals(RailSignals.Aspect.RED, signals.view(chain).aspect());
    assertTrue(signals.view(chain).signals().contains(exit.id()));
    claims.remove(other);
    points.remove(exit.id());
    blocks.changed(e.id());
    finish(blocks);
    assertEquals(RailSignals.Aspect.RED, signals.view(chain).aspect());
  }

  @Test
  void exactBrakeDisplacementAgreesWithTheExistingThreeSourceSubsteps() {
    for (double weight : new double[] {2000, 6000, 66000})
      for (double speed : new double[] {0, .001, .18, 1.2}) {
        var p =
            new factorycraft.simulation.trains.TrainMotion.Parameters(weight, 1, 20, .0075, 0, 1.2);
        double expected = factorycraft.simulation.trains.TrainMotion.brakeDistance(speed, p),
            distance = 0;
        for (int tick = 0; speed > 0 && tick < 100000; tick++) {
          var next = factorycraft.simulation.trains.TrainMotion.tick(speed, p, false, true);
          distance += next.distance();
          speed = next.speed();
        }
        assertEquals(0, speed);
        assertEquals(expected, distance, 1e-7);
      }
  }

  @Test
  void scheduleBoundsAndTypedWildcardSubstitutionDoNotInterpretCommands() {
    var match = new RailInterrupt.Match(RailInterrupt.Type.ITEM, "minecraft:iron_ingot");
    assertEquals(
        "Unload [item=minecraft:iron_ingot]", RailInterrupt.substitute("Unload [item=*]", match));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RailInterrupt.Match(RailInterrupt.Type.FLUID, "invalid resource"));
    assertThrows(IllegalArgumentException.class, () -> new RailSchedule.Entry("", List.of()));
  }

  @Test
  void nativeFluidThresholdScalingRetainsFractionalUnitsForEveryComparison() {
    var condition =
        new RailWait.Condition(
            RailWait.Type.FLUID_COUNT, "water", RailWait.Comparison.GT, 123, false);
    assertTrue(RailWait.ready(List.of(condition), c -> OptionalLong.of(1231), c -> c.value() * 10));
    assertFalse(
        RailWait.ready(List.of(condition), c -> OptionalLong.of(1230), c -> c.value() * 10));
  }
}
