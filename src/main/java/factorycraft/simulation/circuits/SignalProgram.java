package factorycraft.simulation.circuits;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * Structured Factorio 2.1 logic. It has no world, item ownership, rendering or registry dependency.
 */
public record SignalProgram(
    Kind kind,
    boolean enabled,
    List<Row> constants,
    Operand left,
    Operand right,
    SignalMath.Arithmetic arithmetic,
    Output result,
    List<Condition> conditions,
    List<Output> outputs,
    List<Output> elseOutputs,
    Selector selector) {
  public enum Kind {
    CONSTANT,
    ARITHMETIC,
    DECIDER,
    SELECTOR
  }

  public enum Mode {
    SIGNAL,
    CONSTANT,
    EACH,
    ANYTHING,
    EVERYTHING
  }

  public enum Selection {
    SELECT,
    COUNT,
    RANDOM,
    STACK_SIZE,
    TIME,
    ROCKET_CAPACITY,
    QUALITY_FILTER,
    QUALITY_TRANSFER,
    QUALITY_SELECT
  }

  public record Row(SignalKey signal, int value, int group, boolean enabled) {
    public Row {
      Objects.requireNonNull(signal);
      if (group < 0 || group > 15) throw new IllegalArgumentException("Invalid constant section");
    }
  }

  public record Operand(Mode mode, SignalKey signal, int constant, int colors) {
    public Operand {
      Objects.requireNonNull(mode);
      if (signal == null || colors < 0 || colors > 15)
        throw new IllegalArgumentException("Invalid operand");
    }

    public static Operand number(int n) {
      return new Operand(Mode.CONSTANT, SignalKey.A, n, 15);
    }

    public static Operand signal(SignalKey key) {
      return new Operand(Mode.SIGNAL, key, 0, 15);
    }
  }

  public record Condition(
      Operand first, SignalMath.Compare comparison, Operand second, boolean orBefore) {
    public Condition {
      Objects.requireNonNull(first);
      Objects.requireNonNull(second);
      Objects.requireNonNull(comparison);
      if (second.mode() == Mode.ANYTHING || second.mode() == Mode.EVERYTHING)
        throw new IllegalArgumentException("Invalid second condition operand");
    }
  }

  public record Output(Mode mode, SignalKey signal, boolean inputCount, int constant, int colors) {
    public Output {
      if (signal == null || mode == Mode.CONSTANT || colors < 0 || colors > 15)
        throw new IllegalArgumentException("Invalid output");
    }

    public static Output single(SignalKey key, int n) {
      return new Output(Mode.SIGNAL, key, false, n, 15);
    }
  }

  public record Selector(
      Selection mode,
      boolean highest,
      Operand index,
      SignalMath.Compare indexComparison,
      int interval,
      SignalKey countSignal,
      SignalKey tickSignal,
      SignalKey daySignal,
      SignalKey dayLengthSignal) {
    public Selector {
      Objects.requireNonNull(mode);
      Objects.requireNonNull(index);
      Objects.requireNonNull(indexComparison);
      Objects.requireNonNull(countSignal);
      Objects.requireNonNull(tickSignal);
      Objects.requireNonNull(daySignal);
      Objects.requireNonNull(dayLengthSignal);
      if (index.mode() != Mode.SIGNAL && index.mode() != Mode.CONSTANT)
        throw new IllegalArgumentException("Selector index requires a number or signal");
      if (interval < 1 || interval > 1200000)
        throw new IllegalArgumentException("Invalid random interval");
    }

    public static Selector defaults() {
      return new Selector(
          Selection.SELECT,
          true,
          Operand.number(0),
          SignalMath.Compare.EQUAL,
          1,
          SignalKey.COUNT,
          SignalKey.TICK,
          SignalKey.DAY,
          SignalKey.DAY_LENGTH);
    }

    public boolean available() {
      return mode != Selection.ROCKET_CAPACITY
          && mode != Selection.QUALITY_FILTER
          && mode != Selection.QUALITY_TRANSFER
          && mode != Selection.QUALITY_SELECT;
    }
  }

  public static SignalProgram defaults(Kind kind) {
    return new SignalProgram(
        kind,
        true,
        List.of(),
        Operand.signal(SignalKey.A),
        Operand.number(0),
        SignalMath.Arithmetic.ADD,
        Output.single(SignalKey.A, 1),
        List.of(),
        List.of(),
        List.of(),
        Selector.defaults());
  }

  public SignalProgram {
    Objects.requireNonNull(kind);
    constants = List.copyOf(constants);
    conditions = List.copyOf(conditions);
    outputs = List.copyOf(outputs);
    elseOutputs = List.copyOf(elseOutputs);
    Objects.requireNonNull(left);
    Objects.requireNonNull(right);
    Objects.requireNonNull(arithmetic);
    Objects.requireNonNull(result);
    Objects.requireNonNull(selector);
    if (constants.size() > 64
        || conditions.size() > 16
        || outputs.size() > 16
        || elseOutputs.size() > 16)
      throw new IllegalArgumentException("Program row limit exceeded");
    if (kind == Kind.ARITHMETIC
        && (left.mode() == Mode.ANYTHING
            || left.mode() == Mode.EVERYTHING
            || right.mode() == Mode.ANYTHING
            || right.mode() == Mode.EVERYTHING
            || result.mode() == Mode.ANYTHING
            || result.mode() == Mode.EVERYTHING
            || result.mode() == Mode.EACH && left.mode() != Mode.EACH && right.mode() != Mode.EACH))
      throw new IllegalArgumentException("Invalid arithmetic wildcard");
    boolean each =
        conditions.stream()
            .anyMatch(c -> c.first.mode() == Mode.EACH || c.second.mode() == Mode.EACH);
    if (kind == Kind.DECIDER
        && ((!each
                && (outputs.stream().anyMatch(o -> o.mode == Mode.EACH)
                    || elseOutputs.stream().anyMatch(o -> o.mode == Mode.EACH)))
            || (each
                && (outputs.stream().anyMatch(o -> o.mode == Mode.EVERYTHING)
                    || elseOutputs.stream().anyMatch(o -> o.mode == Mode.EVERYTHING)))))
      throw new IllegalArgumentException("Invalid decider output wildcard");
  }

  public static final class Inputs {
    private final SignalSet[] colors = new SignalSet[4], merged = new SignalSet[16];

    public Inputs(SignalSet... sets) {
      if (sets.length != 4) throw new IllegalArgumentException();
      System.arraycopy(sets, 0, colors, 0, 4);
      merged[0] = SignalSet.EMPTY;
    }

    public static Inputs single(SignalSet set) {
      return new Inputs(set, SignalSet.EMPTY, SignalSet.EMPTY, SignalSet.EMPTY);
    }

    public SignalSet read(int mask) {
      mask &= 15;
      if (merged[mask] == null) {
        var out = new SignalSet.Builder();
        for (int c = 0; c < 4; c++) if ((mask & (1 << c)) != 0) out.addAll(colors[c]);
        merged[mask] = out.build();
      }
      return merged[mask];
    }
  }

  private static int value(Operand op, Inputs in, SignalKey each) {
    return op.mode == Mode.CONSTANT
        ? op.constant
        : op.mode == Mode.EACH
            ? (each == null ? 0 : in.read(op.colors).get(each))
            : in.read(op.colors).get(op.signal);
  }

  private static boolean test(Condition condition, Inputs in, SignalKey each) {
    var op = condition.first;
    if (op.mode != Mode.ANYTHING && op.mode != Mode.EVERYTHING)
      return SignalMath.test(
          condition.comparison, value(op, in, each), value(condition.second, in, each));
    var signals = in.read(op.colors).values();
    boolean all = op.mode == Mode.EVERYTHING;
    for (var e : signals.entrySet()) {
      boolean passed =
          SignalMath.test(
              condition.comparison, e.getValue(), value(condition.second, in, e.getKey()));
      if (passed && !all) return true;
      if (!passed && all) return false;
    }
    return all;
  }

  public static boolean matches(List<Condition> conditions, Inputs in, SignalKey each) {
    if (conditions.isEmpty()) return false;
    boolean disjunction = false, group = true;
    for (int i = 0; i < conditions.size(); i++) {
      var c = conditions.get(i);
      if (i > 0 && c.orBefore) {
        disjunction |= group;
        group = true;
      }
      group &= test(c, in, each);
    }
    return disjunction || group;
  }

  private static List<SignalKey> keys(SignalSet signals) {
    return signals.values().keySet().stream().sorted().toList();
  }

  private static void output(
      SignalSet.Builder result,
      Output output,
      Inputs in,
      List<SignalKey> candidates,
      boolean each) {
    if (output.mode == Mode.SIGNAL) {
      if (each) {
        for (var key : candidates)
          result.add(
              output.signal, output.inputCount ? in.read(output.colors).get(key) : output.constant);
      } else
        result.add(
            output.signal,
            output.inputCount ? in.read(output.colors).get(output.signal) : output.constant);
      return;
    }
    var selected = each ? candidates : keys(in.read(output.colors));
    int limit = output.mode == Mode.ANYTHING ? Math.min(1, selected.size()) : selected.size();
    for (int i = 0; i < limit; i++) {
      var key = selected.get(i);
      result.add(key, output.inputCount ? in.read(output.colors).get(key) : output.constant);
    }
  }

  public record Clock(long tick, int dayTick, int dayLength) {}

  public SignalSet evaluate(
      Inputs in, Clock clock, long randomSeed, ToIntFunction<SignalKey> stackSize) {
    if (!enabled) return SignalSet.EMPTY;
    var out = new SignalSet.Builder();
    switch (kind) {
      case CONSTANT -> {
        for (var row : constants) if (row.enabled) out.add(row.signal, row.value);
      }
      case ARITHMETIC -> {
        boolean each = left.mode == Mode.EACH || right.mode == Mode.EACH;
        var signals =
            each
                ? keys(
                    in.read(
                        (left.mode == Mode.EACH ? left.colors : 0)
                            | (right.mode == Mode.EACH ? right.colors : 0)))
                : Collections.singletonList((SignalKey) null);
        for (var key : signals)
          out.add(
              result.mode == Mode.EACH ? key : result.signal,
              SignalMath.calculate(arithmetic, value(left, in, key), value(right, in, key)));
      }
      case DECIDER -> {
        boolean each =
            conditions.stream()
                .anyMatch(c -> c.first.mode == Mode.EACH || c.second.mode == Mode.EACH);
        if (each) {
          int mask = 0;
          for (var c : conditions) {
            if (c.first.mode == Mode.EACH) mask |= c.first.colors;
            if (c.second.mode == Mode.EACH) mask |= c.second.colors;
          }
          var passed = new ArrayList<SignalKey>();
          var failed = new ArrayList<SignalKey>();
          for (var key : keys(in.read(mask)))
            (matches(conditions, in, key) ? passed : failed).add(key);
          for (var o : outputs) output(out, o, in, passed, true);
          for (var o : elseOutputs) output(out, o, in, failed, true);
        } else {
          for (var o : matches(conditions, in, null) ? outputs : elseOutputs)
            output(out, o, in, List.of(), false);
        }
      }
      case SELECTOR -> {
        if (!selector.available()) return SignalSet.EMPTY;
        var signals = in.read(15);
        switch (selector.mode) {
          case COUNT -> out.add(selector.countSignal, signals.size());
          case TIME -> {
            out.add(selector.tickSignal, (int) clock.tick);
            out.add(selector.daySignal, clock.dayTick);
            out.add(selector.dayLengthSignal, clock.dayLength);
          }
          case STACK_SIZE -> {
            for (var key : signals.values().keySet())
              if (key.type() == SignalKey.Type.ITEM)
                out.add(key, Math.max(0, stackSize.applyAsInt(key)));
          }
          case RANDOM -> {
            var list = keys(signals);
            if (!list.isEmpty()) {
              long n = randomSeed ^ (clock.tick / selector.interval) * 0x9e3779b97f4a7c15L;
              n ^= n >>> 30;
              n *= 0xbf58476d1ce4e5b9L;
              n ^= n >>> 27;
              n *= 0x94d049bb133111ebL;
              n ^= n >>> 31;
              var selected = list.get((int) Math.floorMod(n, list.size()));
              out.add(selected, signals.get(selected));
            }
          }
          case SELECT -> {
            var list = new ArrayList<>(signals.values().entrySet());
            Comparator<Map.Entry<SignalKey, Integer>> order =
                Comparator.comparingInt(Map.Entry::getValue);
            if (selector.highest) order = order.reversed();
            list.sort(order.thenComparing(Map.Entry::getKey));
            int index = value(selector.index, in, null);
            for (int i = 0; i < list.size(); i++)
              if (SignalMath.test(selector.indexComparison, i, index)
                  || list.size() == 1 && selector.indexComparison == SignalMath.Compare.EQUAL)
                out.add(list.get(i).getKey(), list.get(i).getValue());
          }
          default -> {}
        }
      }
    }
    return out.build();
  }
}
