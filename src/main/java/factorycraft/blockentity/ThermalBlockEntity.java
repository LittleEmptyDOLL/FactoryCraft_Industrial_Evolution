package factorycraft.blockentity;

import factorycraft.blocks.*;
import factorycraft.energy.*;
import factorycraft.fluids.*;
import factorycraft.recipes.BoilingRecipe;
import factorycraft.registry.Content;
import factorycraft.simulation.fluids.FluidCell;
import factorycraft.simulation.thermal.*;
import factorycraft.thermal.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;
import java.util.*;

/** Canonical fuel, progress, water, steam and FE. All ports recheck loaded chunk ownership. */
public final class ThermalBlockEntity extends FactoryBlockEntity implements factorycraft.multiblock.AssemblyOwner,MenuProvider,GridGenerator {
    public static final int FLUID_CAPACITY=4000,PORT_RATE=100;
    private final factorycraft.multiblock.IndustrialAssembly assembly;
    public factorycraft.multiblock.IndustrialAssembly assembly() { return assembly; }
    public boolean loaded() { return loaded && !isRemoved(); }
    private boolean loaded,transaction,inventoryDirty,syncPending;
    private net.minecraft.world.level.chunk.LevelChunk ownedChunk;
    private BlockPos intake,outlet;
    private int duration=20,status;
    private long recipeTick=Long.MIN_VALUE;
    private record CachedRecipe(RecipeHolder<BoilingRecipe> holder,String identity) {}
    private List<CachedRecipe> recipes=List.of();
    public final FluidCell<FluidKey> water=new FluidCell<>(FLUID_CAPACITY,this::changed);
    public final FluidCell<FluidKey> steam=new FluidCell<>(FLUID_CAPACITY,this::changed);
    public final BoilerProcess boiler=new BoilerProcess();
    public final SteamEngine<FluidKey> engine=new SteamEngine<>(steam,this::changed);
    public final ItemStackHandler items=new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) {
            inventoryDirty=true;if(!transaction)inventoryChanged();
        }
        @Override public boolean isItemValid(int slot,ItemStack stack) { return kind()==ThermalKind.BOILER && stack.getBurnTime(RecipeType.SMELTING)>0; }
    };
    private final IItemHandler automation=new IItemHandler() {
        public int getSlots() { return 1; }
        public ItemStack getStackInSlot(int slot) { return available() ? items.getStackInSlot(slot).copy():ItemStack.EMPTY; }
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) { return available() ? items.insertItem(slot,stack,simulate):stack; }
        public ItemStack extractItem(int slot,int count,boolean simulate) {
            return available() && !items.isItemValid(slot,items.getStackInSlot(slot)) ? items.extractItem(slot,count,simulate):ItemStack.EMPTY;
        }
        public int getSlotLimit(int slot) { return 64; }
        public boolean isItemValid(int slot,ItemStack stack) { return available() && items.isItemValid(slot,stack); }
    };
    public final IEnergyStorage energyPort=new IEnergyStorage() {
        public int receiveEnergy(int n,boolean simulate) { return 0; }
        public int extractEnergy(int n,boolean simulate) { return available() && generatesElectricity() ? engine.extract(n,simulate):0; }
        public int getEnergyStored() { return readable() ? engine.energy():0; }
        public int getMaxEnergyStored() { return Math.max(SteamEngine.CAPACITY,engine.energy()); }
        public boolean canReceive() { return false; }
        public boolean canExtract() { return available() && generatesElectricity(); }
    };
    private final IFluidHandler[] ports=new IFluidHandler[7];
    public ThermalBlockEntity(BlockPos pos,BlockState state) {
        super(Content.THERMAL.get(),pos,state);assembly=new factorycraft.multiblock.IndustrialAssembly(this);positions();
        for(int i=0;i<7;i++)ports[i]=new Port(i==6 ? null:Direction.from3DDataValue(i));
    }
    public ThermalKind kind() { return ((ThermalBlock)getBlockState().getBlock()).kind; }
    public Direction facing() { return getBlockState().getValue(ThermalBlock.FACING); }
    public BlockPos inputNozzle() { var p=factorycraft.multiblock.MachineProfile.of(getBlockState());return p.offset(worldPosition,facing(),p.layout.width()/2,0,p.layout.depth()-1); }
    public BlockPos outputNozzle() { var p=factorycraft.multiblock.MachineProfile.of(getBlockState());return p.offset(worldPosition,facing(),p.layout.width()/2,0,0); }
    private void positions() { intake=inputNozzle().relative(facing().getOpposite());outlet=outputNozzle().relative(facing()); }
    public IFluidHandler structurePort(BlockPos pos,Direction side) {
        return side==null ? port(null):pos.equals(inputNozzle()) && side==facing().getOpposite() || kind()==ThermalKind.BOILER && pos.equals(outputNozzle()) && side==facing() ? port(side):null;
    }
    public boolean generatesElectricity() { return kind()==ThermalKind.ENGINE; }
    public boolean available() {
        if(!loaded || isRemoved() || !(level instanceof net.minecraft.server.level.ServerLevel server))return false;
        var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
        if(chunk==null || chunk.getBlockEntities().get(worldPosition)!=this)return false;
        if(ownedChunk==null)ownedChunk=chunk;return ownedChunk==chunk && assembly.ready();
    }
    private boolean readable() { return level!=null && !isRemoved() && (level.isClientSide ? level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition)==this:available()); }
    private void changed() {
        if(level==null || level.isClientSide || !loaded)return;
        if(ownedChunk!=null)ownedChunk.setUnsaved(true);markPersistentState();syncPending=true;
    }
    private void inventoryChanged() {
        if(!inventoryDirty)return;inventoryDirty=false;changed();if(level!=null && !level.isClientSide && loaded)setChanged();
    }
    public FluidStack contents(FluidCell<FluidKey> cell) { return cell.amount()==0 ? FluidStack.EMPTY:cell.key().stack(cell.amount()); }
    public boolean hasContents() { return water.amount()>0 || steam.amount()>0 || engine.energy()>0 || boiler.fuel()>0 || boiler.progress()>0 || !items.getStackInSlot(0).isEmpty(); }
    @Override public IItemHandler inventory() { return items; }
    public IItemHandler automation() { return automation; }
    public IFluidHandler port(Direction side) { return ports[side==null ? 6:side.get3DDataValue()]; }
    private boolean acceptsSteam(FluidKey key) { return key!=null && key.stack(1).getFluid().getFluidType()==SteamFluids.TYPE.get(); }
    private FluidCell<FluidKey> input(Direction side) {
        return side==null || side==facing().getOpposite() ? kind()==ThermalKind.BOILER ? water:steam:null;
    }
    private FluidCell<FluidKey> output(Direction side) { return kind()==ThermalKind.BOILER && (side==null || side==facing()) ? steam:null; }
    private final class Port implements IFluidHandler {
        final Direction side;
        Port(Direction side) { this.side=side; }
        public int getTanks() { return side==null && kind()==ThermalKind.BOILER ? 2:1; }
        private FluidCell<FluidKey> tank(int index) {
            Objects.checkIndex(index,getTanks());return side==null && kind()==ThermalKind.BOILER ? index==0 ? water:steam:side==facing() ? steam:kind()==ThermalKind.BOILER ? water:steam;
        }
        public FluidStack getFluidInTank(int index) { var cell=tank(index);return readable() ? contents(cell):FluidStack.EMPTY; }
        public int getTankCapacity(int index) { var cell=tank(index);return Math.max(FLUID_CAPACITY,cell.amount()); }
        public boolean isFluidValid(int index,FluidStack fluid) { var cell=tank(index);return available() && cell==input(side) && !fluid.isEmpty() && (kind()==ThermalKind.BOILER || acceptsSteam(new FluidKey(fluid))); }
        public int fill(FluidStack fluid,FluidAction action) {
            var cell=input(side);return !available() || cell==null || fluid.isEmpty() || kind()==ThermalKind.ENGINE && !acceptsSteam(new FluidKey(fluid)) ? 0:cell.fill(new FluidKey(fluid),fluid.getAmount(),action.simulate());
        }
        public FluidStack drain(FluidStack fluid,FluidAction action) {
            if(fluid.isEmpty())return FluidStack.EMPTY;
            var cell=drainCell(fluid);if(cell==null)return FluidStack.EMPTY;
            var key=cell.key();return key.stack(cell.drain(fluid.getAmount(),action.simulate()));
        }
        private FluidCell<FluidKey> drainCell(FluidStack requested) {
            if(!available())return null;
            if(side==null) {
                for(var cell:List.of(water,steam))if(cell.amount()>0 && (requested==null || cell.key().equals(new FluidKey(requested))))return cell;
                return null;
            }
            var cell=output(side);
            return cell!=null && cell.amount()>0 && (requested==null || cell.key().equals(new FluidKey(requested))) ? cell:null;
        }
        public FluidStack drain(int amount,FluidAction action) {
            if(amount<=0)return FluidStack.EMPTY;var cell=drainCell(null);if(cell==null)return FluidStack.EMPTY;
            var key=cell.key();return key.stack(cell.drain(amount,action.simulate()));
        }
    }
    public FluidCell<FluidKey> externalInput(Direction side,FluidKey key) { return available() && (kind()==ThermalKind.BOILER || acceptsSteam(key)) ? input(side):null; }
    public FluidCell<FluidKey> externalOutput(Direction side) { return available() ? output(side):null; }
    public void tick() {
        if(!available())return;
        var incoming=kind()==ThermalKind.BOILER ? water:steam;
        if(incoming.space()>0) {
            var from=FluidConnections.output(level,intake,facing());
            if(from!=null && (kind()==ThermalKind.BOILER || acceptsSteam(from.key())))from.transferTo(incoming,PORT_RATE,false);
        }
        if(!available())return;
        boolean worked;
        if(kind()==ThermalKind.BOILER) {
            worked=boil();
            if(available() && steam.amount()>0) { var to=FluidConnections.input(level,outlet,facing().getOpposite(),steam.key());if(to!=null)steam.transferTo(to,PORT_RATE,false); }
        } else {
            int made=engine.generate(acceptsSteam(steam.key()));
            int delivered=engine.energy()>0 && PowerGrid.hasDemand(this) ? PowerGrid.distribute(this,Math.min(120,engine.energy())):0;
            engine.extract(delivered,false);worked=made>0 || delivered>0;
        }
        if(!available())return;
        if(getBlockState().getValue(ThermalBlock.ACTIVE)!=worked)level.setBlock(worldPosition,getBlockState().setValue(ThermalBlock.ACTIVE,worked),3);
        if(syncPending && Math.floorMod(level.getGameTime()+worldPosition.asLong(),5)==0) { syncPending=false;sendUpdate(); }
    }
    private boolean boil() {
        long now=level.getGameTime();
        if(recipeTick==Long.MIN_VALUE || now-recipeTick>=20) {
            var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,level.registryAccess());
            recipes=level.getRecipeManager().getAllRecipesFor(Content.BOILING.get()).stream().sorted(Comparator.comparing(r->r.id().toString()))
                    .map(r->new CachedRecipe(r,r.id()+"|"+BoilingRecipe.CODEC.codec().encodeStart(ops,r.value()).getOrThrow())).toList();recipeTick=now;
        }
        var input=new BoilingRecipe.Input(contents(water));CachedRecipe chosen=null;
        for(var recipe:recipes)if(recipe.holder().value().matches(input,level)) { chosen=recipe;break; }
        if(chosen==null) { status=2;return false; }
        var recipe=chosen.holder().value();var result=recipe.result();duration=recipe.ticks();
        var key=new FluidKey(result);
        if(steam.space()<result.getAmount() || !steam.accepts(key)) { status=3;return false; }
        if(boiler.fuel()==0 && !refuel()) { status=1;return false; }
        if(!available())return false;
        // The serialized recipe definition is part of identity: datapack edits cannot reuse old progress.
        String identity=chosen.identity();
        boolean worked=boiler.advance(identity,duration,water,recipe.input().amount(),steam,key,result.getAmount());
        if(worked)changed();status=worked ? 4:0;return worked;
    }
    private boolean refuel() {
        var fuel=items.getStackInSlot(0);int burn=fuel.getBurnTime(RecipeType.SMELTING);if(burn<=0)return false;
        var remainder=fuel.getCraftingRemainingItem();if(!remainder.isEmpty() && fuel.getCount()>1)return false;
        transaction=true;
        try { items.extractItem(0,1,false);if(!remainder.isEmpty())items.setStackInSlot(0,remainder);boiler.refuel(burn); }
        finally { transaction=false;inventoryChanged(); }
        changed();return true;
    }
    public final ContainerData data=new ContainerData() {
        public int get(int index) {
            return switch(index) {
                case 0->water.amount()&65535;case 1->water.amount()>>>16;case 2->steam.amount()&65535;case 3->steam.amount()>>>16;
                case 4->engine.energy()&65535;case 5->engine.energy()>>>16;case 6->boiler.fuel()&65535;case 7->boiler.fuel()>>>16;
                case 8->boiler.progress()&65535;case 9->boiler.progress()>>>16;case 10->duration&65535;case 11->duration>>>16;
                case 12->status;default->0;
            };
        }
        public void set(int index,int value) {}
        public int getCount() { return 13; }
    };
    @Override public void onLoad() { super.onLoad();if(level.isClientSide || loaded)return;loaded=true;assembly.invalidate();available();if(generatesElectricity())PowerGrid.add(this); }
    private void detach() { if(!loaded || level==null || level.isClientSide)return;loaded=false;ownedChunk=null;assembly.invalidate();if(generatesElectricity())PowerGrid.remove(this); }
    @Override public void onChunkUnloaded() { detach();super.onChunkUnloaded(); }
    @Override public void setRemoved() { detach();super.setRemoved(); }
    @Override public void setBlockState(BlockState state) {
        boolean rotated=facing()!=state.getValue(ThermalBlock.FACING);super.setBlockState(state);positions();
        if(rotated && loaded)level.invalidateCapabilities(worldPosition);
    }
    @Override public void dropContents() { assembly.removeWhole(true); }
    @Override public Component getDisplayName() { return Component.translatable("block.factorycraft."+kind().id); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new ThermalMenu(id,inventory,this,data); }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider r) {
        super.saveAdditional(tag,r);assembly.save(tag);tag.putInt("Schema",1);tag.put("Water",contents(water).saveOptional(r));tag.put("Steam",contents(steam).saveOptional(r));
        tag.put("Items",items.serializeNBT(r));tag.putInt("Energy",engine.energy());tag.putInt("Fuel",boiler.fuel());tag.putInt("Progress",boiler.progress());tag.putString("BoilingRecipe",boiler.recipe());tag.putInt("Duration",duration);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider r) {
        super.loadAdditional(tag,r);assembly.load(tag);
        var input=FluidStack.parseOptional(r,tag.getCompound("Water"));var output=FluidStack.parseOptional(r,tag.getCompound("Steam"));
        water.restore(input.isEmpty() ? null:new FluidKey(input),input.getAmount());steam.restore(output.isEmpty() ? null:new FluidKey(output),output.getAmount());
        items.deserializeNBT(r,tag.getCompound("Items"));engine.restore(Math.max(0,tag.getInt("Energy")));boiler.restore(Math.max(0,tag.getInt("Fuel")),Math.max(0,tag.getInt("Progress")),tag.getString("BoilingRecipe"));
        duration=Math.max(1,tag.getInt("Duration"));recipeTick=Long.MIN_VALUE;changed();
    }
}
