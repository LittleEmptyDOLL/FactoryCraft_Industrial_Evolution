package factorycraft.simulation;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.belts.BeltRoute;
import factorycraft.simulation.belts.*;
import org.junit.jupiter.api.Test;

class BeltMetricTest {
  @Test
  void pathLengthsAreMetric() {
    assertEquals(96, BeltRoute.FLAT.length(0));
    assertEquals(136, new BeltRoute(BeltRoute.Shape.UP, 0, 0).length(0));
    var left = new BeltRoute(BeltRoute.Shape.LEFT, 0, 0);
    assertEquals(42, left.length(0));
    assertEquals(109, left.length(1));
    for (int side = 0; side < 4; side++)
      for (int h : new int[] {-16, -1, 1, 16})
        for (int lane = 0; lane < 2; lane++) {
          var lift = new BeltRoute(BeltRoute.Shape.LIFT, h, side);
          assertTrue(lift.length(lane) >= Math.abs(h) * 96);
          assertEquals(.1875 + h, lift.point(lane, lift.length(lane)).y(), 1e-8);
        }
  }

  @Test
  void variableLengthPipelineHasExactFlowAndDelay() {
    for (int tier = 1; tier <= 3; tier++) {
      var t = new BeltTransport<Integer>();
      var a = new BeltLane<Integer>(42);
      var b = new BeltLane<Integer>(109);
      var c = new BeltLane<Integer>(136);
      var x = t.create(a, tier, v -> {});
      var y = t.create(b, tier, v -> {});
      var z = t.create(c, tier, v -> {});
      int[] count = {0};
      var sink = t.createSink(v -> count[0]++);
      t.connect(x, y, null);
      t.connect(y, z, null);
      t.connect(z, sink, null);
      int injected = 0;
      for (int step = 0; step < 12000; step++) {
        if (t.insert(x, injected)) injected++;
        t.step();
        assertEquals(
            injected, count[0] + a.carriers().size() + b.carriers().size() + c.carriers().size());
      }
      assertTrue(count[0] > 11000 * tier / 24, "curved path lost throughput");
    }
  }

  @Test
  void undergroundTransitCannotTeleport() {
    var lane = new BeltLane<Integer>(960);
    var t = new BeltTransport<Integer>();
    var n = t.create(lane, 1, c -> {});
    int[] done = {0};
    t.connect(n, t.createSink(v -> done[0]++), null);
    t.insert(n, 1);
    for (int step = 0; step < 959; step++) t.step();
    assertEquals(0, done[0]);
    t.step();
    assertEquals(1, done[0]);
  }

  @Test
  void resizeCannotDiscardOrOverlapCarriers() {
    var lane = new BeltLane<Integer>();
    for (int u : new int[] {96, 72, 48, 24}) lane.restore(u, u / 96.0);
    assertFalse(lane.canResize(42));
    assertThrows(IllegalStateException.class, () -> lane.resize(42));
    assertEquals(4, lane.carriers().size());
    lane.resize(136);
    assertEquals(136, lane.carriers().getFirst().positionUnits());
    assertEquals(64, lane.carriers().getLast().positionUnits());
  }

  @Test
  void variableLengthPackedRingMovesWithoutCreatingGapsOrDuplicates() {
    for (int tier = 1; tier <= 3; tier++) {
      int[] lengths = {42, 109, 136, 193}; // 480 units, exactly twenty 24-unit gaps.
      var t = new BeltTransport<Integer>();
      var lanes = new java.util.ArrayList<BeltLane<Integer>>();
      var nodes = new java.util.ArrayList<BeltTransport.Node<Integer>>();
      int offset = 0;
      for (int length : lengths) {
        var l = new BeltLane<Integer>(length);
        for (int u = length; u >= 0; u--)
          if ((offset + u) % 24 == 0 && u > 0) l.restore((offset + u) % 480, (double) u / length);
        lanes.add(l);
        nodes.add(t.create(l, tier, c -> {}));
        offset += length;
      }
      for (int i = 0; i < nodes.size(); i++)
        t.connect(nodes.get(i), nodes.get((i + 1) % nodes.size()), null);
      for (var n : nodes) t.changedExternally(n);
      for (int step = 0; step < 3000; step++) {
        t.step();
        var ids = new java.util.HashSet<Integer>();
        for (var l : lanes) {
          int prev = Integer.MAX_VALUE;
          for (var c : l.carriers()) {
            assertTrue(c.positionUnits() >= 0 && c.positionUnits() <= l.length());
            if (prev != Integer.MAX_VALUE) assertTrue(prev - c.positionUnits() >= 24);
            prev = c.positionUnits();
            assertTrue(ids.add(c.value));
          }
        }
        assertEquals(20, ids.size());
      }
      assertTrue(t.transfers() > tier * 240, "packed metric ring stalled");
    }
  }

