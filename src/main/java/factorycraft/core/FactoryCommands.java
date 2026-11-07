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
    private static void put(net.minecraft.server.level.ServerLevel level,BlockPos origin,int x,int z,FactoryKind kind) {
        level.setBlockAndUpdate(origin.offset(x,2,z),Content.FACTORY.get(kind).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.EAST));
    }
    private FactoryCommands(){}
}
