package factorycraft.blocks;

import com.mojang.serialization.MapCodec;
import factorycraft.blockentity.ThermalBlockEntity;
import factorycraft.thermal.ThermalKind;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

/** Compact one-block thermal equipment. The one-item multiblock rule still governs larger layouts. */
public final class ThermalBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE=BlockStateProperties.LIT;
    public final ThermalKind kind;
    public ThermalBlock(ThermalKind kind,Properties properties) { super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ACTIVE,false)); }
    protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p->new ThermalBlock(kind,p)); }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,ACTIVE); }
    public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection()); }
    public boolean port(BlockState state,Direction side) { var facing=state.getValue(FACING);return side==facing.getOpposite() || kind==ThermalKind.BOILER && side==facing; }
    public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new ThermalBlockEntity(pos,state); }
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide ? null:createTickerHelper(type,factorycraft.registry.Content.THERMAL.get(),(l,p,s,be)->be.tick());
    }
    protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        return factorycraft.fluids.FluidBlocks.useItem(stack,level,pos,player,hand,hit.getDirection());
    }
    protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof ThermalBlockEntity be)player.openMenu(be,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    public InteractionResult useWrench(Level level,BlockPos pos) {
        if(!level.isClientSide)level.setBlock(pos,level.getBlockState(pos).cycle(FACING),3);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    protected List<ItemStack> getDrops(BlockState state,LootParams.Builder context) {
        var drops=super.getDrops(state,context);
        if(context.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ThermalBlockEntity be && be.hasContents())
            for(var item:drops)if(item.is(asItem()))be.saveToItem(item,context.getLevel().registryAccess());
        return drops;
    }
}
