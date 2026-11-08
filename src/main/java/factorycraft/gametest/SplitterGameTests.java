package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class SplitterGameTests {
    private static BeltBlockEntity put(GameTestHelper h,FactoryKind kind,int x,int z) {
        var pos=h.absolutePos(new BlockPos(x,2,z));StructurePlacement.place(h.getLevel(),pos,Content.FACTORY.get(kind).get().defaultBlockState());return (BeltBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static int count(BeltBlockEntity be) {int n=0;for(int i=0;i<be.laneCount();i++)n+=be.lane(i).carriers().size();return n;}
    private static void seed(BeltBlockEntity be,Item item) {for(int l=0;l<be.laneCount();l++)for(int i=0;i<4;i++)be.lane(l).restore(new ItemStack(item),1-i*.25);be.setChanged();be.wakeTransport();}
    @GameTest(template="empty",timeoutTicks=180)
    public static void bothPhysicalInputsReachBothOutputsWithoutDuplication(GameTestHelper h) {
        var splitter=put(h,FactoryKind.SPLITTER,5,5);var a=put(h,FactoryKind.BELT,5,6);var b=put(h,FactoryKind.BELT,6,6);var x=put(h,FactoryKind.BELT,5,4);var y=put(h,FactoryKind.BELT,6,4);
        seed(a,Items.RAW_IRON);seed(b,Items.RAW_COPPER);
        h.runAfterDelay(120,()->{
            h.assertTrue(splitter.laneCount()==4 && count(a)==0 && count(b)==0 && count(splitter)==0 && count(x)==8 && count(y)==8,"two-track splitter lost or failed to balance input");
            for(var out:List.of(x,y))for(int lane=0;lane<2;lane++)h.assertTrue(out.lane(lane).carriers().stream().anyMatch(c->c.value.is(Items.RAW_IRON)) && out.lane(lane).carriers().stream().anyMatch(c->c.value.is(Items.RAW_COPPER)),"one physical input starved");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void inserterCapabilitiesAddressOnlyTheirPhysicalTrack(GameTestHelper h) {
        var splitter=put(h,FactoryKind.SPLITTER,5,5);
        h.runAfterDelay(5,()->{
            var a=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,splitter.getBlockPos(),Direction.UP);var b=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,splitter.trackPosition(1),Direction.UP);
            h.assertTrue(a!=null && b!=null && a.getSlots()==8 && b.getSlots()==8,"missing track ports");a.insertItem(0,new ItemStack(Items.DIAMOND),false);b.insertItem(0,new ItemStack(Items.EMERALD),false);
            h.assertTrue(a.getStackInSlot(0).is(Items.DIAMOND) && b.getStackInSlot(0).is(Items.EMERALD) && splitter.lane(0).carriers().size()==1 && splitter.lane(2).carriers().size()==1,"physical ports alias another track");
            var part=(CasingBlockEntity)h.getLevel().getBlockEntity(splitter.trackPosition(1));part.onChunkUnloaded();var tag=splitter.saveWithFullMetadata(h.getLevel().registryAccess());
            h.assertTrue(a.extractItem(0,1,false).isEmpty() && b.extractItem(0,1,false).isEmpty(),"cached port extracted from unavailable assembly");
            h.runAfterDelay(30,()->{var now=splitter.saveWithFullMetadata(h.getLevel().registryAccess());h.assertTrue(now.getList("Lane0",10).equals(tag.getList("Lane0",10)) && now.getList("Lane2",10).equals(tag.getList("Lane2",10)),"unloaded tracks advanced");part.onLoad();h.assertTrue(b.extractItem(0,1,false).is(Items.EMERALD) && a.extractItem(0,1,false).is(Items.DIAMOND),"ports failed to resume");h.succeed();});
        });
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void blockedOutputKeepsAllFourLanesAndFallsBackToFreeOutput(GameTestHelper h) {
        var splitter=put(h,FactoryKind.SPLITTER,5,5);var blocked=put(h,FactoryKind.BELT,5,4);var free=put(h,FactoryKind.BELT,6,4);seed(blocked,Items.COBBLESTONE);seed(splitter,Items.RAW_IRON);
        h.runAfterDelay(100,()->{h.assertTrue(count(blocked)==8 && count(free)==8 && count(splitter)==8,"blocked route lost items or failed fallback");for(int lane=0;lane<2;lane++)for(int n=0;n<4;n++)blocked.inventory().extractItem(lane*4,1,false);});
        h.runAfterDelay(140,()->{h.assertTrue(count(blocked)==8 && count(free)==8 && count(splitter)==0,"unblocking did not resume all four lanes");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void oldTwoLaneDataMigratesAndFourLanePositionsRoundTrip(GameTestHelper h) {
        var split=put(h,FactoryKind.SPLITTER,5,5);seed(split,Items.RAW_COPPER);var r=h.getLevel().registryAccess();var tag=split.saveWithFullMetadata(r);
        var copy=(BeltBlockEntity)BlockEntity.loadStatic(split.getBlockPos(),split.getBlockState(),tag,r);h.assertTrue(count(copy)==16 && copy.lane(3).carriers().getLast().positionUnits()==24,"new lanes lost positions");for(int lane=0;lane<4;lane++)h.assertTrue(tag.getList("Lane"+lane,10).equals(copy.saveWithFullMetadata(r).getList("Lane"+lane,10)),"lane did not round-trip every stack/coordinate");
        tag.putInt("Schema",2);tag.remove("Lane2");tag.remove("Lane3");tag.remove("Alternate2");tag.remove("Alternate3");copy=(BeltBlockEntity)BlockEntity.loadStatic(split.getBlockPos(),split.getBlockState(),tag,r);
        h.assertTrue(count(copy)==8 && copy.lane(2).carriers().isEmpty() && copy.lane(3).carriers().isEmpty() && copy.saveWithFullMetadata(r).getInt("Schema")==3,"legacy splitter lost or duplicated carriers");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void cachedOrdinaryBeltPortCannotExtractAfterChunkUnload(GameTestHelper h) {
        var belt=put(h,FactoryKind.BELT,5,5);seed(belt,Items.DIAMOND);
        h.runAfterDelay(5,()->{var port=belt.inventory();belt.onChunkUnloaded();h.assertTrue(port.extractItem(0,1,false).isEmpty() && count(belt)==8,"unloaded belt retained mutable cached port");belt.onLoad();h.assertTrue(port.extractItem(0,1,false).is(Items.DIAMOND) && count(belt)==7,"belt port did not resume");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void actualInserterSamplesThePartTrackAtItsPickupPosition(GameTestHelper h) {
        var split=put(h,FactoryKind.SPLITTER,5,5);
        var armPos=split.trackPosition(1).east();h.getLevel().setBlockAndUpdate(armPos,Content.FACTORY.get(FactoryKind.INSERTER).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.EAST));
        var chestPos=armPos.east();h.getLevel().setBlockAndUpdate(chestPos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        h.runAfterDelay(5,()->{
            split.lane(0).restore(new ItemStack(Items.DIAMOND),1);split.lane(2).restore(new ItemStack(Items.EMERALD),.25);split.wakeTransport();
            var arm=(InserterBlockEntity)h.getLevel().getBlockEntity(armPos);arm.tick();h.assertTrue(arm.held().isEmpty(),"arm picked behind the part track pickup position");
        });
        h.runAfterDelay(65,()->{
            var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)h.getLevel().getBlockEntity(chestPos);int emeralds=0,diamonds=0;
            for(int i=0;i<chest.getContainerSize();i++){var item=chest.getItem(i);if(item.is(Items.EMERALD))emeralds+=item.getCount();if(item.is(Items.DIAMOND))diamonds+=item.getCount();}
            h.assertTrue(emeralds==1 && diamonds==0 && split.lane(0).carriers().size()==1 && split.lane(2).carriers().isEmpty(),"arm sampled another physical track or lost its cargo");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void portableSplitterRetainsSixteenCarriersAndBindsFreshParts(GameTestHelper h) {
        var split=put(h,FactoryKind.SPLITTER,5,5);
        h.runAfterDelay(5,()->{
            seed(split,Items.DIAMOND);var old=split.assembly().identity();h.getLevel().destroyBlock(split.trackPosition(1),true);
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(split.getBlockPos()).inflate(3));
            h.assertTrue(drops.size()==1 && drops.getFirst().getItem().is(Content.FACTORY.get(FactoryKind.SPLITTER).get().asItem()) && drops.getFirst().getItem().getCount()==1,"demolition copied or lost splitter cargo");
            var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setYRot(Direction.NORTH.toYRot());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,drops.getFirst().getItem().copy());drops.getFirst().discard();
            var at=h.absolutePos(new BlockPos(10,2,5));h.assertTrue(PlacementTestSupport.place(player,at).consumesAction() && player.getMainHandItem().isEmpty(),"portable splitter failed placement");
            var restored=(BeltBlockEntity)h.getLevel().getBlockEntity(at);h.assertTrue(count(restored)==16 && restored.lane(3).carriers().getLast().positionUnits()==24 && !old.equals(restored.assembly().identity()),"portable state lost positions or reused old identity");
            var part=(CasingBlockEntity)h.getLevel().getBlockEntity(restored.trackPosition(1));h.assertTrue(part.boundTo(at,restored.assembly().identity()),"portable data orphaned freshly generated part");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void fourLaneSplitterPersistsAcrossServerProcesses(GameTestHelper h) {
        var level=h.getLevel();var pos=new BlockPos(415,70,415);for(var cell:MachineProfile.SPLITTER.cells(pos,Direction.NORTH))level.getChunkAt(cell);
        if(level.getBlockEntity(pos)==null) {StructurePlacement.place(level,pos,Content.FACTORY.get(FactoryKind.SPLITTER).get().defaultBlockState());seed((BeltBlockEntity)level.getBlockEntity(pos),Items.DIAMOND);System.out.println("FACTORYCRAFT_SPLITTER_DISK: SEEDED sixteen carriers in four physical lanes");}
        else {var be=(BeltBlockEntity)level.getBlockEntity(pos);h.assertTrue(count(be)==16 && be.lane(2).carriers().size()==4 && be.lane(3).carriers().getLast().positionUnits()==24,"disk lost four-lane state");System.out.println("FACTORYCRAFT_SPLITTER_DISK: VERIFIED four lanes, positions and part identity");}
        var be=(BeltBlockEntity)level.getBlockEntity(pos);for(int lane=0;lane<4;lane++)for(int i=0;i<4;i++)h.assertTrue(be.lane(lane).carriers().get(i).value.is(Items.DIAMOND) && be.lane(lane).carriers().get(i).positionUnits()==96-i*24,"disk changed a carrier or its exact coordinate");var part=(CasingBlockEntity)level.getBlockEntity(be.trackPosition(1));h.assertTrue(part!=null && part.boundTo(pos,be.assembly().identity()),"disk part identity differs from controller");for(var cell:MachineProfile.SPLITTER.cells(pos,Direction.NORTH)) {var entity=level.getBlockEntity(cell);if(entity!=null)entity.onChunkUnloaded();level.getChunkAt(cell).setUnsaved(true);}h.succeed();
    }
}
