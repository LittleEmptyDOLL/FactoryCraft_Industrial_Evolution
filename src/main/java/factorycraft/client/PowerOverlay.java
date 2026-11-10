package factorycraft.client;

import factorycraft.FactoryCraft;
import factorycraft.energy.*;
import factorycraft.multiblock.*;
import factorycraft.simulation.power.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Exact horizontal squares at base Y; proposed neighbors refresh only when the target changes or
 * every 10 ticks.
 */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class PowerOverlay {
  private static BlockPos cached;
  private static net.minecraft.world.level.Level cachedLevel;
  private static Direction direction;
  private static PoleSpec type;
  private static long tick;
  private static java.util.List<factorycraft.blockentity.PoleBlockEntity> candidates =
      java.util.List.of();

  @SubscribeEvent
  public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
    if (event.getLevel() == cachedLevel) {
      cachedLevel = null;
      cached = null;
      candidates = java.util.List.of();
    }
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null
        || mc.player == null
        || mc.screen != null
        || !(mc.hitResult instanceof BlockHitResult hit)
        || !PlacementTarget.valid(mc.level, mc.player, hit)) return;
    PowerFootprint bounds = null;
    PoleSpec spec = null;
    BlockPos anchor = null;
    Direction facing = null;
    boolean placing = false;
    for (var hand : InteractionHand.values())
      if (mc.player.getItemInHand(hand).getItem() instanceof factorycraft.items.MachineItem item) {
        var context = new BlockPlaceContext(new UseOnContext(mc.player, hand, hit));
        var state = item.getBlock().getStateForPlacement(context);
        var p = state == null ? null : MachineProfile.of(state);
        if (p != null && p.poleType() != null) {
          var plan = PlacementPlan.inspect(context, state);
          placing = plan.valid();
          anchor = plan.anchor();
          facing = plan.facing();
          spec = p.poleType();
          var a = p.origin(anchor, facing);
          var b = p.offset(anchor, facing, p.layout.width() - 1, 0, p.layout.depth() - 1);
          bounds =
              new PowerFootprint(
                  Math.min(a.getX(), b.getX()),
                  Math.max(a.getX(), b.getX()),
                  anchor.getY(),
                  Math.min(a.getZ(), b.getZ()),
                  Math.max(a.getZ(), b.getZ()));
          break;
        }
      }
    var existing = PowerWireTool.pole(mc.level, hit.getBlockPos());
    if (bounds == null && existing != null && existing.assembly().formed()) {
      bounds = PowerGrid.footprint(existing);
      spec = existing.spec();
      anchor = existing.getBlockPos();
      facing = existing.getBlockState().getValue(factorycraft.blocks.FactoryBlock.FACING);
    }
    if (bounds == null) return;
    var pose = e.getPoseStack();
    var camera = e.getCamera().getPosition();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(RenderType.lines());
    double cx = bounds.centerX2() * .5, cz = bounds.centerZ2() * .5, half = spec.supply * .5;
    LevelRenderer.renderLineBox(
        pose,
        lines,
        new AABB(cx - half, bounds.y() + .02, cz - half, cx + half, bounds.y() + .055, cz + half),
        .2f,
        .75f,
        1f,
        .9f);
    if (placing) {
      if (mc.level != cachedLevel
          || !anchor.equals(cached)
          || direction != facing
          || type != spec
          || mc.level.getGameTime() < tick
          || mc.level.getGameTime() - tick >= 10) {
        cachedLevel = mc.level;
        cached = anchor;
        direction = facing;
        type = spec;
        tick = mc.level.getGameTime();
        candidates = PowerWires.candidates(mc.level, bounds, spec, anchor);
      }
      for (var other : candidates) {
        if (other.getLevel() != mc.level
            || PowerWires.at(mc.level, other.getBlockPos()) != other
            || !other.visualAvailable()) continue;
        var a = new Vec3(cx, bounds.y() + spec.height - .12, cz);
        var b = PoleGeometry.attachment(other);
        float dx = (float) (b.x - a.x),
            dy = (float) (b.y - a.y),
            dz = (float) (b.z - a.z),
            len = (float) a.distanceTo(b);
        if (len == 0) continue;
        lines
            .addVertex(pose.last(), (float) cx, bounds.y() + .08f, (float) cz)
            .setColor(.3f, 1f, .45f, .8f)
            .setNormal(pose.last(), dx / len, dy / len, dz / len);
        lines
            .addVertex(pose.last(), (float) b.x, bounds.y() + .08f, (float) b.z)
            .setColor(.3f, 1f, .45f, .8f)
            .setNormal(pose.last(), dx / len, dy / len, dz / len);
        lines
            .addVertex(pose.last(), (float) a.x, (float) a.y, (float) a.z)
            .setColor(.3f, 1f, .45f, .8f)
            .setNormal(pose.last(), dx / len, dy / len, dz / len);
        lines
            .addVertex(pose.last(), (float) b.x, (float) b.y, (float) b.z)
            .setColor(.3f, 1f, .45f, .8f)
            .setNormal(pose.last(), dx / len, dy / len, dz / len);
      }
    }
    buffers.endBatch(RenderType.lines());
    pose.popPose();
  }

  private PowerOverlay() {}
}
