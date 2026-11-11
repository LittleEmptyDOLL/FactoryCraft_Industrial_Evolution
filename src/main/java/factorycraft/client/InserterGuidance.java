package factorycraft.client;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.InserterBlockEntity;
import factorycraft.simulation.BeltSimulation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Hover-only physical pickup and exact selected input lane. Never simulates transfers. */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class InserterGuidance {
  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null
        || mc.player == null
        || mc.screen != null
        || !(mc.hitResult instanceof BlockHitResult hit)
        || !PlacementPreview.validTarget(mc, hit)
        || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof InserterBlockEntity arm)) return;
    var pose = e.getPoseStack();
    var camera = e.getCamera().getPosition();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(RenderType.lines());
    var source = arm.sourcePosition();
    var target = arm.targetPosition();
    LevelRenderer.renderLineBox(pose, lines, new AABB(source).inflate(.01), .2f, .65f, 1f, .9f);
    var belt = BeltSimulation.at(mc.level, target);
    Vec3 end = target.getCenter();
    if (belt != null) {
      var pt = belt.route().point(arm.dropLane(belt), 0);
      var local = factorycraft.belts.TransportShapes.rotate(pt.x(), pt.z(), belt.facing());
      end =
          new Vec3(
              target.getX() + local[0],
              belt.getBlockPos().getY() + pt.y() + .05,
              target.getZ() + local[1]);
    }
    LevelRenderer.renderLineBox(
        pose,
        lines,
        new AABB(end.subtract(.10, .04, .10), end.add(.10, .12, .10)),
        1f,
        .65f,
        .15f,
        1f);
    LevelRenderer.renderLineBox(pose, lines, new AABB(target).deflate(.015), 1f, .65f, .15f, .5f);
    buffers.endBatch(RenderType.lines());
    pose.popPose();
  }

  private InserterGuidance() {}
}
