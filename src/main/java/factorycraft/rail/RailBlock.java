package factorycraft.rail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class RailBlock extends BaseEntityBlock {
  public RailBlock(Properties properties) {
    super(properties);
  }

  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(RailBlock::new);
  }

  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new RailCellBlockEntity(p, s);
  }

  public RenderShape getRenderShape(BlockState s) {
    return RenderShape.MODEL;
  }

  protected VoxelShape getShape(
      BlockState s, BlockGetter level, BlockPos p, CollisionContext context) {
    return level.getBlockEntity(p) instanceof RailCellBlockEntity c ? c.shape() : Shapes.empty();
  }

  protected VoxelShape getCollisionShape(
      BlockState s, BlockGetter level, BlockPos p, CollisionContext c) {
    return getShape(s, level, p, c);
  }

  protected void onRemove(BlockState old, Level l, BlockPos p, BlockState next, boolean moved) {
    if (!old.is(next.getBlock()) && l instanceof net.minecraft.server.level.ServerLevel server)
      RailwayData.get(server).destroyed(p);
    super.onRemove(old, l, p, next, moved);
  }

  protected InteractionResult useWithoutItem(
      BlockState s, Level level, BlockPos p, Player player, BlockHitResult hit) {
    if (level instanceof net.minecraft.server.level.ServerLevel server) {
      var data = RailwayData.get(server);
      var id = data.nearest(p, hit.getLocation());
      if (id != null) {
        var edge = data.segment(id);
        player.displayClientMessage(
            net.minecraft.network.chat.Component.translatable(
                "rail.factorycraft.inspect",
                id.toString().substring(0, 8),
                String.format(java.util.Locale.ROOT, "%.3f", edge.curve.length()),
                data.graph.at(edge.curve.start).size(),
                data.graph.at(edge.curve.end).size(),
                data.available(id)),
            false);
      }
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  public ItemStack getCloneItemStack(LevelReader level, BlockPos p, BlockState state) {
    return new ItemStack(RailContent.RAIL.get());
  }

  public ItemStack getCloneItemStack(
      BlockState s,
      net.minecraft.world.phys.HitResult target,
      LevelReader level,
      BlockPos p,
      Player player) {
    return new ItemStack(RailContent.RAIL.get());
  }
}
