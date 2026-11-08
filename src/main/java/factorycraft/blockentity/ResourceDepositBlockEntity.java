package factorycraft.blockentity;

import factorycraft.blocks.ResourceDepositBlock;
import factorycraft.config.FactoryConfig;
import factorycraft.fluids.FluidConnections;
import factorycraft.registry.Content;
import factorycraft.simulation.resources.ResourceReserve;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.*;

public final class ResourceDepositBlockEntity extends FactoryBlockEntity {
    private static final IItemHandler EMPTY=new ItemStackHandler(0);
    private final ResourceReserve reserve=new ResourceReserve();
    private boolean initialized,loaded;
    private net.minecraft.world.level.chunk.LevelChunk ownedChunk;
    public ResourceDepositBlockEntity(BlockPos pos,BlockState state) { super(Content.RESOURCE_DEPOSIT_ENTITY.get(),pos,state); }
    public int remaining() { return reserve.remaining(); }
    public boolean available() { return loaded && FluidConnections.canonical(this); }
    public void initializeReserve(int units) { initializeStock(units);dirty(); }
    /** Called while the generation region exclusively owns this new BE; no ServerLevel callbacks. */
    public void initializeGeneratedReserve(int units) { if(loaded)throw new IllegalStateException("Already loaded");initializeStock(units); }
    private void initializeStock(int units) { if(initialized)throw new IllegalStateException("Already initialized");reserve.restore(units);initialized=true; }
    private void dirty() { if(level!=null && !level.isClientSide) { if(ownedChunk!=null)ownedChunk.setUnsaved(true);markPersistentState(); } }
    /** Caller already placed one result into its canonical, callback-free inventory. */
    public boolean extractAccepted() {
        if(!available() || !reserve.extractOne(true))return false;dirty();
        if(remaining()==0)level.setBlock(worldPosition,Blocks.AIR.defaultBlockState(),3);
        return true;
    }
    public ResourceDepositBlock resourceBlock() { return (ResourceDepositBlock)getBlockState().getBlock(); }
    public IItemHandler inventory() { return EMPTY; }
    public void onLoad() { super.onLoad();if(level.isClientSide)return;loaded=true;ownedChunk=((net.minecraft.server.level.ServerLevel)level).getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);if(!initialized)initializeReserve(FactoryConfig.ORE_RICHNESS.get()); }
    public void onChunkUnloaded() { loaded=false;ownedChunk=null;super.onChunkUnloaded(); }
    public void setRemoved() { loaded=false;ownedChunk=null;super.setRemoved(); }
    protected void saveAdditional(CompoundTag tag,HolderLookup.Provider r) { super.saveAdditional(tag,r);tag.putInt("Schema",1);tag.putBoolean("Initialized",initialized);tag.putInt("Remaining",remaining()); }
    protected void loadAdditional(CompoundTag tag,HolderLookup.Provider r) { super.loadAdditional(tag,r);initialized=tag.getBoolean("Initialized");reserve.restore(Math.max(0,tag.getInt("Remaining"))); }
}
