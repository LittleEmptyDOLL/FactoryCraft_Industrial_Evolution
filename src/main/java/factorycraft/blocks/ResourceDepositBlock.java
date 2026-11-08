package factorycraft.blocks;

import com.mojang.serialization.MapCodec;
import factorycraft.blockentity.ResourceDepositBlockEntity;
import factorycraft.resources.ResourceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

/** A resource-bearing tile, harvested repeatedly rather than replaced by air after one unit. */
public final class ResourceDepositBlock extends BaseEntityBlock {
    public final ResourceKind kind;
    public ResourceDepositBlock(ResourceKind kind,Properties properties) { super(properties);this.kind=kind; }
    protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p->new ResourceDepositBlock(kind,p)); }
    public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new ResourceDepositBlockEntity(pos,state); }
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public boolean onDestroyedByPlayer(BlockState state,Level level,BlockPos pos,Player player,boolean harvest,FluidState fluid) {
        if(player.isCreative())return super.onDestroyedByPlayer(state,level,pos,player,harvest,fluid);
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof ResourceDepositBlockEntity deposit) {
            if(harvest && deposit.available() && deposit.remaining()>0) {
                if(player.getInventory().add(kind.result()))deposit.extractAccepted();
                else player.displayClientMessage(Component.translatable("message.factorycraft.resource_inventory_full"),true);
            }
            level.sendBlockUpdated(pos,state,level.getBlockState(pos),3);
        }
        return false;
    }
    protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof ResourceDepositBlockEntity deposit)player.displayClientMessage(Component.translatable("message.factorycraft.resource_remaining",kind.result().getHoverName(),deposit.remaining()),true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
