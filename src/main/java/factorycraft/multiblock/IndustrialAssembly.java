package factorycraft.multiblock;

import factorycraft.blockentity.*;
import factorycraft.blocks.FactoryBlock;
import factorycraft.simulation.multiblock.CuboidLayout;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Controller owns all items/energy/progress. Parts hold only a persistent identity and anchor. */
public final class IndustrialAssembly {
    public static final CuboidLayout LAYOUT=new CuboidLayout(2,2,2);
    public static final int UNFORMED=0,WAITING=1,READY=2;
    private final MachineBlockEntity controller;
    private UUID identity;
    private Direction facing=Direction.NORTH;
    private boolean formed,ready,forming,automatic,dirty=true;
    private long checkedAt=Long.MIN_VALUE;
    private List<BlockPos> parts=List.of();
    public IndustrialAssembly(MachineBlockEntity controller) { this.controller=controller; }
    public boolean formed() { return formed; }
    public boolean automatic() { return automatic; }
    public UUID identity() { return identity; }
    public List<BlockPos> positions() {
        if(parts.isEmpty())parts=positions(controller.getBlockPos(),facing);
        return parts;
    }
    public static List<BlockPos> positions(BlockPos origin,Direction facing) {
        return LAYOUT.parts(facing.getStepX(),facing.getStepZ()).stream().map(o->origin.offset(o.x(),o.y(),o.z()).immutable()).toList();
    }
    public boolean owns(UUID id) { return formed && identity!=null && identity.equals(id); }
    public void invalidate() { ready=false;dirty=true; }
    /** All checks complete before any casing is bound. Formation never copies or consumes inventories. */
    public boolean form() { return form(false); }
    public boolean formAutomatic() { return form(true); }
    private boolean form(boolean automaticPlacement) {
        var level=controller.getLevel();if(level==null || level.isClientSide)return false;
        if(formed)return ready();
        var direction=controller.getBlockState().getValue(FactoryBlock.FACING);
        var requested=positions(controller.getBlockPos(),direction);
        for(var p:requested) {
            if(!level.hasChunkAt(p) || !(level.getBlockEntity(p) instanceof CasingBlockEntity part) || !part.availableFor(controller.getBlockPos(),identity))return false;
        }
        UUID next=UUID.randomUUID();
        facing=direction;parts=requested;identity=next;automatic=automaticPlacement;formed=true;forming=true;invalidate();
        try {
            // Reserve every identity before a block update can reenter a capability or neighbor.
            for(var p:requested)((CasingBlockEntity)level.getBlockEntity(p)).bind(controller.getBlockPos(),next,automatic);
            for(var p:requested)if(level.getBlockEntity(p) instanceof CasingBlockEntity part && part.boundTo(controller.getBlockPos(),next))part.refreshAppearance();
        } finally { forming=false; }
        controller.sync();return formed;
    }
    public boolean ready() {
        if(!formed || forming)return false;
        var level=controller.getLevel();if(level==null || level.isClientSide || !controller.loaded())return false;
        long now=level.getGameTime();
        if(ready && !dirty)return true;
        if(!dirty && now-checkedAt<20)return false;
        dirty=false;checkedAt=now;
        for(var p:positions()) {
            if(!level.hasChunkAt(p))return false;
            if(!(level.getBlockEntity(p) instanceof CasingBlockEntity part) || !part.boundTo(controller.getBlockPos(),identity)) {
                dismantle();return false;
            }
            if(!part.loaded())return false;
        }
        ready=true;return true;
    }
    public int status() { return !formed ? UNFORMED:ready() ? READY:formed ? WAITING:UNFORMED; }
    public void dismantle() {
        if(automatic) { removeWhole(true);return; }
        if(!formed)return;
        formed=false;ready=false;dirty=true;
        var level=controller.getLevel();
        if(level!=null && !level.isClientSide)for(var p:positions()) {
            if(level.hasChunkAt(p) && level.getBlockEntity(p) instanceof CasingBlockEntity part && part.boundTo(controller.getBlockPos(),identity))part.unbind();
        }
        controller.sync();
    }
    /** Generated parts are never independent resources; only the controller can drop a machine item. */
    public void removeWhole(boolean dropMachine) {
        if(!formed || !automatic)return;
        formed=false;ready=false;dirty=true;
        var level=controller.getLevel();
        if(level==null || level.isClientSide)return;
        for(var p:positions())
            if(level.hasChunkAt(p) && level.getBlockEntity(p) instanceof CasingBlockEntity part && part.boundTo(controller.getBlockPos(),identity))
                level.removeBlock(p,false);
        if(level.getBlockEntity(controller.getBlockPos())==controller && level.getBlockState(controller.getBlockPos()).is(controller.getBlockState().getBlock()))
            level.destroyBlock(controller.getBlockPos(),dropMachine);
        controller.setChanged();
    }
    public void save(CompoundTag parent) {
        var t=new CompoundTag();t.putInt("Schema",2);t.putBoolean("Formed",formed);t.putBoolean("Automatic",automatic);t.putInt("Facing",facing.get2DDataValue());
        if(identity!=null)t.putUUID("Identity",identity);parent.put("Assembly",t);
    }
    public void load(CompoundTag parent) {
        var t=parent.getCompound("Assembly");formed=t.getBoolean("Formed");automatic=t.getBoolean("Automatic");identity=t.hasUUID("Identity") ? t.getUUID("Identity"):null;
        if(identity==null)formed=false;
        facing=Direction.from2DDataValue(t.getInt("Facing"));parts=List.of();invalidate();
    }
}
