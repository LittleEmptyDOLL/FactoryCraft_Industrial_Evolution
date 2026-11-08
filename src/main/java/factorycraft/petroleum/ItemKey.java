package factorycraft.petroleum;

import net.minecraft.world.item.ItemStack;

/** Snapshot identity includes item components; every outward stack is a copy. */
public final class ItemKey {
    private final ItemStack template;
    public ItemKey(ItemStack stack) { if(stack.isEmpty())throw new IllegalArgumentException("Empty item key");template=stack.copyWithCount(1); }
    public ItemStack stack(int count) { return template.copyWithCount(count); }
    @Override public boolean equals(Object o) { return o instanceof ItemKey key && ItemStack.isSameItemSameComponents(template,key.template); }
    @Override public int hashCode() { return ItemStack.hashItemAndComponents(template); }
}
