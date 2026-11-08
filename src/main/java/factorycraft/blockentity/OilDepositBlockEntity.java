package factorycraft.blockentity;

import factorycraft.blocks.OilDepositBlock;
import factorycraft.config.FactoryConfig;
import factorycraft.fluids.*;
import factorycraft.petroleum.PetroleumFluids;
import factorycraft.registry.Content;
import factorycraft.simulation.fluids.FluidCell;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.*;

public final class OilDepositBlockEntity extends FactoryBlockEntity {
    private static final IItemHandler EMPTY=new ItemStackHandler(0);
    private boolean initialized,loaded;
    private net.minecraft.world.level.chunk.LevelChunk ownedChunk;
    private final FluidCell<FluidKey> reserve=new FluidCell<>(Integer.MAX_VALUE,this::changed);
    public OilDepositBlockEntity(BlockPos pos,BlockState state) { super(Content.OIL_DEPOSIT_ENTITY.get(),pos,state); }
    public int remaining() { return reserve.amount(); }
    public FluidCell<FluidKey> reserve() { return reserve; }
    public boolean available() { return loaded && FluidConnections.canonical(this); }
    private void changed() {
        if(level==null || level.isClientSide || !loaded)return;
        if(ownedChunk!=null)ownedChunk.setUnsaved(true);markPersistentState();
        boolean depleted=remaining()==0;
        if(getBlockState().getValue(OilDepositBlock.DEPLETED)!=depleted)level.setBlock(worldPosition,getBlockState().setValue(OilDepositBlock.DEPLETED,depleted),3);
    }
    public void initializeReserve(int amount) {
        if(initialized || amount<0)throw new IllegalStateException("Oil reserve already initialized/invalid");
        reserve.restore(new FluidKey(PetroleumFluids.CRUDE.stack(1)),amount);initialized=true;changed();
    }
    @Override public IItemHandler inventory() { return EMPTY; }
    @Override public void onLoad() {
        super.onLoad();if(level.isClientSide)return;loaded=true;
        var server=(net.minecraft.server.level.ServerLevel)level;ownedChunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
        if(!initialized)initializeReserve(FactoryConfig.OIL_RICHNESS.get());changed();
    }
    @Override public void onChunkUnloaded() { loaded=false;ownedChunk=null;super.onChunkUnloaded(); }
    @Override public void setRemoved() { loaded=false;ownedChunk=null;super.setRemoved(); }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider r) { super.saveAdditional(tag,r);tag.putInt("Schema",1);tag.putBoolean("Initialized",initialized);tag.putInt("Remaining",remaining()); }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider r) {
        super.loadAdditional(tag,r);initialized=tag.getBoolean("Initialized");int amount=Math.max(0,tag.getInt("Remaining"));reserve.restore(amount==0 ? null:new FluidKey(PetroleumFluids.CRUDE.stack(1)),amount);
    }
}
