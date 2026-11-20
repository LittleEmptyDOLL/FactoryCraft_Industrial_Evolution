package factorycraft.simulation.rail;

import java.util.*;

/** Immutable Factorio name-based schedule definition; runtime progress lives on one train. */
public record RailSchedule(List<Entry> entries, List<UUID> interrupts) {
  public static final RailSchedule EMPTY = new RailSchedule(List.of(), List.of());

  public record Entry(String station, List<RailWait.Condition> waits) {
    public Entry {
      if (station == null || station.isBlank() || station.length() > 128 || waits.size() > 32)
        throw new IllegalArgumentException("Schedule entry");
      waits = List.copyOf(waits);
    }
  }

  public RailSchedule {
    if (entries.size() > 64
        || interrupts.size() > 32
        || new HashSet<>(interrupts).size() != interrupts.size())
      throw new IllegalArgumentException("Schedule size");
    entries = List.copyOf(entries);
    interrupts = List.copyOf(interrupts);
  }
}
