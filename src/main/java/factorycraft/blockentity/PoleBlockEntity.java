package factorycraft.blockentity;

import factorycraft.energy.PowerGrid;
import factorycraft.registry.Content;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.*;

public final class PoleBlockEntity extends FactoryBlockEntity {
    private static final IItemHandler EMPTY = new ItemStackHandler(0);
    public PoleBlockEntity(BlockPos p, BlockState s) { super(Content.POLE.get(),p,s); }
    @Override public IItemHandler inventory() { return EMPTY; }
    @Override public void onLoad() { super.onLoad(); if(!level.isClientSide)PowerGrid.add(this); }
    @Override public void setRemoved() { if(level!=null && !level.isClientSide)PowerGrid.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { if(level!=null && !level.isClientSide)PowerGrid.remove(this); super.onChunkUnloaded(); }
}
