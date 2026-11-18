package factorycraft.combat;

import factorycraft.FactoryCraft;
import factorycraft.simulation.combat.Resistance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * One source-equivalent armor resistance pass, before ordinary vanilla/Apothic armor rules.
 * Attribute values may come from FactoryCraft chest armor, other items, or later modules.
 */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class ArmorProtection {
  @SubscribeEvent
  public static void incoming(LivingIncomingDamageEvent event) {
    if (event.getEntity().level().isClientSide || event.getAmount() <= 0) return;
    var kind = ArmorAttributes.classify(event.getSource());
    if (kind == null) return;
    var target = event.getEntity();
    if (target.getAttribute(ArmorAttributes.flat(kind)) == null
        || target.getAttribute(ArmorAttributes.percent(kind)) == null) return;
    double flat = target.getAttributeValue(ArmorAttributes.flat(kind));
    double percent = target.getAttributeValue(ArmorAttributes.percent(kind));
    if (flat == 0 && percent == 0) return;
    // Values are displayed in Factorio source HP units, not Minecraft hearts.
    double sourceDamage = event.getAmount() / Combat.HP_SCALE;
    double after = Resistance.apply(sourceDamage, flat, percent) * Combat.HP_SCALE;
    event.setAmount((float) after);
  }
}
