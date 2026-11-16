package factorycraft.modules;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class ModuleItem extends Item {
  public enum Kind {
    SPEED,
    EFFICIENCY,
    PRODUCTIVITY
  }

  public final Kind kind;
  public final factorycraft.simulation.modules.ModuleProfile profile;

  public ModuleItem(Kind kind, Properties properties) {
    this(kind, 1, properties);
  }

  public ModuleItem(Kind kind, int tier, Properties properties) {
    super(properties);
    this.kind = kind;
    this.profile =
        factorycraft.simulation.modules.ModuleProfile.of(
            factorycraft.simulation.modules.ModuleProfile.Family.valueOf(kind.name()), tier);
  }

  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
    super.appendHoverText(stack, context, lines, flag);
    lines.add(
        Component.translatable(
            "tooltip.factorycraft.module_values",
            profile.speed() / 100.0,
            profile.energy() / 100.0,
            profile.productivity() / 100.0));
    if (kind == Kind.EFFICIENCY)
      lines.add(Component.translatable("tooltip.factorycraft.module_minimum"));
  }
}
