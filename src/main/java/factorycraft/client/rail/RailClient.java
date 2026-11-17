package factorycraft.client.rail;

import com.mojang.blaze3d.platform.InputConstants;
import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Cached local preview and immutable geometry projections, never authoritative track simulation.
 */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class RailClient {
  private static final net.minecraft.client.KeyMapping ROTATE =
      new net.minecraft.client.KeyMapping(
          "key.factorycraft.rail_rotate",
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_R,
          "key.categories.factorycraft");
  private static final net.minecraft.client.KeyMapping FLIP =
      new net.minecraft.client.KeyMapping(
          "key.factorycraft.rail_flip",
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_X,
          "key.categories.factorycraft");
  private static final net.minecraft.client.KeyMapping CANCEL =
      new net.minecraft.client.KeyMapping(
          "key.factorycraft.rail_cancel",
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_C,
          "key.categories.factorycraft");
  private static RailEnd start;
  private static int goal = -1;
  private static net.minecraft.world.level.Level currentLevel;
  private static final Map<UUID, RailViews.Route> curves =
      new LinkedHashMap<>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<UUID, RailViews.Route> e) {
          return size() > 1024;
        }
      };
  private static RailPlan plan;
  private static float[] ghost = new float[0];
  private static String reason = "selected";
  private static RailEnd lastEnd, lastStart;
  private static long checkedAt = Long.MIN_VALUE;

  @SubscribeEvent
  public static void keys(RegisterKeyMappingsEvent e) {
    e.register(ROTATE);
    e.register(FLIP);
    e.register(CANCEL);
  }

  @SubscribeEvent
  public static void model(ModelEvent.RegisterGeometryLoaders e) {
    e.register(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "rail"),
        new RailGeometryLoader());
    RailNetwork.clientReceiver =
        p -> {
          start = p.tag().contains("Start") ? RailCodec.end(p.tag().getCompound("Start")) : null;
          goal = p.tag().getInt("Goal");
          lastStart = null;
        };
    RailViews.client = RailClient::curves;
  }

  private static Collection<RailViews.Route> curves(
      net.minecraft.world.level.Level level, RailFootprint f) {
    var found = new LinkedHashMap<UUID, RailViews.Route>();
    for (var c : f.clearance) {
      var pos = RailwayData.pos(c);
      if (!level.hasChunkAt(pos)) continue;
      if (level.getBlockEntity(pos) instanceof RailCellBlockEntity cell)
        for (var d : cell.descriptors)
          found.put(
              d.id(), curves.computeIfAbsent(d.id(), k -> new RailViews.Route(d.id(), d.curve())));
    }
    return found.values();
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    var mc = Minecraft.getInstance();
    if (mc.level != currentLevel) {
      currentLevel = mc.level;
      curves.clear();
      start = null;
      plan = null;
      lastStart = null;
    }
    if (mc.player == null
        || mc.screen != null
        || !mc.player.getMainHandItem().is(RailContent.RAIL.get())
            && !mc.player.getOffhandItem().is(RailContent.RAIL.get())) return;
    while (ROTATE.consumeClick()) PacketDistributor.sendToServer(new RailNetwork.Edit(0));
    while (FLIP.consumeClick()) PacketDistributor.sendToServer(new RailNetwork.Edit(1));
    while (CANCEL.consumeClick()) PacketDistributor.sendToServer(new RailNetwork.Edit(2));
  }

  private static boolean target(Minecraft mc, BlockHitResult hit) {
    return hit.getType() == HitResult.Type.BLOCK
        && !mc.level.getBlockState(hit.getBlockPos()).isAir()
        && mc.player.getEyePosition().distanceToSqr(hit.getLocation())
            <= mc.player.blockInteractionRange() * mc.player.blockInteractionRange() + .01;
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    plan = null;
    if (mc.level == null
        || mc.player == null
        || mc.screen != null
        || start == null
        || !mc.player.getMainHandItem().is(RailContent.RAIL.get())
            && !mc.player.getOffhandItem().is(RailContent.RAIL.get())
        || !(mc.hitResult instanceof BlockHitResult hit)
        || !target(mc, hit)) return;
    var look = mc.player.getLookAngle();
    var end = RailPlanner.target(mc.level, hit, false, RailHeading.nearest(look.x, look.z));
    if (end == null) {
      reason = "endpoint";
      return;
    }
    if (!mc.level.getBlockState(hit.getBlockPos()).is(RailContent.BED.get())) {
      var delta = end.point().subtract(start.point());
      end =
          new RailEnd(
              end.x(),
              end.y(),
              end.z(),
              goal < 0 ? RailHeading.nearest(delta.x(), delta.z()) : new RailHeading(goal));
    }
    try {
      if (!start.equals(lastStart) || !end.equals(lastEnd)) {
        lastStart = start;
        lastEnd = end;
        cached = null;
        plan = new RailPlan(RailCurve.plan(start, end));
        cached = plan;
        ghost = ghost(plan);
        checkedAt = Long.MIN_VALUE;
      } else plan = cached;
      if (plan == null) return;
      if (mc.level.getGameTime() - checkedAt >= 4 || checkedAt == Long.MIN_VALUE) {
        reason = plan.inspect(mc.level, mc.player);
        if (reason.equals("ready")
            && !mc.player.isCreative()
            && RailPlacement.count(mc.player) < plan.curve.cost()) reason = "price";
        checkedAt = mc.level.getGameTime();
      }
      var pose = e.getPoseStack();
      pose.pushPose();
      var camera = e.getCamera().getPosition();
      pose.translate(-camera.x, -camera.y, -camera.z);
      var buffers = mc.renderBuffers().bufferSource();
      boolean valid = reason.equals("ready");
      float r = valid ? .2f : 1, g = valid ? 1 : .18f;
      pose.pushPose();
      pose.translate(start.x(), start.y(), start.z());
      var fill = buffers.getBuffer(RenderType.debugQuads());
      for (int i = 0; i < ghost.length; i += 3)
        fill.addVertex(pose.last(), ghost[i], ghost[i + 1], ghost[i + 2]).setColor(r, g, .4f, .22f);
      buffers.endBatch(RenderType.debugQuads());
      pose.popPose();
      var line = buffers.getBuffer(RenderType.lines());
      RailCurve.Frame prev = plan.curve.sample(0, false);
      for (double d = .5; d < plan.curve.length() + .5; d += .5) {
        var next = plan.curve.sample(Math.min(d, plan.curve.length()), false);
        for (double offset : new double[] {-1, -.72, 0, .72, 1})
          line(
              pose,
              line,
              prev.position().add(prev.side().scale(offset)),
              next.position().add(next.side().scale(offset)),
              r,
              g,
              .4f);
        prev = next;
      }
      LevelRenderer.renderLineBox(
          pose,
          line,
          new AABB(start.x(), start.y(), start.z(), start.x() + 1, start.y() + .3, start.z() + 1),
          .3f,
          .5f,
          1,
          1);
      buffers.endBatch(RenderType.lines());
      pose.popPose();
    } catch (IllegalArgumentException bad) {
      reason = bad.getMessage();
      plan = null;
      cached = null;
    }
  }

  private static RailPlan cached;

  private static float[] ghost(RailPlan plan) {
    var coords = new it.unimi.dsi.fastutil.floats.FloatArrayList();
    for (var entry : RailMesh.build(plan.curve).entrySet())
      for (var face : entry.getValue()) {
        var vertices = face.vertices();
        for (int i = 1; i < vertices.size() - 1; i++)
          for (var p :
              List.of(vertices.get(0), vertices.get(i), vertices.get(i + 1), vertices.get(i + 1))) {
            coords.add((float) (entry.getKey().x() - plan.curve.start.x() + p.x()));
            coords.add((float) (entry.getKey().y() - plan.curve.start.y() + p.y()));
            coords.add((float) (entry.getKey().z() - plan.curve.start.z() + p.z()));
          }
      }
    return coords.toFloatArray();
  }

  private static void line(
      com.mojang.blaze3d.vertex.PoseStack pose,
      com.mojang.blaze3d.vertex.VertexConsumer out,
      RailVector a,
      RailVector b,
      float r,
      float g,
      float blue) {
    var n = b.subtract(a);
    if (n.length() < 1e-8) return;
    n = n.unit();
    var m = pose.last();
    out.addVertex(m, (float) a.x(), (float) a.y(), (float) a.z())
        .setColor(r, g, blue, .85f)
        .setNormal(m, (float) n.x(), (float) n.y(), (float) n.z());
    out.addVertex(m, (float) b.x(), (float) b.y(), (float) b.z())
        .setColor(r, g, blue, .85f)
        .setNormal(m, (float) n.x(), (float) n.y(), (float) n.z());
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Post e) {
    var mc = Minecraft.getInstance();
    if (mc.player == null
        || mc.level == null
        || mc.screen != null
        || start == null
        || !mc.player.getMainHandItem().is(RailContent.RAIL.get())
            && !mc.player.getOffhandItem().is(RailContent.RAIL.get())
        || !(mc.hitResult instanceof BlockHitResult hit)
        || !target(mc, hit)) return;
    e.getGuiGraphics()
        .drawCenteredString(
            mc.font,
            Component.translatable(
                "rail.factorycraft.preview",
                plan == null ? 0 : plan.curve.cost(),
                Component.translatable("rail.factorycraft.direction_" + goal),
                Component.translatable("rail.factorycraft." + reason)),
            e.getGuiGraphics().guiWidth() / 2,
            e.getGuiGraphics().guiHeight() / 2 + 30,
            reason.equals("ready") ? 0x55ff88 : 0xff7777);
  }
}
