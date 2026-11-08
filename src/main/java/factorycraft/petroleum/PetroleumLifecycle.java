package factorycraft.petroleum;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber(modid=FactoryCraft.ID)
public final class PetroleumLifecycle {
    @SubscribeEvent public static void unloading(ChunkEvent.Unload event) {
        if(!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) || chunk.getLevel().isClientSide)return;
        for(var be:chunk.getBlockEntities().values()) {
            if(be instanceof MachineBlockEntity machine && machine.petroleum()!=null || be instanceof OilDepositBlockEntity) { chunk.setUnsaved(true);be.onChunkUnloaded(); }
            else if(be instanceof CasingBlockEntity part) {
                var owner=part.controllerForRemoval();if(owner!=null && owner.petroleum()!=null) { chunk.setUnsaved(true);part.onChunkUnloaded(); }
            }
        }
    }
    private PetroleumLifecycle() {}
}
