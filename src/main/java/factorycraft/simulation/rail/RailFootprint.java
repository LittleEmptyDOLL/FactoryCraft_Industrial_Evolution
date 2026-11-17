package factorycraft.simulation.rail;

import java.util.*;

/** Thin physical bed vs conservative 3x7x4 future vehicle swept clearance; no world mutation. */
public final class RailFootprint {
  public record Cell(int x, int y, int z) {}

  public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {}

  public final Map<Cell, List<Box>> bed = new LinkedHashMap<>();
  public final Set<Cell> clearance = new LinkedHashSet<>();
  private final Set<Long> chunkKeys;

  public RailFootprint(RailCurve curve) {
    int samples = (int) Math.ceil(curve.length() * 4);
    for (int i = 0; i <= samples; i++) {
      var f = curve.sample(curve.length() * i / samples, false);
      var p = f.position();
      var side = f.side();
      var forward = f.tangent();
      double bx = Math.abs(side.x()) + Math.abs(forward.x()) * .14,
          bz = Math.abs(side.z()) + Math.abs(forward.z()) * .14;
      addBed(new Box(p.x() - bx, p.y() - .125, p.z() - bz, p.x() + bx, p.y() + .25, p.z() + bz));
      // A full sized future 7-block body, 3-wide and 4-high. AABB sweep is conservative at
      // rotations.
      double cx = Math.abs(side.x()) * 1.5 + Math.abs(forward.x()) * 3.5,
          cz = Math.abs(side.z()) * 1.5 + Math.abs(forward.z()) * 3.5;
      for (int x = floor(p.x() - cx + .00001); x <= floor(p.x() + cx - .00001); x++)
        for (int z = floor(p.z() - cz + .00001); z <= floor(p.z() + cz - .00001); z++)
          for (int y = floor(p.y() + .125 + .00001); y <= floor(p.y() + 4 - .00001); y++)
            clearance.add(new Cell(x, y, z));
    }
    // Endpoint foundations remain real world blocks. Clip the tiny eased-ramp abutment
    // below their top plane instead of replacing the supporting block with a rail part.
    for (var end : List.of(curve.start, curve.end))
      bed.remove(new Cell(end.x(), end.y() - 1, end.z()));
    clearance.addAll(bed.keySet());
    for (var end : List.of(curve.start, curve.end))
      clearance.remove(new Cell(end.x(), end.y() - 1, end.z()));
    var keys = new HashSet<Long>();
    for (var c : clearance) keys.add(chunk(c.x() >> 4, c.z() >> 4));
    chunkKeys = Set.copyOf(keys);
  }

  private static int floor(double d) {
    return (int) Math.floor(d);
  }

  private void addBed(Box b) {
    for (int x = floor(b.x0() + .00001); x <= floor(b.x1() - .00001); x++)
      for (int y = floor(b.y0() + .00001); y <= floor(b.y1() - .00001); y++)
        for (int z = floor(b.z0() + .00001); z <= floor(b.z1() - .00001); z++) {
          var c = new Cell(x, y, z);
          var clip =
              new Box(
                  Math.max(0, b.x0() - x),
                  Math.max(0, b.y0() - y),
                  Math.max(0, b.z0() - z),
                  Math.min(1, b.x1() - x),
                  Math.min(1, b.y1() - y),
                  Math.min(1, b.z1() - z));
          bed.computeIfAbsent(c, k -> new ArrayList<>()).add(clip);
        }
  }

  public Set<Long> chunks() {
    return chunkKeys;
  }

  public static long chunk(int x, int z) {
    return (x & 0xffffffffL) | ((long) z << 32);
  }
}
