package factorycraft.multiblock;

import factorycraft.blockentity.*;
import factorycraft.blocks.FactoryBlock;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;

public final class StructureSupport {
    public static List<ItemStack> preserve(List<ItemStack> drops,LootParams.Builder context) {
        var be=context.getOptionalParameter(LootContextParams.BLOCK_ENTITY);var owner=AssemblyAccess.owner(be);
        if(owner!=null)for(var item:drops)if(item.is(be.getBlockState().getBlock().asItem())) {
            be.saveToItem(item,context.getLevel().registryAccess());var data=item.get(DataComponents.BLOCK_ENTITY_DATA);
            if(data!=null) { var tag=data.copyTag();tag.remove("Assembly");item.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(tag)); }
        }
        return drops;
    }
    public static InteractionResult wrench(Level level,BlockPos pos,Player player) {
        if(!level.isClientSide) {
            var be=level.getBlockEntity(pos);var owner=be instanceof CasingBlockEntity part ? part.genericForRemoval():AssemblyAccess.owner(be);
            if(owner!=null) {
                if(player.isShiftKeyDown())owner.assembly().removeWhole(true);
                else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.factorycraft.dismantle"),true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private StructureSupport() {}
}
