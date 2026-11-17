package factorycraft.mixin.client;

import factorycraft.combat.FirearmItem;
import factorycraft.registry.Content;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Presentation only; recoil follows the server-confirmed shot component. */
@Mixin(HumanoidModel.class)
public abstract class WeaponPoseMixin {
  @Shadow @Final public ModelPart rightArm;
  @Shadow @Final public ModelPart leftArm;

  @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
  private void factorycraft$recoil(
      LivingEntity entity,
      float walk,
      float speed,
      float age,
      float yaw,
      float pitch,
      CallbackInfo ci) {
    var stack = entity.getMainHandItem();
    var arm = entity.getMainArm();
    if (!(stack.getItem() instanceof FirearmItem)) {
      stack = entity.getOffhandItem();
      arm = arm.getOpposite();
    }
    if (!(stack.getItem() instanceof FirearmItem)) return;
    long shot = stack.getOrDefault(Content.SHOT_TIME.get(), Long.MIN_VALUE);
    if (shot == Long.MIN_VALUE) return;
    double recoil =
        Math.max(0, 1 - (entity.level().getGameTime() + age - Math.floor(age) - shot) / 3.0);
    (arm == HumanoidArm.RIGHT ? rightArm : leftArm).xRot -= (float) recoil * .12f;
  }
}
