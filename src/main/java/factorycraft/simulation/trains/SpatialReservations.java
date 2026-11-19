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

  private static final class Claim {
    final UUID owner;
    final Bounds box;
    final SweptHull hull;
    long seen;

    Claim(UUID owner, Bounds box, SweptHull hull) {
      this.owner = owner;
      this.box = box;
      this.hull = hull;
    }
  }

  private long query;

  private static final ArrayList<Claim> EMPTY = new ArrayList<>();
  private final Map<Cell, ArrayList<Claim>> grid = new HashMap<>();
  private final Map<UUID, List<Claim>> owners = new HashMap<>();

  private static List<Cell> cells(Bounds b) {
    var out = new ArrayList<Cell>();
    for (int x = (int) Math.floor(b.x0 / 8); x <= (int) Math.floor(b.x1 / 8); x++)
      for (int y = (int) Math.floor(b.y0 / 8); y <= (int) Math.floor(b.y1 / 8); y++)
        for (int z = (int) Math.floor(b.z0 / 8); z <= (int) Math.floor(b.z1 / 8); z++)
          out.add(new Cell(x, y, z));
    return out;
  }

  public boolean canReserve(UUID owner, List<Bounds> boxes) {
    for (var box : boxes) {
      long stamp = ++query;
      for (var cell : cells(box))
        for (var old : grid.getOrDefault(cell, EMPTY)) {
          if (old.seen == stamp) continue;
          old.seen = stamp;
          if (!old.owner.equals(owner) && old.box.intersects(box)) return false;
        }
    }
    return true;
  }

  public boolean canReserveShapes(UUID owner, List<SweptHull> hulls) {
    for (var hull : hulls) {
      long stamp = ++query;
      var box = hull.bounds();
      for (var cell : cells(box))
        for (var old : grid.getOrDefault(cell, EMPTY)) {
          if (old.seen == stamp) continue;
          old.seen = stamp;
          if (!old.owner.equals(owner)
              && old.box.intersects(box)
              && (old.hull == null || hull.intersects(old.hull))) return false;
        }
    }
    return true;
  }

  public boolean reserve(UUID owner, List<Bounds> boxes) {
    if (!canReserve(owner, boxes)) return false;
    var claims = new ArrayList<Claim>();
    for (var b : boxes) claims.add(new Claim(owner, b, null));
    publish(owner, claims);
    return true;
  }

  public boolean reserveShapes(UUID owner, List<SweptHull> hulls) {
    if (!canReserveShapes(owner, hulls)) return false;
    var claims = new ArrayList<Claim>();
    for (var h : hulls) claims.add(new Claim(owner, h.bounds(), h));
    publish(owner, claims);
    return true;
  }

  private void publish(UUID owner, List<Claim> claims) {
    release(owner);
    owners.put(owner, List.copyOf(claims));
    for (var claim : claims)
      for (var cell : cells(claim.box))
        grid.computeIfAbsent(cell, k -> new ArrayList<>()).add(claim);
  }

  public void release(UUID owner) {
    var old = owners.remove(owner);
    if (old == null) return;
    for (var claim : old)
      for (var cell : cells(claim.box)) {
        var entries = grid.get(cell);
        if (entries == null) continue;
        entries.removeIf(e -> e.owner.equals(owner));
        if (entries.isEmpty()) grid.remove(cell);
      }
  }
}
