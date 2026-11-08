package factorycraft.simulation.belts;

import static factorycraft.simulation.belts.BeltLane.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Server-independent transport kernel. Nine simultaneous microsteps per 20 Hz game tick: tier 1/2/3
 * moves 1/2/3 of 96 units per step, giving 15/30/45 items/s across two lanes. Constraints only
 * decrease integral projections; each carrier loses at most three units per ordinary step. Worklist
 * propagation handles chains, merges and packed cycles.
 */
public final class BeltTransport<T> {
  public static final int STEPS_PER_TICK = 9;

  public interface Routing<T> {
    int outputs(T value);

    int preference();
  }

  @FunctionalInterface
  public interface ChangeListener {
    void changed(boolean contentsChanged);
  }

  public static final class Node<T> {
    private final BeltTransport<T> owner;
    private final BeltLane<T> lane;
    private final int speed;
    private final ChangeListener listener;
    private final Consumer<T> sink;
    private final ArrayList<Node<T>> inputs = new ArrayList<>(4);
    private int[] projected;
    private Node<T> primary, secondary, target, winner;
    private Carrier<T> outgoing;
    private long prepared = -1, reservation = -1;
    private int count, cursor, inputPriority, savedOutput, lastOutput;
    private Routing<T> routing;
    private boolean available = true;
    private boolean alternate, scheduled, removed, canExit, queued, dirty, contentDirty;

    private Node(
        BeltTransport<T> owner,
        BeltLane<T> lane,
        int speed,
        ChangeListener listener,
        Consumer<T> sink) {
      this.owner = owner;
      this.lane = lane;
      this.speed = speed;
      this.listener = listener;
      this.sink = sink;
      this.projected = new int[lane.capacity()];
    }

    public int committedOutput() {
      if (lane.count() == 0 || lane.at(0).units <= lane.length() - SPACING) return 0;
      return savedOutput != 0
          ? savedOutput
          : target == primary && primary != null
              ? 1
              : target == secondary && secondary != null ? 2 : lastOutput;
    }

    public void restoreCommittedOutput(int output) {
      if (output < 0 || output > 2) throw new IllegalArgumentException("Invalid committed output");
      savedOutput = output;
      lastOutput = output;
    }

    public BeltLane<T> lane() {
      return lane;
    }

    public boolean isActive() {
      return scheduled && available && !removed;
    }

    public boolean prefersSecondary() {
      return alternate;
    }

    public void restorePreference(boolean value) {
      alternate = value;
    }
  }

  private final ArrayList<Node<T>> active = new ArrayList<>(),
      pending = new ArrayList<>(),
      dirty = new ArrayList<>();
  private final ArrayDeque<Node<T>> queue = new ArrayDeque<>();
  private long step, visits, transfers;
  private final boolean singleRouteFastPath;

  public BeltTransport() {
    this(true);
  }

  BeltTransport(boolean singleRouteFastPath) {
    this.singleRouteFastPath = singleRouteFastPath;
  }

  public Node<T> create(BeltLane<T> lane, int tier, ChangeListener listener) {
    if (tier < 1 || tier > 3) throw new IllegalArgumentException("Unknown belt tier");
    return new Node<>(
        this, Objects.requireNonNull(lane), tier, Objects.requireNonNull(listener), null);
  }

  /** A terminal for independent benchmarks/tests. World transport uses actual connected lanes. */
  public Node<T> createSink(Consumer<T> consumer) {
    return new Node<>(this, new BeltLane<>(), 0, c -> {}, Objects.requireNonNull(consumer));
  }

  public boolean canResize(Node<T> n, int length) {
    requireOwned(n);
    if (!n.lane.canResize(length)) return false;
    if (n.lane.count() == 0) return true;
    int tail = n.lane.at(n.lane.count() - 1).units + length - n.lane.length();
    for (var input : n.inputs)
      if (input.lane.count() != 0 && input.lane.length() - input.lane.at(0).units + tail < SPACING)
        return false;
    return true;
  }

