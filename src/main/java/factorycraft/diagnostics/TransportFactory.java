package factorycraft.diagnostics;

import factorycraft.belts.*;
import factorycraft.blockentity.BeltBlockEntity;
import factorycraft.blocks.*;
import factorycraft.core.FactoryCommands;
import factorycraft.registry.Content;
import factorycraft.simulation.BeltSimulation;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Finite chest-to-chest 3D route, not a second transport implementation. */
public final class TransportFactory {
  private static BeltBlockEntity belt(
      ServerLevel level, BlockPos p, FactoryKind kind, Direction facing) {
    level.setBlockAndUpdate(
        p,
        Content.FACTORY.get(kind).get().defaultBlockState().setValue(FactoryBlock.FACING, facing));
    var b = (BeltBlockEntity) level.getBlockEntity(p);
    if (!b.loaded()) b.onLoad();
    return b;
  }

  public static ChestBlockEntity build(ServerLevel level, BlockPos o) {
    return build(level, o, false, ItemStack.EMPTY);
  }

  public static ChestBlockEntity buildStacked(ServerLevel level, BlockPos o, ItemStack cargo) {
    return build(level, o, true, cargo);
  }

  private static ChestBlockEntity build(
      ServerLevel level, BlockPos o, boolean stacked, ItemStack cargo) {
    level.setBlockAndUpdate(o.offset(1, 0, 0), Blocks.CHEST.defaultBlockState());
    var source = (ChestBlockEntity) level.getBlockEntity(o.offset(1, 0, 0));
    if (stacked) {
      source.setItem(0, cargo.copy());
      FactoryCommands.machine(level, o.offset(2, 0, 0), FactoryKind.STACK_INSERTER, Direction.EAST);
    } else {
      source.setItem(0, new ItemStack(Items.RAW_IRON, 32));
      source.setItem(1, new ItemStack(Items.RAW_COPPER, 32));
      fueledArm(level, o.offset(2, 0, 0), Direction.EAST);
    }
    belt(level, o.offset(3, 0, 0), FactoryKind.BELT, Direction.EAST);
    belt(level, o.offset(4, 0, 0), FactoryKind.BELT, Direction.SOUTH);
    var ramp = belt(level, o.offset(4, 0, 1), FactoryKind.FAST_BELT, Direction.SOUTH);
    belt(level, o.offset(4, 1, 2), FactoryKind.FAST_BELT, Direction.SOUTH);
    belt(level, o.offset(4, 1, 3), FactoryKind.EXPRESS_BELT, Direction.EAST);
    belt(level, o.offset(5, 1, 3), FactoryKind.EXPRESS_BELT, Direction.EAST);
    var entry = belt(level, o.offset(6, 1, 3), FactoryKind.UNDERGROUND, Direction.EAST);
    for (int x = 7; x <= 10; x++)
      level.setBlockAndUpdate(o.offset(x, 1, 3), Blocks.STONE_BRICKS.defaultBlockState());
    var exit = belt(level, o.offset(11, 1, 3), FactoryKind.UNDERGROUND, Direction.EAST);
    exit.toggleUnderground();
    belt(level, o.offset(12, 1, 3), FactoryKind.BELT, Direction.EAST);
    var lift = belt(level, o.offset(13, 1, 3), FactoryKind.LIFT, Direction.EAST);
    lift.initializeRoute(new BeltRoute(BeltRoute.Shape.LIFT, 4, 1));
    TransportPlan.buildParts(lift);
    belt(level, o.offset(13, 5, 4), FactoryKind.BELT, Direction.SOUTH);
    fueledArm(level, o.offset(13, 5, 5), Direction.SOUTH);
    level.setBlockAndUpdate(o.offset(13, 5, 6), Blocks.CHEST.defaultBlockState());
    for (var b : java.util.List.of(ramp, entry, exit, lift)) BeltSimulation.topologyChanged(b);
    return (ChestBlockEntity) level.getBlockEntity(o.offset(13, 5, 6));
  }

  private static void fueledArm(ServerLevel level, BlockPos position, Direction facing) {
    FactoryCommands.machine(level, position, FactoryKind.BURNER_INSERTER, facing);
    ((factorycraft.blockentity.InserterBlockEntity) level.getBlockEntity(position))
        .fuel.setStackInSlot(0, new ItemStack(Items.COAL));
  }

  private TransportFactory() {}
}
