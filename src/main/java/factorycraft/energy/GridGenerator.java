package factorycraft.energy;

/** Marks devices which supply the cached pole graph rather than receive from it. */
public interface GridGenerator {
    boolean generatesElectricity();
}
