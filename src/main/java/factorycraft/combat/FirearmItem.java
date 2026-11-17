package factorycraft.combat;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class FirearmItem extends Item {
  public final String profile;

  public FirearmItem(String profile, Properties p) {
    super(p);
    this.profile = profile;
  }

  public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
    var s = p.getItemInHand(hand);
    if (p.isShiftKeyDown()) {
      if (!l.isClientSide) LoadedMagazine.unload(s, p);
      return InteractionResultHolder.sidedSuccess(s, l.isClientSide);
    }
    if (!l.isClientSide) Combat.fire(p, s, profile);
    p.startUsingItem(hand);
    return InteractionResultHolder.consume(s);
  }

  public int getUseDuration(ItemStack s, net.minecraft.world.entity.LivingEntity e) {
    return 72000;
  }

  public UseAnim getUseAnimation(ItemStack s) {
    return UseAnim.BOW;
  }

  public void onUseTick(Level l, net.minecraft.world.entity.LivingEntity e, ItemStack s, int left) {
    if (!l.isClientSide && e instanceof Player p) Combat.fire(p, s, profile);
  }

  public void appendHoverText(
      ItemStack s,
      TooltipContext c,
      java.util.List<net.minecraft.network.chat.Component> out,
      TooltipFlag f) {
    out.add(
        net.minecraft.network.chat.Component.translatable(
            "tooltip.factorycraft.loaded", MagazineItem.rounds(LoadedMagazine.peek(s))));
    out.add(net.minecraft.network.chat.Component.translatable("tooltip.factorycraft.unload"));
  }

  @Override
  public boolean shouldCauseReequipAnimation(
      ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
    return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
  }
}
