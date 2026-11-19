package factorycraft.simulation.rail;

import factorycraft.simulation.trains.*;
import java.util.*;

/** Thin physical bed and source-sized bogie-oriented future clearance; no world mutation. */
public final class RailFootprint {
  public record Cell(int x, int y, int z) {}

  public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {}

  public final Map<Cell, List<Box>> bed = new LinkedHashMap<>();
  public final Set<Cell> clearance = new LinkedHashSet<>();
  private final Set<Long> chunkKeys;
  private final Map<Cell, List<MotionHull>> hulls = new HashMap<>();

  public boolean intersects(Cell cell, Box box) {
    double x0 = cell.x() + box.x0,
        y0 = cell.y() + box.y0,
        z0 = cell.z() + box.z0,
        x1 = cell.x() + box.x1,
        y1 = cell.y() + box.y1,
        z1 = cell.z() + box.z1;
    for (int x = floor(x0 + 1e-7); x <= floor(x1 - 1e-7); x++)
      for (int y = floor(y0 + 1e-7); y <= floor(y1 - 1e-7); y++)
        for (int z = floor(z0 + 1e-7); z <= floor(z1 - 1e-7); z++) {
          var c = new Cell(x, y, z);
          for (var hull : hulls.getOrDefault(c, List.of()))
            if (hull.intersects(x0, y0, z0, x1, y1, z1)) return true;
          for (var b : bed.getOrDefault(c, List.of()))
            if (x0 < c.x() + b.x1 - 1e-8
                && x1 > c.x() + b.x0 + 1e-8
                && y0 < c.y() + b.y1 - 1e-8
                && y1 > c.y() + b.y0 + 1e-8
                && z0 < c.z() + b.z1 - 1e-8
                && z1 > c.z() + b.z0 + 1e-8) return true;
        }
    return false;
  }

  public final Set<Cell> obstacles = new LinkedHashSet<>();

  public RailFootprint(RailCurve curve) {
    // Preserve the exact published physical bed sampling; rounding a doubled sample count
    // changes curved cells and can invalidate existing world projections.
    int bedSamples = (int) Math.ceil(curve.length() * 4);
    for (int i = 0; i <= bedSamples; i++) {
      var f = curve.sample(curve.length() * i / bedSamples, false);
      var p = f.position();
      var side = f.side();
      var forward = f.tangent();
      double bx = Math.abs(side.x()) + Math.abs(forward.x()) * .14,
          bz = Math.abs(side.z()) + Math.abs(forward.z()) * .14;
      addBed(new Box(p.x() - bx, p.y() - .125, p.z() - bz, p.x() + bx, p.y() + .25, p.z() + bz));
    }
    int samples = (int) Math.ceil(curve.length() * 8);
    StockEnvelope previous = null;
    for (int i = 0; i <= samples; i++) {
      var current = StockEnvelope.of(BogiePose.onCurve(curve, curve.length() * i / samples));
      var hull = new MotionHull(previous, current);
      previous = current;
      var bounds = hull.bounds();
      for (int x = floor(bounds.x0() + .00001); x <= floor(bounds.x1() - .00001); x++)
        for (int z = floor(bounds.z0() + .00001); z <= floor(bounds.z1() - .00001); z++)
          for (int y = floor(bounds.y0() + .00001); y <= floor(bounds.y1() - .00001); y++) {
            if (!hull.intersects(x, y, z, x + 1, y + 1, z + 1)) continue;
            var cell = new Cell(x, y, z);
            clearance.add(cell);
            hulls.computeIfAbsent(cell, k -> new ArrayList<>()).add(hull);
          }
    }
    // Endpoint foundations remain real world blocks. Clip the tiny eased-ramp abutment
    // below their top plane instead of replacing the supporting block with a rail part.
    for (var end : List.of(curve.start, curve.end))
      bed.remove(new Cell(end.x(), end.y() - 1, end.z()));
    clearance.addAll(bed.keySet());
    for (var end : List.of(curve.start, curve.end))
      clearance.remove(new Cell(end.x(), end.y() - 1, end.z()));
    for (var c : clearance)
      for (int x = -1; x <= 1; x++)
        for (int y = -1; y <= 1; y++)
          for (int z = -1; z <= 1; z++) obstacles.add(new Cell(c.x() + x, c.y() + y, c.z() + z));
    var keys = new HashSet<Long>();
    for (var c : obstacles) keys.add(chunk(c.x() >> 4, c.z() >> 4));
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
