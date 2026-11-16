package factorycraft.research;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Twelve stable physical identities cover the complete canonical Space Age science set. */
public final class SciencePacks {
  public static final List<ResourceLocation> IDS =
      List.of(
              "automation",
              "logistic",
              "chemical",
              "military",
              "production",
              "utility",
              "space",
              "metallurgic",
              "agricultural",
              "electromagnetic",
              "cryogenic",
              "promethium")
          .stream()
          .map(n -> ResourceLocation.fromNamespaceAndPath("factorycraft", n + "_science_pack"))
          .toList();
  public static final int INITIAL_COUNT = 7, COUNT = 12;

  public static boolean accepts(int slot, ItemStack stack) {
    return slot >= 0
        && slot < COUNT
        && !stack.isEmpty()
        && IDS.get(slot).equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
  }

  public static boolean future(ResourceLocation id) {
    return IDS.indexOf(id) >= 3;
  }

  private SciencePacks() {}
}
