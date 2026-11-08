package factorycraft.simulation.belts;

import java.util.function.Predicate;

/** Optional policy on the existing reservation kernel: 1=first output, 2=second, 3=either. */
public record SplitterPolicy<T>(int preference, Predicate<T> filter)
    implements BeltTransport.Routing<T> {
  public SplitterPolicy {
    if (preference < 0 || preference > 2 || filter != null && preference == 0)
      throw new IllegalArgumentException("Invalid splitter policy");
  }

  public int outputs(T value) {
    if (filter == null) return 3;
    int selected = preference == 1 ? 1 : 2;
    return filter.test(value) ? selected : 3 ^ selected;
  }
}
