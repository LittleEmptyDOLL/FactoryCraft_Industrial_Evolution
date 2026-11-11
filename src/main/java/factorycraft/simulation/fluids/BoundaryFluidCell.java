package factorycraft.simulation.fluids;

import java.util.function.*;

/** Non-owning atomic port. Pressure quota belongs to this boundary, never the whole pipeline. */
public final class BoundaryFluidCell<K> extends FluidCell<K> {
  private final Supplier<FluidCell<K>> current;
  private final BooleanSupplier available, manual, payable;
  private final Runnable pay;
  private final LongSupplier clock;
  private final int rate;
  private final DoubleSupplier fullness;
  private final Bucket input = new Bucket(), output = new Bucket();
  private static final long SCALE = 1_000_000;

  private final class Bucket {
    long tick = Long.MIN_VALUE, credit;

    int limit(double fraction) {
      long now = clock.getAsLong();
      if (tick != now) {
        tick = now;
        credit =
            Math.min(
                (long) rate * SCALE,
                credit + Math.round(rate * SCALE * Math.clamp(fraction, 0, 1)));
      }
      return (int) (credit / SCALE);
    }

    void spend(int n) {
      credit -= n * SCALE;
      if (credit < 0) throw new IllegalStateException("Port quota exceeded");
    }
  }

  public BoundaryFluidCell(
      Supplier<FluidCell<K>> current,
      BooleanSupplier available,
      LongSupplier clock,
      int rate,
      BooleanSupplier manual,
      BooleanSupplier payable,
      Runnable pay) {
    this(
        current,
        available,
        clock,
        rate,
        manual,
        payable,
        pay,
        () -> (double) current.get().amount() / Math.max(1, current.get().capacity()));
  }

  public BoundaryFluidCell(
      Supplier<FluidCell<K>> current,
      BooleanSupplier available,
      LongSupplier clock,
      int rate,
      BooleanSupplier manual,
      BooleanSupplier payable,
      Runnable pay,
      DoubleSupplier fullness) {
    super(1, () -> {});
    this.fullness = fullness;
    this.current = current;
    this.available = available;
    this.clock = clock;
    this.rate = rate;
    this.manual = manual;
    this.payable = payable;
    this.pay = pay;
  }

  private boolean live() {
    return available.getAsBoolean() && (manual.getAsBoolean() || payable.getAsBoolean());
  }

  public int capacity() {
    return available.getAsBoolean() ? current.get().capacity() : 0;
  }

  public int amount() {
    return available.getAsBoolean() ? current.get().amount() : 0;
  }

  public K key() {
    return available.getAsBoolean() ? current.get().key() : null;
  }

  public int space() {
    return live() ? current.get().space() : 0;
  }

  public boolean accepts(K k) {
    return live() && current.get().accepts(k);
  }

  public Object ownerToken() {
    return current.get().ownerToken();
  }

  public int inputLimit(K k, int offered) {
    if (!live() || !accepts(k)) return 0;
    int n = Math.min(Math.max(0, offered), space());
    return manual.getAsBoolean() ? n : Math.min(n, input.limit(fraction(true)));
  }

  public int outputLimit(int requested) {
    if (!live()) return 0;
    int n = Math.min(Math.max(0, requested), current.get().outputLimit(requested));
    return manual.getAsBoolean() ? n : Math.min(n, output.limit(fraction(false)));
  }

  /** NaN denotes a positive-displacement pump chamber; adjacent passive ports supply pressure. */
  private double fraction(boolean input) {
    var fill = fullness.getAsDouble();
    return Double.isNaN(fill) ? 1 : input ? 1 - fill : fill;
  }

  protected void adjust(K key, int delta) {
    boolean batch = manual.getAsBoolean();
    if (!batch) {
      pay.run();
      if (delta > 0) input.spend(delta);
      else output.spend(-delta);
    }
    if (delta > 0) current.get().addReserved(key, delta);
    else current.get().removeReserved(-delta);
  }

  protected void setContents(K k, int amount) {
    throw new UnsupportedOperationException("A boundary owns no checkpoint");
  }

  void notifyChanged() {
    current.get().notifyChanged();
  }
}
