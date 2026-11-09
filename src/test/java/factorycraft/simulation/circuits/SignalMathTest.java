package factorycraft.simulation.circuits;

import static factorycraft.simulation.circuits.SignalMath.Arithmetic.*;
import static factorycraft.simulation.circuits.SignalMath.Compare.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SignalMathTest {
  @Test
  void allOperationsAndSignedOverflowAreDefined() {
    assertEquals(10, SignalMath.calculate(ADD, 7, 3));
    assertEquals(4, SignalMath.calculate(SUBTRACT, 7, 3));
    assertEquals(21, SignalMath.calculate(MULTIPLY, 7, 3));
    assertEquals(-2, SignalMath.calculate(DIVIDE, -7, 3));
    assertEquals(-1, SignalMath.calculate(REMAINDER, -7, 3));
    assertEquals(0, SignalMath.calculate(DIVIDE, 8, 0));
    assertEquals(0, SignalMath.calculate(REMAINDER, 8, 0));
    assertEquals(2, SignalMath.calculate(AND, 6, 3));
    assertEquals(7, SignalMath.calculate(OR, 6, 3));
    assertEquals(5, SignalMath.calculate(XOR, 6, 3));
    assertEquals(24, SignalMath.calculate(LEFT_SHIFT, 3, 3));
    assertEquals(-2, SignalMath.calculate(RIGHT_SHIFT, -8, 2));
    assertEquals(Integer.MIN_VALUE, SignalMath.calculate(ADD, Integer.MAX_VALUE, 1));
    assertEquals(Integer.MIN_VALUE, SignalMath.calculate(DIVIDE, Integer.MIN_VALUE, -1));
    assertEquals(1, SignalMath.calculate(LEFT_SHIFT, 1, 32));
  }

  @Test
  void comparisonsUseSignedIntValues() {
    assertTrue(SignalMath.test(GREATER, 3, 2));
    assertTrue(SignalMath.test(LESS, -3, 2));
    assertTrue(SignalMath.test(EQUAL, 2, 2));
    assertTrue(SignalMath.test(NOT_EQUAL, 2, 3));
    assertTrue(SignalMath.test(AT_LEAST, 2, 2));
    assertTrue(SignalMath.test(AT_MOST, 2, 2));
    assertFalse(SignalMath.test(GREATER, Integer.MIN_VALUE, Integer.MAX_VALUE));
  }
}
