package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.circuits.*;
import factorycraft.diagnostics.CircuitFactory;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.research.*;
import factorycraft.simulation.circuits.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class DirectCircuitGameTests {
  private static final SignalKey IRON = SignalKey.item("minecraft:raw_iron"),
      WATER = SignalKey.fluid("minecraft:water"),
      A = SignalKey.A;

  private static CircuitNodeBlockEntity node(
      GameTestHelper h, FactoryKind kind, int x, SignalProgram program, int input, int output) {
    return CircuitFactory.node(
        h.getLevel(), h.absolutePos(new BlockPos(x, 3, 5)), kind, program, input, output);
  }

  private static void configure(
      GameTestHelper h, CircuitAttachment n, CircuitSettings settings, SignalProgram program) {
    h.assertTrue(n.configure(settings, program), "direct configuration rejected: " + n.pos());
  }

  private static void connect(
      GameTestHelper h, CircuitAttachment a, int ap, CircuitAttachment b, int bp, int color) {
    h.assertTrue(CircuitSimulation.connect(a, ap, b, bp, color), "direct wire rejected");
  }

  private static CircuitSettings settings(
      boolean read, boolean gate, List<SignalProgram.Condition> conditions, int in, int out) {
    return CircuitFactory.settings(read, gate, conditions, in, out);
  }

  private static SignalProgram math() {
    var p = SignalProgram.defaults(SignalProgram.Kind.ARITHMETIC);
    var each = new SignalProgram.Operand(SignalProgram.Mode.EACH, A, 0, 15);
    return new SignalProgram(
        p.kind(),
        true,
        List.of(),
        each,
        SignalProgram.Operand.number(2),
        SignalMath.Arithmetic.MULTIPLY,
        new SignalProgram.Output(SignalProgram.Mode.EACH, A, false, 1, 15),
        List.of(),
        List.of(),
        List.of(),
        p.selector());
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 100)
  public static void typedMapsFourColorsIndependentMasksAndNoLoopDuplicates(GameTestHelper h) {
    var p = SignalProgram.defaults(SignalProgram.Kind.CONSTANT);
    var source =
        new SignalProgram(
            p.kind(),
            true,
            List.of(
                new SignalProgram.Row(A, 4, 0, true),
                new SignalProgram.Row(IRON, 7, 1, true),
                new SignalProgram.Row(WATER, 300, 0, true)),
            p.left(),
            p.right(),
            p.arithmetic(),
            p.result(),
            List.of(),
            List.of(),
            List.of(),
            p.selector());
    var a = node(h, FactoryKind.CONSTANT, 3, source, 0, 15);
    var b = node(h, FactoryKind.ARITHMETIC, 8, math(), 15, 8);
    h.runAfterDelay(
        5,
        () -> {
          for (int c = 0; c < 4; c++) connect(h, a.circuit(true), 0, b.circuit(true), 0, c);
        });
    h.runAfterDelay(
        25,
        () -> {
          var out = b.circuit(false).output;
          h.assertTrue(
              out.get(A) == 32 && out.get(IRON) == 56 && out.get(WATER) == 2400,
              "typed four-color map wrong: " + out.values());
          configure(h, b.circuit(false), settings(false, false, List.of(), 5, 1), math());
        });
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(
              b.circuit(false).output.get(A) == 16 && b.circuit(false).output.get(IRON) == 28,
              "independent masks ignored");
          connect(h, a.circuit(false), 0, b.circuit(false), 0, 2);
        });
    h.runAfterDelay(
        65,
        () -> {
          h.assertTrue(b.circuit(false).output.get(A) == 8, "removed color retained stale output");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 90)
  public static void deciderElseAndSelectorTimeUseNativeTypedCodecAndStaging(GameTestHelper h) {
    var a = node(h, FactoryKind.CONSTANT, 3, CircuitFactory.constant(A, -4), 0, 1);
    var d = SignalProgram.defaults(SignalProgram.Kind.DECIDER);
    var p =
        new SignalProgram(
            d.kind(),
            true,
            List.of(),
            d.left(),
            d.right(),
            d.arithmetic(),
            d.result(),
            List.of(CircuitFactory.condition(A, SignalMath.Compare.GREATER, 0)),
            List.of(SignalProgram.Output.single(IRON, 7)),
            List.of(SignalProgram.Output.single(WATER, -9)),
            d.selector());
    var b = node(h, FactoryKind.DECIDER, 8, p, 1, 1);
    var s = SignalProgram.defaults(SignalProgram.Kind.SELECTOR);
    var t = SignalProgramData.save(s);
    t.getCompound("Selector").putString("Mode", "TIME");
    var c = node(h, FactoryKind.SELECTOR, 13, SignalProgramData.load(t), 0, 1);
    h.runAfterDelay(5, () -> connect(h, a.circuit(true), 0, b.circuit(true), 0, 0));
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              b.circuit(false).output.get(WATER) == -9 && b.circuit(false).output.get(IRON) == 0,
              "Else not emitted");
          h.assertTrue(
              c.circuit(false).output.get(SignalKey.DAY_LENGTH) == 24000
                  && Math.abs(
                          c.circuit(false).output.get(SignalKey.TICK)
                              - (int) h.getLevel().getGameTime())
                      <= 2,
              "Time not clock-driven");
          h.assertTrue(
              SignalProgramData.load(SignalProgramData.save(p)).equals(p),
              "codec changed structured program");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 100)
  public static void directChestUsesOwnInventoryPreservesComponentsAndQuarantinesUnload(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(4, 3, 5));
    h.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
    var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(pos);
    var stack = new ItemStack(Items.RAW_IRON, 17);
    var custom = new CompoundTag();
    custom.putInt("grade", 77);
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    chest.setItem(0, stack);
    var a = CircuitAccess.at(h.getLevel(), pos);
    var b = node(h, FactoryKind.ARITHMETIC, 9, math(), 1, 1);
    h.runAfterDelay(
        5,
        () -> {
          configure(h, a, settings(true, false, List.of(), 0, 1), a.program());
          connect(h, a, 0, b.circuit(true), 0, 0);
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              b.circuit(false).output.get(IRON) == 34
                  && chest.getItem(0).get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                      == 77,
              "read changed resources");
          a.suspended = true;
          CircuitSimulation.changed(a);
          h.assertTrue(!CircuitSimulation.inputReady(b.circuit(false)), "unload remained valid");
        });
    h.runAfterDelay(
        35,
        () -> {
          a.suspended = false;
          CircuitSimulation.changed(a);
        });
    h.runAfterDelay(
        55,
        () -> {
          h.assertTrue(
              b.circuit(false).inputReady
                  && b.circuit(false).output.get(IRON) == 34
                  && chest.getItem(0).getCount() == 17,
              "reload failed or changed resources");
          h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
          h.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
          var replacement = CircuitAccess.at(h.getLevel(), pos);
          h.assertTrue(
              !replacement.identity().equals(a.identity()) && replacement.links.isEmpty(),
              "replacement adopted old wires");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 100)
  public static void pipeReadAggregatesWholeLoadedComponentWithoutMixingFluidComponents(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(5, 3, 5));
    var pipes = new ArrayList<FluidBlockEntity>();
    for (int x = 0; x < 2; x++) {
      h.getLevel()
          .setBlockAndUpdate(
              pos.east(x), Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
      pipes.add((FluidBlockEntity) h.getLevel().getBlockEntity(pos.east(x)));
    }
    var first = pipes.get(0);
    var second = pipes.get(1);
    h.runAfterDelay(
        5,
        () -> {
          for (int i = 0; i < 2; i++) {
            var f = new FluidStack(Fluids.WATER, i == 0 ? 333 : 667);
            var tag = new CompoundTag();
            tag.putInt("grade", i);
            f.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            pipes.get(i).cell().fill(new FluidKey(f), f.getAmount(), false);
          }
        });
    h.runAfterDelay(
        25,
        () -> {
          var sample = FluidSimulation.circuitContents(first);
          h.assertTrue(
              sample.ready()
                  && sample.cells() == 2
                  && sample.amounts().get(WATER) == 1000
                  && first.cell().amount() == 333
                  && second.cell().amount() == 667,
              "aggregate changed fluid mixing or missed neighbor");
          second.onChunkUnloaded();
        });
    h.runAfterDelay(
        35,
        () -> {
          h.assertTrue(
              FluidSimulation.circuitContents(first).amounts().get(WATER) == 333,
              "unloaded volume counted");
          second.onLoad();
        });
    h.runAfterDelay(
        55,
        () -> {
          h.assertTrue(
              FluidSimulation.circuitContents(first).amounts().get(WATER) == 1000
                  && second.cell().amount() == 667,
              "reload lost volume");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 100)
  public static void beltHoldAndPulseCountPhysicalStackArrivalOnceAndGateStopsMotion(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(5, 3, 5));
    h.getLevel()
        .setBlockAndUpdate(pos, Content.FACTORY.get(FactoryKind.BELT).get().defaultBlockState());
    var belt = (BeltBlockEntity) h.getLevel().getBlockEntity(pos);
    var a = belt.circuit(true);
    var countProgram =
        SignalProgramData.save(SignalProgram.defaults(SignalProgram.Kind.ARITHMETIC));
    countProgram.put("Left", SignalProgramData.operand(SignalProgram.Operand.signal(IRON)));
    countProgram.put("Result", SignalProgramData.output(SignalProgram.Output.single(IRON, 1)));
    var counter = node(h, FactoryKind.ARITHMETIC, 11, SignalProgramData.load(countProgram), 1, 1);
    h.runAfterDelay(
        5,
        () -> {
          var tag = settings(true, false, List.of(), 0, 1).save();
          tag.putBoolean("Pulse", true);
          configure(h, a, CircuitSettings.load(tag), a.program());
          connect(h, a, 0, counter.circuit(true), 0, 0);
          connect(h, counter.circuit(true), 1, counter.circuit(true), 0, 0);
          h.assertTrue(
              belt.inventory().insertItem(0, new ItemStack(Items.RAW_IRON, 4), false).getCount()
                  == 3,
              "ordinary insertion did not retain unaccepted remainder");
        });
    h.runAfterDelay(
        7,
        () ->
            h.assertTrue(
                counter.circuit(false).output.get(IRON) == 1,
                "arrival pulse missing or counted whole offered stack"));
    h.runAfterDelay(
        9,
        () -> {
          h.assertTrue(
              a.output.get(IRON) == 0 && counter.circuit(false).output.get(IRON) == 1,
              "pulse repeated while carrier held");
          configure(
              h,
              a,
              settings(
                  true,
                  true,
                  List.of(CircuitFactory.condition(A, SignalMath.Compare.GREATER, 0)),
                  0,
                  1),
              a.program());
        });
    h.runAfterDelay(
        18,
        () -> {
          h.assertTrue(
              !belt.circuitRunning() && a.output.get(IRON) == 1, "hold or direct gate failed");
          var before =
              belt.lane(0).carriers().isEmpty()
                  ? belt.lane(1).carriers().getFirst().positionUnits()
                  : belt.lane(0).carriers().getFirst().positionUnits();
          h.runAfterDelay(
              8,
              () -> {
                var after =
                    belt.lane(0).carriers().isEmpty()
                        ? belt.lane(1).carriers().getFirst().positionUnits()
                        : belt.lane(0).carriers().getFirst().positionUnits();
                h.assertTrue(before == after, "disabled belt moved");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 110)
  public static void directArmFiltersAndHandLimitDoNotOverwriteManualConfiguration(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(6, 3, 5));
    h.getLevel()
        .setBlockAndUpdate(
            pos, Content.FACTORY.get(FactoryKind.BULK_INSERTER).get().defaultBlockState());
    var arm = (InserterBlockEntity) h.getLevel().getBlockEntity(pos);
    for (var p : List.of(arm.sourcePosition(), arm.targetPosition()))
      h.getLevel().setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
    var source = (ChestBlockEntity) h.getLevel().getBlockEntity(arm.sourcePosition());
    var dest = (ChestBlockEntity) h.getLevel().getBlockEntity(arm.targetPosition());
    source.setItem(0, new ItemStack(Items.RAW_COPPER, 7));
    source.setItem(1, new ItemStack(Items.RAW_IRON, 3));
    var p = SignalProgram.defaults(SignalProgram.Kind.CONSTANT);
    var program =
        new SignalProgram(
            p.kind(),
            true,
            List.of(
                new SignalProgram.Row(IRON, 1, 0, true),
                new SignalProgram.Row(SignalKey.virtual("hand_size"), 1, 0, true)),
            p.left(),
            p.right(),
            p.arithmetic(),
            p.result(),
            List.of(),
            List.of(),
            List.of(),
            p.selector());
    var n = node(h, FactoryKind.CONSTANT, 11, program, 0, 1);
    h.runAfterDelay(
        5,
        () -> {
          var tag = settings(false, false, List.of(), 1, 0).save();
          tag.putBoolean("Filters", true);
          tag.putBoolean("Hand", true);
          configure(h, arm.circuit(true), CircuitSettings.load(tag), arm.circuit(true).program());
          connect(h, n.circuit(true), 0, arm.circuit(true), 0, 0);
          arm.power.restore(2000);
        });
    h.runAfterDelay(
        85,
        () -> {
          h.assertTrue(
              source.getItem(0).getCount() == 7
                  && source.getItem(1).isEmpty()
                  && CircuitGameTests.count(dest) == 3
                  && arm.held().isEmpty(),
              "direct filter lost cargo or selected copper");
          h.assertTrue(
              arm.circuit(false).handLimit == 1
                  && arm.filterIds().stream().allMatch(String::isEmpty),
              "circuit overwrote manual filter");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 100)
  public static void multiblockContactsResolveOneOwnerAndUnavailablePartClosesGate(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(6, 3, 6));
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(FactoryKind.ASSEMBLER).get().defaultBlockState());
    var machine = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
    var a = machine.circuit(true);
    var c = node(h, FactoryKind.CONSTANT, 13, CircuitFactory.constant(A, 1), 0, 1);
    h.runAfterDelay(
        5,
        () -> {
          configure(
              h,
              a,
              settings(
                  true,
                  true,
                  List.of(CircuitFactory.condition(A, SignalMath.Compare.GREATER, 0)),
                  1,
                  2),
              a.program());
          connect(h, c.circuit(true), 0, a, 0, 0);
          for (var cell : machine.assembly().positions())
            h.assertTrue(
                CircuitAccess.at(h.getLevel(), cell) == a, "part owns separate circuit state");
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(CircuitBinding.allowed(machine), "connected machine stopped");
          var part =
              (CasingBlockEntity)
                  h.getLevel().getBlockEntity(machine.assembly().positions().getFirst());
          part.onChunkUnloaded();
          h.assertTrue(!CircuitBinding.allowed(machine), "missing part left gate enabled");
          part.onLoad();
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(CircuitBinding.allowed(machine), "complete machine did not resume");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 100)
  public static void labSelectsNextAvailableTeamTechnologyButPreservesManualSelection(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(6, 3, 6));
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(FactoryKind.LAB).get().defaultBlockState());
    var lab = (LabBlockEntity) h.getLevel().getBlockEntity(pos);
    String scope = ResearchGameTests.personal(h, "direct22-" + UUID.randomUUID());
    lab.bind(scope);
    var a = lab.circuit(true);
    var technology = new SignalKey(SignalKey.Type.TECHNOLOGY, "factorycraft:automation");
    var c = node(h, FactoryKind.CONSTANT, 13, CircuitFactory.constant(technology, 1), 0, 1);
    h.runAfterDelay(
        5,
        () -> {
          var tag = settings(false, false, List.of(), 1, 2).save();
          tag.putBoolean("SelectResearch", true);
          tag.putBoolean("Research", true);
          configure(h, a, CircuitSettings.load(tag), a.program());
          connect(h, c.circuit(true), 0, a, 0, 0);
        });
    h.runAfterDelay(
        30,
        () -> {
          var ledger = ResearchData.get(h.getLevel().getServer()).team(scope);
          h.assertTrue(
              ledger.selected().equals("factorycraft:automation") && a.output.get(technology) == 1,
              "next available team research not selected/read");
          ledger.restore("factorycraft:logistics", Map.of("factorycraft:automation", 1), Map.of());
          ResearchData.get(h.getLevel().getServer()).setDirty();
        });
    h.runAfterDelay(
        55,
        () -> {
          h.assertTrue(
              ResearchData.get(h.getLevel().getServer())
                  .team(scope)
                  .selected()
                  .equals("factorycraft:logistics"),
              "circuit overwrote running manual research");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 80)
  public static void malformedProgramClosesDirectGateAndPortableStateDropsPhysicalIdentity(
      GameTestHelper h) {
    var n = node(h, FactoryKind.CONSTANT, 4, CircuitFactory.constant(A, -17), 0, 1);
    h.runAfterDelay(
        5,
        () -> {
          var a = n.circuit(false);
          var original = a.identity();
          var tag = n.saveWithoutMetadata(h.getLevel().registryAccess());
          tag.getCompound("DirectCircuit").getCompound("Program").putInt("Schema", 999);
          n.loadWithComponents(tag, h.getLevel().registryAccess());
          CircuitSimulation.changed(a);
          h.assertTrue(
              a.invalid() && !CircuitSimulation.permitted(n), "malformed config enabled work");
          configure(h, a, settings(false, false, List.of(), 0, 1), CircuitFactory.constant(A, -17));
          var portable = n.saveWithoutMetadata(h.getLevel().registryAccess());
          CircuitAttachment.portable(portable);
          h.assertTrue(
              !portable.getCompound("DirectCircuit").contains("Identity")
                  && !portable.getCompound("DirectCircuit").contains("Links")
                  && SignalProgramData.load(
                              portable.getCompound("DirectCircuit").getCompound("Program"))
                          .constants()
                          .getFirst()
                          .value()
                      == -17,
              "portable config lost program or retained physical wires");
          h.assertTrue(a.identity().equals(original), "inspection changed identity");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 80)
  public static void directTypedProgramsAndContainerLinksRetainExactStateAcrossJvm(
      GameTestHelper h) {
    var level = h.getLevel();
    var pos = new BlockPos(19015, 75, 19015);
    var peer = pos.east(4);
    for (var p : List.of(pos, peer)) level.getChunkAt(p);
    boolean fresh = level.getBlockState(pos).isAir() && level.getBlockState(peer).isAir();
    if (fresh) {
      var n =
          CircuitFactory.node(
              level,
              pos,
              FactoryKind.CONSTANT,
              CircuitFactory.constant(SignalKey.item("missing:retained"), Integer.MIN_VALUE),
              0,
              4);
      level.setBlockAndUpdate(peer, Blocks.CHEST.defaultBlockState());
      var chest = (ChestBlockEntity) level.getBlockEntity(peer);
      chest.setItem(0, new ItemStack(Items.DIAMOND, 23));
      var a = CircuitAccess.at(level, peer);
      CircuitFactory.configure(a, settings(true, false, List.of(), 4, 8), a.program());
      CircuitFactory.wire(n.circuit(true), 0, a, 0, 2);
    }
    var n = (CircuitNodeBlockEntity) level.getBlockEntity(pos);
    var chest = (ChestBlockEntity) level.getBlockEntity(peer);
    var a = CircuitAccess.at(level, peer);
    h.assertTrue(
        n.circuit(true).program().constants().getFirst().value() == Integer.MIN_VALUE
            && n.circuit(true)
                .program()
                .constants()
                .getFirst()
                .signal()
                .id()
                .equals("missing:retained")
            && n.links.size() == 1
            && a.links.size() == 1
            && n.links.getFirst().identity().equals(a.identity())
            && chest.getItem(0).getCount() == 23
            && a.settings().inputColors() == 4
            && a.settings().outputColors() == 8,
        "strict direct-circuit disk state changed");
    n.onChunkUnloaded();
    a.suspended = true;
    CircuitSimulation.remove(a);
    for (var p : List.of(pos, peer)) level.getChunkAt(p).setUnsaved(true);
    System.out.println(
        "FACTORYCRAFT_DIRECT_CIRCUIT_DISK: "
            + (fresh ? "SEEDED" : "VERIFIED")
            + " typed INT_MIN, unknown ID, chest 23 diamonds, blue links, independent masks");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 420)
  public static void finiteAssemblerLineStopsAtTwoThenResumesAfterOutputRemoved(GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    var machinePos = o.offset(8, 0, 8);
    StructurePlacement.place(
        h.getLevel(),
        machinePos,
        Content.FACTORY.get(FactoryKind.ASSEMBLER).get().defaultBlockState());
    var machine = (MachineBlockEntity) h.getLevel().getBlockEntity(machinePos);
    var arms = new ArrayList<InserterBlockEntity>();
    for (int z : new int[] {11, 7}) {
      var p = o.offset(8, 0, z);
      h.getLevel()
          .setBlockAndUpdate(
              p, Content.FACTORY.get(FactoryKind.FAST_INSERTER).get().defaultBlockState());
      arms.add((InserterBlockEntity) h.getLevel().getBlockEntity(p));
    }
    var sourcePos = arms.getFirst().sourcePosition();
    var outputPos = arms.getLast().targetPosition();
    for (var p : List.of(sourcePos, outputPos))
      h.getLevel().setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
    var source = (ChestBlockEntity) h.getLevel().getBlockEntity(sourcePos);
    var output = (ChestBlockEntity) h.getLevel().getBlockEntity(outputPos);
    var gear = SignalKey.item("factorycraft:iron_gear");
    var sensor = CircuitAccess.at(h.getLevel(), outputPos);
    source.setItem(
        0,
        new ItemStack(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("factorycraft:iron_plate")),
            6));
    final int[] removed = {0};
    h.runAfterDelay(
        5,
        () -> {
          machine.power.restore(20000);
          for (var arm : arms) arm.power.restore(2000);
          configure(h, sensor, settings(true, false, List.of(), 0, 1), sensor.program());
          for (var consumer : List.of(machine, arms.getLast())) {
            var n = consumer.circuit(true);
            configure(
                h,
                n,
                settings(
                    false,
                    true,
                    List.of(CircuitFactory.condition(gear, SignalMath.Compare.LESS, 2)),
                    1,
                    0),
                n.program());
            connect(h, sensor, 0, n, 0, 0);
          }
        });
    h.runAfterDelay(
        200,
        () -> {
          h.assertTrue(
              CircuitGameTests.count(output) == 2 && !CircuitBinding.allowed(machine),
              "finite production did not stop at two: " + CircuitGameTests.count(output));
          for (int i = 0; i < output.getContainerSize(); i++)
            if (!output.getItem(i).isEmpty()) {
              removed[0] += output.getItem(i).getCount();
              output.setItem(i, ItemStack.EMPTY);
            }
        });
    h.runAfterDelay(
        380,
        () -> {
          h.assertTrue(
              removed[0] == 2
                  && CircuitGameTests.count(output) == 1
                  && source.isEmpty()
                  && arms.stream().allMatch(a -> a.held().isEmpty()),
              "line failed to resume or lost finite resources");
          int plates = 0, gears = removed[0] + CircuitGameTests.count(output);
          for (int i = 0; i < machine.items.getSlots(); i++) {
            var item = machine.items.getStackInSlot(i);
            if (item.is(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("factorycraft:iron_plate")))) plates += item.getCount();
            if (item.is(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("factorycraft:iron_gear")))) gears += item.getCount();
          }
          h.assertTrue(
              plates + 2 * gears == 6 && machine.power.getEnergyStored() < 20000,
              "finite plate/gear/FE ledger changed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 100)
  public static void conflictingLabsRejectUnavailableResearchAndUseCanonicalTeamOrder(
      GameTestHelper h) {
    String scope = ResearchGameTests.personal(h, "conflict22-" + UUID.randomUUID());
    var labs = new ArrayList<LabBlockEntity>();
    var sources = new ArrayList<CircuitNodeBlockEntity>();
    var level = h.getLevel();
    var ledger = ResearchData.get(level.getServer()).team(scope);
    ledger.restore("", Map.of("factorycraft:automation", 1), Map.of());
    ResearchData.get(level.getServer()).setDirty();
    for (int i = 0; i < 2; i++) {
      var pos = h.absolutePos(new BlockPos(4 + 6 * i, 3, 5));
      StructurePlacement.place(
          level, pos, Content.FACTORY.get(FactoryKind.LAB).get().defaultBlockState());
      var lab = (LabBlockEntity) level.getBlockEntity(pos);
      lab.bind(scope);
      labs.add(lab);
      var tech =
          new SignalKey(
              SignalKey.Type.TECHNOLOGY,
              i == 0 ? "factorycraft:logistics" : "factorycraft:electronics");
      sources.add(
          CircuitFactory.node(
              level, pos.south(7), FactoryKind.CONSTANT, CircuitFactory.constant(tech, 1), 0, 1));
    }
    h.runAfterDelay(
        5,
        () -> {
          for (int i = 0; i < 2; i++) {
            var n = labs.get(i).circuit(true);
            var tag = settings(false, false, List.of(), 1, 0).save();
            tag.putBoolean("SelectResearch", true);
            configure(h, n, CircuitSettings.load(tag), n.program());
            connect(h, sources.get(i).circuit(true), 0, n, 0, 0);
          }
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              ledger.selected().equals("factorycraft:logistics"),
              "conflicting labs did not use stable owner order");
          h.assertTrue(
              ResearchData.get(level.getServer())
                  .team(ResearchGameTests.personal(h, "other22"))
                  .selected()
                  .isEmpty(),
              "technology leaked into another team");
          var a = sources.getFirst().circuit(true);
          configure(
              h,
              a,
              a.settings(),
              CircuitFactory.constant(
                  new SignalKey(
                      SignalKey.Type.TECHNOLOGY, "factorycraft:transport_belt_capacity_1"),
                  1000));
        });
    h.runAfterDelay(45, () -> ledger.restore("", Map.of("factorycraft:automation", 1), Map.of()));
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              ledger.selected().equals("factorycraft:electronics"),
              "sandbox/unavailable technology bypassed prerequisites or blocked valid lab");
          for (var lab : labs) {
            var n = lab.circuit(false);
            configure(h, n, settings(false, false, List.of(), 1, 0), n.program());
          }
          ledger.restore("", Map.of("factorycraft:automation", 1), Map.of());
        });
    h.runAfterDelay(
        85,
        () -> {
          h.assertTrue(
              ledger.selected().isEmpty() && !CircuitDevices.researchControlled(level, scope),
              "disabled lab kept automatic selection locked");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 110)
  public static void recipeSignalHonorsResearchAndPreservesPaidPartialProduction(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(5, 3, 5));
    StructurePlacement.place(
        h.getLevel(), p, Content.FACTORY.get(FactoryKind.ASSEMBLER).get().defaultBlockState());
    var m = (MachineBlockEntity) h.getLevel().getBlockEntity(p);
    String scope = ResearchGameTests.personal(h, "recipe22-" + UUID.randomUUID());
    m.bindResearch(scope);
    var signal = new SignalKey(SignalKey.Type.RECIPE, "factorycraft:advanced_circuit");
    var c = node(h, FactoryKind.CONSTANT, 12, CircuitFactory.constant(signal, 1), 0, 1);
    h.runAfterDelay(
        5,
        () -> {
          var n = m.circuit(true);
          var t = settings(false, false, List.of(), 1, 0).save();
          t.putBoolean("Recipe", true);
          configure(h, n, CircuitSettings.load(t), n.program());
          connect(h, c.circuit(true), 0, n, 0, 0);
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              !m.circuitSelectRecipe(ResourceLocation.parse("factorycraft:advanced_circuit")),
              "recipe command bypassed research");
          var a = c.circuit(false);
          configure(
              h,
              a,
              a.settings(),
              CircuitFactory.constant(
                  new SignalKey(SignalKey.Type.RECIPE, "factorycraft:iron_gear"), 1));
          m.items.setStackInSlot(
              0,
              new ItemStack(
                  net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                      ResourceLocation.parse("factorycraft:iron_plate")),
                  2));
          m.power.restore(24);
        });
    h.runAfterDelay(
        45,
        () -> {
          var before = m.saveWithoutMetadata(h.getLevel().registryAccess());
          h.assertTrue(before.getInt("Progress") > 0, "fixture did not pay partial production");
          h.assertTrue(
              !m.circuitSelectRecipe(ResourceLocation.parse("factorycraft:copper_wire")),
              "recipe changed paid partial production");
          var after = m.saveWithoutMetadata(h.getLevel().registryAccess());
          h.assertTrue(
              before.getInt("Progress") == after.getInt("Progress")
                  && before.getInt("Energy") == after.getInt("Energy")
                  && m.items.getStackInSlot(0).getCount() == 2,
              "rejected command changed resources");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 110)
  public static void poleRelaysCarryAllColorsWithoutCreatingAnyCopperPowerLink(GameTestHelper h) {
    var a = node(h, FactoryKind.CONSTANT, 3, CircuitFactory.constant(A, 7), 0, 15);
    var b = node(h, FactoryKind.ARITHMETIC, 15, math(), 15, 1);
    var p = h.absolutePos(new BlockPos(9, 3, 5));
    StructurePlacement.place(
        h.getLevel(), p, Content.FACTORY.get(FactoryKind.MEDIUM_POLE).get().defaultBlockState());
    var pole = (PoleBlockEntity) h.getLevel().getBlockEntity(p);
    var n = pole.circuit(true);
    h.runAfterDelay(
        5,
        () -> {
          for (int c = 0; c < 4; c++) {
            connect(h, a.circuit(true), 0, n, 0, c);
            connect(h, n, 0, b.circuit(true), 0, c);
          }
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              b.circuit(false).output.get(A) == 56 && pole.links().isEmpty(),
              "logic transit changed FE topology or duplicated colors");
          pole.onChunkUnloaded();
          h.assertTrue(
              !CircuitSimulation.inputReady(b.circuit(false)), "unloaded relay remained live");
        });
    h.runAfterDelay(45, pole::onLoad);
    h.runAfterDelay(
        75,
        () -> {
          h.assertTrue(
              b.circuit(false).output.get(A) == 56
                  && b.circuit(false).inputReady
                  && pole.links().isEmpty(),
              "relay reload changed graph");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 80)
  public static void settingsMenuRejectsDistantStaleAndForeignTeamEdits(GameTestHelper h) {
    var n = node(h, FactoryKind.CONSTANT, 5, CircuitFactory.constant(A, 3), 0, 1);
    h.runAfterDelay(
        5,
        () -> {
          var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
          var owner = n.circuit(true);
          var menu = new LogicMenu(71, player.getInventory(), owner, n.getBlockPos());
          player.containerMenu = menu;
          player.setPos(
              n.getBlockPos().getX() + .5, n.getBlockPos().getY() + 1, n.getBlockPos().getZ() + .5);
          h.assertTrue(menu.allowed(player), "near owner menu rejected");
          var config = new CompoundTag();
          config.put("Settings", owner.settings().save());
          config.put("Program", SignalProgramData.save(CircuitFactory.constant(A, 9)));
          h.assertTrue(!menu.apply(player, UUID.randomUUID(), config), "stale identity accepted");
          player.setPos(
              n.getBlockPos().getX() + 40, n.getBlockPos().getY(), n.getBlockPos().getZ());
          h.assertTrue(
              !menu.apply(player, owner.identity(), config)
                  && owner.program().constants().getFirst().value() == 3,
              "distant edit accepted");
          player.setPos(
              n.getBlockPos().getX() + .5, n.getBlockPos().getY() + 1, n.getBlockPos().getZ() + .5);
          h.assertTrue(
              menu.apply(player, owner.identity(), config), "valid structured edit rejected");
          var p = n.getBlockPos().east(6);
          StructurePlacement.place(
              h.getLevel(), p, Content.FACTORY.get(FactoryKind.LAB).get().defaultBlockState());
          var lab = (LabBlockEntity) h.getLevel().getBlockEntity(p);
          lab.bind(ResearchGameTests.personal(h, "foreign-menu"));
          h.runAfterDelay(
              5,
              () -> {
                var lm = new LogicMenu(72, player.getInventory(), lab.circuit(true), p);
                player.containerMenu = lm;
                player.setPos(p.getX() + .5, p.getY() + 1, p.getZ() + .5);
                h.assertTrue(!lm.allowed(player), "foreign team edited lab circuits");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 90)
  public static void liftContactsQuarantineWhenAnyPhysicalPartUnloadsAndRecoverWithoutNewOwner(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(5, 3, 5));
    h.getLevel()
        .setBlockAndUpdate(pos, Content.FACTORY.get(FactoryKind.LIFT).get().defaultBlockState());
    var belt = (BeltBlockEntity) h.getLevel().getBlockEntity(pos);
    if (!belt.loaded()) belt.onLoad();
    h.assertTrue(
        belt.initializeRoute(
            new factorycraft.belts.BeltRoute(factorycraft.belts.BeltRoute.Shape.LIFT, 4, 0)),
        "lift route rejected");
    h.assertTrue(factorycraft.belts.TransportPlan.buildParts(belt), "lift parts rejected");
    var a = belt.circuit(true);
    var n = node(h, FactoryKind.CONSTANT, 12, CircuitFactory.constant(A, 1), 0, 1);
    var identity = a.identity();
    h.runAfterDelay(
        5,
        () -> {
          configure(
              h,
              a,
              settings(
                  true,
                  true,
                  List.of(CircuitFactory.condition(A, SignalMath.Compare.GREATER, 0)),
                  1,
                  2),
              a.program());
          connect(h, n.circuit(true), 0, a, 0, 0);
          for (var cell : belt.physicalCells())
            h.assertTrue(
                CircuitAccess.at(h.getLevel(), cell) == a && a.portAt(cell) == 0,
                "lift part did not resolve canonical contact");
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(CircuitBinding.allowed(belt), "lift not enabled");
          var part = (TransportPartBlockEntity) h.getLevel().getBlockEntity(pos.above(2));
          part.onChunkUnloaded();
          h.assertTrue(
              !a.available() && !CircuitBinding.allowed(belt),
              "unloaded lift body left circuit live");
          part.onLoad();
        });
    h.runAfterDelay(
        55,
        () -> {
          h.assertTrue(
              a.identity().equals(identity) && a.available() && CircuitBinding.allowed(belt),
              "lift changed owner or failed to recover");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 80)
  public static void nativeWireFirstUseConnectsChestAndPipeBeforeTheirMenusWithoutConsumption(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(5, 3, 5));
    h.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
    var pipePos = pos.east(4);
    h.getLevel()
        .setBlockAndUpdate(
            pipePos, Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    h.runAfterDelay(
        5,
        () -> {
          var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
          player.setItemInHand(
              net.minecraft.world.InteractionHand.MAIN_HAND,
              new ItemStack(Content.WHITE_CIRCUIT_WIRE.get()));
          for (var p : List.of(pos, pipePos)) {
            player.setPos(p.getX() + .5, p.getY() + 1, p.getZ() + .5);
            var context =
                new net.minecraft.world.item.context.UseOnContext(
                    player,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(
                        p.getCenter(), Direction.UP, p, false));
            h.assertTrue(
                player.getMainHandItem().onItemUseFirst(context).consumesAction(),
                "native pre-menu circuit tool hook failed");
          }
          var a = CircuitAccess.at(h.getLevel(), pos);
          var b = CircuitAccess.at(h.getLevel(), pipePos);
          h.assertTrue(
              a.links.size() == 1
                  && b.links.size() == 1
                  && a.links.getFirst().color() == 3
                  && player.getMainHandItem().getCount() == 1,
              "pre-menu tool duplicated connection or consumed wire");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 90)
  public static void disablingSensorWithdrawsOldSignalsAndReenablingReadsCurrentInventory(
      GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(5, 3, 5));
    h.getLevel().setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
    var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(p);
    chest.setItem(0, new ItemStack(Items.RAW_IRON, 13));
    var a = CircuitAccess.at(h.getLevel(), p);
    var b = node(h, FactoryKind.ARITHMETIC, 11, math(), 1, 1);
    h.runAfterDelay(
        5,
        () -> {
          configure(h, a, settings(true, false, List.of(), 0, 1), a.program());
          connect(h, a, 0, b.circuit(true), 0, 0);
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(b.circuit(false).output.get(IRON) == 26, "sensor did not publish");
          configure(h, a, settings(false, false, List.of(), 0, 1), a.program());
        });
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              a.output.isEmpty()
                  && b.circuit(false).output.get(IRON) == 0
                  && chest.getItem(0).getCount() == 13,
              "disabled sensor kept old output or changed inventory");
          configure(h, a, settings(true, false, List.of(), 0, 1), a.program());
        });
    h.runAfterDelay(
        65,
        () -> {
          h.assertTrue(
              b.circuit(false).output.get(IRON) == 26 && chest.getItem(0).getCount() == 13,
              "sensor did not resume");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 40)
  public static void beltCollisionQueriesNeverLoadMissingChunksDuringTicketNotifications(
      GameTestHelper h) {
    var p = new BlockPos(26000000, 90, 26000000);
    var level = h.getLevel();
    h.assertTrue(
        level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null,
        "unloaded fixture unexpectedly exists");
    var state = Content.FACTORY.get(FactoryKind.BELT).get().defaultBlockState();
    var shape = state.getCollisionShape(level, p);
    h.assertTrue(
        !shape.isEmpty()
            && level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null,
        "belt shape lookup forced chunk loading");
    h.succeed();
  }

  /**
   * Detached raw computational checkpoint; its numbers are signals, never seeded item/fluid
   * outputs.
   */
  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 50)
  public static void typedPublishedOutputMapAndFeedbackEdgesRetainExactDiskState(GameTestHelper h) {
    var level = h.getLevel();
    var pos = new BlockPos(19615, 75, 19615);
    var cells = MachineProfile.ARITHMETIC.cells(pos, Direction.NORTH);
    for (var cell : cells) level.getChunkAt(cell);
    boolean fresh = cells.stream().allMatch(p -> level.getBlockState(p).isAir());
    var expected = SignalSet.copy(Map.of(A, Integer.MIN_VALUE, IRON, 6, WATER, 1334));
    if (fresh) {
      var n = CircuitFactory.node(level, pos, FactoryKind.ARITHMETIC, math(), 4, 4);
      var owner = n.circuit(true);
      owner.output = expected;
      CircuitFactory.wire(owner, 1, owner, 0, 2);
      owner.persistent();
    }
    var node = (CircuitNodeBlockEntity) level.getBlockEntity(pos);
    var owner = node.circuit(true);
    h.assertTrue(
        owner.output.equals(expected)
            && owner.program().equals(math())
            && owner.settings().inputColors() == 4
            && owner.settings().outputColors() == 4
            && owner.links.size() == 2
            && owner.links.stream()
                .allMatch(
                    link ->
                        link.peer().equals(pos)
                            && link.identity().equals(owner.identity())
                            && link.localPort() != link.remotePort()
                            && link.color() == 2),
        "published typed snapshot or reciprocal feedback identity changed on disk");
    node.onChunkUnloaded();
    for (var cell : cells) level.getChunkAt(cell).setUnsaved(true);
    System.out.println(
        "FACTORYCRAFT_TYPED_FEEDBACK_DISK: "
            + (fresh ? "SEEDED" : "VERIFIED")
            + " three typed outputs including INT_MIN, reciprocal blue feedback ports; detached"
            + " diagnostic state");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "direct_circuit_v0220", timeoutTicks = 100)
  public static void overCapacityEachOutputHoldsLastSnapshotAndRecoversWithoutDiscardingSources(
      GameTestHelper h) {
    var level = h.getLevel();
    var base = h.absolutePos(new BlockPos(2, 3, 2));
    var sources = new ArrayList<CircuitAttachment>();
    for (int i = 0; i < 64; i++) {
      int row = i / 8, col = row % 2 == 0 ? i % 8 : 7 - i % 8;
      var p = SignalProgram.defaults(SignalProgram.Kind.CONSTANT);
      var rows = new ArrayList<SignalProgram.Row>();
      for (int j = 0; j < 64; j++)
        rows.add(new SignalProgram.Row(SignalKey.virtual("limit_" + (i * 64 + j)), 1, 0, true));
      var program =
          new SignalProgram(
              p.kind(),
              true,
              rows,
              p.left(),
              p.right(),
              p.arithmetic(),
              p.result(),
              p.conditions(),
              p.outputs(),
              p.elseOutputs(),
              p.selector());
      sources.add(
          CircuitFactory.node(
                  level, base.offset(col * 2, 0, row * 2), FactoryKind.CONSTANT, program, 0, 1)
              .circuit(true));
      if (i > 0) CircuitFactory.wire(sources.get(i - 1), 0, sources.get(i), 0, 0);
    }
    var p = SignalProgram.defaults(SignalProgram.Kind.DECIDER);
    var each = new SignalProgram.Operand(SignalProgram.Mode.EACH, A, 0, 15);
    var total = SignalKey.virtual("limit_total");
    var program =
        new SignalProgram(
            p.kind(),
            true,
            List.of(),
            p.left(),
            p.right(),
            p.arithmetic(),
            p.result(),
            List.of(
                new SignalProgram.Condition(
                    each, SignalMath.Compare.GREATER, SignalProgram.Operand.number(0), false)),
            List.of(
                new SignalProgram.Output(SignalProgram.Mode.EACH, A, true, 1, 15),
                SignalProgram.Output.single(total, 1)),
            List.of(),
            p.selector());
    var node =
        CircuitFactory.node(level, base.offset(10, 0, 17), FactoryKind.DECIDER, program, 1, 4)
            .circuit(true);
    CircuitFactory.wire(sources.getLast(), 0, node, 0, 0);
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              node.input.size() == 4096
                  && node.overCapacity
                  && node.program().equals(program)
                  && node.output.size() <= 4096,
              "output limit crashed, truncated the program, or failed to mark the computation");
          var source = sources.getFirst();
          var old = source.program();
          var rows = new ArrayList<>(old.constants());
          rows.removeLast();
          configure(
              h,
              source,
              source.settings(),
              new SignalProgram(
                  old.kind(),
                  true,
                  rows,
                  old.left(),
                  old.right(),
                  old.arithmetic(),
                  old.result(),
                  old.conditions(),
                  old.outputs(),
                  old.elseOutputs(),
                  old.selector()));
        });
    h.runAfterDelay(
        80,
        () -> {
          h.assertTrue(
              !node.overCapacity
                  && node.output.size() == 4096
                  && node.output.get(total) == 4095
                  && sources.stream().mapToInt(n -> n.program().constants().size()).sum() == 4095
                  && node.program().equals(program),
              "computation did not recover with all remaining signals and its original program"
                  + " intact");
          h.succeed();
        });
  }

  private DirectCircuitGameTests() {}
}
