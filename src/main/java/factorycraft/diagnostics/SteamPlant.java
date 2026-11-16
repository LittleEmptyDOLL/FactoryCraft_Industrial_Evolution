package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.thermal.ThermalKind;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Explicit diagnostic plant: world-water shore + Offshore + fueled boiler, no injected FE/steam.
 */
public final class SteamPlant {
  public static ThermalBlockEntity build(ServerLevel level, BlockPos formerGenerator, int coal) {
    if (level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)
      return factorycraft.gametest.FinitePowerFixture.place(
          level, formerGenerator.east().above(4), Direction.NORTH);
    return buildReal(level, formerGenerator, coal);
  }

  public static ThermalBlockEntity buildReal(
      ServerLevel level, BlockPos formerGenerator, int coal) {
    var engine = formerGenerator.east().above(4).south(2);
    var boiler = engine.south(5);
    var offshore = engine.offset(-6, 0, 6);
    var plan = new LinkedHashMap<BlockPos, BlockState>();
    var second = engine.north(6);
    add(plan, second, Content.THERMAL_BLOCKS.get(ThermalKind.ENGINE).get().defaultBlockState());
    plan.put(engine.north(3), Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    add(plan, engine, Content.THERMAL_BLOCKS.get(ThermalKind.ENGINE).get().defaultBlockState());
    add(plan, boiler, Content.THERMAL_BLOCKS.get(ThermalKind.BOILER).get().defaultBlockState());
    add(
        plan,
        offshore,
        Content.FLUID_BLOCKS
            .get(FluidKind.OFFSHORE)
            .get()
            .defaultBlockState()
            .setValue(FluidDeviceBlock.FACING, Direction.EAST));
    for (int z = 3; z <= 4; z++)
      plan.put(engine.south(z), Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    for (int x = -5; x <= -2; x++)
      plan.put(
          engine.offset(x, 0, 6),
          Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    // Two-wide shoreline, with a sealed basin and real solid support; water blocks remain intact.
    for (int z = 5; z <= 6; z++) {
      plan.put(engine.offset(-6, -1, z), Blocks.STONE.defaultBlockState());
      plan.put(engine.offset(-7, -1, z), Blocks.WATER.defaultBlockState());
      plan.put(engine.offset(-7, -2, z), Blocks.STONE.defaultBlockState());
      plan.put(engine.offset(-8, -1, z), Blocks.STONE.defaultBlockState());
    }
    plan.put(engine.offset(-7, -1, 4), Blocks.STONE.defaultBlockState());
    plan.put(engine.offset(-7, -1, 7), Blocks.STONE.defaultBlockState());
    for (var p : plan.keySet())
      if (!level.hasChunkAt(p)
          || !level.getBlockState(p).isAir()
          || level.getBlockEntity(p) != null)
        throw new IllegalStateException("Occupied/unloaded steam diagnostic: " + p);
    for (var e : plan.entrySet())
      if (MachineProfile.of(e.getValue()) == null
          && !e.getValue().is(Content.FACTORY.get(FactoryKind.CASING).get()))
        level.setBlock(e.getKey(), e.getValue(), 2);
    for (var pos : List.of(engine, second, boiler, offshore))
      StructurePlacement.place(level, pos, plan.get(pos));
    ((ThermalBlockEntity) level.getBlockEntity(boiler))
        .items.setStackInSlot(0, new ItemStack(Items.COAL, coal));
    return (ThermalBlockEntity) level.getBlockEntity(engine);
  }

  private static void add(Map<BlockPos, BlockState> plan, BlockPos pos, BlockState state) {
    var profile = MachineProfile.of(state);
    for (var p : profile.cells(pos, profile.facing(state)))
      plan.put(
          p,
          p.equals(pos)
              ? state
              : Content.FACTORY.get(FactoryKind.CASING).get().defaultBlockState());
  }

  private SteamPlant() {}
}
