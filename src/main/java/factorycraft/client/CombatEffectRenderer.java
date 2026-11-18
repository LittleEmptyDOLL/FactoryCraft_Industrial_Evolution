package factorycraft.client;

import factorycraft.FactoryCraft;
import factorycraft.combat.CombatEffects;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Short-lived local lines/particles, bounded per shot, never entities or authoritative projectiles.
 */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class CombatEffectRenderer {
  private static final Deque<CombatEffects.Effect> effects = new ArrayDeque<>();
  private static net.minecraft.client.multiplayer.ClientLevel level;
  public static long received;
  private static int seenKinds;

  @SubscribeEvent
  public static void setup(RegisterClientExtensionsEvent e) {
    CombatEffects.client =
        effect -> {
          var mc = Minecraft.getInstance();
          if (mc.level == null) return;
          if (level != mc.level) {
            level = mc.level;
            effects.clear();
          }
          if (effects.size() >= 512) effects.removeFirst();
          effects.addLast(effect);
          received++;
          int bit = 1 << effect.kind();
          if ((seenKinds & bit) == 0) {
            seenKinds |= bit;
            org.slf4j.LoggerFactory.getLogger("FactoryCraftEffects")
                .info(
                    "Confirmed paid combat effect kind={} impacts={} tick={}",
                    effect.kind(),
                    effect.impacts().size(),
                    effect.tick());
          }
          if (effect.kind() != 2)
            for (var hit : effect.impacts())
              if (hit.hit()) {
                var p = hit.end().add(hit.normal().scale(.025));
                mc.level.addParticle(
                    effect.kind() == 1 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CRIT,
                    p.x,
                    p.y,
                    p.z,
                    hit.normal().x * .06,
                    hit.normal().y * .06,
                    hit.normal().z * .06);
              }
        };
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    var mc = Minecraft.getInstance();
    if (mc.level != level) {
      level = mc.level;
      effects.clear();
    }
    if (level == null) return;
    long now = level.getGameTime();
    effects.removeIf(effect -> now - effect.tick() > lifetime(effect));
    for (var effect : effects)
      if (effect.kind() == 2) {
        var end = effect.impacts().getFirst().end();
        double length = effect.from().distanceTo(end);
        double progress =
            Math.min(1, Math.max(0, (now - effect.tick()) * .75 / Math.max(.01, length)));
        var point = effect.from().lerp(end, progress);
        level.addParticle(ParticleTypes.FLAME, point.x, point.y, point.z, 0, .015, 0);
      }
  }

  private static double lifetime(CombatEffects.Effect e) {
    return e.kind() == 2
        ? Math.ceil(e.from().distanceTo(e.impacts().getFirst().end()) / .75) + 2
        : 4;
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null || mc.level != level || effects.isEmpty()) return;
    var pose = e.getPoseStack();
    pose.pushPose();
    var camera = e.getCamera().getPosition();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    var out = buffers.getBuffer(RenderType.lines());
    float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
    for (var fx : effects) {
      if (fx.kind() == 2) continue;
      float alpha = (float) Math.clamp(1 - (level.getGameTime() + partial - fx.tick()) / 4.0, 0, 1);
      for (var hit : fx.impacts()) {
        var d = hit.end().subtract(fx.from());
        if (d.lengthSqr() < 1e-8) continue;
        var normal = d.normalize();
        for (int core = 0; core < (fx.kind() == 1 ? 3 : 1); core++) {
          double offset = (core - 1) * .015;
          var start = fx.from().add(0, fx.kind() == 1 ? offset : 0, 0);
          var end = hit.end().add(0, fx.kind() == 1 ? offset : 0, 0);
          for (var p : List.of(start, end))
            out.addVertex(pose.last(), (float) p.x, (float) p.y, (float) p.z)
                .setColor(1f, fx.kind() == 1 ? .12f : .8f, fx.kind() == 1 ? .06f : .32f, alpha)
                .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        }
      }
    }
    buffers.endBatch(RenderType.lines());
    pose.popPose();
  }

  private CombatEffectRenderer() {}
}
