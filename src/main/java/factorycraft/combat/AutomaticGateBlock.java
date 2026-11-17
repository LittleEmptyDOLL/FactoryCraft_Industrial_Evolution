package factorycraft.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.AABB;

/**
 * Native gate collision/redstone/manual interaction, with bounded local player-triggered opening.
 */
public final class AutomaticGateBlock extends FenceGateBlock
    implements net.minecraft.world.level.block.EntityBlock {
  public AutomaticGateBlock(Properties p) {
    super(WoodType.OAK, p);
  }

  protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moving) {
    super.onPlace(s, l, p, old, moving);
    if (!l.isClientSide) l.scheduleTick(p, this, 5);
  }

  public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource random) {
    boolean near =
        !l.getEntitiesOfClass(
                net.minecraft.world.entity.player.Player.class,
                new AABB(p).inflate(2),
                e ->
                    !e.isSpectator()
                        && (!(l.getBlockEntity(p) instanceof GateBlockEntity gate)
                            || gate.friendly(e)))
            .isEmpty();
    boolean open = near || l.hasNeighborSignal(p) || l.hasNeighborSignal(p.above());
    if (s.getValue(OPEN) != open) l.setBlock(p, s.setValue(OPEN, open), 3);
    l.scheduleTick(p, this, 5);
  }

  public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
      BlockPos p, BlockState s) {
    return new GateBlockEntity(p, s);
  }

  public void setPlacedBy(
      Level l,
      BlockPos pos,
      BlockState s,
      net.minecraft.world.entity.LivingEntity owner,
      net.minecraft.world.item.ItemStack stack) {
    super.setPlacedBy(l, pos, s, owner, stack);
    if (!l.isClientSide
        && owner instanceof net.minecraft.world.entity.player.Player p
        && l.getBlockEntity(pos) instanceof GateBlockEntity gate) gate.bind(p);
  }

  protected net.minecraft.world.InteractionResult useWithoutItem(
      BlockState s,
      Level l,
      BlockPos p,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.phys.BlockHitResult hit) {
    if (!l.isClientSide
        && l.getBlockEntity(p) instanceof GateBlockEntity gate
        && !gate.friendly(player)) return net.minecraft.world.InteractionResult.FAIL;
    return super.useWithoutItem(s, l, p, player, hit);
  }
}
