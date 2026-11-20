package factorycraft.simulation.rail;

import java.util.*;

/** Derived non-resource indexes. Atomic prospective claims never erase actual body occupancy. */
public final class RailBlockClaims {
  private final Map<UUID, Set<UUID>> bodies = new HashMap<>(), byTrain = new HashMap<>();
  private final Map<UUID, UUID> reserved = new HashMap<>();
  private final Map<UUID, Set<UUID>> leases = new HashMap<>();
  private final Set<UUID> changed = new HashSet<>();
  private long revision;

  public long revision() {
    return revision;
  }

  public Set<UUID> occupants(UUID block) {
    return Set.copyOf(bodies.getOrDefault(block, Set.of()));
  }

  public UUID reservation(UUID block) {
    return reserved.get(block);
  }

  public Set<UUID> takeChanged() {
    var out = Set.copyOf(changed);
    changed.clear();
    return out;
  }

  public boolean available(UUID block, UUID train) {
    var owner = reserved.get(block);
    if (owner != null && !owner.equals(train)) return false;
    for (var occupant : bodies.getOrDefault(block, Set.of()))
      if (!occupant.equals(train)) return false;
    return true;
  }

  public void occupy(UUID train, Set<UUID> blocks) {
    var old = byTrain.getOrDefault(train, Set.of());
    if (old.equals(blocks)) return;
    changed.addAll(old);
    changed.addAll(blocks);
    for (var id : old)
      if (!blocks.contains(id)) {
        var set = bodies.get(id);
        set.remove(train);
        if (set.isEmpty()) bodies.remove(id);
      }
    for (var id : blocks) bodies.computeIfAbsent(id, key -> new HashSet<>()).add(train);
    if (blocks.isEmpty()) byTrain.remove(train);
    else byTrain.put(train, Set.copyOf(blocks));
    revision++;
  }

  public boolean reserve(UUID train, Set<UUID> blocks) {
    for (var id : blocks) if (!available(id, train)) return false;
    var old = leases.getOrDefault(train, Set.of());
    if (old.equals(blocks)) return true;
    changed.addAll(old);
    changed.addAll(blocks);
    for (var id : old) if (!blocks.contains(id)) reserved.remove(id, train);
    for (var id : blocks) reserved.put(id, train);
    if (blocks.isEmpty()) leases.remove(train);
    else leases.put(train, Set.copyOf(blocks));
    revision++;
    return true;
  }

  public void remove(UUID train) {
    occupy(train, Set.of());
    reserve(train, Set.of());
  }
}
