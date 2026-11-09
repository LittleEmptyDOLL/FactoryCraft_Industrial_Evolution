package factorycraft.blockentity;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public abstract class FactoryBlockEntity extends BlockEntity {
    protected FactoryBlockEntity(BlockEntityType<?> t, BlockPos p, BlockState s) { super(t, p, s); }
    public void sync() {
        setChanged();
        sendUpdate();
    }
    /** Internal motion/progress/FE changes persist without inventory comparator notifications. */
    protected final void markPersistentState() {
        if(level==null)return;
        if(getBlockState().hasAnalogOutputSignal())setChanged();
        else level.blockEntityChanged(worldPosition);
    }
    protected final void sendUpdate() {
        factorycraft.diagnostics.FactoryTelemetry.synced(this);
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) { return saveWithoutMetadata(r); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
        var owner=factorycraft.multiblock.AssemblyAccess.owner(this);
        if(owner==null)return net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
        var profile=owner.assembly().profile();var at=profile.controllerLocal();
        return net.neoforged.neoforge.client.model.data.ModelData.builder().with(factorycraft.multiblock.MachineModelData.CELL,new factorycraft.multiblock.MachineModelData.Cell(profile,at.getX(),at.getY(),at.getZ(),owner.assembly().mirrored())).build();
    }
    private boolean mirroredModelState;
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        boolean mirror=tag.getCompound("Assembly").getBoolean("MirroredPorts");
        if(level!=null&&level.isClientSide && mirror!=mirroredModelState) {
            mirroredModelState=mirror;requestModelDataUpdate();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        }
    }
    public abstract IItemHandler inventory();
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        var items = inventory();
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.extractItem(i, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, stack);
        }
    }
}
