package factorycraft.client.trains;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.*;
import factorycraft.FactoryCraft;
import factorycraft.simulation.rail.RailVector;
import factorycraft.simulation.trains.*;
import factorycraft.trains.StockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;

/** Opt-in close-range hull/mesh/broadphase visualization; never runs in normal rendering. */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class StockDebug {
  private static final net.minecraft.client.KeyMapping KEY =
      new net.minecraft.client.KeyMapping(
          "key.factorycraft.stock_debug",
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_F8,
          "key.categories.factorycraft");
  private static boolean enabled;

  @SubscribeEvent
  public static void keys(RegisterKeyMappingsEvent e) {
    e.register(KEY);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    while (KEY.consumeClick()) enabled = !enabled;
  }

  @SubscribeEvent
  public static void draw(RenderLevelStageEvent e) {
    if (!enabled || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;
    var camera = e.getCamera().getPosition();
    var pose = e.getPoseStack();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    var out = buffers.getBuffer(RenderType.lines());
    for (var car :
        mc.level.getEntitiesOfClass(StockEntity.class, new AABB(camera, camera).inflate(48))) {
      var hull = car.hull();
      outline(pose, out, hull, .1f, 1, .3f, 1);
      LevelRenderer.renderLineBox(pose, out, car.getBoundingBox(), .2f, .55f, 1, 1);
      var origin =
          hull.center().subtract(hull.up().scale(hull.halfHeight() + StockGeometry.BOTTOM));
      for (var face : StockMeshes.BODY.get(car.kind())) {
        var vertices = face.vertices();
        for (int edge = 0; edge < 4; edge++) {
          var a = vertex(hull, origin, vertices, edge);
          var b = vertex(hull, origin, vertices, (edge + 1) % 4);
          segment(pose, out, a, b, 1, .25f, .8f, 1);
        }
      }
    }
    buffers.endBatch(RenderType.lines());
    pose.popPose();
  }

  public static void outline(
      PoseStack pose, VertexConsumer out, StockEnvelope h, float r, float g, float b, float alpha) {
    var v = new RailVector[8];
    for (int i = 0; i < 8; i++)
      v[i] =
          h.center()
              .add(h.side().scale(((i & 1) == 0 ? -1 : 1) * h.halfWidth()))
              .add(h.up().scale(((i & 2) == 0 ? -1 : 1) * h.halfHeight()))
              .add(h.forward().scale(((i & 4) == 0 ? -1 : 1) * h.halfLength()));
    for (int i = 0; i < 8; i++)
      for (int bit : new int[] {1, 2, 4})
        if ((i & bit) == 0) {
          var a = v[i];
          var c = v[i | bit];
          segment(pose, out, a, c, r, g, b, alpha);
        }
  }

  private static RailVector vertex(StockEnvelope hull, RailVector origin, float[] v, int i) {
    return origin
        .add(hull.side().scale(v[i * 3]))
        .add(hull.up().scale(v[i * 3 + 1]))
        .subtract(hull.forward().scale(v[i * 3 + 2]));
  }

  private static void segment(
      PoseStack pose,
      VertexConsumer out,
      RailVector a,
      RailVector c,
      float r,
      float g,
      float b,
      float alpha) {
    var delta = c.subtract(a);
    if (delta.length() < 1e-9) return;
    var n = delta.unit();
    out.addVertex(pose.last(), (float) a.x(), (float) a.y(), (float) a.z())
        .setColor(r, g, b, alpha)
        .setNormal(pose.last(), (float) n.x(), (float) n.y(), (float) n.z());
    out.addVertex(pose.last(), (float) c.x(), (float) c.y(), (float) c.z())
        .setColor(r, g, b, alpha)
        .setNormal(pose.last(), (float) n.x(), (float) n.y(), (float) n.z());
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Post e) {
    if (enabled)
      e.getGuiGraphics()
          .drawString(
              Minecraft.getInstance().font,
              net.minecraft.network.chat.Component.translatable("train.factorycraft.debug_legend"),
              8,
              8,
              0xffffff);
  }

  private StockDebug() {}
}
