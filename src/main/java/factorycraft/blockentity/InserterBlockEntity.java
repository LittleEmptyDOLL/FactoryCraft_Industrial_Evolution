package factorycraft.blockentity;

import factorycraft.blocks.FactoryBlock;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.*;

/** Mechanical inserter: no energy requirement, one item per 24 ticks, persistent ownership in hand. */
public final class InserterBlockEntity extends FactoryBlockEntity {
    public final ItemStackHandler hand = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { sync(); }
    };
    private int phase;
    private long phaseTick;
    private final IItemHandler exposed = new IItemHandler() {
        public int getSlots() { return 1; }
        public ItemStack getStackInSlot(int s) { return hand.getStackInSlot(0).copy(); }
        public ItemStack insertItem(int s, ItemStack stack, boolean sim) { return stack; }
        public ItemStack extractItem(int s, int n, boolean sim) { return ItemStack.EMPTY; }
        public int getSlotLimit(int s) { return 1; }
        public boolean isItemValid(int s, ItemStack stack) { return false; }
    };
    public InserterBlockEntity(BlockPos p, BlockState s) { super(Content.INSERTER.get(), p, s); }
    @Override public IItemHandler inventory() { return exposed; }
    public ItemStack held() { return hand.getStackInSlot(0); }
    public double visualPhase(float partial) { return Math.max(0,phase-(level.getGameTime()+partial-phaseTick))/12.0; }
    public void tick() {
        Direction facing = getBlockState().getValue(FactoryBlock.FACING);
        BlockPos sourcePos = worldPosition.relative(facing.getOpposite());
        BlockPos targetPos = worldPosition.relative(facing);
        if (!level.hasChunkAt(sourcePos) || !level.hasChunkAt(targetPos)) return;
        var target = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, facing.getOpposite());
        if (held().isEmpty()) {
            if (phase > 0) { phase--; markPersistentState(); if (phase == 0) sync(); return; }
            var source = level.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos, facing);
            if (source == null || target == null) return;
            // Belts are sampled at the front-most available carrier in either lane.
            for (int s = 0; s < source.getSlots(); s++) {
                if (level.getBlockEntity(sourcePos) instanceof BeltBlockEntity belt) {
                    var carriers=belt.lane(s/4).carriers();
                    if(s%4>=carriers.size() || carriers.get(s%4).position()<.75)continue;
                }
                var candidate = source.extractItem(s, 1, true);
                if (candidate.isEmpty() || !ItemHandlerHelper.insertItem(target, candidate, true).isEmpty()) continue;
                var extracted = source.extractItem(s, 1, false);
                if (extracted.isEmpty()) continue;
                hand.setStackInSlot(0, extracted); phase = 12; sync(); break;
            }
        } else {
            if (phase > 0) { phase--; markPersistentState(); return; }
            if (target == null) return;
            ItemStack remainder = ItemHandlerHelper.insertItem(target, held().copy(), false);
            if (remainder.getCount() != held().getCount()) {
                factorycraft.diagnostics.FactoryTelemetry.transferred(this,held().getCount()-remainder.getCount());
                hand.setStackInSlot(0, remainder); phase = remainder.isEmpty() ? 12 : 0; sync();
            }
        }
    }
    @Override public void dropContents() {
        if (level == null || level.isClientSide) return;
        var stack = hand.extractItem(0, Integer.MAX_VALUE, false);
        if (!stack.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX()+.5, worldPosition.getY()+.5, worldPosition.getZ()+.5, stack);
    }
    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.saveAdditional(t,r); t.putInt("Schema",1); t.put("Hand", hand.serializeNBT(r)); t.putInt("Phase",phase); t.putLong("PhaseTick",level==null ? 0 : level.getGameTime());
    }
    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.loadAdditional(t,r); hand.deserializeNBT(r,t.getCompound("Hand")); phase=Math.clamp(t.getInt("Phase"),0,12); phaseTick=t.getLong("PhaseTick");
    }
}