  public void geometryChanged(Node<T> node) {
    requireOwned(node);
    if (node.projected.length != node.lane.capacity())
      node.projected = new int[node.lane.capacity()];
    changedExternally(node);
  }

  public void configure(Node<T> node, Routing<T> routing, int inputPriority) {
    requireOwned(node);
    if (inputPriority < 0 || inputPriority > 1)
      throw new IllegalArgumentException("Invalid input priority");
    node.routing = routing;
    node.inputPriority = inputPriority;
    changedExternally(node);
    if (node.primary != null) for (var input : node.primary.inputs) activate(input);
    if (node.secondary != null) for (var input : node.secondary.inputs) activate(input);
  }

  public void connect(Node<T> node, Node<T> primary, Node<T> secondary) {
    requireOwned(node);
    if (primary != null) requireOwned(primary);
    if (secondary != null) requireOwned(secondary);
    if (node.primary == primary && node.secondary == secondary) return;
    detachOutputs(node);
    node.primary = primary;
    node.secondary = secondary;
    int output = node.savedOutput != 0 ? node.savedOutput : node.lastOutput;
    if (output != 0
        && node.lane.count() != 0
        && node.lane.at(0).units > node.lane.length() - SPACING) {
      var target = output == 1 ? primary : secondary;
      if (target != null) {
        node.target = target;
        node.savedOutput = 0;
      }
    }
    if (primary != null && !primary.inputs.contains(node)) primary.inputs.add(node);
    if (secondary != null && !secondary.inputs.contains(node)) secondary.inputs.add(node);
    if (primary != null) for (var input : primary.inputs) activate(input);
    if (secondary != null) for (var input : secondary.inputs) activate(input);
    changedExternally(node);
  }

  private void requireOwned(Node<T> n) {
    if (n.owner != this || n.removed)
      throw new IllegalArgumentException("Foreign or removed belt node");
  }

  private void detachOutputs(Node<T> n) {
    if (n.primary != null) n.primary.inputs.remove(n);
    if (n.secondary != null) n.secondary.inputs.remove(n);
    n.primary = null;
    n.secondary = null;
  }

  /** Inactive multiblock tracks retain their exact positions and ownership. */
  public void setAvailable(Node<T> node, boolean available) {
    requireOwned(node);
    if (node.available == available) return;
    node.available = available;
    changedExternally(node);
  }

  public void remove(Node<T> n) {
    if (n == null || n.removed) return;
    requireOwned(n);
    while (!n.inputs.isEmpty()) {
      var source = n.inputs.getLast();
      connect(
          source,
          source.primary == n ? null : source.primary,
          source.secondary == n ? null : source.secondary);
    }
    detachOutputs(n);
    n.removed = true;
  }

  private void activate(Node<T> n) {
    if (n == null
        || n.removed
        || n.sink != null
        || !n.available
        || n.scheduled
        || n.lane.count() == 0) return;
    n.scheduled = true;
    pending.add(n);
  }

  public void changedExternally(Node<T> n) {
    if (n == null || n.removed) return;
    activate(n);
    for (var input : n.inputs) activate(input);
  }

  /**
   * External injection must also respect headway on incoming connections. Simulation is read-only.
   */
  public boolean canInsert(Node<T> n) {
    requireOwned(n);
    if (!n.available || !n.lane.canInsert()) return false;
    for (var input : n.inputs)
      if (input.lane.count() != 0 && input.lane.at(0).units > input.lane.length() - SPACING)
        return false;
    return true;
  }

  public boolean insert(Node<T> n, T value) {
    if (!canInsert(n)) return false;
    n.lane.insert(value);
    n.listener.changed(true);
    changedExternally(n);
    return true;
  }

  public int activeLanes() {
    return active.size() + pending.size();
  }

  public long visits() {
    return visits;
  }

  public long transfers() {
    return transfers;
  }

  public void tick() {
    for (int i = 0; i < STEPS_PER_TICK; i++) step();
  }

