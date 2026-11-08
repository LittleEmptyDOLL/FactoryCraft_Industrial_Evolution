package factorycraft.petroleum;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.*;
import factorycraft.recipes.*;
import factorycraft.registry.Content;
import factorycraft.simulation.fluids.FluidCell;
import factorycraft.simulation.production.BatchInventory;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import java.util.*;

/** Composition for pumpjack and mixed processors. The controller remains the sole persistent owner. */
public final class PetroleumProduction {
    public static final int CAPACITY=4000,TRANSFER=100;
    private final MachineBlockEntity machine;
    public final List<FluidCell<FluidKey>> cells;
    private final IFluidHandler[] handlers=new IFluidHandler[7];
    private BlockPos[] nozzles;
    private Direction[] directions;
    private BlockPos deposit;
    private int progress,duration=1,selected,status,preview;
    private String active="",selectedId="";
    private long recipesAt=Long.MIN_VALUE;
    private boolean syncPending;
    private record Cached(RecipeHolder<ChemicalRecipe> holder,String signature,List<BatchInventory.Ingredient<ItemKey>> items,List<BatchInventory.Stack<ItemKey>> outputs,List<FluidKey> fluids,List<Integer> volumes) {}
    private List<Cached> recipes=List.of();
    public PetroleumProduction(MachineBlockEntity machine) {
        this.machine=machine;
        var storage=new ArrayList<FluidCell<FluidKey>>(5);
        for(int i=0;i<(machine.kind()==FactoryKind.REFINERY ? 5:4);i++)storage.add(new FluidCell<>(CAPACITY,this::changed));
        cells=List.copyOf(storage);
        for(int i=0;i<7;i++)handlers[i]=new Port(i==6 ? null:Direction.from3DDataValue(i));
    }
    public boolean ready() { return machine.petroleumAvailable() && (machine.assembly()==null || machine.assembly().ready()); }
    private boolean pump() { return machine.kind()==FactoryKind.PUMPJACK; }
    public boolean hasPortableState() {
        return progress>0 || !selectedId.isEmpty() || machine.power.getEnergyStored()>0 || cells.stream().anyMatch(c->c.amount()>0) || java.util.stream.IntStream.range(0,9).anyMatch(i->!machine.items.getStackInSlot(i).isEmpty());
    }
    public void changed() { syncPending=true;machine.markPetroleumState(); }
    public FluidStack stored(int i) { var c=cells.get(i);return c.amount()==0 ? FluidStack.EMPTY:c.key().stack(c.amount()); }
    private void positions() {
        var forward=machine.getBlockState().getValue(FactoryBlock.FACING);var right=forward.getClockWise();var anchor=machine.getBlockPos();var profile=factorycraft.multiblock.MachineProfile.of(machine.getBlockState());
        directions=new Direction[]{forward.getOpposite(),right.getOpposite(),forward,right,forward};
        nozzles=new BlockPos[]{profile.offset(anchor,forward,profile.layout.width()/2,0,profile.layout.depth()-1),profile.offset(anchor,forward,0,0,profile.layout.depth()/2),profile.offset(anchor,forward,profile.layout.width()/2,0,0),profile.offset(anchor,forward,profile.layout.width()-1,0,profile.layout.depth()/2),anchor};
    }
    public void rotated() { nozzles=null;directions=null;deposit=null; }
    public BlockPos nozzle(int index) { Objects.checkIndex(index,cells.size());if(nozzles==null)positions();return nozzles[index]; }
    public Direction direction(int index) { Objects.checkIndex(index,cells.size());if(directions==null)positions();return directions[index]; }
    private int index(BlockPos physical,Direction side) {
        if(side==null)return -1;
        for(int i=pump() ? 2:0;i<(pump() ? 3:cells.size());i++)if(side==direction(i) && physical.equals(nozzle(i)))return i;return -2;
    }
    public boolean hasPort(BlockPos pos,Direction side) { return side==null || index(pos,side)>=0; }
    public FluidCell<FluidKey> input(BlockPos pos,Direction side) { int i=index(pos,side);return ready() && i>=0 && i<2 ? cells.get(i):null; }
    public FluidCell<FluidKey> output(BlockPos pos,Direction side) { int i=index(pos,side);return ready() && i>=2 ? cells.get(i):null; }
    public IFluidHandler port(BlockPos pos,Direction side) { return pos.equals(machine.getBlockPos()) ? handlers[side==null ? 6:side.get3DDataValue()]:new PortAt(pos,side); }
    private class PortAt implements IFluidHandler {
        final BlockPos pos;final Direction side;
        PortAt(BlockPos pos,Direction side) { this.pos=pos.immutable();this.side=side; }
        private boolean accessible() { return ready() && FluidConnections.at(machine.getLevel(),pos) instanceof FactoryBlockEntity && (pos.equals(machine.getBlockPos()) || FluidConnections.at(machine.getLevel(),pos) instanceof CasingBlockEntity part && part.loaded() && part.owner()==machine); }
        public int getTanks() { return side==null ? cells.size():1; }
        private int tank(int i) { Objects.checkIndex(i,getTanks());return side==null ? i:Math.max(0,index(pos,side)); }
        public FluidStack getFluidInTank(int i) { int index=tank(i);return machine.getLevel()!=null && machine.getLevel().isClientSide || accessible() ? stored(index):FluidStack.EMPTY; }
        public int getTankCapacity(int i) { var c=cells.get(tank(i));return Math.max(c.capacity(),c.amount()); }
        public boolean isFluidValid(int i,FluidStack stack) { int index=tank(i);return accessible() && !pump() && index<2 && !stack.isEmpty() && (side==null || PetroleumProduction.this.index(pos,side)==index); }
        public int fill(FluidStack stack,FluidAction action) {
            if(!accessible() || pump() || stack.isEmpty())return 0;
            int first=side==null ? 0:index(pos,side);if(first<0 || first>1)return 0;
            var key=new FluidKey(stack);int filled=0;
            for(int i=first;i<(side==null ? 2:first+1) && filled<stack.getAmount();i++)filled+=cells.get(i).fill(key,stack.getAmount()-filled,action.simulate());
            return filled;
        }
        public FluidStack drain(FluidStack request,FluidAction action) {
            if(request.isEmpty())return FluidStack.EMPTY;return drainMatching(request.getAmount(),new FluidKey(request),action);
        }
        public FluidStack drain(int amount,FluidAction action) { return drainMatching(amount,null,action); }
        private FluidStack drainMatching(int amount,FluidKey key,FluidAction action) {
            if(!accessible() || amount<=0)return FluidStack.EMPTY;
            int first=side==null ? 0:index(pos,side);if(first<0 || side!=null && first<2)return FluidStack.EMPTY;
            for(int i=first;i<(side==null ? cells.size():first+1);i++) { var c=cells.get(i);if(c.amount()>0 && (key==null || key.equals(c.key()))) { var fluid=c.key();return fluid.stack(c.drain(amount,action.simulate())); } }
            return FluidStack.EMPTY;
        }
    }
    private final class Port extends PortAt { Port(Direction side) { super(machine.getBlockPos(),side); } }
    public void tickSync() { if(syncPending && Math.floorMod(machine.getLevel().getGameTime()+machine.getBlockPos().asLong(),5)==0) { syncPending=false;machine.syncPetroleum(); } }
    public boolean tick() {
        if(!ready())return false;
        if(!pump())for(int i=0;i<2;i++)if(cells.get(i).space()>0) {
            var from=FluidConnections.output(machine.getLevel(),nozzle(i).relative(direction(i)),direction(i).getOpposite());
            if(from!=null)from.transferTo(cells.get(i),TRANSFER,false);
        }
        if(!ready())return false;
        boolean worked=pump() ? extract():produce();
        if(ready())for(int i=2;i<(pump() ? 3:cells.size());i++)if(cells.get(i).amount()>0) {
            var to=FluidConnections.input(machine.getLevel(),nozzle(i).relative(direction(i)),direction(i).getOpposite(),cells.get(i).key());
            if(to!=null)cells.get(i).transferTo(to,TRANSFER,false);
        }
        return worked;
    }
    private boolean extract() {
        duration=1;var target=deposit==null ? null:FluidConnections.at(machine.getLevel(),deposit);
        if(!(target instanceof OilDepositBlockEntity oil) || !oil.available() || oil.remaining()==0) {
            deposit=null;if(machine.getLevel().getGameTime()%20!=0) { status=1;return false; }
            search:for(int y=1;y<=8;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
                var profile=factorycraft.multiblock.MachineProfile.PUMPJACK;var pos=profile.center(machine.getBlockPos(),machine.getBlockState().getValue(FactoryBlock.FACING)).offset(x,-y,z);var be=FluidConnections.at(machine.getLevel(),pos);
                if(be instanceof OilDepositBlockEntity oil && oil.available() && oil.remaining()>0) { deposit=pos.immutable();break search; }
            }
            target=deposit==null ? null:FluidConnections.at(machine.getLevel(),deposit);
        }
        if(!(target instanceof OilDepositBlockEntity oil) || !oil.available()) { status=1;return false; }
        int n=oil.reserve().transferTo(cells.get(2),10,true);
        if(n==0) { status=2;return false; }if(!machine.power.spend(15)) { status=3;return false; }
        oil.reserve().transferTo(cells.get(2),n,false);status=4;changed();return true;
    }
    public void refreshRecipes() {
        if(pump() || machine.getLevel()==null)return;
        long now=machine.getLevel().getGameTime();
        if(recipesAt!=Long.MIN_VALUE && now-recipesAt<20)return;
        var manager=machine.getLevel().getRecipeManager();
        var candidates=new ArrayList<RecipeHolder<ChemicalRecipe>>();
        for(var holder:manager.getAllRecipesFor(Content.CHEMISTRY.get()))
            if(holder.value().machine().equals(machine.kind().id))candidates.add(holder);
        // Tier two also executes the original dry definitions with their native IDs/gates.
        // Projection never registers duplicate recipes or changes the old assembler's data.
        if(machine.kind()==FactoryKind.ADVANCED_ASSEMBLER)
            for(var holder:manager.getAllRecipesFor(Content.PROCESSING.get())) {
                var recipe=holder.value();
                if(!recipe.machine().equals(FactoryKind.ASSEMBLER.id))continue;
                var result=recipe.result();
                // Original processing results may span several output slots.
                if(result.isEmpty() || result.getCount()>4*result.getMaxStackSize())continue;
                var outputs=new ArrayList<ItemStack>();
                for(int remaining=result.getCount();remaining>0;remaining-=result.getMaxStackSize())
                    outputs.add(result.copyWithCount(Math.min(remaining,result.getMaxStackSize())));
                candidates.add(new RecipeHolder<>(holder.id(),new ChemicalRecipe(machine.kind().id,recipe.inputs(),
                        List.of(),outputs,List.of(),recipe.ticks(),recipe.energy())));
            }
        var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,machine.getLevel().registryAccess());
        recipes=candidates.stream().sorted(Comparator.comparing(h->h.id().toString())).map(holder->{
            var recipe=holder.value();
            var outputs=recipe.results().stream().map(stack->new BatchInventory.Stack<>(new ItemKey(stack),stack.getCount(),stack.getMaxStackSize())).toList();
            var fluids=recipe.fluidResults();
            return new Cached(holder,holder.id()+"|"+ChemicalRecipe.CODEC.codec().encodeStart(ops,recipe).getOrThrow(),
                    recipe.items().stream().map(i->new BatchInventory.Ingredient<ItemKey>(key->i.ingredient().test(key.stack(1)),i.count())).toList(),
                    outputs,fluids.stream().map(FluidKey::new).toList(),fluids.stream().map(FluidStack::getAmount).toList());
        }).toList();
        recipesAt=now;selected=selectedId.isEmpty() ? 0:-1;
        for(int i=0;i<recipes.size();i++)if(recipes.get(i).holder.id().toString().equals(selectedId))selected=i+1;
    }
    private void chooseRecipe(int index) {
        selected=index;selectedId=index==0 ? "":recipes.get(index-1).holder.id().toString();progress=0;active="";changed();
    }
    public void cycleRecipe() { refreshRecipes();chooseRecipe((selected+1)%(recipes.size()+1)); }
    /** Validated ID selection for factory setup; never silently substitutes a sorted recipe. */
    public boolean selectRecipe(net.minecraft.resources.ResourceLocation id) {
        if(machine.getLevel()==null || machine.getLevel().isClientSide)return false;
        refreshRecipes();for(int i=0;i<recipes.size();i++)if(recipes.get(i).holder.id().equals(id)){chooseRecipe(i+1);return true;}return false;
    }
    public List<RecipeHolder<ChemicalRecipe>> recipes() { refreshRecipes();return recipes.stream().map(Cached::holder).toList(); }
    private List<BatchInventory.Stack<ItemKey>> inventory() {
        var result=new ArrayList<BatchInventory.Stack<ItemKey>>(8);
        for(int i=0;i<8;i++) { var s=machine.items.getStackInSlot(i);result.add(new BatchInventory.Stack<>(s.isEmpty() ? null:new ItemKey(s),s.getCount(),s.isEmpty() ? 64:s.getMaxStackSize())); }return result;
    }
    private boolean produce() {
        refreshRecipes();List<BatchInventory.Stack<ItemKey>> before=null;Cached chosen=null;List<BatchInventory.Stack<ItemKey>> itemPlan=null;boolean blocked=false,locked=false;
        boolean modular=machine.modules.enabled();var effects=modular ? machine.modules.effects():factorycraft.simulation.modules.ModuleEffects.NONE;
        int chosenRate=0;
        for(int i=0;i<recipes.size();i++) {
            var cached=recipes.get(i);if(!selectedId.isEmpty() && !selectedId.equals(cached.holder.id().toString()))continue;var recipe=cached.holder.value();boolean matches=true;
            for(int f=0;f<recipe.fluids().size();f++)if(!recipe.fluids().get(f).test(stored(f))) { matches=false;break; }
            if(!matches)continue;if(!machine.recipeAllowed(cached.holder.id())){locked=true;continue;}if(before==null)before=inventory();int rate=modular && recipe.fluidResults().isEmpty() && !recipe.results().isEmpty() && recipe.results().stream().allMatch(machine.modules::productive) ? effects.productivity():0;
            int batches=machine.modules.batches(cached.signature,rate,recipe.ticks()-(!active.equals(cached.signature) || machine.modules.identityChanged(cached.signature) ? 0:Math.min(progress,recipe.ticks()-1)),recipe.ticks());
            var outputs=batches==1 ? cached.outputs:cached.outputs.stream().map(o->new BatchInventory.Stack<>(o.key(),o.count()*batches,o.limit())).toList();
            var plan=BatchInventory.plan(before,cached.items,outputs);if(plan!=null) { chosen=cached;itemPlan=plan;chosenRate=rate;break; }else if(BatchInventory.plan(before,cached.items,List.of())!=null)blocked=true;
        }
        if(chosen==null) { status=locked ? 5:blocked ? 2:1;return false; }
        preview=recipes.indexOf(chosen)+1;var recipe=chosen.holder.value();duration=recipe.ticks();
        for(int i=0;i<chosen.fluids.size();i++)if(cells.get(i+2).space()<chosen.volumes.get(i) || !cells.get(i+2).accepts(chosen.fluids.get(i))) { status=2;return false; }
        int current=!active.equals(chosen.signature) || modular && machine.modules.identityChanged(chosen.signature) ? 0:progress;
        var step=modular ? effects.plan(recipe.energy(),1,duration-Math.min(current,duration-1),machine.modules.identityChanged(chosen.signature) ? 0:machine.modules.fraction()):null;
        if(!machine.power.spend(modular ? step.energy():recipe.energy())) { status=3;return false; }
        active=chosen.signature;progress=Math.min(current,duration-1)+(modular ? step.work():1);
        if(modular)machine.modules.advance(chosen.signature,step.remainder(),step.work(),duration,chosenRate);
        changed();status=4;
        if(progress>=duration) {
            var mutations=new ArrayList<FluidCell.Mutation<FluidKey>>(cells.size());
            for(int i=0;i<recipe.fluids().size();i++) { var c=cells.get(i);mutations.add(new FluidCell.Mutation<>(c,c.key(),c.amount()-recipe.fluids().get(i).amount())); }
            for(int i=0;i<chosen.fluids.size();i++) { var c=cells.get(i+2);mutations.add(new FluidCell.Mutation<>(c,chosen.fluids.get(i),c.amount()+chosen.volumes.get(i))); }
            var committed=itemPlan;final int productivityRate=chosenRate;
            machine.beginPetroleumInventoryTransaction();
            try {
                boolean success=FluidCell.commitBatch(mutations,()->{
                    for(int i=0;i<8;i++) { var s=committed.get(i);machine.items.setStackInSlot(i,s.count()==0 ? ItemStack.EMPTY:s.key().stack(s.count())); }
                    if(modular)machine.modules.finish();progress=0;active="";
                },false);
                if(!success)throw new IllegalStateException("Validated mixed recipe commit failed");
            } finally { machine.endPetroleumInventoryTransaction(); }
        }
        return true;
    }
    public final ContainerData data=new ContainerData() {
        public int get(int i) { return switch(i) { case 0->progress&65535;case 1->progress>>>16;case 2->duration&65535;case 3->duration>>>16;case 4->machine.power.getEnergyStored();case 5->status;case 6->selected;case 7->machine.assembly()==null ? 2:machine.assembly().status();case 8->preview;default->0; }; }
        public void set(int i,int v) {}
        public int getCount() { return 9; }
    };
    public void save(CompoundTag parent,HolderLookup.Provider r) {
        var tag=new CompoundTag();tag.putInt("Schema",cells.size()==5 ? 2:1);for(int i=0;i<cells.size();i++)tag.put("Fluid"+i,stored(i).saveOptional(r));tag.putInt("Progress",progress);tag.putInt("Duration",duration);tag.putString("SelectedRecipe",selectedId);tag.putString("Active",active);parent.put("Petroleum",tag);
    }
    public void load(CompoundTag parent,HolderLookup.Provider r) {
        var tag=parent.getCompound("Petroleum");for(int i=0;i<cells.size();i++) { var f=FluidStack.parseOptional(r,tag.getCompound("Fluid"+i));cells.get(i).restore(f.isEmpty() ? null:new FluidKey(f),f.getAmount()); }
        progress=Math.max(0,tag.getInt("Progress"));duration=Math.max(1,tag.getInt("Duration"));selectedId=tag.getString("SelectedRecipe");selected=0;active=tag.getString("Active");recipesAt=Long.MIN_VALUE;deposit=null;changed();
    }
}
