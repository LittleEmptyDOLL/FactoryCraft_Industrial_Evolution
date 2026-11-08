package factorycraft.core;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.MachineBlockEntity;
import factorycraft.blocks.*;
import factorycraft.registry.Content;
import factorycraft.simulation.BeltSimulation;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid=FactoryCraft.ID)
public final class FactoryCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("factorycraft").requires(s->s.hasPermission(2))
                .then(Commands.literal("smelter").executes(c->smelter(c.getSource())))
                .then(Commands.literal("fluids").executes(c->{c.getSource().sendSuccess(()->Component.literal(factorycraft.fluids.FluidSimulation.statistics(c.getSource().getLevel())),false);return 1;}))
                .then(Commands.literal("oilworks").executes(c->oilworks(c.getSource())))
                .then(Commands.literal("steamworks").executes(c->steamworks(c.getSource())))
                .then(Commands.literal("waterworks").executes(c->waterworks(c.getSource())))
                .then(Commands.literal("power").executes(c->{c.getSource().sendSuccess(()->Component.literal(factorycraft.energy.PowerGrid.statistics(c.getSource().getLevel())),false);return 1;}))
                .then(Commands.literal("profile").executes(c->{c.getSource().sendSuccess(()->Component.literal(BeltSimulation.statistics(c.getSource().getLevel())),false);return 1;}))
                .then(Commands.literal("demo").executes(c->{
                    var source=c.getSource();var level=source.getLevel();
                    BlockPos origin=BlockPos.containing(source.getPosition()).offset(2,0,2);
                    for(var p:BlockPos.betweenClosed(origin,origin.offset(15,4,7))) {
                        if(!level.hasChunkAt(p) || !level.getBlockState(p).isAir()) {
                            source.sendFailure(Component.translatable("command.factorycraft.space"));return 0;
                        }
                    }
                    for(int x=0;x<16;x++)for(int z=0;z<8;z++)level.setBlockAndUpdate(origin.offset(x,1,z),Blocks.STONE_BRICKS.defaultBlockState());
                    for(int x=1;x<=3;x++)for(int z=1;z<=3;z++)level.setBlockAndUpdate(origin.offset(x,1,z),Content.IRON_ORE.get().defaultBlockState());
                    put(level,origin,2,2,FactoryKind.DRILL);put(level,origin,3,2,FactoryKind.INSERTER);
                    put(level,origin,4,2,FactoryKind.BELT);put(level,origin,5,2,FactoryKind.BELT);
                    put(level,origin,6,2,FactoryKind.INSERTER);put(level,origin,7,2,FactoryKind.FURNACE);
                    put(level,origin,8,2,FactoryKind.INSERTER);put(level,origin,9,2,FactoryKind.ASSEMBLER);put(level,origin,10,2,FactoryKind.INSERTER);
                    level.setBlockAndUpdate(origin.offset(11,2,2),Blocks.CHEST.defaultBlockState());
                    put(level,origin,5,5,FactoryKind.GENERATOR);put(level,origin,4,4,FactoryKind.POLE);put(level,origin,9,4,FactoryKind.POLE);
                    ((MachineBlockEntity)level.getBlockEntity(origin.offset(5,2,5))).items.setStackInSlot(8,new ItemStack(Items.COAL,4));
                    source.sendSuccess(()->Component.translatable("command.factorycraft.demo",origin.toShortString()),true);return 1;
                })));
    }
    private static int smelter(net.minecraft.commands.CommandSourceStack source) {
        var level=source.getLevel();var origin=BlockPos.containing(source.getPosition()).offset(2,0,2);
        for(var p:BlockPos.betweenClosed(origin.offset(-1,0,-3),origin.offset(4,4,3)))
            if(!level.hasChunkAt(p) || !level.getBlockState(p).isAir()) { source.sendFailure(Component.translatable("command.factorycraft.smelter_space"));return 0; }
        for(int x=-1;x<=4;x++)for(int z=-3;z<=3;z++)level.setBlockAndUpdate(origin.offset(x,1,z),Blocks.STONE_BRICKS.defaultBlockState());
        var anchor=origin.offset(0,2,0);
        level.setBlockAndUpdate(anchor,Content.FACTORY.get(FactoryKind.INDUSTRIAL_FURNACE).get().defaultBlockState());
        for(var p:factorycraft.multiblock.IndustrialAssembly.positions(anchor,Direction.NORTH))
            level.setBlockAndUpdate(p,Content.FACTORY.get(FactoryKind.CASING).get().defaultBlockState());
        var machine=(MachineBlockEntity)level.getBlockEntity(anchor);machine.assembly().formAutomatic();
        put(level,origin,3,-1,FactoryKind.GENERATOR);put(level,origin,2,0,FactoryKind.POLE);
        ((MachineBlockEntity)level.getBlockEntity(origin.offset(3,2,-1))).items.setStackInSlot(8,new ItemStack(Items.COAL,4));
        level.setBlockAndUpdate(origin.offset(3,2,1),Blocks.CHEST.defaultBlockState());
        ((net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(origin.offset(3,2,1))).setItem(0,new ItemStack(Items.RAW_IRON,12));
        level.setBlockAndUpdate(origin.offset(2,2,1),Content.FACTORY.get(FactoryKind.INSERTER).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.WEST));
        level.setBlockAndUpdate(origin.offset(0,2,-1),Content.FACTORY.get(FactoryKind.INSERTER).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.NORTH));
        level.setBlockAndUpdate(origin.offset(0,2,-2),Blocks.CHEST.defaultBlockState());
        source.sendSuccess(()->Component.translatable("command.factorycraft.smelter",anchor.toShortString()),true);return 1;
    }
    private static int waterworks(net.minecraft.commands.CommandSourceStack source) {
        var level=source.getLevel();var origin=BlockPos.containing(source.getPosition()).offset(2,0,2);
        for(var p:BlockPos.betweenClosed(origin,origin.offset(8,3,4)))
            if(!level.hasChunkAt(p) || !level.getBlockState(p).isAir()) { source.sendFailure(Component.translatable("command.factorycraft.fluid_space"));return 0; }
        for(int x=0;x<9;x++)for(int z=0;z<5;z++)level.setBlockAndUpdate(origin.offset(x,0,z),Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(1,1,2),Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(0,1,2),Blocks.STONE_BRICKS.defaultBlockState());
        for(int z=1;z<=3;z++)if(z!=2)level.setBlockAndUpdate(origin.offset(1,1,z),Blocks.STONE_BRICKS.defaultBlockState());
        for(int x=2;x<=7;x++) {
            var kind=x==2 ? factorycraft.fluids.FluidKind.PUMP:x==7 ? factorycraft.fluids.FluidKind.TANK:factorycraft.fluids.FluidKind.PIPE;
            var state=Content.FLUID_BLOCKS.get(kind).get().defaultBlockState();
            if(kind==factorycraft.fluids.FluidKind.PUMP)state=state.setValue(factorycraft.blocks.FluidDeviceBlock.FACING,Direction.EAST);
            level.setBlockAndUpdate(origin.offset(x,1,2),state);
        }
        level.setBlockAndUpdate(origin.offset(0,1,0),Content.FACTORY.get(FactoryKind.GENERATOR).get().defaultBlockState());
        ((MachineBlockEntity)level.getBlockEntity(origin.offset(0,1,0))).items.setStackInSlot(8,new ItemStack(Items.COAL,4));
        level.setBlockAndUpdate(origin.offset(2,1,0),Content.FACTORY.get(FactoryKind.POLE).get().defaultBlockState());
        source.sendSuccess(()->Component.translatable("command.factorycraft.fluid_demo",origin.toShortString()),true);return 1;
    }
    private static int steamworks(net.minecraft.commands.CommandSourceStack source) {
        var level=source.getLevel();var origin=BlockPos.containing(source.getPosition()).offset(2,0,2);
        for(var p:BlockPos.betweenClosed(origin,origin.offset(12,3,6)))
            if(!level.hasChunkAt(p) || !level.getBlockState(p).isAir()) { source.sendFailure(Component.translatable("command.factorycraft.steam_space"));return 0; }
        for(int x=0;x<13;x++)for(int z=0;z<7;z++)level.setBlockAndUpdate(origin.offset(x,0,z),Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(1,1,4),Blocks.WATER.defaultBlockState());
        for(var p:java.util.List.of(origin.offset(0,1,4),origin.offset(1,1,3),origin.offset(1,1,5)))level.setBlockAndUpdate(p,Blocks.STONE_BRICKS.defaultBlockState());
        for(int x=2;x<=8;x++) {
            var pos=origin.offset(x,1,4);
            if(x==5 || x==8) {
                var kind=x==5 ? factorycraft.thermal.ThermalKind.BOILER:factorycraft.thermal.ThermalKind.ENGINE;
                level.setBlockAndUpdate(pos,Content.THERMAL_BLOCKS.get(kind).get().defaultBlockState().setValue(factorycraft.blocks.ThermalBlock.FACING,Direction.EAST));
                if(x==5)((factorycraft.blockentity.ThermalBlockEntity)level.getBlockEntity(pos)).items.setStackInSlot(0,new ItemStack(Items.COAL,4));
            } else {
                var kind=x==2 ? factorycraft.fluids.FluidKind.PUMP:factorycraft.fluids.FluidKind.PIPE;
                var state=Content.FLUID_BLOCKS.get(kind).get().defaultBlockState();
                if(x==2)state=state.setValue(factorycraft.blocks.FluidDeviceBlock.FACING,Direction.EAST);
                level.setBlockAndUpdate(pos,state);
            }
        }
        for(int x:new int[]{3,7})level.setBlockAndUpdate(origin.offset(x,1,2),Content.FACTORY.get(FactoryKind.POLE).get().defaultBlockState());
        level.setBlockAndUpdate(origin.offset(1,1,1),Content.FACTORY.get(FactoryKind.GENERATOR).get().defaultBlockState());
        ((MachineBlockEntity)level.getBlockEntity(origin.offset(1,1,1))).items.setStackInSlot(8,new ItemStack(Items.COAL));
        level.setBlockAndUpdate(origin.offset(10,1,2),Content.FACTORY.get(FactoryKind.FURNACE).get().defaultBlockState());
        ((MachineBlockEntity)level.getBlockEntity(origin.offset(10,1,2))).items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,8));
        source.sendSuccess(()->Component.translatable("command.factorycraft.steam_demo",origin.toShortString()),true);return 1;
    }
    private static int oilworks(net.minecraft.commands.CommandSourceStack source) {
        var level=source.getLevel();var origin=BlockPos.containing(source.getPosition()).offset(2,0,2);
        for(var pos:BlockPos.betweenClosed(origin,origin.offset(21,5,8)))if(!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) { source.sendFailure(Component.translatable("command.factorycraft.oil_space"));return 0; }
        for(int x=0;x<22;x++)for(int z=0;z<9;z++)for(int y=0;y<2;y++)level.setBlockAndUpdate(origin.offset(x,y,z),Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(3,1,4),Content.OIL_DEPOSIT.get().defaultBlockState());
        for(var entry:java.util.Map.of(3,FactoryKind.PUMPJACK,9,FactoryKind.REFINERY,13,FactoryKind.CHEMICAL_PLANT).entrySet()) {
            var pos=origin.offset(entry.getKey(),2,4);level.setBlockAndUpdate(pos,Content.FACTORY.get(entry.getValue()).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.EAST));
            if(entry.getValue().large()) {
                for(var part:factorycraft.multiblock.IndustrialAssembly.positions(pos,Direction.EAST))level.setBlockAndUpdate(part,Content.FACTORY.get(FactoryKind.REFINERY_CASING).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.EAST));
                ((MachineBlockEntity)level.getBlockEntity(pos)).assembly().formAutomatic();
            }
        }
        for(int x:new int[]{4,5,6,7,10,11,12})level.setBlockAndUpdate(origin.offset(x,2,4),Content.FLUID_BLOCKS.get(factorycraft.fluids.FluidKind.PIPE).get().defaultBlockState());
        level.setBlockAndUpdate(origin.offset(13,2,2),Blocks.CHEST.defaultBlockState());
        ((net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(origin.offset(13,2,2))).setItem(0,new ItemStack(Items.COAL,32));
        level.setBlockAndUpdate(origin.offset(13,2,3),Content.FACTORY.get(FactoryKind.INSERTER).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.SOUTH));
        level.setBlockAndUpdate(origin.offset(14,2,4),Content.FACTORY.get(FactoryKind.INSERTER).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.EAST));
        level.setBlockAndUpdate(origin.offset(15,2,4),Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(6,2,1),Content.FACTORY.get(FactoryKind.GENERATOR).get().defaultBlockState());
        ((MachineBlockEntity)level.getBlockEntity(origin.offset(6,2,1))).items.setStackInSlot(8,new ItemStack(Items.COAL,8));
        for(var p:java.util.List.of(origin.offset(5,2,2),origin.offset(10,2,1),origin.offset(14,2,2)))level.setBlockAndUpdate(p,Content.FACTORY.get(FactoryKind.POLE).get().defaultBlockState());
        source.sendSuccess(()->Component.translatable("command.factorycraft.oil_demo",origin.toShortString()),true);return 1;
    }
    private static void put(net.minecraft.server.level.ServerLevel level,BlockPos origin,int x,int z,FactoryKind kind) {
        level.setBlockAndUpdate(origin.offset(x,2,z),Content.FACTORY.get(kind).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.EAST));
    }
    private FactoryCommands(){}
}
