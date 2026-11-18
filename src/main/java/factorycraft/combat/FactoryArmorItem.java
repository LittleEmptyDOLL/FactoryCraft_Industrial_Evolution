package factorycraft.combat;

import net.minecraft.core.Holder;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** One source-style chest item, with exact typed resistance attributes and no durability. */
public final class FactoryArmorItem extends ArmorItem {
  public final boolean heavy;
  private ItemAttributeModifiers sourceModifiers;

  public FactoryArmorItem(Holder<ArmorMaterial> material, boolean heavy, Properties p) {
    super(material, Type.CHESTPLATE, p.stacksTo(1));
    this.heavy = heavy;
  }

  @Override
  public ItemAttributeModifiers getDefaultAttributeModifiers() {
    if (sourceModifiers == null) sourceModifiers = ArmorAttributes.armor(heavy);
    return sourceModifiers;
  }
}
