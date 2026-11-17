package factorycraft.combat;

import net.minecraft.core.Holder;
import net.minecraft.world.item.*;

/** Infinite source-style armor; resistance is applied once by the server damage hook. */
public final class FactoryArmorItem extends ArmorItem {
  public final boolean heavy;

  public FactoryArmorItem(Holder<ArmorMaterial> material, boolean heavy, Properties p) {
    super(material, Type.CHESTPLATE, p.stacksTo(1));
    this.heavy = heavy;
  }
}
