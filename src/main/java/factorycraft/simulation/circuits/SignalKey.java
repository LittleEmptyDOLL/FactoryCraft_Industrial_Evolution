package factorycraft.simulation.circuits;

import java.util.Objects;
import java.util.regex.Pattern;

/** Typed identity: an item, fluid and virtual signal with the same ID remain different keys. */
public record SignalKey(Type type, String id) implements Comparable<SignalKey> {
  public enum Type {
    ITEM,
    FLUID,
    VIRTUAL,
    RECIPE,
    TECHNOLOGY
  }

  private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
  public static final SignalKey A = virtual("signal_a"),
      STATE = virtual("state"),
      ENERGY = virtual("energy"),
      PROGRESS = virtual("progress"),
      TICK = virtual("game_tick"),
      DAY = virtual("day_tick"),
      DAY_LENGTH = virtual("day_length"),
      COUNT = virtual("signal_count");

  public SignalKey {
    Objects.requireNonNull(type);
    Objects.requireNonNull(id);
    if (id.length() > 256 || !ID.matcher(id).matches())
      throw new IllegalArgumentException("Malformed signal ID");
  }

  public static SignalKey item(String id) {
    return new SignalKey(Type.ITEM, id);
  }

  public static SignalKey fluid(String id) {
    return new SignalKey(Type.FLUID, id);
  }

  public static SignalKey virtual(String path) {
    return new SignalKey(Type.VIRTUAL, "factorycraft:" + path);
  }

  public int compareTo(SignalKey b) {
    int kind = type.compareTo(b.type);
    return kind == 0 ? id.compareTo(b.id) : kind;
  }
}
