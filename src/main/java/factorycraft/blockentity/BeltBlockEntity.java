package factorycraft.blockentity;

import factorycraft.blocks.*;
import factorycraft.registry.Content;
import factorycraft.simulation.belts.*;
import factorycraft.simulation.BeltSimulation;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.Objects;

public final class BeltBlockEntity extends FactoryBlockEntity implements factorycraft.multiblock.AssemblyOwner {
    private final factorycraft.multiblock.IndustrialAssembly assembly;
    public factorycraft.multiblock.IndustrialAssembly assembly() { return assembly; }
    public boolean loaded() { return simulationLoaded && !isRemoved(); }
    public boolean structureReady() { return assembly==null || assembly.ready(); }
    private final BeltLane<ItemStack> left=new BeltLane<>(),right=new BeltLane<>();
    private BeltTransport<ItemStack> transport;
    private BeltTransport.Node<ItemStack> leftNode,rightNode;
    private boolean alternateLeft,alternateRight,queuedForSync,contentsChanged;
    public boolean simulationLoaded;
    private long snapshotTick;
    private final IItemHandler handler=new IItemHandler() {
        @Override public int getSlots() { return 8; }
        @Override public ItemStack getStackInSlot(int s) {
            Objects.checkIndex(s,8);var lane=lane(s/4);
            return s%4<lane.carriers().size() ? lane.carriers().get(s%4).value.copy():ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int s,ItemStack stack,boolean simulate) {
            Objects.checkIndex(s,8);
            if(stack.isEmpty() || !structureReady() || transport==null || !transport.canInsert(node(s/4)))return stack;
            if(!simulate) {
                if(!transport.insert(node(s/4),stack.copyWithCount(1)))return stack;
                sync();
            }
            return stack.getCount()==1 ? ItemStack.EMPTY:stack.copyWithCount(stack.getCount()-1);
        }
        @Override public ItemStack extractItem(int s,int n,boolean simulate) {
            Objects.checkIndex(s,8);var lane=lane(s/4);
            if(!structureReady() || n<=0 || s%4>=lane.carriers().size())return ItemStack.EMPTY;
            var result=lane.carriers().get(s%4).value.copy();
            if(!simulate) { lane.remove(s%4);sync();wakeTransport(); }
            return result;
        }
        @Override public int getSlotLimit(int s) { Objects.checkIndex(s,8);return 1; }
        @Override public boolean isItemValid(int s,ItemStack stack) { Objects.checkIndex(s,8);return true; }
    };
    public BeltBlockEntity(BlockPos p,BlockState s) { super(Content.BELT.get(),p,s);assembly=((FactoryBlock)s.getBlock()).kind==FactoryKind.SPLITTER ? new factorycraft.multiblock.IndustrialAssembly(this):null; }
    public BeltLane<ItemStack> lane(int i) { Objects.checkIndex(i,2);return i==0 ? left:right; }
    private BeltTransport.Node<ItemStack> node(int i) { return i==0 ? leftNode:rightNode; }
    public double speed() { return ((FactoryBlock)getBlockState().getBlock()).kind.beltTier*.09375; }
    public long snapshotTick() { return snapshotTick; }
    @Override public IItemHandler inventory() { return handler; }
    public boolean hasItems() { return !left.carriers().isEmpty() || !right.carriers().isEmpty(); }
    public boolean isScheduled() { return transport!=null && (leftNode.isActive() || rightNode.isActive()); }

