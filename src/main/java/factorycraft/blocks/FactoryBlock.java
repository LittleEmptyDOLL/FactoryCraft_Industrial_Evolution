package factorycraft.blocks;

import com.mojang.serialization.MapCodec;
import factorycraft.blockentity.*;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class FactoryBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    public final FactoryKind kind;
    public FactoryBlock(FactoryKind kind, Properties properties) {
        super(properties); this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> new FactoryBlock(kind, p)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ACTIVE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection()); }
    @Override protected BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState s, Mirror m) { return rotate(s, m.getRotation(s.getValue(FACING))); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        if (kind.belt()) return box(0, 0, 0, 16, 3, 16);
        if (kind == FactoryKind.INSERTER) return box(5, 0, 5, 11, 8, 11);
        if (kind == FactoryKind.POLE) return box(6, 0, 6, 10, 16, 10);
        return Shapes.block();
    }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        if (kind.belt()) return new BeltBlockEntity(p, s);
        if (kind == FactoryKind.INSERTER) return new InserterBlockEntity(p, s);
        if (kind == FactoryKind.POLE) return new PoleBlockEntity(p, s);
        return new MachineBlockEntity(p, s);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return null;
        if (kind.belt()) return null;
        if (kind == FactoryKind.INSERTER) return createTickerHelper(t, Content.INSERTER.get(), (a,b,c,d) -> d.tick());
        if (kind.machine()) return createTickerHelper(t, Content.MACHINE.get(), (a,b,c,d) -> d.tick());
        return null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (player.getMainHandItem().is(Content.ITEMS.getEntries().stream().filter(i -> i.getId().getPath().equals("wrench")).findFirst().orElseThrow().get())) {
            l.setBlock(p, s.cycle(FACING), 3);
            if(l.getBlockEntity(p) instanceof BeltBlockEntity belt) {
                factorycraft.simulation.BeltSimulation.activate(belt);
                factorycraft.simulation.BeltSimulation.wakeInputs(belt);
            }
            return InteractionResult.CONSUME;
        }
        if (l.getBlockEntity(p) instanceof MachineBlockEntity machine) player.openMenu(machine, p);
        else player.displayClientMessage(Component.translatable("message.factorycraft.direction", s.getValue(FACING).getName()), true);
        return InteractionResult.CONSUME;
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof FactoryBlockEntity be) be.dropContents();
        super.onRemove(s, l, p, next, moving);
    }
}
