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
  private static RailEnd start, other;
  private static int side;
  private static RailConnectionResolver.Request lastRequest;
  private static RailConnectionResolver.Result resolution;
  private static int goal = -1, selectedSlot = -1;
  private static UUID anchor;
  private static boolean geometric = true;
  private static BlockHitResult hovering;
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
  private static long checkedAt = Long.MIN_VALUE, worldRevision, hintTick = Long.MIN_VALUE;
  private static final EndpointHints hints = new EndpointHints();
  private static boolean showHints;

  public static void changed(BlockPos pos) {
    if (start == null || lastEnd == null) return;
    var source = lastStart == null ? start : lastStart;
    if (pos.getX() >= Math.min(source.x(), lastEnd.x()) - 8
        && pos.getX() <= Math.max(source.x(), lastEnd.x()) + 8
        && pos.getZ() >= Math.min(source.z(), lastEnd.z()) - 8
        && pos.getZ() <= Math.max(source.z(), lastEnd.z()) + 8) {
      worldRevision++;
      checkedAt = Long.MIN_VALUE;
    }
  }

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
          var level = Minecraft.getInstance().level;
          if (currentLevel != level) curves.clear();
          currentLevel = level;
          anchor = p.tag().hasUUID("Anchor") ? p.tag().getUUID("Anchor") : null;
          selectedSlot = p.tag().getInt("Slot");
          start = p.tag().contains("Start") ? RailCodec.end(p.tag().getCompound("Start")) : null;
          goal = p.tag().getInt("Goal");
          other = p.tag().contains("Other") ? RailCodec.end(p.tag().getCompound("Other")) : null;
          side = p.tag().getInt("Side");
          lastRequest = null;
          lastStart = null;
        };
    RailViews.client = RailClient::curves;
  }

  private static Collection<RailViews.Route> curves(
      net.minecraft.world.level.Level level, RailFootprint f) {
    var found = new LinkedHashMap<UUID, RailViews.Route>();
    for (var c : f.clearance) {
      var pos = RailwayData.pos(c);
      if (!RailPlan.loaded(level, pos)) continue;
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
      hints.clear();
      worldRevision++;
    }
    if (mc.player == null) return;
    boolean held =
        mc.player.getMainHandItem().is(RailContent.RAIL.get())
            || mc.player.getOffhandItem().is(RailContent.RAIL.get());
    boolean anchorChanged = false;
    if (start != null) {
      var at = new BlockPos(start.x(), start.y(), start.z());
      // A client view-distance boundary is not proof that the server-owned anchor was removed.
      // Keep the UUID selection; the server clears a genuinely unavailable/removed owner.
      if (RailPlan.loaded(mc.level, at)) {
        anchorChanged = !mc.level.getBlockState(at).is(RailContent.BED.get());
        if (mc.level.getBlockEntity(at) instanceof RailCellBlockEntity cell
            && !cell.segments().isEmpty()) anchorChanged |= !cell.segments().contains(anchor);
      }
    }
    if (start != null
        && (!held || selectedSlot != mc.player.getInventory().selected || anchorChanged)) {
      start = null;
      plan = null;
      lastStart = null;
      PacketDistributor.sendToServer(new RailNetwork.Edit(2));
    }
    if (mc.screen != null || !held) return;
    while (ROTATE.consumeClick()) PacketDistributor.sendToServer(new RailNetwork.Edit(0));
    while (FLIP.consumeClick()) PacketDistributor.sendToServer(new RailNetwork.Edit(1));
    while (CANCEL.consumeClick()) {
      start = null;
      plan = null;
      lastStart = null;
      PacketDistributor.sendToServer(new RailNetwork.Edit(2));
    }
  }

  private static boolean target(Minecraft mc, BlockHitResult hit) {
    return hit.getType() == HitResult.Type.BLOCK
        && RailPlan.loaded(mc.level, hit.getBlockPos())
        && !mc.level.getBlockState(hit.getBlockPos()).isAir();
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    plan = null;
    hovering = null;
    showHints = false;
    if (mc.level == null
        || mc.player == null
        || mc.screen != null
        || !mc.player.getMainHandItem().is(RailContent.RAIL.get())
            && !mc.player.getOffhandItem().is(RailContent.RAIL.get())) return;
    // Extend only an active construction ray. A real surface remains required; MISS has no ghost.
    var result =
        start == null
            ? mc.hitResult
            : mc.player.pick(64, e.getPartialTick().getGameTimeDeltaPartialTick(false), false);
    if (!(result instanceof BlockHitResult hit) || !target(mc, hit)) return;
    hovering = hit;
    RailConnectionResolver.Result resolved = null;
    var end =
        RailPlanner.destination(
            mc.level, hit, RailHeading.fromYaw(mc.player.getYRot()), start == null ? -1 : goal);
    if (end == null) return;
    RailEnd origin = start;
    if (origin != null) {
      if (other == null) return;
      var request =
          RailPlanner.request(
              mc.level,
              hit,
              new RailConnectionResolver.Source(start, other, side),
              RailHeading.fromYaw(mc.player.getYRot()),
              goal);
      if (request == null) return;
      if (!request.equals(lastRequest)) {
        lastRequest = request;
        resolution = RailConnectionResolver.resolve(request);
      }
      resolved = resolution;
      origin = resolved.start();
      end = resolved.end();
    } else {
      if (mc.level.getBlockEntity(hit.getBlockPos()) instanceof RailCellBlockEntity) return;
      origin = end;
      end = RailPlanner.starter(origin).end;
    }

    try {
      if (!origin.equals(lastStart) || !end.equals(lastEnd)) {
        lastStart = origin;
        lastEnd = end;
        geometric = true;
        String invalid = null;
        RailCurve curve;
        try {
          if (resolved != null && !resolved.valid())
            throw new IllegalArgumentException(resolved.reason());
          curve = resolved == null ? RailCurve.plan(origin, end) : resolved.curve();
        } catch (IllegalArgumentException bad) {
          geometric = false;
          invalid = bad.getMessage();
          try {
            curve = RailCurve.preview(origin, end);
          } catch (IllegalArgumentException beyond) {
            curve = RailPlanner.starter(origin);
          }
        }
        cached = new RailPlan(curve);
        ghost = ghost(cached);
        checkedAt = Long.MIN_VALUE;
        if (!geometric) reason = invalid;
      }
      plan = cached;
      if (plan == null) return;
      if (start != null && hit.getDirection() == Direction.UP) {
        showHints = true;
        var base =
            new RailEnd(
                hit.getBlockPos().getX(),
                hit.getBlockPos().getY() + 1,
                hit.getBlockPos().getZ(),
                goal < 0 ? RailHeading.fromYaw(mc.player.getYRot()) : new RailHeading(goal));
        hints.key(
            new EndpointHints.Key(
                start,
                base,
                goal,
                anchor,
                worldRevision + mc.level.getGameTime() / 80,
                other,
                side));
        if (hintTick != mc.level.getGameTime()) {
          hintTick = mc.level.getGameTime();
          hints.advance(
              1,
              point -> {
                var p = new BlockPos(point.x(), point.y() - 1, point.z());
                if (!RailPlan.loaded(mc.level, p))
                  return new EndpointHints.Result(point, "unloaded", 0);
                var h =
                    new BlockHitResult(Vec3.atCenterOf(p).add(0, .5, 0), Direction.UP, p, false);
                var target =
                    RailPlanner.destination(
                        mc.level, h, RailHeading.fromYaw(mc.player.getYRot()), goal);
                try {
                  var intent =
                      RailPlanner.request(
                          mc.level,
                          h,
                          new RailConnectionResolver.Source(start, other, side),
                          RailHeading.fromYaw(mc.player.getYRot()),
                          goal);
                  if (intent == null) return new EndpointHints.Result(point, "unloaded", 0);
                  var connection = RailConnectionResolver.resolve(intent);
                  if (!connection.valid())
                    return new EndpointHints.Result(point, connection.reason(), 0);
                  var candidate = new RailPlan(connection.curve());
                  var status = candidate.inspect(mc.level, mc.player);
                  if (status.equals("ready")
                      && !mc.player.isCreative()
                      && RailPlacement.count(mc.player) < candidate.curve.cost()) status = "price";
                  return new EndpointHints.Result(point, status, candidate.curve.cost());
                } catch (IllegalArgumentException invalid) {
                  return new EndpointHints.Result(point, invalid.getMessage(), 0);
                }
              });
        }
      }
      if (mc.level.getGameTime() - checkedAt >= 4 || checkedAt == Long.MIN_VALUE) {
        if (geometric) {
          reason = plan.inspect(mc.level, mc.player);
          if (reason.equals("ready")
              && !mc.player.isCreative()
              && RailPlacement.count(mc.player) < plan.curve.cost()) reason = "price";
        }
        checkedAt = mc.level.getGameTime();
      }
      if (mc.player.getEyePosition().distanceToSqr(hit.getLocation())
          > mc.player.blockInteractionRange() * mc.player.blockInteractionRange() + .01)
        reason = "reach";
      var pose = e.getPoseStack();
      pose.pushPose();
      var camera = e.getCamera().getPosition();
      pose.translate(-camera.x, -camera.y, -camera.z);
      var buffers = mc.renderBuffers().bufferSource();
      if (showHints) {
        var grid = buffers.getBuffer(RenderType.debugQuads());
        for (int cell = 0; cell < 25; cell++) {
          var point = hints.candidate(cell);
          var value = hints.get(cell);
          float cr = value == null ? .95f : value.valid() ? .2f : 1f,
              cg = value == null ? .65f : value.valid() ? .95f : .23f;
          double y = point.y() + .012;
          for (int square = 0; square < 4; square++) {
            double x = point.x() + .05 + (square % 2) * .45,
                z = point.z() + .05 + (square / 2) * .45;
            if (square == 0 || square == 3) {
              grid.addVertex(pose.last(), (float) x, (float) y, (float) z)
                  .setColor(cr, cg, .2f, .30f);
              grid.addVertex(pose.last(), (float) x, (float) y, (float) (z + .40))
                  .setColor(cr, cg, .2f, .30f);
              grid.addVertex(pose.last(), (float) (x + .40), (float) y, (float) (z + .40))
                  .setColor(cr, cg, .2f, .30f);
              grid.addVertex(pose.last(), (float) (x + .40), (float) y, (float) z)
                  .setColor(cr, cg, .2f, .30f);
            }
          }
        }
        buffers.endBatch(RenderType.debugQuads());
      }
      boolean valid = reason.equals("ready");
      float r = valid ? .2f : 1, g = valid ? 1 : .18f;
      pose.pushPose();
      pose.translate(plan.curve.start.x(), plan.curve.start.y(), plan.curve.start.z());
      var fill = buffers.getBuffer(RenderType.debugQuads());
      for (int i = 0; i < ghost.length; i += 3)
        fill.addVertex(pose.last(), ghost[i], ghost[i + 1], ghost[i + 2]).setColor(r, g, .4f, .22f);
      buffers.endBatch(RenderType.debugQuads());
      pose.popPose();
      var out = buffers.getBuffer(RenderType.lines());
      var prev = plan.curve.sample(0, false);
      for (double d = .5; d < plan.curve.length() + .5; d += .5) {
        var next = plan.curve.sample(Math.min(d, plan.curve.length()), false);
        for (double offset : new double[] {-1, -.72, 0, .72, 1})
          line(
              pose,
              out,
              prev.position().add(prev.side().scale(offset)),
              next.position().add(next.side().scale(offset)),
              r,
              g,
              .4f);
        prev = next;
      }
      for (var point : List.of(plan.curve.start, plan.curve.end))
        LevelRenderer.renderLineBox(
            pose,
            out,
            new AABB(point.x(), point.y(), point.z(), point.x() + 1, point.y() + .4, point.z() + 1),
            .3f,
            .5f,
            1,
            1);
      // Full-sized shared oriented body, not an axis-aligned square around each rail point.
      for (double d = 0; d <= plan.curve.length(); d += 4)
        factorycraft.client.trains.StockDebug.outline(
            pose,
            out,
            factorycraft.simulation.trains.StockEnvelope.of(
                factorycraft.simulation.trains.BogiePose.onCurve(plan.curve, d)),
            1,
            .72f,
            .2f,
            .28f);
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
        || !mc.player.getMainHandItem().is(RailContent.RAIL.get())
            && !mc.player.getOffhandItem().is(RailContent.RAIL.get())
        || hovering == null
        || plan == null) return;
    var message =
        Component.translatable(
            "rail.factorycraft.preview",
            plan.curve.cost(),
            Component.translatable("rail.factorycraft.direction_" + goal),
            Component.translatable("rail.factorycraft." + reason),
            Component.translatable("rail.factorycraft.source_" + side));
    var lines = mc.font.split(message, e.getGuiGraphics().guiWidth() - 24);
    for (int row = 0; row < lines.size(); row++)
      e.getGuiGraphics()
          .drawCenteredString(
              mc.font,
              lines.get(row),
              e.getGuiGraphics().guiWidth() / 2,
              e.getGuiGraphics().guiHeight() / 2 + 30 + row * 10,
              reason.equals("ready") ? 0x55ff88 : 0xff7777);
  }
}
