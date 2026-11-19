package factorycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import factorycraft.trains.*;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep vanilla discovery, filters and rider rules, replacing only stock's precise ray test. */
@Mixin(ProjectileUtil.class)
abstract class StockPickingMixin {
  @WrapOperation(
      method = {
        "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
        "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
  private static Optional<Vec3> factorycraft$clip(
      AABB box,
      Vec3 from,
      Vec3 to,
      Operation<Optional<Vec3>> original,
      @Local(ordinal = 2) Entity candidate) {
    return candidate instanceof StockEntity stock
        ? TrainCollision.clip(stock, from, to)
        : original.call(box, from, to);
  }

  @WrapOperation(
      method =
          "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z"))
  private static boolean factorycraft$contains(
      AABB box, Vec3 point, Operation<Boolean> original, @Local(ordinal = 2) Entity candidate) {
    return candidate instanceof StockEntity stock
        ? TrainCollision.clip(stock, point, point).isPresent()
        : original.call(box, point);
  }
}
