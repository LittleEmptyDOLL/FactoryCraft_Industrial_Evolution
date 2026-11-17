package factorycraft.rail;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class RailItem extends Item {
  public RailItem(Properties p) {
    super(p);
  }

  @Override
  public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
    return useOn(context);
  }

  public InteractionResult useOn(UseOnContext c) {
    if (c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer p) {
      if (p.isShiftKeyDown()) RailPlanner.clear(p);
      else {
        var hit =
            new BlockHitResult(
                c.getClickLocation(), c.getClickedFace(), c.getClickedPos(), c.isInside());
        String result = RailPlanner.click(p, hit);
        p.displayClientMessage(Component.translatable("rail.factorycraft." + result), true);
      }
    }
    return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
  }

  public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
    if (p.isShiftKeyDown() && p instanceof net.minecraft.server.level.ServerPlayer s)
      RailPlanner.clear(s);
    return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), l.isClientSide);
  }

  public void appendHoverText(ItemStack s, TooltipContext c, List<Component> text, TooltipFlag f) {
    text.add(Component.translatable("rail.factorycraft.controls"));
    text.add(Component.translatable("rail.factorycraft.dimensions"));
  }
}
