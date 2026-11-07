package factorycraft.blockentity;

import factorycraft.blocks.FactoryBlock;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.UUID;

/** A stable forwarding port: a cached capability can never retain a removed controller inventory. */
public final class CasingBlockEntity extends FactoryBlockEntity {
    private BlockPos anchor;
    private UUID identity;
    private boolean loaded,virtualPart;
    private final IItemHandler port=new IItemHandler() {
        public int getSlots() { return 9; }
        public ItemStack getStackInSlot(int s) { check(s);var owner=owner();return owner==null ? ItemStack.EMPTY:owner.automation(null).getStackInSlot(s); }
        public ItemStack insertItem(int s,ItemStack stack,boolean simulate) { check(s);var owner=owner();return owner==null ? stack:owner.automation(null).insertItem(s,stack,simulate); }
        public ItemStack extractItem(int s,int n,boolean simulate) { check(s);var owner=owner();return owner==null ? ItemStack.EMPTY:owner.automation(null).extractItem(s,n,simulate); }
        public int getSlotLimit(int s) { check(s);return 64; }
        public boolean isItemValid(int s,ItemStack stack) { check(s);var owner=owner();return owner!=null && owner.automation(null).isItemValid(s,stack); }
        private void check(int s) { java.util.Objects.checkIndex(s,9); }
    };
    public final IEnergyStorage energy=new IEnergyStorage() {
        public int receiveEnergy(int amount,boolean simulate) { var owner=owner();return owner==null ? 0:owner.power.receiveEnergy(amount,simulate); }
        public int extractEnergy(int amount,boolean simulate) { return 0; }
        public int getEnergyStored() { var owner=owner();return owner==null ? 0:owner.power.getEnergyStored(); }
        public int getMaxEnergyStored() { return 16000; }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return owner()!=null; }
    };
    public CasingBlockEntity(BlockPos pos,BlockState state) { super(Content.CASING.get(),pos,state); }
    @Override public IItemHandler inventory() { return port; }
    public boolean loaded() { return loaded && !isRemoved(); }
    public boolean virtualPart() { return virtualPart; }
    public boolean boundTo(BlockPos pos,UUID id) { return anchor!=null && anchor.equals(pos) && identity!=null && identity.equals(id); }
    private MachineBlockEntity linkedController() {
        if(level==null || level.isClientSide || anchor==null || !level.hasChunkAt(anchor))return null;
        return level.getBlockEntity(anchor) instanceof MachineBlockEntity machine && machine.loaded() && machine.assembly()!=null && machine.assembly().owns(identity) ? machine:null;
    }
    public MachineBlockEntity controllerForInteraction() { return loaded() ? linkedController():null; }
    /** Placement and destruction can happen before the first onLoad callback. */
    public MachineBlockEntity controllerForRemoval() {
        if(level==null || level.isClientSide || anchor==null || !level.hasChunkAt(anchor))return null;
        return level.getBlockEntity(anchor) instanceof MachineBlockEntity machine && machine.assembly()!=null && machine.assembly().owns(identity) ? machine:null;
    }
    public MachineBlockEntity owner() {
        if(!loaded())return null;
        var machine=linkedController();return machine!=null && machine.assembly().ready() ? machine:null;
    }
    public boolean availableFor(BlockPos controller,UUID oldId) {
        if(anchor==null || boundTo(controller,oldId))return true;
        repairOrphan();return anchor==null;
    }
    public void bind(BlockPos controller,UUID id,boolean generated) { anchor=controller.immutable();identity=id;virtualPart=generated; }
    public void refreshAppearance() { setAppearance(anchor!=null && identity!=null);sync(); }
    public void unbind() { anchor=null;identity=null;setAppearance(false);sync(); }
    private void setAppearance(boolean formed) {
        if(level!=null && !level.isClientSide && level.getBlockState(worldPosition).is(getBlockState().getBlock()) && getBlockState().getValue(FactoryBlock.ACTIVE)!=formed)
            level.setBlock(worldPosition,getBlockState().setValue(FactoryBlock.ACTIVE,formed),3);
    }
    private void repairOrphan() {
        if(anchor==null || level==null || level.isClientSide || !level.hasChunkAt(anchor))return;
        // An existing controller can be temporarily inactive while its chunk is transitioning.
        // Keep its identity; only capabilities depend on loaded()/ready().
        var existing=level.getBlockEntity(anchor);
        if(!(existing instanceof MachineBlockEntity machine) || machine.assembly()==null || !machine.assembly().owns(identity)) {
            if(virtualPart)level.removeBlock(worldPosition,false);
            else unbind();
        }
    }
    private void suspendOwner() { var machine=linkedController();if(machine!=null)machine.assembly().invalidate(); }
    @Override public void onLoad() { super.onLoad();loaded=true;repairOrphan();suspendOwner(); }
    @Override public void onChunkUnloaded() { loaded=false;suspendOwner();super.onChunkUnloaded(); }
    @Override public void setRemoved() { loaded=false;suspendOwner();super.setRemoved(); }
    @Override public void dropContents() {
        // Only the controller owns resources; automatic demolition drops that inventory once.
        var machine=controllerForRemoval();if(machine!=null)machine.assembly().dismantle();
    }
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r) {
        super.saveAdditional(t,r);t.putInt("Schema",2);t.putBoolean("VirtualPart",virtualPart);
        if(anchor!=null && identity!=null) { t.putLong("Controller",anchor.asLong());t.putUUID("AssemblyIdentity",identity); }
    }
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r) {
        super.loadAdditional(t,r);anchor=t.contains("Controller") ? BlockPos.of(t.getLong("Controller")):null;
        identity=t.hasUUID("AssemblyIdentity") ? t.getUUID("AssemblyIdentity"):null;virtualPart=t.getBoolean("VirtualPart");
        if(identity==null)anchor=null;
        if(loaded)suspendOwner();
    }
}
