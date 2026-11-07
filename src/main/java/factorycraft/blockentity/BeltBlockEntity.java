package factorycraft.blockentity;

import factorycraft.blocks.*;
import factorycraft.registry.Content;
import factorycraft.simulation.belts.BeltLane;
import factorycraft.simulation.BeltSimulation;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.List;

public final class BeltBlockEntity extends FactoryBlockEntity {
    private final BeltLane<ItemStack> left = new BeltLane<>(), right = new BeltLane<>();
    private boolean alternate;
    public boolean scheduled, simulationLoaded;
    private long snapshotTick;
    private final IItemHandler handler = new IItemHandler() {
        @Override public int getSlots() { return 8; }
        @Override public ItemStack getStackInSlot(int s) { var lane = lane(s / 4); return s % 4 < lane.carriers().size() ? lane.carriers().get(s % 4).value.copy() : ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int s, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !lane(s / 4).canInsert()) return stack;
            if (!simulate) { lane(s / 4).insert(stack.copyWithCount(1), level == null ? 0 : level.getGameTime()); sync(); BeltSimulation.activate(BeltBlockEntity.this); }
            return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        }
        @Override public ItemStack extractItem(int s, int n, boolean simulate) {
            var lane = lane(s / 4);
            if (n <= 0 || s % 4 >= lane.carriers().size()) return ItemStack.EMPTY;
            var result = lane.carriers().get(s % 4).value.copy();
            if (!simulate) { lane.remove(s % 4); sync(); BeltSimulation.activate(BeltBlockEntity.this); BeltSimulation.wakeInputs(BeltBlockEntity.this); }
            return result;
        }
        @Override public int getSlotLimit(int s) { return 1; }
        @Override public boolean isItemValid(int s, ItemStack stack) { return true; }
    };
    public BeltBlockEntity(BlockPos p, BlockState s) { super(Content.BELT.get(), p, s); }
    public BeltLane<ItemStack> lane(int i) { return i == 0 ? left : right; }
    public double speed() { return ((FactoryBlock)getBlockState().getBlock()).kind.beltTier * .09375; }
    public long snapshotTick() { return snapshotTick; }
    @Override public IItemHandler inventory() { return handler; }

    private boolean send(ItemStack stack, int laneIndex) {
        Direction facing = getBlockState().getValue(FactoryBlock.FACING);
        boolean split = ((FactoryBlock)getBlockState().getBlock()).kind == FactoryKind.SPLITTER;
        for (int attempt = 0; attempt < (split ? 2 : 1); attempt++) {
            boolean branch = split && (alternate ^ (attempt == 1));
            Direction output = branch ? facing.getClockWise() : facing;
            BlockPos targetPos = worldPosition.relative(output);
            if (!level.hasChunkAt(targetPos) || !(level.getBlockEntity(targetPos) instanceof BeltBlockEntity target)) continue;
            Direction targetFacing = target.getBlockState().getValue(FactoryBlock.FACING);
            if (targetFacing == output.getOpposite()) continue;
            // Side loading merges into the near lane; straight flow preserves the lane.
            int targetLane = targetFacing == output ? laneIndex : (output == targetFacing.getClockWise() ? 0 : 1);
            if (target.lane(targetLane).insert(stack, level.getGameTime())) {
                target.sync(); BeltSimulation.activate(target);
                if (split) alternate = !branch;
                return true;
            }
        }
        return false;
    }
    public boolean hasItems() { return !left.carriers().isEmpty() || !right.carriers().isEmpty(); }
    @Override public void onLoad() {
        super.onLoad();
        if(!level.isClientSide && !simulationLoaded) { simulationLoaded=true; BeltSimulation.register(this); }
    }
    @Override public void setRemoved() {
        if(simulationLoaded)BeltSimulation.unregister(this);
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        if(simulationLoaded)BeltSimulation.unregister(this);
        super.onChunkUnloaded();
    }
    public boolean tickMovement() {
        boolean wasLeft=left.canInsert(), wasRight=right.canInsert();
        int before=left.carriers().size()+right.carriers().size();
        long now = level.getGameTime();
        boolean moved = left.advance(speed(), now, s -> send(s, 0));
        moved |= right.advance(speed(), now, s -> send(s, 1));
        if (moved) {
            setChanged();
            if ((now & 3) == 0 || before!=left.carriers().size()+right.carriers().size()) sync();
        }
        if((!wasLeft && left.canInsert()) || (!wasRight && right.canInsert()))BeltSimulation.wakeInputs(this);
        return hasItems() && (moved || (!left.carriers().isEmpty() && left.carriers().getLast().insertedTick==now) || (!right.carriers().isEmpty() && right.carriers().getLast().insertedTick==now));
    }
    @Override public void dropContents() {
        if(level==null || level.isClientSide)return;
        for(int l=0;l<2;l++)while(!lane(l).carriers().isEmpty()) {
            ItemStack stack=lane(l).remove(0);
            net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack);
        }
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r); tag.putInt("Schema", 1); tag.putBoolean("Alternate", alternate);
        tag.putLong("SnapshotTick", level == null ? 0 : level.getGameTime());
        for (int l = 0; l < 2; l++) {
            ListTag list = new ListTag();
            for (var c : lane(l).carriers()) { var n = new CompoundTag(); n.put("Stack", c.value.save(r)); n.putDouble("Position", c.position); list.add(n); }
            tag.put("Lane" + l, list);
        }
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r); alternate = tag.getBoolean("Alternate"); snapshotTick = tag.getLong("SnapshotTick");
        for (int l = 0; l < 2; l++) {
            lane(l).clear();
            ListTag list = tag.getList("Lane" + l, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var n = list.getCompound(i); var stack = ItemStack.parseOptional(r, n.getCompound("Stack"));
                if (!stack.isEmpty()) lane(l).restore(stack, n.getDouble("Position"));
            }
        }
    }
}
