package factorycraft.simulation.benchmark;

import static factorycraft.simulation.benchmark.BenchmarkPlan.*;

import java.util.*;

/** 100 transport controllers per ring, including two non-owning underground outputs. */
public final class MixedBeltPlan {
  public record Result(List<Cell> cells, Set<Cell> extraSeeds) {}

  public static Result create(int loops) {
    var cells = new ArrayList<Cell>();
    var extra = new HashSet<Cell>();
    for (int ring = 0; ring < loops; ring++) {
      int x = 2 + (ring % 5) * 28,
          z = 2 + (ring / 5 % 2) * 28,
          y = 20 + (ring / 10) * 8,
          tier = 1 + ring % 3;
      var route = new ArrayList<Cell>();
      for (int i = 0; i < 26; i++) {
        if (i == 19 || i == 20) continue;
        int h = i >= 4 && i <= 7 ? 1 : i >= 11 && i <= 15 ? 3 : 0;
        Role role =
            switch (i) {
              case 3 -> Role.INCLINE_UP;
              case 7 -> Role.INCLINE_DOWN;
              case 10 -> Role.LIFT_UP;
              case 15 -> Role.LIFT_DOWN;
              case 18 -> Role.UNDERGROUND_INPUT;
              case 21 -> Role.UNDERGROUND_OUTPUT;
              default -> Role.BELT;
            };
        var cell = new Cell(x + i, y + h, z, role, i == 25 ? Heading.SOUTH : Heading.EAST, tier);
        route.add(cell);
        if (i == 1 || i == 2) extra.add(cell);
      }
      for (int i = 1; i < 26; i++) {
        if (i == 11 || i == 12) continue;
        if (i == 10) {
          route.add(new Cell(x + 25, y, z + i, Role.BELT, Heading.EAST, tier));
          route.add(new Cell(x + 26, y, z + i, Role.BELT, Heading.EAST, tier));
          route.add(new Cell(x + 27, y, z + i, Role.BELT, Heading.SOUTH, tier));
          route.add(new Cell(x + 27, y, z + 11, Role.BELT, Heading.SOUTH, tier));
          route.add(new Cell(x + 27, y, z + 12, Role.BELT, Heading.SOUTH, tier));
          route.add(new Cell(x + 27, y, z + 13, Role.BELT, Heading.WEST, tier));
          route.add(new Cell(x + 26, y, z + 13, Role.BELT, Heading.WEST, tier));
        } else
          route.add(
              new Cell(x + 25, y, z + i, Role.BELT, i == 25 ? Heading.WEST : Heading.SOUTH, tier));
      }
      for (int i = 24; i >= 0; i--) {
        if (i == 19 || i == 18) continue;
        var role = i == 20 ? Role.UNDERGROUND_INPUT : i == 17 ? Role.UNDERGROUND_OUTPUT : Role.BELT;
        route.add(new Cell(x + i, y, z + 25, role, i == 0 ? Heading.NORTH : Heading.WEST, tier));
      }
      for (int i = 24; i >= 1; i--)
        route.add(new Cell(x, y, z + i, Role.BELT, Heading.NORTH, tier));
      if (route.size() != 100)
        throw new IllegalStateException("Mixed ring must have 100 controllers: " + route.size());
      cells.addAll(route);
      for (var cell : route) {
        int h =
            switch (cell.role()) {
              case INCLINE_UP -> 1;
              case INCLINE_DOWN -> -1;
              case LIFT_UP -> 3;
              case LIFT_DOWN -> -3;
              default -> 0;
            };
        for (int dy = Integer.signum(h);
            h != 0 && Math.abs(dy) <= Math.abs(h);
            dy += Integer.signum(h))
          cells.add(
              new Cell(
                  cell.x(), cell.y() + dy, cell.z(), Role.TRANSPORT_PART, cell.heading(), tier));
      }
      for (int i : new int[] {19, 20})
        cells.add(new Cell(x + i, y, z, Role.OBSTACLE, Heading.NORTH));
      for (int i : new int[] {18, 19})
        cells.add(new Cell(x + i, y, z + 25, Role.OBSTACLE, Heading.NORTH));
    }
    return new Result(List.copyOf(cells), Set.copyOf(extra));
  }

  private MixedBeltPlan() {}
}
