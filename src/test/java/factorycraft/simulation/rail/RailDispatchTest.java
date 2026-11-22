package factorycraft.simulation.rail;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RailDispatchTest {
  private final UUID origin = UUID.randomUUID(),
      target = UUID.randomUUID(),
      train = UUID.randomUUID();

  private RailStopSlots slots() {
    var s = new RailStopSlots();
    s.put(new RailStopSlots.Stop(origin, "team", "Load", true, 1, 200));
    s.put(new RailStopSlots.Stop(target, "team", "Unload", true, 1, 50));
    assertTrue(s.acquire(train, origin));
    return s;
  }

  @Test
  void readyDepartureCountsUntilActualMovementAndCanOwnOneIncomingSlotToo() {
    var s = slots();
    assertTrue(s.depart(train, origin));
    assertNull(s.destination(train));
    assertEquals(origin, s.origin(train));
    assertFalse(s.available(origin, UUID.randomUUID()));
    assertTrue(s.acquire(train, target));
    assertEquals(1, s.count(origin));
    assertEquals(1, s.count(target));
    assertTrue(s.acquire(train, target));
    s.releaseOrigin(train);
    s.releaseOrigin(train);
    assertEquals(0, s.count(origin));
    assertEquals(1, s.count(target));
    assertEquals(target, s.destination(train));
  }

  @Test
  void fullTargetDoesNotSurrenderOriginOrCreateAnotherIncomingLease() {
    var s = slots();
    s.depart(train, origin);
    var other = UUID.randomUUID();
    assertTrue(s.acquire(other, target));
    assertFalse(s.acquire(train, target));
    assertEquals(origin, s.origin(train));
    assertNull(s.destination(train));
    assertEquals(Set.of(train), s.trains(origin));
    assertEquals(Set.of(other), s.trains(target));
    s.release(train);
    assertEquals(0, s.count(origin));
    assertEquals(1, s.count(target));
  }

  @Test
  void singleStopLoopCountsTheSamePhysicalTrainOnlyOnce() {
    var s = slots();
    s.depart(train, origin);
    s.put(new RailStopSlots.Stop(origin, "team", "Load", false, 0, 200));
    assertTrue(s.acquire(train, origin));
    assertEquals(1, s.count(origin));
    assertTrue(s.depart(train, origin));
    assertEquals(1, s.count(origin));
    s.release(train);
    assertEquals(0, s.count(origin));
  }

  @Test
  void renameRetainsPhysicalDepartureButInvalidatesAnApproachingDestination() {
    var s = slots();
    s.depart(train, origin);
    s.acquire(train, target);
    assertTrue(
        s.put(new RailStopSlots.Stop(origin, "team", "Renamed source", true, 1, 255)).isEmpty());
    assertEquals(origin, s.origin(train));
    assertEquals(target, s.destination(train));
    assertEquals(
        Set.of(train),
        s.put(new RailStopSlots.Stop(target, "team", "Renamed target", true, 1, 50)));
    assertEquals(origin, s.origin(train));
    assertNull(s.destination(train));
    assertEquals(1, s.count(origin));
  }

  @Test
  void deletingOneOfTwoLeasesNeverErasesTheOther() {
    for (boolean removeOrigin : new boolean[] {true, false}) {
      var s = slots();
      s.depart(train, origin);
      s.acquire(train, target);
      assertEquals(Set.of(train), s.remove(removeOrigin ? origin : target));
      assertEquals(removeOrigin ? null : origin, s.origin(train));
      assertEquals(removeOrigin ? target : null, s.destination(train));
      assertEquals(1, s.count(removeOrigin ? target : origin));
    }
  }

  @Test
  void independentRestorationRetainsTwoRealCommitmentsDespiteReducedLimits() {
    var s = slots();
    s.release(train);
    s.put(new RailStopSlots.Stop(origin, "team", "Load", false, 0, 200));
    s.put(new RailStopSlots.Stop(target, "team", "Unload", false, 0, 50));
    assertTrue(s.restoreOrigin(train, origin));
    assertFalse(s.restoreOrigin(train, origin));
    assertTrue(s.restore(train, target));
    assertFalse(s.restore(train, target));
    assertEquals(1, s.count(origin));
    assertEquals(1, s.count(target));
    assertFalse(s.available(target, UUID.randomUUID()));
  }

  private RailDispatchQueue.Request request(UUID id, int priority, long order, Set<UUID> targets) {
    return new RailDispatchQueue.Request(
        id, "team", "Unload", origin, priority, order, targets, false);
  }

  @Test
  void completedShortSearchCannotOvertakeHigherPriorityUnfinishedLongSearch() {
    var q = new RailDispatchQueue();
    var low = UUID.randomUUID();
    q.put(request(low, 20, 1, Set.of(target)));
    q.put(request(train, 200, 2, Set.of(target)));
    q.resolve(low, target);
    assertFalse(q.first(low, target));
    q.resolve(train, target);
    assertTrue(q.first(train, target));
    q.remove(train);
    assertTrue(q.first(low, target));
  }

  @Test
  void resolvingHigherSearchToDifferentStopUnblocksIndependentDestination() {
    var q = new RailDispatchQueue();
    var other = UUID.randomUUID();
    var low = UUID.randomUUID();
    q.put(request(train, 255, 2, Set.of(target, other)));
    q.put(request(low, 10, 1, Set.of(target)));
    assertFalse(q.first(low, target));
    q.resolve(train, other);
    assertTrue(q.first(low, target));
    assertTrue(q.first(train, other));
  }

  @Test
  void priorityChangesAndStableSavedOrderDoNotDependOnInsertionOrSearchCompletionOrder() {
    var q = new RailDispatchQueue();
    var later = UUID.randomUUID();
    q.put(request(later, 50, 2, Set.of(target)));
    q.put(request(train, 50, 1, Set.of(target)));
    assertTrue(q.first(train, target));
    var separateOrigin = UUID.randomUUID();
    q.put(
        new RailDispatchQueue.Request(
            later, "team", "Unload", separateOrigin, 50, 2, Set.of(target), false));
    q.priority(separateOrigin, 255);
    assertTrue(q.first(later, target));
    q.remove(later); // unloaded, no path, manual or retired: cannot keep blocking
    assertTrue(q.first(train, target));
    q.remove(train);
    assertFalse(q.first(train, target));
  }

  @Test
  void namedUpdatesAreScopedAndDoNotReexpandAnAlreadyResolvedSearch() {
    var q = new RailDispatchQueue();
    var other = UUID.randomUUID();
    q.put(request(train, 200, 1, Set.of(target, other)));
    q.resolve(train, target);
    q.targets("other team", "Unload", Set.of(other));
    assertEquals(Set.of(target), q.get(train).targets());
    q.targets("team", "Unload", Set.of(target, other));
    assertEquals(Set.of(target), q.get(train).targets());
    q.targets("team", "Unload", Set.of(other));
    assertTrue(q.get(train).targets().isEmpty());
    assertFalse(q.first(train, target));
    assertThrows(IllegalArgumentException.class, () -> q.resolve(train, other));
  }
}
