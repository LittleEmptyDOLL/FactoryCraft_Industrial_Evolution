package factorycraft.simulation.belts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Four carriers per lane; integral coordinates make collision solving exact and bounded. */
public final class BeltLane<T> {
  public static final int LENGTH = 96;
  public static final int SPACING = 24;
  public static final double GAP = (double) SPACING / LENGTH;
  public static final int CAPACITY = 4;
  private int length;

  public BeltLane() {
    this(LENGTH);
  }

  public BeltLane(int length) {
    if (length < 1 || length > 128 * LENGTH)
      throw new IllegalArgumentException("Invalid lane length");
    this.length = length;
  }

  public int length() {
    return length;
  }

  public int capacity() {
    return (length + SPACING - 1) / SPACING;
  }

  public boolean canResize(int next) {
    int delta = next - length;
    return next >= 1
        && next <= 128 * LENGTH
        && carriers.size() <= (next + SPACING - 1) / SPACING
        && (carriers.isEmpty() || carriers.getLast().units + delta >= 0);
  }

  public void resize(int next) {
    if (!canResize(next)) throw new IllegalStateException("Occupied path cannot shrink");
    int delta = next - length;
    for (var carrier : carriers) carrier.units += delta;
    length = next;
  }

  private final ArrayList<Carrier<T>> carriers = new ArrayList<>(CAPACITY);
  private final List<Carrier<T>> view = Collections.unmodifiableList(carriers);

  public static final class Carrier<T> {
    public final T value;
    int units;

    private Carrier(T value, int units) {
      this.value = Objects.requireNonNull(value);
      this.units = units;
    }

    public double position() {
      return (double) units / LENGTH;
    }

    public int positionUnits() {
      return units;
    }
  }

  public List<Carrier<T>> carriers() {
    return view;
  }

  int count() {
    return carriers.size();
  }

  Carrier<T> at(int index) {
    return carriers.get(index);
  }

  public boolean canInsert() {
    return canInsertAt(0);
  }

  boolean canInsertAt(int units) {
    return carriers.size() < capacity()
        && (carriers.isEmpty() || carriers.getLast().units - units >= SPACING);
  }

  public boolean insert(T value) {
    if (!canInsert()) return false;
    carriers.add(new Carrier<>(value, 0));
    return true;
  }

  public T remove(int index) {
    return removeCarrier(index).value;
  }

  Carrier<T> removeCarrier(int index) {
    return carriers.remove(index);
  }

  void accept(Carrier<T> carrier, int units) {
    if (!canInsertAt(units)) throw new IllegalStateException("Belt transfer was not reserved");
    carrier.units = units;
    carriers.add(carrier);
  }

  public void clear() {
    carriers.clear();
  }

  public void restore(T value, double position) {
    if (!Double.isFinite(position) || position < 0 || position > 1 || carriers.size() >= capacity())
      throw new IllegalArgumentException("Invalid persisted belt position");
    int units = (int) Math.round(position * length);
    if (!carriers.isEmpty() && carriers.getLast().units - units < SPACING)
      throw new IllegalArgumentException("Overlapping persisted belt carriers");
    carriers.add(new Carrier<>(value, units));
  }
}