  public void step() {
    step++;
    for (var node : pending) active.add(node);
    pending.clear();
    // Project every active lane before reading any neighbor's movement.
    for (var n : active) {
      if (n.removed || !n.available) continue;
      n.prepared = step;
      n.count = n.lane.count();
      n.outgoing = null;
      n.queued = false;
      n.canExit = false;
      int limit = n.lane.length() + n.speed;
      for (int i = 0; i < n.count; i++) {
        n.projected[i] = Math.min(limit, n.lane.at(i).units + n.speed);
        limit = n.projected[i] - SPACING;
      }
    }
    for (var n : active) {
      if (n.removed || !n.available || n.count == 0) continue;
      if (singleRouteFastPath && n.secondary == null && n.routing == null) {
        n.target = n.primary;
        if (n.target != null) n.lastOutput = 1;
      } else {
        int mask = n.routing == null ? 3 : n.routing.outputs(n.lane.at(0).value);
        if (mask < 1 || mask > 3) throw new IllegalStateException("Invalid output mask");
        var first = (mask & 1) != 0 ? n.primary : null;
        var second = (mask & 2) != 0 ? n.secondary : null;
        int preference = n.routing == null ? 0 : n.routing.preference();
        Node<T> preferred =
            preference == 2
                ? second
                : preference == 1 ? first : n.alternate && second != null ? second : first;
        Node<T> fallback = preferred == first ? second : first;
        if (mask == 1) {
          preferred = n.primary;
          fallback = null;
        } else if (mask == 2) {
          preferred = n.secondary;
          fallback = null;
        }
        boolean committedApproach =
            n.lane.at(0).units > n.lane.length() - SPACING
                && n.target != null
                && !n.target.removed
                && (n.target == n.primary || n.target == n.secondary);
        boolean finishOldRoute =
            committedApproach
                && (n.target == n.primary && (mask & 1) == 0
                    || n.target == n.secondary && (mask & 2) == 0);
        Node<T> selected =
            approachPossible(n, preferred)
                ? preferred
                : approachPossible(n, fallback) ? fallback : preferred;
        if (n.secondary != null && claimedByOther(selected, n)) {
          var other = selected == first ? second : first;
          if (approachPossible(n, other) && !claimedByOther(other, n)) selected = other;
        }
        if (!finishOldRoute
            && (!committedApproach
                || (n.target != selected
                    && (!approachPossible(n, n.target) || claimedByOther(n.target, n))
                    && safeRetarget(n, selected)))) n.target = selected;
        if (n.target != null) n.lastOutput = n.target == n.primary ? 1 : 2;
      }
      n.canExit =
          n.target != null
              && n.target.available
              && !n.target.removed
              && n.projected[0] >= n.lane.length();
      // Reserve before the junction: losers stop a quarter block before the entry,
      // rather than moving backwards when another branch actually enters.
      if (n.target != null
          && n.target.sink == null
          && (!singleRouteFastPath || n.target.inputs.size() > 1)
          && n.projected[0] > n.lane.length() - SPACING) reserve(n.target, n);
    }
    for (var n : active) enqueue(n);
    while (!queue.isEmpty()) {
      var n = queue.removeFirst();
      n.queued = false;
      if (constrain(n)) {
        for (int i = 0; i < n.inputs.size(); i++) enqueue(n.inputs.get(i));
        if (n.target != null)
          for (int i = 0; i < n.target.inputs.size(); i++) enqueue(n.target.inputs.get(i));
      }
    }
    // Validate every destination before changing ownership anywhere in the network.
    for (var n : active)
      if (!n.removed && n.available && n.count > 0 && n.canExit) validateTransfer(n);
    // First update positions and remove ALL outgoing carriers. Packed cycles remain legal.
    for (var n : active) {
      if (n.removed || !n.available || n.count == 0) continue;
      for (int i = 0; i < n.count; i++) {
        var c = n.lane.at(i);
        if (c.units != n.projected[i]) {
          c.units = n.projected[i];
          mark(n, false);
        }
      }
      if (n.canExit) {
        n.outgoing = n.lane.removeCarrier(0);
        mark(n, true);
      }
    }
    // Then give each reserved target its carrier. Reuse the carrier object across segments.
    for (var n : active)
      if (!n.removed && n.available && n.outgoing != null) {
        var target = n.target;
        if (target.sink != null) target.sink.accept(n.outgoing.value);
        else {
          target.lane.accept(n.outgoing, n.projected[0] - n.lane.length());
          mark(target, true);
          activate(target);
        }
        if (n.secondary != null) n.alternate = target == n.primary;
        if (target.inputs.size() <= 1) target.cursor = 0;
        else {
          int index = target.inputs.indexOf(n);
          target.cursor = (index + 1) % target.inputs.size();
        }
        // The next carrier owns a new routing decision, even when packed
        // within the committed approach quarter of this departing carrier.
        n.outgoing = null;
        n.target = null;
        n.lastOutput = 0;
        n.savedOutput = 0;
        transfers++;
      }
    for (var n : dirty) {
      n.listener.changed(n.contentDirty);
      for (int i = 0; i < n.inputs.size(); i++) activate(n.inputs.get(i));
    }
    // A fully jammed lane sleeps; position/contents/topology changes wake its incoming lanes.
    for (int i = 0; i < active.size(); ) {
      var n = active.get(i);
      visits++;
      if (!n.removed && n.available && n.dirty && n.lane.count() != 0) {
        i++;
        continue;
      }
      n.scheduled = false;
      int last = active.size() - 1;
      active.set(i, active.get(last));
      active.remove(last);
    }
    for (var n : dirty) {
      n.dirty = false;
      n.contentDirty = false;
    }
    dirty.clear();
  }

