package factorycraft.thermal;

import factorycraft.blockentity.ThermalBlockEntity;
import factorycraft.registry.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class ThermalMenu extends AbstractContainerMenu {
    public final ThermalBlockEntity machine;
    public final ContainerData data;
    public ThermalMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer) { this(id,inventory,(ThermalBlockEntity)inventory.player.level().getBlockEntity(buffer.readBlockPos()),new SimpleContainerData(13)); }
    public ThermalMenu(int id,Inventory inventory,ThermalBlockEntity machine,ContainerData data) {
        super(Content.THERMAL_MENU.get(),id);this.machine=machine;this.data=data;addDataSlots(data);
        addSlot(new SlotItemHandler(machine.items,0,17,79) { @Override public boolean isActive() { return machine.kind()==ThermalKind.BOILER; } @Override public boolean mayPlace(ItemStack stack) { return machine.items.isItemValid(0,stack); } });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,17+col*18,122+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,17+col*18,180));
    }
    public int value(int index) { return (data.get(index)&65535)|((data.get(index+1)&65535)<<16); }
    @Override public boolean stillValid(Player player) { return machine!=null && machine.available() && player.level()==machine.getLevel() && player.level().getBlockEntity(machine.getBlockPos())==machine && player.distanceToSqr(machine.getBlockPos().getCenter())<=64; }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index<0 || index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var original=stack.copy();
        if(index==0) { if(!moveItemStackTo(stack,1,37,true))return ItemStack.EMPTY; }
        else if(!machine.items.isItemValid(0,stack) || !moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return original;
    }
}
