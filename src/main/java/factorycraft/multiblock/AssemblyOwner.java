package factorycraft.multiblock;

import factorycraft.blockentity.FactoryBlockEntity;

public interface AssemblyOwner {
    IndustrialAssembly assembly();
    boolean loaded();
    default FactoryBlockEntity factoryEntity() { return (FactoryBlockEntity)this; }
}
