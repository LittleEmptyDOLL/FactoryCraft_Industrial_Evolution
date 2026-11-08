package factorycraft.simulation.resources;

/** Finite units per tile. No destination acceptance means no depletion. */
public final class ResourceReserve {
    private int remaining;
    public int remaining() { return remaining; }
    public void restore(int units) { if(units<0)throw new IllegalArgumentException("Negative reserve");remaining=units; }
    public boolean extractOne(boolean destinationAccepted) { if(!destinationAccepted || remaining==0)return false;remaining--;return true; }
}
