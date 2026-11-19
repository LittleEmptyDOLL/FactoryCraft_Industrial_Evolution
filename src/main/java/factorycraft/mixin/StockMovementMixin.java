package factorycraft.mixin;

import factorycraft.trains.TrainCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class StockMovementMixin {
  @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
  private void factorycraft$orientedMotion(Vec3 requested, CallbackInfoReturnable<Vec3> cir) {
    cir.setReturnValue(TrainCollision.clipMotion((Entity) (Object) this, cir.getReturnValue()));
  }
}
