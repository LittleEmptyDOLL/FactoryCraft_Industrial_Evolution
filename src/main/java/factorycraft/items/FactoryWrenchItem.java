package factorycraft.items;

import factorycraft.blocks.FactoryBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

/** Item-first handling keeps sneak-wrench usable even when the player's offhand is occupied. */
public final class FactoryWrenchItem extends Item {
    public FactoryWrenchItem(Properties properties) { super(properties); }
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context) {
        if(context.getHand()!=InteractionHand.MAIN_HAND || context.getPlayer()==null)return InteractionResult.PASS;
        var state=context.getLevel().getBlockState(context.getClickedPos());
        return state.getBlock() instanceof FactoryBlock block ? block.useWrench(context.getLevel(),context.getClickedPos(),context.getPlayer()):InteractionResult.PASS;
    }
}
