package factorycraft.combat;

import factorycraft.research.ResearchTeams;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class GrenadeItem extends Item {
  public GrenadeItem(Properties p) {
    super(p);
  }

  public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
    var stack = p.getItemInHand(hand);
    if (level instanceof ServerLevel l
        && !p.isSpectator()
        && !p.getCooldowns().isOnCooldown(this)
        && l.getGameTime() >= p.getPersistentData().getLong("FactoryGrenadeDue")) {
      p.getPersistentData().putLong("FactoryGrenadeDue", l.getGameTime() + 10);
      var one = stack.split(1);
      p.getCooldowns().addCooldown(this, 10);
      var shot =
          new CombatProjectile(l, p, one, ResearchTeams.resolve(p).orElse("unavailable"), 0, 1);
      shot.shootFromRotation(p, p.getXRot(), p.getYRot(), -10, .8f, 0);
      l.addFreshEntity(shot);
      p.getInventory().setChanged();
    }
    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
  }
}
