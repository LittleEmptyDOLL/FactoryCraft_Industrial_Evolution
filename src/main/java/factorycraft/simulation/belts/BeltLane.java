package factorycraft.simulation.belts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** Bounded, allocation-free hot loop. Front carrier is index zero. No Minecraft dependency. */
public final class BeltLane<T> {
    public static final double GAP = 0.25;
    public static final int CAPACITY = 4;
    private final ArrayList<Carrier<T>> carriers = new ArrayList<>(CAPACITY);
    private final List<Carrier<T>> view = Collections.unmodifiableList(carriers);

    public static final class Carrier<T> {
        public final T value;
        public double position;
        public final long insertedTick;
        private Carrier(T value, double position, long insertedTick) {
            this.value = value;
            this.position = position;
            this.insertedTick = insertedTick;
        }
    }

    public List<Carrier<T>> carriers() { return view; }
    public boolean canInsert() {
        return carriers.size() < CAPACITY && (carriers.isEmpty() || carriers.getLast().position >= GAP - 1e-8);
    }
    public boolean insert(T value, long tick) {
        if (!canInsert()) return false;
        carriers.add(new Carrier<>(value, 0, tick));
        return true;
    }
    public T remove(int index) { return carriers.remove(index).value; }
    public void clear() { carriers.clear(); }
    public void restore(T value, double position) {
        if (!Double.isFinite(position) || position < 0 || position > 1 || carriers.size() >= CAPACITY)
            throw new IllegalArgumentException("Invalid persisted belt position");
        if (!carriers.isEmpty() && carriers.getLast().position - position < GAP - 1e-8)
            throw new IllegalArgumentException("Overlapping persisted belt carriers");
        carriers.add(new Carrier<>(value, position, Long.MIN_VALUE));
    }

    /** Exit must atomically accept ownership iff it returns true. New arrivals never move twice in a tick. */
    public boolean advance(double speed, long tick, Predicate<T> exit) {
        boolean changed = false;
        double limit = 1;
        for (int i = 0; i < carriers.size();) {
            var c = carriers.get(i);
            double next = c.insertedTick == tick ? c.position : Math.min(limit, c.position + speed);
            if (i == 0 && next >= 1 && c.insertedTick != tick && exit.test(c.value)) {
                carriers.remove(i);
                changed = true;
                continue;
            }
            if (next != c.position) { c.position = next; changed = true; }
            limit = c.position - GAP;
            i++;
        }
        return changed;
    }
}
