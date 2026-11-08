package factorycraft.items;

import factorycraft.blocks.FactoryBlock;
import factorycraft.blocks.FactoryKind;
import factorycraft.blockentity.MachineBlockEntity;
import factorycraft.multiblock.IndustrialAssembly;
import factorycraft.registry.Content;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;

/** One item places the entire footprint inside NeoForge's normal placement snapshot transaction. */
public final class IndustrialFurnaceItem extends FactoryBlockItem {
    public IndustrialFurnaceItem(FactoryBlock block, Properties properties) { super(block,properties); }

    @Override protected boolean canPlace(BlockPlaceContext context,BlockState state) {
        if(!super.canPlace(context,state))return false;
        var level=context.getLevel();var player=context.getPlayer();
        var collision=player==null ? CollisionContext.empty():CollisionContext.of(player);
        var cells=new ArrayList<>(IndustrialAssembly.positions(context.getClickedPos(),state.getValue(FactoryBlock.FACING)));
        cells.add(context.getClickedPos());
        var part=Content.FACTORY.get(((FactoryBlock)getBlock()).kind==FactoryKind.REFINERY ? FactoryKind.REFINERY_CASING:FactoryKind.CASING).get().defaultBlockState();
        for(var pos:cells) {
            if(level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)
                    || level.getBlockEntity(pos)!=null || !level.getBlockState(pos).canBeReplaced(BlockPlaceContext.at(context,pos,context.getClickedFace()))
                    || !level.isUnobstructed(part,pos,collision)
                    || player!=null && (!level.mayInteract(player,pos) || !player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))) {
                if(player!=null)player.displayClientMessage(Component.translatable("message.factorycraft.placement_space"),true);
                return false;
            }
        }
        return true;
    }

    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState state) {
        if(context.getLevel().isClientSide)return super.placeBlock(context,state);
        var level=context.getLevel();
        var cells=new ArrayList<>(IndustrialAssembly.positions(context.getClickedPos(),state.getValue(FactoryBlock.FACING)));
        cells.addFirst(context.getClickedPos());
        var oldStates=cells.stream().map(level::getBlockState).toList();
        int snapshotStart=level.capturedBlockSnapshots.size();
        boolean success=false;
        try {
            if(!super.placeBlock(context,state))return false;
            for(int i=1;i<cells.size();i++)
                if(!level.setBlock(cells.get(i),Content.FACTORY.get(((FactoryBlock)getBlock()).kind==FactoryKind.REFINERY ? FactoryKind.REFINERY_CASING:FactoryKind.CASING).get().defaultBlockState().setValue(FactoryBlock.FACING,state.getValue(FactoryBlock.FACING)),2))return false;
            if(!(level.getBlockEntity(context.getClickedPos()) instanceof MachineBlockEntity machine))return false;
            success=machine.assembly().formAutomatic();
            return success;
        } finally {
            if(!success) {
                // Suppress demolition and drops while undoing an incomplete placement.
                boolean restoring=level.restoringBlockSnapshots;level.restoringBlockSnapshots=true;
                try {
                    for(int i=cells.size()-1;i>=0;i--)level.setBlock(cells.get(i),oldStates.get(i),3);
                } finally { level.restoringBlockSnapshots=restoring; }
                level.capturedBlockSnapshots.subList(snapshotStart,level.capturedBlockSnapshots.size()).clear();
            }
        }
    }
}
