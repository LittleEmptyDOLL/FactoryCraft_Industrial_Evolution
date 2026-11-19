package factorycraft.simulation.rail;

import java.util.*;
import java.util.function.Function;

/**
 * Bounded5x5 endpoint cache, independent of client classes. At most the caller's budget is
 * inspected.
 */
public final class EndpointHints {
  public record Key(RailEnd start, RailEnd hover, int override, UUID anchor, long revision) {}

  public record Result(RailEnd end, String reason, int cost) {
    public boolean valid() {
      return reason.equals("ready");
    }
  }

  private Key key;
  private int cursor;
  private final Result[] entries = new Result[25];

  public boolean key(Key next) {
    if (next.equals(key)) return false;
    key = next;
    cursor = 0;
    Arrays.fill(entries, null);
    return true;
  }

  public int advance(int budget, Function<RailEnd, Result> inspect) {
    if (key == null || budget < 0) throw new IllegalArgumentException("Hint budget");
    int n = 0;
    while (cursor < 25 && n < budget) {
      int x = cursor % 5 - 2, z = cursor / 5 - 2;
      var h = key.hover;
      var end = new RailEnd(h.x() + x, h.y(), h.z() + z, h.heading());
      entries[cursor++] = inspect.apply(end);
      n++;
    }
    return n;
  }

  public Result get(int index) {
    return entries[index];
  }

  public RailEnd candidate(int index) {
    var h = key.hover;
    return new RailEnd(h.x() + index % 5 - 2, h.y(), h.z() + index / 5 - 2, h.heading());
  }

  public boolean complete() {
    return cursor == 25;
  }

  public void clear() {
    key = null;
    cursor = 0;
    Arrays.fill(entries, null);
  }
}
