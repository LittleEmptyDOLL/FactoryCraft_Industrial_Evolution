package factorycraft.client.trains;

import factorycraft.FactoryCraft;
import factorycraft.rail.automation.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Read-only cached local placement outline, never resolves a target from MISS or stale air. */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class TracksidePreview {
  private static TracksidePlacement.Plan plan;
  private static long checked = Long.MIN_VALUE;
  private static float yaw;
  private static String reason;
  private static Object world;

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null
        || mc.player == null
        || mc.screen != null
        || !(mc.hitResult instanceof BlockHitResult hit)
        || hit.getType() != HitResult.Type.BLOCK
        || mc.level.getBlockState(hit.getBlockPos()).isAir()
        || !mc.player.canInteractWithBlock(hit.getBlockPos(), 0)) {
      plan = null;
      return;
    }
    TracksideItem item = null;
    InteractionHand selected = null;
    for (var hand : InteractionHand.values())
      if (mc.player.getItemInHand(hand).getItem() instanceof TracksideItem held) {
        item = held;
        selected = hand;
        break;
      }
    if (item == null) {
      plan = null;
      return;
    }
    var target = TracksidePlacement.target(new UseOnContext(mc.player, selected, hit));
    long now = mc.level.getGameTime();
    if (plan == null
        || world != mc.level
        || !plan.anchor().equals(target)
        || plan.kind() != item.kind
        || Math.abs(yaw - mc.player.getYRot()) > 8
        || now - checked >= 10) {
      world = mc.level;
      checked = now;
      yaw = mc.player.getYRot();
      plan = TracksidePlacement.plan(mc.level, target, item.kind, yaw);
      reason = TracksidePlacement.inspect(mc.level, mc.player, plan);
    }
    var pose = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(RenderType.lines());
    boolean valid = reason.equals("ready");
    float red = valid ? .15f : 1, green = valid ? 1 : .15f;
    for (var cell : plan.cells())
      LevelRenderer.renderLineBox(pose, lines, new AABB(cell).inflate(.006), red, green, .35f, .7f);
    if (plan.frame() != null) {
      var q = plan.frame().position();
      var v = plan.frame().tangent().scale(plan.reverse() ? -1 : 1);
      for (int i = 0; i < 12; i++) {
        var p = q.add(v.scale(i * .2));
        LevelRenderer.renderLineBox(
            pose,
            lines,
            new AABB(
                p.x() - .045, p.y() + .15, p.z() - .045, p.x() + .045, p.y() + .22, p.z() + .045),
            .2f,
            .75f,
            1f,
            1);
      }
      var tip = q.add(v.scale(2.4));
      for (int side : new int[] {-1, 1})
        for (int i = 0; i < 4; i++) {
          var p =
              tip.subtract(v.scale(i * .18))
                  .add(
                      new factorycraft.simulation.rail.RailVector(
                          -v.z() * side * i * .12, 0, v.x() * side * i * .12));
          LevelRenderer.renderLineBox(
              pose,
              lines,
              new AABB(
                  p.x() - .04, p.y() + .15, p.z() - .04, p.x() + .04, p.y() + .22, p.z() + .04),
              .2f,
              .75f,
              1f,
              1);
        }
    }
    buffers.endBatch(RenderType.lines());
    pose.popPose();
  }

  private TracksidePreview() {}
}
