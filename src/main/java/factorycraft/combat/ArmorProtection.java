package factorycraft.combat;

import factorycraft.FactoryCraft;
import factorycraft.simulation.combat.Resistance;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = FactoryCraft.ID)
public final class ArmorProtection {
  @SubscribeEvent
  public static void incoming(LivingIncomingDamageEvent e) {
    if (e.getEntity().level().isClientSide
        || !(e.getEntity().getItemBySlot(EquipmentSlot.CHEST).getItem()
            instanceof FactoryArmorItem armor)) return;
    var s = e.getSource();
    boolean fire = s.is(DamageTypeTags.IS_FIRE), blast = s.is(DamageTypeTags.IS_EXPLOSION);
    boolean physical =
        s.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)
            || s.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK_NO_AGGRO)
            || s.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
            || s.is(net.minecraft.world.damagesource.DamageTypes.ARROW)
            || s.is(net.minecraft.world.damagesource.DamageTypes.TRIDENT)
            || s.is(net.minecraft.world.damagesource.DamageTypes.STING)
            || s.typeHolder()
                .unwrapKey()
                .map(k -> k.location().toString().equals("factorycraft:physical"))
                .orElse(false);
    if (!fire && !blast && !physical) return;
    double flat = fire ? 0 : blast ? (armor.heavy ? 20 : 2) : (armor.heavy ? 6 : 3);
    double pct = fire ? (armor.heavy ? .3 : .1) : (armor.heavy ? .3 : .2);
    // Convert to Factorio HP before the flat-resistance formula, then back to Minecraft HP.
    e.setAmount(
        (float) (Resistance.apply(e.getAmount() / Combat.HP_SCALE, flat, pct) * Combat.HP_SCALE));
  }
}
