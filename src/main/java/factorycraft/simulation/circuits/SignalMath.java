package factorycraft.simulation.circuits;

/** Factorio-style signed 32-bit signals. Ordinary Java integer overflow wraps deliberately. */
public final class SignalMath {
  public enum Arithmetic {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    REMAINDER,
    AND,
    OR,
    XOR,
    LEFT_SHIFT,
    RIGHT_SHIFT
  }

  public enum Compare {
    GREATER,
    LESS,
    EQUAL,
    NOT_EQUAL,
    AT_LEAST,
    AT_MOST
  }

  public static int calculate(Arithmetic op, int a, int b) {
    return switch (op) {
      case ADD -> a + b;
      case SUBTRACT -> a - b;
      case MULTIPLY -> a * b;
      case DIVIDE -> b == 0 ? 0 : a / b;
      case REMAINDER -> b == 0 ? 0 : a % b;
      case AND -> a & b;
      case OR -> a | b;
      case XOR -> a ^ b;
      case LEFT_SHIFT -> a << b;
      case RIGHT_SHIFT -> a >> b;
    };
  }

  public static boolean test(Compare op, int a, int b) {
    return switch (op) {
      case GREATER -> a > b;
      case LESS -> a < b;
      case EQUAL -> a == b;
      case NOT_EQUAL -> a != b;
      case AT_LEAST -> a >= b;
      case AT_MOST -> a <= b;
    };
  }

  private SignalMath() {}
}
