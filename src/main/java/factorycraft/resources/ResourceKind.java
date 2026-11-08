package factorycraft.resources;

import net.minecraft.world.item.*;

public enum ResourceKind {
    IRON("iron_ore",Items.RAW_IRON),COPPER("copper_ore",Items.RAW_COPPER),COAL("coal_deposit",Items.COAL),STONE("stone_deposit",Items.COBBLESTONE);
    public final String id;private final Item result;
    ResourceKind(String id,Item result) { this.id=id;this.result=result; }
    public ItemStack result() { return new ItemStack(result); }
}
