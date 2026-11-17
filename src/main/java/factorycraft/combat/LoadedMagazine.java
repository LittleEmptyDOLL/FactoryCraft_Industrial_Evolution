package factorycraft.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;

/** The weapon's container component is the sole owner, including a partial magazine. */
public final class LoadedMagazine {
  public static ItemStack peek(ItemStack weapon) {
    var contents = weapon.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
    return contents.stream().findFirst().map(ItemStack::copy).orElse(ItemStack.EMPTY);
  }

  public static void set(ItemStack weapon, ItemStack magazine) {
    if (magazine.isEmpty()) weapon.remove(DataComponents.CONTAINER);
    else
      weapon.set(
          DataComponents.CONTAINER,
          ItemContainerContents.fromItems(java.util.List.of(magazine.copy())));
  }

  public static boolean load(ItemStack weapon, Player player, String category) {
    var held = peek(weapon);
    if (!held.isEmpty())
      return held.getCount() == 1
          && MagazineItem.rounds(held) > 0
          && held.getItem() instanceof MagazineItem m
          && m.category.equals(category);
    for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
      var source = player.getInventory().getItem(s);
      if (source.getItem() instanceof MagazineItem m
          && m.category.equals(category)
          && MagazineItem.rounds(source) > 0) {
        // Commit source removal before exposing the new sole owner. No external callbacks in
        // between.
        var one = source.split(1);
        set(weapon, one);
        player.getInventory().setChanged();
        return true;
      }
    }
    return false;
  }

  public static ItemStack spend(ItemStack weapon) {
    var magazine = peek(weapon);
    if (!MagazineItem.spend(magazine)) return ItemStack.EMPTY;
    var shot = magazine.copy();
    set(weapon, MagazineItem.rounds(magazine) == 0 ? ItemStack.EMPTY : magazine);
    return shot;
  }

  public static boolean unload(ItemStack weapon, Player p) {
    var held = peek(weapon);
    if (held.isEmpty()) return true;
    // Inventory.add may partially accept. Store exactly the actual remainder back in the weapon.
    p.getInventory().add(held);
    set(weapon, held);
    p.getInventory().setChanged();
    return held.isEmpty();
  }

  private LoadedMagazine() {}
}
