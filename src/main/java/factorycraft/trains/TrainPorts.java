package factorycraft.trains;

import factorycraft.fluids.FluidKey;
import factorycraft.simulation.fluids.*;
import factorycraft.simulation.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** UUID-bound, halt-only adapters. No neighboring or unloaded car may inherit an old handle. */
public final class TrainPorts {
  static boolean ready(StockEntity entity) {
    if (!(entity.level() instanceof ServerLevel l)
        || entity.isRemoved()
        || l.getEntity(entity.getUUID()) != entity) return false;
    var d = TrainData.get(l);
    var t = d.owner(entity.getUUID());
    var car = d.car(entity.getUUID());
    return t != null
        && car != null
        && car.recovery != null
        && entity.position().distanceToSqr(TrainWorld.vec(car.recovery)) < .0001
        && d.stopped(t);
  }

  public static IItemHandler inventory(StockEntity entity) {
    return new IItemHandler() {
      TrainCar car() {
        return ready(entity)
            ? TrainData.get((ServerLevel) entity.level()).car(entity.getUUID())
            : null;
      }

      public int getSlots() {
        var c = car();
        return c == null ? 0 : c.items.getSlots();
      }

      public ItemStack getStackInSlot(int s) {
        var c = car();
        return c == null || s < 0 || s >= c.items.getSlots()
            ? ItemStack.EMPTY
            : c.items.getStackInSlot(s);
      }

      public ItemStack insertItem(int s, ItemStack stack, boolean simulate) {
        var c = car();
        return c == null || s < 0 || s >= c.items.getSlots()
            ? stack
            : c.items.insertItem(s, stack, simulate);
      }

      public ItemStack extractItem(int s, int n, boolean simulate) {
        var c = car();
        return c == null || s < 0 || s >= c.items.getSlots()
            ? ItemStack.EMPTY
            : c.items.extractItem(s, n, simulate);
      }

      public int getSlotLimit(int s) {
        var c = car();
        return c == null || s < 0 || s >= c.items.getSlots() ? 0 : c.items.getSlotLimit(s);
      }

      public boolean isItemValid(int s, ItemStack stack) {
        var c = car();
        return c != null && s >= 0 && s < c.items.getSlots() && c.items.isItemValid(s, stack);
      }
    };
  }

  public static IItemHandler items(
      net.minecraft.world.level.Level level, BlockPos pos, Direction side) {
    var block = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
    if (block != null) return block;
    var stock = at(level, pos, side, false);
    return stock == null ? null : inventory(stock);
  }

  public static StockEntity at(
      net.minecraft.world.level.Level level, BlockPos pos, Direction side, boolean fluid) {
    if (!(level instanceof ServerLevel l) || !l.hasChunkAt(pos)) return null;
    for (var e :
        l.getEntitiesOfClass(
            StockEntity.class,
            new AABB(pos).inflate(3),
            s -> ready(s) && (!fluid || s.kind() == StockSpec.FLUID))) {
      var point = pos.getCenter().subtract(e.position());
      double a = Math.toRadians(e.getYRot());
      double forward = -Math.sin(a) * point.x + Math.cos(a) * point.z,
          across = Math.cos(a) * point.x + Math.sin(a) * point.z;
      if (point.y < 0
          || point.y > 2.2
          || Math.abs(forward) > 2.65
          || Math.abs(across) > .0 + 1.7
          || Math.abs(across) < .55) continue;
      if (side != null
          && (side.getAxis().isVertical()
              || Math.abs(side.getStepX() * Math.cos(a) + side.getStepZ() * Math.sin(a)) < .7))
        continue;
      if (fluid && Math.min(Math.abs(forward), Math.abs(Math.abs(forward) - 2)) > .65) continue;
      return e;
    }
    return null;
  }

  public static FluidCell<FluidKey> cell(StockEntity entity) {
    var d = TrainData.get((ServerLevel) entity.level());
    var car = d.car(entity.getUUID());
    if (car == null || car.kind != StockSpec.FLUID) return null;
    if (car.guardedFluid == null || car.fluidProjection != entity) {
      car.fluidProjection = entity;
      car.guardedFluid =
          new BoundaryFluidCell<>(
              () -> car.fluid,
              () -> ready(entity) && d.car(entity.getUUID()) == car,
              () -> entity.level().getGameTime(),
              Integer.MAX_VALUE,
              () -> true,
              () -> true,
              () -> {});
    }
    return car.guardedFluid;
  }

  public static FluidCell<FluidKey> fluid(
      net.minecraft.world.level.Level level, BlockPos pos, Direction side) {
    var e = at(level, pos, side, true);
    return e == null ? null : cell(e);
  }

  public static IFluidHandler fluids(StockEntity entity, Direction side) {
    return new IFluidHandler() {
      FluidCell<FluidKey> live() {
        double yaw = Math.toRadians(entity.getYRot());
        boolean sided =
            side == null
                || side.getAxis().isHorizontal()
                    && Math.abs(side.getStepX() * Math.cos(yaw) + side.getStepZ() * Math.sin(yaw))
                        > .7;
        return sided && ready(entity) && entity.kind() == StockSpec.FLUID ? cell(entity) : null;
      }

      public int getTanks() {
        return 1;
      }

      public FluidStack getFluidInTank(int tank) {
        var c = live();
        return tank == 0 && c != null && c.amount() > 0
            ? c.key().stack(c.amount())
            : FluidStack.EMPTY;
      }

      public int getTankCapacity(int tank) {
        return tank == 0 && live() != null ? StockSpec.FLUID_CAPACITY : 0;
      }

      public boolean isFluidValid(int tank, FluidStack stack) {
        var c = live();
        return tank == 0 && c != null && !stack.isEmpty() && c.accepts(new FluidKey(stack));
      }

      public int fill(FluidStack offered, FluidAction action) {
        var c = live();
        return c == null || offered.isEmpty()
            ? 0
            : c.fill(new FluidKey(offered), offered.getAmount(), action.simulate());
      }

      public FluidStack drain(FluidStack stack, FluidAction action) {
        var c = live();
        if (c == null || stack.isEmpty() || c.amount() == 0 || !c.key().equals(new FluidKey(stack)))
          return FluidStack.EMPTY;
        return drain(stack.getAmount(), action);
      }

      public FluidStack drain(int amount, FluidAction action) {
        var c = live();
        if (c == null || c.amount() == 0) return FluidStack.EMPTY;
        var key = c.key();
        return key.stack(c.drain(amount, action.simulate()));
      }
    };
  }

  public static void wakePumps(TrainData data, TrainData.Train train) {
    // Halt transition/explicit loading changes only: bounded physical neighborhoods, no world scan.
    for (var car : train.cars)
      if (car.kind == StockSpec.FLUID && car.recovery != null) {
        var center = BlockPos.containing(TrainWorld.vec(car.recovery));
        for (var p : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 2, 4))) {
          var pump = factorycraft.fluids.FluidConnections.fluidDevice(data.level, p);
          if (pump != null && pump.kind() == factorycraft.fluids.FluidKind.PUMP)
            factorycraft.fluids.FluidSimulation.activatePump(pump);
        }
      }
  }

  private TrainPorts() {}
}