  private void validateTransfer(Node<T> source) {
    var target = source.target;
    if (target.sink != null) return;
    int remaining = count(target);
    if (target.prepared == step && target.canExit) remaining--;
    int residual = source.projected[0] - source.lane.length();
    if (remaining >= target.lane.capacity()
        || residual < 0
        || residual > source.speed
        || (remaining > 0 && tail(target) - residual < SPACING))
      throw new IllegalStateException(
          "Invalid belt reservation: remaining="
              + remaining
              + ", residual="
              + residual
              + ", sourceSpeed="
              + source.speed
              + ", targetTail="
              + tail(target)
              + ", targetExit="
              + target.canExit
              + ", prepared="
              + (target.prepared == step));
  }

  private void mark(Node<T> n, boolean contents) {
    if (!n.dirty) {
      n.dirty = true;
      dirty.add(n);
    }
    n.contentDirty |= contents;
  }

  private int count(Node<T> n) {
    return n.prepared == step ? n.count : n.lane.count();
  }

  private int tail(Node<T> n) {
    int count = count(n);
    return count == 0
        ? Integer.MAX_VALUE
        : n.prepared == step ? n.projected[count - 1] : n.lane.at(n.lane.count() - 1).units;
  }

  private boolean safeRetarget(Node<T> source, Node<T> target) {
    if (!approachPossible(source, target)) return false;
    if (target.sink != null) return true;
    int tail = tail(target);
    if (tail != Integer.MAX_VALUE
        && source.lane.length() + tail - SPACING < source.lane.at(0).units) return false;
    for (var other : target.inputs)
      if (other != source && other.target == target && count(other) > 0) {
        int front = other.prepared == step ? other.projected[0] : other.lane.at(0).units;
        if (front > other.lane.length() - SPACING) return false;
      }
    return true;
  }

  private boolean claimedByOther(Node<T> target, Node<T> source) {
    return target != null
        && target.sink == null
        && target.reservation == step
        && target.winner != null
        && target.winner != source
        && (target.winner.inputPriority == source.inputPriority
            || priority(source, target.winner) <= 0);
  }

