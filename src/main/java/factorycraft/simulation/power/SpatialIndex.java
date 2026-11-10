package factorycraft.simulation.power;

import java.util.*;

/** A rebuild-time index. A cursor spends at most one bucket probe or candidate per step. */
final class SpatialIndex<N> {
  private record Cell(int x, int y, int z) {}

  private final int size;
  private final Map<Cell, ArrayList<N>> buckets = new HashMap<>();

  SpatialIndex(int size) {
    this.size = size;
  }

  void add(PowerPoint p, N value) {
    buckets.computeIfAbsent(cell(p), k -> new ArrayList<>()).add(value);
  }

  private Cell cell(PowerPoint p) {
    return new Cell(
        Math.floorDiv(p.x(), size), Math.floorDiv(p.y(), size), Math.floorDiv(p.z(), size));
  }

  Cursor query(PowerPoint p) {
    return query(p, size, size);
  }

  Cursor query(PowerPoint p, int horizontal, int vertical) {
    return new Cursor(
        cell(new PowerPoint(p.x() - horizontal, p.y() - vertical, p.z() - horizontal)),
        cell(new PowerPoint(p.x() + horizontal, p.y() + vertical, p.z() + horizontal)));
  }

  final class Cursor {
    private final Cell min, max;
    private int x, y, z, entry;
    private boolean finished;
    private List<N> bucket;

    Cursor(Cell min, Cell max) {
      this.min = min;
      this.max = max;
      x = min.x;
      y = min.y;
      z = min.z;
    }

    boolean done() {
      return finished && (bucket == null || entry == bucket.size());
    }

    N step() {
      if (bucket != null && entry < bucket.size()) return bucket.get(entry++);
      if (finished) return null;
      bucket = buckets.get(new Cell(x, y, z));
      entry = 0;
      if (++x > max.x) {
        x = min.x;
        if (++y > max.y) {
          y = min.y;
          if (++z > max.z) finished = true;
        }
      }
      return null;
    }
  }
}
