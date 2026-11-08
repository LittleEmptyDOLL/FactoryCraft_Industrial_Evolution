package factorycraft.logistics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A ghost filter is an item identifier, never an owned inventory stack. */
public record SplitterSettings(int input, int output, ResourceLocation filter) {
  public static final SplitterSettings DEFAULT = new SplitterSettings(0, 0, null);

  public SplitterSettings {
    if (input < 0 || input > 2 || output < 0 || output > 2)
      throw new IllegalArgumentException("Invalid splitter priority");
    if (filter != null && output == 0) output = 1;
  }

  public boolean matches(ItemStack stack) {
    return filter != null && filter.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
  }

  public ItemStack icon() {
    return filter == null
        ? ItemStack.EMPTY
        : BuiltInRegistries.ITEM.getOptional(filter).map(ItemStack::new).orElse(ItemStack.EMPTY);
  }

  public CompoundTag save() {
    var tag = new CompoundTag();
    tag.putInt("Schema", 1);
    tag.putInt("Input", input);
    tag.putInt("Output", output);
    if (filter != null) tag.putString("Filter", filter.toString());
    return tag;
  }

  public static SplitterSettings load(CompoundTag tag) {
    if (tag.isEmpty()) return DEFAULT;
    var id = tag.contains("Filter") ? ResourceLocation.tryParse(tag.getString("Filter")) : null;
    return new SplitterSettings(
        Math.clamp(tag.getInt("Input"), 0, 2), Math.clamp(tag.getInt("Output"), 0, 2), id);
  }
}
