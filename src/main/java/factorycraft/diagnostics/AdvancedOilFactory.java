package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.core.FactoryCommands;
import factorycraft.fluids.FluidKind;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Opt-in full-size finite oil → sulfur → acid → battery factory. No intermediate fluid injection. */
public final class AdvancedOilFactory {
    public static boolean available(ServerLevel level) {
        return level.getRecipeManager().getAllRecipesFor(Content.CHEMISTRY.get()).stream().anyMatch(r->r.id().equals(ResourceLocation.fromNamespaceAndPath("factorycraft","advanced_oil_processing")) && r.value().machine().equals("oil_refinery"));
    }
    public static void build(ServerLevel level,BlockPos o) {
        level.setBlockAndUpdate(o.offset(2,-1,3),Content.OIL_DEPOSIT.get().defaultBlockState());
        machine(level,o,3,2,FactoryKind.PUMPJACK);
        for(int x=4;x<=6;x++)pipe(level,o,x,0,3);
        var refinery=machine(level,o,11,1,FactoryKind.REFINERY);
        // Keep selection independent of recipe ordering and transient water shortages.
        if(!refinery.petroleum().selectRecipe(ResourceLocation.fromNamespaceAndPath("factorycraft","advanced_oil_processing")))
            throw new IllegalStateException("Advanced oil recipe is missing from the active datapacks");
        for(int x=12;x<=14;x++)pipe(level,o,x,0,3);
        machine(level,o,17,2,FactoryKind.CHEMICAL_PLANT);
        machine(level,o,17,8,FactoryKind.CHEMICAL_PLANT);
        machine(level,o,23,8,FactoryKind.CHEMICAL_PLANT);
        for(int x=18;x<=20;x++)pipe(level,o,x,0,9);

        // Sulfur is handed through a chest containing the acid's finite iron supply.
        arm(level,o,16,5,Direction.SOUTH);var acidFeed=chest(level,o,16,6);
        acidFeed.setItem(0,stack("iron_plate",16));arm(level,o,16,7,Direction.SOUTH);
        var batteryFeed=chest(level,o,22,6);batteryFeed.setItem(0,stack("iron_plate",8));batteryFeed.setItem(1,stack("copper_plate",8));
        arm(level,o,22,7,Direction.SOUTH);arm(level,o,24,9,Direction.EAST);chest(level,o,25,9);

        // Independent overhead product lines keep the three refinery outputs isolated.
        pipe(level,o,12,0,1);pipe(level,o,12,1,1);
        // Offset before crossing y=2: the water bus at z=0 must not touch
        // the heavy-oil riser at z=1. Never pass through the gas pipe at y=0,z=3.
        for(int y=1;y<=4;y++)pipe(level,o,12,y,2);
        for(int y=0;y<=4;y++)pipe(level,o,9,y,6);
        for(int x=12;x<=20;x++)pipe(level,o,x,4,2);
        pipe(level,o,20,4,1);
        for(int x=9;x<=20;x++)pipe(level,o,x,4,6);
        pipe(level,o,20,4,5);pipe(level,o,20,3,5);pipe(level,o,20,3,1);
        tank(level,o,19,0);tank(level,o,19,4);

        // A real native source-water pump supplies a bus two blocks above the floor.
        var water=o.offset(1,0,12);
        for(var d:Direction.Plane.HORIZONTAL)if(d!=Direction.EAST)level.setBlockAndUpdate(water.relative(d),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(water,Blocks.WATER.defaultBlockState());
        StructurePlacement.place(level,o.offset(3,0,12),Content.FLUID_BLOCKS.get(FluidKind.PUMP).get().defaultBlockState().setValue(FluidDeviceBlock.FACING,Direction.EAST));
        for(int y=0;y<=2;y++)pipe(level,o,4,y,12);
        for(int z=0;z<=12;z++)pipe(level,o,4,2,z);
        for(int x=4;x<=16;x++)pipe(level,o,x,2,0);
        for(int z=0;z<=9;z++)pipe(level,o,14,2,z);
        pipe(level,o,16,2,1);pipe(level,o,14,2,9);
        for(int y=0;y<2;y++) {pipe(level,o,9,y,0);pipe(level,o,16,y,1);pipe(level,o,14,y,9);}
        // Join the refinery input's vertical drop to the bus at y=2.
        pipe(level,o,9,2,0);
        machine(level,o,8,13,FactoryKind.GENERATOR).items.setStackInSlot(8,new ItemStack(Items.COAL,8));
        for(int x=0;x<30;x+=4)for(int z=0;z<16;z+=4)if(level.getBlockState(o.offset(x,0,z)).isAir())
            FactoryCommands.machine(level,o.offset(x,0,z),FactoryKind.POLE,Direction.NORTH);
    }
    private static MachineBlockEntity machine(ServerLevel level,BlockPos o,int x,int z,FactoryKind kind) { return FactoryCommands.machine(level,o.offset(x,0,z),kind,Direction.EAST); }
    private static void arm(ServerLevel level,BlockPos o,int x,int z,Direction facing) { FactoryCommands.machine(level,o.offset(x,0,z),FactoryKind.INSERTER,facing); }
    private static ChestBlockEntity chest(ServerLevel level,BlockPos o,int x,int z) { level.setBlockAndUpdate(o.offset(x,0,z),Blocks.CHEST.defaultBlockState());return (ChestBlockEntity)level.getBlockEntity(o.offset(x,0,z)); }
    private static void pipe(ServerLevel level,BlockPos o,int x,int y,int z) { level.setBlockAndUpdate(o.offset(x,y,z),Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState()); }
    private static void tank(ServerLevel level,BlockPos o,int x,int z) { StructurePlacement.place(level,o.offset(x,0,z),Content.FLUID_BLOCKS.get(FluidKind.TANK).get().defaultBlockState()); }
    private static ItemStack stack(String id,int n) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft",id)),n); }
    private AdvancedOilFactory() {}
}
