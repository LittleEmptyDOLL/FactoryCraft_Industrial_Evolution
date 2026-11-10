package factorycraft.simulation.benchmark;

import java.util.*;

/**
 * Deterministic, Minecraft-independent footprint. Only the isolated diagnostic server builds it.
 */
public final class BenchmarkPlan {
  public enum Profile {
    SMOKE(1, 5, 40, 120),
    FULL(100, 250, 200, 600),
    MIXED(100, 250, 200, 600);
    public final int loops, cells, warmup, ticks;

    Profile(int loops, int cells, int warmup, int ticks) {
      this.loops = loops;
      this.cells = cells;
      this.warmup = warmup;
      this.ticks = ticks;
    }

    public static Profile parse(String value) {
      return valueOf(value.toUpperCase(Locale.ROOT));
    }
  }

  public enum Heading {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);
    public final int x, z;

    Heading(int x, int z) {
      this.x = x;
      this.z = z;
    }
  }

  public enum Role {
    BELT,
    FURNACE,
    ASSEMBLER,
    INDUSTRIAL_FURNACE,
    CASING,
    GENERATOR,
    POLE,
    INSERTER,
    SOURCE,
    INCLINE_UP,
    INCLINE_DOWN,
    LIFT_UP,
    LIFT_DOWN,
    UNDERGROUND_INPUT,
    UNDERGROUND_OUTPUT,
    TRANSPORT_PART,
    OBSTACLE;

    public boolean belt() {
      return this == BELT
          || this == INCLINE_UP
          || this == INCLINE_DOWN
          || this == LIFT_UP
          || this == LIFT_DOWN
          || this == UNDERGROUND_INPUT
          || this == UNDERGROUND_OUTPUT;
    }
  }

  public record Cell(int x, int y, int z, Role role, Heading heading, int tier) {
    public Cell(int x, int y, int z, Role role, Heading heading) {
      this(x, y, z, role, heading, 1);
    }
  }

  private final List<Cell> blocks;
  private final Set<Cell> extraSeeds;

  public boolean extraSeed(Cell cell) {
    return extraSeeds.contains(cell);
  }

  public int movingBelts() {
    return (int)
        blocks.stream().filter(c -> c.role().belt() && c.role() != Role.UNDERGROUND_OUTPUT).count();
  }

  public BenchmarkPlan(Profile profile) {
    var cells = new ArrayList<Cell>();
    var mixed = profile == Profile.MIXED ? MixedBeltPlan.create(profile.loops) : null;
    extraSeeds = mixed == null ? Set.of() : mixed.extraSeeds();
    if (mixed != null) cells.addAll(mixed.cells());
    for (int ring = 0; ring < profile.loops && mixed == null; ring++) {
      int x = 2 + (ring % 5) * 28, z = 2 + (ring / 5 % 2) * 28, y = 20 + (ring / 10) * 3;
      for (int i = 0; i < 26; i++)
        cells.add(new Cell(x + i, y, z, Role.BELT, i == 25 ? Heading.SOUTH : Heading.EAST));
      for (int i = 1; i < 26; i++)
        cells.add(new Cell(x + 25, y, z + i, Role.BELT, i == 25 ? Heading.WEST : Heading.SOUTH));
      for (int i = 24; i >= 0; i--)
        cells.add(new Cell(x + i, y, z + 25, Role.BELT, i == 0 ? Heading.NORTH : Heading.WEST));
      for (int i = 24; i >= 1; i--) cells.add(new Cell(x, y, z + i, Role.BELT, Heading.NORTH));
    }
    for (int i = 0; i < profile.cells; i++) {
      int x = (i % 25) * 16, z = (i / 25) * 12;
      add(cells, x, 0, z + 1, Role.SOURCE, Heading.NORTH);
      add(cells, x + 1, 0, z + 1, Role.INSERTER, Heading.EAST);
      machine(cells, x + 2, z, Role.FURNACE);
      add(cells, x + 5, 0, z + 1, Role.INSERTER, Heading.EAST);
      machine(cells, x + 6, z, Role.ASSEMBLER);
      machine(cells, x + 2, z + 6, Role.FURNACE);
      machine(cells, x + 6, z + 6, Role.INDUSTRIAL_FURNACE);
      machine(cells, x + 10, z, Role.GENERATOR);
      add(cells, x + 5, 0, z + 4, Role.POLE, Heading.NORTH);
      add(cells, x + 11, 0, z + 4, Role.POLE, Heading.NORTH);
      if (i + 1 < profile.cells && i % 25 < 24)
        add(cells, x + 17, 0, z + 4, Role.POLE, Heading.NORTH);
      if (i % 25 == 0 && i + 25 < profile.cells)
        add(cells, x + 5, 0, z + 10, Role.POLE, Heading.NORTH);
    }
    blocks = List.copyOf(cells);
  }

  private static void machine(List<Cell> cells, int x, int z, Role role) {
    // Schema 4: body origin remains fixed; controller is the front-center base cell.
    add(cells, x + 1, 0, z, role, Heading.NORTH);
    for (int y = 0; y < 3; y++)
      for (int dz = 0; dz < 3; dz++)
        for (int dx = 0; dx < 3; dx++)
          if (dx != 1 || y != 0 || dz != 0)
            add(cells, x + dx, y, z + dz, Role.CASING, Heading.NORTH);
  }

  public static int poleCount(Profile p) {
    return p.cells * 2 + (p.cells - 1);
  }

  private static void add(List<Cell> cells, int x, int y, int z, Role role, Heading heading) {
    cells.add(new Cell(x, y, z, role, heading));
    if (role == Role.POLE) cells.add(new Cell(x, y + 1, z, Role.CASING, Heading.NORTH));
  }

  public List<Cell> blocks() {
    return blocks;
  }
}
