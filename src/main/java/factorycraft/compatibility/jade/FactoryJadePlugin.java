package factorycraft.compatibility.jade;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.FactoryBlockEntity;
import factorycraft.multiblock.MachineInspection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.*;

/** Jade scans this optional entrypoint. Common mod classes never reference Jade API types. */
@WailaPlugin
public final class FactoryJadePlugin implements IWailaPlugin, IServerDataProvider<BlockAccessor> {
  static final String KEY = "FactoryCraftInspection";

  public ResourceLocation getUid() {
    return ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "machine_inspection");
  }

  public void register(IWailaCommonRegistration r) {
    r.registerBlockDataProvider(this, FactoryBlockEntity.class);
  }

  public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
    var snapshot =
        MachineInspection.snapshot(
            accessor.getLevel(), accessor.getPosition(), accessor.getPlayer());
    if (!snapshot.isEmpty()) tag.put(KEY, snapshot);
  }

  public void registerClient(IWailaClientRegistration registration) {
    FactoryJadeClient.register(registration);
  }
}
