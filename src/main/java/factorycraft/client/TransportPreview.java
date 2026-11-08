package factorycraft.client;

import factorycraft.FactoryCraft;
import factorycraft.belts.TransportPlan;
import factorycraft.items.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class TransportPreview {
  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null
        || mc.player == null
        || mc.screen != null
        || !(mc.hitResult instanceof BlockHitResult hit)) return;
    for (var hand : InteractionHand.values()) {
      var stack = mc.player.getItemInHand(hand);
      if (!(stack.getItem() instanceof LiftItem item)) continue;
      var original = new BlockPlaceContext(new UseOnContext(mc.player, hand, hit));
      var selected = LiftItem.selection(stack);
      var context = item.selectedContext(original);
      var pose = event.getPoseStack();
      var camera = event.getCamera().getPosition();
      pose.pushPose();
      pose.translate(-camera.x, -camera.y, -camera.z);
      var buffers = mc.renderBuffers().bufferSource();
      var lines = buffers.getBuffer(RenderType.lines());
      if (context != null) {
        var plan = TransportPlan.inspect(context, context.route);
        for (var p : plan.cells())
          LevelRenderer.renderLineBox(
              pose,
              lines,
              new AABB(p).inflate(.006),
              plan.valid() ? .2f : 1f,
              plan.valid() ? 1f : .15f,
              .35f,
              .85f);
        var exit =
            plan.anchor().above(plan.route().heightDelta()).relative(mc.player.getDirection());
        LevelRenderer.renderLineBox(pose, lines, new AABB(exit).deflate(.25), .2f, .65f, 1f, .9f);
      } else if (selected.contains("Anchor")) {
        var anchor = net.minecraft.core.BlockPos.of(selected.getLong("Anchor"));
        LevelRenderer.renderLineBox(
            pose, lines, new AABB(anchor).inflate(.02), 1f, .25f, .15f, .9f);
      } else
        LevelRenderer.renderLineBox(
            pose, lines, new AABB(original.getClickedPos()).inflate(.01), .2f, .65f, 1f, .9f);
      buffers.endBatch(RenderType.lines());
      pose.popPose();
      return;
    }
  }

  private TransportPreview() {}
}
