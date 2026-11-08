package factorycraft.fluids;

import factorycraft.blockentity.*;
import factorycraft.simulation.fluids.FluidCell;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Loaded-only canonical lookups for the fixed machine nozzles; never calls a chunk-loading API. */
public final class FluidConnections {
    public static BlockEntity at(Level level,BlockPos pos) {
        if(!(level instanceof ServerLevel server))return null;
        var chunk=server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        return chunk==null ? null:chunk.getBlockEntities().get(pos);
    }
    public static boolean canonical(BlockEntity be) { return be.getLevel()!=null && !be.isRemoved() && at(be.getLevel(),be.getBlockPos())==be; }
    public static FluidCell<FluidKey> input(Level level,BlockPos pos,Direction side,FluidKey key) {
        var be=at(level,pos);
        if(be instanceof FluidBlockEntity fluid && fluid.simulationAvailable() && (fluid.kind()!=FluidKind.PUMP || side==fluid.facing().getOpposite()))return fluid.cell();
        if(be instanceof ThermalBlockEntity thermal)return thermal.externalInput(side,key);
        var machine=be instanceof MachineBlockEntity m ? m:be instanceof CasingBlockEntity part && canonical(part) ? part.owner():null;
        return machine!=null && machine.petroleum()!=null ? machine.petroleum().input(pos,side):null;
    }
    public static FluidCell<FluidKey> output(Level level,BlockPos pos,Direction side) {
        var be=at(level,pos);
        if(be instanceof FluidBlockEntity fluid && fluid.simulationAvailable() && (fluid.kind()!=FluidKind.PUMP || side==fluid.facing()))return fluid.cell();
        if(be instanceof ThermalBlockEntity thermal)return thermal.externalOutput(side);
        var machine=be instanceof MachineBlockEntity m ? m:be instanceof CasingBlockEntity part && canonical(part) ? part.owner():null;
        return machine!=null && machine.petroleum()!=null ? machine.petroleum().output(pos,side):null;
    }
    private FluidConnections() {}
}
