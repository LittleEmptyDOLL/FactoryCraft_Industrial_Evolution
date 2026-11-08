package factorycraft.simulation.thermal;

import factorycraft.simulation.fluids.FluidCell;
import java.util.Objects;

/** Finite steam-to-energy conversion. Energy is owned before the fluid observer can run. */
public final class SteamEngine<K> {
    public static final int CAPACITY=16000, FE_PER_MB=12, MB_PER_TICK=10;
    private final FluidCell<K> steam;
    private final Runnable changed;
    private int energy;
    public SteamEngine(FluidCell<K> steam,Runnable changed) { this.steam=Objects.requireNonNull(steam);this.changed=Objects.requireNonNull(changed); }
    public int energy() { return energy; }
    public void restore(int energy) { if(energy<0)throw new IllegalArgumentException("Negative energy");this.energy=energy; }
    public int generate(boolean validSteam) {
        if(!validSteam)return 0;
        int amount=Math.min(MB_PER_TICK,Math.min(steam.amount(),Math.max(0,CAPACITY-energy)/FE_PER_MB));
        if(amount==0)return 0;
        energy+=amount*FE_PER_MB;steam.drain(amount,false);return amount*FE_PER_MB;
    }
    public int extract(int requested,boolean simulate) {
        int accepted=Math.min(Math.max(0,requested),energy);
        if(accepted>0 && !simulate) { energy-=accepted;changed.run(); }return accepted;
    }
}
