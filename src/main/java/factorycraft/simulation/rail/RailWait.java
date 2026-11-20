package factorycraft.simulation.rail;

import java.util.*;
import java.util.function.Function;

/** Factorio wait conditions: AND runs separated by OR, not left-to-right operator folding. */
public final class RailWait {
  public enum Type {
    TIME,
    INACTIVITY,
    FULL,
    EMPTY,
    HAS_CARGO,
    ITEM_COUNT,
    FLUID_COUNT,
    ANY_FUEL,
    ALL_FUEL,
    FULL_FUEL,
    PASSENGER,
    NO_PASSENGER,
    STATION_FULL,
    STATION_AVAILABLE,
    CIRCUIT,
    NO_PATH,
    DESTINATION_FULL,
    AT_STATION,
    NOT_AT_STATION
  }

  public enum Comparison {
    LT,
    LE,
    EQ,
    NE,
    GE,
    GT;

    public boolean test(long a, long b) {
      return switch (this) {
        case LT -> a < b;
        case LE -> a <= b;
        case EQ -> a == b;
        case NE -> a != b;
        case GE -> a >= b;
        case GT -> a > b;
      };
    }
  }

  public record Condition(Type type, String filter, Comparison comparison, long value, boolean or) {
    public Condition {
      Objects.requireNonNull(type);
      Objects.requireNonNull(comparison);
      Objects.requireNonNull(filter);
      if (filter.length() > 4096 || value < -(1L << 52) || value > (1L << 52))
        throw new IllegalArgumentException("Wait condition bounds");
    }
  }

  public static boolean ready(
      List<Condition> conditions, Function<Condition, OptionalLong> samples) {
    return ready(conditions, samples, Condition::value);
  }

  public static boolean ready(
      List<Condition> conditions,
      Function<Condition, OptionalLong> samples,
      java.util.function.ToLongFunction<Condition> threshold) {
    if (conditions.size() > 32) throw new IllegalArgumentException("Wait condition count");
    boolean any = false, group = true;
    for (int i = 0; i < conditions.size(); i++) {
      var condition = conditions.get(i);
      if (i > 0 && condition.or()) {
        any |= group;
        group = true;
      }
      var sample = samples.apply(condition);
      group &=
          sample.isPresent()
              && condition.comparison().test(sample.orElse(0), threshold.applyAsLong(condition));
    }
    return any || group;
  }

  private RailWait() {}
}
