package factorycraft.blocks;

import com.mojang.serialization.MapCodec;
import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.fluids.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

public final class FluidDeviceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE=BlockStateProperties.LIT;
    public final FluidKind kind;
    public FluidDeviceBlock(FluidKind kind,Properties properties) {
        super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ACTIVE,false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p->new FluidDeviceBlock(kind,p)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,ACTIVE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new FluidBlockEntity(pos,state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) { return FluidBlocks.useItem(stack,level,pos,player,hand,hit.getDirection()); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) { return FluidBlocks.open(level,pos,player); }
    public InteractionResult useWrench(Level level,BlockPos pos) {
        if(!level.isClientSide && kind==FluidKind.PUMP)level.setBlock(pos,level.getBlockState(pos).cycle(FACING),3);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos neighborPos,boolean moving) {
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof FluidBlockEntity be)FluidSimulation.activatePump(be);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params) { return FluidBlocks.preserve(super.getDrops(state,params),params); }
}
