package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.diagnostics.ShoreFixture;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.simulation.BeltSimulation;
import factorycraft.simulation.power.PoleSpec;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class ReleaseV261GameTests {
  private static void load(AssemblyOwner owner) {
    var be = owner.factoryEntity();
    for (var p :
        owner
            .assembly()
            .profile()
            .cells(be.getBlockPos(), owner.assembly().profile().facing(be.getBlockState())))
      be.getLevel().getBlockEntity(p).onLoad();
  }

  private static MachineBlockEntity drill(
      GameTestHelper h, FactoryKind kind, BlockPos p, Direction f) {
    StructurePlacement.place(
        h.getLevel(),
        p,
        Content.FACTORY.get(kind).get().defaultBlockState().setValue(FactoryBlock.FACING, f));
    var m = (MachineBlockEntity) h.getLevel().getBlockEntity(p);
    load(m);
    return m;
  }

  private static ResourceDepositBlockEntity ore(
      GameTestHelper h, MachineBlockEntity m, boolean coal) {
    var p =
        m.assembly()
            .profile()
            .center(m.getBlockPos(), m.getBlockState().getValue(FactoryBlock.FACING))
            .below();
    h.getLevel()
        .setBlockAndUpdate(
            p,
            (coal
                    ? Content.RESOURCES.get(factorycraft.resources.ResourceKind.COAL)
                    : Content.IRON_ORE)
                .get()
                .defaultBlockState());
    var ore = (ResourceDepositBlockEntity) h.getLevel().getBlockEntity(p);
    ore.initializeReserve(10);
    ore.onLoad();
    return ore;
  }

  private static int pending(MachineBlockEntity m) {
    int n = 0;
    for (int i = 4; i < 8; i++) n += m.items.getStackInSlot(i).getCount();
    return n;
  }

  private static int count(BeltBlockEntity b) {
    int n = 0;
    for (int i = 0; i < b.laneCount(); i++)
      for (var c : b.lane(i).carriers()) n += c.value.getCount();
    return n;
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 220)
  public static void bothDrillsMineDirectlyIntoChestsInAllFourRotations(GameTestHelper h) {
    var machines = new ArrayList<MachineBlockEntity>();
    var deposits = new ArrayList<ResourceDepositBlockEntity>();
    int index = 0;
    for (var kind : List.of(FactoryKind.BURNER_DRILL, FactoryKind.DRILL))
      for (var f : Direction.Plane.HORIZONTAL) {
        var p = h.absolutePos(new BlockPos(5 + index % 4 * 7, 3, 5 + index / 4 * 10));
        index++;
        var m = drill(h, kind, p, f);
        machines.add(m);
        deposits.add(ore(h, m, false));
        h.getLevel().setBlockAndUpdate(m.drillOutputPosition(), Blocks.CHEST.defaultBlockState());
        if (kind.burner()) m.items.setStackInSlot(8, new ItemStack(Items.COAL, 4));
        else m.power.restore(16000);
      }
    h.runAfterDelay(
        180,
        () -> {
          for (int i = 0; i < machines.size(); i++) {
            var m = machines.get(i);
            var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(m.drillOutputPosition());
            int n = 0;
            for (int j = 0; j < chest.getContainerSize(); j++) n += chest.getItem(j).getCount();
            h.assertTrue(
                n > 0 && 10 - deposits.get(i).remaining() == n + pending(m),
                "front direction/finite debit mismatch " + m.getBlockState() + " chest=" + n);
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 100)
  public static void drillChuteUsesSingleOrdinaryBeltCarriersInAllThreeTiers(GameTestHelper h) {
    var miners = new ArrayList<MachineBlockEntity>();
    var belts = new ArrayList<BeltBlockEntity>();
    int i = 0;
    for (var kind : List.of(FactoryKind.BELT, FactoryKind.FAST_BELT, FactoryKind.EXPRESS_BELT)) {
      var m =
          drill(
              h, FactoryKind.DRILL, h.absolutePos(new BlockPos(5, 3, 5 + 8 * i++)), Direction.EAST);
      var p = m.drillOutputPosition();
      h.getLevel()
          .setBlockAndUpdate(
              p,
              Content.FACTORY
                  .get(kind)
                  .get()
                  .defaultBlockState()
                  .setValue(FactoryBlock.FACING, Direction.EAST));
      var b = (BeltBlockEntity) h.getLevel().getBlockEntity(p);
      b.onLoad();
      miners.add(m);
      belts.add(b);
      m.items.setStackInSlot(4, new ItemStack(Items.RAW_IRON, 4));
    }
    h.runAfterDelay(
        70,
        () -> {
          for (int j = 0; j < 3; j++) {
            var b = belts.get(j);
            h.assertTrue(
                pending(miners.get(j)) == 0 && count(b) == 4,
                "belt chute did not deliver all four");
            for (int lane = 0; lane < 2; lane++)
              for (var c : b.lane(lane).carriers())
                h.assertTrue(c.value.getCount() == 1, "drill created belt stack");
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 60)
  public static void partialChuteAcceptancePreservesComponentsAndNoReceiverSaturatesSafely(
      GameTestHelper h) {
    var m =
        drill(h, FactoryKind.BURNER_DRILL, h.absolutePos(new BlockPos(7, 3, 7)), Direction.NORTH);
    var tagged = new ItemStack(Items.RAW_IRON, 8);
    var data = new CompoundTag();
    data.putInt("grade", 261);
    tagged.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    m.items.setStackInSlot(4, tagged.copy());
    var p = m.drillOutputPosition();
    h.getLevel().setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
    var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(p);
    for (int i = 0; i < 27; i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    chest.setItem(0, tagged.copyWithCount(61));
    h.runAfterDelay(
        4,
        () -> {
          h.assertTrue(
              chest.getItem(0).getCount() == 64
                  && pending(m) == 5
                  && ItemStack.isSameItemSameComponents(tagged, m.items.getStackInSlot(4)),
              "partial/components mismatch");
          h.getLevel()
              .removeBlock(
                  p, false); // Deliberately discard only diagnostic chest, not machine cargo.
          for (int i = 4; i < 8; i++) m.items.setStackInSlot(i, new ItemStack(Items.RAW_IRON, 64));
          var deposit = ore(h, m, false);
          m.items.setStackInSlot(8, new ItemStack(Items.COAL, 2));
          var fuel = m.items.getStackInSlot(8).copy();
          for (int i = 0; i < 200; i++) m.tick();
          h.assertTrue(
              deposit.remaining() == 10
                  && pending(m) == 256
                  && m.fuelTicks() == 0
                  && m.items.getStackInSlot(8).getCount() == fuel.getCount(),
              "blocked no-receiver chute consumed ore/fuel");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 180)
  public static void burnerCoalFeedsAnotherDrillFuelAndElectricFeedsFurnace(GameTestHelper h) {
    var a =
        drill(h, FactoryKind.BURNER_DRILL, h.absolutePos(new BlockPos(6, 3, 7)), Direction.EAST);
    ore(h, a, true);
    a.items.setStackInSlot(8, new ItemStack(Items.COAL, 2));
    var b = drill(h, FactoryKind.BURNER_DRILL, a.drillOutputPosition(), Direction.WEST);
    var c = drill(h, FactoryKind.DRILL, h.absolutePos(new BlockPos(17, 3, 12)), Direction.NORTH);
    ore(h, c, false);
    c.power.restore(16000);
    var furnace = drill(h, FactoryKind.STONE_FURNACE, c.drillOutputPosition(), Direction.SOUTH);
    furnace.items.setStackInSlot(8, new ItemStack(Items.COAL));
    h.runAfterDelay(
        150,
        () -> {
          h.assertTrue(
              b.items.getStackInSlot(8).is(Items.COAL) && b.items.getStackInSlot(8).getCount() > 0,
              "coal not routed to fuel");
          int n = 0;
          for (int i = 0; i < 4; i++) n += furnace.items.getStackInSlot(i).getCount();
          for (int i = 4; i < 8; i++) n += furnace.items.getStackInSlot(i).getCount();
          h.assertTrue(n > 0, "electric drill did not directly feed furnace");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 60)
  public static void shoreNormalIntakeHasNoSupportAndOnlyInlandFluidPort(GameTestHelper h) {
    int i = 0;
    for (var f : Direction.Plane.HORIZONTAL) {
      var p = h.absolutePos(new BlockPos(5 + 12 * (i % 2), 3, 5 + 12 * (i++ / 2)));
      ShoreFixture.prepare(h.getLevel(), p, f);
      h.assertTrue(
          OffshorePlacement.intake(p, f).equals(p.relative(f.getOpposite())),
          "intake not across shoreline");
      h.assertTrue(
          h.getLevel().getBlockState(OffshorePlacement.intake(p, f).below()).is(Blocks.WATER),
          "source replaced");
      StructurePlacement.place(
          h.getLevel(),
          p,
          Content.FLUID_BLOCKS
              .get(FluidKind.OFFSHORE)
              .get()
              .defaultBlockState()
              .setValue(FluidDeviceBlock.FACING, f));
      var be = (FluidBlockEntity) h.getLevel().getBlockEntity(p);
      load(be);
      h.assertTrue(
          OffshorePlacement.invalid(h.getLevel(), p, f).isEmpty()
              && be.outputNozzle().equals(p)
              && be.structurePort(p, f) != null
              && be.structurePort(OffshorePlacement.intake(p, f), f.getOpposite()) == null,
          "rotated source/part/output mismatch");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 60)
  public static void ticketPromotionOnlyReconnectsOnSafeTickAndStaleOwnerCannotMutate(
      GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(7, 3, 7));
    h.getLevel()
        .setBlockAndUpdate(p, Content.FACTORY.get(FactoryKind.BELT).get().defaultBlockState());
    var b = (BeltBlockEntity) h.getLevel().getBlockEntity(p);
    b.onLoad();
    long cp = new net.minecraft.world.level.ChunkPos(p).toLong();
    h.runAfterDelay(
        4,
        () -> {
          b.inventory().insertItem(0, new ItemStack(Items.DIAMOND), false);
          var owningChunk = h.getLevel().getChunkAt(p);
          owningChunk.setUnsaved(false);
          BeltSimulation.changed(b, true);
          h.assertTrue(
              owningChunk.isUnsaved(),
              "pending transport mutation was not persisted before ticket transition");
          BeltSimulation.ticketLevel(
              new ChunkTicketLevelUpdatedEvent(h.getLevel(), cp, 31, 33, null));
          var position = b.lane(0).carriers().getFirst().positionUnits();
          BeltSimulation.ticketLevel(
              new ChunkTicketLevelUpdatedEvent(h.getLevel(), cp, 33, 31, null));
          h.assertTrue(
              !BeltSimulation.tickable(b)
                  && b.lane(0).carriers().getFirst().positionUnits() == position,
              "ticket callback mutated/activated belt immediately");
          h.runAfterDelay(
              4,
              () -> {
                h.assertTrue(
                    count(b) == 1 && b.lane(0).carriers().getFirst().positionUnits() > position,
                    "deferred activation lost or stalled carrier");
                b.onChunkUnloaded();
                BeltSimulation.ticketLevel(
                    new ChunkTicketLevelUpdatedEvent(h.getLevel(), cp, 31, 33, null));
                b.refreshConnections();
                h.assertTrue(
                    !BeltSimulation.refreshReady(b) && count(b) == 1,
                    "stale owner remained active");
                b.onLoad();
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 60)
  public static void smallPoleOwnsThirdBlockAndKeepsFactorioElectricalMetrics(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(7, 3, 7));
    StructurePlacement.place(
        h.getLevel(), p, Content.FACTORY.get(FactoryKind.POLE).get().defaultBlockState());
    var be = (PoleBlockEntity) h.getLevel().getBlockEntity(p);
    load(be);
    h.assertTrue(
        MachineProfile.SMALL_POLE.layout.height() == 3
            && PoleSpec.SMALL.height == 3
            && PoleSpec.SMALL.supply == 5
            && PoleSpec.SMALL.wire == 7.5,
        "small pole metrics changed");
    h.assertTrue(
        h.getLevel().getBlockEntity(p.above(2)) instanceof CasingBlockEntity part
            && part.genericOwner() == be,
        "third cell lacks unique owner");
    h.assertTrue(
        Math.abs(factorycraft.energy.PoleGeometry.attachment(be).y - p.getY() - 2.88) < 1e-9,
        "wire endpoint retained old height");
    be.assembly().removeWhole(false);
    h.assertTrue(h.getLevel().getBlockState(p.above(2)).isAir(), "upper casing orphaned");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 120)
  public static void newShorePendingDrillAndPoleTransportCheckpointAcrossJvm(GameTestHelper h) {
    var level = h.getLevel();
    var base = new BlockPos(2300015, 70, 2300015);
    var drillPos = base.east(8);
    var beltPos = base.east(16);
    var polePos = base.east(24);
    var marker = base.above(4);
    var cells = new ArrayList<BlockPos>();
    cells.addAll(MachineProfile.OFFSHORE.cells(base, Direction.NORTH));
    cells.addAll(MachineProfile.BURNER_DRILL.cells(drillPos, Direction.NORTH));
    cells.addAll(MachineProfile.SMALL_POLE.cells(polePos, Direction.NORTH));
    cells.add(beltPos);
    cells.add(marker);
    cells.add(base.below());
    cells.add(OffshorePlacement.intake(base, Direction.NORTH).below());
    for (var p : cells) level.getChunkAt(p);
    var tagged = new ItemStack(Items.RAW_IRON, 8);
    var data = new CompoundTag();
    data.putInt("grade", 261);
    tagged.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    boolean fresh = level.getBlockState(marker).isAir();
    if (fresh) {
      h.assertTrue(
          cells.stream().filter(p -> p.getY() >= 70).allMatch(p -> level.getBlockState(p).isAir()),
          "partial 261 checkpoint; refusing reseed");
      ShoreFixture.prepare(level, base, Direction.NORTH);
      StructurePlacement.place(
          level, base, Content.FLUID_BLOCKS.get(FluidKind.OFFSHORE).get().defaultBlockState());
      load((FluidBlockEntity) level.getBlockEntity(base));
      var m = drill(h, FactoryKind.BURNER_DRILL, drillPos, Direction.NORTH);
      m.items.setStackInSlot(4, tagged.copy());
      m.items.setStackInSlot(8, new ItemStack(Items.COAL, 13));
      StructurePlacement.place(
          level, polePos, Content.FACTORY.get(FactoryKind.POLE).get().defaultBlockState());
      load((PoleBlockEntity) level.getBlockEntity(polePos));
      level.setBlockAndUpdate(
          beltPos, Content.FACTORY.get(FactoryKind.BELT).get().defaultBlockState());
      var b = (BeltBlockEntity) level.getBlockEntity(beltPos);
      b.onLoad();
      b.lane(0).restore(new ItemStack(Items.DIAMOND), 1);
      b.sync();
      for (var cp : cells.stream().map(net.minecraft.world.level.ChunkPos::new).distinct().toList())
        level.setChunkForced(cp.x, cp.z, true);
      h.runAfterDelay(
          25,
          () -> {
            level.setBlockAndUpdate(marker, Blocks.GOLD_BLOCK.defaultBlockState());
            verify261(h, base, drillPos, beltPos, polePos, tagged);
            for (var p : cells)
              if (level.getBlockEntity(p) != null) level.getBlockEntity(p).onChunkUnloaded();
            for (var cp :
                cells.stream().map(net.minecraft.world.level.ChunkPos::new).distinct().toList())
              level.setChunkForced(cp.x, cp.z, false);
            System.out.println(
                "FACTORYCRAFT_V0261_DISK: SEEDED cross-chunk over-water intake, pending grade261"
                    + " ore, fuel13, belt diamond and 3-high pole");
            h.succeed();
          });
    } else {
      h.assertTrue(level.getBlockState(marker).is(Blocks.GOLD_BLOCK), "261 marker changed");
      verify261(h, base, drillPos, beltPos, polePos, tagged);
      for (var p : cells)
        if (level.getBlockEntity(p) != null) level.getBlockEntity(p).onChunkUnloaded();
      System.out.println(
          "FACTORYCRAFT_V0261_DISK: VERIFIED source/pending components/fuel/transport/third pole"
              + " cell across JVM");
      h.succeed();
    }
  }

  private static void verify261(
      GameTestHelper h,
      BlockPos base,
      BlockPos drillPos,
      BlockPos beltPos,
      BlockPos polePos,
      ItemStack tagged) {
    var level = h.getLevel();
    var source = (FluidBlockEntity) level.getBlockEntity(base);
    var m = (MachineBlockEntity) level.getBlockEntity(drillPos);
    var b = (BeltBlockEntity) level.getBlockEntity(beltPos);
    var pole = (PoleBlockEntity) level.getBlockEntity(polePos);
    h.assertTrue(
        source.waterGenerated() == 4000
            && source.cell().amount() == 4000
            && level
                .getBlockState(OffshorePlacement.intake(base, Direction.NORTH).below())
                .is(Blocks.WATER),
        "261 shore source ledger changed");
    h.assertTrue(
        pending(m) == 8
            && ItemStack.isSameItemSameComponents(tagged, m.items.getStackInSlot(4))
            && m.items.getStackInSlot(8).getCount() == 13,
        "pending output/fuel changed");
    h.assertTrue(
        count(b) == 1
            && b.lane(0).carriers().getFirst().value.is(Items.DIAMOND)
            && b.lane(0).carriers().getFirst().positionUnits() == 96,
        "belt checkpoint changed");
    h.assertTrue(
        level.getBlockEntity(polePos.above(2)) instanceof CasingBlockEntity part
            && part.boundTo(polePos, pole.assembly().identity()),
        "pole top ownership changed");
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 60)
  public static void unavailableOwnerTicketCallbackNeverLoadsChunkOrChangesCargo(GameTestHelper h) {
    var p = new BlockPos(3100015, 70, 3100015);
    var server = h.getLevel();
    h.assertTrue(
        server.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null,
        "diagnostic chunk already loaded");
    var stale =
        new BeltBlockEntity(p, Content.FACTORY.get(FactoryKind.BELT).get().defaultBlockState());
    stale.setLevel(server);
    stale.lane(0).restore(new ItemStack(Items.DIAMOND, 2), .5);
    stale.onLoad();
    long cp = new net.minecraft.world.level.ChunkPos(p).toLong();
    BeltSimulation.ticketLevel(new ChunkTicketLevelUpdatedEvent(server, cp, 31, 33, null));
    BeltSimulation.ticketLevel(new ChunkTicketLevelUpdatedEvent(server, cp, 33, 31, null));
    stale.refreshConnections();
    h.assertTrue(
        !BeltSimulation.refreshReady(stale)
            && count(stale) == 2
            && stale.lane(0).carriers().getFirst().positionUnits() == 48
            && server.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null,
        "unavailable owner mutated/loaded a chunk");
    stale.onChunkUnloaded();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 60)
  public static void reconstructingOffshoreInSameTickDoesNotCreateSecondWaterQuota(
      GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(7, 3, 7));
    var level = h.getLevel();
    ShoreFixture.prepare(level, p, Direction.NORTH);
    StructurePlacement.place(
        level, p, Content.FLUID_BLOCKS.get(FluidKind.OFFSHORE).get().defaultBlockState());
    var be = (FluidBlockEntity) level.getBlockEntity(p);
    load(be);
    h.runAfterDelay(
        10,
        () -> {
          var cap = be.structurePort(p, Direction.NORTH);
          int first =
              FluidSimulation.manual(
                  () ->
                      cap.drain(
                              4000,
                              net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
                                  .EXECUTE)
                          .getAmount());
          FluidSimulation.tick(
              new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true, level));
          int sameTick =
              FluidSimulation.manual(
                  () ->
                      cap.drain(
                              4000,
                              net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
                                  .EXECUTE)
                          .getAmount());
          var tag = be.saveWithFullMetadata(level.registryAccess());
          long generated = be.waterGenerated();
          be.onChunkUnloaded();
          var copy =
              (FluidBlockEntity)
                  BlockEntity.loadStatic(p, be.getBlockState(), tag, level.registryAccess());
          level.setBlockEntity(copy);
          load(copy);
          FluidSimulation.tick(
              new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true, level));
          h.assertTrue(
              first == 4000
                  && sameTick == 600
                  && copy.cell().amount() == 0
                  && copy.waterGenerated() == generated
                  && generated == first + sameTick,
              "same-tick reconstruction duplicated water");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "release_v0261", timeoutTicks = 200)
  public static void drillChuteWaitsForTickingNeighborAcrossChunkBoundary(GameTestHelper h) {
    var level = h.getLevel();
    var site = PowerV20GameTests.site(h, 800);
    var p = new BlockPos(((site.getX() >> 4) << 4) + 15, 70, site.getZ() + 8);
    var cp = new net.minecraft.world.level.ChunkPos(p);
    level.setChunkForced(cp.x, cp.z, true);
    var m = drill(h, FactoryKind.DRILL, p, Direction.EAST);
    var target = m.drillOutputPosition();
    var other = new net.minecraft.world.level.ChunkPos(target);
    h.assertTrue(other.x != cp.x, "chute fixture is not across chunk boundary");
    m.items.setStackInSlot(4, new ItemStack(Items.RAW_IRON, 8));
    h.runAfterDelay(
        5,
        () -> {
          for (int i = 0; i < 100; i++) m.tick();
          h.assertTrue(
              pending(m) == 8 && m.power.getEnergyStored() == 0,
              "unavailable neighbor lost ore/charged FE");
          level.setChunkForced(other.x, other.z, true);
          level.setBlockAndUpdate(target, Blocks.CHEST.defaultBlockState());
          h.succeedWhen(
              () -> {
                h.assertTrue(
                    level.shouldTickBlocksAt(cp.toLong())
                        && level.shouldTickBlocksAt(other.toLong()),
                    "waiting for actual block-ticking holders");
                m.tick();
                var chest = (ChestBlockEntity) level.getBlockEntity(target);
                h.assertTrue(
                    chest.getItem(0).getCount() == 8 && pending(m) == 0,
                    "chute did not resume: loaded="
                        + m.loaded()
                        + " ready="
                        + m.assembly().ready()
                        + " pending="
                        + pending(m)
                        + " chest="
                        + chest.getItem(0));
                level.setChunkForced(cp.x, cp.z, false);
                level.setChunkForced(other.x, other.z, false);
              });
        });
  }
}
