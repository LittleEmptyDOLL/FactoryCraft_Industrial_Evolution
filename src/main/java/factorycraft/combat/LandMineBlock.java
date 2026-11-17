package factorycraft.combat;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

/**
 * Arming state and force live in one persisted block entity; one removal precedes blast callbacks.
 */
public final class LandMineBlock extends BaseEntityBlock {
  public LandMineBlock(Properties p) {
    super(p);
  }

  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(LandMineBlock::new);
  }

  protected RenderShape getRenderShape(BlockState s) {
    return RenderShape.MODEL;
  }

  protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return box(2, 0, 2, 14, 3, 14);
  }

  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new LandMineBlockEntity(p, s);
  }

  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level l, BlockState s, BlockEntityType<T> type) {
    return l.isClientSide
        ? null
        : createTickerHelper(
            type, factorycraft.registry.Content.LAND_MINE_ENTITY.get(), (a, b, c, d) -> d.tick());
  }

  public void setPlacedBy(
      Level l, BlockPos pos, BlockState s, LivingEntity owner, ItemStack stack) {
    super.setPlacedBy(l, pos, s, owner, stack);
    if (!l.isClientSide
        && owner instanceof Player p
        && l.getBlockEntity(pos) instanceof LandMineBlockEntity mine) {
      mine.owner = p.getUUID();
      mine.scope = factorycraft.research.ResearchTeams.resolve(p).orElse("unavailable");
      mine.setChanged();
    }
  }
}
