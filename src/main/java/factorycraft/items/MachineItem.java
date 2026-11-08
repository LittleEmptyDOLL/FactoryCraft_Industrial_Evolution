package factorycraft.items;

import factorycraft.blocks.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** One held item, one complete protected structure; portable data never imports an old identity. */
public final class MachineItem extends BlockItem {
    public MachineItem(Block block,Properties properties) { super(block,properties); }
    protected boolean canPlace(BlockPlaceContext context,BlockState state) {
        if(!super.canPlace(context,state))return false;boolean valid=PlacementPlan.inspect(context,state).valid();
        if(!valid && context.getPlayer()!=null)context.getPlayer().displayClientMessage(Component.translatable("message.factorycraft.placement_space"),true);return valid;
    }
    protected boolean placeBlock(BlockPlaceContext context,BlockState state) {
        if(context.getLevel().isClientSide)return super.placeBlock(context,state);
        var level=context.getLevel();var plan=PlacementPlan.inspect(context,state);if(!plan.valid())return false;
        var old=plan.cells().stream().map(level::getBlockState).toList();int snapshot=level.capturedBlockSnapshots.size();boolean success=false;
        try {
            if(!super.placeBlock(context,state))return false;
            var part=Content.FACTORY.get(FactoryKind.CASING).get().defaultBlockState().setValue(FactoryBlock.FACING,plan.profile().facing(state)).setValue(FactoryBlock.PROFILE,plan.profile());
            for(int i=1;i<plan.cells().size();i++)if(!level.setBlock(plan.cells().get(i),part,2))return false;
            var owner=AssemblyAccess.owner(level.getBlockEntity(plan.anchor()));success=owner!=null && owner.assembly().formAutomatic();return success;
        } finally {
            if(!success) {
                boolean restoring=level.restoringBlockSnapshots;level.restoringBlockSnapshots=true;
                try { for(int i=plan.cells().size()-1;i>=0;i--)level.setBlock(plan.cells().get(i),old.get(i),3); }finally { level.restoringBlockSnapshots=restoring; }
                level.capturedBlockSnapshots.subList(snapshot,level.capturedBlockSnapshots.size()).clear();
            }
        }
    }
    public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags) {
        super.appendHoverText(stack,context,lines,flags);var profile=MachineProfile.of(getBlock().defaultBlockState());
        lines.add(Component.translatable("tooltip.factorycraft.machine_dimensions",profile.layout.width(),profile.layout.depth(),profile.layout.height()));
        lines.add(Component.translatable("tooltip.factorycraft.machine_preview"));
        lines.add(Component.translatable("tooltip.factorycraft."+profile.id));
    }
}
