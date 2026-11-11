package factorycraft.simulation.fluids;

import java.util.Objects;

/** Integer volumes, one immutable fluid identity per cell. Simulation never mutates storage. */
public class FluidCell<K> {
  private final int capacity;
  private final Runnable changed;
  private Runnable observer;
  private K key;
  private int amount;

  public FluidCell(int capacity, Runnable changed) {
    if (capacity < 1) throw new IllegalArgumentException("Nonpositive fluid capacity");
    this.capacity = capacity;
    this.changed = Objects.requireNonNull(changed);
  }

  public int capacity() {
    return capacity;
  }

  public int amount() {
    return amount;
  }

  public K key() {
    return key;
  }

  public int space() {
    return Math.max(0, capacity() - amount());
  }

  public Object ownerToken() {
    return this;
  }

  public int inputLimit(K fluid, int n) {
    return accepts(fluid) ? Math.min(Math.max(0, n), space()) : 0;
  }

  public int outputLimit(int n) {
    return Math.min(Math.max(0, n), amount());
  }

  protected void adjust(K fluid, int delta) {
    amount += delta;
    key = amount == 0 ? null : fluid;
  }

  protected void setContents(K fluid, int volume) {
    amount = volume;
    key = volume == 0 ? null : fluid;
  }

  public boolean accepts(K fluid) {
    return amount() == 0 || Objects.equals(key(), fluid);
  }

  public int fill(K fluid, int offered, boolean simulate) {
    Objects.requireNonNull(fluid);
    if (offered <= 0 || !accepts(fluid)) return 0;
    int accepted = inputLimit(fluid, offered);
    if (accepted > 0 && !simulate) {
      addReserved(fluid, accepted);
      notifyChanged();
    }
    return accepted;
  }

  public int drain(int requested, boolean simulate) {
    int drained = outputLimit(Math.max(0, requested));
    if (drained > 0 && !simulate) {
      removeReserved(drained);
      notifyChanged();
    }
    return drained;
  }

  /** Both stores commit before either callback; no temporary unowned volume. */
  public int transferTo(FluidCell<K> target, int limit, boolean simulate) {
    Objects.requireNonNull(target);
    if (target.ownerToken() == ownerToken()
        || amount() == 0
        || limit <= 0
        || !target.accepts(key())) return 0;
    int moved = Math.min(outputLimit(limit), target.inputLimit(key(), limit));
    if (moved > 0 && !simulate) {
      K fluid = key();
      removeReserved(moved);
      target.addReserved(fluid, moved);
      try {
        notifyChanged();
      } finally {
        target.notifyChanged();
      }
    }
    return moved;
  }

  /**
   * Recipe conversion commits both stores before either callback; no intermediate unowned fluid.
   */
  public boolean convertTo(
      FluidCell<K> target, int consumed, K output, int produced, boolean simulate) {
    Objects.requireNonNull(target);
    Objects.requireNonNull(output);
    if (target.ownerToken() == ownerToken()
        || consumed < 1
        || produced < 1
        || amount() < consumed
        || target.space() < produced
        || !target.accepts(output)) return false;
    if (!simulate) {
      removeReserved(consumed);
      target.addReserved(output, produced);
      try {
        notifyChanged();
      } finally {
        target.notifyChanged();
      }
    }
    return true;
  }

  public record Mutation<K>(FluidCell<K> cell, K key, int amount) {}

  /**
   * All fluid owners update before the caller's validated, callback-free item/progress commit and
   * observers.
   */
  public static <K> boolean commitBatch(
      java.util.List<Mutation<K>> changes, Runnable otherCommit, boolean simulate) {
    var seen =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
    for (var change : changes) {
      var cell = Objects.requireNonNull(change.cell());
      if (!seen.add(cell.ownerToken())
          || change.amount() < 0
          || change.amount() > 0 && change.key() == null
          || change.amount() > cell.capacity() && change.amount() > cell.amount()) return false;
    }
    if (simulate) return true;
    for (var change : changes) {
      change.cell.setContents(change.key, change.amount);
    }
    // The adapter only writes prevalidated slots/primitive fields here; world callbacks are
    // deferred.
    otherCommit.run();
    RuntimeException failure = null;
    for (var change : changes)
      try {
        change.cell.notifyChanged();
      } catch (RuntimeException error) {
        if (failure == null) failure = error;
        else if (failure != error) failure.addSuppressed(error);
      }
    if (failure != null) throw failure;
    return true;
  }

  /** Oversized legacy saves retain volume; further filling waits until overflow is drained. */
  public void restore(K fluid, int volume) {
    if (volume < 0 || volume > 0 && fluid == null)
      throw new IllegalArgumentException("Invalid persisted fluid");
    setContents(fluid, volume);
  }

  void observe(Runnable callback) {
    if (callback != null && observer != null)
      throw new IllegalStateException("Fluid cell already belongs to a graph");
    observer = callback;
  }

  void removeReserved(int volume) {
    if (volume < 0 || volume > amount())
      throw new IllegalStateException("Fluid reservation exceeded source");
    adjust(key(), -volume);
  }

  void addReserved(K fluid, int volume) {
    if (volume < 0 || volume > space() || !accepts(fluid))
      throw new IllegalStateException("Fluid reservation exceeded destination");
    if (volume > 0) adjust(fluid, volume);
  }

  void notifyChanged() {
    try {
      changed.run();
    } finally {
      if (observer != null) observer.run();
    }
  }
}
