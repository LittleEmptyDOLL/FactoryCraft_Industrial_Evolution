package factorycraft.mixin;

import factorycraft.research.ResearchCrafting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Recheck the result at pickup, including shift-click, after research/datapack changes. */
@Mixin(Slot.class)
public abstract class CraftingSlotMixin {
  @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
  private void factorycraft$research(Player player, CallbackInfoReturnable<Boolean> ci) {
    if (((Slot) (Object) this).container instanceof ResultContainer result
        && !ResearchCrafting.allowed(player, result.getRecipeUsed())) ci.setReturnValue(false);
  }
}
