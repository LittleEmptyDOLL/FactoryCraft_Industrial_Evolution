package factorycraft.simulation;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.belts.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TieredSplitterTest {
  @Test
  void allTierCombinationsSortAndConserveThroughJamsAndInactiveOutputs() {
    for (int inputTier = 1; inputTier <= 3; inputTier++)
      for (int splitTier = 1; splitTier <= 3; splitTier++)
        for (int outputTier = 1; outputTier <= 3; outputTier++) {
          var t = new BeltTransport<Integer>();
          var inputs = new ArrayList<BeltTransport.Node<Integer>>();
          var splits = new ArrayList<BeltTransport.Node<Integer>>();
          var outputs = new ArrayList<BeltTransport.Node<Integer>>();
          var seen = new HashSet<Integer>();
          for (int i = 0; i < 4; i++) {
            final int track = i / 2;
            inputs.add(t.create(new BeltLane<>(), inputTier, c -> {}));
            splits.add(t.create(new BeltLane<>(), splitTier, c -> {}));
            outputs.add(t.create(new BeltLane<>(), outputTier, c -> {}));
            t.connect(
                outputs.get(i),
                t.createSink(
                    v -> {
                      assertEquals(track, v % 2);
                      assertTrue(seen.add(v), "duplicate carrier");
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
          for (int step = 0; step < 5000; step++) {
            if (step == 700 || step == 2300)
              for (int i = 0; i < 2; i++) t.setAvailable(outputs.get(i), false);
            if (step == 1100 || step == 2700)
              for (int i = 0; i < 2; i++) t.setAvailable(outputs.get(i), true);
            if (step < 3500) for (var n : inputs) if (t.insert(n, created)) created++;
            t.step();
            assertEquals(
                created,
                seen.size() + all.stream().mapToInt(n -> n.lane().carriers().size()).sum());
            for (var n : all) {
              int previous = n.lane().length() + 24;
              for (var c : n.lane().carriers()) {
                assertTrue(previous - c.positionUnits() >= 24, "overlapping carriers");
                previous = c.positionUnits();
              }
            }
          }
          assertEquals(created, seen.size(), "line failed to drain after backpressure");
        }
  }

  @Test
  void allTiersGiveNewReservationsToPriorityInputWithoutPreemptingCommittedItems() {
    for (int tier = 1; tier <= 3; tier++)
      for (boolean committed : new boolean[] {false, true}) {
        var t = new BeltTransport<Integer>();
        var low = t.create(new BeltLane<>(), tier, c -> {});
        var high = t.create(new BeltLane<>(), tier, c -> {});
        var target = t.create(new BeltLane<>(), tier, c -> {});
        var result = new ArrayList<Integer>();
        t.connect(low, target, null);
        t.connect(high, target, null);
        t.connect(target, t.createSink(result::add), null);
        t.configure(high, null, 1);
        low.lane().restore(1, committed ? .9 : .5);
        high.lane().restore(2, committed ? .7 : .5);
        t.changedExternally(low);
        t.changedExternally(high);
        for (int step = 0; step < 400; step++) t.step();
        assertEquals(committed ? List.of(1, 2) : List.of(2, 1), result);
      }
  }

  @Test
  void eachTierRespectsOutputPreferenceAndFallback() {
    for (int tier = 1; tier <= 3; tier++) {
      var t = new BeltTransport<Integer>();
      var n = t.create(new BeltLane<>(), tier, c -> {});
      var preferred = t.create(new BeltLane<>(), tier, c -> {});
      var left = new ArrayList<Integer>();
      var right = new ArrayList<Integer>();
      t.connect(preferred, t.createSink(left::add), null);
      t.connect(n, preferred, t.createSink(right::add));
      t.configure(n, new SplitterPolicy<>(1, null), 0);
      assertTrue(t.insert(n, 1));
      for (int i = 0; i < 250; i++) t.step();
      assertEquals(List.of(1), left);
      assertTrue(right.isEmpty());
      t.setAvailable(preferred, false);
      assertTrue(t.insert(n, 2));
      for (int i = 0; i < 250; i++) t.step();
      assertEquals(List.of(2), right);
    }
  }
}