    public void bindTransport(BeltTransport<ItemStack> transport) {
        this.transport=transport;int tier=((FactoryBlock)getBlockState().getBlock()).kind.beltTier;
        leftNode=transport.create(left,tier,c->BeltSimulation.changed(this,c));
        rightNode=transport.create(right,tier,c->BeltSimulation.changed(this,c));
        leftNode.restorePreference(alternateLeft);rightNode.restorePreference(alternateRight);
    }
    public void unbindTransport() {
        if(transport==null)return;
        alternateLeft=leftNode.prefersSecondary();alternateRight=rightNode.prefersSecondary();
        transport.remove(leftNode);transport.remove(rightNode);transport=null;leftNode=null;rightNode=null;
    }
    public void wakeTransport() {
        if(transport!=null) { transport.changedExternally(leftNode);transport.changedExternally(rightNode); }
    }
    public void refreshConnections() {
        if(transport==null || level==null)return;
        if(!structureReady()) { transport.connect(leftNode,null,null);transport.connect(rightNode,null,null);return; }
        Direction facing=getBlockState().getValue(FactoryBlock.FACING);
        boolean split=((FactoryBlock)getBlockState().getBlock()).kind==FactoryKind.SPLITTER;
        for(int l=0;l<2;l++)transport.connect(node(l),target(l,facing),split ? targetAt(l,worldPosition.relative(facing.getClockWise()).relative(facing),facing):null);
    }
    private BeltTransport.Node<ItemStack> target(int lane,Direction output) {
        return targetAt(lane,worldPosition.relative(output),output);
    }
    private BeltTransport.Node<ItemStack> targetAt(int lane,BlockPos p,Direction output) {
        var target=BeltSimulation.at(level,p);
        if(target==null || !target.simulationLoaded || target.transport==null || !target.structureReady())return null;
        Direction targetFacing=target.getBlockState().getValue(FactoryBlock.FACING);
        if(targetFacing==output.getOpposite())return null;
        int targetLane=targetFacing==output ? lane:(output==targetFacing.getClockWise() ? 0:1);
        return target.node(targetLane);
    }
    public boolean markSimulationChanged(boolean contents) {
        contentsChanged|=contents;
        if(queuedForSync)return false;
        queuedForSync=true;return true;
    }
    public void flushSimulationChanges(long tick) {
        factorycraft.diagnostics.FactoryTelemetry.beltChanged(this);
        if(contentsChanged)setChanged();else markPersistentState();
        if(contentsChanged || (tick&3)==0)sendUpdate();
        queuedForSync=false;contentsChanged=false;
    }
    @Override public void setBlockState(BlockState state) {
        boolean changed=getBlockState().getValue(FactoryBlock.FACING)!=state.getValue(FactoryBlock.FACING);
        super.setBlockState(state);
        // LevelChunk calls onPlace before it updates the existing block entity's state.
        if(changed && simulationLoaded)BeltSimulation.topologyChanged(this);
    }
    @Override public void onLoad() {
        super.onLoad();
        if(!level.isClientSide && !simulationLoaded) { simulationLoaded=true;BeltSimulation.register(this);if(assembly!=null)assembly.invalidate(); }
    }
    @Override public void setRemoved() { if(simulationLoaded)BeltSimulation.unregister(this);super.setRemoved(); }
    @Override public void onChunkUnloaded() { if(simulationLoaded)BeltSimulation.unregister(this);super.onChunkUnloaded(); }
    @Override public void dropContents() {
        if(level==null || level.isClientSide)return;
        if(assembly!=null) { assembly.removeWhole(true);return; }
        for(int l=0;l<2;l++)while(!lane(l).carriers().isEmpty()) {
            ItemStack stack=lane(l).remove(0);
            net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack);
        }
        setChanged();wakeTransport();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider r) {
        super.saveAdditional(tag,r);if(assembly!=null)assembly.save(tag);tag.putInt("Schema",2);
        tag.putBoolean("Alternate",transport==null ? alternateLeft:leftNode.prefersSecondary());
        tag.putBoolean("AlternateRight",transport==null ? alternateRight:rightNode.prefersSecondary());
        tag.putLong("SnapshotTick",level==null ? 0:level.getGameTime());
        for(int l=0;l<2;l++) {
            ListTag list=new ListTag();
            for(var c:lane(l).carriers()) {
                var n=new CompoundTag();n.put("Stack",c.value.save(r));n.putDouble("Position",c.position());n.putInt("Units",c.positionUnits());list.add(n);
            }
            tag.put("Lane"+l,list);
        }
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider r) {
        super.loadAdditional(tag,r);if(assembly!=null)assembly.load(tag);alternateLeft=tag.getBoolean("Alternate");
        alternateRight=tag.contains("AlternateRight") ? tag.getBoolean("AlternateRight"):alternateLeft;
        snapshotTick=tag.getLong("SnapshotTick");
        for(int l=0;l<2;l++) {
            lane(l).clear();ListTag list=tag.getList("Lane"+l,Tag.TAG_COMPOUND);
            for(int i=0;i<list.size();i++) {
                var n=list.getCompound(i);var stack=ItemStack.parseOptional(r,n.getCompound("Stack"));
                double position=n.contains("Units") ? (double)n.getInt("Units")/BeltLane.LENGTH:n.getDouble("Position");
                if(!stack.isEmpty())lane(l).restore(stack,position);
            }
        }
        if(transport!=null) { leftNode.restorePreference(alternateLeft);rightNode.restorePreference(alternateRight);wakeTransport(); }
    }
}
