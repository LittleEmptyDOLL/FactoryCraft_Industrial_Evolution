package factorycraft.petroleum;

import factorycraft.blockentity.*;
import factorycraft.fluids.FluidConnections;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Never caches resources; a cached part capability revalidates part, owner and assembly every call. */
public final class CasingFluidPort implements IFluidHandler {
    private final CasingBlockEntity part;private final Direction side;
    private MachineBlockEntity cached;private IFluidHandler port;
    public CasingFluidPort(CasingBlockEntity part,Direction side) { this.part=part;this.side=side; }
    private IFluidHandler current() {
        if(!part.loaded() || !FluidConnections.canonical(part))return null;
        var owner=part.owner();if(owner==null || owner.petroleum()==null || !owner.petroleum().hasPort(part.getBlockPos(),side))return null;
        if(owner!=cached) { cached=owner;port=owner.petroleum().port(part.getBlockPos(),side); }return port;
    }
    public int getTanks() { var h=current();return h==null ? 0:h.getTanks(); }
    public FluidStack getFluidInTank(int i) { var h=current();return h==null ? FluidStack.EMPTY:h.getFluidInTank(i); }
    public int getTankCapacity(int i) { var h=current();return h==null ? 0:h.getTankCapacity(i); }
    public boolean isFluidValid(int i,FluidStack f) { var h=current();return h!=null && h.isFluidValid(i,f); }
    public int fill(FluidStack f,FluidAction a) { var h=current();return h==null ? 0:h.fill(f,a); }
    public FluidStack drain(FluidStack f,FluidAction a) { var h=current();return h==null ? FluidStack.EMPTY:h.drain(f,a); }
    public FluidStack drain(int n,FluidAction a) { var h=current();return h==null ? FluidStack.EMPTY:h.drain(n,a); }
}
