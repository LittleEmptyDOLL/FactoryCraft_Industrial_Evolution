package factorycraft.client;

import factorycraft.FactoryCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Bounded server-supplied membership selection, no neighbor/world graph scan per frame. */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class PipelineOverlay {
  private static java.util.List<BlockPos> cells = java.util.List.of();
  private static Object world;
  private static long until;

  public static void show(java.util.List<BlockPos> positions) {
    var mc = Minecraft.getInstance();
    world = mc.level;
    cells = java.util.List.copyOf(positions);
    until = System.nanoTime() + 10_000_000_000L;
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    var mc = Minecraft.getInstance();
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    if (mc.level != world || System.nanoTime() > until) {
      cells = java.util.List.of();
      world = null;
      return;
    }
    var pose = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(RenderType.lines());
    for (var cell : cells)
      if (mc.level.hasChunkAt(cell))
        LevelRenderer.renderLineBox(pose, lines, new AABB(cell).inflate(.008), .15f, .85f, 1f, .9f);
    buffers.endBatch(RenderType.lines());
    pose.popPose();
  }

  private PipelineOverlay() {}
}
