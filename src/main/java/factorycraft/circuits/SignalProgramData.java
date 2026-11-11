package factorycraft.circuits;

import factorycraft.simulation.circuits.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Bounded schema used by saved programs and the validated menu protocol; no resource stacks. */
public final class SignalProgramData {
  public static CompoundTag key(SignalKey key) {
    var t = new CompoundTag();
    t.putString("Type", key.type().name());
    t.putString("Id", key.id());
    return t;
  }

  public static SignalKey key(CompoundTag t) {
    return new SignalKey(SignalKey.Type.valueOf(t.getString("Type")), t.getString("Id"));
  }

  public static CompoundTag operand(SignalProgram.Operand op) {
    var t = key(op.signal());
    t.putString("Mode", op.mode().name());
    t.putInt("Value", op.constant());
    t.putInt("Colors", op.colors());
    return t;
  }

  public static SignalProgram.Operand operand(CompoundTag t) {
    return new SignalProgram.Operand(
        SignalProgram.Mode.valueOf(t.getString("Mode")),
        key(t),
        t.getInt("Value"),
        t.getInt("Colors"));
  }

  public static CompoundTag output(SignalProgram.Output out) {
    var t = key(out.signal());
    t.putString("Mode", out.mode().name());
    t.putBoolean("Count", out.inputCount());
    t.putInt("Value", out.constant());
    t.putInt("Colors", out.colors());
    return t;
  }

  public static SignalProgram.Output output(CompoundTag t) {
    return new SignalProgram.Output(
        SignalProgram.Mode.valueOf(t.getString("Mode")),
        key(t),
        t.getBoolean("Count"),
        t.getInt("Value"),
        t.getInt("Colors"));
  }

  public static ListTag conditions(List<SignalProgram.Condition> list) {
    var rows = new ListTag();
    for (var c : list) {
      var t = new CompoundTag();
      t.put("Left", operand(c.first()));
      t.put("Right", operand(c.second()));
      t.putString("Compare", c.comparison().name());
      t.putBoolean("Or", c.orBefore());
      rows.add(t);
    }
    return rows;
  }

  public static List<SignalProgram.Condition> conditions(ListTag rows) {
    if (rows.size() > 16) throw new IllegalArgumentException("Too many conditions");
    var out = new ArrayList<SignalProgram.Condition>();
    for (var tag : rows) {
      var t = (CompoundTag) tag;
      out.add(
          new SignalProgram.Condition(
              operand(t.getCompound("Left")),
              SignalMath.Compare.valueOf(t.getString("Compare")),
              operand(t.getCompound("Right")),
              t.getBoolean("Or")));
    }
    return out;
  }

  private static ListTag outputs(List<SignalProgram.Output> list) {
    var out = new ListTag();
    list.forEach(o -> out.add(output(o)));
    return out;
  }

  private static List<SignalProgram.Output> outputs(ListTag list) {
    if (list.size() > 16) throw new IllegalArgumentException("Too many outputs");
    return list.stream().map(t -> output((CompoundTag) t)).toList();
  }

  public static CompoundTag save(SignalProgram p) {
    var t = new CompoundTag();
    t.putInt("Schema", 2);
    t.putString("Kind", p.kind().name());
    t.putBoolean("Enabled", p.enabled());
    var rows = new ListTag();
    for (var row : p.constants()) {
      var r = key(row.signal());
      r.putInt("Value", row.value());
      r.putInt("Group", row.group());
      r.putBoolean("Enabled", row.enabled());
      rows.add(r);
    }
    t.put("Rows", rows);
    t.put("Left", operand(p.left()));
    t.put("Right", operand(p.right()));
    t.putString("Arithmetic", p.arithmetic().name());
    t.put("Result", output(p.result()));
    t.put("Conditions", conditions(p.conditions()));
    t.put("Outputs", outputs(p.outputs()));
    t.put("Else", outputs(p.elseOutputs()));
    var s = p.selector();
    var a = new CompoundTag();
    a.putString("Mode", s.mode().name());
    a.putBoolean("Highest", s.highest());
    a.put("Index", operand(s.index()));
    a.putString("Compare", s.indexComparison().name());
    a.putInt("Interval", s.interval());
    a.put("Count", key(s.countSignal()));
    a.put("Tick", key(s.tickSignal()));
    a.put("Day", key(s.daySignal()));
    a.put("Length", key(s.dayLengthSignal()));
    t.put("Selector", a);
    return t;
  }

