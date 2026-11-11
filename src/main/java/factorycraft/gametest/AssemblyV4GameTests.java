package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.petroleum.PetroleumFluids;
import factorycraft.registry.Content;
import factorycraft.thermal.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

/** Fresh schema-4 integration fixtures. Old schema-3 worlds are never reseeded or migrated. */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class AssemblyV4GameTests {
  private static final IFluidHandler.FluidAction EXEC = IFluidHandler.FluidAction.EXECUTE;

  private static FactoryBlockEntity place(
      GameTestHelper h, MachineProfile profile, BlockPos controller, Direction facing) {
    var state =
        BuiltInRegistries.BLOCK
            .get(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, profile.id))
            .defaultBlockState()
            .setValue(FactoryBlock.FACING, facing);
    StructurePlacement.place(h.getLevel(), controller, state);
    var be = (FactoryBlockEntity) h.getLevel().getBlockEntity(controller);
    profile.cells(controller, facing).forEach(p -> h.getLevel().getBlockEntity(p).onLoad());
    h.assertTrue(AssemblyAccess.owner(be).assembly().ready(), "new assembly not ready: " + profile);
    return be;
  }

  private static IFluidHandler port(GameTestHelper h, FactoryBlockEntity be, MachinePorts.Port p) {
    var profile = MachineProfile.of(be.getBlockState());
    var facing = profile.facing(be.getBlockState());
    return h.getLevel()
        .getCapability(
            Capabilities.FluidHandler.BLOCK,
            p.position(profile, be.getBlockPos(), facing),
            p.side(facing));
  }

  @GameTest(template = "empty", timeoutTicks = 450)
  public static void physicalBothBoilerPortsFeedSteamEngineWithoutReturningEveryInputBatch(
      GameTestHelper h) {
    var boiler =
        (ThermalBlockEntity)
            place(
                h, MachineProfile.BOILER, h.absolutePos(new BlockPos(10, 3, 15)), Direction.NORTH);
    var engine =
        (ThermalBlockEntity)
            place(
                h, MachineProfile.ENGINE, h.absolutePos(new BlockPos(10, 3, 11)), Direction.NORTH);
    var supplyPos = h.absolutePos(new BlockPos(8, 3, 16));
    var steamPos = h.absolutePos(new BlockPos(10, 3, 14));
    var pipe = Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState();
    h.getLevel().setBlockAndUpdate(supplyPos, pipe);
    h.getLevel().setBlockAndUpdate(steamPos, pipe);
    var supply = (FluidBlockEntity) h.getLevel().getBlockEntity(supplyPos);
    var steam = (FluidBlockEntity) h.getLevel().getBlockEntity(steamPos);
    supply.onLoad();
    steam.onLoad();
    supply.cell().restore(new FluidKey(new FluidStack(Fluids.WATER, 1)), 1000);
    boiler.items.setStackInSlot(0, new ItemStack(Items.COAL));
    h.runAfterDelay(
        350,
        () -> {
          int fe = engine.engine.energy();
          long fluids =
              supply.cell().amount()
                  + steam.cell().amount()
                  + boiler.water.amount()
                  + boiler.steam.amount()
                  + engine.steam.amount();
          h.assertTrue(fe > 0 && fe % 12 == 0, "BOTH intake stalled live steam production");
          h.assertTrue(
              fluids + fe / 12 == 1000, "physical boiler/engine line lost or duplicated fluid/FE");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void mirrorReassignsPhysicalPortsWithoutMovingOwnedFluids(GameTestHelper h) {
    var be =
        (MachineBlockEntity)
            place(
                h, MachineProfile.REFINERY, h.absolutePos(new BlockPos(8, 3, 8)), Direction.NORTH);
    be.petroleum().cells.get(4).restore(new FluidKey(PetroleumFluids.HEAVY.stack(1)), 137);
    var previous =
        MachinePorts.definitions(MachineProfile.REFINERY).stream()
            .filter(p -> p.name().equals("heavy"))
            .findFirst()
            .orElseThrow();
    var cached = port(h, be, previous);
    h.assertTrue(
        cached.getFluidInTank(0).getAmount() == 137 && be.assembly().mirrorPorts(),
        "mirror failed");
    h.assertTrue(cached.getFluidInTank(0).isEmpty(), "old physical point retained old channel");
    var heavy =
        MachinePorts.forController(be).stream()
            .filter(p -> p.name().equals("heavy"))
            .findFirst()
            .orElseThrow();
    h.assertTrue(
        !heavy.at().equals(previous.at())
            && port(h, be, heavy).getFluidInTank(0).getAmount() == 137,
        "mirror moved/lost owned fluid");
    var cell =
        h.getLevel()
            .getBlockEntity(
                heavy.position(MachineProfile.REFINERY, be.getBlockPos(), Direction.NORTH))
            .getModelData()
            .get(MachineModelData.CELL);
    h.assertTrue(cell != null && cell.mirrored(), "visual port did not follow server mirror state");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void previewRequiresRealNonairLoadedHitWithinPlayerReach(GameTestHelper h) {
    var at = h.absolutePos(new BlockPos(8, 2, 8));
    h.getLevel().setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setPos(at.getX() + .5, at.getY() + 1, at.getZ() + .5);
    var hit = new net.minecraft.world.phys.BlockHitResult(at.getCenter(), Direction.UP, at, false);
    h.assertTrue(PlacementTarget.valid(h.getLevel(), player, hit), "reachable surface rejected");
    h.assertTrue(
        !PlacementTarget.valid(
            h.getLevel(),
            player,
            net.minecraft.world.phys.BlockHitResult.miss(at.getCenter(), Direction.UP, at)),
        "MISS produced preview");
    h.assertTrue(
        !PlacementTarget.valid(
            h.getLevel(),
            player,
            new net.minecraft.world.phys.BlockHitResult(
                at.above(3).getCenter(), Direction.DOWN, at.above(3), false)),
        "air produced preview");
    player.setPos(at.getX() + 20, at.getY() + 1, at.getZ());
    h.assertTrue(
        !PlacementTarget.valid(h.getLevel(), player, hit), "distant surface produced preview");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void allPhysicalFluidPortsMatchTheirCapabilitiesInFourRotations(GameTestHelper h) {
    for (var profile : MachineProfile.values())
      for (var facing : Direction.Plane.HORIZONTAL) {
        var pos = h.absolutePos(new BlockPos(10, 3, 10));
        var be = place(h, profile, pos, facing);
        for (var p : MachinePorts.definitions(profile)) {
          var physical = p.position(profile, pos, facing);
          var cap = port(h, be, p);
          h.assertTrue(
              cap != null && cap.getTanks() > 0,
              "missing physical capability " + profile + "/" + p.name() + "/" + facing);
          h.assertTrue(
              FluidBlocks.port(h.getLevel(), physical, p.side(facing)),
              "pipe visual rejected actual port");
          h.assertTrue(
              h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, physical, Direction.UP)
                  == null,
              "vertical casing bypass");
          h.assertTrue(
              h.getLevel()
                      .getCapability(Capabilities.FluidHandler.BLOCK, physical, (Direction) null)
                  == null,
              "unsided casing bypass");
        }
        AssemblyAccess.owner(be).assembly().removeWhole(false);
      }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void boilerBothSidesShareWaterWhileSteamUsesItsOwnChannel(GameTestHelper h) {
    var be =
        (ThermalBlockEntity)
            place(h, MachineProfile.BOILER, h.absolutePos(new BlockPos(8, 3, 8)), Direction.NORTH);
    var ports = MachinePorts.definitions(MachineProfile.BOILER);
    var left = port(h, be, ports.get(0));
    var right = port(h, be, ports.get(1));
    var steam = port(h, be, ports.get(2));
    h.assertTrue(left.fill(new FluidStack(Fluids.WATER, 731), EXEC) == 731, "BOTH input failed");
    h.assertTrue(
        right.drain(31, EXEC).getAmount() == 31 && be.water.amount() == 700,
        "opposite BOTH output not shared");
    h.assertTrue(
        left.fill(new FluidStack(Fluids.LAVA, 10), EXEC) == 0
            && left.fill(new FluidStack(SteamFluids.STEAM.get(), 10), EXEC) == 0,
        "water channel accepted incompatible fluid");
    h.assertTrue(
        steam.fill(new FluidStack(Fluids.WATER, 20), EXEC) == 0 && steam.drain(20, EXEC).isEmpty(),
        "steam output reached water channel");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void coincidentBothPortKeepsSeparateInputAndOutputChannels(GameTestHelper h) {
    var be =
        (MachineBlockEntity)
            place(
                h, MachineProfile.CHEMICAL, h.absolutePos(new BlockPos(8, 3, 8)), Direction.NORTH);
    var p = MachinePorts.definitions(MachineProfile.CHEMICAL).getFirst();
    var pos = p.position(MachineProfile.CHEMICAL, be.getBlockPos(), Direction.NORTH);
    var both =
        new MachinePorts.Port("test-both", p.at(), null, p.face(), MachinePorts.Mode.BOTH, 0, 2);
    var cap = MachineFluidAccess.view(be, pos, () -> both);
    be.petroleum().cells.get(2).restore(new FluidKey(PetroleumFluids.GAS.stack(1)), 113);
    h.assertTrue(
        cap.getTanks() == 2 && cap.fill(new FluidStack(Fluids.WATER, 97), EXEC) == 97,
        "separate BOTH input failed");
    h.assertTrue(
        cap.drain(13, EXEC).is(PetroleumFluids.GAS.source.get())
            && be.petroleum().cells.get(2).amount() == 100
            && be.petroleum().cells.get(0).amount() == 97,
        "BOTH mixed internal channels");
    h.assertTrue(cap.fill(new FluidStack(Fluids.LAVA, 1), EXEC) == 0, "incompatible fluids mixed");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void cachedPhysicalPortsCloseOnUnloadAndStructuralDamage(GameTestHelper h) {
    var be = place(h, MachineProfile.TANK, h.absolutePos(new BlockPos(8, 3, 8)), Direction.NORTH);
    h.runAfterDelay(
        5,
        () -> {
          var p = MachinePorts.definitions(MachineProfile.TANK).getFirst();
          var cap = port(h, be, p);
          cap.fill(new FluidStack(Fluids.WATER, 731), EXEC);
          var part =
              (CasingBlockEntity)
                  h.getLevel()
                      .getBlockEntity(
                          p.position(MachineProfile.TANK, be.getBlockPos(), Direction.NORTH));
          part.onChunkUnloaded();
          h.assertTrue(
              cap.getTanks() == 0 && cap.fill(new FluidStack(Fluids.WATER, 1), EXEC) == 0,
              "cached capability remained active during unload");
          part.onLoad();
          h.runAfterDelay(
              5,
              () -> {
                h.assertTrue(cap.getFluidInTank(0).getAmount() == 731, "reload lost volume");
                h.getLevel().removeBlock(part.getBlockPos(), false);
                h.assertTrue(
                    cap.getTanks() == 0 && cap.drain(100, EXEC).isEmpty(),
                    "cached capability accessed removed structure");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 150)
  public static void realPipesExchangeWaterThroughRemoteTankPort(GameTestHelper h) {
    var be =
        (FluidBlockEntity)
            place(h, MachineProfile.TANK, h.absolutePos(new BlockPos(8, 3, 8)), Direction.NORTH);
    var p = MachinePorts.definitions(MachineProfile.TANK).getFirst();
    var physical = p.position(MachineProfile.TANK, be.getBlockPos(), Direction.NORTH);
    var pipePos = physical.north();
    h.getLevel()
        .setBlockAndUpdate(
            pipePos,
            Content.FLUID_BLOCKS
                .get(FluidKind.PIPE)
                .get()
                .defaultBlockState()
                .setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH,
                    true));
    var pipe = (FluidBlockEntity) h.getLevel().getBlockEntity(pipePos);
    pipe.onLoad();
    pipe.cell().restore(new FluidKey(new FluidStack(Fluids.WATER, 1)), 800);
    h.assertTrue(
        !physical.equals(be.getBlockPos())
            && FluidBlocks.port(h.getLevel(), physical, Direction.NORTH),
        "fixture did not use a remote port");
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              be.cell().amount() > 0 && be.cell().amount() + pipe.cell().amount() == 800,
              "pipe did not exchange/conserve fluid through physical port");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void machineMenuUsesClickedPartAndRejectsRemotePlayers(GameTestHelper h) {
    var be =
        place(h, MachineProfile.REFINERY, h.absolutePos(new BlockPos(8, 3, 8)), Direction.NORTH);
    var cell = MachineProfile.REFINERY.offset(be.getBlockPos(), Direction.NORTH, 4, 3, 4);
    var p =
        new net.minecraft.world.entity.player.Player(
            h.getLevel(),
            cell,
            0,
            new com.mojang.authlib.GameProfile(UUID.randomUUID(), "assembly-menu-test")) {
          public boolean isSpectator() {
            return false;
          }

          public boolean isCreative() {
            return false;
          }

          @Override
          public java.util.OptionalInt openMenu(
              net.minecraft.world.MenuProvider provider,
              java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> data) {
            containerMenu = provider.createMenu(1, getInventory(), this);
            return java.util.OptionalInt.of(1);
          }
        };
    p.setPos(cell.getX() + 4, cell.getY(), cell.getZ() + 4);
    h.assertTrue(
        p.distanceToSqr(be.getBlockPos().getCenter()) > 64,
        "fixture must be beyond controller menu range");
    h.assertTrue(
        MachineInteraction.open(be, p, cell) && p.containerMenu.stillValid(p), "part menu failed");
    p.setPos(cell.getX() + 30, cell.getY(), cell.getZ());
    h.assertTrue(
        !p.containerMenu.stillValid(p) && !MachineInteraction.open(be, p, cell),
        "remote player reached machine");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void filledRefineryDismantlesIntoOnePortableOwner(GameTestHelper h) {
    var be =
        (MachineBlockEntity)
            place(h, MachineProfile.REFINERY, h.absolutePos(new BlockPos(8, 3, 8)), Direction.WEST);
    h.assertTrue(be.assembly().mirrorPorts(), "mirror configuration failed");
    be.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.CRUDE.stack(1)), 731);
    be.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
    be.power.restore(1234);
    var shape = MachineProfile.REFINERY.cells(be.getBlockPos(), Direction.WEST);
    h.getLevel().destroyBlock(shape.getLast(), true);
    var drops =
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(be.getBlockPos()).inflate(8));
    h.assertTrue(
        drops.size() == 1 && drops.getFirst().getItem().is(be.getBlockState().getBlock().asItem()),
        "demolition did not return exactly one owner");
    var portable = drops.getFirst().getItem().copy();
    drops.getFirst().discard();
    for (var cell : shape)
      h.assertTrue(h.getLevel().getBlockState(cell).isAir(), "demolition left structure");
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setYRot(Direction.EAST.toYRot());
    player.setItemInHand(InteractionHand.MAIN_HAND, portable);
    h.assertTrue(
        PlacementTestSupport.place(player, be.getBlockPos()).consumesAction(),
        "filled owner could not be item-placed");
    var copy = (MachineBlockEntity) h.getLevel().getBlockEntity(be.getBlockPos());
    h.assertTrue(
        copy.petroleum().cells.get(0).amount() == 731
            && copy.items.getStackInSlot(0).getCount() == 3
            && copy.power.getEnergyStored() == 1234
            && copy.assembly().mirrored()
            && !copy.assembly().identity().equals(be.assembly().identity()),
        "portable machine lost resources or reused identity");
    copy.assembly().removeWhole(false);
    for (var cell : shape)
      h.assertTrue(h.getLevel().getBlockState(cell).isAir(), "demolition left structure");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void schemaFourExactResourcesAndIdentityPersistAcrossJvms(GameTestHelper h) {
    disk(h, new BlockPos(18015, 72, 18015), false);
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void mirroredSchemaFourConfigurationPersistsAcrossJvms(GameTestHelper h) {
    disk(h, new BlockPos(18111, 72, 18111), true);
  }

  private static void disk(GameTestHelper h, BlockPos pos, boolean mirror) {
    var level = h.getLevel();
    var profile = MachineProfile.REFINERY;
    var facing = Direction.EAST;
    var cells = profile.cells(pos, facing);
    for (var cell : cells) level.getChunkAt(cell);
    boolean empty = cells.stream().allMatch(p -> level.getBlockState(p).isAir());
    if (empty) {
      var be = (MachineBlockEntity) place(h, profile, pos, facing);
      if (mirror) h.assertTrue(be.assembly().mirrorPorts(), "disk mirror failed");
      be.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.CRUDE.stack(1)), 731);
      be.petroleum().cells.get(4).restore(new FluidKey(PetroleumFluids.HEAVY.stack(1)), 137);
      be.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
      be.power.restore(1234);
      var tag = be.saveWithFullMetadata(level.registryAccess());
      tag.getCompound("Petroleum").putInt("Progress", 7);
      tag.getCompound("Petroleum").putInt("Duration", 20);
      be.loadWithComponents(tag, level.registryAccess());
      System.out.println(
          "FACTORYCRAFT_ASSEMBLY_V4_DISK: SEEDED exact state, four-chunk structure; mirror="
              + mirror);
    } else {
      h.assertTrue(
          level.getBlockEntity(pos) instanceof MachineBlockEntity,
          "partial schema-4 fixture; refusing reseed");
      var be = (MachineBlockEntity) level.getBlockEntity(pos);
      h.assertTrue(
          be.petroleum().cells.get(0).amount() == 731
              && be.petroleum().cells.get(4).amount() == 137
              && be.items.getStackInSlot(0).getCount() == 3
              && be.power.getEnergyStored() == 1234
              && be.petroleum().data.get(0) == 7
              && be.assembly().mirrored() == mirror,
          "schema-4 disk values changed");
      for (var cell : cells)
        if (!cell.equals(pos))
          h.assertTrue(
              level.getBlockEntity(cell) instanceof CasingBlockEntity part
                  && part.boundTo(pos, be.assembly().identity()),
              "part identity not persisted");
      h.assertTrue(
          be.saveWithFullMetadata(level.registryAccess()).getCompound("Assembly").getInt("Schema")
              == 4,
          "wrong disk schema");
      System.out.println(
          "FACTORYCRAFT_ASSEMBLY_V4_DISK: VERIFIED exact resources, progress and owner identity;"
              + " mirror="
              + mirror);
    }
    for (var cell : cells) {
      level.getBlockEntity(cell).onChunkUnloaded();
      level.getChunkAt(cell).setUnsaved(true);
    }
    h.succeed();
  }
}
