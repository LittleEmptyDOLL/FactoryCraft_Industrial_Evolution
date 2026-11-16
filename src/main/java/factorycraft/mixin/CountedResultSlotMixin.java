package factorycraft.mixin;

import factorycraft.recipes.CountedCraftingRecipe;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only FactoryCraft counted recipes override the vanilla one-item-per-slot debit. */
@Mixin(ResultSlot.class)
public abstract class CountedResultSlotMixin {
  @Shadow @Final private CraftingContainer craftSlots;

  @Shadow
  protected abstract void checkTakeAchievements(ItemStack stack);

  @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
  private void factorycraft$counted(Player player, ItemStack result, CallbackInfo ci) {
    var match =
        player
            .level()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, craftSlots.asCraftInput(), player.level());
    if (match.isEmpty() || !(match.get().value() instanceof CountedCraftingRecipe recipe)) return;
    var positioned = craftSlots.asPositionedCraftInput();
    var input = positioned.input();
    var plan = recipe.plan(input);
    if (plan == null) throw new IllegalStateException("Counted crafting changed before commit");
    // No external inventory participates; all inputs are rechecked and committed on the server
    // thread.
    checkTakeAchievements(result);
    for (int slot = 0; slot < plan.length; slot++)
      if (plan[slot] > 0)
        craftSlots.removeItem(
            slot % input.width()
                + positioned.left()
                + (slot / input.width() + positioned.top()) * craftSlots.getWidth(),
            plan[slot]);
    ci.cancel();
  }
}
