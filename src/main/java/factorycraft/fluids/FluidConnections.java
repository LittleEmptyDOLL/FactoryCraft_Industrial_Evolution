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
    public static FactoryBlockEntity endpoint(Level level,BlockPos pos) {
        var be=at(level,pos);if(be instanceof CasingBlockEntity part) { var owner=part.genericForRemoval();return owner==null ? null:owner.factoryEntity(); }
        return be instanceof FactoryBlockEntity factory ? factory:null;
    }
    public static FluidBlockEntity fluidDevice(Level level,BlockPos pos) { var be=endpoint(level,pos);return be instanceof FluidBlockEntity fluid ? fluid:null; }
    public static FluidCell<FluidKey> input(Level level,BlockPos pos,Direction side,FluidKey key) {
        var be=endpoint(level,pos);
        if(be instanceof FluidBlockEntity f&&f.kind()==FluidKind.PIPE)return f.simulationAvailable()?f.cell():null;
        return be==null?null:factorycraft.multiblock.MachineFluidAccess.input(be,pos,side,key);
    }
    public static FluidCell<FluidKey> output(Level level,BlockPos pos,Direction side) {
        var be=endpoint(level,pos);
        if(be instanceof FluidBlockEntity f&&f.kind()==FluidKind.PIPE)return f.simulationAvailable()?f.cell():null;
        return be==null?null:factorycraft.multiblock.MachineFluidAccess.output(be,pos,side);
    }
    private FluidConnections() {}
}
