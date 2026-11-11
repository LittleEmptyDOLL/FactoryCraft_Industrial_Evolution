package factorycraft.simulation.circuits;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class SignalProgramTest {
  private static final SignalKey IRON = SignalKey.item("test:iron"),
      COPPER = SignalKey.item("test:copper"),
      A = SignalKey.A;

  private static SignalSet input() {
    return SignalSet.copy(Map.of(IRON, 5, COPPER, -3, A, 2));
  }

  private static SignalProgram program(
      SignalProgram.Kind kind,
      List<SignalProgram.Condition> conditions,
      List<SignalProgram.Output> outputs,
      List<SignalProgram.Output> otherwise) {
    var p = SignalProgram.defaults(kind);
    return new SignalProgram(
        kind,
        true,
        p.constants(),
        p.left(),
        p.right(),
        p.arithmetic(),
        p.result(),
        conditions,
        outputs,
        otherwise,
        p.selector());
  }

  private static SignalSet eval(SignalProgram p, SignalSet in) {
    return p.evaluate(
        SignalProgram.Inputs.single(in), new SignalProgram.Clock(42, 13, 24000), 71, k -> 64);
  }

  private static SignalProgram.Condition condition(
      SignalProgram.Mode mode, SignalMath.Compare compare, int rhs, boolean or) {
    return new SignalProgram.Condition(
        new SignalProgram.Operand(mode, IRON, 0, 15),
        compare,
        SignalProgram.Operand.number(rhs),
        or);
  }

  @Test
  void typeIdentityUnknownKeysZerosAndInt32AreSafe() {
    var a = SignalKey.item("missing:same");
    var b = SignalKey.fluid("missing:same");
    assertNotEquals(a, b);
    var set =
        new SignalSet.Builder().add(a, Integer.MAX_VALUE).add(a, 1).add(b, 3).add(b, -3).build();
    assertEquals(Integer.MIN_VALUE, set.get(a));
    assertEquals(1, set.size());
    assertThrows(IllegalArgumentException.class, () -> SignalKey.item("bad ID"));
  }

  @Test
  void fourColorReadsSumRepeatedSourcesAndCanSelectIndependentMasks() {
    var in =
        new SignalProgram.Inputs(
            SignalSet.of(A, 4), SignalSet.of(A, 4), SignalSet.of(A, -1), SignalSet.of(IRON, 2));
    assertEquals(7, in.read(7).get(A));
    assertEquals(3, in.read(6).get(A));
    assertEquals(0, in.read(0).size());
    assertEquals(2, in.read(8).get(IRON));
  }

  @Test
  void eachArithmeticMapsAndSpecificOutputSumsEveryActiveInput() {
    var p = SignalProgram.defaults(SignalProgram.Kind.ARITHMETIC);
    var each = new SignalProgram.Operand(SignalProgram.Mode.EACH, A, 0, 15);
    var eachOut = new SignalProgram.Output(SignalProgram.Mode.EACH, A, false, 1, 15);
    var q =
        new SignalProgram(
            p.kind(),
            true,
            List.of(),
            each,
            SignalProgram.Operand.number(2),
            SignalMath.Arithmetic.MULTIPLY,
            eachOut,
            List.of(),
            List.of(),
            List.of(),
            p.selector());
    assertEquals(Map.of(IRON, 10, COPPER, -6, A, 4), eval(q, input()).values());
    var sum =
        new SignalProgram(
            q.kind(),
            true,
            List.of(),
            each,
            q.right(),
            q.arithmetic(),
            SignalProgram.Output.single(A, 1),
            List.of(),
            List.of(),
            List.of(),
            p.selector());
    assertEquals(8, eval(sum, input()).get(A));
    assertEquals(0, eval(q, SignalSet.EMPTY).size());
  }

  @Test
  void elseOutputsApplyPerEachAndDoNotLoseNegativeValues() {
    var condition = condition(SignalProgram.Mode.EACH, SignalMath.Compare.GREATER, 0, false);
    var pass = new SignalProgram.Output(SignalProgram.Mode.EACH, A, true, 1, 15);
    var fail = SignalProgram.Output.single(SignalKey.COUNT, 7);
    var p = program(SignalProgram.Kind.DECIDER, List.of(condition), List.of(pass), List.of(fail));
    assertEquals(Map.of(IRON, 5, A, 2, SignalKey.COUNT, 7), eval(p, input()).values());
    assertEquals(SignalSet.EMPTY, eval(p, SignalSet.EMPTY));
  }

  @Test
  void anythingFalseEverythingTrueOnEmptyAndInputCountUsesOutputSignal() {
    var yes = SignalProgram.Output.single(A, 11);
    var no = SignalProgram.Output.single(A, -4);
    for (var mode : List.of(SignalProgram.Mode.ANYTHING, SignalProgram.Mode.EVERYTHING)) {
      var p =
          program(
              SignalProgram.Kind.DECIDER,
              List.of(condition(mode, SignalMath.Compare.GREATER, 0, false)),
              List.of(yes),
              List.of(no));
      assertEquals(
          mode == SignalProgram.Mode.EVERYTHING ? 11 : -4, eval(p, SignalSet.EMPTY).get(A));
    }
    var c = condition(SignalProgram.Mode.SIGNAL, SignalMath.Compare.GREATER, 0, false);
    var p =
        program(
            SignalProgram.Kind.DECIDER,
            List.of(c),
            List.of(new SignalProgram.Output(SignalProgram.Mode.SIGNAL, COPPER, true, 1, 15)),
            List.of());
    assertEquals(-3, eval(p, input()).get(COPPER));
  }

  @Test
  void andHasPrecedenceOverOrWithMultipleConditionsAndSignalOperands() {
    var a = condition(SignalProgram.Mode.SIGNAL, SignalMath.Compare.LESS, 0, false);
    var b = condition(SignalProgram.Mode.SIGNAL, SignalMath.Compare.GREATER, 0, true);
    var c = condition(SignalProgram.Mode.SIGNAL, SignalMath.Compare.EQUAL, 5, false);
    var p =
        program(
            SignalProgram.Kind.DECIDER,
            List.of(a, b, c),
            List.of(SignalProgram.Output.single(A, 1)),
            List.of());
    assertEquals(1, eval(p, input()).get(A));
  }

  @Test
  void constantSectionsHaveIndependentEnabledRowsAndSumDuplicateSignals() {
    var d = SignalProgram.defaults(SignalProgram.Kind.CONSTANT);
    var p =
        new SignalProgram(
            d.kind(),
            true,
            List.of(
                new SignalProgram.Row(A, 9, 0, true),
                new SignalProgram.Row(A, -2, 1, true),
                new SignalProgram.Row(IRON, 7, 2, false)),
            d.left(),
            d.right(),
            d.arithmetic(),
            d.result(),
            d.conditions(),
            d.outputs(),
            d.elseOutputs(),
            d.selector());
    assertEquals(Map.of(A, 7), eval(p, SignalSet.EMPTY).values());
  }

  @Test
  void selectorSupportsIndexComparatorsNegativeValuesAndSingleSignalOutOfRange() {
    var d = SignalProgram.defaults(SignalProgram.Kind.SELECTOR);
    var s =
        new SignalProgram.Selector(
            SignalProgram.Selection.SELECT,
            true,
            SignalProgram.Operand.number(2),
            SignalMath.Compare.LESS,
            1,
            A,
            A,
            SignalKey.DAY,
            SignalKey.DAY_LENGTH);
    var p =
        new SignalProgram(
            d.kind(),
            true,
            d.constants(),
            d.left(),
            d.right(),
            d.arithmetic(),
            d.result(),
            d.conditions(),
            d.outputs(),
            d.elseOutputs(),
            s);
    assertEquals(Map.of(IRON, 5, A, 2), eval(p, input()).values());
    var single =
        new SignalProgram.Selector(
            s.mode(),
            false,
            SignalProgram.Operand.number(99),
            SignalMath.Compare.EQUAL,
            1,
            A,
            A,
            SignalKey.DAY,
            SignalKey.DAY_LENGTH);
    p =
        new SignalProgram(
            d.kind(),
            true,
            d.constants(),
            d.left(),
            d.right(),
            d.arithmetic(),
            d.result(),
            d.conditions(),
            d.outputs(),
            d.elseOutputs(),
            single);
    assertEquals(-7, eval(p, SignalSet.of(COPPER, -7)).get(COPPER));
  }

  @Test
  void selectorTimeCountStackSizeRandomAndUnsupportedModesAreExplicit() {
    var d = SignalProgram.defaults(SignalProgram.Kind.SELECTOR);
    for (var mode : SignalProgram.Selection.values()) {
      var s =
          new SignalProgram.Selector(
              mode,
              true,
              SignalProgram.Operand.number(0),
              SignalMath.Compare.EQUAL,
              10,
              A,
              SignalKey.TICK,
              SignalKey.DAY,
              SignalKey.DAY_LENGTH);
      var p =
          new SignalProgram(
              d.kind(),
              true,
              d.constants(),
              d.left(),
              d.right(),
              d.arithmetic(),
              d.result(),
              d.conditions(),
              d.outputs(),
              d.elseOutputs(),
              s);
      var out = eval(p, input());
      switch (mode) {
        case TIME ->
            assertEquals(
                Map.of(SignalKey.TICK, 42, SignalKey.DAY, 13, SignalKey.DAY_LENGTH, 24000),
                out.values());
        case COUNT -> assertEquals(3, out.get(A));
        case STACK_SIZE -> assertEquals(Map.of(IRON, 64, COPPER, 64), out.values());
        case RANDOM -> {
          assertEquals(1, out.size());
          assertEquals(out, eval(p, input()));
        }
        case SELECT -> assertEquals(Map.of(IRON, 5), out.values());
        default -> {
          assertFalse(s.available());
          assertEquals(SignalSet.EMPTY, out);
        }
      }
    }
  }

  @Test
  void powerAndAllIntegerOperationsWrapWithoutFloatingPointSaturation() {
    assertEquals(Integer.MIN_VALUE, SignalMath.calculate(SignalMath.Arithmetic.POWER, 2, 31));
    assertEquals(0, SignalMath.calculate(SignalMath.Arithmetic.POWER, 2, 32));
    assertEquals(1, SignalMath.calculate(SignalMath.Arithmetic.POWER, 0, 0));
    assertEquals(0, SignalMath.calculate(SignalMath.Arithmetic.POWER, 4, -2));
  }

  @Test
  void validEachProgramReportsOutputCapacityWithoutTruncatingInputOrDefinition() {
    var map = new HashMap<SignalKey, Integer>();
    for (int i = 0; i < 4096; i++) map.put(SignalKey.virtual("limit_" + i), 1);
    var input = SignalSet.copy(map);
    var p =
        program(
            SignalProgram.Kind.DECIDER,
            List.of(condition(SignalProgram.Mode.EACH, SignalMath.Compare.GREATER, 0, false)),
            List.of(
                new SignalProgram.Output(SignalProgram.Mode.EACH, A, true, 1, 15),
                SignalProgram.Output.single(SignalKey.virtual("limit_total"), 1)),
            List.of());
    assertThrows(IllegalArgumentException.class, () -> eval(p, input));
    assertEquals(4096, input.size());
    assertEquals(2, p.outputs().size());
  }
}
