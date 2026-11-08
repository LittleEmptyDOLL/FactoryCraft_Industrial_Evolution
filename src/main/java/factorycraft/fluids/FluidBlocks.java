package factorycraft.fluids;

import factorycraft.blocks.*;
import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.fluids.FluidUtil;
import java.util.List;

public final class FluidBlocks {
    public static FluidKind kind(BlockState state) { return state.getBlock() instanceof FluidDeviceBlock device ? device.kind:FluidKind.PIPE; }
    public static boolean port(BlockState state,Direction face) {
        return state.getBlock() instanceof FluidPipeBlock || state.getBlock() instanceof FluidDeviceBlock device &&
                (device.kind==FluidKind.TANK || face==state.getValue(FluidDeviceBlock.FACING) || face==state.getValue(FluidDeviceBlock.FACING).getOpposite());
    }
    public static ItemInteractionResult useItem(ItemStack stack,Level level,BlockPos pos,Player player,InteractionHand hand,Direction side) {
        if(FluidUtil.getFluidHandler(stack).isEmpty())return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(level.isClientSide || FluidUtil.interactWithFluidHandler(player,hand,level,pos,side))return ItemInteractionResult.sidedSuccess(level.isClientSide);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    public static InteractionResult open(Level level,BlockPos pos,Player player) {
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof FluidBlockEntity be)player.openMenu(be,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    public static List<ItemStack> preserve(List<ItemStack> drops,LootParams.Builder context) {
        if(context.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof FluidBlockEntity be && (be.cell().amount()>0 || be.power.getEnergyStored()>0))
            for(var stack:drops)if(stack.is(be.getBlockState().getBlock().asItem()))be.saveToItem(stack,context.getLevel().registryAccess());
        return drops;
    }
    private FluidBlocks() {}
}
