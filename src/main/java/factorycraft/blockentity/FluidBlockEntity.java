package factorycraft.blockentity;

import factorycraft.blocks.FluidDeviceBlock;
import factorycraft.energy.*;
import factorycraft.fluids.*;
import factorycraft.production.PowerBuffer;
import factorycraft.registry.Content;
import factorycraft.simulation.fluids.FluidCell;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;

/** One canonical, persisted fluid cell. Cached capability objects recheck lifecycle and orientation. */
public final class FluidBlockEntity extends FactoryBlockEntity implements MenuProvider,GridConsumer {
    private static final IItemHandler EMPTY=new ItemStackHandler(0);
    private boolean loaded;
    private final FluidKind kind;
    private final FluidCell<FluidKey> cell;
    private net.minecraft.world.level.chunk.LevelChunk ownedChunk;
    public final PowerBuffer power=new PowerBuffer(16000,this::energyChanged);
    private final IFluidHandler[] ports=new IFluidHandler[7];
    public final net.neoforged.neoforge.energy.IEnergyStorage energyPort=new net.neoforged.neoforge.energy.IEnergyStorage() {
        public int receiveEnergy(int n,boolean simulate) { return accessible() && kind==FluidKind.PUMP ? power.receiveEnergy(n,simulate):0; }
        public int extractEnergy(int n,boolean simulate) { return 0; }
        public int getEnergyStored() { return readable() ? power.getEnergyStored():0; }
        public int getMaxEnergyStored() { return 16000; }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return accessible() && kind==FluidKind.PUMP; }
    };
    public FluidBlockEntity(BlockPos pos,BlockState state) {
        super(Content.FLUID.get(),pos,state);kind=FluidBlocks.kind(state);cell=new FluidCell<>(kind.capacity,this::fluidChanged);
        for(int i=0;i<ports.length;i++)ports[i]=new Port(i==6 ? null:Direction.from3DDataValue(i));
    }
    public FluidKind kind() { return kind; }
    public FluidCell<FluidKey> cell() { return cell; }
    public boolean loaded() { return loaded && !isRemoved(); }
    public boolean simulationAvailable() {
        if(!loaded() || !(level instanceof net.minecraft.server.level.ServerLevel server))return false;
        var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
        if(chunk==null || chunk.getBlockEntities().get(worldPosition)!=this)return false;
        if(ownedChunk==null)ownedChunk=chunk;
        return ownedChunk==chunk;
    }
    private boolean accessible() { return simulationAvailable(); }
    private boolean readable() {
        if(level==null || isRemoved())return false;
        return level.isClientSide ? level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition)==this:simulationAvailable();
    }
    public Direction facing() { return kind==FluidKind.PUMP ? getBlockState().getValue(FluidDeviceBlock.FACING):Direction.NORTH; }
    public IFluidHandler port(Direction side) { return ports[side==null ? 6:side.get3DDataValue()]; }
    public FluidStack stored() { return cell.amount()==0 ? FluidStack.EMPTY:cell.key().stack(cell.amount()); }
    @Override public IItemHandler inventory() { return EMPTY; }
    @Override public PowerBuffer gridBuffer() { return power; }
    @Override public int receiveGridEnergy(int offered,boolean simulate) { return energyPort.receiveEnergy(offered,simulate); }
    private void fluidChanged() {
        if(level==null || level.isClientSide || !loaded())return;
        if(ownedChunk!=null)ownedChunk.setUnsaved(true);
        markPersistentState();FluidSimulation.changed(this);
    }
    private void energyChanged() {
        if(level==null || level.isClientSide || !loaded())return;
        if(ownedChunk!=null)ownedChunk.setUnsaved(true);
        markPersistentState();FluidSimulation.activatePump(this);
    }
    public void flushUpdate() { sendUpdate(); }
    private final class Port implements IFluidHandler {
        final Direction side;
        Port(Direction side) { this.side=side; }
        boolean input() { return accessible() && (kind!=FluidKind.PUMP || side==null || side==facing().getOpposite()); }
        boolean output() { return accessible() && (kind!=FluidKind.PUMP || side==null || side==facing()); }
        public int getTanks() { return 1; }
        public FluidStack getFluidInTank(int tank) { java.util.Objects.checkIndex(tank,1);return readable() ? stored():FluidStack.EMPTY; }
        public int getTankCapacity(int tank) { java.util.Objects.checkIndex(tank,1);return Math.max(cell.capacity(),cell.amount()); }
        public boolean isFluidValid(int tank,FluidStack stack) { java.util.Objects.checkIndex(tank,1);return input() && !stack.isEmpty(); }
        public int fill(FluidStack stack,FluidAction action) {
            return !input() || stack.isEmpty() ? 0:cell.fill(new FluidKey(stack),stack.getAmount(),action.simulate());
        }
        public FluidStack drain(FluidStack stack,FluidAction action) {
            if(!output() || stack.isEmpty() || cell.key()==null || !cell.key().equals(new FluidKey(stack)))return FluidStack.EMPTY;
            return drain(stack.getAmount(),action);
        }
        public FluidStack drain(int amount,FluidAction action) {
            if(!output() || amount<=0 || cell.amount()==0)return FluidStack.EMPTY;
            var fluid=cell.key();int extracted=cell.drain(amount,action.simulate());return fluid.stack(extracted);
        }
    }
    @Override public void onLoad() {
        super.onLoad();if(level.isClientSide || loaded)return;loaded=true;simulationAvailable();
        FluidSimulation.register(this);if(kind==FluidKind.PUMP)PowerGrid.add(this);
    }
    private void detach() {
        if(!loaded || level==null || level.isClientSide)return;
        loaded=false;ownedChunk=null;FluidSimulation.unregister(this);if(kind==FluidKind.PUMP)PowerGrid.remove(this);
    }
    @Override public void onChunkUnloaded() { detach();super.onChunkUnloaded(); }
    @Override public void setRemoved() { detach();super.setRemoved(); }
    @Override public void setBlockState(BlockState state) {
        boolean rotated=kind==FluidKind.PUMP && getBlockState().getValue(FluidDeviceBlock.FACING)!=state.getValue(FluidDeviceBlock.FACING);
        super.setBlockState(state);
        if(rotated && loaded()) { level.invalidateCapabilities(worldPosition);FluidSimulation.topologyChanged(this); }
    }
    public final ContainerData data=new ContainerData() {
        public int get(int index) {
            int amount=cell.amount(),capacity=Math.max(cell.capacity(),amount),id=cell.key()==null ? 0:BuiltInRegistries.FLUID.getId(stored().getFluid());
            return switch(index) {case 0->amount&65535;case 1->amount>>>16;case 2->capacity&65535;case 3->capacity>>>16;case 4->power.getEnergyStored();case 5->id&65535;case 6->id>>>16;default->0;};
        }
        public void set(int index,int value) {}
        public int getCount() { return 7; }
    };
    @Override public Component getDisplayName() { return Component.translatable("block.factorycraft."+kind.id); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new FluidMenu(id,inventory,FluidBlockEntity.this,data); }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);tag.putInt("Schema",1);tag.put("StoredFluid",stored().saveOptional(registries));tag.putInt("Energy",power.getEnergyStored());
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);var fluid=FluidStack.parseOptional(registries,tag.getCompound("StoredFluid"));
        cell.restore(fluid.isEmpty() ? null:new FluidKey(fluid),fluid.getAmount());power.restore(tag.getInt("Energy"));
        if(loaded())FluidSimulation.changed(this);
    }
}
