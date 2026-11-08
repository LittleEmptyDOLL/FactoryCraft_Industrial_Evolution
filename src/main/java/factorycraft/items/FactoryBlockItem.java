package factorycraft.items;

import factorycraft.blocks.FactoryBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.*;
import java.util.List;

public class FactoryBlockItem extends BlockItem {
    public FactoryBlockItem(FactoryBlock block,Properties p) { super(block,p); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags) {
        super.appendHoverText(stack,context,lines,flags);
        var kind=((FactoryBlock)getBlock()).kind;
        String key=kind.belt() && kind!=factorycraft.blocks.FactoryKind.SPLITTER ? "belt" : kind.id;
        lines.add(Component.translatable("tooltip.factorycraft."+key).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(kind.casing() || kind.large() ? "tooltip.factorycraft.multiblock_controls":"tooltip.factorycraft.rotate").withStyle(ChatFormatting.DARK_GRAY));
    }
}
