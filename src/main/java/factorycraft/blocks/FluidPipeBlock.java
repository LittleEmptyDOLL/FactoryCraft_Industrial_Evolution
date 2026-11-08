package factorycraft.blocks;

import com.mojang.serialization.MapCodec;
import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.fluids.FluidBlocks;
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
import net.minecraft.world.phys.shapes.*;
import java.util.*;

public final class FluidPipeBlock extends BaseEntityBlock {
    private static final BooleanProperty[] CONNECTIONS={BlockStateProperties.DOWN,BlockStateProperties.UP,BlockStateProperties.NORTH,BlockStateProperties.SOUTH,BlockStateProperties.WEST,BlockStateProperties.EAST};
    private static final VoxelShape[] SHAPES=new VoxelShape[64];
    static {
        VoxelShape center=box(5,5,5,11,11,11);
        VoxelShape[] arms={box(5,0,5,11,5,11),box(5,11,5,11,16,11),box(5,5,0,11,11,5),box(5,5,11,11,11,16),box(0,5,5,5,11,11),box(11,5,5,16,11,11)};
        for(int mask=0;mask<64;mask++) { var shape=center;for(int i=0;i<6;i++)if((mask&(1<<i))!=0)shape=Shapes.or(shape,arms[i]);SHAPES[mask]=shape; }
    }
    public FluidPipeBlock(Properties properties) { super(properties);var state=stateDefinition.any();for(var property:CONNECTIONS)state=state.setValue(property,false);registerDefaultState(state); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(FluidPipeBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(CONNECTIONS); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state=defaultBlockState();for(var direction:Direction.values()) {
            var neighbor=context.getClickedPos().relative(direction);
            state=state.setValue(CONNECTIONS[direction.get3DDataValue()],context.getLevel().hasChunkAt(neighbor) && FluidBlocks.port(context.getLevel().getBlockState(neighbor),direction.getOpposite()));
        }
        return state;
    }
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        return state.setValue(CONNECTIONS[direction.get3DDataValue()],FluidBlocks.port(neighbor,direction.getOpposite()));
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        int mask=0;for(int i=0;i<6;i++)if(state.getValue(CONNECTIONS[i]))mask|=1<<i;return SHAPES[mask];
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new FluidBlockEntity(pos,state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) { return FluidBlocks.useItem(stack,level,pos,player,hand,hit.getDirection()); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) { return FluidBlocks.open(level,pos,player); }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params) { return FluidBlocks.preserve(super.getDrops(state,params),params); }
}
