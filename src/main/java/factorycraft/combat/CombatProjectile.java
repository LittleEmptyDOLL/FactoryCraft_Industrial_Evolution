package factorycraft.combat;

import factorycraft.registry.Content;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** One entity for a rocket/grenade, never for hitscan bullets or individual flame particles. */
public final class CombatProjectile extends ThrowableItemProjectile {
  private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> VISUAL_MODE =
      net.minecraft.network.syncher.SynchedEntityData.defineId(
          CombatProjectile.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

  @Override
  protected void defineSynchedData(
      net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(VISUAL_MODE, 0);
  }

  private String scope = "unavailable";
  private double amount = 0;
  private double rangeLeft = 36, areaDamage;
  private boolean paid;
  private int age, mode;

  public CombatProjectile(EntityType<? extends CombatProjectile> type, Level l) {
    super(type, l);
  }

  public CombatProjectile(
      ServerLevel l, LivingEntity owner, ItemStack item, String scope, double amount, int mode) {
    super(Content.COMBAT_PROJECTILE.get(), owner, l);
    this.scope = scope;
    this.amount = amount;
    this.mode = mode;
    entityData.set(VISUAL_MODE, mode);
    this.paid = true;
    this.rangeLeft = mode == 1 ? 15 : 36;
    this.areaDamage =
        (mode == 1 ? 35 : 100)
            * (1 + CombatResearch.bonus(l, scope, "ammo-damage", mode == 1 ? "grenade" : "rocket"));
    setItem(item);
  }

  public void range(double range) {
    if (Double.isFinite(range) && range > 0 && range <= 128) rangeLeft = range;
  }

  protected Item getDefaultItem() {
    return Items.FIRE_CHARGE;
  }

  protected double getDefaultGravity() {
    return entityData.get(VISUAL_MODE) == 1 ? .03 : 0;
  }

  public void tick() {
    if (!level().isClientSide) {
      var l = (ServerLevel) level();
      if (!paid || ++age > 400) {
        discard();
        return;
      }
      if (!Combat.loadedRay(l, position(), position().add(getDeltaMovement()))) return;
      if (!paid) {
        discard();
        return;
      }
      double step = getDeltaMovement().length();
      // A grenade's source throwing range is not an invisible collision surface.
      // Its Minecraft adaptation detonates on real collision or a saved 100-tick fuse.
      if (mode == 1 && age >= 100) {
        explodeAt(
            new HitResult(position()) {
              public Type getType() {
                return Type.MISS;
              }
            });
        return;
      }
      if (mode == 0 && step >= rangeLeft) {
        var end = position().add(getDeltaMovement().normalize().scale(rangeLeft));
        setPos(end);
        discard(); // Rocket range exhaustion is expiration, never a fabricated hit.
        return;
      }
      if (mode == 0) rangeLeft -= step;
    }
    super.tick();
  }

  protected void onHit(HitResult hit) {
    super.onHit(hit);
    if (hit.getType() != HitResult.Type.MISS) explodeAt(hit);
  }

  private void explodeAt(HitResult hit) {
    if (!(level() instanceof ServerLevel l) || isRemoved() || !paid) return;
    paid = false;
    boolean explosive =
        mode == 1
            || net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(getItem().getItem())
                .getPath()
                .equals("explosive_rocket");
    // Entity-only blasts deliberately obey LOS and friendly-fire policy; no block-grief bypass.
    if (hit instanceof EntityHitResult e)
      Combat.damage(l, e.getEntity(), this, getOwner(), scope, "explosion", amount, false);
    double radius = explosive ? 6.5 : 0;
    if (radius > 0)
      for (var target :
          l.getEntities(
              (net.minecraft.world.entity.Entity) null,
              new AABB(hit.getLocation(), hit.getLocation()).inflate(radius),
              target -> Combat.allowed(l, target, getOwner(), scope, false)))
        if (target.position().distanceToSqr(hit.getLocation()) <= radius * radius
            && Combat.sight(l, hit.getLocation().add(0, .05, 0), target))
          Combat.damageAt(
              l,
              target,
              null,
              getOwner(),
              scope,
              "explosion",
              areaDamage,
              false,
              hit.getLocation());
    l.sendParticles(
        ParticleTypes.EXPLOSION,
        hit.getLocation().x,
        hit.getLocation().y,
        hit.getLocation().z,
        1,
        0,
        0,
        0,
        0);
    l.playSound(
        null,
        blockPosition(),
        net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
        net.minecraft.sounds.SoundSource.PLAYERS,
        1,
        1);
    discard();
  }

  public void addAdditionalSaveData(CompoundTag n) {
    super.addAdditionalSaveData(n);
    n.putString("Force", scope);
    n.putDouble("Damage", amount);
    n.putInt("Age", age);
    n.putInt("Mode", mode);
    n.putBoolean("Paid", paid);
    n.putDouble("RangeLeft", rangeLeft);
    n.putDouble("AreaDamage", areaDamage);
  }

  public void readAdditionalSaveData(CompoundTag n) {
    super.readAdditionalSaveData(n);
    scope = n.getString("Force");
    amount = n.getDouble("Damage");
    age = n.getInt("Age");
    mode = n.getInt("Mode");
    entityData.set(VISUAL_MODE, mode);
    paid = n.getBoolean("Paid");
    rangeLeft = n.getDouble("RangeLeft");
    areaDamage = n.getDouble("AreaDamage");
    if (!Double.isFinite(rangeLeft)
        || rangeLeft < 0
        || rangeLeft > 128
        || !Double.isFinite(areaDamage)
        || areaDamage < 0
        || areaDamage > 10000) paid = false;
    if (age < 0 || age > 400) paid = false;
    if (!Double.isFinite(amount) || amount < 0 || amount > 10000 || mode < 0 || mode > 1)
      paid = false;
  }
}
