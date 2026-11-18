package factorycraft.trains;

import factorycraft.simulation.trains.StockSpec;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

public final class StockItem extends Item {
  public final StockSpec kind;

  public StockItem(StockSpec kind, Properties p) {
    super(p);
    this.kind = kind;
  }

  public InteractionResult useOn(UseOnContext c) {
    if (c.getPlayer() instanceof ServerPlayer p) {
      String result = StockPlacement.place(p, c, kind);
      p.displayClientMessage(Component.translatable("train.factorycraft." + result), true);
      return result.equals("placed") ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
    return InteractionResult.SUCCESS;
  }

  public void appendHoverText(ItemStack s, TooltipContext c, List<Component> lines, TooltipFlag f) {
    lines.add(Component.translatable("train.factorycraft.item_help"));
    if (kind == StockSpec.CARGO)
      lines.add(Component.translatable("train.factorycraft.cargo_slots", 40));
    if (kind == StockSpec.FLUID)
      lines.add(Component.translatable("train.factorycraft.fluid_capacity", 500000));
  }
}
