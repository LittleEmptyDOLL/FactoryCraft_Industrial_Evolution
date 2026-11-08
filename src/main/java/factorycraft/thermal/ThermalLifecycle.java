package factorycraft.thermal;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.ThermalBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber(modid=FactoryCraft.ID)
public final class ThermalLifecycle {
    @SubscribeEvent public static void unloading(ChunkEvent.Unload event) {
        if(!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) || chunk.getLevel().isClientSide)return;
        for(var entity:chunk.getBlockEntities().values())if(entity instanceof ThermalBlockEntity thermal) { chunk.setUnsaved(true);thermal.onChunkUnloaded(); }
    }
    private ThermalLifecycle() {}
}
