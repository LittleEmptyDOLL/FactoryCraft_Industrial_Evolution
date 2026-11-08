package factorycraft.diagnostics;

import factorycraft.blocks.FactoryKind;
import factorycraft.blockentity.*;
import factorycraft.core.FactoryCommands;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import factorycraft.research.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Automated red science from raw components, a real assembler, belts/inserters, power and team lab. */
public final class ResearchFactory {
    public static LabBlockEntity build(ServerLevel level,BlockPos o,String scope){
        FactoryCommands.machine(level,o.offset(3,0,2),FactoryKind.ASSEMBLER,Direction.NORTH);
        level.setBlockAndUpdate(o.offset(4,0,0),Blocks.CHEST.defaultBlockState());var chest=(ChestBlockEntity)level.getBlockEntity(o.offset(4,0,0));
        chest.setItem(0,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft","iron_gear")),20));chest.setItem(1,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft","copper_plate")),20));
        FactoryCommands.machine(level,o.offset(4,0,1),FactoryKind.INSERTER,Direction.SOUTH);FactoryCommands.machine(level,o.offset(6,0,3),FactoryKind.INSERTER,Direction.EAST);FactoryCommands.machine(level,o.offset(7,0,3),FactoryKind.BELT,Direction.EAST);FactoryCommands.machine(level,o.offset(8,0,3),FactoryKind.INSERTER,Direction.EAST);
        StructurePlacement.place(level,o.offset(9,0,2),Content.FACTORY.get(FactoryKind.LAB).get().defaultBlockState());var lab=(LabBlockEntity)level.getBlockEntity(o.offset(9,0,2));lab.bind(scope);
        var ledger=ResearchData.get(level.getServer()).team(scope);ledger.select(ResearchCatalog.get(level).entry(ResourceLocation.fromNamespaceAndPath("factorycraft","automation")).logic());ResearchData.get(level.getServer()).setDirty();
        FactoryCommands.machine(level,o.offset(4,0,8),FactoryKind.GENERATOR,Direction.NORTH).items.setStackInSlot(8,new ItemStack(Items.COAL,8));for(int x:new int[]{3,8,12})FactoryCommands.machine(level,o.offset(x,0,6),FactoryKind.POLE,Direction.NORTH);return lab;
    }
    private ResearchFactory(){}
}
