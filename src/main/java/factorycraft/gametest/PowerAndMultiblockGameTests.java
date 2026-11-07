package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blocks.*;
import factorycraft.blockentity.*;
import factorycraft.multiblock.IndustrialAssembly;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class PowerAndMultiblockGameTests {
    private static final BlockPos ORIGIN=new BlockPos(3,2,3);
    private static Item plate() { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID,"iron_plate")); }
    private static MachineBlockEntity controller(GameTestHelper h,BlockPos pos,Direction facing) {
        h.setBlock(pos,Content.FACTORY.get(FactoryKind.INDUSTRIAL_FURNACE).get().defaultBlockState().setValue(FactoryBlock.FACING,facing));
        return (MachineBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
    }
    private static void casings(GameTestHelper h,MachineBlockEntity machine) {
        for(var p:IndustrialAssembly.positions(machine.getBlockPos(),machine.getBlockState().getValue(FactoryBlock.FACING)))
            if(h.getLevel().getBlockState(p).isAir())h.getLevel().setBlockAndUpdate(p,Content.FACTORY.get(FactoryKind.CASING).get().defaultBlockState());
    }
    private static MachineBlockEntity formed(GameTestHelper h,BlockPos origin,Direction facing) {
        var machine=controller(h,origin,facing);casings(h,machine);h.assertTrue(machine.assembly().form(),"complete structure did not form");return machine;
    }
    private static int outputs(MachineBlockEntity m) { int n=0;for(int s=4;s<8;s++)n+=m.items.getStackInSlot(s).getCount();return n; }
    @GameTest(template="empty",timeoutTicks=180)
    public static void incompleteAssemblyDoesNotConsumeInputOrPower(GameTestHelper h) {
        var machine=controller(h,ORIGIN,Direction.NORTH);machine.items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,2));machine.power.restore(10000);
        h.runAfterDelay(60,()->{
            h.assertTrue(outputs(machine)==0 && machine.data.get(0)==0 && machine.power.getEnergyStored()==10000,"unformed machine spent resources");
            casings(h,machine);h.assertTrue(machine.assembly().form(),"valid structure failed to form");
        });
        h.runAfterDelay(140,()->{
            h.assertTrue(outputs(machine)==2 && machine.items.getStackInSlot(0).isEmpty(),"formed furnace did not smelt two ores");
            h.assertTrue(machine.power.getEnergyStored()==8720,"industrial furnace changed FE cost per result");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void formationIsAtomicAndOverlappingControllersCannotClaimParts(GameTestHelper h) {
        var first=controller(h,ORIGIN,Direction.NORTH);var cells=IndustrialAssembly.positions(first.getBlockPos(),Direction.NORTH);
        for(int i=0;i<cells.size()-1;i++)h.getLevel().setBlockAndUpdate(cells.get(i),Content.FACTORY.get(FactoryKind.CASING).get().defaultBlockState());
        h.assertTrue(!first.assembly().form(),"incomplete shape formed");
        for(int i=0;i<cells.size()-1;i++) {
            var part=h.getLevel().getBlockEntity(cells.get(i));
            h.assertTrue(!part.saveWithFullMetadata(h.getLevel().registryAccess()).contains("Controller"),"failed formation bound a partial structure");
        }
        h.getLevel().setBlockAndUpdate(cells.getLast(),Content.FACTORY.get(FactoryKind.CASING).get().defaultBlockState());
        h.assertTrue(first.assembly().form(),"complete shape failed to form");
        var second=controller(h,new BlockPos(5,2,4),Direction.EAST);casings(h,second);
        h.assertTrue(!second.assembly().form(),"second controller stole an occupied casing");
        h.assertTrue(first.assembly().formed(),"first structure was broken by failed overlapping formation");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void allFourAssemblyOrientationsWork(GameTestHelper h) {
        int i=0;
        for(var d:Direction.Plane.HORIZONTAL) {
            var pos=new BlockPos(3+i%2*7,2,3+i/2*7);formed(h,pos,d);i++;
        }
        h.runAfterDelay(5,()->{
            int n=0;for(var d:Direction.Plane.HORIZONTAL) {
                var m=(MachineBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3+n%2*7,2,3+n/2*7)));
                h.assertTrue(m.assembly().ready(),"rotated structure not operational: "+d);n++;
            }
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void breakingCasingKeepsInventoryAndInvalidatesCachedPorts(GameTestHelper h) {
        var machine=formed(h,ORIGIN,Direction.NORTH);machine.items.setStackInSlot(4,new ItemStack(plate(),5));
        h.runAfterDelay(5,()->{
            var pos=machine.assembly().positions().getFirst();
            var port=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,pos,Direction.UP);
            h.assertTrue(port!=null && port.extractItem(4,2,false).getCount()==2,"part did not expose controller output");
            h.getLevel().destroyBlock(pos,false);
            h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"dismantling resurrected the broken casing");
            h.assertTrue(!machine.assembly().formed() && machine.items.getStackInSlot(4).getCount()==3,"breaking part lost controller inventory");
            h.assertTrue(port.extractItem(4,64,false).isEmpty(),"cached port can still extract after destruction");
            h.getLevel().setBlockAndUpdate(pos,Content.FACTORY.get(FactoryKind.CASING).get().defaultBlockState());
            h.assertTrue(machine.assembly().form(),"repaired structure did not form");
            h.assertTrue(port.extractItem(4,64,false).isEmpty(),"old part capability became a duplicate port after repair");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void twoCasingPortsShareOneOutputAndOneEnergyBuffer(GameTestHelper h) {
        var machine=formed(h,ORIGIN,Direction.NORTH);machine.items.setStackInSlot(4,new ItemStack(plate(),5));
        h.runAfterDelay(5,()->{
            var cells=machine.assembly().positions();var a=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,cells.get(0),Direction.NORTH);
            var b=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,cells.get(1),Direction.SOUTH);
            int extracted=a.extractItem(4,4,false).getCount()+b.extractItem(4,4,false).getCount();
            h.assertTrue(extracted==5 && outputs(machine)==0,"parts duplicated their controller output");
            var energy=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,cells.get(0),Direction.UP);
            h.assertTrue(energy.receiveEnergy(1234,false)==1234 && machine.power.getEnergyStored()==1234,"casing energy is not shared");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=180)
    public static void unloadingAPartPausesProductionAndReloadResumes(GameTestHelper h) {
        var machine=formed(h,ORIGIN,Direction.NORTH);machine.items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,2));machine.power.restore(10000);
        h.runAfterDelay(8,()->{
            var part=(CasingBlockEntity)h.getLevel().getBlockEntity(machine.assembly().positions().getFirst());part.onChunkUnloaded();
            int progress=machine.data.get(0),energy=machine.power.getEnergyStored();
            h.runAfterDelay(20,()->{
                h.assertTrue(machine.data.get(0)==progress && machine.power.getEnergyStored()==energy && machine.assembly().formed(),"partial unload did not pause safely");
                part.onLoad();
            });
        });
        h.runAfterDelay(130,()->{h.assertTrue(outputs(machine)==2 && machine.power.getEnergyStored()==8720,"reload lost state or changed production cost");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void breakingControllerDropsInventoryOnceAndLeavesEmptyCasings(GameTestHelper h) {
        var machine=formed(h,ORIGIN,Direction.NORTH);machine.items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,2));machine.items.setStackInSlot(4,new ItemStack(plate(),5));
        var pos=machine.getBlockPos();var cells=machine.assembly().positions();h.getLevel().destroyBlock(pos,false);
        int dropped=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)).stream()
                .filter(e->e.getItem().is(Items.RAW_IRON) || e.getItem().is(plate())).mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(dropped==7,"controller inventory dropped more or less than once");
        for(var p:cells)h.assertTrue(h.getLevel().getBlockEntity(p) instanceof CasingBlockEntity && !h.getLevel().getBlockEntity(p).saveWithFullMetadata(h.getLevel().registryAccess()).contains("Controller"),"casing kept a destroyed owner");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=500)
    public static void insertersAutomateTheMultiblockThroughACasingPort(GameTestHelper h) {
        var machine=formed(h,ORIGIN,Direction.NORTH);
        h.setBlock(new BlockPos(6,2,4),Blocks.CHEST);h.setBlock(new BlockPos(3,2,1),Blocks.CHEST);
        var source=(ChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(6,2,4)));source.setItem(0,new ItemStack(Items.RAW_IRON,6));
        var output=(ChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3,2,1)));
        h.setBlock(new BlockPos(5,2,4),Content.FACTORY.get(FactoryKind.INSERTER).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.WEST));
        h.setBlock(new BlockPos(3,2,2),Content.FACTORY.get(FactoryKind.INSERTER).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.NORTH));
        h.setBlock(new BlockPos(6,2,2),Content.FACTORY.get(FactoryKind.GENERATOR).get().defaultBlockState());
        ((MachineBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(6,2,2)))).items.setStackInSlot(8,new ItemStack(Items.COAL));
        h.setBlock(new BlockPos(5,2,3),Content.FACTORY.get(FactoryKind.POLE).get().defaultBlockState());
        h.succeedWhen(()->{h.assertTrue(source.isEmpty() && output.getItem(0).is(plate()) && output.getItem(0).getCount()==6,"multiblock has not delivered all plates");});
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void controllerAndPartsResumeFromSerializedProgress(GameTestHelper h) {
        var machine=formed(h,ORIGIN,Direction.NORTH);machine.items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,2));machine.power.restore(10000);
        h.runAfterDelay(10,()->{
            var positions=new ArrayList<BlockPos>();positions.add(machine.getBlockPos());positions.addAll(machine.assembly().positions());
            var copies=new ArrayList<BlockEntity>();var r=h.getLevel().registryAccess();
            for(var p:positions) {
                var be=h.getLevel().getBlockEntity(p);copies.add(BlockEntity.loadStatic(p,be.getBlockState(),be.saveWithFullMetadata(r),r));
            }
            for(var p:positions)h.getLevel().removeBlockEntity(p);
            for(var be:copies)h.getLevel().setBlockEntity(be);
        });
        h.runAfterDelay(110,()->{
            var restored=(MachineBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(ORIGIN));
            h.assertTrue(restored.assembly().ready() && outputs(restored)==2 && restored.power.getEnergyStored()==8720,"serialized assembly failed to resume its original transaction");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void multiblockIdentityPersistsAcrossServerProcesses(GameTestHelper h) {
        var level=h.getLevel();var anchor=new BlockPos(111,70,95);var cells=IndustrialAssembly.positions(anchor,Direction.NORTH);
        level.getChunkAt(anchor);for(var p:cells)level.getChunkAt(p);
        if(!(level.getBlockEntity(anchor) instanceof MachineBlockEntity)) {
            level.setBlockAndUpdate(anchor,Content.FACTORY.get(FactoryKind.INDUSTRIAL_FURNACE).get().defaultBlockState());
            for(var p:cells)level.setBlockAndUpdate(p,Content.FACTORY.get(FactoryKind.CASING).get().defaultBlockState());
            var machine=(MachineBlockEntity)level.getBlockEntity(anchor);h.assertTrue(machine.assembly().form(),"disk fixture did not form");machine.items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,3));
            System.out.println("FACTORYCRAFT_MULTIBLOCK_DISK: SEEDED cross-chunk 2x2x2 assembly");
        } else {
            var machine=(MachineBlockEntity)level.getBlockEntity(anchor);
            h.assertTrue(machine.assembly()!=null && machine.assembly().formed() && machine.items.getStackInSlot(0).getCount()==3,"disk controller lost its state");
            for(var p:cells)h.assertTrue(level.getBlockEntity(p) instanceof CasingBlockEntity part && part.boundTo(anchor,machine.assembly().identity()),"disk casing lost controller identity");
            System.out.println("FACTORYCRAFT_MULTIBLOCK_DISK: VERIFIED controller and seven casing identities across four chunks");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void cachedControllerCapabilitiesPauseAcrossUnloadAndResumeAfterLoad(GameTestHelper h) {
        var machine=formed(h,ORIGIN,Direction.NORTH);machine.items.setStackInSlot(4,new ItemStack(plate(),5));machine.power.restore(1000);
        h.runAfterDelay(5,()->{
            var port=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,machine.getBlockPos(),Direction.NORTH);
            var energy=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,machine.getBlockPos(),Direction.NORTH);
            machine.onChunkUnloaded();
            var part=(CasingBlockEntity)h.getLevel().getBlockEntity(machine.assembly().positions().getFirst());
            part.onChunkUnloaded();part.onLoad();
            h.assertTrue(part.boundTo(machine.getBlockPos(),machine.assembly().identity()),"part reload during controller unload erased a valid identity");
            h.assertTrue(port.extractItem(4,5,false).isEmpty() && energy.receiveEnergy(100,false)==0,"cached controller capability can modify unloaded data");
            h.assertTrue(machine.items.getStackInSlot(4).getCount()==5 && machine.power.getEnergyStored()==1000,"unload lost state");
            machine.onLoad();h.assertTrue(port.extractItem(4,5,false).getCount()==5,"controller capability did not resume");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void aLateLoadedOrphanCasingDropsItsStaleBinding(GameTestHelper h) {
        var machine=formed(h,ORIGIN,Direction.NORTH);var pos=machine.assembly().positions().getFirst();
        var saved=h.getLevel().getBlockEntity(pos).saveWithFullMetadata(h.getLevel().registryAccess());
        h.getLevel().destroyBlock(machine.getBlockPos(),false);
        h.getLevel().removeBlockEntity(pos);
        var orphan=(CasingBlockEntity)BlockEntity.loadStatic(pos,h.getLevel().getBlockState(pos),saved,h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(orphan);
        h.runAfterDelay(5,()->{
            h.assertTrue(!orphan.saveWithFullMetadata(h.getLevel().registryAccess()).contains("Controller"),"orphan did not clear its stale controller");
            h.assertTrue(!h.getLevel().getBlockState(pos).getValue(FactoryBlock.ACTIVE),"orphan kept the formed visual state");
            h.assertTrue(orphan.inventory().getStackInSlot(0).isEmpty(),"orphan exposed an inventory");h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void sneakWrenchDismantlesEvenWithAnOccupiedOffhand(GameTestHelper h) {
        var machine=formed(h,ORIGIN,Direction.NORTH);machine.items.setStackInSlot(4,new ItemStack(plate(),5));
        h.runAfterDelay(5,()->{
            var player=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Content.WRENCH.get()));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.TORCH));player.setShiftKeyDown(true);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(machine.getBlockPos()),Direction.NORTH,machine.getBlockPos(),false);
            var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            var result=player.getMainHandItem().onItemUseFirst(context);
            h.assertTrue(result.consumesAction() && !machine.assembly().formed(),"sneak-wrench was suppressed by offhand item handling");
            h.assertTrue(machine.items.getStackInSlot(4).getCount()==5,"wrench dismantling dropped or consumed inventory");h.succeed();
        });
    }

}
