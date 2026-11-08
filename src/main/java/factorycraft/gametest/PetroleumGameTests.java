package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.IndustrialAssembly;
import factorycraft.petroleum.*;
import factorycraft.recipes.ChemicalInput;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class PetroleumGameTests {
    private static final IFluidHandler.FluidAction EXEC=IFluidHandler.FluidAction.EXECUTE,SIM=IFluidHandler.FluidAction.SIMULATE;
    private static MachineBlockEntity machine(GameTestHelper h,FactoryKind kind,int x,int z) {
        var pos=h.absolutePos(new BlockPos(x,2,z));
        if(kind.large()) {
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.setYRot(Direction.WEST.toYRot());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Content.FACTORY.get(kind).get()));
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
            h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"one-item refinery placement failed");
        } else h.getLevel().setBlockAndUpdate(pos,Content.FACTORY.get(kind).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.EAST));
        return (MachineBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static OilDepositBlockEntity deposit(GameTestHelper h,int x,int z) {
        var pos=new BlockPos(x,1,z);h.setBlock(pos,Content.OIL_DEPOSIT.get().defaultBlockState());return (OilDepositBlockEntity)h.getBlockEntity(pos);
    }
    private static int count(MachineBlockEntity machine,Item item) { int n=0;for(int i=0;i<8;i++)if(machine.items.getStackInSlot(i).is(item))n+=machine.items.getStackInSlot(i).getCount();return n; }
    private static Item plastic() { return Content.ITEMS.getEntries().stream().filter(i->i.getId().getPath().equals("plastic_bar")).findFirst().orElseThrow().get(); }
    @GameTest(template="empty",timeoutTicks=180)
    public static void pumpjackRequiresEnergyAndMovesOnlyFiniteReserve(GameTestHelper h) {
        var oil=deposit(h,4,4);var pump=machine(h,FactoryKind.PUMPJACK,4,4);
        h.runAfterDelay(30,()->{h.assertTrue(pump.petroleum().cells.get(2).amount()==0,"unpowered pump extracted");pump.power.receiveEnergy(150,false);});
        h.runAfterDelay(80,()->{h.assertTrue(pump.petroleum().cells.get(2).amount()==100 && oil.remaining()==factorycraft.config.FactoryConfig.OIL_RICHNESS.get()-100 && pump.power.getEnergyStored()==0,"finite oil/FE ledger incorrect");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=150)
    public static void aFullPumpjackOutputConsumesNeitherOilNorEnergy(GameTestHelper h) {
        var oil=deposit(h,4,4);var pump=machine(h,FactoryKind.PUMPJACK,4,4);
        h.runAfterDelay(5,()->{pump.petroleum().cells.get(2).fill(new FluidKey(PetroleumFluids.CRUDE.stack(1)),4000,false);pump.power.receiveEnergy(1000,false);});
        h.runAfterDelay(70,()->{h.assertTrue(oil.remaining()==factorycraft.config.FactoryConfig.OIL_RICHNESS.get() && pump.power.getEnergyStored()==1000,"blocked pump spent resources");pump.petroleum().port(pump.petroleum().nozzle(2),Direction.EAST).drain(100,EXEC);});
        h.runAfterDelay(100,()->{h.assertTrue(pump.petroleum().cells.get(2).amount()==4000 && pump.power.getEnergyStored()==850,"pump failed to resume");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void exhaustedDepositsDoNotRefillOnReload(GameTestHelper h) {
        var oil=deposit(h,4,4);var pump=machine(h,FactoryKind.PUMPJACK,4,4);
        h.runAfterDelay(5,()->{oil.reserve().drain(Integer.MAX_VALUE,false);pump.power.receiveEnergy(500,false);oil.onChunkUnloaded();oil.onLoad();});
        h.runAfterDelay(60,()->{h.assertTrue(oil.remaining()==0 && oil.getBlockState().getValue(OilDepositBlock.DEPLETED) && pump.power.getEnergyStored()==500,"exhausted deposit refilled or spent FE");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void refineryFormsFromOneItemAndPartsHaveNoResourceCopies(GameTestHelper h) {
        var refinery=machine(h,FactoryKind.REFINERY,6,5);
        h.runAfterDelay(5,()->{
            h.assertTrue(refinery.assembly().automatic() && refinery.assembly().ready(),"refinery not automatically formed");
            for(var pos:refinery.assembly().positions()) {
                var part=(CasingBlockEntity)h.getLevel().getBlockEntity(pos);var tag=part.saveWithFullMetadata(h.getLevel().registryAccess());
                h.assertTrue(part.virtualPart() && part.boundTo(refinery.getBlockPos(),refinery.assembly().identity()) && !tag.contains("Petroleum") && !tag.contains("Items"),"part duplicated controller resources");
                h.assertTrue(part.getBlockState().getBlock().getCloneItemStack(h.getLevel(),pos,part.getBlockState()).is(Content.FACTORY.get(FactoryKind.REFINERY).get().asItem()),"pick-block returned wrong machine");
            }
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=220)
    public static void refineryFluidBackpressurePausesAndResumesRecipe(GameTestHelper h) {
        var r=machine(h,FactoryKind.REFINERY,6,5);
        h.runAfterDelay(5,()->{r.petroleum().cells.get(0).fill(new FluidKey(PetroleumFluids.CRUDE.stack(1)),100,false);r.petroleum().cells.get(2).fill(new FluidKey(PetroleumFluids.GAS.stack(1)),3990,false);r.power.receiveEnergy(5000,false);});
        h.runAfterDelay(40,()->{h.assertTrue(r.power.getEnergyStored()==5000 && r.petroleum().cells.get(0).amount()==100,"blocked refinery spent input or FE");r.petroleum().cells.get(2).drain(100,false);});
        h.runAfterDelay(180,()->{h.assertTrue(r.petroleum().cells.get(0).amount()==0 && r.petroleum().cells.get(2).amount()==3935 && r.power.getEnergyStored()==2000,"basic oil recipe incorrect");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=150)
    public static void mixedRecipeNeedsEnergyAndCommitsCoalGasAndPlasticTogether(GameTestHelper h) {
        var c=machine(h,FactoryKind.CHEMICAL_PLANT,5,5);
        h.runAfterDelay(5,()->{c.petroleum().cells.get(0).fill(new FluidKey(PetroleumFluids.GAS.stack(1)),20,false);c.items.setStackInSlot(0,new ItemStack(Items.COAL));});
        h.runAfterDelay(40,()->{h.assertTrue(c.petroleum().cells.get(0).amount()==20 && count(c,Items.COAL)==1 && count(c,plastic())==0,"unpowered chemistry consumed inputs");c.power.receiveEnergy(1000,false);});
        h.runAfterDelay(90,()->{h.assertTrue(c.petroleum().cells.get(0).amount()==0 && count(c,Items.COAL)==0 && count(c,plastic())==2 && c.power.getEnergyStored()==600,"mixed commit did not conserve recipe counts/FE");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void itemOutputReservationsPreventConsumptionWhileFull(GameTestHelper h) {
        var c=machine(h,FactoryKind.CHEMICAL_PLANT,5,5);
        h.runAfterDelay(5,()->{c.petroleum().cells.get(0).fill(new FluidKey(PetroleumFluids.GAS.stack(1)),20,false);c.items.setStackInSlot(0,new ItemStack(Items.COAL));for(int i=4;i<8;i++)c.items.setStackInSlot(i,new ItemStack(plastic(),64));c.power.receiveEnergy(1000,false);});
        h.runAfterDelay(50,()->{h.assertTrue(c.petroleum().cells.get(0).amount()==20 && count(c,Items.COAL)==1 && c.power.getEnergyStored()==1000,"blocked item output spent resources");c.items.extractItem(4,2,false);});
        h.runAfterDelay(90,()->{h.assertTrue(count(c,plastic())==256 && count(c,Items.COAL)==0 && c.petroleum().cells.get(0).amount()==0,"chemical output did not resume safely");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void twoFluidRecipeConsumesOnlyItsAssignedPorts(GameTestHelper h) {
        var c=machine(h,FactoryKind.CHEMICAL_PLANT,5,5);
        h.runAfterDelay(5,()->{c.petroleum().cells.get(0).fill(new FluidKey(PetroleumFluids.GAS.stack(1)),30,false);c.petroleum().cells.get(1).fill(new FluidKey(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,1)),30,false);c.power.receiveEnergy(1000,false);});
        h.runAfterDelay(60,()->{h.assertTrue(c.petroleum().cells.get(0).amount()==0 && c.petroleum().cells.get(1).amount()==0 && c.items.getStackInSlot(4).getCount()==2 && c.items.getStackInSlot(4).getItem().toString().contains("sulfur"),"two-fluid recipe failed");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void cachedNozzlePortsStopOnPartUnloadAndResume(GameTestHelper h) {
        var r=machine(h,FactoryKind.REFINERY,6,5);
        h.runAfterDelay(5,()->{
            var rear=r.petroleum().nozzle(0);var side=r.petroleum().direction(0);var cap=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,rear,side);
            h.assertTrue(cap!=null && cap.fill(PetroleumFluids.CRUDE.stack(250),EXEC)==250,"rear part did not expose input");
            var part=(CasingBlockEntity)h.getLevel().getBlockEntity(rear);part.onChunkUnloaded();
            h.assertTrue(cap.fill(PetroleumFluids.CRUDE.stack(10),EXEC)==0 && !r.petroleum().ready(),"cached part port ignored unload");part.onLoad();
            h.assertTrue(cap.fill(PetroleumFluids.CRUDE.stack(10),EXEC)==10 && r.petroleum().cells.get(0).amount()==260,"part did not resume with canonical storage");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void partDestructionReturnsOnePortableRefineryAndNoDuplicatedInputs(GameTestHelper h) {
        var r=machine(h,FactoryKind.REFINERY,6,5);
        h.runAfterDelay(5,()->{
            r.petroleum().cells.get(0).fill(new FluidKey(PetroleumFluids.CRUDE.stack(1)),250,false);r.items.setStackInSlot(0,new ItemStack(Items.DIAMOND,3));
            var parts=new ArrayList<>(r.assembly().positions());h.getLevel().destroyBlock(parts.getLast(),true);
            var entities=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(r.getBlockPos()).inflate(4));
            var items=entities.stream().filter(e->e.getItem().is(Content.FACTORY.get(FactoryKind.REFINERY).get().asItem())).toList();
            h.assertTrue(items.size()==1 && items.getFirst().getItem().getCount()==1 && entities.stream().noneMatch(e->e.getItem().is(Items.DIAMOND)),"refinery destruction duplicated or lost owner");
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.setYRot(Direction.WEST.toYRot());player.setItemInHand(InteractionHand.MAIN_HAND,items.getFirst().getItem().copy());items.getFirst().discard();
            var at=h.absolutePos(new BlockPos(11,2,5));h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false))).consumesAction(),"portable refinery would not place");
            var restored=(MachineBlockEntity)h.getLevel().getBlockEntity(at);
            h.assertTrue(restored.petroleum().cells.get(0).amount()==250 && count(restored,Items.DIAMOND)==3 && restored.assembly().formed(),"portable refinery lost state or overwrote fresh identity");
            for(var pos:restored.assembly().positions())h.assertTrue(((CasingBlockEntity)h.getLevel().getBlockEntity(pos)).boundTo(at,restored.assembly().identity()),"portable NBT orphaned new parts");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void partialChemicalProductionResumesAfterNbtReconstruction(GameTestHelper h) {
        var c=machine(h,FactoryKind.CHEMICAL_PLANT,5,5);
        h.runAfterDelay(5,()->{c.petroleum().cells.get(0).fill(new FluidKey(PetroleumFluids.GAS.stack(1)),20,false);c.items.setStackInSlot(0,new ItemStack(Items.COAL));c.power.receiveEnergy(1000,false);});
        h.runAfterDelay(15,()->{
            h.assertTrue(c.petroleum().data.get(0)>0 && c.petroleum().data.get(0)<20,"fixture lacks partial progress");var level=h.getLevel();var copy=(MachineBlockEntity)BlockEntity.loadStatic(c.getBlockPos(),c.getBlockState(),c.saveWithFullMetadata(level.registryAccess()),level.registryAccess());c.setRemoved();level.setBlockEntity(copy);copy.onLoad();
        });
        h.runAfterDelay(65,()->{var m=(MachineBlockEntity)h.getBlockEntity(new BlockPos(5,2,5));h.assertTrue(count(m,plastic())==2 && m.petroleum().cells.get(0).amount()==0 && m.power.getEnergyStored()==600,"restart changed mixed recipe balance");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void mixedRecipeCodecsUseFluidInputsAndBoundedWireLists(GameTestHelper h) {
        var recipes=h.getLevel().getRecipeManager().getAllRecipesFor(Content.CHEMISTRY.get());var recipe=recipes.stream().filter(r->r.id().getPath().equals("plastic")).findFirst().orElseThrow().value();
        var inventory=new net.neoforged.neoforge.items.ItemStackHandler(4);inventory.setStackInSlot(0,new ItemStack(Items.COAL));
        h.assertTrue(!recipe.matches(new ChemicalInput(inventory,List.of()),h.getLevel()) && recipe.matches(new ChemicalInput(inventory,List.of(PetroleumFluids.GAS.stack(20))),h.getLevel()),"recipe API ignored fluids");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try { var codec=Content.CHEMISTRY_SERIALIZER.get().streamCodec();codec.encode(buffer,recipe);var decoded=codec.decode(buffer);h.assertTrue(decoded.fluids().getFirst().amount()==20 && decoded.results().getFirst().getCount()==2 && decoded.energy()==20,"mixed recipe wire changed"); }finally { buffer.release(); }
        var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,h.getLevel().registryAccess());var encoded=factorycraft.recipes.ChemicalRecipe.CODEC.codec().encodeStart(ops,recipe).getOrThrow();encoded.getAsJsonObject().addProperty("energy",16001);
        h.assertTrue(factorycraft.recipes.ChemicalRecipe.CODEC.codec().parse(ops,encoded).error().isPresent(),"oversized energy accepted");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void configuredWorldgenReallyPlacesFiniteOilBlocks(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,4,8));
        for(var p:BlockPos.betweenClosed(origin.offset(-3,-3,-3),origin.offset(3,2,3)))level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
        var feature=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID,"oil_deposit"));
        boolean placed=feature.place(level,level.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(12),origin);h.assertTrue(placed,"oil worldgen failed to place");
        h.runAfterDelay(5,()->{int count=0;for(var p:BlockPos.betweenClosed(origin.offset(-3,-3,-3),origin.offset(3,2,3)))if(level.getBlockEntity(p) instanceof OilDepositBlockEntity oil) { count++;h.assertTrue(oil.remaining()>0,"generated oil never initialized"); }h.assertTrue(count>0,"worldgen did not create reservoirs");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void refineryPlacementProtectionRollsBackEveryGeneratedPart(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(6,2,5));var player=h.makeMockPlayer(GameType.SURVIVAL);player.setYRot(Direction.WEST.toYRot());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Content.FACTORY.get(FactoryKind.REFINERY).get()));
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent> listener=e->e.setCanceled(true);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try {
            player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
            h.assertTrue(player.getMainHandItem().getCount()==1 && h.getLevel().getBlockState(pos).isAir(),"protection consumed refinery or left controller");
            for(var part:IndustrialAssembly.positions(pos,Direction.EAST))h.assertTrue(h.getLevel().getBlockState(part).isAir(),"canceled placement left generated part");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void petroleumStateAndFourChunkIdentityPersistAcrossServerProcesses(GameTestHelper h) {
        var level=h.getLevel();var anchor=new BlockPos(271,70,271);var plantPos=new BlockPos(281,70,271);var resource=new BlockPos(280,69,271);var exhausted=new BlockPos(281,69,271);
        var all=new ArrayList<>(factorycraft.multiblock.MachineProfile.REFINERY.parts(anchor,Direction.NORTH));all.addAll(List.of(anchor,plantPos,resource,exhausted));all.addAll(factorycraft.multiblock.MachineProfile.CHEMICAL.parts(plantPos,Direction.NORTH));for(var p:all)level.getChunkAt(p);
        if(level.getBlockEntity(anchor)==null && level.getBlockEntity(plantPos)==null && level.getBlockEntity(resource)==null && level.getBlockEntity(exhausted)==null) {
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.setYRot(Direction.SOUTH.toYRot());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Content.FACTORY.get(FactoryKind.REFINERY).get()));
            h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(anchor),Direction.UP,anchor,false))).consumesAction(),"disk refinery placement failed");
            var refinery=(MachineBlockEntity)level.getBlockEntity(anchor);refinery.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.CRUDE.stack(1)),731);refinery.petroleum().cells.get(2).restore(new FluidKey(PetroleumFluids.GAS.stack(1)),389);refinery.items.setStackInSlot(0,new ItemStack(Items.DIAMOND,3));refinery.power.restore(7312);
            factorycraft.multiblock.StructurePlacement.place(level,plantPos,Content.FACTORY.get(FactoryKind.CHEMICAL_PLANT).get().defaultBlockState());var plant=(MachineBlockEntity)level.getBlockEntity(plantPos);plant.petroleum().cells.get(0).restore(new FluidKey(PetroleumFluids.GAS.stack(1)),11);plant.items.setStackInSlot(0,new ItemStack(Items.COAL,2));
            var tag=plant.saveWithFullMetadata(level.registryAccess());tag.getCompound("Petroleum").putInt("Progress",7);tag.getCompound("Petroleum").putInt("Duration",20);plant.loadWithComponents(tag,level.registryAccess());
            level.setBlockAndUpdate(resource,Content.OIL_DEPOSIT.get().defaultBlockState());level.setBlockAndUpdate(exhausted,Content.OIL_DEPOSIT.get().defaultBlockState());
            ((OilDepositBlockEntity)level.getBlockEntity(resource)).initializeReserve(7117);((OilDepositBlockEntity)level.getBlockEntity(exhausted)).initializeReserve(0);
            refinery.onLoad();plant.onLoad();((OilDepositBlockEntity)level.getBlockEntity(resource)).onLoad();((OilDepositBlockEntity)level.getBlockEntity(exhausted)).onLoad();
            System.out.println("FACTORYCRAFT_PETROLEUM_DISK: SEEDED four-chunk refinery, mixed state and finite/exhausted reserves");
        } else {
            h.assertTrue(level.getBlockEntity(anchor) instanceof MachineBlockEntity && level.getBlockEntity(plantPos) instanceof MachineBlockEntity && level.getBlockEntity(resource) instanceof OilDepositBlockEntity && level.getBlockEntity(exhausted) instanceof OilDepositBlockEntity,"partial petroleum fixture; refusing to reseed");
            var r=(MachineBlockEntity)level.getBlockEntity(anchor);var plant=(MachineBlockEntity)level.getBlockEntity(plantPos);
            h.assertTrue(r.petroleum().cells.get(0).amount()==731 && r.petroleum().cells.get(2).amount()==389 && count(r,Items.DIAMOND)==3 && r.power.getEnergyStored()==7312 && r.assembly().automatic(),"disk refinery lost authoritative state");
            for(var p:factorycraft.multiblock.MachineProfile.REFINERY.parts(anchor,Direction.NORTH))h.assertTrue(level.getBlockEntity(p) instanceof CasingBlockEntity part && part.boundTo(anchor,r.assembly().identity()),"four-chunk refinery identity changed");
            h.assertTrue(plant.petroleum().cells.get(0).amount()==11 && count(plant,Items.COAL)==2 && plant.petroleum().data.get(0)==7,"mixed recipe state changed on disk");
            h.assertTrue(((OilDepositBlockEntity)level.getBlockEntity(resource)).remaining()==7117 && ((OilDepositBlockEntity)level.getBlockEntity(exhausted)).remaining()==0,"oil reserve refilled/lost on disk");
            System.out.println("FACTORYCRAFT_PETROLEUM_DISK: VERIFIED exact resources, depleted reserve and four-chunk identity");
        }
        // Exact-value persistence fixture is deliberately inactive; live production is covered separately.
        for(var p:all) { var be=level.getBlockEntity(p);if(be!=null) { be.onChunkUnloaded();level.getChunkAt(p).setUnsaved(true); } }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=2000)
    public static void finiteDepositBecomesPlasticThroughPoweredPipesAndInserters(GameTestHelper h) {
        var o=h.absolutePos(new BlockPos(1,2,1));factorycraft.core.FactoryCommands.oilLine(h.getLevel(),o);
        h.runAfterDelay(1700,()->{
            var oil=(OilDepositBlockEntity)h.getLevel().getBlockEntity(o.offset(2,-1,3));var pump=(MachineBlockEntity)h.getLevel().getBlockEntity(o.offset(3,0,2));var r=(MachineBlockEntity)h.getLevel().getBlockEntity(o.offset(11,0,1));var c=(MachineBlockEntity)h.getLevel().getBlockEntity(o.offset(17,0,2));
            var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(o.offset(19,0,3));long plastic=0;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(plastic()))plastic+=chest.getItem(i).getCount();h.assertTrue(plastic>=20,"full-size oil line has not delivered plastic");plastic+=count(c,plastic());var held=((InserterBlockEntity)h.getLevel().getBlockEntity(o.offset(18,0,3))).held();if(held.is(plastic()))plastic+=held.getCount();
            long crude=oil.remaining()+pump.petroleum().cells.get(2).amount()+r.petroleum().cells.get(0).amount(),gas=r.petroleum().cells.get(2).amount()+c.petroleum().cells.get(0).amount();
            for(int x:new int[]{4,5,6})crude+=((FluidBlockEntity)h.getLevel().getBlockEntity(o.offset(x,0,3))).cell().amount();for(int x:new int[]{12,13,14})gas+=((FluidBlockEntity)h.getLevel().getBlockEntity(o.offset(x,0,3))).cell().amount();
            h.assertTrue(crude*9+gas*20+plastic*200==9L*factorycraft.config.FactoryConfig.OIL_RICHNESS.get(),"oil/gas/plastic ledger lost resources");h.succeed();});
    }
}
