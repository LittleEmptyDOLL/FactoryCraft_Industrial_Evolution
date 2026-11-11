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
import net.minecraft.world.phys.BlockHitResult;

/** Percentage-yield oil point. No block item exists which could reset the remaining oil. */
public final class OilDepositBlock extends BaseEntityBlock {
  public OilDepositBlock(Properties properties) {
    super(properties);
  }

  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(OilDepositBlock::new);
  }

  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new OilDepositBlockEntity(pos, state);
  }

  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  protected ItemInteractionResult useItemOn(
      net.minecraft.world.item.ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    // Geological inspection must not consume the click before BlockItem.useOn can place a pumpjack.
    return stack.getItem() instanceof net.minecraft.world.item.BlockItem
        ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
        : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof OilDepositBlockEntity oil)
      player.displayClientMessage(
          Component.translatable(
              "message.factorycraft.oil_yield",
              String.format(java.util.Locale.ROOT, "%.2f", oil.yield().initial() / 1500.0),
              String.format(java.util.Locale.ROOT, "%.3f", oil.yield().current() / 1500.0),
              String.format(
                  java.util.Locale.ROOT, "%.3f", Math.min(10000, oil.yield().current() / 1500.0))),
          true);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
