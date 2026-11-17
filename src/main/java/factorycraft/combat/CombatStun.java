package factorycraft.combat;

import factorycraft.FactoryCraft;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Only affected UUIDs are indexed. The mob's own saved tag owns its original AI state. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class CombatStun {
  private static final String KEY = "FactoryCombatStun";
  private static final Map<ServerLevel, Set<UUID>> ACTIVE = new IdentityHashMap<>();

  public static void apply(Mob mob, int ticks) {
    if (!(mob.level() instanceof ServerLevel l) || ticks <= 0) return;
    var root = mob.getPersistentData();
    var n = root.getCompound(KEY);
    if (!root.contains(KEY)) n.putBoolean("OriginalNoAI", mob.isNoAi());
    n.putLong("Until", Math.max(n.getLong("Until"), l.getGameTime() + ticks));
    root.put(KEY, n);
    mob.setNoAi(true);
    mob.setDeltaMovement(Vec3.ZERO);
    ACTIVE.computeIfAbsent(l, k -> new HashSet<>()).add(mob.getUUID());
  }

  @SubscribeEvent
  public static void join(EntityJoinLevelEvent e) {
    if (e.getLevel() instanceof ServerLevel l
        && e.getEntity() instanceof Mob m
        && m.getPersistentData().contains(KEY))
      ACTIVE.computeIfAbsent(l, k -> new HashSet<>()).add(m.getUUID());
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload e) {
    ACTIVE.remove(e.getLevel());
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Post e) {
    if (!(e.getLevel() instanceof ServerLevel l)) return;
    var set = ACTIVE.get(l);
    if (set == null) return;
    for (var it = set.iterator(); it.hasNext(); ) {
      var entity = l.getEntity(it.next());
      if (!(entity instanceof Mob m)) {
        it.remove();
        continue;
      }
      var root = m.getPersistentData();
      var n = root.getCompound(KEY);
      if (!root.contains(KEY) || l.getGameTime() >= n.getLong("Until")) {
        m.setNoAi(n.getBoolean("OriginalNoAI"));
        root.remove(KEY);
        it.remove();
      } else if (!m.isNoAi()) m.setNoAi(true);
    }
    if (set.isEmpty()) ACTIVE.remove(l);
  }

  private CombatStun() {}
}
