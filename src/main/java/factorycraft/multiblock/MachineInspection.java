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
    factorycraft.simulation.petroleum.OilYield oilYield =
        be instanceof OilDepositBlockEntity oil && oil.available()
            ? oil.yield()
            : be instanceof MachineBlockEntity m && m.petroleum() != null
                ? m.petroleum().oilYield()
                : null;
    if (oilYield != null) {
      tag.putLong("InitialYield", oilYield.initial());
      tag.putLong("CurrentYield", oilYield.current());
      tag.putDouble("OilRate", Math.min(10000, oilYield.current() / 1500.0));
    }
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
    if (be instanceof factorycraft.combat.TurretBlockEntity turret) {
      energy = turret.laser() ? turret.power.getEnergyStored() : -1;
      if (turret.oil.amount() > 0) fluids.add(turret.oil.key().stack(turret.oil.amount()));
      if (turret.pipeOil.amount() > 0)
        fluids.add(turret.pipeOil.key().stack(turret.pipeOil.amount()));
    } else if (be instanceof factorycraft.nuclear.NuclearOwner nuclear) {
      var port = nuclear.energyPort();
      if (port != null) energy = port.getEnergyStored();
      for (int c = 0; c < 2; c++) {
        var cell = nuclear.cell(c);
        if (cell != null && cell.amount() > 0) fluids.add(cell.key().stack(cell.amount()));
      }
      if (nuclear instanceof factorycraft.nuclear.HeatOwner)
        tag.putInt("Temperature", nuclear.metrics()[2]);
      if (nuclear instanceof factorycraft.nuclear.ReactorBlockEntity reactor) {
        tag.putInt("FuelTicks", reactor.burnTicks);
        tag.putInt("ActiveNeighbors", reactor.neighbors);
        if (!reactor.burning().isEmpty()) {
          var entry = new CompoundTag();
          entry.putInt("Slot", 128);
          entry.put("Stack", reactor.burning().save(registry));
          list.add(entry);
        }
      }
      if (nuclear instanceof factorycraft.nuclear.ElectricDeviceBlockEntity e && e.panel())
        tag.putInt("GenerationW", e.metrics()[0] * 500);
    } else if (be instanceof MachineBlockEntity m) {
      if (m.miningAcid != null && m.miningAcid.amount() > 0)
        fluids.add(m.miningAcid.key().stack(m.miningAcid.amount()));
      if (m.uraniumWork != null && !m.uraniumWork.escrow.isEmpty()) {
        var e = new CompoundTag();
        e.putInt("Slot", 64);
        e.put("Stack", m.uraniumWork.escrow.save(registry));
        list.add(e);
      }
      if (m.construction.ownsMaterials())
        for (int slot = 0; slot < m.construction.escrow.getSlots(); slot++) {
          var stack = m.construction.escrow.getStackInSlot(slot);
          if (!stack.isEmpty()) {
            var e = new CompoundTag();
            e.putInt("Slot", 65 + slot);
            e.put("Stack", stack.save(registry));
            list.add(e);
          }
        }

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
      var c = factorycraft.fluids.FluidSimulation.view(f);
      if (c.amount() > 0 && c.key() != null)
        fluids.add(c.key().stack((int) Math.min(Integer.MAX_VALUE, c.amount())));
    } else if (be instanceof LabBlockEntity lab) {
      energy = lab.power.getEnergyStored();
      for (int i = 0; i < 2; i++)
        if (!lab.modules.getStackInSlot(i).isEmpty()) {
          var entry = new CompoundTag();
          entry.putInt("Slot", factorycraft.research.SciencePacks.COUNT + i);
          entry.put("Stack", lab.modules.getStackInSlot(i).copy().save(registry));
          list.add(entry);
        }
      tag.putInt("Progress", lab.progress());
    } else if (be instanceof BeaconBlockEntity b) energy = b.power.getEnergyStored();
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
