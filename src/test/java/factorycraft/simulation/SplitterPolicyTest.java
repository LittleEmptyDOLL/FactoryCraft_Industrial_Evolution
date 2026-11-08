package factorycraft.simulation;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.belts.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SplitterPolicyTest {
  @Test
  void filterSortsBothInputsWithoutCopies() {
    var t = new BeltTransport<Integer>();
    var left = new ArrayList<Integer>();
    var right = new ArrayList<Integer>();
    var a = t.create(new BeltLane<Integer>(), 1, c -> {});
    var b = t.create(new BeltLane<Integer>(), 1, c -> {});
    var l = t.createSink(left::add);
    var r = t.createSink(right::add);
    t.connect(a, l, r);
    t.connect(b, l, r);
    var policy = new SplitterPolicy<Integer>(1, v -> v % 2 == 0);
    t.configure(a, policy, 0);
    t.configure(b, policy, 0);
    int next = 0;
    for (int step = 0; step < 15000; step++) {
      if (t.insert(a, next)) next++;
      if (t.insert(b, next)) next++;
      t.step();
    }
    for (int i = 0; i < 1000; i++) t.step();
    var all = new HashSet<Integer>();
    for (int v : left) {
      assertTrue(v % 2 == 0);
      assertTrue(all.add(v));
    }
    for (int v : right) {
      assertTrue(v % 2 == 1);
      assertTrue(all.add(v));
    }
    assertEquals(next, all.size());
    assertTrue(next > 900);
  }

  @Test
  void blockedFilteredOutputCannotLeakIntoOtherExit() {
    var t = new BeltTransport<Integer>();
    var lane = new BeltLane<Integer>();
    var source = t.create(lane, 3, c -> {});
    var blocked = t.create(new BeltLane<Integer>(), 3, c -> {});
    int[] leaked = {0};
    t.connect(source, blocked, t.createSink(v -> leaked[0]++));
    t.configure(source, new SplitterPolicy<>(1, v -> true), 0);
    t.setAvailable(blocked, false);
    t.insert(source, 7);
    for (int i = 0; i < 500; i++) t.step();
    assertEquals(0, leaked[0]);
    assertEquals(1, lane.carriers().size());
    assertEquals(96, lane.carriers().getFirst().positionUnits());
  }

  @Test
  void outputPriorityUsesPreferredExitAndFallsBackWhenBlocked() {
    var t = new BeltTransport<Integer>();
    var lane = new BeltLane<Integer>();
    var source = t.create(lane, 2, c -> {});
    var preferred = t.create(new BeltLane<Integer>(), 2, c -> {});
    int[] count = {0};
    t.connect(source, preferred, t.createSink(v -> count[0]++));
    t.configure(source, new SplitterPolicy<>(1, null), 0);
    t.insert(source, 1);
    for (int i = 0; i < 120; i++) t.step();
    assertEquals(1, preferred.lane().carriers().size());
    assertEquals(0, count[0]);
    t.setAvailable(preferred, false);
    t.insert(source, 2);
    for (int i = 0; i < 120; i++) t.step();
    assertEquals(1, count[0]);
  }

  @Test
  void preferredInputWinsContestedReservationsThenLowInputResumes() {
    var t = new BeltTransport<Integer>();
    var low = t.create(new BeltLane<Integer>(), 1, c -> {});
    var high = t.create(new BeltLane<Integer>(), 1, c -> {});
    var result = new ArrayList<Integer>();
    var sink = t.create(new BeltLane<Integer>(), 1, c -> {});
    t.connect(sink, t.createSink(result::add), null);
    t.connect(low, sink, null);
    t.connect(high, sink, null);
    t.configure(high, null, 1);
    low.lane().restore(1, .5);
    high.lane().restore(2, .5);
    t.changedExternally(low);
    t.changedExternally(high);
    for (int i = 0; i < 200; i++) t.step();
    assertEquals(List.of(2, 1), result);
  }

  @Test
  void savedCommittedExitSurvivesChangedFilter() {
    var t = new BeltTransport<Integer>();
    var source = t.create(new BeltLane<Integer>(), 1, c -> {});
    source.lane().restore(7, .9);
    source.restoreCommittedOutput(2);
    var first = new ArrayList<Integer>();
    var second = new ArrayList<Integer>();
    t.connect(source, t.createSink(first::add), t.createSink(second::add));
    t.configure(source, new SplitterPolicy<>(1, v -> true), 0);
    t.changedExternally(source);
    for (int i = 0; i < 100; i++) t.step();
    assertEquals(List.of(7), second);
    assertTrue(first.isEmpty());
    assertEquals(0, source.committedOutput());
    t.insert(source, 8);
    for (int i = 0; i < 100; i++) t.step();
    assertEquals(List.of(8), first);
  }

  @Test
  void policyRejectsImpossibleModes() {
    assertThrows(IllegalArgumentException.class, () -> new SplitterPolicy<Integer>(0, v -> true));
    assertThrows(IllegalArgumentException.class, () -> new SplitterPolicy<Integer>(3, null));
  }

  @Test
  void inputPriorityCannotStealAnAlreadyCommittedApproach() {
    var t = new BeltTransport<Integer>();
    var low = t.create(new BeltLane<Integer>(), 1, c -> {});
    var high = t.create(new BeltLane<Integer>(), 1, c -> {});
    var merged = t.create(new BeltLane<Integer>(), 1, c -> {});
    var result = new ArrayList<Integer>();
    t.connect(low, merged, null);
    t.connect(high, merged, null);
    t.connect(merged, t.createSink(result::add), null);
    t.configure(high, null, 1);
    low.lane().restore(1, .9);
    high.lane().restore(2, .7);
    t.changedExternally(low);
    t.changedExternally(high);
    int previous = low.lane().carriers().getFirst().positionUnits();
    for (int step = 0; step < 240; step++) {
      t.step();
      if (!low.lane().carriers().isEmpty()) {
        int current = low.lane().carriers().getFirst().positionUnits();
        assertTrue(current >= previous, "priority moved a committed item backwards");
        previous = current;
      }
    }
    assertEquals(List.of(1, 2), result);
  }

  @Test
  void liveFilterChangeFinishesReservedExitBeforeApplyingNextItem() {
    var t = new BeltTransport<Integer>();
    var source = t.create(new BeltLane<Integer>(), 1, c -> {});
    var l = new ArrayList<Integer>();
    var r = new ArrayList<Integer>();
    t.connect(source, t.createSink(l::add), t.createSink(r::add));
    t.configure(source, new SplitterPolicy<>(2, null), 0);
    t.insert(source, 7);
    for (int i = 0; i < 80; i++) t.step();
    assertEquals(2, source.committedOutput());
    int position = source.lane().carriers().getFirst().positionUnits();
    t.configure(source, new SplitterPolicy<>(1, v -> true), 0);
    for (int i = 0; i < 30; i++) {
      t.step();
      if (!source.lane().carriers().isEmpty())
        assertTrue(source.lane().carriers().getFirst().positionUnits() >= position);
    }
    assertEquals(List.of(7), r);
    assertTrue(l.isEmpty());
    t.insert(source, 8);
    for (int i = 0; i < 100; i++) t.step();
    assertEquals(List.of(8), l);
  }
}
