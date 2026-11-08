package factorycraft.energy;

import factorycraft.production.PowerBuffer;

/** Small composition boundary shared by production machines and electric fluid equipment. */
public interface GridConsumer {
    PowerBuffer gridBuffer();
    default int receiveGridEnergy(int offered,boolean simulate) { return gridBuffer().receiveEnergy(offered,simulate); }
}
