package factorycraft.multiblock;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber(modid=FactoryCraft.ID)
public final class AssemblyLifecycle {
    @SubscribeEvent public static void unloading(ChunkEvent.Unload event) {
        if(!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) || chunk.getLevel().isClientSide)return;
        for(var be:chunk.getBlockEntities().values())if(be instanceof CasingBlockEntity || be instanceof AssemblyOwner || be instanceof ResourceDepositBlockEntity) { chunk.setUnsaved(true);be.onChunkUnloaded(); }
    }
    private AssemblyLifecycle() {}
}
