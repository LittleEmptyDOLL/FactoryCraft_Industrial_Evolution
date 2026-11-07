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
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) { return saveWithoutMetadata(r); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
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
