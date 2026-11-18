package factorycraft.trains;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Chemical source fuels; native Minecraft-only fuels retain an explicit coal-equivalent fallback.
 */
public final class TrainFuel {
  public record Value(long joules, double acceleration, double speed) {}

  public static Value value(ItemStack item) {
    if (item.isEmpty()) return null;
    String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
    if (item.is(Items.COAL) || item.is(Items.CHARCOAL)) return new Value(4_000_000, 1, 1);
    if (id.equals("factorycraft:solid_fuel")) return new Value(12_000_000, 1.2, 1.05);
    if (id.equals("factorycraft:rocket_fuel")) return new Value(100_000_000, 1.8, 1.15);
    if (id.equals("factorycraft:nuclear_fuel")) return new Value(1_210_000_000, 2.5, 1.15);
    if (item.is(ItemTags.LOGS)) return new Value(2_000_000, 1, 1);
    int time = item.getBurnTime(RecipeType.SMELTING);
    return time <= 0 ? null : new Value(Math.min(20_000_000_000L, time * 2500L), 1, 1);
  }

  private TrainFuel() {}
}
