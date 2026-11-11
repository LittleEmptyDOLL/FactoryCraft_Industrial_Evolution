package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.blocks.FluidDeviceBlock;
import factorycraft.fluids.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

/**
 * Segment-specific invariants supplement, rather than replace, the required finite production
 * chains.
 */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class PipelineGameTests {
  private static final IFluidHandler.FluidAction EXEC = IFluidHandler.FluidAction.EXECUTE,
      SIM = IFluidHandler.FluidAction.SIMULATE;

  private static FluidBlockEntity pipe(GameTestHelper h, BlockPos pos) {
    h.getLevel()
        .setBlockAndUpdate(pos, Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    return (FluidBlockEntity) h.getLevel().getBlockEntity(pos);
  }

  private static FluidStack fluid(int amount, int grade) {
    var f = new FluidStack(Fluids.WATER, amount);
    var tag = new CompoundTag();
    tag.putInt("grade", grade);
    f.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    return f;
  }

  private static long mass(Collection<FluidBlockEntity> cells) {
    return cells.stream().mapToLong(f -> f.cell().amount()).sum();
  }

  @GameTest(template = "empty", batch = "fluids_v0240", timeoutTicks = 100)
  public static void segmentIsImmediatelyAccessibleAcrossAllSixFacesWithoutEdgeTicks(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(7, 4, 7));
    var cells = new ArrayList<FluidBlockEntity>();
    var center = pipe(h, pos);
    cells.add(center);
    for (var d : Direction.values()) cells.add(pipe(h, pos.relative(d)));
    h.runAfterDelay(
        5,
        () -> {
          var source = cells.get(1).port(null);
          h.assertTrue(
              source.fill(fluid(2000, 7), SIM) == 2000 && mass(cells) == 0,
              "simulation changed pool");
          h.assertTrue(source.fill(fluid(2000, 7), EXEC) == 2000, "finite source refused fluid");
          for (var be : cells) {
            var cap = be.port(Direction.UP);
            h.assertTrue(
                cap.getTankCapacity(0) == 7000 && cap.getFluidInTank(0).getAmount() == 2000,
                "remote view is not the same segment");
            h.assertTrue(
                FluidSimulation.circuitContents(be).amounts().values().iterator().next() == 2000,
                "circuit read disagrees with segment");
          }
          int extracted = 0;
          for (var be : cells)
            extracted += FluidSimulation.manual(() -> be.port(null).drain(2000, EXEC)).getAmount();
          h.assertTrue(
              extracted == 2000 && mass(cells) == 0,
              "multiple physical capabilities duplicated the pool");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "fluids_v0240", timeoutTicks = 100)
  public static void incompatibleComponentBridgeRetainsMassAndReopensAfterEmptying(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(5, 3, 5));
    var a = pipe(h, pos);
    var c = pipe(h, pos.east(2));
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(
              a.port(null).fill(fluid(400, 1), EXEC) == 400
                  && c.port(null).fill(fluid(600, 2), EXEC) == 600,
              "finite separated sources refused components");
          pipe(h, pos.east());
        });
    h.runAfterDelay(
        15,
        () -> {
          h.assertTrue(
              FluidSimulation.segment(a) != FluidSimulation.segment(c)
                  && FluidSimulation.view(a).amount() == 400
                  && FluidSimulation.view(c).amount() == 600,
              "incompatible pipelines merged or lost volume");
          h.assertTrue(FluidSimulation.view(a).conflicts() > 0, "conflict warning absent");
          h.assertTrue(
              FluidSimulation.manual(() -> a.port(null).drain(400, EXEC)).getAmount() == 400,
              "conflicted side cannot be emptied");
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              FluidSimulation.segment(a) == FluidSimulation.segment(c)
                  && FluidSimulation.view(a).amount() == 600,
              "empty transition did not recover boundary");
          h.assertTrue(
              a.port(null)
                      .getFluidInTank(0)
                      .get(DataComponents.CUSTOM_DATA)
                      .copyTag()
                      .getInt("grade")
                  == 2,
              "reconnect lost components");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "fluids_v0240", timeoutTicks = 100)
  public static void demolitionExportsOnlyOneCheckpointShareAndClosesCachedOwner(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(4, 3, 4));
    var a = pipe(h, p);
    var b = pipe(h, p.east());
    var c = pipe(h, p.east(2));
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(a.port(null).fill(fluid(2001, 9), EXEC) == 2001, "seed refused");
          var old = b.port(null);
          int expected = b.cell().amount();
          h.getLevel().destroyBlock(b.getBlockPos(), true);
          var drops =
              h
                  .getLevel()
                  .getEntitiesOfClass(ItemEntity.class, new AABB(b.getBlockPos()).inflate(1))
                  .stream()
                  .filter(
                      e -> e.getItem().is(Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().asItem()))
                  .toList();
          h.assertTrue(
              drops.size() == 1 && drops.getFirst().getItem().getCount() == 1,
              "pipe drop duplicated");
          var data = drops.getFirst().getItem().get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
          var owned =
              FluidStack.parseOptional(
                  h.getLevel().registryAccess(), data.getCompound("StoredFluid"));
          h.assertTrue(
              owned.getAmount() == expected
                  && owned.get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 9,
              "portable share/components wrong");
          h.assertTrue(
              a.cell().amount() + c.cell().amount() + owned.getAmount() == 2001,
              "split/drop ledger lost or copied volume");
          h.assertTrue(
              old.drain(9999, EXEC).isEmpty() && b.cell().amount() == 0,
              "packed owner still owns portable fluid");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "fluids_v0240", timeoutTicks = 100)
  public static void hotReloadChangesCanonicalUuidAndRejectsCachedCapability(GameTestHelper h) {
    var a = pipe(h, h.absolutePos(new BlockPos(5, 3, 5)));
    h.runAfterDelay(
        5,
        () -> {
          a.port(null).fill(fluid(731, 3), EXEC);
          var old = a.port(null);
          var tag = a.saveWithFullMetadata(h.getLevel().registryAccess());
          tag.putUUID("FluidOwner", UUID.randomUUID());
          a.loadWithComponents(tag, h.getLevel().registryAccess());
          h.assertTrue(
              old.getTanks() == 0
                  && old.getFluidInTank(0).isEmpty()
                  && old.fill(fluid(1, 3), EXEC) == 0
                  && old.drain(1, EXEC).isEmpty(),
              "stale UUID capability remained writable");
          h.runAfterDelay(
              5,
              () -> {
                h.assertTrue(
                    a.port(null).getFluidInTank(0).getAmount() == 731,
                    "live reconstruction lost volume");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", batch = "fluids_v0240", timeoutTicks = 120)
  public static void poweredPumpSeparatesDifferentFluidsAndDeniesUnpoweredExternalTransfers(
      GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(7, 3, 7));
    factorycraft.multiblock.StructurePlacement.place(
        h.getLevel(),
        p,
        Content.FLUID_BLOCKS
            .get(FluidKind.PUMP)
            .get()
            .defaultBlockState()
            .setValue(FluidDeviceBlock.FACING, Direction.EAST));
    var pump = (FluidBlockEntity) h.getLevel().getBlockEntity(p);
    var input = pipe(h, pump.inputNozzle().west());
    var output = pipe(h, pump.outputNozzle().east());
    h.runAfterDelay(
        5,
        () -> {
          input.port(null).fill(new FluidStack(Fluids.WATER, 800), EXEC);
          output.port(null).fill(new FluidStack(Fluids.LAVA, 500), EXEC);
          var cap =
              h.getLevel()
                  .getCapability(
                      Capabilities.FluidHandler.BLOCK, pump.inputNozzle(), Direction.WEST);
          h.assertTrue(
              cap.fill(new FluidStack(Fluids.WATER, 100), EXEC) == 0,
              "unpowered pump accepted automatic flow");
          pump.power.restore(4000);
        });
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              FluidSimulation.segment(input) != FluidSimulation.segment(output),
              "pump merged passive segments");
          h.assertTrue(
              input.cell().amount() + output.cell().amount() + pump.cell().amount() == 1300,
              "pump changed fluid mass");
          h.assertTrue(
              output.stored().getFluid() == Fluids.LAVA && pump.stored().getFluid() == Fluids.WATER,
              "pump mixed opposite liquids");
          h.assertTrue(pump.power.getEnergyStored() < 4000, "pump moved without paying FE");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "fluids_v0240", timeoutTicks = 160)
  public static void segmentCheckpointSharesPersistAcrossFourChunksAndSeparateJvms(
      GameTestHelper h) {
    var level = h.getLevel();
    var positions =
        List.of(
            new BlockPos(575, 70, 511),
            new BlockPos(576, 70, 511),
            new BlockPos(576, 70, 512),
            new BlockPos(575, 70, 512));
    var chunks =
        positions.stream().map(p -> new net.minecraft.world.level.ChunkPos(p)).distinct().toList();
    for (var cp : chunks) {
      level.setChunkForced(cp.x, cp.z, true);
      level.getChunk(cp.x, cp.z);
    }
    boolean seed = positions.stream().allMatch(p -> level.getBlockEntity(p) == null);
    if (seed) for (var p : positions) pipe(h, p);
    h.runAfterDelay(
        10,
        () -> {
          try {
            var cells =
                positions.stream()
                    .map(
                        p -> {
                          h.assertTrue(
                              level.getBlockEntity(p) instanceof FluidBlockEntity,
                              "partial checkpoint: refusing to reseed");
                          return (FluidBlockEntity) level.getBlockEntity(p);
                        })
                    .toList();
            if (seed) {
              var source = cells.getFirst().port(null);
              h.assertTrue(
                  FluidSimulation.manual(() -> source.fill(fluid(3999, 24), EXEC)) == 3999,
                  "finite disk seed refused");
              h.assertTrue(
                  FluidSimulation.manual(() -> cells.getLast().port(null).drain(123, EXEC))
                          .getAmount()
                      == 123,
                  "far-end disk debit failed");
              h.assertTrue(source.fill(fluid(67, 24), EXEC) == 67, "disk credit failed");
            }
            h.assertTrue(
                mass(cells) == 3943, "four independent chunk shares lost/duplicated volume");
            var group = FluidSimulation.segment(cells.getFirst());
            h.assertTrue(
                group != null && group.size() == 4 && group.amount() == 3943,
                "disk segment did not reconstruct");
            for (var be : cells) {
              h.assertTrue(
                  FluidSimulation.segment(be) == group
                      && be.stored().get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade")
                          == 24,
                  "disk components/membership changed");
            }
            var ids = cells.stream().map(c -> c.fluidOwner().toString()).sorted().toList();
            h.assertTrue(new HashSet<>(ids).size() == 4, "canonical owner UUID duplicated");
            System.out.println(
                "FACTORYCRAFT_SEGMENT_DISK: "
                    + (seed ? "SEEDED" : "VERIFIED")
                    + " 3943 mB grade=24 owners="
                    + ids);
            h.succeed();
          } finally {
            for (var cp : chunks) level.setChunkForced(cp.x, cp.z, false);
          }
        });
  }

  private static FluidBlockEntity pump(GameTestHelper h, BlockPos p) {
    factorycraft.multiblock.StructurePlacement.place(
        h.getLevel(),
        p,
        Content.FLUID_BLOCKS
            .get(FluidKind.PUMP)
            .get()
            .defaultBlockState()
            .setValue(FluidDeviceBlock.FACING, Direction.EAST));
    return (FluidBlockEntity) h.getLevel().getBlockEntity(p);
  }

  @GameTest(template = "empty", batch = "fluids_v0240", timeoutTicks = 240)
  public static void seriesPumpsSharePhysicalPortBudgetsAndPreserveFiniteSupply(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(6, 3, 6));
    var a = pump(h, p);
    var b = pump(h, p.east(2));
    var source = pipe(h, a.inputNozzle().west());
    var sink = pipe(h, b.outputNozzle().east());
    h.runAfterDelay(
        5,
        () -> {
          source.port(null).fill(new FluidStack(Fluids.WATER, 700), EXEC);
          a.power.restore(4000);
          b.power.restore(4000);
        });
    h.runAfterDelay(
        180,
        () -> {
          h.assertTrue(
              source.cell().amount() + a.cell().amount() + b.cell().amount() + sink.cell().amount()
                  == 700,
              "series pumps lost or duplicated fluid");
          h.assertTrue(
              sink.cell().amount() == 700
                  && a.power.getEnergyStored() < 4000
                  && b.power.getEnergyStored() < 4000,
              "series pumps stopped before delivering finite supply or used free power");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "fluids_v0240", timeoutTicks = 240)
  public static void parallelPumpsHaveSeparateBudgetsAgainstOneSharedSource(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(6, 3, 5));
    var a = pump(h, p);
    var b = pump(h, p.south(2));
    var pipes = new ArrayList<FluidBlockEntity>();
    for (int z = 0; z < 3; z++) {
      pipes.add(pipe(h, p.west(3).south(z)));
      pipes.add(pipe(h, p.east(2).south(z)));
    }
    pipes.add(pipe(h, a.inputNozzle().west()));
    pipes.add(pipe(h, b.inputNozzle().west()));
    pipes.add(pipe(h, a.outputNozzle().east()));
    pipes.add(pipe(h, b.outputNozzle().east()));
    var source = pipes.getFirst();
    var sink = pipes.get(1);
    h.runAfterDelay(
        5,
        () -> {
          source.port(null).fill(new FluidStack(Fluids.WATER, 1500), EXEC);
          a.power.restore(4000);
          b.power.restore(4000);
        });
    h.runAfterDelay(
        180,
        () -> {
          h.assertTrue(
              mass(pipes) + a.cell().amount() + b.cell().amount() == 1500,
              "parallel debit lost or duplicated source");
          h.assertTrue(
              FluidSimulation.view(sink).amount() == 1500
                  && a.power.getEnergyStored() < 4000
                  && b.power.getEnergyStored() < 4000,
              "parallel pumps did not deliver real finite source: sink="
                  + FluidSimulation.view(sink).amount()
                  + ", source="
                  + FluidSimulation.view(source).amount()
                  + ", hands="
                  + a.cell().amount()
                  + "/"
                  + b.cell().amount()
                  + ", FE="
                  + a.power.getEnergyStored()
                  + "/"
                  + b.power.getEnergyStored());
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "fluids_v0240", timeoutTicks = 120)
  public static void dryPumpWakesWhenWorldWaterAppearsBehindRemoteInputPart(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(7, 3, 7));
    var pump = pump(h, p);
    var sink = pipe(h, pump.outputNozzle().east());
    h.runAfterDelay(5, () -> pump.power.restore(2000));
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              pump.power.getEnergyStored() == 2000 && pump.cell().amount() == 0,
              "dry pump billed work");
          var source = pump.inputNozzle().west();
          h.getLevel()
              .setBlockAndUpdate(
                  source, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        });
    h.runAfterDelay(
        70,
        () -> {
          h.assertTrue(
              sink.cell().amount() > 0 && pump.power.getEnergyStored() < 2000,
              "water behind casing did not wake idle pump");
          h.succeed();
        });
  }
}
