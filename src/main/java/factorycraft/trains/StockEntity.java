package factorycraft.trains;

import factorycraft.simulation.trains.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** One interaction/render handle per car. No resource data is saved here. */
public final class StockEntity extends Entity {
  private static final EntityDataAccessor<Integer>
      KIND = SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.INT),
      COLOR = SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Optional<UUID>> TRAIN =
      SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.OPTIONAL_UUID);
  private static final EntityDataAccessor<Float>
      FRONT_YAW = SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.FLOAT),
      REAR_YAW = SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.FLOAT),
      WHEELS = SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.FLOAT),
      SPEED = SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.FLOAT),
      HEALTH = SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float>
      BOGIE_DISTANCE = SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.FLOAT),
      FRONT_PITCH = SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.FLOAT),
      REAR_PITCH = SynchedEntityData.defineId(StockEntity.class, EntityDataSerializers.FLOAT);
  private long lastVisual = Long.MIN_VALUE;
  private double lerpX, lerpY, lerpZ;
  private float lerpYaw, lerpPitch;
  private int lerpSteps, visualSteps;
  private float visualFront,
      visualRear,
      oldFront,
      oldRear,
      visualFrontPitch,
      visualRearPitch,
      oldFrontPitch,
      oldRearPitch;
  private float wheelVisual, wheelPrevious, wheelTarget;
  private boolean visualInitialized;

  public float frontYaw(float partial) {
    return net.minecraft.util.Mth.rotLerp(partial, oldFront, visualFront);
  }

  public float rearYaw(float partial) {
    return net.minecraft.util.Mth.rotLerp(partial, oldRear, visualRear);
  }

  public float frontPitch(float partial) {
    return net.minecraft.util.Mth.lerp(partial, oldFrontPitch, visualFrontPitch);
  }

  public float rearPitch(float partial) {
    return net.minecraft.util.Mth.lerp(partial, oldRearPitch, visualRearPitch);
  }

  public float wheelPhase(float partial) {
    return net.minecraft.util.Mth.lerp(partial, wheelPrevious, wheelVisual);
  }

  public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
    super.onSyncedDataUpdated(accessor);
    if (level().isClientSide
        && (accessor == WHEELS || accessor == FRONT_YAW || accessor == REAR_YAW)) {
      if (!visualInitialized) {
        visualInitialized = true;
        visualFront = oldFront = frontYaw();
        visualRear = oldRear = rearYaw();
        wheelVisual = wheelPrevious = wheels();
      }
      visualSteps = 3;
      float delta = wheels() - wheelVisual;
      delta = (delta + 1024) % 2048;
      if (delta < 0) delta += 2048;
      wheelTarget = wheelVisual + delta - 1024;
    }
  }

  public StockEntity(EntityType<? extends StockEntity> type, Level level, StockSpec kind) {
    super(type, level);
    noPhysics = true;
    entityData.set(KIND, kind.ordinal());
  }

  protected void defineSynchedData(SynchedEntityData.Builder b) {
    b.define(KIND, 0);
    b.define(COLOR, 0xb45c27);
    b.define(TRAIN, Optional.empty());
    b.define(FRONT_YAW, 0f);
    b.define(REAR_YAW, 0f);
    b.define(WHEELS, 0f);
    b.define(SPEED, 0f);
    b.define(HEALTH, 1000f);
    b.define(BOGIE_DISTANCE, 4f);
    b.define(FRONT_PITCH, 0f);
    b.define(REAR_PITCH, 0f);
  }

  public StockSpec kind() {
    return StockSpec.values()[Math.clamp(entityData.get(KIND), 0, 2)];
  }

  public UUID trainId() {
    return entityData.get(TRAIN).orElse(new UUID(0, 0));
  }

  public void bind(UUID id) {
    entityData.set(TRAIN, Optional.of(id));
  }

  public int color() {
    return entityData.get(COLOR);
  }

  public float frontYaw() {
    return entityData.get(FRONT_YAW);
  }

  public float rearYaw() {
    return entityData.get(REAR_YAW);
  }

  public float wheels() {
    return entityData.get(WHEELS);
  }

  public void pauseVisual() {
    entityData.set(SPEED, 0f);
  }

  public float speed() {
    return entityData.get(SPEED);
  }

  public float bogieDistance() {
    return entityData.get(BOGIE_DISTANCE);
  }

  public float frontPitch() {
    return entityData.get(FRONT_PITCH);
  }

  public float rearPitch() {
    return entityData.get(REAR_PITCH);
  }

  static float yaw(factorycraft.simulation.rail.RailVector f) {
    return (float) Math.toDegrees(Math.atan2(-f.x(), f.z()));
  }

  public void authoritative(BogiePose pose, TrainCar car, double distance, double speed) {
    var p = pose.center();
    setPos(p.x(), p.y(), p.z());
    setYRot(yaw(pose.forward()));
    setXRot(
        (float)
            -Math.toDegrees(
                Math.atan2(
                    pose.forward().y(), Math.hypot(pose.forward().x(), pose.forward().z()))));
    if (lastVisual == Long.MIN_VALUE
        || level().getGameTime() - lastVisual >= 3
        || car.color != color()
        || (float) car.health != entityData.get(HEALTH)
        || speed == 0 && speed() != 0) {
      lastVisual = level().getGameTime();
      entityData.set(
          BOGIE_DISTANCE, (float) pose.front().position().distance(pose.rear().position()));
      entityData.set(
          FRONT_PITCH,
          (float)
              (-Math.toDegrees(Math.asin(pose.front().tangent().y())) * (car.reversed ? -1 : 1)));
      entityData.set(
          REAR_PITCH,
          (float)
              (-Math.toDegrees(Math.asin(pose.rear().tangent().y())) * (car.reversed ? -1 : 1)));
      entityData.set(COLOR, car.color);
      entityData.set(HEALTH, (float) car.health);
      entityData.set(FRONT_YAW, yaw(pose.front().tangent()) + (car.reversed ? 180 : 0));
      entityData.set(REAR_YAW, yaw(pose.rear().tangent()) + (car.reversed ? 180 : 0));
      entityData.set(WHEELS, (float) ((car.reversed ? -distance : distance) % 2048));
      entityData.set(SPEED, (float) speed);
    }
    setBoundingBox(TrainWorld.box(pose));
    setDeltaMovement(Vec3.ZERO);
  }

  public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
    lerpX = x;
    lerpY = y;
    lerpZ = z;
    lerpYaw = yaw;
    lerpPitch = pitch;
    lerpSteps = Math.min(steps, 5);
  }

  public void tick() {
    super.tick();
    setDeltaMovement(Vec3.ZERO);
    if (level().isClientSide) {
      oldFront = visualFront;
      oldRear = visualRear;
      oldFrontPitch = visualFrontPitch;
      oldRearPitch = visualRearPitch;
      wheelPrevious = wheelVisual;
      if (visualSteps > 0) {
        int n = visualSteps--;
        visualFront += net.minecraft.util.Mth.wrapDegrees(frontYaw() - visualFront) / n;
        visualRear += net.minecraft.util.Mth.wrapDegrees(rearYaw() - visualRear) / n;
        visualFrontPitch += (frontPitch() - visualFrontPitch) / n;
        visualRearPitch += (rearPitch() - visualRearPitch) / n;
        wheelVisual += (wheelTarget - wheelVisual) / n;
      }
      if (lerpSteps > 0) {
        double n = lerpSteps--;
        setPos(
            getX() + (lerpX - getX()) / n,
            getY() + (lerpY - getY()) / n,
            getZ() + (lerpZ - getZ()) / n);
        setYRot(getYRot() + net.minecraft.util.Mth.wrapDegrees(lerpYaw - getYRot()) / (float) n);
        setXRot(getXRot() + (lerpPitch - getXRot()) / (float) n);
      }
      updateBox();
      return;
    }
    var l = (ServerLevel) level();
    var d = TrainData.get(l);
    var t = d.owner(getUUID());
    if (t == null) {
      discard();
      return;
    }
    if (l.getEntity(getUUID()) != this) {
      discard();
      return;
    }
    bind(t.id);
    var car = d.car(getUUID());
    if (car != null
        && car.recovery != null
        && position().distanceToSqr(TrainWorld.vec(car.recovery)) > 1e-8) d.active.add(t.id);
  }

  private void updateBox() {
    double a = Math.toRadians(getYRot());
    double x = Math.abs(Math.sin(a)) * 3.5 + Math.abs(Math.cos(a)) * 1.5,
        z = Math.abs(Math.cos(a)) * 3.5 + Math.abs(Math.sin(a)) * 1.5;
    setBoundingBox(
        new AABB(getX() - x, getY() + .02, getZ() - z, getX() + x, getY() + 4, getZ() + z));
  }

  public void onAddedToLevel() {
    super.onAddedToLevel();
    if (level() instanceof ServerLevel l) {
      var d = TrainData.get(l);
      var t = d.owner(getUUID());
      if (t != null) d.active.add(t.id);
    }
  }

  public boolean canUsePortal(boolean allowPassengers) {
    return false;
  }

  public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
    return false;
  }

  public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
    if (level() instanceof ServerLevel l) {
      double yaw = Math.toRadians(getYRot());
      for (int side : new int[] {1, -1})
        for (double lift : new double[] {0, 1, 2}) {
          var candidate =
              getPassengerRidingPosition(passenger)
                  .subtract(passenger.getVehicleAttachmentPoint(this))
                  .add(Math.cos(yaw) * side * 2.1, lift, Math.sin(yaw) * side * 2.1);
          var b = passenger.getBoundingBox().move(candidate.subtract(passenger.position()));
          boolean loaded = true;
          for (var pos :
              net.minecraft.core.BlockPos.betweenClosed(
                  net.minecraft.core.BlockPos.containing(b.minX, b.minY, b.minZ),
                  net.minecraft.core.BlockPos.containing(b.maxX, b.maxY, b.maxZ)))
            if (!l.hasChunkAt(pos)) {
              loaded = false;
              break;
            }
          if (loaded && l.noCollision(passenger, b)) return candidate;
        }
    }
    return super.getDismountLocationForPassenger(passenger);
  }

  public boolean isPickable() {
    return true;
  }

  public boolean isPushable() {
    return false;
  }

  public boolean canBeCollidedWith() {
    return true;
  }

  public void push(double x, double y, double z) {}

  protected boolean canAddPassenger(Entity p) {
    return kind() == StockSpec.LOCOMOTIVE && getPassengers().isEmpty() && p instanceof Player;
  }

  public Vec3 getPassengerRidingPosition(Entity p) {
    double a = Math.toRadians(getYRot());
    return position()
        .add(-Math.sin(a) * -1.9, 1.05, Math.cos(a) * -1.9)
        .add(p.getVehicleAttachmentPoint(this));
  }

  public Component getName() {
    return Component.translatable("item.factorycraft." + kind().id);
  }

  public InteractionResult interact(Player player, InteractionHand hand) {
    if (!(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
    var d = TrainData.get(p.serverLevel());
    var t = d.owner(getUUID());
    if (t == null || !TrainWorld.mayUse(t, p) || p.distanceToSqr(this) > 64)
      return InteractionResult.FAIL;
    var held = p.getItemInHand(hand);
    if (net.minecraft.core.registries.BuiltInRegistries.ITEM
        .getKey(held.getItem())
        .toString()
        .equals("factorycraft:wrench")) {
      if (p.isShiftKeyDown()) StockPlacement.split(d, t, t.cars.indexOf(d.car(getUUID())));
      else StockPlacement.pack(d, t, getUUID(), p);
      return InteractionResult.CONSUME;
    }
    if (!d.stopped(t)) return InteractionResult.FAIL;
    if (kind() == StockSpec.LOCOMOTIVE && !p.isShiftKeyDown() && d.car(getUUID()).health > 0) {
      if (p.startRiding(this)) p.connection.teleport(p.getX(), p.getY(), p.getZ(), getYRot(), 0);
      return InteractionResult.CONSUME;
    }
    p.openMenu(
        new net.minecraft.world.SimpleMenuProvider(
            (id, inv, who) -> new TrainMenu(id, inv, this), getName()),
        buf -> {
          buf.writeInt(getId());
          buf.writeVarInt(kind().ordinal());
        });
    return InteractionResult.CONSUME;
  }

  public boolean hurt(DamageSource source, float amount) {
    if (!(level() instanceof ServerLevel l) || amount <= 0) return false;
    return StockPlacement.damage(TrainData.get(l), this, source, amount);
  }

  protected void addAdditionalSaveData(CompoundTag n) {
    if (entityData.get(TRAIN).isPresent()) n.putUUID("Train", trainId());
  }

  protected void readAdditionalSaveData(CompoundTag n) {
    if (n.hasUUID("Train")) bind(n.getUUID("Train"));
  }
}
