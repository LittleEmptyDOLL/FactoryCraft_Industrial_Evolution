package factorycraft.simulation.trains;

import java.util.*;

/** Bounded grid broadphase for swept bodies on different rail edges, including junction forks. */
public final class SpatialReservations {
  public record Bounds(double x0, double y0, double z0, double x1, double y1, double z1) {
    public Bounds {
      if (!Double.isFinite(x0 + y0 + z0 + x1 + y1 + z1)
          || x1 < x0
          || y1 < y0
          || z1 < z0
          || x1 - x0 > 32
          || y1 - y0 > 32
          || z1 - z0 > 32) throw new IllegalArgumentException("Stock claim bounds");
    }

    boolean intersects(Bounds b) {
      return x0 < b.x1 - 1e-6
          && b.x0 < x1 - 1e-6
          && y0 < b.y1 - 1e-6
          && b.y0 < y1 - 1e-6
          && z0 < b.z1 - 1e-6
          && b.z0 < z1 - 1e-6;
    }
  }

  private record Cell(int x, int y, int z) {}

  private record Claim(UUID owner, Bounds box) {}

  private static final ArrayList<Claim> EMPTY = new ArrayList<>();
  private final Map<Cell, ArrayList<Claim>> grid = new HashMap<>();
  private final Map<UUID, List<Bounds>> owners = new HashMap<>();

  private static List<Cell> cells(Bounds b) {
    var out = new ArrayList<Cell>();
    for (int x = (int) Math.floor(b.x0 / 8); x <= (int) Math.floor(b.x1 / 8); x++)
      for (int y = (int) Math.floor(b.y0 / 8); y <= (int) Math.floor(b.y1 / 8); y++)
        for (int z = (int) Math.floor(b.z0 / 8); z <= (int) Math.floor(b.z1 / 8); z++)
          out.add(new Cell(x, y, z));
    return out;
  }

  public boolean canReserve(UUID owner, List<Bounds> boxes) {
    for (var box : boxes)
      for (var cell : cells(box))
        for (var old : grid.getOrDefault(cell, EMPTY))
          if (!old.owner.equals(owner) && old.box.intersects(box)) return false;
    return true;
  }

  public boolean reserve(UUID owner, List<Bounds> boxes) {
    if (!canReserve(owner, boxes)) return false;
    release(owner);
    owners.put(owner, List.copyOf(boxes));
    for (var b : boxes)
      for (var c : cells(b))
        grid.computeIfAbsent(c, k -> new ArrayList<>()).add(new Claim(owner, b));
    return true;
  }

  public void release(UUID owner) {
    var old = owners.remove(owner);
    if (old == null) return;
    for (var b : old)
      for (var c : cells(b)) {
        var entries = grid.get(c);
        if (entries == null) continue;
        entries.removeIf(e -> e.owner.equals(owner));
        if (entries.isEmpty()) grid.remove(c);
      }
  }
}
