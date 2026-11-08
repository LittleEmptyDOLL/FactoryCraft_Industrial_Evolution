package factorycraft.items;

import factorycraft.fluids.FluidBlocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.List;

public final class FluidBlockItem extends BlockItem {
    public FluidBlockItem(Block block,Properties properties) { super(block,properties); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,lines,flag);var kind=FluidBlocks.kind(getBlock().defaultBlockState());
        lines.add(Component.translatable("tooltip.factorycraft."+kind.id).withStyle(ChatFormatting.GRAY));
        var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if(data!=null && context.registries()!=null) {
            var fluid=FluidStack.parseOptional(context.registries(),data.copyTag().getCompound("StoredFluid"));
            if(!fluid.isEmpty())lines.add(Component.translatable("tooltip.factorycraft.fluid_contents",fluid.getHoverName(),fluid.getAmount()).withStyle(ChatFormatting.AQUA));
        }
    }
}
