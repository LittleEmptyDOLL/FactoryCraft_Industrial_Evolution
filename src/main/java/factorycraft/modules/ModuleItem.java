package factorycraft.modules;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class ModuleItem extends Item {
  public enum Kind {
    SPEED(2000, 5000, 0),
    EFFICIENCY(0, -3000, 0),
    PRODUCTIVITY(-500, 4000, 400);
    public final int speed, energy, productivity;

    Kind(int speed, int energy, int productivity) {
      this.speed = speed;
      this.energy = energy;
      this.productivity = productivity;
    }
  }

  public final Kind kind;

  public ModuleItem(Kind kind, Properties properties) {
    super(properties);
    this.kind = kind;
  }

  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
    super.appendHoverText(stack, context, lines, flag);
    lines.add(
        Component.translatable(
            "tooltip.factorycraft.module_" + kind.name().toLowerCase(java.util.Locale.ROOT)));
  }
}
