package factorycraft.production;

import factorycraft.simulation.EnergyBuffer;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** NeoForge capability adapter over the independently testable energy model. */
public final class PowerBuffer extends EnergyBuffer implements IEnergyStorage {
    public PowerBuffer(int capacity, Runnable dirty) { super(capacity,dirty); }
}
