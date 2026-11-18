package factorycraft.client.trains;

import factorycraft.FactoryCraft;
import factorycraft.simulation.trains.StockSpec;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Local nonowning audio/particles for tracked locomotive handles only. */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class TrainSounds {
  private static final Map<StockEntity, Engine> engines = new WeakHashMap<>();

  @SubscribeEvent
  public static void join(EntityJoinLevelEvent e) {
    if (e.getLevel().isClientSide
        && e.getEntity() instanceof StockEntity stock
        && stock.kind() == StockSpec.LOCOMOTIVE) engines.put(stock, null);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    var it = engines.entrySet().iterator();
    while (it.hasNext()) {
      var entry = it.next();
      var stock = entry.getKey();
      if (stock.isRemoved() || stock.level() != mc.level) {
        if (entry.getValue() != null) entry.getValue().finish();
        it.remove();
        continue;
      }
      if (stock.speed() > .001) {
        if (entry.getValue() == null || entry.getValue().isStopped()) {
          var sound = new Engine(stock);
          entry.setValue(sound);
          mc.getSoundManager().play(sound);
        }
        if (mc.player != null
            && mc.player.distanceToSqr(stock) < 64 * 64
            && stock.tickCount % 8 == 0) {
          double a = Math.toRadians(stock.getYRot());
          mc.level.addParticle(
              ParticleTypes.SMOKE,
              stock.getX() - Math.sin(a) * .05,
              stock.getY() + 3,
              stock.getZ() + Math.cos(a) * .05,
              0,
              .045,
              0);
        }
      } else if (entry.getValue() != null) {
        entry.getValue().finish();
        entry.setValue(null);
      }
    }
  }

  private static final class Engine extends AbstractTickableSoundInstance {
    private final StockEntity entity;

    Engine(StockEntity e) {
      super(TrainContent.ENGINE.get(), SoundSource.NEUTRAL, RandomSource.create());
      entity = e;
      looping = true;
      delay = 0;
      volume = .7f;
      x = e.getX();
      y = e.getY() + 1;
      z = e.getZ();
    }

    public void finish() {
      stop();
    }

    public void tick() {
      if (entity.isRemoved() || entity.speed() <= .001) {
        stop();
        return;
      }
      x = entity.getX();
      y = entity.getY() + 1;
      z = entity.getZ();
      pitch = .7f + Math.min(.6f, entity.speed() / 2);
      volume = .45f + Math.min(.3f, entity.speed() / 2);
    }
  }

  private TrainSounds() {}
}
