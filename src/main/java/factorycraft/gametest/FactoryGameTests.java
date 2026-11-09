package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blocks.*;
import factorycraft.blockentity.*;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class FactoryGameTests {
    private static BlockPos p(int x,int z){return new BlockPos(x,2,z);}
    private static <T extends BlockEntity> T place(GameTestHelper h,FactoryKind k,BlockPos pos,Direction facing){
        factorycraft.multiblock.StructurePlacement.place(h.getLevel(),h.absolutePos(pos),Content.FACTORY.get(k).get().defaultBlockState().setValue(FactoryBlock.FACING,facing));
        return (T)h.getLevel().getBlockEntity(h.absolutePos(pos));
    }
    private static int count(BeltBlockEntity be){return be.lane(0).carriers().size()+be.lane(1).carriers().size();}
    private static int outputs(MachineBlockEntity be){int n=0;for(int s=4;s<8;s++)n+=be.items.getStackInSlot(s).getCount();return n;}
    private static Item item(String name){return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID,name));}
    private static void seed(BeltBlockEntity be,int n){for(int i=0;i<n;i++)be.lane(i%2).restore(new ItemStack(Items.RAW_IRON),1-(i/2)*.25);be.sync();factorycraft.simulation.BeltSimulation.activate(be); }

    @GameTest(template="empty",timeoutTicks=160)
    public static void beltsTurnAndMergeWithoutDuplication(GameTestHelper h){
        BeltBlockEntity a=place(h,FactoryKind.BELT,p(2,2),Direction.EAST);
        BeltBlockEntity b=place(h,FactoryKind.BELT,p(3,2),Direction.SOUTH);
        BeltBlockEntity c=place(h,FactoryKind.BELT,p(3,3),Direction.SOUTH);
        seed(a,8);
        h.runAfterDelay(100,()->{
            h.assertTrue(count(a)+count(b)+count(c)==8,"turn/merge lost or duplicated items");
            h.assertTrue(count(c)>0,"belt did not move items around corner");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void splitterBalancesAndConserves(GameTestHelper h){
        BeltBlockEntity a=place(h,FactoryKind.SPLITTER,p(3,3),Direction.EAST);
        BeltBlockEntity forward=place(h,FactoryKind.BELT,p(4,3),Direction.EAST);
        BeltBlockEntity right=place(h,FactoryKind.BELT,p(4,4),Direction.EAST);
        seed(a,8);
        h.runAfterDelay(100,()->{
            h.assertTrue(count(a)==0 && count(forward)==4 && count(right)==4,"splitter must deliver 4 items to each output: splitter="+count(a)+", first="+count(forward)+", second="+count(right));h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void inserterTransfersBetweenVanillaChests(GameTestHelper h){
        h.setBlock(p(1,2),Blocks.CHEST);h.setBlock(p(3,2),Blocks.CHEST);
        place(h,FactoryKind.INSERTER,p(2,2),Direction.EAST);
        var source=(ChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p(1,2)));
        var dest=(ChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p(3,2)));
        source.setItem(0,new ItemStack(Items.IRON_INGOT,8));
        h.succeedWhen(()->{h.assertTrue(source.isEmpty(),"source not yet empty");h.assertTrue(dest.getItem(0).getCount()==8,"destination count differs");});
    }
    @GameTest(template="empty",timeoutTicks=260)
    public static void machineStopsWithoutPowerThenProduces(GameTestHelper h){
        MachineBlockEntity m=place(h,FactoryKind.FURNACE,p(2,2),Direction.EAST);
        m.items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,2));
        h.runAfterDelay(80,()->{
            h.assertTrue(outputs(m)==0 && m.data.get(0)==0,"unpowered machine advanced");
            int preview=m.previewIndex();h.assertTrue(preview>0 && m.recipes().get(preview-1).value().result().is(item("iron_plate")),"server recipe preview does not match input");
            m.power.receiveEnergy(16000,false);
        });
        h.runAfterDelay(220,()->{
            h.assertTrue(outputs(m)==2 && m.items.getStackInSlot(0).isEmpty(),"recipe conservation failed");
            h.assertTrue(m.items.getStackInSlot(4).is(item("iron_plate")),"wrong product");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void blockedOutputDoesNotConsumeInputOrEnergy(GameTestHelper h){
        MachineBlockEntity m=place(h,FactoryKind.ASSEMBLER,p(2,2),Direction.EAST);
        m.items.setStackInSlot(0,new ItemStack(item("iron_plate"),4));
        for(int s=4;s<8;s++)m.items.setStackInSlot(s,new ItemStack(Items.COBBLESTONE,64));
        m.power.receiveEnergy(1000,false);
        h.runAfterDelay(80,()->{h.assertTrue(m.items.getStackInSlot(0).getCount()==4 && m.power.getEnergyStored()==1000,"blocked machine spent resources");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=240)
    public static void poleGraphSplitsAndRejoins(GameTestHelper h){
        MachineBlockEntity g=place(h,FactoryKind.GENERATOR,p(1,5),Direction.NORTH);
        place(h,FactoryKind.POLE,p(2,3),Direction.EAST);
        place(h,FactoryKind.POLE,p(7,3),Direction.EAST);
        place(h,FactoryKind.POLE,p(12,3),Direction.EAST);
        MachineBlockEntity m=place(h,FactoryKind.FURNACE,p(14,2),Direction.NORTH);
        g.items.setStackInSlot(8,new ItemStack(Items.COAL,2));
        h.runAfterDelay(40,()->{
            h.assertTrue(m.power.getEnergyStored()>0,"connected network did not deliver energy");
            h.setBlock(p(7,3),Blocks.AIR);m.power.restore(0);
        });
        h.runAfterDelay(80,()->{h.assertTrue(m.power.getEnergyStored()==0,"split network still connected");place(h,FactoryKind.POLE,p(7,3),Direction.EAST);});
        h.runAfterDelay(120,()->{h.assertTrue(m.power.getEnergyStored()>0,"network did not reconnect");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void persistedCarriersMachineAndHandRoundTrip(GameTestHelper h){
        BeltBlockEntity belt=place(h,FactoryKind.BELT,p(2,2),Direction.NORTH);seed(belt,8);
        MachineBlockEntity machine=place(h,FactoryKind.ASSEMBLER,p(4,2),Direction.NORTH);
        machine.items.setStackInSlot(0,new ItemStack(item("iron_plate"),3));machine.power.restore(777);
        InserterBlockEntity hand=place(h,FactoryKind.INSERTER,p(8,2),Direction.NORTH);
        hand.hand.setStackInSlot(0,new ItemStack(Items.DIAMOND));
        var r=h.getLevel().registryAccess();
        CompoundTag a=belt.saveWithFullMetadata(r),b=machine.saveWithFullMetadata(r),c=hand.saveWithFullMetadata(r);
        var beltCopy=(BeltBlockEntity)BlockEntity.loadStatic(belt.getBlockPos(),belt.getBlockState(),a,r);
        var machineCopy=(MachineBlockEntity)BlockEntity.loadStatic(machine.getBlockPos(),machine.getBlockState(),b,r);
        var handCopy=(InserterBlockEntity)BlockEntity.loadStatic(hand.getBlockPos(),hand.getBlockState(),c,r);
        h.assertTrue(count(beltCopy)==8 && beltCopy.lane(0).carriers().get(1).position()==.75,"carrier NBT mismatch");
        h.assertTrue(machineCopy.power.getEnergyStored()==777 && machineCopy.items.getStackInSlot(0).getCount()==3,"machine NBT mismatch");
        h.assertTrue(handCopy.held().is(Items.DIAMOND),"held item not persisted");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=1600)
    public static void firstFactoryMinesSmeltsAssemblesIntoChest(GameTestHelper h){
        var origin=h.absolutePos(new BlockPos(1,2,1));
        var chest=factorycraft.core.FactoryCommands.ironLine(h.getLevel(),origin,false);
        h.succeedWhen(()->h.assertTrue(chest.getItem(0).is(item("iron_gear")) && chest.getItem(0).getCount()>=3,"full-size factory has not delivered three gears"));
    }
    @GameTest(template="empty",timeoutTicks=180)
    public static void sleepingJammedBeltWakesAfterExtraction(GameTestHelper h) {
        BeltBlockEntity source=place(h,FactoryKind.BELT,p(2,2),Direction.EAST);
        BeltBlockEntity target=place(h,FactoryKind.BELT,p(3,2),Direction.EAST);
        seed(source,8);seed(target,8);
        h.runAfterDelay(30,()->{
            h.assertTrue(!source.isScheduled() && !target.isScheduled(),"jammed belts must sleep");
            target.inventory().extractItem(0,1,false);
        });
        h.runAfterDelay(100,()->{h.assertTrue(count(source)==7 && count(target)==8,"extraction failed to wake upstream");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void chunkUnloadCallbacksKeepInventoryAndResume(GameTestHelper h) {
        BeltBlockEntity belt=place(h,FactoryKind.BELT,p(2,2),Direction.EAST);seed(belt,8);
        MachineBlockEntity m=place(h,FactoryKind.ASSEMBLER,p(7,2),Direction.NORTH);m.items.setStackInSlot(0,new ItemStack(Items.DIAMOND,9));
        belt.onChunkUnloaded();m.onChunkUnloaded();
        h.assertTrue(count(belt)==8 && m.items.getStackInSlot(0).getCount()==9,"unload callback destroyed contents");
        belt.onLoad();m.onLoad();
        h.assertTrue(belt.simulationLoaded,"reload did not reactivate belt");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void breakingFullBeltDropsEveryCarrier(GameTestHelper h) {
        BlockPos pos=p(2,2);BeltBlockEntity belt=place(h,FactoryKind.BELT,pos,Direction.EAST);seed(belt,8);
        var absolute=h.absolutePos(pos);h.getLevel().destroyBlock(absolute,false);
        int dropped=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(absolute).inflate(2))
                .stream().filter(e->e.getItem().is(Items.RAW_IRON)).mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(dropped==8,"breaking belt lost or duplicated carriers");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void overlappingIngredientsCannotDoubleSpend(GameTestHelper h) {
        MachineBlockEntity m=place(h,FactoryKind.ASSEMBLER,p(2,2),Direction.EAST);
        m.items.setStackInSlot(0,new ItemStack(item("iron_plate"),3));
        var ingredient=new factorycraft.recipes.FactoryRecipe.CountedIngredient(net.minecraft.world.item.crafting.Ingredient.of(item("iron_plate")),2);
        var recipe=new factorycraft.recipes.FactoryRecipe("assembling_machine",List.of(ingredient,ingredient),new ItemStack(item("iron_gear")),10,8);
        h.assertTrue(recipe.consumptionPlan(new factorycraft.recipes.FactoryInput(m.items))==null,"shared slot was spent twice");
        m.items.setStackInSlot(1,new ItemStack(item("iron_plate"),1));
        var plan=recipe.consumptionPlan(new factorycraft.recipes.FactoryInput(m.items));
        h.assertTrue(plan!=null && plan[0]==3 && plan[1]==1,"count allocation did not reserve all inputs");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void sharedMenusCannotWithdrawSameOutputTwice(GameTestHelper h) {
        MachineBlockEntity m=place(h,FactoryKind.ASSEMBLER,p(2,2),Direction.EAST);
        m.items.setStackInSlot(4,new ItemStack(item("iron_gear"),1));
        var a=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);var b=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        m.assembly().positions().forEach(pos->h.getLevel().getBlockEntity(pos).onLoad());m.onLoad();
        var first=new factorycraft.production.FactoryMenu(1,a.getInventory(),m,m.data);
        var second=new factorycraft.production.FactoryMenu(2,b.getInventory(),m,m.data);
        a.containerMenu=first;b.containerMenu=second;a.setPos(m.getBlockPos().getX(),m.getBlockPos().getY(),m.getBlockPos().getZ());b.setPos(a.getX(),a.getY(),a.getZ());
        first.quickMoveStack(a,4);second.quickMoveStack(b,4);
        int count=0;for(int i=0;i<a.getInventory().getContainerSize();i++) {
            if(a.getInventory().getItem(i).is(item("iron_gear")))count+=a.getInventory().getItem(i).getCount();
            if(b.getInventory().getItem(i).is(item("iron_gear")))count+=b.getInventory().getItem(i).getCount();
        }
        h.assertTrue(count==1 && m.items.getStackInSlot(4).isEmpty(),"two viewers duplicated an output");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=180)
    public static void mixedTierJunctionsMaintainCrossBlockSpacing(GameTestHelper h) {
        BeltBlockEntity a=place(h,FactoryKind.EXPRESS_BELT,p(2,2),Direction.EAST);
        BeltBlockEntity b=place(h,FactoryKind.BELT,p(3,2),Direction.EAST);
        BeltBlockEntity c=place(h,FactoryKind.FAST_BELT,p(4,2),Direction.EAST);
        int[] created={0};
        h.onEachTick(()->{
            for(int lane=0;lane<2;lane++)if(a.inventory().insertItem(lane*4,new ItemStack(Items.RAW_IRON),false).isEmpty())created[0]++;
            for(int lane=0;lane<2;lane++) {
                assertJunction(h,a,b,lane);assertJunction(h,b,c,lane);
            }
            h.assertTrue(count(a)+count(b)+count(c)==created[0],"mixed tier junction lost or duplicated items");
        });
        h.runAfterDelay(120,()->{h.assertTrue(count(c)>0,"flow never reached the final belt");h.succeed();});
    }
    private static void assertJunction(GameTestHelper h,BeltBlockEntity a,BeltBlockEntity b,int lane) {
        var first=a.lane(lane).carriers();var second=b.lane(lane).carriers();
        if(!first.isEmpty() && !second.isEmpty())h.assertTrue(96+second.getLast().positionUnits()-first.getFirst().positionUnits()>=24,"items overlap across the block boundary");
    }
    @GameTest(template="empty",timeoutTicks=180)
    public static void rotatedBlockStateInvalidatesCachedBeltConnections(GameTestHelper h) {
        BeltBlockEntity a=place(h,FactoryKind.BELT,p(2,2),Direction.EAST);
        BeltBlockEntity b=place(h,FactoryKind.BELT,p(3,2),Direction.WEST);seed(a,2);
        h.runAfterDelay(20,()->{
            h.assertTrue(count(a)==2 && count(b)==0,"opposing belts must not exchange items");
            var absolute=h.absolutePos(p(3,2));
            h.getLevel().setBlockAndUpdate(absolute,b.getBlockState().setValue(FactoryBlock.FACING,Direction.EAST));
        });
        h.runAfterDelay(60,()->{
            h.assertTrue(count(a)==0 && count(b)==2,"cached connection ignored target rotation");
            place(h,FactoryKind.BELT,p(2,3),Direction.SOUTH);
            h.getLevel().setBlockAndUpdate(a.getBlockPos(),a.getBlockState().setValue(FactoryBlock.FACING,Direction.SOUTH));
            seed(a,2);
        });
        h.runAfterDelay(110,()->{
            var c=(BeltBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p(2,3)));
            h.assertTrue(count(a)==0 && count(c)==2 && count(b)==2,"cached source rotation lost the new output");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void schemaOnePositionsAndSplitterPreferenceRemainReadable(GameTestHelper h) {
        BeltBlockEntity original=place(h,FactoryKind.SPLITTER,p(2,2),Direction.NORTH);seed(original,8);
        var r=h.getLevel().registryAccess();var legacy=original.saveWithFullMetadata(r);
        legacy.putInt("Schema",1);legacy.putBoolean("Alternate",true);legacy.remove("AlternateRight");
        for(int lane=0;lane<2;lane++) {
            var list=legacy.getList("Lane"+lane,net.minecraft.nbt.Tag.TAG_COMPOUND);
            for(int i=0;i<list.size();i++)list.getCompound(i).remove("Units");
        }
        var restored=(BeltBlockEntity)BlockEntity.loadStatic(original.getBlockPos(),original.getBlockState(),legacy,r);
        var modern=restored.saveWithFullMetadata(r);
        h.assertTrue(count(restored)==8 && restored.lane(1).carriers().getLast().positionUnits()==24,"legacy lane migration changed contents");
        h.assertTrue(modern.getInt("Schema")==3 && modern.getBoolean("Alternate") && modern.getBoolean("AlternateRight"),"legacy splitter preference not migrated");
        h.succeed();
    }

}
