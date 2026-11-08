package factorycraft.items;

import factorycraft.blocks.ThermalBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import java.util.List;

public final class ThermalBlockItem extends BlockItem {
    public ThermalBlockItem(Block block,Properties properties) { super(block,properties); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,lines,flag);
        lines.add(Component.translatable("tooltip.factorycraft."+((ThermalBlock)getBlock()).kind.id).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.factorycraft.rotate").withStyle(ChatFormatting.GRAY));
    }
}
