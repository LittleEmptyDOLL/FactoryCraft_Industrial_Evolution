package factorycraft.multiblock;

import factorycraft.blockentity.*;
import factorycraft.fluids.FluidConnections;
import net.minecraft.core.*;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;

/** Shared ownership adapter keeps resources in their existing canonical production kernels. */
public final class AssemblyAccess {
    private static final IItemHandler EMPTY=new ItemStackHandler(0);
    public static AssemblyOwner owner(net.minecraft.world.level.block.entity.BlockEntity be) { return be instanceof AssemblyOwner o && o.assembly()!=null ? o:null; }
    public static boolean ready(FactoryBlockEntity be) { var owner=owner(be);return owner==null || owner.assembly().ready(); }
    public static IItemHandler items(AssemblyOwner owner,Direction side) {
        var be=owner.factoryEntity();
        if(be instanceof MachineBlockEntity m)return m.automation(side);
        if(be instanceof ThermalBlockEntity thermal)return thermal.automation();
        if(be instanceof BeltBlockEntity belt)return belt.inventory();return EMPTY;
    }
    public static IEnergyStorage energy(AssemblyOwner owner) {
        var be=owner.factoryEntity();return be instanceof MachineBlockEntity m ? m.energyPort:be instanceof ThermalBlockEntity t ? t.energyPort:be instanceof FluidBlockEntity f ? f.energyPort:null;
    }
    public static IFluidHandler fluid(AssemblyOwner owner,BlockPos physical,Direction side) {
        var be=owner.factoryEntity();
        if(be instanceof MachineBlockEntity m && m.petroleum()!=null)return m.petroleum().hasPort(physical,side) ? m.petroleum().port(physical,side):null;
        if(be instanceof ThermalBlockEntity t)return t.structurePort(physical,side);
        if(be instanceof FluidBlockEntity f)return f.structurePort(physical,side);return null;
    }
    public static void changed(AssemblyOwner owner) {
        var be=owner.factoryEntity();
        if(be instanceof BeltBlockEntity belt)factorycraft.simulation.BeltSimulation.topologyChanged(belt);
        if(be instanceof FluidBlockEntity fluid)factorycraft.fluids.FluidSimulation.topologyChanged(fluid);
    }
    public static void open(AssemblyOwner owner,Player player) { if(owner.factoryEntity() instanceof MenuProvider menu)player.openMenu(menu,owner.factoryEntity().getBlockPos()); }
    private AssemblyAccess() {}
}
