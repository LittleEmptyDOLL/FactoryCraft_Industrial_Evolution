package factorycraft.simulation.rail;

import java.util.*;
import java.util.regex.Pattern;

/** Typed Factorio-style wildcard binding, never command interpolation or unbounded replacement. */
public record RailInterrupt(
    UUID id, String name, List<RailWait.Condition> conditions, List<RailSchedule.Entry> targets) {
  public enum Type {
    ITEM,
    FLUID,
    FUEL,
    SIGNAL
  }

  public record Match(Type type, String id) implements Comparable<Match> {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public Match {
      if (type == null || id == null || id.length() > 256 || !ID.matcher(id).matches())
        throw new IllegalArgumentException("Typed wildcard");
    }

    public int compareTo(Match b) {
      int c = type.compareTo(b.type);
      return c == 0 ? id.compareTo(b.id) : c;
    }
  }

  public RailInterrupt {
    if (id == null
        || name == null
        || name.isBlank()
        || name.length() > 128
        || conditions.size() > 32
        || targets.isEmpty()
        || targets.size() > 16) throw new IllegalArgumentException("Interrupt definition");
    conditions = List.copyOf(conditions);
    targets = List.copyOf(targets);
  }

  /** Rich text tokens keep item/fluid/fuel/virtual namespaces distinct in actual stop names. */
  public static String substitute(String template, Match match) {
    if (template == null || template.length() > 128)
      throw new IllegalArgumentException("Station name");
    String key =
        switch (match.type()) {
          case ITEM -> "item";
          case FLUID -> "fluid";
          case FUEL -> "fuel";
          case SIGNAL -> "signal";
        };
    String output = template.replace("[" + key + "=*]", "[" + key + "=" + match.id() + "]");
    if (output.length() > 128 || output.contains("=*]"))
      throw new IllegalArgumentException("Unresolved or oversized typed wildcard");
    return output;
  }
}
