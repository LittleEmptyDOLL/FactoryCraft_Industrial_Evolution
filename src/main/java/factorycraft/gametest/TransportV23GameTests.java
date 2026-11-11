package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.belts.*;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class TransportV23GameTests {
  private static final FactoryKind[] ARMS = {
    FactoryKind.BURNER_INSERTER,
    FactoryKind.INSERTER,
    FactoryKind.LONG_INSERTER,
    FactoryKind.FAST_INSERTER,
    FactoryKind.BULK_INSERTER,
    FactoryKind.STACK_INSERTER
  };
  private static final FactoryKind[] SPLITS = {
    FactoryKind.SPLITTER, FactoryKind.FAST_SPLITTER, FactoryKind.EXPRESS_SPLITTER
  };

  private static int items(BeltBlockEntity b) {
    int count = 0;
    for (int i = 0; i < b.laneCount(); i++)
      for (var c : b.lane(i).carriers()) count += c.value.getCount();
    return count;
  }

  private static ItemStack cargo(int n) {
    var s = new ItemStack(Items.DIAMOND, n);
    var tag = new net.minecraft.nbt.CompoundTag();
    tag.putInt("grade", 23);
    s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    return s;
  }

  private static void prepare(GameTestHelper h, Direction dir, boolean split) {
    prepare(h, dir, split, false);
  }

  private static void prepare(GameTestHelper h, Direction dir, boolean split, boolean side) {
    var level = h.getLevel();
    var arms = new ArrayList<InserterBlockEntity>();
    var belts = new ArrayList<BeltBlockEntity>();
    for (int i = 0; i < 6; i++) {
      var p = h.absolutePos(new BlockPos(14, 3, 14)).relative(dir.getClockWise(), (i - 2) * 4);
      level.setBlockAndUpdate(
          p,
          Content.FACTORY
              .get(ARMS[i])
              .get()
              .defaultBlockState()
              .setValue(FactoryBlock.FACING, dir));
      var a = (InserterBlockEntity) level.getBlockEntity(p);
      a.onLoad();
      level.setBlockAndUpdate(a.sourcePosition(), Blocks.CHEST.defaultBlockState());
      var kind =
          split
              ? SPLITS[i % 3]
              : new FactoryKind[] {
                    FactoryKind.BELT, FactoryKind.FAST_BELT, FactoryKind.EXPRESS_BELT
                  }
                  [i % 3];
      var state =
          Content.FACTORY
              .get(kind)
              .get()
              .defaultBlockState()
              .setValue(FactoryBlock.FACING, side ? dir.getClockWise() : dir);
      if (split) StructurePlacement.place(level, a.targetPosition(), state);
      else level.setBlockAndUpdate(a.targetPosition(), state);
      var b = (BeltBlockEntity) level.getBlockEntity(a.targetPosition());
      b.onLoad();
      arms.add(a);
      belts.add(b);
    }
    h.runAfterDelay(
        2,
        () -> {
          for (int i = 0; i < 6; i++) {
            var a = arms.get(i);
            var b = belts.get(i);
            int count =
                a.kind() == FactoryKind.STACK_INSERTER
                    ? 4
                    : a.kind() == FactoryKind.BULK_INSERTER ? 3 : 1;
            int recovered = 0;
            for (boolean mirror : new boolean[] {false, true}) {
              var t = a.saveWithoutMetadata(level.registryAccess());
              t.putInt("Stage", 2);
              t.putInt("Phase", 0);
              t.putInt("Energy", 2000);
              t.putLong("FuelEnergy", 10000);
              t.putBoolean("DropCredit", true);
              t.putBoolean("Enabled", true);
              a.loadWithComponents(t, level.registryAccess());
              a.setReflected(mirror);
              a.hand.setStackInSlot(0, cargo(count));
              a.tick();
              int lane = side ? 0 : mirror ? 0 : 1,
                  placed = a.kind() == FactoryKind.STACK_INSERTER ? 2 : 1;
              int sent = side && mirror ? 0 : placed;
              h.assertTrue(
                  b.lane(lane).carriers().size() == 1,
                  "missing selected lane: "
                      + a.kind()
                      + " "
                      + dir
                      + " "
                      + mirror
                      + " stage="
                      + a.stage()
                      + " ready="
                      + a.ready());
              var carrier = b.lane(lane).carriers().getFirst();
              h.assertTrue(
                  carrier.positionUnits() == 0
                      && carrier.value.getCount() == placed
                      && carrier.value.get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                          == 23,
                  "not a component-exact physical input carrier");
              h.assertTrue(a.held().getCount() == count - sent, "partial hand lost");
              int before = items(b) + a.held().getCount();
              if (!a.held().isEmpty()) {
                a.tick();
                h.assertTrue(
                    items(b) + a.held().getCount() == before && a.held().getCount() == count - sent,
                    "blocked input fell back to another lane");
              }
              // Only the exact checked remainder is recovered into a local counted test ledger.
              recovered += a.held().getCount();
              a.hand.setStackInSlot(0, ItemStack.EMPTY);
            }
            h.assertTrue(
                items(b) + recovered == count * 2, "hand + carrier resource ledger changed");
            h.assertTrue(
                items(b) == (a.kind() == FactoryKind.STACK_INSERTER ? 2 : 1) * (side ? 1 : 2),
                "unexpected splitter/output-track deposition");
            if (split)
              h.assertTrue(
                  b.lane(2).carriers().isEmpty() && b.lane(3).carriers().isEmpty(),
                  "teleported to the other physical track");
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void reflectedSixNorth(GameTestHelper h) {
    prepare(h, Direction.NORTH, false);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void reflectedSixEast(GameTestHelper h) {
    prepare(h, Direction.EAST, false);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void reflectedSixSouth(GameTestHelper h) {
    prepare(h, Direction.SOUTH, false);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void reflectedSixWest(GameTestHelper h) {
    prepare(h, Direction.WEST, false);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void splitterInputNorth(GameTestHelper h) {
    prepare(h, Direction.NORTH, true);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void splitterInputEast(GameTestHelper h) {
    prepare(h, Direction.EAST, true);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void splitterInputSouth(GameTestHelper h) {
    prepare(h, Direction.SOUTH, true);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void splitterInputWest(GameTestHelper h) {
    prepare(h, Direction.WEST, true);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void perpendicularSixNorth(GameTestHelper h) {
    prepare(h, Direction.NORTH, false, true);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void perpendicularSixEast(GameTestHelper h) {
    prepare(h, Direction.EAST, false, true);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void perpendicularSixSouth(GameTestHelper h) {
    prepare(h, Direction.SOUTH, false, true);
  }

  @GameTest(template = "empty", batch = "transport_v0230", timeoutTicks = 30)
  public static void perpendicularSixWest(GameTestHelper h) {
    prepare(h, Direction.WEST, false, true);
  }

  @GameTest(template = "empty", batch = "transport_v0230")
  public static void capacityTwoRequiresBothEarlierCapacityAndBonusSeven(GameTestHelper h) {
    var id = ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "transport_belt_capacity_2");
    var e = factorycraft.research.ResearchCatalog.get(h.getLevel()).entry(id);
    var ledger = new factorycraft.simulation.research.ResearchLedger();
    h.assertTrue(e.recipe().sandboxOnly(), "late stack research exposed early");
    ledger.restore("", Map.of("factorycraft:transport_belt_capacity_1", 1), Map.of());
    h.assertTrue(!ledger.select(e.logic()), "missing bonus 7 accepted");
    ledger.restore("", Map.of("factorycraft:inserter_capacity_bonus_7", 1), Map.of());
    h.assertTrue(!ledger.select(e.logic()), "missing capacity 1 accepted");
    ledger.restore(
        "",
        Map.of(
            "factorycraft:inserter_capacity_bonus_7",
            1,
            "factorycraft:transport_belt_capacity_1",
            1),
        Map.of());
    h.assertTrue(ledger.select(e.logic()), "valid prerequisites rejected");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "transport_v0230")
  public static void showcaseBuildsAllLiftSectionsWithFiniteComponentCargo(GameTestHelper h) {
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    factorycraft.diagnostics.TransportShowcase.build(h.getLevel(), o);
    h.runAfterDelay(
        2,
        () -> {
          int total = 0;
          for (int i = 0; i < 6; i++) {
            var p = o.offset(2 + i * 4, i == 3 ? 4 : 0, 18);
            var b = (BeltBlockEntity) h.getLevel().getBlockEntity(p);
            h.assertTrue(b.structureReady(), "partial showcase lift");
            for (int lane = 0; lane < 2; lane++)
              for (var c : b.lane(lane).carriers()) {
                total += c.value.getCount();
                h.assertTrue(
                    c.value.get(DataComponents.CUSTOM_DATA).copyTag().getInt("fixture") == 23,
                    "component cargo changed");
              }
          }
          h.assertTrue(total == 96, "finite visual cargo count differs");
          h.succeed();
        });
  }

  private TransportV23GameTests() {}
}
