package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.resources.ResourceKind;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class ResourceGameTests {
    private static ResourceDepositBlockEntity deposit(GameTestHelper h,ResourceKind kind,BlockPos pos,int units) {
        h.setBlock(pos,Content.RESOURCES.get(kind).get());var be=(ResourceDepositBlockEntity)h.getBlockEntity(pos);be.initializeReserve(units);return be;
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void survivalMiningConsumesOneUnitAndLeavesTileUntilExhausted(GameTestHelper h) {
        var ore=deposit(h,ResourceKind.IRON,new BlockPos(3,2,3),3);
        h.runAfterDelay(5,()->{
            var player=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.fromString("4d2a1370-fb8b-422d-b41d-c811f35cae36"),"ResourceMiner"));
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
            for(int i=1;i<=3;i++) {
                player.gameMode.destroyBlock(ore.getBlockPos());int count=0;for(var stack:player.getInventory().items)if(stack.is(Items.RAW_IRON))count+=stack.getCount();
                h.assertTrue(count==i && ore.remaining()==3-i,"manual mining did not exchange exactly one unit");
                h.assertTrue(h.getLevel().getBlockState(ore.getBlockPos()).isAir()==(i==3),"tile removed before exhaustion or left after exhaustion");
            }
            player.getInventory().clearContent();h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void fullInventoryAndWrongToolCannotDeleteReserve(GameTestHelper h) {
        var ore=deposit(h,ResourceKind.COPPER,new BlockPos(3,2,3),7);
        h.runAfterDelay(5,()->{
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.getAbilities().instabuild=false;
            for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Items.DIAMOND,64));
            var state=ore.getBlockState();state.onDestroyedByPlayer(h.getLevel(),ore.getBlockPos(),player,true,state.getFluidState());
            h.assertTrue(ore.remaining()==7,"full inventory consumed stock");player.getInventory().clearContent();
            state.onDestroyedByPlayer(h.getLevel(),ore.getBlockPos(),player,false,state.getFluidState());h.assertTrue(ore.remaining()==7,"wrong tool consumed stock");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=260)
    public static void drillExtractsSeveralUnitsFromOneTileThenStopsAtZero(GameTestHelper h) {
        var ore=deposit(h,ResourceKind.COPPER,new BlockPos(4,1,4),3);
        StructurePlacement.place(h.getLevel(),h.absolutePos(new BlockPos(3,2,3)),Content.FACTORY.get(FactoryKind.DRILL).get().defaultBlockState());
        var drill=(MachineBlockEntity)h.getBlockEntity(new BlockPos(3,2,3));drill.power.restore(10000);
        h.runAfterDelay(230,()->{int total=0;for(int slot=4;slot<8;slot++)if(drill.items.getStackInSlot(slot).is(Items.RAW_COPPER))total+=drill.items.getStackInSlot(slot).getCount();
            h.assertTrue(total==3 && h.getLevel().getBlockState(ore.getBlockPos()).isAir() && drill.power.getEnergyStored()==8200,"finite drill output/stock/energy ledger failed");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=180)
    public static void blockedDrillAndMissingPowerDoNotSpendOre(GameTestHelper h) {
        var ore=deposit(h,ResourceKind.IRON,new BlockPos(4,1,4),9);StructurePlacement.place(h.getLevel(),h.absolutePos(new BlockPos(3,2,3)),Content.FACTORY.get(FactoryKind.DRILL).get().defaultBlockState());
        var drill=(MachineBlockEntity)h.getBlockEntity(new BlockPos(3,2,3));
        h.runAfterDelay(50,()->{h.assertTrue(ore.remaining()==9,"unpowered drill consumed stock");for(int i=4;i<8;i++)drill.items.setStackInSlot(i,new ItemStack(Items.COBBLESTONE,64));drill.power.restore(10000);});
        h.runAfterDelay(140,()->{h.assertTrue(ore.remaining()==9 && drill.power.getEnergyStored()==10000,"blocked drill spent ore or FE");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeOreNbtAndUnloadKeepExactFiniteQuantity(GameTestHelper h) {
        var ore=deposit(h,ResourceKind.COAL,new BlockPos(3,2,3),17);
        h.runAfterDelay(5,()->{ore.extractAccepted();var r=h.getLevel().registryAccess();var tag=ore.saveWithFullMetadata(r);var copy=(ResourceDepositBlockEntity)BlockEntity.loadStatic(ore.getBlockPos(),ore.getBlockState(),tag,r);
            h.assertTrue(copy.remaining()==16,"NBT lost finite stock");ore.onChunkUnloaded();h.assertTrue(!ore.extractAccepted() && ore.remaining()==16,"unloaded ore remained mutable");ore.onLoad();h.assertTrue(ore.extractAccepted() && ore.remaining()==15,"ore did not resume");
            tag.putInt("Remaining",0);copy=(ResourceDepositBlockEntity)BlockEntity.loadStatic(ore.getBlockPos(),ore.getBlockState(),tag,r);h.assertTrue(copy.remaining()==0,"zero reserve regenerated");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void resourceWorldgenCreatesBlockEntitiesAndNoRegeneratingItems(GameTestHelper h) {
        VeinGameTests.configuredResources(h);
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void fullSizeAssemblyAndFiniteOrePersistAcrossServerProcesses(GameTestHelper h) {
        var level=h.getLevel();var anchor=new BlockPos(351,70,351);var orePos=anchor.offset(1,-1,1);
        for(var pos:MachineProfile.DRILL.cells(anchor,Direction.NORTH))level.getChunkAt(pos);level.getChunkAt(orePos);
        if(level.getBlockEntity(anchor)==null && level.getBlockEntity(orePos)==null) {
            StructurePlacement.place(level,anchor,Content.FACTORY.get(FactoryKind.DRILL).get().defaultBlockState());level.setBlockAndUpdate(orePos,Content.IRON_ORE.get().defaultBlockState());
            var ore=(ResourceDepositBlockEntity)level.getBlockEntity(orePos);ore.initializeReserve(137);var drill=(MachineBlockEntity)level.getBlockEntity(anchor);drill.items.setStackInSlot(4,new ItemStack(Items.RAW_IRON,3));drill.power.restore(1234);drill.data.set(0,17);drill.setChanged();
            System.out.println("FACTORYCRAFT_RESOURCE_DISK: SEEDED 137 units and cross-chunk 3x3 drill");
        } else {
            h.assertTrue(level.getBlockEntity(anchor) instanceof MachineBlockEntity && level.getBlockEntity(orePos) instanceof ResourceDepositBlockEntity,"partial disk fixture; refusing to reseed");
            var drill=(MachineBlockEntity)level.getBlockEntity(anchor);h.assertTrue(((ResourceDepositBlockEntity)level.getBlockEntity(orePos)).remaining()==137 && drill.power.getEnergyStored()==1234 && drill.items.getStackInSlot(4).getCount()==3 && drill.data.get(0)==17,"disk changed resources or progress");
            for(var pos:drill.assembly().positions())h.assertTrue(level.getBlockEntity(pos) instanceof CasingBlockEntity part && part.boundTo(anchor,drill.assembly().identity()),"cross-chunk identity lost");
            System.out.println("FACTORYCRAFT_RESOURCE_DISK: VERIFIED exact finite stock, FE, progress, output and cross-chunk identity");
        }
        for(var pos:MachineProfile.DRILL.cells(anchor,Direction.NORTH)){var be=level.getBlockEntity(pos);if(be!=null)be.onChunkUnloaded();level.getChunkAt(pos).setUnsaved(true);}
        ((ResourceDepositBlockEntity)level.getBlockEntity(orePos)).onChunkUnloaded();level.getChunkAt(orePos).setUnsaved(true);h.succeed();
    }
}
