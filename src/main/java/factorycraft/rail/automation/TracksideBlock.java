package factorycraft.rail.automation;

import com.mojang.serialization.MapCodec;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** One native controller plus automatically placed nonowning physical parts; no per-part ticker. */
public final class TracksideBlock extends BaseEntityBlock {
  public static final IntegerProperty PART = IntegerProperty.create("part", 0, 11);
  public static final IntegerProperty ASPECT = IntegerProperty.create("aspect", 0, 4);
  public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
  public final TracksideKind kind;

  public TracksideBlock(TracksideKind kind, Properties properties) {
    super(properties);
    this.kind = kind;
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(PART, 0)
            .setValue(ASPECT, 0)
            .setValue(FACING, Direction.NORTH));
  }

  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(p -> new TracksideBlock(kind, p));
  }

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    b.add(PART, ASPECT, FACING);
  }

  public static BlockPos offset(BlockPos origin, TracksideKind kind, int part) {
    return origin.offset(
        part % kind.width, part / (kind.width * kind.width), (part / kind.width) % kind.width);
  }

  public static BlockPos origin(BlockPos p, BlockState s) {
    var kind = ((TracksideBlock) s.getBlock()).kind;
    int part = s.getValue(PART);
    return p.offset(
        -(part % kind.width),
        -(part / (kind.width * kind.width)),
        -((part / kind.width) % kind.width));
  }

  public static TracksideBlockEntity owner(Level level, BlockPos p) {
    if (!level.hasChunkAt(p)) return null;
    var state = level.getBlockState(p);
    if (!(state.getBlock() instanceof TracksideBlock)) return null;
    var origin = origin(p, state);
    if (!level.hasChunkAt(origin)) return null;
    var be = factorycraft.fluids.FluidConnections.at(level, origin);
    if (level.isClientSide) be = level.getBlockEntity(origin);
    return be instanceof TracksideBlockEntity root ? root : null;
  }

  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return s.getValue(PART) == 0 ? new TracksideBlockEntity(p, s) : null;
  }

  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos p, CollisionContext c) {
    int part = s.getValue(PART), layer = part / (kind.width * kind.width);
    if (kind != TracksideKind.STOP)
      return layer == 0
          ? Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(7, 2, 7, 9, 16, 9))
          : Shapes.or(Block.box(7, 0, 7, 9, 12, 9), Block.box(4, 3, 5, 12, 15, 11));
    int x = part % kind.width, z = (part / kind.width) % kind.width;
    if (layer == 0) return Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(6, 2, 6, 10, 16, 10));
    if (x == 0 && z == 0) return Block.box(6, 0, 6, 10, 16, 10);
    return layer == kind.height - 1 ? Block.box(0, 8, 4, 16, 16, 12) : Shapes.empty();
  }

  protected InteractionResult useWithoutItem(
      BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
    var owner = owner(l, p);
    if (owner != null && player instanceof net.minecraft.server.level.ServerPlayer server)
      TracksideMenus.open(server, owner, p);
    return InteractionResult.sidedSuccess(l.isClientSide);
  }

  protected List<ItemStack> getDrops(BlockState s, LootParams.Builder context) {
    return List.of();
  }

  public ItemStack getCloneItemStack(LevelReader l, BlockPos p, BlockState s) {
    return new ItemStack(TracksideContent.ITEMS.get(kind).get());
  }

  protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
    if (!s.is(next.getBlock()) && !l.restoringBlockSnapshots && !TracksidePlacement.writing)
      TracksidePlacement.environmentRemoved(l, p, s);
    super.onRemove(s, l, p, next, moving);
  }
}
