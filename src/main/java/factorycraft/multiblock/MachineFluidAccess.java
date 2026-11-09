package factorycraft.multiblock;

import factorycraft.blockentity.*;
import factorycraft.fluids.*;
import factorycraft.simulation.fluids.FluidCell;
import factorycraft.thermal.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** A nonowning physical-side view. Input/output channels may differ at a BOTH port. */
public final class MachineFluidAccess {
  public static MachinePorts.Port definition(FactoryBlockEntity be, BlockPos pos, Direction side) {
    var profile = MachineProfile.of(be.getBlockState());
    return profile == null ? null : MachinePorts.at(be, pos, side);
  }

  public static boolean ready(FactoryBlockEntity be, BlockPos physical) {
    var owner = AssemblyAccess.owner(be);
    if (owner == null
        || !owner.loaded()
        || !owner.assembly().ready()
        || !FluidConnections.canonical(be)) return false;
    var cell = FluidConnections.at(be.getLevel(), physical);
    return cell == be
        || cell instanceof CasingBlockEntity p
            && p.loaded()
            && p.boundTo(be.getBlockPos(), owner.assembly().identity());
  }

  public static FluidCell<FluidKey> cell(FactoryBlockEntity be, int channel) {
    if (channel < 0) return null;
    if (be instanceof MachineBlockEntity m && m.petroleum() != null)
      return channel < m.petroleum().cells.size() ? m.petroleum().cells.get(channel) : null;
    if (be instanceof ThermalBlockEntity t)
      return channel == 0 ? t.water : channel == 1 ? t.steam : null;
    return be instanceof FluidBlockEntity f && channel == 0 ? f.cell() : null;
  }

  public static boolean accepts(FactoryBlockEntity be, int channel, FluidKey key) {
    if (key == null) return false;
    if (be instanceof ThermalBlockEntity t) {
      if (channel == 1) return key.stack(1).getFluidType() == SteamFluids.TYPE.get();
      return t.kind() == ThermalKind.BOILER
          && channel == 0
          && key.stack(1).getFluidType() == Fluids.WATER.getFluidType();
    }
    return true;
  }

  /** Same-cell BOTH ports equalize rather than immediately returning each newly received batch. */
  public static int transferLimit(
      MachinePorts.Port port, FluidCell<FluidKey> from, FluidCell<FluidKey> to, int limit) {
    if (port.input() < 0 || port.input() != port.output()) return limit;
    long a = Math.max(1, from.capacity()), b = Math.max(1, to.capacity());
    long difference = (long) from.amount() * b - (long) to.amount() * a;
    return (int) Math.max(0, Math.min(limit, difference / (a + b)));
  }

  public static FluidCell<FluidKey> input(
      FactoryBlockEntity be, BlockPos pos, Direction side, FluidKey fluid) {
    var p = definition(be, pos, side);
    return p != null && p.input() >= 0 && ready(be, pos) && accepts(be, p.input(), fluid)
        ? cell(be, p.input())
        : null;
  }

  public static FluidCell<FluidKey> output(FactoryBlockEntity be, BlockPos pos, Direction side) {
    var p = definition(be, pos, side);
    return p != null && p.output() >= 0 && ready(be, pos) ? cell(be, p.output()) : null;
  }

  public static IFluidHandler port(FactoryBlockEntity be, BlockPos pos, Direction side) {
    var p = definition(be, pos, side);
    if (p == null || !ready(be, pos)) return null;
    return view(be, pos, () -> definition(be, pos, side));
  }

  /** Also used by native tests to exercise coincident ports with distinct owned channels. */
  public static IFluidHandler view(
      FactoryBlockEntity be, BlockPos pos, java.util.function.Supplier<MachinePorts.Port> spec) {
    var owner = Objects.requireNonNull(AssemblyAccess.owner(be));
    var identity = owner.assembly().identity();
    var physical = pos.immutable();
    return new IFluidHandler() {
      private MachinePorts.Port live() {
        return owner.assembly().owns(identity) && ready(be, physical) ? spec.get() : null;
      }

      private int tanks(MachinePorts.Port p) {
        return p.input() >= 0 && p.output() >= 0 && p.input() != p.output() ? 2 : 1;
      }

      private int channel(MachinePorts.Port p, int i) {
        Objects.checkIndex(i, tanks(p));
        return i == 0 ? (p.input() >= 0 ? p.input() : p.output()) : p.output();
      }

      public int getTanks() {
        var p = live();
        return p == null ? 0 : tanks(p);
      }

      public FluidStack getFluidInTank(int i) {
        var p = live();
        if (p == null) return FluidStack.EMPTY;
        var c = cell(be, channel(p, i));
        return c == null || c.amount() == 0 ? FluidStack.EMPTY : c.key().stack(c.amount());
      }

      public int getTankCapacity(int i) {
        var p = live();
        if (p == null) return 0;
        var c = cell(be, channel(p, i));
        return c == null ? 0 : Math.max(c.capacity(), c.amount());
      }

      public boolean isFluidValid(int i, FluidStack stack) {
        var p = live();
        if (p == null || stack.isEmpty()) return false;
        int channel = channel(p, i);
        return channel == p.input() && accepts(be, channel, new FluidKey(stack));
      }

      public int fill(FluidStack stack, FluidAction action) {
        var p = live();
        if (p == null || p.input() < 0 || stack.isEmpty()) return 0;
        var key = new FluidKey(stack);
        var c = cell(be, p.input());
        return c == null || !accepts(be, p.input(), key)
            ? 0
            : c.fill(key, stack.getAmount(), action.simulate());
      }

      public FluidStack drain(FluidStack stack, FluidAction action) {
        return stack.isEmpty()
            ? FluidStack.EMPTY
            : drainMatching(stack.getAmount(), new FluidKey(stack), action);
      }

      public FluidStack drain(int amount, FluidAction action) {
        return drainMatching(amount, null, action);
      }

      private FluidStack drainMatching(int amount, FluidKey key, FluidAction action) {
        var p = live();
        if (p == null || p.output() < 0 || amount <= 0) return FluidStack.EMPTY;
        var c = cell(be, p.output());
        if (c == null || c.amount() == 0 || key != null && !key.equals(c.key()))
          return FluidStack.EMPTY;
        var owned = c.key();
        return owned.stack(c.drain(amount, action.simulate()));
      }
    };
  }

  private MachineFluidAccess() {}
}
