package factorycraft.fluids;

import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.registry.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Read-only status menu. Volumes/registry IDs use two 16-bit fields to avoid vanilla data-slot truncation. */
public final class FluidMenu extends AbstractContainerMenu {
    public final FluidBlockEntity machine;
    public final ContainerData data;
    public FluidMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer) { this(id,inventory,(FluidBlockEntity)inventory.player.level().getBlockEntity(buffer.readBlockPos()),new SimpleContainerData(7)); }
    public FluidMenu(int id,Inventory inventory,FluidBlockEntity machine,ContainerData data) { super(Content.FLUID_MENU.get(),id);this.machine=machine;this.data=data;addDataSlots(data); }
    public int amount() { return (data.get(0)&65535)|((data.get(1)&65535)<<16); }
    public int capacity() { return (data.get(2)&65535)|((data.get(3)&65535)<<16); }
    public int fluidId() { return (data.get(5)&65535)|((data.get(6)&65535)<<16); }
    @Override public boolean stillValid(Player player) { return machine!=null && machine.loaded() && player.level()==machine.getLevel() && player.level().getBlockEntity(machine.getBlockPos())==machine && player.distanceToSqr(machine.getBlockPos().getCenter())<=64; }
    @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
}
