package factorycraft.diagnostics;

import factorycraft.belts.*;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.registry.Content;
import factorycraft.simulation.BeltSimulation;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;

/** Explicit operator visual bench, finite component-bearing cargo, no result/output seeding. */
public final class TransportShowcase {
  public static BeltBlockEntity belt(
      ServerLevel level, BlockPos p, FactoryKind type, Direction facing) {
    level.setBlockAndUpdate(
        p,
        Content.FACTORY.get(type).get().defaultBlockState().setValue(FactoryBlock.FACING, facing));
    var b = (BeltBlockEntity) level.getBlockEntity(p);
    if (!b.loaded()) b.onLoad();
    return b;
  }

  public static void build(ServerLevel level, BlockPos o) {
    var kinds =
        new FactoryKind[] {FactoryKind.BELT, FactoryKind.FAST_BELT, FactoryKind.EXPRESS_BELT};
    var lifts =
        new FactoryKind[] {FactoryKind.LIFT, FactoryKind.FAST_LIFT, FactoryKind.EXPRESS_LIFT};
    for (int tier = 0; tier < 3; tier++) {
      for (int x = 1; x < 9; x++)
        belt(level, o.offset(x, 0, 1 + tier * 3), kinds[tier], Direction.EAST);
      var in = belt(level, o.offset(10, 0, 1 + tier * 3), kinds[tier], Direction.EAST);
      belt(level, o.offset(11, 0, 1 + tier * 3), kinds[tier], Direction.SOUTH);
      var ramp = belt(level, o.offset(11, 0, 2 + tier * 3), kinds[tier], Direction.SOUTH);
      ramp.initializeRoute(new BeltRoute(BeltRoute.Shape.UP, 0, 0));
      TransportPlan.buildParts(ramp);
      belt(level, o.offset(11, 1, 3 + tier * 3), kinds[tier], Direction.SOUTH);
      for (var b : java.util.List.of(in, ramp)) BeltSimulation.topologyChanged(b);
    }
    for (int i = 0; i < 6; i++) {
      int height = i == 0 ? 4 : i == 1 ? 12 : i == 2 ? 16 : i == 3 ? -4 : i == 4 ? 6 : 10;
      int start = height < 0 ? 4 : 0;
      var p = o.offset(2 + i * 4, start, 18);
      var b = belt(level, p, lifts[i % 3], Direction.NORTH);
      b.initializeRoute(new BeltRoute(BeltRoute.Shape.LIFT, height, i % 4));
      TransportPlan.buildParts(b);
      BeltSimulation.topologyChanged(b);
      // Finite source on the real route, not fabricated destination output.
      for (int lane = 0; lane < 2; lane++)
        for (int j = 3; j >= 0; j--) {
          var s =
              new ItemStack(
                  lane == 0 ? Items.RAW_IRON : Items.RAW_COPPER, (i == 1 || i == 2) ? 4 : 1);
          var tag = new net.minecraft.nbt.CompoundTag();
          tag.putInt("fixture", 23);
          s.set(
              net.minecraft.core.component.DataComponents.CUSTOM_DATA,
              net.minecraft.world.item.component.CustomData.of(tag));
          b.lane(lane).restore(s, .10 + j * .18);
        }
      b.sync();
      b.wakeTransport();
      belt(level, p.relative(Direction.SOUTH), kinds[i % 3], Direction.NORTH);
      belt(level, b.outputPosition().relative(b.outputFacing()), kinds[i % 3], b.outputFacing());
    }
    // A finite real source -> burner -> splitter input, with a mirrored neighboring example.
    for (int i = 0; i < 2; i++) {
      var p = o.offset(19 + i * 5, 0, 4);
      factorycraft.core.FactoryCommands.machine(
          level, p, FactoryKind.BURNER_INSERTER, Direction.NORTH);
      var a = (InserterBlockEntity) level.getBlockEntity(p);
      a.setReflected(i == 1);
      a.fuel.setStackInSlot(0, new ItemStack(Items.COAL));
      level.setBlockAndUpdate(
          p.south(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
      ((net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(p.south()))
          .setItem(0, new ItemStack(Items.DIAMOND, 16));
      factorycraft.multiblock.StructurePlacement.place(
          level, p.north(), Content.FACTORY.get(FactoryKind.SPLITTER).get().defaultBlockState());
    }
  }

  private TransportShowcase() {}
}
