package factorycraft.blockentity;

import factorycraft.blocks.FactoryBlock;
import factorycraft.fluids.FluidConnections;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.UUID;

/** Stateless resource forwarding: only anchor/identity persist, never an inventory or fluid copy. */
public final class CasingBlockEntity extends FactoryBlockEntity {
    private BlockPos anchor;private UUID identity;private boolean loaded;
    public CasingBlockEntity(BlockPos pos,BlockState state) { super(Content.CASING.get(),pos,state); }
    public boolean loaded() { return loaded && !isRemoved(); }
    public boolean virtualPart() { return true; }
    public boolean boundTo(BlockPos pos,UUID id) { return anchor!=null && anchor.equals(pos) && identity!=null && identity.equals(id); }
    public AssemblyOwner genericForRemoval() {
        if(level==null || anchor==null || !level.hasChunkAt(anchor))return null;
        var owner=AssemblyAccess.owner(level.getBlockEntity(anchor));return owner!=null && owner.assembly().owns(identity) ? owner:null;
    }
    public AssemblyOwner genericOwner() { var owner=genericForRemoval();return loaded() && FluidConnections.canonical(this) && owner!=null && owner.assembly().ready() ? owner:null; }
    public MachineBlockEntity controllerForRemoval() { var owner=genericForRemoval();return owner instanceof MachineBlockEntity m ? m:null; }
    public MachineBlockEntity controllerForInteraction() { return loaded() ? controllerForRemoval():null; }
    public MachineBlockEntity owner() { var owner=genericOwner();return owner instanceof MachineBlockEntity m ? m:null; }
    public ItemStack controllerItem() { var owner=genericForRemoval();return owner==null ? ItemStack.EMPTY:new ItemStack(owner.factoryEntity().getBlockState().getBlock().asItem()); }
    public boolean availableFor(BlockPos pos,UUID oldId) { if(anchor==null || boundTo(pos,oldId))return true;repairOrphan();return anchor==null; }
    public void bind(BlockPos pos,UUID id,boolean generated) { anchor=pos.immutable();identity=id; }
    public void refreshAppearance() { if(level!=null && !level.isClientSide) { level.invalidateCapabilities(worldPosition);level.setBlock(worldPosition,getBlockState().setValue(FactoryBlock.ACTIVE,true),3);sync(); } }
    public void unbind() { anchor=null;identity=null; }
    private void repairOrphan() {
        if(anchor==null || level==null || level.isClientSide || !level.hasChunkAt(anchor))return;
        if(genericForRemoval()==null)level.removeBlock(worldPosition,false);
    }
    private void suspendOwner() { var owner=genericForRemoval();if(owner!=null)owner.assembly().invalidate(); }
    public void onLoad() { super.onLoad();loaded=true;repairOrphan();suspendOwner(); }
    public void onChunkUnloaded() { loaded=false;suspendOwner();super.onChunkUnloaded(); }
    public void setRemoved() { loaded=false;suspendOwner();super.setRemoved(); }
    public void dropContents() { var owner=genericForRemoval();if(owner!=null)owner.assembly().removeWhole(true); }
    private IItemHandler items(Direction side) { var owner=genericOwner();return owner==null ? null:AssemblyAccess.items(owner,side); }
    private final IItemHandler itemPort=new IItemHandler() {
        public int getSlots() { var h=items(null);return h==null ? 0:h.getSlots(); }
        public ItemStack getStackInSlot(int i) { var h=items(null);return h==null ? ItemStack.EMPTY:h.getStackInSlot(i).copy(); }
        public ItemStack insertItem(int i,ItemStack s,boolean sim) { var h=items(null);return h==null ? s:h.insertItem(i,s,sim); }
        public ItemStack extractItem(int i,int n,boolean sim) { var h=items(null);return h==null ? ItemStack.EMPTY:h.extractItem(i,n,sim); }
        public int getSlotLimit(int i) { var h=items(null);return h==null ? 0:h.getSlotLimit(i); }
        public boolean isItemValid(int i,ItemStack s) { var h=items(null);return h!=null && h.isItemValid(i,s); }
    };
    public IItemHandler inventory() { return itemPort; }
    public final IEnergyStorage energy=new IEnergyStorage() {
        private IEnergyStorage port() { var owner=genericOwner();return owner==null ? null:AssemblyAccess.energy(owner); }
        public int receiveEnergy(int n,boolean sim) { var p=port();return p==null ? 0:p.receiveEnergy(n,sim); }
        public int extractEnergy(int n,boolean sim) { var p=port();return p==null ? 0:p.extractEnergy(n,sim); }
        public int getEnergyStored() { var p=port();return p==null ? 0:p.getEnergyStored(); }
        public int getMaxEnergyStored() { var p=port();return p==null ? 0:p.getMaxEnergyStored(); }
        public boolean canReceive() { var p=port();return p!=null && p.canReceive(); }
        public boolean canExtract() { var p=port();return p!=null && p.canExtract(); }
    };
    public IFluidHandler fluid(Direction side) { return new IFluidHandler() {
        private IFluidHandler port() { var owner=genericOwner();return owner==null ? null:AssemblyAccess.fluid(owner,worldPosition,side); }
        public int getTanks() { var p=port();return p==null ? 0:p.getTanks(); }
        public FluidStack getFluidInTank(int i) { var p=port();return p==null ? FluidStack.EMPTY:p.getFluidInTank(i); }
        public int getTankCapacity(int i) { var p=port();return p==null ? 0:p.getTankCapacity(i); }
        public boolean isFluidValid(int i,FluidStack s) { var p=port();return p!=null && p.isFluidValid(i,s); }
        public int fill(FluidStack s,FluidAction a) { var p=port();return p==null ? 0:p.fill(s,a); }
        public FluidStack drain(FluidStack s,FluidAction a) { var p=port();return p==null ? FluidStack.EMPTY:p.drain(s,a); }
        public FluidStack drain(int n,FluidAction a) { var p=port();return p==null ? FluidStack.EMPTY:p.drain(n,a); }
    }; }
    protected void saveAdditional(CompoundTag tag,HolderLookup.Provider r) { super.saveAdditional(tag,r);tag.putInt("Schema",3);if(anchor!=null)tag.putLong("Controller",anchor.asLong());if(identity!=null)tag.putUUID("AssemblyIdentity",identity); }
    protected void loadAdditional(CompoundTag tag,HolderLookup.Provider r) { super.loadAdditional(tag,r);anchor=tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")):null;identity=tag.hasUUID("AssemblyIdentity") ? tag.getUUID("AssemblyIdentity"):null; }
}
