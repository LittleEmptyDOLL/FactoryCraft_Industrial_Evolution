package factorycraft.circuits;

/** Persistent color IDs: never reorder existing red=0 and green=1 links. */
public final class WireColors {
  public static final int COUNT = 4;
  public static final int MAX_LINKS = 2 * COUNT * 8;

  public static boolean valid(int color) {
    return color >= 0 && color < COUNT;
  }

  public static int rgb(int color) {
    return switch (color) {
      case 0 -> 0xff1f1a;
      case 1 -> 0x1aff1a;
      case 2 -> 0x4080ff;
      case 3 -> 0xf0f0f0;
      default -> throw new IllegalArgumentException("Unknown wire color");
    };
  }

  private WireColors() {}
}
