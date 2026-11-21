package factorycraft.simulation.rail;

/** Public Factorio railway path-finding weights, in tile-equivalent metric costs. */
public final class RailRoutePenalties {
  public static final double CIRCUIT_RED = 1000, INTERMEDIATE_STOP = 2000;

  public enum Obstruction {
    MOVING,
    STATION,
    TERMINAL_STATION,
    MANUAL_PASSENGER,
    MANUAL_EMPTY,
    EMPTY_SCHEDULE,
    ARRIVING,
    SIGNAL_WAIT,
    NO_PATH
  }

  public static double occupied(double blockLength, int blocksFromStart) {
    if (!Double.isFinite(blockLength) || blockLength < 0 || blocksFromStart < 1)
      throw new IllegalArgumentException("Occupied block distance");
    return 2 * blockLength / blocksFromStart;
  }

  /** Minecraft runs at 20Hz; the source waiting surcharge is 0.1 per 60Hz tick. */
  public static double train(Obstruction kind, int minecraftWaitTicks) {
    if (minecraftWaitTicks < 0) throw new IllegalArgumentException("Wait duration");
    return switch (kind) {
      case MOVING -> 0;
      case STATION -> 500;
      case TERMINAL_STATION, NO_PATH -> 1000;
      case MANUAL_PASSENGER -> 2000;
      case MANUAL_EMPTY, EMPTY_SCHEDULE -> 7000;
      case ARRIVING -> 100;
      case SIGNAL_WAIT -> 100 + .3 * minecraftWaitTicks;
    };
  }

  public static int chainRetryTicks(int sameNameStops) {
    if (sameNameStops < 1) throw new IllegalArgumentException("Destinations");
    return sameNameStops > 1 ? 100 : 600;
  }

  private RailRoutePenalties() {}
}
