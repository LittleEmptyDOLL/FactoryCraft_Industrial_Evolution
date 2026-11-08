package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.*;
import factorycraft.registry.Content;
import factorycraft.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class ThermalGameTests {
    private static final IFluidHandler.FluidAction EXEC=IFluidHandler.FluidAction.EXECUTE,SIM=IFluidHandler.FluidAction.SIMULATE;
    private static ThermalBlockEntity put(GameTestHelper h,ThermalKind kind,int x,int z) {
        var p=new BlockPos(x,2,z);factorycraft.multiblock.StructurePlacement.place(h.getLevel(),h.absolutePos(p),Content.THERMAL_BLOCKS.get(kind).get().defaultBlockState().setValue(ThermalBlock.FACING,Direction.EAST));
        return (ThermalBlockEntity)h.getBlockEntity(p);
    }
    private static FluidStack water(int n) { return new FluidStack(Fluids.WATER,n); }
    private static FluidStack steam(int n) { return new FluidStack(SteamFluids.STEAM.get(),n); }
    @GameTest(template="empty",timeoutTicks=100)
    public static void boilerNeedsWaterAndFuelThenMakesSteamByDatapackRecipe(GameTestHelper h) {
        var b=put(h,ThermalKind.BOILER,4,4);
        h.runAfterDelay(10,()->{h.assertTrue(b.steam.amount()==0 && b.boiler.fuel()==0,"empty boiler worked");b.port(Direction.WEST).fill(water(200),EXEC);});
        h.runAfterDelay(20,()->{h.assertTrue(b.water.amount()==200 && b.boiler.progress()==0,"unfueled boiler worked");b.items.setStackInSlot(0,new ItemStack(Items.COAL));});
        h.runAfterDelay(60,()->{h.assertTrue(b.water.amount()==0 && b.steam.amount()==200 && b.boiler.fuel()==1580,"boiling recipe/fuel balance incorrect");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void fullSteamOutputPausesFuelAndResumesAfterDrain(GameTestHelper h) {
        var b=put(h,ThermalKind.BOILER,4,4);
        h.runAfterDelay(5,()->{b.water.fill(new FluidKey(water(1)),200,false);b.steam.fill(new FluidKey(steam(1)),4000,false);b.items.setStackInSlot(0,new ItemStack(Items.COAL));});
        h.runAfterDelay(35,()->{h.assertTrue(b.boiler.fuel()==0 && b.items.getStackInSlot(0).getCount()==1,"blocked boiler spent fuel");b.port(Direction.EAST).drain(200,EXEC);});
        h.runAfterDelay(80,()->{h.assertTrue(b.steam.amount()==4000 && b.water.amount()==0 && b.boiler.fuel()==1580,"blocked boiler failed to resume");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void engineConsumesFiniteSteamAndExportsOnlyGeneratedEnergy(GameTestHelper h) {
        var e=put(h,ThermalKind.ENGINE,4,4);
        h.runAfterDelay(5,()->{h.assertTrue(e.port(Direction.WEST).fill(water(100),EXEC)==0,"engine accepted water");e.port(Direction.WEST).fill(steam(2000),EXEC);});
        h.runAfterDelay(40,()->{
            int n=e.energyPort.extractEnergy(Integer.MAX_VALUE,true);h.assertTrue(n>0 && e.engine.energy()==n,"FE simulation mutated buffer");
            int actual=e.energyPort.extractEnergy(Integer.MAX_VALUE,false);h.assertTrue(actual==n && actual+12L*e.steam.amount()==24000,"steam/FE ledger failed");
            h.assertTrue(e.energyPort.receiveEnergy(100,false)==0,"generator accepted FE input");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=240)
    public static void fullEnergyBufferStopsSteamConsumptionWithoutRoundingLoss(GameTestHelper h) {
        var e=put(h,ThermalKind.ENGINE,4,4);
        h.runAfterDelay(5,()->e.port(Direction.WEST).fill(steam(2000),EXEC));
        h.runAfterDelay(170,()->{
            h.assertTrue(e.engine.energy()==15996 && e.steam.amount()==667,"FE buffer overfilled or steam wasted");e.energyPort.extractEnergy(8,false);
        });
        h.runAfterDelay(200,()->{h.assertTrue(e.engine.energy()==16000 && e.steam.amount()==666,"12-FE remainder did not resume exactly");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void cachedThermalPortsFollowRotationAndUnload(GameTestHelper h) {
        var b=put(h,ThermalKind.BOILER,4,4);
        h.runAfterDelay(5,()->{
            var input=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,b.inputNozzle(),Direction.WEST);var output=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,b.outputNozzle(),Direction.EAST);
            h.assertTrue(input.fill(water(400),EXEC)==400 && output.fill(water(100),EXEC)==0,"physical thermal sides incorrect");
            var part=(factorycraft.blockentity.CasingBlockEntity)h.getLevel().getBlockEntity(b.inputNozzle());part.onChunkUnloaded();h.assertTrue(input.fill(water(10),EXEC)==0,"unloaded structure remained writable");
            part.onLoad();h.assertTrue(input.fill(water(10),EXEC)==10,"physical thermal port failed to resume");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void steamBucketsUseRealNeoForgeContainerCapabilities(GameTestHelper h) {
        var b=put(h,ThermalKind.BOILER,4,4);
        h.runAfterDelay(5,()->{
            b.steam.fill(new FluidKey(steam(1)),1000,false);
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));
            h.assertTrue(FluidUtil.interactWithFluidHandler(player,InteractionHand.MAIN_HAND,b.getLevel(),b.outputNozzle(),Direction.EAST),"bucket could not draw steam");
            h.assertTrue(player.getMainHandItem().is(SteamFluids.BUCKET.get()) && b.steam.amount()==0,"steam bucket lost volume/container");
            var engine=put(h,ThermalKind.ENGINE,12,4);engine.onLoad();for(var part:engine.assembly().positions())((factorycraft.blockentity.CasingBlockEntity)h.getLevel().getBlockEntity(part)).onLoad();
            h.assertTrue(FluidUtil.interactWithFluidHandler(player,InteractionHand.MAIN_HAND,engine.getLevel(),engine.inputNozzle(),Direction.WEST),"steam bucket could not fill engine");
            h.assertTrue(player.getMainHandItem().is(Items.BUCKET) && engine.steam.amount()==1000,"bucket returned incorrect container");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void fuelContainerRemainderIsNotLostAndCanBeAutomated(GameTestHelper h) {
        var b=put(h,ThermalKind.BOILER,4,4);
        h.runAfterDelay(5,()->{b.port(Direction.WEST).fill(water(200),EXEC);b.automation().insertItem(0,new ItemStack(Items.LAVA_BUCKET),false);});
        h.runAfterDelay(55,()->{
            h.assertTrue(b.steam.amount()==200 && b.boiler.fuel()==19980,"container fuel incorrect");
            h.assertTrue(b.automation().extractItem(0,1,false).is(Items.BUCKET),"fuel remainder was destroyed or unextractable");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void harvestingAndActualPlacementPreserveAllThermalResources(GameTestHelper h) {
        var b=put(h,ThermalKind.BOILER,4,4);
        h.runAfterDelay(5,()->{
            b.water.fill(new FluidKey(water(1)),250,false);b.steam.fill(new FluidKey(steam(1)),600,false);b.boiler.restore(43,7,"pending");b.items.setStackInSlot(0,new ItemStack(Items.COAL,3));
            var level=h.getLevel();var pos=b.getBlockPos();var drops=Block.getDrops(b.getBlockState(),level,pos,b);h.assertTrue(drops.size()==1,"machine did not retain one owner");
            level.removeBlock(pos,false);var at=h.absolutePos(new BlockPos(8,2,4));level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,drops.getFirst());
            var hit=new BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false);
            h.assertTrue(PlacementTestSupport.place(player,at).consumesAction(),"harvested block placement failed");
            var restored=(ThermalBlockEntity)level.getBlockEntity(at);
            h.assertTrue(restored.water.amount()==250 && restored.steam.amount()==600 && restored.boiler.fuel()==43 && restored.boiler.progress()==7 && restored.items.getStackInSlot(0).getCount()==3,"portable machine lost canonical resources");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void thermalNbtReconstructionResumesPartialRecipe(GameTestHelper h) {
        var b=put(h,ThermalKind.BOILER,4,4);
        h.runAfterDelay(5,()->{b.port(Direction.WEST).fill(water(200),EXEC);b.items.setStackInSlot(0,new ItemStack(Items.COAL));});
        h.runAfterDelay(15,()->{
            var level=h.getLevel();var copy=(ThermalBlockEntity)BlockEntity.loadStatic(b.getBlockPos(),b.getBlockState(),b.saveWithFullMetadata(level.registryAccess()),level.registryAccess());
            h.assertTrue(copy.boiler.progress()>0 && copy.boiler.progress()<20,"fixture did not preserve partial progress");b.setRemoved();level.setBlockEntity(copy);copy.onLoad();
        });
        h.runAfterDelay(50,()->{var be=(ThermalBlockEntity)h.getBlockEntity(new BlockPos(4,2,4));h.assertTrue(be.water.amount()==0 && be.steam.amount()==200 && be.boiler.fuel()==1580,"partial recipe did not resume exactly");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void finiteBoilerPipeEngineLineConservesEveryUnit(GameTestHelper h) {
        var b=put(h,ThermalKind.BOILER,4,4);var e=put(h,ThermalKind.ENGINE,11,4);
        h.setBlock(new BlockPos(5,2,5),Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());h.setBlock(new BlockPos(6,2,5),Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
        h.runAfterDelay(5,()->{b.port(Direction.WEST).fill(water(1000),EXEC);b.items.setStackInSlot(0,new ItemStack(Items.COAL));});
        h.runAfterDelay(220,()->{var p=(FluidBlockEntity)h.getBlockEntity(new BlockPos(5,2,5));var q=(FluidBlockEntity)h.getBlockEntity(new BlockPos(6,2,5));long fluid=b.water.amount()+b.steam.amount()+p.cell().amount()+q.cell().amount()+e.steam.amount();
            h.assertTrue(e.engine.energy()>0 && fluid*12+e.engine.energy()==12000 && b.boiler.fuel()==1500,"full-size thermal line ledger failed");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=900)
    public static void naturalWaterSteamPowersThePumpAndProductionWithoutCoalGenerator(GameTestHelper h) {
        var o=h.absolutePos(new BlockPos(1,2,1));
        factorycraft.multiblock.StructurePlacement.place(h.getLevel(),o.offset(3,0,2),Content.FLUID_BLOCKS.get(FluidKind.PUMP).get().defaultBlockState().setValue(FluidDeviceBlock.FACING,Direction.EAST));
        var b=put(h,ThermalKind.BOILER,8,2);var e=put(h,ThermalKind.ENGINE,15,2);
        for(int x:new int[]{5,6,9,10})h.setBlock(new BlockPos(x,2,3),Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
        factorycraft.multiblock.StructurePlacement.place(h.getLevel(),h.absolutePos(new BlockPos(18,2,2)),Content.FACTORY.get(FactoryKind.FURNACE).get().defaultBlockState());
        for(int x:new int[]{5,10,15,19})h.setBlock(new BlockPos(x,2,6),Content.FACTORY.get(FactoryKind.POLE).get().defaultBlockState());
        var source=new BlockPos(2,2,3);h.setBlock(source.below(),Blocks.STONE);for(var d:Direction.Plane.HORIZONTAL)if(d!=Direction.EAST)h.setBlock(source.relative(d),Blocks.STONE);h.setBlock(source,Blocks.WATER);
        h.runAfterDelay(5,()->{((FluidBlockEntity)h.getBlockEntity(new BlockPos(4,2,3))).energyPort.receiveEnergy(400,false);b.items.setStackInSlot(0,new ItemStack(Items.COAL));((MachineBlockEntity)h.getBlockEntity(new BlockPos(18,2,2))).items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,4));});
        h.runAfterDelay(700,()->{var pump=(FluidBlockEntity)h.getBlockEntity(new BlockPos(4,2,3));var furnace=(MachineBlockEntity)h.getBlockEntity(new BlockPos(18,2,2));h.assertTrue(furnace.items.getStackInSlot(4).getCount()==4 && pump.power.getEnergyStored()>400 && e.engine.energy()>0,"full-size steam line did not sustain production");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void boilingJsonAndWireCodecPreserveDataAndRejectOversizedRecipes(GameTestHelper h) {
        var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,h.getLevel().registryAccess());
        var json=com.google.gson.JsonParser.parseString("{\"input\":{\"fluid\":\"minecraft:water\",\"amount\":200},\"result\":{\"id\":\"factorycraft:steam\",\"amount\":200},\"ticks\":20}");
        var recipe=factorycraft.recipes.BoilingRecipe.CODEC.codec().parse(ops,json).getOrThrow();
        h.assertTrue(recipe.matches(new factorycraft.recipes.BoilingRecipe.Input(water(200)),h.getLevel()),"JSON sized fluid ingredient did not match");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            var codec=Content.BOILING_SERIALIZER.get().streamCodec();codec.encode(buffer,recipe);var decoded=codec.decode(buffer);
            h.assertTrue(decoded.input().amount()==200 && decoded.ticks()==20 && FluidStack.isSameFluidSameComponents(decoded.result(),steam(200)),"wire recipe changed");
        } finally { buffer.release(); }
        json.getAsJsonObject().getAsJsonObject("input").addProperty("amount",4001);
        h.assertTrue(factorycraft.recipes.BoilingRecipe.CODEC.codec().parse(ops,json).error().isPresent(),"oversized recipe accepted");
        h.assertTrue(!SteamFluids.TYPE.get().canBePlacedInLevel(h.getLevel(),h.absolutePos(BlockPos.ZERO),steam(1000)),"container-only steam became world-placeable");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void plainBucketsCannotEraseFluidComponents(GameTestHelper h) {
        var b=put(h,ThermalKind.BOILER,4,4);
        h.runAfterDelay(5,()->{
            var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("grade",7);
            var fluid=steam(1000);fluid.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
            b.steam.fill(new FluidKey(fluid),1000,false);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));
            h.assertTrue(!FluidUtil.interactWithFluidHandler(player,InteractionHand.MAIN_HAND,h.getLevel(),b.outputNozzle(),Direction.EAST),"native steam bucket accepted unsupported components");
            h.assertTrue(b.steam.amount()==1000 && player.getMainHandItem().is(Items.BUCKET),"steam components were lost");
            var pos=new BlockPos(8,2,4);factorycraft.multiblock.StructurePlacement.place(h.getLevel(),h.absolutePos(pos),Content.FLUID_BLOCKS.get(FluidKind.TANK).get().defaultBlockState());var tank=(FluidBlockEntity)h.getBlockEntity(pos);tank.onLoad();for(var cell:tank.assembly().positions())((factorycraft.blockentity.CasingBlockEntity)h.getLevel().getBlockEntity(cell)).onLoad();
            fluid=water(1000);fluid.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));tank.port(Direction.UP).fill(fluid,EXEC);
            var hit=new BlockHitResult(Vec3.atCenterOf(tank.getBlockPos()),Direction.NORTH,tank.getBlockPos(),false);
            tank.getBlockState().useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(tank.cell().amount()==1000 && player.getMainHandItem().is(Items.BUCKET) && tank.stored().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getInt("grade")==7,"water bucket erased components");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void thermalStatePersistsAcrossServerProcesses(GameTestHelper h) {
        var level=h.getLevel();var a=new BlockPos(223,70,143);var b=new BlockPos(230,70,144);level.getChunkAt(a);level.getChunkAt(b);
        if(level.getBlockEntity(a)==null && level.getBlockEntity(b)==null) {
            for(var profile:java.util.List.of(factorycraft.multiblock.MachineProfile.BOILER,factorycraft.multiblock.MachineProfile.ENGINE))for(var cell:profile.cells(profile==factorycraft.multiblock.MachineProfile.BOILER ? a:b,Direction.NORTH))level.getChunkAt(cell);factorycraft.multiblock.StructurePlacement.place(level,a,Content.THERMAL_BLOCKS.get(ThermalKind.BOILER).get().defaultBlockState());factorycraft.multiblock.StructurePlacement.place(level,b,Content.THERMAL_BLOCKS.get(ThermalKind.ENGINE).get().defaultBlockState());
            var boiler=(ThermalBlockEntity)level.getBlockEntity(a);boiler.water.restore(new FluidKey(water(1)),731);boiler.steam.restore(new FluidKey(steam(1)),389);boiler.boiler.restore(913,7,"saved-pending");boiler.items.setStackInSlot(0,new ItemStack(Items.COAL,3));
            var engine=(ThermalBlockEntity)level.getBlockEntity(b);engine.steam.restore(new FluidKey(steam(1)),523);engine.engine.restore(7312);
            boiler.onLoad();engine.onLoad();for(var part:engine.assembly().positions())((factorycraft.blockentity.CasingBlockEntity)h.getLevel().getBlockEntity(part)).onLoad();boiler.onChunkUnloaded();engine.onChunkUnloaded();boiler.setChanged();engine.setChanged();
            System.out.println("FACTORYCRAFT_THERMAL_DISK: SEEDED fuel/progress/fluids/FE across chunks");
        } else {
            h.assertTrue(level.getBlockEntity(a) instanceof ThermalBlockEntity && level.getBlockEntity(b) instanceof ThermalBlockEntity,"partial saved thermal fixture; refusing to reseed");
            var boiler=(ThermalBlockEntity)level.getBlockEntity(a);var engine=(ThermalBlockEntity)level.getBlockEntity(b);
            h.assertTrue(boiler.water.amount()==731 && boiler.steam.amount()==389 && boiler.boiler.fuel()==913 && boiler.boiler.progress()==7 && boiler.items.getStackInSlot(0).getCount()==3 && engine.steam.amount()==523 && engine.engine.energy()==7312,"saved thermal resources changed");
            boiler.onLoad();engine.onLoad();for(var part:engine.assembly().positions())((factorycraft.blockentity.CasingBlockEntity)h.getLevel().getBlockEntity(part)).onLoad();boiler.onChunkUnloaded();engine.onChunkUnloaded();
            System.out.println("FACTORYCRAFT_THERMAL_DISK: VERIFIED exact fuel/progress/fluids/FE across chunks");
        }
        h.succeed();
    }
}
