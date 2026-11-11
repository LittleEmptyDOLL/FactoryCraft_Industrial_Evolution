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
    RIGHT_SHIFT,
    POWER
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
      case POWER -> power(a, b);
    };
  }

  private static int power(int a, int b) {
    if (b < 0) return a == 1 ? 1 : a == -1 ? ((b & 1) == 0 ? 1 : -1) : 0;
    int out = 1;
    while (b != 0) {
      if ((b & 1) != 0) out *= a;
      b >>>= 1;
      if (b != 0) a *= a;
    }
    return out;
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
