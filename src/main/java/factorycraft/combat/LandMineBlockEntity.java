package factorycraft.combat;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class LandMineBlockEntity extends BlockEntity {
  public java.util.UUID owner;
  public String scope = "unavailable";
  private int armTicks = 40;

  public LandMineBlockEntity(BlockPos p, BlockState s) {
    super(factorycraft.registry.Content.LAND_MINE_ENTITY.get(), p, s);
  }

  public void tick() {
    if (!(level instanceof ServerLevel l)) return;
    if (armTicks > 0) {
      armTicks--;
      setChanged();
      return;
    }
    if ((l.getGameTime() + worldPosition.asLong()) % 5 != 0) return;
    var shooter = owner == null ? null : l.getPlayerByUUID(owner);
    var victims =
        l.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(worldPosition).inflate(2.5),
            e -> Combat.allowed(l, e, shooter, scope, true));
    if (victims.isEmpty()) return;
    var center = worldPosition.getCenter();
    // Invalidate the sole mine before damage can re-enter block handling.
    l.removeBlock(worldPosition, false);
    for (var e : l.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(3))) {
      if (!Combat.allowed(l, e, shooter, scope, true) || !Combat.sight(l, center, e)) continue;
      Combat.damage(
          l,
          e,
          shooter,
          shooter,
          scope,
          "explosion",
          250 * (1 + CombatResearch.bonus(l, scope, "ammo-damage", "landmine")),
          true);
      if (e instanceof net.minecraft.world.entity.Mob mob && mob.isAlive())
        CombatStun.apply(mob, 60);
    }
    l.sendParticles(
        net.minecraft.core.particles.ParticleTypes.EXPLOSION,
        center.x,
        center.y,
        center.z,
        1,
        0,
        0,
        0,
        0);
  }

  protected void saveAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.saveAdditional(n, r);
    n.putInt("ArmTicks", armTicks);
    n.putString("Force", scope);
    if (owner != null) n.putUUID("Owner", owner);
  }

  protected void loadAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.loadAdditional(n, r);
    armTicks = Math.clamp(n.getInt("ArmTicks"), 0, 40);
    scope = n.getString("Force");
    owner = n.hasUUID("Owner") ? n.getUUID("Owner") : null;
  }
}
