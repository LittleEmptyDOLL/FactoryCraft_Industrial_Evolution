package factorycraft.combat;

import factorycraft.registry.Content;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

/** Absence of rounds means a factory-new magazine; components survive every ordinary item move. */
public final class MagazineItem extends Item {
  public final String category;
  public final int capacity;
  public final double damage;
  public final int pellets;

  public MagazineItem(String category, int capacity, double damage, Properties p) {
    this(category, capacity, damage, 0, p);
  }

  public MagazineItem(String category, int capacity, double damage, int pellets, Properties p) {
    super(
        p.component(Content.AMMO_CAPACITY.get(), capacity)
            .component(Content.AMMO_DAMAGE.get(), damage));
    this.category = category;
    this.capacity = capacity;
    this.damage = damage;
    this.pellets = pellets;
  }

  public static int rounds(ItemStack stack) {
    if (!(stack.getItem() instanceof MagazineItem m)) return 0;
    int n = stack.getOrDefault(Content.AMMO_ROUNDS.get(), capacity(stack));
    // Malformed data must never become fresh ammunition.
    return n >= 0 && n <= capacity(stack) ? n : 0;
  }

  public static int capacity(ItemStack stack) {
    return stack.getItem() instanceof MagazineItem m
        ? stack.getOrDefault(Content.AMMO_CAPACITY.get(), m.capacity)
        : 0;
  }

  public static double damage(ItemStack stack) {
    return stack.getItem() instanceof MagazineItem m
        ? stack.getOrDefault(Content.AMMO_DAMAGE.get(), m.damage)
        : 0;
  }

  public static boolean spend(ItemStack stack) {
    int n = rounds(stack);
    if (stack.getCount() != 1 || n <= 0) return false;
    stack.set(Content.AMMO_ROUNDS.get(), n - 1);
    return true;
  }

  public void appendHoverText(ItemStack s, TooltipContext c, List<Component> out, TooltipFlag f) {
    out.add(Component.translatable("tooltip.factorycraft.rounds", rounds(s), capacity(s)));
  }
}
