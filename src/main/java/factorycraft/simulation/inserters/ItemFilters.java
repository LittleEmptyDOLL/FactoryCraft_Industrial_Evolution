package factorycraft.simulation.inserters;

import java.util.*;

/** Immutable five-type list. Unknown IDs remain visible data and fail closed in whitelist mode. */
public record ItemFilters(List<String> ids, boolean blacklist) {
  public ItemFilters {
    if (ids.size() != 5) throw new IllegalArgumentException("Five filters required");
    ids = List.copyOf(ids);
  }

  public boolean matches(String item) {
    boolean any = false, found = false;
    for (var id : ids)
      if (!id.isEmpty()) {
        any = true;
        found |= id.equals(item);
      }
    return !any || blacklist != found;
  }

  public static ItemFilters empty() {
    return new ItemFilters(Collections.nCopies(5, ""), false);
  }
}
