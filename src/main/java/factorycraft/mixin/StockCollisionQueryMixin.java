package factorycraft.mixin;

import factorycraft.trains.TrainCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CollisionGetter.class)
interface StockCollisionQueryMixin {
  @Inject(
      method = "noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z",
      at = @At("RETURN"),
      cancellable = true)
  private void factorycraft$orientedQuery(
      Entity entity, AABB box, CallbackInfoReturnable<Boolean> cir) {
    if (cir.getReturnValue()
        && (Object) this instanceof Level level
        && TrainCollision.occupied(level, entity, box)) cir.setReturnValue(false);
  }
}
