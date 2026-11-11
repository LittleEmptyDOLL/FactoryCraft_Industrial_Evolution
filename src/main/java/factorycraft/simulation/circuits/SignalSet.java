package factorycraft.simulation.circuits;

import java.util.*;

/** Immutable sparse int32 snapshot; builders bound distinct keys and never silently truncate. */
public final class SignalSet {
  public static final int LIMIT = 4096;
  public static final SignalSet EMPTY = new SignalSet(Map.of());
  private final Map<SignalKey, Integer> values;

  private SignalSet(Map<SignalKey, Integer> values) {
    this.values = Map.copyOf(values);
  }

  public static SignalSet of(SignalKey key, int value) {
    return value == 0 ? EMPTY : new SignalSet(Map.of(key, value));
  }

  public static SignalSet copy(Map<SignalKey, Integer> source) {
    if (source.isEmpty()) return EMPTY;
    if (source.size() > LIMIT) throw new IllegalArgumentException("Signal capacity exceeded");
    if (source.values().stream().anyMatch(v -> v == 0)) {
      var b = new Builder();
      source.forEach(b::add);
      return b.build();
    }
    return new SignalSet(source);
  }

  public Map<SignalKey, Integer> values() {
    return values;
  }

  public int get(SignalKey key) {
    return values.getOrDefault(key, 0);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public int size() {
    return values.size();
  }

  public boolean equals(Object o) {
    return o instanceof SignalSet s && values.equals(s.values);
  }

  public int hashCode() {
    return values.hashCode();
  }

  public static final class Builder {
    private final Map<SignalKey, Integer> values = new HashMap<>();

    public Builder add(SignalKey key, int n) {
      Objects.requireNonNull(key);
      int next = values.getOrDefault(key, 0) + n;
      if (next == 0) values.remove(key);
      else {
        if (!values.containsKey(key) && values.size() >= LIMIT)
          throw new IllegalArgumentException("Signal capacity exceeded");
        values.put(key, next);
      }
      return this;
    }

    public Builder addAll(SignalSet set) {
      set.values.forEach(this::add);
      return this;
    }

    public SignalSet build() {
      return values.isEmpty() ? EMPTY : new SignalSet(values);
    }
  }
}
