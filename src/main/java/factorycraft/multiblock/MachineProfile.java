package factorycraft.multiblock;

import factorycraft.blocks.FactoryBlock;
import factorycraft.simulation.multiblock.CuboidLayout;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

/** Factorio X/Z tile footprints; Minecraft heights are explicit 3D design choices. */
public enum MachineProfile implements StringRepresentable {
  DRILL("electric_drill", 3, 2, 3),
  FURNACE("electric_furnace", 3, 3, 3),
  ASSEMBLER("assembling_machine", 3, 3, 3),
  GENERATOR("coal_generator", 3, 3, 3),
  INDUSTRIAL("industrial_furnace", 3, 3, 3),
  PUMPJACK("pumpjack", 3, 4, 3),
  REFINERY("oil_refinery", 5, 4, 5),
  CHEMICAL("chemical_plant", 3, 3, 3),
  BOILER("boiler", 3, 2, 2),
  ENGINE("steam_engine", 3, 2, 5),
  TANK("fluid_tank", 3, 3, 3),
  PUMP("electric_pump", 1, 1, 2),
  SPLITTER("splitter", 2, 1, 1),
  LAB("lab", 3, 3, 3),
  ADVANCED_ASSEMBLER("assembling_machine_2", 3, 3, 3),
  BEACON("beacon", 3, 3, 3),
  FAST_SPLITTER("fast_splitter", 2, 1, 1),
  EXPRESS_SPLITTER("express_splitter", 2, 1, 1),
  CONSTANT("constant_combinator", 1, 1, 1),
  ARITHMETIC("arithmetic_combinator", 1, 1, 2),
  DECIDER("decider_combinator", 1, 1, 2),
  CIRCUIT_READER("circuit_reader", 1, 1, 1),
  CIRCUIT_CONTROLLER("circuit_controller", 1, 1, 1);
  public final String id;
  public final CuboidLayout layout;

  MachineProfile(String id, int width, int height, int depth) {
    this.id = id;
    layout = new CuboidLayout(width, height, depth);
  }

  public String getSerializedName() {
    return id;
  }

  public static MachineProfile byId(String id) {
    for (var p : values()) if (p.id.equals(id)) return p;
    return null;
  }

  public static MachineProfile of(BlockState state) {
    var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
    return key.getNamespace().equals("factorycraft") ? byId(key.getPath()) : null;
  }

  public List<BlockPos> parts(BlockPos anchor, Direction facing) {
    return layout.parts(facing.getStepX(), facing.getStepZ()).stream()
        .map(o -> anchor.offset(o.x(), o.y(), o.z()).immutable())
        .toList();
  }

  public List<BlockPos> cells(BlockPos anchor, Direction facing) {
    var list = new ArrayList<BlockPos>();
    list.add(anchor.immutable());
    list.addAll(parts(anchor, facing));
    return List.copyOf(list);
  }

  public BlockPos offset(BlockPos anchor, Direction facing, int right, int up, int back) {
    return anchor
        .relative(facing.getClockWise(), right)
        .above(up)
        .relative(facing.getOpposite(), back)
        .immutable();
  }

  public BlockPos center(BlockPos anchor, Direction facing) {
    return offset(anchor, facing, layout.width() / 2, 0, layout.depth() / 2);
  }

  public BlockPos anchorAtCenter(BlockPos center, Direction facing) {
    return center
        .relative(facing.getCounterClockWise(), layout.width() / 2)
        .relative(facing, layout.depth() / 2)
        .immutable();
  }

  public boolean circuit() {
    return this == CONSTANT
        || this == ARITHMETIC
        || this == DECIDER
        || this == CIRCUIT_READER
        || this == CIRCUIT_CONTROLLER;
  }

  public boolean splitter() {
    return this == SPLITTER || this == FAST_SPLITTER || this == EXPRESS_SPLITTER;
  }

  public Direction placementFacing(Direction look) {
    return splitter() || circuit() ? look : look.getOpposite();
  }

  public Direction facing(BlockState state) {
    return state.getValue(FactoryBlock.FACING);
  }
}
