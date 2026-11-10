package factorycraft.multiblock;

import factorycraft.blockentity.*;
import factorycraft.fluids.FluidConnections;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Canonical readonly inspection; never changes automation capabilities or copies data into parts.
 */
public final class MachineInspection {
  public static FactoryBlockEntity resolve(Level level, BlockPos pos) {
    if (level == null || !level.hasChunkAt(pos)) return null;
    var be = level.getBlockEntity(pos);
    if (be instanceof CasingBlockEntity part) {
      var owner = part.genericForRemoval();
      if (owner == null) return null;
      be = owner.factoryEntity();
    }
    if (!(be instanceof FactoryBlockEntity factory)) return null;
    if (level.isClientSide) {
      if (factory.isRemoved()
          || !level.hasChunkAt(factory.getBlockPos())
          || level.getBlockEntity(factory.getBlockPos()) != factory) return null;
    } else if (!FluidConnections.canonical(factory)) return null;
    var owner = AssemblyAccess.owner(factory);
    if (owner != null
        && (!owner.assembly().formed()
            || !level.isClientSide && (!owner.loaded() || !owner.assembly().ready()))) return null;
    return factory;
  }

  public static Component name(Level level, BlockPos pos) {
    var be = resolve(level, pos);
    return be == null ? null : be.getBlockState().getBlock().getName();
  }

  public static CompoundTag snapshot(Level level, BlockPos pos, Player player) {
    var tag = new CompoundTag();
    if (level.isClientSide
        || player == null
        || player.level() != level
        || !player.canInteractWithBlock(pos, 0)
        || !level.mayInteract(player, pos)) return tag;
    var be = resolve(level, pos);
    if (be == null) return tag;
    var registry = level.registryAccess();
    tag.putString(
        "Block", BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString());
    tag.putString("Name", be.getBlockState().getBlock().getDescriptionId());
    var items = be.inventory();
    var list = new ListTag();
    for (int slot = 0; slot < Math.min(64, items.getSlots()); slot++) {
      var stack = items.getStackInSlot(slot);
      if (stack.isEmpty()) continue;
      var entry = new CompoundTag();
      entry.putInt("Slot", slot);
      entry.put("Stack", stack.copy().save(registry));
      list.add(entry);
    }
    tag.put("Items", list);
    var fluids = new ArrayList<FluidStack>();
    int energy = -1;
    if (be instanceof MachineBlockEntity m) {
      energy = m.power.getEnergyStored();
      if (m.petroleum() != null)
        for (int i = 0; i < m.petroleum().cells.size(); i++) fluids.add(m.petroleum().stored(i));
      tag.putInt("Progress", m.petroleum() == null ? m.data.get(0) : m.petroleum().data.get(0));
      tag.putInt("Duration", m.petroleum() == null ? m.data.get(1) : m.petroleum().data.get(1));
    } else if (be instanceof ThermalBlockEntity t) {
      energy = t.generatesElectricity() ? t.engine.energy() : -1;
      fluids.add(t.contents(t.water));
      fluids.add(t.contents(t.steam));
    } else if (be instanceof FluidBlockEntity f) {
      energy = f.kind() == factorycraft.fluids.FluidKind.PUMP ? f.power.getEnergyStored() : -1;
      var c = f.cell();
      if (c.amount() > 0) fluids.add(c.key().stack(c.amount()));
    } else if (be instanceof LabBlockEntity lab) energy = lab.power.getEnergyStored();
    else if (be instanceof BeaconBlockEntity b) energy = b.power.getEnergyStored();
    else if (be instanceof InserterBlockEntity arm && arm.electric())
      energy = arm.power.getEnergyStored();
    if (energy >= 0) tag.putInt("Energy", energy);
    var liquid = new ListTag();
    for (var stack : fluids) if (!stack.isEmpty()) liquid.add(stack.copy().save(registry));
    tag.put("Fluids", liquid);
    return tag;
  }

  private MachineInspection() {}
}
