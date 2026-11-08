package factorycraft.blocks;

import com.mojang.serialization.MapCodec;
import factorycraft.blockentity.OilDepositBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Finite mineral reservoir. No block item exists which could reset the remaining oil. */
public final class OilDepositBlock extends BaseEntityBlock {
    public static final BooleanProperty DEPLETED=BooleanProperty.create("depleted");
    public OilDepositBlock(Properties properties) { super(properties);registerDefaultState(stateDefinition.any().setValue(DEPLETED,false)); }
    protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(OilDepositBlock::new); }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(DEPLETED); }
    public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new OilDepositBlockEntity(pos,state); }
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof OilDepositBlockEntity oil)player.displayClientMessage(Component.translatable("message.factorycraft.oil_remaining",oil.remaining()),true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