  private int approachSpace(Node<T> source, Node<T> target) {
    if (target.sink != null) return Integer.MAX_VALUE;
    int space = tail(target);
    if (singleRouteFastPath && target.inputs.size() == 1) return space;
    for (int i = 0; i < target.inputs.size(); i++) {
      var other = target.inputs.get(i);
      if (other != source
          && other.available
          && !other.removed
          && other.target == target
          && count(other) > 0
          && other.lane.at(0).units > other.lane.length() - SPACING)
        space =
            Math.min(
                space,
                (other.prepared == step ? other.projected[0] : other.lane.at(0).units)
                    - other.lane.length());
    }
    return space;
  }

  private boolean approachPossible(Node<T> source, Node<T> target) {
    if (target == null || !target.available || target.removed) return false;
    if (target.sink != null) return true;
    int tail = approachSpace(source, target);
    // A route is reserved during the last quarter of the source. Requiring a
    // completely free entry here wastes that approach distance on shifted inputs.
    return tail == Integer.MAX_VALUE
        || source.lane.length() + tail - SPACING >= source.lane.at(0).units + source.speed;
  }

  private void reserve(Node<T> target, Node<T> source) {
    if (target.reservation != step) {
      target.reservation = step;
      target.winner = null;
    }
    if (target.winner == null
        || source.inputPriority != target.winner.inputPriority
            && priority(source, target.winner) > 0
        || source.inputPriority == target.winner.inputPriority
            && rank(target, source) < rank(target, target.winner)) target.winner = source;
  }

  private int priority(Node<T> a, Node<T> b) {
    boolean committedA = a.lane.count() != 0 && a.lane.at(0).units > a.lane.length() - SPACING,
        committedB = b.lane.count() != 0 && b.lane.at(0).units > b.lane.length() - SPACING;
    if (committedA != committedB) return committedA ? 1 : -1;
    return Integer.compare(a.inputPriority, b.inputPriority);
  }

  private int rank(Node<T> target, Node<T> source) {
    int size = target.inputs.size();
    return Math.floorMod(target.inputs.indexOf(source) - target.cursor, Math.max(1, size));
  }

  private void enqueue(Node<T> n) {
    if (n.prepared != step || n.removed || !n.available || n.count == 0 || n.queued) return;
    n.queued = true;
    queue.addLast(n);
  }

  private boolean constrain(Node<T> n) {
    int limit = n.lane.length();
    boolean canExit = false;
    if (n.target != null && n.target.available && !n.target.removed && n.target.sink != null) {
      limit = n.lane.length() + n.speed;
      canExit = n.canExit;
    } else if (n.target != null && n.target.available && !n.target.removed) {
      var target = n.target;
      int tail = tail(target);
      limit =
          tail == Integer.MAX_VALUE ? n.lane.length() + n.speed : n.lane.length() + tail - SPACING;
      var winner =
          singleRouteFastPath && target.inputs.size() == 1
              ? n
              : target.reservation == step ? target.winner : null;
      if (winner != null && winner != n) {
        // Reserve the junction's headway even if this contender never crosses this step.
        limit =
            Math.min(
                limit,
                n.lane.length()
                    + Math.max(0, winner.projected[0] - winner.lane.length())
                    - SPACING);
      }
      canExit =
          n.canExit
              && winner == n
              && limit >= n.lane.length()
              && (count(target) < target.lane.capacity()
                  || (target.prepared == step && target.canExit));
      if (!canExit) limit = Math.min(limit, n.lane.length());
    }
    boolean changed = n.canExit != canExit;
    n.canExit = canExit;
    for (int i = 0; i < n.count; i++) {
      int next = Math.min(n.projected[i], limit);
      if (next != n.projected[i]) {
        n.projected[i] = next;
        changed = true;
      }
      limit = n.projected[i] - SPACING;
    }
    if (n.canExit && n.projected[0] < n.lane.length()) {
      n.canExit = false;
      changed = true;
    }
    return changed;
  }
}
