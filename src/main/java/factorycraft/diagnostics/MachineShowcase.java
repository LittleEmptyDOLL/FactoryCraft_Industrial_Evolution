package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

/** Explicit admin showroom with finite inputs, real recipes and full physical footprints. */
public final class MachineShowcase {
    public static int build(CommandSourceStack source) {
        var level=source.getLevel();var origin=BlockPos.containing(source.getPosition()).offset(3,0,3);
        for(var p:BlockPos.betweenClosed(origin,origin.offset(31,5,31)))if(!level.hasChunkAt(p) || !level.getBlockState(p).isAir()) {
            source.sendFailure(Component.translatable("command.factorycraft.showroom_space"));return 0;
        }
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlockAndUpdate(origin.offset(x,0,z),Blocks.STONE_BRICKS.defaultBlockState());
        int index=0;
        for(var profile:MachineProfile.values()) {
            var pos=origin.offset(1+index%4*8,1,1+index/4*8);index++;
            var state=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("factorycraft",profile.id)).defaultBlockState().setValue(FactoryBlock.FACING,Direction.NORTH);
            StructurePlacement.place(level,pos,state);var be=level.getBlockEntity(pos);
            if(be instanceof MachineBlockEntity machine) {
                machine.power.restore(32000);
                switch(profile) {
                    case FURNACE,INDUSTRIAL -> machine.items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,64));
                    case ASSEMBLER -> machine.items.setStackInSlot(0,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft","iron_plate")),64));
                    case GENERATOR -> machine.items.setStackInSlot(8,new ItemStack(Items.COAL,8));
                    case DRILL -> level.setBlockAndUpdate(profile.center(pos,Direction.NORTH).below(),Content.IRON_ORE.get().defaultBlockState());
                    case PUMPJACK -> level.setBlockAndUpdate(profile.center(pos,Direction.NORTH).below(),Content.OIL_DEPOSIT.get().defaultBlockState());
                    case REFINERY -> machine.petroleum().cells.get(0).restore(new factorycraft.fluids.FluidKey(factorycraft.petroleum.PetroleumFluids.CRUDE.stack(1)),4000);
                    case CHEMICAL -> { machine.petroleum().cells.get(0).restore(new factorycraft.fluids.FluidKey(factorycraft.petroleum.PetroleumFluids.GAS.stack(1)),4000);machine.items.setStackInSlot(0,new ItemStack(Items.COAL,64)); }
                    default -> {}
                }machine.setChanged();
            } else if(be instanceof ThermalBlockEntity thermal) {
                if(profile==MachineProfile.BOILER) {thermal.water.restore(new factorycraft.fluids.FluidKey(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1)),4000);thermal.items.setStackInSlot(0,new ItemStack(Items.COAL,8));}
                else thermal.steam.restore(new factorycraft.fluids.FluidKey(new net.neoforged.neoforge.fluids.FluidStack(factorycraft.thermal.SteamFluids.STEAM.get(),1)),4000);thermal.setChanged();
            } else if(be instanceof FluidBlockEntity fluid) {fluid.cell().restore(new factorycraft.fluids.FluidKey(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1)),1000);fluid.setChanged();}
            else if(be instanceof BeltBlockEntity splitter) {
                for(int track=0;track<2;track++) {
                    var input=splitter.trackPosition(track).south();var output=splitter.trackPosition(track).north();
                    StructurePlacement.place(level,input,Content.FACTORY.get(FactoryKind.BELT).get().defaultBlockState());StructurePlacement.place(level,output,Content.FACTORY.get(FactoryKind.BELT).get().defaultBlockState());
                    var belt=(BeltBlockEntity)level.getBlockEntity(input);for(int lane=0;lane<2;lane++)for(int n=0;n<2;n++)belt.lane(lane).restore(new ItemStack(track==0 ? Items.RAW_IRON:Items.RAW_COPPER),1-n*.25);belt.setChanged();
                }
            }
        }
        for(int line=7;line<32;line+=8)for(int step=3;step<32;step+=4)for(var pos:java.util.List.of(origin.offset(line,1,step),origin.offset(step,1,line)))
            if(level.getBlockState(pos).isAir())level.setBlockAndUpdate(pos,Content.FACTORY.get(FactoryKind.POLE).get().defaultBlockState());
        source.sendSuccess(()->Component.translatable("command.factorycraft.showroom_built",origin.toShortString()),true);return 1;
    }
    private MachineShowcase() {}
}