  public static SignalProgram load(CompoundTag t) {
    if (t.getInt("Schema") != 2)
      throw new IllegalArgumentException("Unknown circuit program schema");
    var rows = new ArrayList<SignalProgram.Row>();
    var list = t.getList("Rows", Tag.TAG_COMPOUND);
    if (list.size() > 64) throw new IllegalArgumentException("Too many constant rows");
    for (var tag : list) {
      var r = (CompoundTag) tag;
      rows.add(
          new SignalProgram.Row(
              key(r), r.getInt("Value"), r.getInt("Group"), r.getBoolean("Enabled")));
    }
    var s = t.getCompound("Selector");
    var selector =
        new SignalProgram.Selector(
            SignalProgram.Selection.valueOf(s.getString("Mode")),
            s.getBoolean("Highest"),
            operand(s.getCompound("Index")),
            SignalMath.Compare.valueOf(s.getString("Compare")),
            s.getInt("Interval"),
            key(s.getCompound("Count")),
            key(s.getCompound("Tick")),
            key(s.getCompound("Day")),
            key(s.getCompound("Length")));
    return new SignalProgram(
        SignalProgram.Kind.valueOf(t.getString("Kind")),
        t.getBoolean("Enabled"),
        rows,
        operand(t.getCompound("Left")),
        operand(t.getCompound("Right")),
        SignalMath.Arithmetic.valueOf(t.getString("Arithmetic")),
        output(t.getCompound("Result")),
        conditions(t.getList("Conditions", 10)),
        outputs(t.getList("Outputs", 10)),
        outputs(t.getList("Else", 10)),
        selector);
  }

  public static ListTag signals(SignalSet set) {
    var out = new ListTag();
    set.values().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            e -> {
              var row = key(e.getKey());
              row.putInt("Value", e.getValue());
              out.add(row);
            });
    return out;
  }

  public static SignalSet signals(ListTag list) {
    if (list.size() > SignalSet.LIMIT) throw new IllegalArgumentException("Too many signals");
    var out = new SignalSet.Builder();
    for (var row : list) {
      var t = (CompoundTag) row;
      out.add(key(t), t.getInt("Value"));
    }
    return out.build();
  }

  public static SignalKey legacy(net.minecraft.resources.ResourceLocation id) {
    return id.equals(CircuitProgram.A) ? SignalKey.A : SignalKey.item(id.toString());
  }

  public static SignalProgram legacy(CircuitNodeBlockEntity n) {
    var old = n.program();
    var kind =
        n.kind() == factorycraft.blocks.FactoryKind.ARITHMETIC
            ? SignalProgram.Kind.ARITHMETIC
            : n.kind() == factorycraft.blocks.FactoryKind.DECIDER
                ? SignalProgram.Kind.DECIDER
                : SignalProgram.Kind.CONSTANT;
    var d = SignalProgram.defaults(kind);
    var in = SignalProgram.Operand.signal(legacy(old.input()));
    var out =
        new SignalProgram.Output(
            SignalProgram.Mode.SIGNAL, legacy(old.output()), old.outputCount(), 1, 15);
    return new SignalProgram(
        kind,
        true,
        List.of(new SignalProgram.Row(legacy(old.output()), old.operand(), 0, true)),
        in,
        SignalProgram.Operand.number(old.operand()),
        SignalMath.Arithmetic.values()[Math.min(9, old.operation())],
        out,
        List.of(
            new SignalProgram.Condition(
                in,
                SignalMath.Compare.values()[Math.min(5, old.operation())],
                SignalProgram.Operand.number(old.operand()),
                false)),
        List.of(out),
        List.of(),
        d.selector());
  }

  private SignalProgramData() {}
}
