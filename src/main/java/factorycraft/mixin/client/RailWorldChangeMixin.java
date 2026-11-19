package factorycraft.mixin.client;

import factorycraft.client.rail.RailClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class RailWorldChangeMixin {
  @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
  private void factorycraft$invalidateRailPreview(
      BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
    if (oldState != newState
        || oldState.is(factorycraft.rail.RailContent.BED.get())
        || newState.is(factorycraft.rail.RailContent.BED.get())) RailClient.changed(pos);
  }
}
