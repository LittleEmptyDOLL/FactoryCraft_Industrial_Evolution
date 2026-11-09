package factorycraft.simulation;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.belts.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BeltGateTest {
  @Test
  void pickingUpFrozenApproachWakesWaitingBranchWithoutEnablingStoppedLane() {
    var t = new BeltTransport<Integer>();
    var delivered = new ArrayList<Integer>();
    var stopped = t.create(new BeltLane<Integer>(), 1, c -> {});
    var waiting = t.create(new BeltLane<Integer>(), 1, c -> {});
    var merge = t.create(new BeltLane<Integer>(), 1, c -> {});
    t.connect(merge, t.createSink(delivered::add), null);
    stopped.lane().restore(1, 80.0 / 96);
    stopped.restoreCommittedOutput(1);
    t.connect(stopped, merge, null);
    t.connect(waiting, merge, null);
    t.setEnabled(stopped, false);
    t.insert(waiting, 2);
    for (int i = 0; i < 200; i++) t.step();
    assertTrue(delivered.isEmpty());
    assertEquals(1, stopped.lane().remove(0));
    t.changedExternally(stopped);
    for (int i = 0; i < 300; i++) t.step();
    assertEquals(List.of(2), delivered);
    assertFalse(t.canInsert(stopped));
  }

  @Test
  void frozenApproachRetainsItsClaimWhileSleepingAndAfterPolicyChanges() {
    for (int tier = 1; tier <= 3; tier++) {
      var t = new BeltTransport<Integer>();
      var delivered = new ArrayList<Integer>();
      var a = t.create(new BeltLane<Integer>(), tier, c -> {});
      var b = t.create(new BeltLane<Integer>(), tier, c -> {});
      var merge = t.create(new BeltLane<Integer>(), tier, c -> {});
      t.connect(merge, t.createSink(delivered::add), null);
      a.lane().restore(1, 80.0 / 96);
      a.restoreCommittedOutput(1);
      int[] leaked = {0};
      t.connect(a, merge, t.createSink(v -> leaked[0]++));
      t.connect(b, merge, null);
      t.setEnabled(a, false);
      assertTrue(t.insert(b, 2));
      t.configure(a, new SplitterPolicy<>(2, v -> true), 0);
      for (int step = 0; step < 200; step++) t.step();
      assertEquals(80, a.lane().carriers().getFirst().positionUnits());
      assertTrue(
          b.lane().carriers().getFirst().positionUnits() <= 72,
          "sleeping paused approach lost claim");
      assertEquals(1, a.committedOutput());
      assertTrue(delivered.isEmpty());
      assertFalse(t.canInsert(a));
      t.setEnabled(a, true);
      for (int i = 0; i < 400; i++) t.step();
      assertEquals(List.of(1, 2), delivered);
      assertEquals(0, leaked[0], "stopped committed route was replaced by new filter");
    }
  }

  @Test
  void fullRingStopsWithoutBackwardMotionOrLossAndResumes() {
    for (int tier = 1; tier <= 3; tier++) {
      var t = new BeltTransport<Integer>();
      var ring = new ArrayList<BeltTransport.Node<Integer>>();
      for (int i = 0; i < 4; i++) {
        var n = t.create(new BeltLane<Integer>(), tier, c -> {});
        for (int j = 0; j < 4; j++) n.lane().restore(i * 4 + j, (72 - j * 24) / 96.0);
        ring.add(n);
      }
      for (int i = 0; i < 4; i++) {
        t.connect(ring.get(i), ring.get((i + 1) % 4), null);
        t.changedExternally(ring.get(i));
      }
      t.setEnabled(ring.get(0), false);
      for (int step = 0; step < 200; step++) t.step();
      assertEquals(
          List.of(72, 48, 24, 0),
          ring.get(0).lane().carriers().stream().map(BeltLane.Carrier::positionUnits).toList());
      assertEquals(0, t.transfers());
      t.setEnabled(ring.get(0), true);
      for (int step = 0; step < 400; step++) t.step();
      assertTrue(t.transfers() > 30);
      var ids = new HashSet<Integer>();
      for (var n : ring) for (var c : n.lane().carriers()) assertTrue(ids.add(c.value));
      assertEquals(16, ids.size());
    }
  }

  @Test
  void allMixedTiersConserveAndDrainThroughRepeatedFourLaneGates() {
    for (int inTier = 1; inTier <= 3; inTier++)
      for (int tier = 1; tier <= 3; tier++)
        for (int outTier = 1; outTier <= 3; outTier++) {
          var t = new BeltTransport<Integer>();
          var inputs = new ArrayList<BeltTransport.Node<Integer>>();
          var splits = new ArrayList<BeltTransport.Node<Integer>>();
          var outputs = new ArrayList<BeltTransport.Node<Integer>>();
          var seen = new HashSet<Integer>();
          for (int i = 0; i < 4; i++) {
            inputs.add(t.create(new BeltLane<>(), inTier, c -> {}));
            splits.add(t.create(new BeltLane<>(), tier, c -> {}));
            outputs.add(t.create(new BeltLane<>(), outTier, c -> {}));
            final int track = i / 2;
            t.connect(
                outputs.get(i),
                t.createSink(
                    v -> {
                      assertEquals(track, v % 2);
                      assertTrue(seen.add(v));
                    }),
                null);
          }
          for (int i = 0; i < 4; i++) {
            t.connect(inputs.get(i), splits.get(i), null);
            t.connect(splits.get(i), outputs.get(i % 2), outputs.get(2 + i % 2));
            t.configure(splits.get(i), new SplitterPolicy<>(1, v -> v % 2 == 0), i < 2 ? 1 : 0);
          }
          int created = 0;
          var all = new ArrayList<>(inputs);
          all.addAll(splits);
          all.addAll(outputs);
          for (int step = 0; step < 7000; step++) {
            boolean enabled = step >= 4500 || step % 331 < 173;
            for (var n : splits) t.setEnabled(n, enabled);
            var before = new ArrayList<List<Integer>>();
            for (var n : splits)
              before.add(
                  n.lane().carriers().stream().map(BeltLane.Carrier::positionUnits).toList());
            if (step < 4500) for (var n : inputs) if (t.insert(n, created)) created++;
            t.step();
            if (!enabled)
              for (int i = 0; i < 4; i++)
                assertEquals(
                    before.get(i),
                    splits.get(i).lane().carriers().stream()
                        .map(BeltLane.Carrier::positionUnits)
                        .toList(),
                    "closed lane changed");
            assertEquals(
                created,
                seen.size() + all.stream().mapToInt(n -> n.lane().carriers().size()).sum());
            for (var n : all) {
              int last = n.lane().length() + 24;
              for (var c : n.lane().carriers()) {
                assertTrue(last - c.positionUnits() >= 24);
                last = c.positionUnits();
              }
              if (!n.lane().carriers().isEmpty()) {
                var destinations =
                    inputs.contains(n)
                        ? List.of(splits.get(inputs.indexOf(n)))
                        : splits.contains(n)
                            ? List.of(
                                outputs.get(splits.indexOf(n) % 2),
                                outputs.get(2 + splits.indexOf(n) % 2))
                            : List.<BeltTransport.Node<Integer>>of();
                int choice = n.committedOutput();
                if (choice > 0 && choice <= destinations.size()) {
                  var dest = destinations.get(choice - 1);
                  if (!dest.lane().carriers().isEmpty())
                    assertTrue(
                        n.lane().length()
                                - n.lane().carriers().getFirst().positionUnits()
                                + dest.lane().carriers().getLast().positionUnits()
                            >= 24,
                        "cross-edge overlap");
                }
              }
            }
          }
          assertEquals(created, seen.size(), "gated line failed to drain");
        }
  }
}
