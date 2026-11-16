package factorycraft.mixin;

import factorycraft.research.ResearchCrafting;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both the 2×2 inventory and workbench use this server-authoritative result update. */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
  @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"), cancellable = true)
  private static void factorycraft$research(
      AbstractContainerMenu menu,
      Level level,
      Player player,
      CraftingContainer input,
      ResultContainer output,
      RecipeHolder<CraftingRecipe> last,
      CallbackInfo ci) {
    if (level.isClientSide) return;
    var match =
        level
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input.asCraftInput(), level, last);
    if (match.isPresent() && !ResearchCrafting.allowed(player, match.get())) {
      output.setItem(0, ItemStack.EMPTY);
      output.setRecipeUsed(null);
      menu.setRemoteSlot(0, ItemStack.EMPTY);
      ((ServerPlayer) player)
          .connection.send(
              new ClientboundContainerSetSlotPacket(
                  menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
      ci.cancel();
    }
  }
}
