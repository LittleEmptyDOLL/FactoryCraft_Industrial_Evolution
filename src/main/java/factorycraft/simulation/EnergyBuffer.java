package factorycraft.simulation;


/** All mutations are server-thread only. FE capability cannot extract machine energy. */
public class EnergyBuffer {
    private int stored;
    private final int capacity;
    private final Runnable dirty;
    public EnergyBuffer(int capacity, Runnable dirty) {
        if(capacity<=0)throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity; this.dirty = dirty;
    }
    public void restore(int amount) { stored = Math.clamp(amount, 0, capacity); }
    public boolean spend(int amount) {
        if (amount < 0 || stored < amount) return false;
        stored -= amount; dirty.run(); return true;
    }
    public int receiveEnergy(int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(amount, capacity - stored));
        if (!simulate && accepted > 0) { stored += accepted; dirty.run(); }
        return accepted;
    }
    public int extractEnergy(int amount, boolean simulate) { return 0; }
    public int getEnergyStored() { return stored; }
    public int getMaxEnergyStored() { return capacity; }
    public boolean canExtract() { return false; }
    public boolean canReceive() { return true; }
}
