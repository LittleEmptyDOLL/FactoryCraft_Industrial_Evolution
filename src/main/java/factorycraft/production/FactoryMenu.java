package factorycraft.production;

import factorycraft.blockentity.MachineBlockEntity;
import factorycraft.registry.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class FactoryMenu extends AbstractContainerMenu {
    public final MachineBlockEntity machine;
    public final ContainerData data;
    public FactoryMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id,inv,(MachineBlockEntity)inv.player.level().getBlockEntity(buf.readBlockPos()),new SimpleContainerData(8));
    }
    public FactoryMenu(int id, Inventory inv, MachineBlockEntity machine, ContainerData data) {
        super(Content.MENU.get(),id); this.machine=machine;this.data=data;
        for(int i=0;i<4;i++)addSlot(new SlotItemHandler(machine.items,i,18+i*18,46));
        for(int i=0;i<4;i++)addSlot(new SlotItemHandler(machine.items,i+4,112+i*18,46){ @Override public boolean mayPlace(ItemStack stack){return false;} });
        addSlot(new SlotItemHandler(machine.items,8,18,82));
        for(int row=0;row<3;row++) for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,22+col*18,166+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,22+col*18,224));
        addDataSlots(data);
    }
    @Override public boolean stillValid(Player p) { return machine.loaded() && p.level()==machine.getLevel() && factorycraft.multiblock.MachineInteraction.inRange(machine,p); }
    @Override public boolean clickMenuButton(Player p,int id) {
        if(id==99 && p.containerMenu==this && stillValid(p))return factorycraft.modules.ModuleMenu.open(machine,p);
        if(p.containerMenu!=this || id!=0 || !stillValid(p) || p.isSpectator())return false;
        machine.cycleRecipe(); return true;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player) { if(player.level().isClientSide || !player.isSpectator() && player.containerMenu==this && stillValid(player))super.clicked(slot,button,type,player); }
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(p.isSpectator() || p.containerMenu!=this || !stillValid(p) || index<0 || index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index); if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(), original=stack.copy();
        if(index<9) { if(!moveItemStackTo(stack,9,45,true))return ItemStack.EMPTY; }
        else {
            if(machine.items.isItemValid(8,stack)) { if(!moveItemStackTo(stack,8,9,false))return ItemStack.EMPTY; }
            else if(!moveItemStackTo(stack,0,4,false))return ItemStack.EMPTY;
        }
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        return original;
    }
}
