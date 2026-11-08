package factorycraft.blockentity;

import factorycraft.blocks.*;
import factorycraft.config.FactoryConfig;
import factorycraft.energy.PowerGrid;
import factorycraft.multiblock.IndustrialAssembly;
import factorycraft.production.*;
import factorycraft.recipes.*;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.*;
import java.util.*;

public final class MachineBlockEntity extends FactoryBlockEntity implements MenuProvider,factorycraft.energy.GridConsumer {
    @Override public PowerBuffer gridBuffer() { return power; }
    private boolean loaded;
    public boolean loaded() { return loaded && !isRemoved(); }
    private boolean automationReady() { return loaded() && level!=null && !level.isClientSide; }
    public final ItemStackHandler items = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int s) { setChanged(); }
        @Override public boolean isItemValid(int s, ItemStack stack) {
            if (s >= 4 && s < 8) return false;
            if (s == 8) return kind() == FactoryKind.GENERATOR && burnTime(stack) > 0;
            return kind().processing();
        }
    };
    public final PowerBuffer power = new PowerBuffer(16000, this::markPersistentState);
    private final IItemHandler automation = new IItemHandler() {
        public int getSlots() { return 9; }
        public ItemStack getStackInSlot(int s) { return automationReady() ? items.getStackInSlot(s).copy():ItemStack.EMPTY; }
        public ItemStack insertItem(int s, ItemStack stack, boolean sim) { return automationReady() ? items.insertItem(s,stack,sim):stack; }
        public ItemStack extractItem(int s, int n, boolean sim) { return automationReady() && s >= 4 && s < 8 ? items.extractItem(s,n,sim) : ItemStack.EMPTY; }
        public int getSlotLimit(int s) { return items.getSlotLimit(s); }
        public boolean isItemValid(int s, ItemStack stack) { return automationReady() && items.isItemValid(s,stack); }
    };
    public final net.neoforged.neoforge.energy.IEnergyStorage energyPort=new net.neoforged.neoforge.energy.IEnergyStorage() {
        public int receiveEnergy(int n,boolean sim) { return automationReady() ? power.receiveEnergy(n,sim):0; }
        public int extractEnergy(int n,boolean sim) { return 0; }
        public int getEnergyStored() { return automationReady() ? power.getEnergyStored():0; }
        public int getMaxEnergyStored() { return power.getMaxEnergyStored(); }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return automationReady(); }
    };
    private final IndustrialAssembly assembly;
    public IndustrialAssembly assembly() { return assembly; }
    private int progress, duration = 1, fuelTicks;
    private String selected = "", activeRecipe = "";
    private List<RecipeHolder<FactoryRecipe>> recipeCache = List.of();
    private long recipeCacheTick = Long.MIN_VALUE;
    private BlockPos oreTarget;
    public final ContainerData data = new ContainerData() {
        public int get(int i) { return switch (i) {
            case 0 -> progress; case 1 -> duration; case 2 -> power.getEnergyStored(); case 3 -> fuelTicks;
            case 4 -> selectedIndex(); case 5 -> previewIndex(); case 6 -> assembly==null ? IndustrialAssembly.READY:assembly.status(); default -> 0;
        }; }
        public void set(int i, int v) { switch(i) { case 0 -> progress=v; case 1 -> duration=v; case 2 -> power.restore(v); case 3 -> fuelTicks=v; default -> {} } }
        public int getCount() { return 7; }
    };
    public MachineBlockEntity(BlockPos p, BlockState s) {
        super(Content.MACHINE.get(),p,s);
        assembly=((FactoryBlock)s.getBlock()).kind==FactoryKind.INDUSTRIAL_FURNACE ? new IndustrialAssembly(this):null;
    }
    public FactoryKind kind() { return ((FactoryBlock)getBlockState().getBlock()).kind; }
    @Override public IItemHandler inventory() { return items; }
    public IItemHandler automation(Direction side) { return automation; }
    public int fuelTicks() { return fuelTicks; }
    public List<RecipeHolder<FactoryRecipe>> recipes() {
        if (level == null) return List.of();
        long now = level.getGameTime();
        if (recipeCacheTick == Long.MIN_VALUE || now - recipeCacheTick >= 20) {
            recipeCache = level.getRecipeManager().getAllRecipesFor(Content.PROCESSING.get()).stream()
                    .filter(h -> h.value().machine().equals(kind().recipeGroup())).sorted(Comparator.comparing(h -> h.id().toString())).toList();
            recipeCacheTick = now;
        }
        return recipeCache;
    }
    public int selectedIndex() {
        var rs=recipes(); for(int i=0;i<rs.size();i++) if(rs.get(i).id().toString().equals(selected)) return i+1;
        return 0;
    }
    public int previewIndex() {
        var rs=recipes();
        for(int i=0;i<rs.size();i++)if((!activeRecipe.isEmpty() && activeRecipe.equals(rs.get(i).id().toString())) ||
                (activeRecipe.isEmpty() && rs.get(i).value().matches(new FactoryInput(items),level)))return i+1;
        return 0;
    }
    public void cycleRecipe() {
        var rs=recipes(); int next=(selectedIndex()+1)%(rs.size()+1);
        selected=next==0 ? "" : rs.get(next-1).id().toString(); progress=0; activeRecipe=""; sync();
    }
    private static int burnTime(ItemStack stack) { return stack.getBurnTime(RecipeType.SMELTING); }
    public void tick() {
        if(!loaded())return;
        if (assembly!=null && !assembly.ready()) { setActive(false);return; }
        if (kind() == FactoryKind.GENERATOR) { generate(); return; }
        boolean worked = kind() == FactoryKind.DRILL ? mine() : produce();
        if (worked && level.getGameTime()%20==0) sync();
        setActive(worked);
    }
    private void setActive(boolean active) {
        if (getBlockState().getValue(FactoryBlock.ACTIVE) != active) level.setBlock(worldPosition,getBlockState().setValue(FactoryBlock.ACTIVE,active),3);
    }
    private void generate() {
        boolean hasDemand = PowerGrid.hasDemand(this);
        if (fuelTicks <= 0 && hasDemand) {
            var fuel=items.getStackInSlot(8); int burn=burnTime(fuel);
            if (burn>0) {
                ItemStack remainder=fuel.getCraftingRemainingItem();
                // Never replace a stacked container fuel with its remainder.
                if (!remainder.isEmpty() && fuel.getCount()>1) { setActive(false); return; }
                items.extractItem(8,1,false);
                if (!remainder.isEmpty()) items.setStackInSlot(8,remainder);
                fuelTicks=burn; markPersistentState();
            }
        }
        boolean active=fuelTicks>0 && hasDemand;
        if(active) {
            int supplied=PowerGrid.distribute(this,FactoryConfig.GENERATOR_FE.get());
            active=supplied>0;
            if(active) { fuelTicks--;markPersistentState(); }
        }
        setActive(active);
    }
    private boolean produce() {
        var input=new FactoryInput(items);
        RecipeHolder<FactoryRecipe> chosen=null;
        for(var r:recipes()) {
            if (!selected.isEmpty() && !selected.equals(r.id().toString())) continue;
            if (!activeRecipe.isEmpty() && progress>0 && !activeRecipe.equals(r.id().toString())) continue;
            if(r.value().matches(input,level)) { chosen=r; break; }
        }
        if(chosen==null) return false;
        var recipe=chosen.value();
        if(!output(recipe.result(),true).isEmpty()) return false;
        int step=assembly==null ? 1:2;
        int advancing=Math.min(step,recipe.ticks()-Math.min(progress,recipe.ticks()-1));
        if(!power.spend(recipe.energy()*advancing)) return false;
        activeRecipe=chosen.id().toString(); duration=recipe.ticks(); progress+=advancing; markPersistentState();
        factorycraft.diagnostics.FactoryTelemetry.worked(this,recipe.energy()*advancing);
        if(progress>=duration) {
            int[] plan=recipe.consumptionPlan(input);
            // Single server-thread commit, after every output slot and input count has been checked.
            if(plan!=null && output(recipe.result(),true).isEmpty()) {
                for(int s=0;s<plan.length;s++) if(plan[s]>0) items.extractItem(s,plan[s],false);
                output(recipe.result().copy(),false);
                factorycraft.diagnostics.FactoryTelemetry.produced(this);
                progress=0; activeRecipe=""; sync();
            }
        }
        return true;
    }
    public ItemStack output(ItemStack stack, boolean simulate) {
        ItemStack remainder=stack.copy();
        for(int s=4;s<8 && !remainder.isEmpty();s++) {
            var existing=items.getStackInSlot(s);
            if(!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing,remainder)) continue;
            int max=Math.min(items.getSlotLimit(s),remainder.getMaxStackSize());
            int insert=Math.min(remainder.getCount(),max-existing.getCount());
            if(insert<=0) continue;
            if(!simulate) {
                ItemStack merged=remainder.copyWithCount(insert+existing.getCount()); items.setStackInSlot(s,merged);
            }
            remainder.shrink(insert);
        }
        return remainder;
    }
    private ItemStack oreResult(BlockPos pos) {
        var block=level.getBlockState(pos);
        if(block.is(Content.IRON_ORE.get()) || block.is(BlockTags.IRON_ORES)) return new ItemStack(Items.RAW_IRON);
        if(block.is(Content.COPPER_ORE.get()) || block.is(BlockTags.COPPER_ORES)) return new ItemStack(Items.RAW_COPPER);
        if(block.is(BlockTags.COAL_ORES)) return new ItemStack(Items.COAL);
        return ItemStack.EMPTY;
    }
    private boolean mine() {
        duration=FactoryConfig.DRILL_TICKS.get();
        if(oreTarget==null || !level.hasChunkAt(oreTarget) || oreResult(oreTarget).isEmpty()) {
            oreTarget=null;
            if(level.getGameTime()%20!=0) return false;
            search: for(int y=1;y<=8;y++) for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
                var p=worldPosition.offset(x,-y,z);
                if(level.hasChunkAt(p) && !oreResult(p).isEmpty()) { oreTarget=p; break search; }
            }
        }
        if(oreTarget==null) return false;
        ItemStack result=oreResult(oreTarget);
        if(!output(result,true).isEmpty() || !power.spend(10)) return false;
        progress++; markPersistentState();
        if(progress>=duration) {
            if (level.setBlock(oreTarget,Blocks.AIR.defaultBlockState(),3)) {
                output(result,false); progress=0; oreTarget=null; sync();
            }
        }
        return true;
    }
    @Override public void setBlockState(BlockState state) {
        if(assembly!=null && assembly.formed() && getBlockState().getValue(FactoryBlock.FACING)!=state.getValue(FactoryBlock.FACING))assembly.dismantle();
        super.setBlockState(state);
    }
    @Override public void dropContents() { if(assembly!=null)assembly.dismantle();super.dropContents(); }
    @Override public void onLoad() { super.onLoad();loaded=true; if(assembly!=null)assembly.invalidate();if(!level.isClientSide) PowerGrid.add(this); }
    @Override public void setRemoved() { loaded=false;if(assembly!=null)assembly.invalidate();if(level!=null && !level.isClientSide) PowerGrid.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { loaded=false;if(assembly!=null)assembly.invalidate();if(level!=null && !level.isClientSide) PowerGrid.remove(this); super.onChunkUnloaded(); }
    @Override public Component getDisplayName() { return Component.translatable("block.factorycraft."+kind().id); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new FactoryMenu(id,inv,this,data); }
    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.saveAdditional(t,r); t.putInt("Schema",1); t.put("Items",items.serializeNBT(r)); t.putInt("Energy",power.getEnergyStored());
        t.putInt("Progress",progress); t.putInt("Duration",duration); t.putInt("Fuel",fuelTicks); t.putString("Selected",selected); t.putString("ActiveRecipe",activeRecipe);if(assembly!=null)assembly.save(t);
    }
    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.loadAdditional(t,r); items.deserializeNBT(r,t.getCompound("Items")); power.restore(t.getInt("Energy"));
        progress=Math.max(0,t.getInt("Progress")); duration=Math.max(1,t.getInt("Duration")); fuelTicks=Math.max(0,t.getInt("Fuel"));
        selected=t.getString("Selected"); activeRecipe=t.getString("ActiveRecipe"); recipeCacheTick=Long.MIN_VALUE;if(assembly!=null)assembly.load(t);
    }
}