  @Test
  void liftAndUndergroundCheckpointsPreserveExactMetricPositions() {
    for (var route :
        java.util.List.of(
            new BeltRoute(BeltRoute.Shape.LIFT, -16, 3),
            new BeltRoute(BeltRoute.Shape.UNDERGROUND, 9, 0))) {
      var lane = new BeltLane<Integer>(route.length(0));
      for (int i = 0; i < lane.capacity(); i++)
        lane.restore(i, (double) (lane.length() - i * 24) / lane.length());
      var restored = new BeltLane<Integer>(lane.length());
      for (var c : lane.carriers())
        restored.restore(c.value, (double) c.positionUnits() / lane.length());
      assertEquals(lane.carriers().size(), restored.carriers().size());
      for (int i = 0; i < lane.carriers().size(); i++)
        assertEquals(
            lane.carriers().get(i).positionUnits(), restored.carriers().get(i).positionUnits());
    }
  }

  @Test
  void curveAndRotatingLiftEndpointsKeepLaneIdentity() {
    var left = new BeltRoute(BeltRoute.Shape.LEFT, 0, 0);
    assertEquals(0, left.point(0, 0).x(), 1e-8);
    assertEquals(.28, left.point(0, 0).z(), 1e-8);
    assertEquals(.28, left.point(0, left.length(0)).x(), 1e-8);
    for (int turn = 0; turn < 4; turn++) {
      var route = new BeltRoute(BeltRoute.Shape.LIFT, 4, turn);
      var a = route.point(0, route.length(0));
      var b = route.point(1, route.length(1));
      double x = (a.x() + b.x()) / 2, z = (a.z() + b.z()) / 2;
      assertEquals(.5 + .5 * Math.sin(turn * Math.PI / 2), x, 1e-8);
      assertEquals(.5 - .5 * Math.cos(turn * Math.PI / 2), z, 1e-8);
      assertEquals(.44, a.distance(b), 1e-8);
    }
  }

  @Test
  void tenThousandMixedSegmentsConserveBothLanesAcrossTopologyChanges() {
    int count = 20000;
    var t = new BeltTransport<Integer>();
    var nodes = new java.util.ArrayList<BeltTransport.Node<Integer>>(count);
    int[] lengths = {96, 42, 109, 136, 576, 960};
    for (int i = 0; i < count; i++) {
      var lane = new BeltLane<Integer>(lengths[i % lengths.length]);
      lane.restore(i, 1);
      nodes.add(t.create(lane, 1 + (i / 2) % 3, c -> {}));
    }
    for (int i = 0; i < count; i++) t.connect(nodes.get(i), nodes.get((i + 2) % count), null);
    for (var n : nodes) t.changedExternally(n);
    for (int step = 0; step < 1350; step++) {
      if (step == 200 || step == 400)
        for (int i = 0; i < count; i += 137) t.setAvailable(nodes.get(i), step == 400);
      t.step();
      if (step % 270 == 0) {
        var seen = new boolean[count];
        int total = 0;
        for (var n : nodes)
          for (var c : n.lane().carriers()) {
            assertFalse(seen[c.value]);
            seen[c.value] = true;
            total++;
            assertTrue(c.positionUnits() >= 0 && c.positionUnits() <= n.lane().length());
          }
        assertEquals(count, total);
      }
    }
    assertTrue(t.transfers() > count, "large mixed network did not run");
  }
}
