package factorycraft.items;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;

/** Factorio solid fuel is three coal units: retain that ratio in native Minecraft fuel ticks. */
public final class SolidFuelItem extends Item {
  public SolidFuelItem(Properties properties) {
    super(properties);
  }

  @Override
  public int getBurnTime(ItemStack stack, RecipeType<?> recipe) {
    return 4800;
  }
}
