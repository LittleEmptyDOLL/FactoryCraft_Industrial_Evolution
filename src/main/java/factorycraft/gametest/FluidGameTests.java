package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class FluidGameTests {
  private static final IFluidHandler.FluidAction EXEC = IFluidHandler.FluidAction.EXECUTE,
      SIM = IFluidHandler.FluidAction.SIMULATE;

  private static FluidBlockEntity put(GameTestHelper h, FluidKind kind, int x, int y, int z) {
    var state = Content.FLUID_BLOCKS.get(kind).get().defaultBlockState();
    if (kind == FluidKind.PUMP) state = state.setValue(FluidDeviceBlock.FACING, Direction.EAST);
    factorycraft.multiblock.StructurePlacement.place(
        h.getLevel(), h.absolutePos(new BlockPos(x, y, z)), state);
    return (FluidBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x, y, z)));
  }

  private static IFluidHandler physical(FluidBlockEntity be, int index) {
    var profile = factorycraft.multiblock.MachineProfile.of(be.getBlockState());
    if (profile == null) return be.port(null);
    var p = factorycraft.multiblock.MachinePorts.definitions(profile).get(index);
    var facing = profile.facing(be.getBlockState());
    return be.getLevel()
        .getCapability(
            Capabilities.FluidHandler.BLOCK,
            p.position(profile, be.getBlockPos(), facing),
            p.side(facing));
  }

  private static FluidStack water(int amount) {
    return new FluidStack(Fluids.WATER, amount);
  }

  private static long volume(FluidBlockEntity... blocks) {
    return Arrays.stream(blocks).mapToLong(b -> b.cell().amount()).sum();
  }

  private static void source(GameTestHelper h, int x, int y, int z) {
    var pos = h.absolutePos(new BlockPos(x, y, z));
    var level = h.getLevel();
    level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
    for (var d : Direction.Plane.HORIZONTAL)
      if (level.getBlockState(pos.relative(d)).isAir())
        level.setBlockAndUpdate(pos.relative(d), Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void pipesMoveFiniteFluidBetweenTanksWithoutLoss(GameTestHelper h) {
    var a = put(h, FluidKind.TANK, 3, 2, 4);
    var b = put(h, FluidKind.PIPE, 5, 2, 5);
    var c = put(h, FluidKind.PIPE, 6, 2, 5);
    var d = put(h, FluidKind.TANK, 8, 2, 6);
    h.runAfterDelay(
        5,
        () ->
            h.assertTrue(
                physical(a, 0).fill(water(8000), EXEC) == 8000, "source tank refused water"));
    h.runAfterDelay(
        220,
        () -> {
          h.assertTrue(d.cell().amount() > 500, "pipes did not transfer to the destination");
          h.assertTrue(volume(a, b, c, d) == 8000, "pipe line lost/duplicated water");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void pumpRequiresEnergyAndThenPumpsARealWaterSource(GameTestHelper h) {
    var pump = put(h, FluidKind.PUMP, 3, 2, 4);
    var tank = put(h, FluidKind.TANK, 5, 2, 5);
    source(h, 1, 2, 4);
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(volume(pump, tank) == 0, "unpowered pump generated water");
          h.assertTrue(
              pump.energyPort.receiveEnergy(2000, false) == 2000,
              "pump refused FE capability input");
        });
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              tank.cell().amount() > 0 && pump.power.getEnergyStored() < 2000,
              "powered pump did not move water or spend energy");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 600)
  public static void aGeneratorPowersTheWaterPipeTankChain(GameTestHelper h) {
    var pump = put(h, FluidKind.PUMP, 3, 2, 4);
    source(h, 1, 2, 4);
    put(h, FluidKind.PIPE, 4, 2, 4);
    put(h, FluidKind.PIPE, 5, 2, 4);
    put(h, FluidKind.PIPE, 6, 2, 4);
    var tank = put(h, FluidKind.TANK, 8, 2, 5);
    factorycraft.multiblock.StructurePlacement.place(
        h.getLevel(),
        h.absolutePos(new BlockPos(3, 2, 7)),
        Content.FACTORY.get(FactoryKind.GENERATOR).get().defaultBlockState());
    ((MachineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3, 2, 7))))
        .items.setStackInSlot(8, new ItemStack(Items.COAL));
    factorycraft.multiblock.StructurePlacement.place(
        h.getLevel(),
        h.absolutePos(new BlockPos(5, 2, 7)),
        Content.FACTORY.get(FactoryKind.MEDIUM_POLE).get().defaultBlockState());
    h.succeedWhen(
        () ->
            h.assertTrue(
                tank.cell().amount() >= 1000 && pump.power.getEnergyStored() > 0,
                "natural source has not reached the tank through powered pipes"));
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void fluidsAndComponentsCannotMixAndReturnedStacksAreCopies(GameTestHelper h) {
    var tank = put(h, FluidKind.TANK, 3, 2, 3);
    h.runAfterDelay(
        5,
        () -> {
          var special = water(1000);
          var data = new CompoundTag();
          data.putInt("grade", 7);
          special.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
          var port = physical(tank, 0);
          h.assertTrue(
              port.fill(special, SIM) == 1000 && tank.cell().amount() == 0,
              "simulation mutated storage");
          h.assertTrue(
              port.fill(special, EXEC) == 1000
                  && port.fill(water(500), EXEC) == 0
                  && port.fill(new FluidStack(Fluids.LAVA, 500), EXEC) == 0,
              "different fluids/components mixed");
          var copy = port.getFluidInTank(0);
          copy.setAmount(1);
          h.assertTrue(tank.cell().amount() == 1000, "capability exposed a mutable stored stack");
          h.assertTrue(
              port.drain(water(1000), EXEC).isEmpty(), "component mismatch drained the tank");
          var result = port.drain(special, EXEC);
          h.assertTrue(
              result.getAmount() == 1000
                  && result.get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 7,
              "drain lost fluid components");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void cachedSidedPortsRespectPumpRotation(GameTestHelper h) {
    var pump = put(h, FluidKind.PUMP, 3, 2, 3);
    h.runAfterDelay(
        5,
        () -> {
          var input =
              h.getLevel()
                  .getCapability(
                      Capabilities.FluidHandler.BLOCK, pump.inputNozzle(), Direction.WEST);
          var output =
              h.getLevel()
                  .getCapability(
                      Capabilities.FluidHandler.BLOCK, pump.outputNozzle(), Direction.EAST);
          h.assertTrue(
              FluidSimulation.manual(() -> input.fill(water(500), EXEC)) == 500
                  && FluidSimulation.manual(() -> output.fill(water(500), EXEC)) == 0
                  && FluidSimulation.manual(() -> input.drain(100, EXEC)).isEmpty()
                  && FluidSimulation.manual(() -> output.drain(100, EXEC)).getAmount() == 100,
              "physical pump port directions wrong");
          var part = (CasingBlockEntity) h.getLevel().getBlockEntity(pump.inputNozzle());
          part.onChunkUnloaded();
          h.assertTrue(
              FluidSimulation.manual(() -> input.fill(water(10), EXEC)) == 0
                  && output.drain(10, EXEC).isEmpty(),
              "unloaded structure remained active");
          part.onLoad();
          h.assertTrue(
              FluidSimulation.manual(() -> input.fill(water(10), EXEC)) == 10
                  && FluidSimulation.manual(() -> output.drain(410, EXEC)).getAmount() == 410,
              "physical ports failed to resume");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void fullOutputStopsPumpWithoutSpendingEnergyUntilDrained(GameTestHelper h) {
    var pump = put(h, FluidKind.PUMP, 3, 2, 4);
    var tank = put(h, FluidKind.TANK, 5, 2, 5);
    source(h, 1, 2, 4);
    h.runAfterDelay(
        5,
        () -> {
          FluidSimulation.manual(() -> physical(pump, 0).fill(water(4000), EXEC));
          physical(tank, 0).fill(water(250000), EXEC);
          pump.energyPort.receiveEnergy(2000, false);
        });
    h.runAfterDelay(
        70,
        () -> {
          h.assertTrue(
              pump.power.getEnergyStored() == 2000 && volume(pump, tank) == 254000,
              "blocked pump consumed energy or fluid");
          physical(tank, 0).drain(1000, EXEC);
        });
    h.runAfterDelay(
        140,
        () -> {
          h.assertTrue(
              tank.cell().amount() > 249000 && pump.power.getEnergyStored() < 2000,
              "draining did not wake the sleeping pump");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 140)
  public static void unloadedPartsSuspendCachedPortsAndReconnectWithTheirFluid(GameTestHelper h) {
    var a = put(h, FluidKind.PIPE, 3, 2, 3);
    var b = put(h, FluidKind.PIPE, 4, 2, 3);
    h.runAfterDelay(
        5,
        () -> {
          physical(a, 0).fill(water(1000), EXEC);
          h.runAfterDelay(
              5,
              () -> {
                var port = physical(b, 0);
                b.onChunkUnloaded();
                int old = b.cell().amount();
                int filled = 0;
                long expected = 1000L + filled;
                h.assertTrue(
                    port.fill(water(100), EXEC) == 0 && port.drain(100, EXEC).isEmpty(),
                    "cached port modified an unloaded cell");
                h.runAfterDelay(
                    20,
                    () -> {
                      h.assertTrue(
                          b.cell().amount() == old && volume(a, b) == expected,
                          "unload lost fluid or kept transferring");
                      b.onLoad();
                      h.runAfterDelay(
                          40,
                          () -> {
                            h.assertTrue(
                                FluidSimulation.segment(a) == FluidSimulation.segment(b)
                                    && volume(a, b) == expected,
                                "reload did not reconnect original fluid");
                            h.succeed();
                          });
                    });
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 140)
  public static void harvestingAndReplacingATankKeepsItsContentsExactlyOnce(GameTestHelper h) {
    var tank = put(h, FluidKind.TANK, 3, 2, 3);
    h.runAfterDelay(
        5,
        () -> {
          var port = physical(tank, 0);
          port.fill(water(7000), EXEC);
          var pos = tank.getBlockPos();
          h.getLevel().destroyBlock(pos, true);
          var drops =
              h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                  .filter(
                      e -> e.getItem().is(Content.FLUID_BLOCKS.get(FluidKind.TANK).get().asItem()))
                  .toList();
          h.assertTrue(
              drops.size() == 1 && drops.getFirst().getItem().getCount() == 1,
              "harvesting duplicated the tank");
          h.assertTrue(
              port.drain(7000, EXEC).isEmpty(), "old tank capability still owns dropped water");
          var player = h.makeMockPlayer(GameType.SURVIVAL);
          player.getAbilities().instabuild = false;
          player.setItemInHand(InteractionHand.MAIN_HAND, drops.getFirst().getItem().copy());
          drops.getFirst().discard();
          var target = h.absolutePos(new BlockPos(8, 2, 3));
          var hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
          var result = PlacementTestSupport.place(player, target);
          h.assertTrue(
              result.consumesAction() && player.getMainHandItem().isEmpty(),
              "filled tank item was not placed/consumed");
          h.runAfterDelay(
              5,
              () -> {
                var restored = (FluidBlockEntity) h.getLevel().getBlockEntity(target);
                h.assertTrue(
                    restored.cell().amount() == 7000, "tank item lost or duplicated its fluid");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void bucketsUseTheActualBlockInteractionWithoutLosingContainers(GameTestHelper h) {
    var tank = put(h, FluidKind.TANK, 3, 2, 3);
    h.runAfterDelay(
        5,
        () -> {
          var player = h.makeMockPlayer(GameType.SURVIVAL);
          player.getAbilities().instabuild = false;
          player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
          var pos = tank.inputNozzle();
          player.setPos(Vec3.atCenterOf(pos).add(0, 0, -2));
          var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
          h.getLevel()
              .getBlockState(pos)
              .useItemOn(
                  player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
          h.assertTrue(
              tank.cell().amount() == 1000 && player.getMainHandItem().is(Items.BUCKET),
              "bucket fill lost fluid or container");
          h.getLevel()
              .getBlockState(pos)
              .useItemOn(
                  player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
          h.assertTrue(
              tank.cell().amount() == 0 && player.getMainHandItem().is(Items.WATER_BUCKET),
              "bucket drain lost fluid or duplicated a container");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void multiplePortsCannotExtractTheSameFluidTwice(GameTestHelper h) {
    var tank = put(h, FluidKind.TANK, 3, 2, 3);
    h.runAfterDelay(
        5,
        () -> {
          physical(tank, 0).fill(water(1000), EXEC);
          int extracted =
              physical(tank, 0).drain(750, EXEC).getAmount()
                  + physical(tank, 2).drain(750, EXEC).getAmount();
          h.assertTrue(
              extracted == 1000 && tank.cell().amount() == 0, "two viewers/ports duplicated water");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void verticalConnectionsWorkWithoutGravityOrChunkForcing(GameTestHelper h) {
    var a = put(h, FluidKind.TANK, 3, 2, 3);
    var d = put(h, FluidKind.TANK, 3, 7, 3);
    var pipes = new ArrayList<FluidBlockEntity>();
    for (int y = 2; y <= 7; y++) pipes.add(put(h, FluidKind.PIPE, 2, y, 1));
    h.runAfterDelay(5, () -> physical(a, 0).fill(water(8000), EXEC));
    h.runAfterDelay(
        220,
        () -> {
          long total = volume(a, d) + pipes.stream().mapToLong(b -> b.cell().amount()).sum();
          h.assertTrue(
              d.cell().amount() > 0 && total == 8000,
              "vertical external nozzle route failed or lost fluid");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void fluidEntitiesReconstructAndResumeWithComponents(GameTestHelper h) {
    var a = put(h, FluidKind.TANK, 3, 2, 3);
    var b = put(h, FluidKind.PIPE, 5, 2, 4);
    var c = put(h, FluidKind.TANK, 7, 2, 5);
    h.runAfterDelay(5, () -> physical(a, 0).fill(water(8000), EXEC));
    h.runAfterDelay(
        20,
        () -> {
          var positions = List.of(a.getBlockPos(), b.getBlockPos(), c.getBlockPos());
          var copies = new ArrayList<BlockEntity>();
          var registry = h.getLevel().registryAccess();
          for (var pos : positions) {
            var be = h.getLevel().getBlockEntity(pos);
            copies.add(
                BlockEntity.loadStatic(
                    pos, be.getBlockState(), be.saveWithFullMetadata(registry), registry));
          }
          for (var pos : positions) h.getLevel().removeBlockEntity(pos);
          for (var be : copies) h.getLevel().setBlockEntity(be);
        });
    h.runAfterDelay(
        110,
        () -> {
          long total = 0;
          for (var old : List.of(a, b, c))
            total +=
                ((FluidBlockEntity) h.getLevel().getBlockEntity(old.getBlockPos())).cell().amount();
          h.assertTrue(
              total == 8000
                  && ((FluidBlockEntity) h.getLevel().getBlockEntity(c.getBlockPos()))
                          .cell()
                          .amount()
                      > 0,
              "reconstructed line lost fluid or stopped");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void fullIntegerVolumesSurviveMenuFieldsAndLegacyOverflow(GameTestHelper h) {
    var tank = put(h, FluidKind.TANK, 3, 2, 3);
    h.runAfterDelay(
        5,
        () -> {
          tank.cell().restore(new FluidKey(water(1)), Integer.MAX_VALUE);
          tank.setChanged();
          var menu =
              new FluidMenu(
                  42, h.makeMockPlayer(GameType.SURVIVAL).getInventory(), tank, tank.data);
          h.assertTrue(
              menu.amount() == Integer.MAX_VALUE && menu.capacity() == Integer.MAX_VALUE,
              "menu truncated 32-bit volume");
          h.assertTrue(
              physical(tank, 0).fill(water(1), EXEC) == 0
                  && physical(tank, 0).drain(1000, EXEC).getAmount() == 1000,
              "oversized legacy storage was discarded or overflowed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void fluidsPersistInFourChunksAcrossServerProcesses(GameTestHelper h) {
    var level = h.getLevel();
    var positions =
        List.of(
            new BlockPos(175, 70, 127),
            new BlockPos(176, 70, 127),
            new BlockPos(176, 70, 128),
            new BlockPos(175, 70, 128));
    for (var pos : positions) level.getChunkAt(pos);
    if (!(level.getBlockEntity(positions.getFirst()) instanceof FluidBlockEntity)) {
      for (int i = 0; i < positions.size(); i++)
        level.setBlockAndUpdate(
            positions.get(i), Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
      var fluid = water(8000);
      var tag = new CompoundTag();
      tag.putInt("grade", 7);
      fluid.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      var tank = (FluidBlockEntity) level.getBlockEntity(positions.getFirst());
      tank.cell().restore(new FluidKey(fluid), 8000);
      tank.setChanged();
      System.out.println(
          "FACTORYCRAFT_FLUID_DISK: SEEDED 8000 mB with components across four chunks");
    } else {
      long total = 0;
      for (var pos : positions) {
        h.assertTrue(
            level.getBlockEntity(pos) instanceof FluidBlockEntity, "missing disk fluid block");
        var be = (FluidBlockEntity) level.getBlockEntity(pos);
        total += be.cell().amount();
        if (be.cell().amount() > 0)
          h.assertTrue(
              be.stored().get(DataComponents.CUSTOM_DATA).copyTag().getInt("grade") == 7,
              "disk fluid lost data components");
      }
      h.assertTrue(
          total == 8000,
          "disk fixture lost/duplicated fluid: "
              + total
              + "; cells="
              + positions.stream()
                  .map(p -> ((FluidBlockEntity) level.getBlockEntity(p)).cell().amount())
                  .toList());
      System.out.println(
          "FACTORYCRAFT_FLUID_DISK: VERIFIED 8000 mB and components across four chunks");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void restoringDataIntoALiveSleepingPipeWakesItsNetwork(GameTestHelper h) {
    var a = put(h, FluidKind.PIPE, 3, 2, 3);
    var b = put(h, FluidKind.PIPE, 4, 2, 3);
    h.runAfterDelay(
        10,
        () -> {
          var registry = h.getLevel().registryAccess();
          var tag = a.saveWithFullMetadata(registry);
          tag.put("StoredFluid", water(1000).saveOptional(registry));
          a.loadWithComponents(tag, registry);
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(
              b.cell().amount() > 0 && volume(a, b) == 1000,
              "live NBT restoration did not wake cached edges");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void chunkUnloadEventFreezesPortsBeforeTheSaveSnapshot(GameTestHelper h) {
    var origin = h.absolutePos(new BlockPos(0, 0, 0));
    int x = (origin.getX() >> 4) + 100, z = (origin.getZ() >> 4) + 100;
    var level = h.getLevel();
    level.setChunkForced(x, z, true);
    var pos = new BlockPos((x << 4) + 8, 70, (z << 4) + 8);
    level.removeBlock(pos, false);
    level.setBlockAndUpdate(
        pos, Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    h.runAfterDelay(
        5,
        () -> {
          try {
            var be = (FluidBlockEntity) level.getBlockEntity(pos);
            var port = physical(be, 0);
            port.fill(water(400), EXEC);
            var chunk = level.getChunkAt(pos);
            chunk.setUnsaved(false);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk));
            h.assertTrue(
                !be.loaded() && port.drain(400, EXEC).isEmpty() && chunk.isUnsaved(),
                "outgoing chunk was not frozen/dirty before serialization");
            var copy =
                (FluidBlockEntity)
                    BlockEntity.loadStatic(
                        pos,
                        be.getBlockState(),
                        be.saveWithFullMetadata(level.registryAccess()),
                        level.registryAccess());
            h.assertTrue(copy.cell().amount() == 400, "pre-unload snapshot did not preserve fluid");
            be.onLoad();
            h.assertTrue(port.drain(400, SIM).isEmpty(), "rebuilding owner exposed fluid");
            h.runAfterDelay(
                5,
                () -> {
                  h.assertTrue(
                      port.drain(400, SIM).getAmount() == 400,
                      "unchanged canonical port did not resume after rebuild");
                  level.setChunkForced(x, z, false);
                  h.succeed();
                });
          } catch (RuntimeException error) {
            level.setChunkForced(x, z, false);
            throw error;
          }
        });
  }
}
