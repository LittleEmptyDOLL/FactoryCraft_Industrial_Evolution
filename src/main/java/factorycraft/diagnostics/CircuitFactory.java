package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.circuits.*;
import factorycraft.core.FactoryCommands;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import factorycraft.simulation.circuits.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;

/** Finite direct-chest feedback and a staged two-color chain; no fabricated FE/results. */
public final class CircuitFactory {
  public static CircuitNodeBlockEntity node(
      ServerLevel level,
      BlockPos pos,
      FactoryKind kind,
      SignalProgram program,
      int input,
      int output) {
    StructurePlacement.place(level, pos, Content.FACTORY.get(kind).get().defaultBlockState());
    var n = (CircuitNodeBlockEntity) level.getBlockEntity(pos);
    configure(n.circuit(true), settings(false, false, List.of(), input, output), program);
    return n;
  }

  public static CircuitSettings settings(
      boolean contents,
      boolean gate,
      List<SignalProgram.Condition> conditions,
      int input,
      int output) {
    var t = CircuitSettings.defaults(false).save();
    t.putBoolean("Contents", contents);
    t.putBoolean("Gate", gate);
    t.put("Conditions", SignalProgramData.conditions(conditions));
    t.putInt("InputColors", input);
    t.putInt("OutputColors", output);
    return CircuitSettings.load(t);
  }

  /** Fixture initialization before BE onLoad; normal players must use the validated LogicMenu. */
  public static void configure(
      CircuitAttachment n, CircuitSettings settings, SignalProgram program) {
    var direct = n.save();
    direct.put("Settings", settings.save());
    direct.put("Program", SignalProgramData.save(program));
    direct.putBoolean("Legacy", false);
    n.load(direct);
    n.persistent();
    CircuitSimulation.add(n);
  }

  public static void wire(CircuitAttachment a, int pa, CircuitAttachment b, int pb, int color) {
    a.links.add(new CircuitLink(b.pos(), b.identity(), pa, pb, color));
    b.links.add(new CircuitLink(a.pos(), a.identity(), pb, pa, color));
    a.configured = b.configured = true;
    a.changed();
    b.changed();
    CircuitSimulation.add(a);
    CircuitSimulation.add(b);
  }

  public static SignalProgram constant(SignalKey key, int value) {
    var t = SignalProgramData.save(SignalProgram.defaults(SignalProgram.Kind.CONSTANT));
    var rows = new net.minecraft.nbt.ListTag();
    var row = SignalProgramData.key(key);
    row.putInt("Value", value);
    row.putBoolean("Enabled", true);
    rows.add(row);
    t.put("Rows", rows);
    return SignalProgramData.load(t);
  }

  public static SignalProgram.Condition condition(
      SignalKey key, SignalMath.Compare compare, int value) {
    return new SignalProgram.Condition(
        SignalProgram.Operand.signal(key), compare, SignalProgram.Operand.number(value), false);
  }

  public static void build(ServerLevel level, BlockPos o) {
    FactoryCommands.machine(level, o.offset(8, 0, 5), FactoryKind.FAST_INSERTER, Direction.NORTH);
    var arm = (InserterBlockEntity) level.getBlockEntity(o.offset(8, 0, 5));
    for (var p : List.of(arm.sourcePosition(), arm.targetPosition()))
      level.setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
    ((ChestBlockEntity) level.getBlockEntity(arm.sourcePosition()))
        .setItem(0, new ItemStack(Items.RAW_IRON, 32));
    var chest = CircuitAccess.at(level, arm.targetPosition());
    var ac = arm.circuit(true);
    configure(chest, settings(true, false, List.of(), 0, 1), chest.program());
    configure(
        ac,
        settings(
            false,
            true,
            List.of(condition(SignalKey.item("minecraft:raw_iron"), SignalMath.Compare.LESS, 8)),
            1,
            0),
        ac.program());
    wire(chest, 0, ac, 0, 0);
    var a = node(level, o.offset(16, 0, 5), FactoryKind.CONSTANT, constant(SignalKey.A, 4), 0, 1);
    var math = SignalProgramData.save(SignalProgram.defaults(SignalProgram.Kind.ARITHMETIC));
    math.put("Right", SignalProgramData.operand(SignalProgram.Operand.number(3)));
    var b =
        node(level, o.offset(20, 0, 4), FactoryKind.ARITHMETIC, SignalProgramData.load(math), 1, 2);
    var decide = SignalProgramData.save(SignalProgram.defaults(SignalProgram.Kind.DECIDER));
    decide.put(
        "Conditions",
        SignalProgramData.conditions(
            List.of(condition(SignalKey.A, SignalMath.Compare.GREATER, 6))));
    var outputs = new net.minecraft.nbt.ListTag();
    outputs.add(SignalProgramData.output(SignalProgram.Output.single(SignalKey.A, 1)));
    decide.put("Outputs", outputs);
    var c =
        node(level, o.offset(24, 0, 4), FactoryKind.DECIDER, SignalProgramData.load(decide), 2, 1);
    wire(a.circuit(true), 0, b.circuit(true), 0, 0);
    wire(b.circuit(true), 1, c.circuit(true), 0, 1);
    var generator =
        FactoryCommands.machine(level, o.offset(2, 0, 9), FactoryKind.GENERATOR, Direction.NORTH);
    generator.items.setStackInSlot(8, new ItemStack(Items.COAL, 2));
    FactoryCommands.machine(level, o.offset(6, 0, 9), FactoryKind.POLE, Direction.NORTH);
    FactoryCommands.machine(level, o.offset(9, 0, 8), FactoryKind.MEDIUM_POLE, Direction.NORTH);
  }

  private CircuitFactory() {}
}
