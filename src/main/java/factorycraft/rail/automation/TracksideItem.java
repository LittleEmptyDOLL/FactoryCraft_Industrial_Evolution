package factorycraft.rail.automation;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

/** One item forms the entire device, with a common physical-side placement plan. */
public final class TracksideItem extends Item {
  public final TracksideKind kind;

  public TracksideItem(TracksideKind kind, Properties properties) {
    super(properties);
    this.kind = kind;
  }

  public InteractionResult onItemUseFirst(ItemStack s, UseOnContext c) {
    return useOn(c);
  }

  public InteractionResult useOn(UseOnContext c) {
    if (c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer p) {
      var plan =
          TracksidePlacement.plan(c.getLevel(), TracksidePlacement.target(c), kind, p.getYRot());
      String result = TracksidePlacement.place(p, c.getHand(), plan);
      p.displayClientMessage(Component.translatable("rail.factorycraft." + result), true);
    }
    return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
  }

  public void appendHoverText(ItemStack s, TooltipContext c, List<Component> text, TooltipFlag f) {
    text.add(Component.translatable("automation.factorycraft.placement_help"));
  }
}
