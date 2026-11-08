package factorycraft.petroleum;

import factorycraft.blockentity.MachineBlockEntity;
import factorycraft.registry.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class PetroleumMenu extends AbstractContainerMenu {
    public final MachineBlockEntity machine;public final ContainerData data;
    public PetroleumMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer) { this(id,inventory,(MachineBlockEntity)inventory.player.level().getBlockEntity(buffer.readBlockPos()),new SimpleContainerData(9)); }
    public PetroleumMenu(int id,Inventory inv,MachineBlockEntity machine,ContainerData data) {
        super(Content.PETROLEUM_MENU.get(),id);this.machine=machine;this.data=data;addDataSlots(data);
        for(int i=0;i<4;i++)addSlot(new SlotItemHandler(machine.items,i,18+i*18,90));
        for(int i=4;i<8;i++)addSlot(new SlotItemHandler(machine.items,i,140+(i-4)*18,90) { public boolean mayPlace(ItemStack stack) { return false; } });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,39+col*18,161+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,39+col*18,219));
    }
    public int progress() { return (data.get(0)&65535)|((data.get(1)&65535)<<16); }
    public int duration() { return (data.get(2)&65535)|((data.get(3)&65535)<<16); }
    public boolean stillValid(Player p) { if(p.level().isClientSide)return true;return machine.petroleum()!=null && machine.petroleum().ready() && p.level()==machine.getLevel() && p.distanceToSqr(machine.getBlockPos().getCenter())<=64; }
    public boolean clickMenuButton(Player p,int id) {
        if(p.containerMenu!=this || !stillValid(p) || p.isSpectator())return false;
        if(id==1 || id==2)return FluidRecovery.recover(this,p,id-1);
        if(id!=0)return false;
        machine.petroleum().cycleRecipe();return true;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        if(!player.level().isClientSide && (!stillValid(player) || player.isSpectator()))return;
        super.clicked(slot,button,type,player);
    }
    public ItemStack quickMoveStack(Player player,int index) {
        if(!stillValid(player) || player.isSpectator() || index<0 || index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var original=stack.copy();
        if(index<8) { if(!moveItemStackTo(stack,8,44,true))return ItemStack.EMPTY; }
        else if(!machine.kind().processing() || !moveItemStackTo(stack,0,4,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();return original;
    }
}
