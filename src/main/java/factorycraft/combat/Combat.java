package factorycraft.combat;

import factorycraft.FactoryCraft;
import factorycraft.research.ResearchTeams;
import factorycraft.simulation.combat.ShotClock;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** All debits, timing, hit selection and damage happen on the logical server. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class Combat {
  public static final double HP_SCALE = 0.2;

  public static boolean loadedRay(ServerLevel l, Vec3 a, Vec3 b) {
    int n = Math.max(1, (int) Math.ceil(a.distanceTo(b) * 2));
    for (int i = 0; i <= n; i++)
      if (!l.hasChunkAt(BlockPos.containing(a.lerp(b, i / (double) n)))) return false;
    return true;
  }

  public static Vec3 loadedEnd(ServerLevel l, Vec3 from, Vec3 to) {
    int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 4));
    Vec3 last = from;
    for (int i = 0; i <= steps; i++) {
      var point = from.lerp(to, i / (double) steps);
      if (!l.hasChunkAt(BlockPos.containing(point))) break;
      last = point;
    }
    return last;
  }

  public static boolean allowed(
      ServerLevel l, LivingEntity target, Entity owner, String scope, boolean automatic) {
    if (!target.isAlive()
        || target == owner
        || target.isSpectator()
        || target instanceof TamableAnimal
        || owner != null && owner.isAlliedTo(target)) return false;
    if (scope.startsWith("vanilla:")
        && target.getTeam() != null
        && target.getTeam().getName().equals(scope.substring(8))) return false;
    if (automatic && !(target instanceof Enemy)) return false;
    if (target instanceof Player p) {
      if (!(owner instanceof Player shooter)
          || !l.getServer().isPvpAllowed()
          || !shooter.canHarmPlayer(p)) return false;
      var currentForce = ResearchTeams.resolve(shooter);
      if (currentForce.isEmpty() || ResearchTeams.member(p, currentForce.get())) return false;
      if (scope.equals("unavailable") || !scope.isEmpty() && ResearchTeams.member(p, scope))
        return false;
    }
    return true;
  }

  /** Rolling stock uses the same loaded, canonical and ownership guards as its interaction. */
  public static boolean allowed(
      ServerLevel l, Entity target, Entity owner, String scope, boolean automatic) {
    if (target instanceof LivingEntity living) return allowed(l, living, owner, scope, automatic);
    if (!(target instanceof factorycraft.trains.StockEntity stock)
        || stock.isRemoved()
        || automatic
        || !l.hasChunkAt(stock.blockPosition())) return false;
    var data = factorycraft.trains.TrainData.get(l);
    var train = data.owner(stock.getUUID());
    return train != null
        && !train.provisional
        && data.car(stock.getUUID()) != null
        && factorycraft.trains.TrainPorts.canonicalView(stock)
        && (owner == null
            ? scope.isEmpty() || train.scope.equals(scope)
            : owner instanceof ServerPlayer p && factorycraft.trains.TrainWorld.mayUse(train, p));
  }

  public static boolean sight(ServerLevel l, Vec3 a, Entity target) {
    Vec3 end = target.getBoundingBox().getCenter();
    return loadedRay(l, a, end)
        && l.clip(
                    new ClipContext(
                        a, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target))
                .getType()
            == HitResult.Type.MISS;
  }

  public static void damage(
      ServerLevel l,
      Entity target,
      Entity direct,
      Entity owner,
      String scope,
      String category,
      double original,
      boolean automatic) {
    damageAt(
        l,
        target,
        direct,
        owner,
        scope,
        category,
        original,
        automatic,
        direct != null ? direct.position() : owner == null ? target.position() : owner.position());
  }

  public static void damageAt(
      ServerLevel l,
      Entity target,
      Entity direct,
      Entity owner,
      String scope,
      String category,
      double original,
      boolean automatic,
      Vec3 origin) {
    if (original <= 0 || !Double.isFinite(original) || !allowed(l, target, owner, scope, automatic))
      return;
    String key =
        category.equals("fire")
            ? "fire"
            : category.equals("explosion") || category.equals("rocket")
                ? "explosion"
                : java.util.Set.of("laser", "acid", "poison", "electric", "impact")
                        .contains(category)
                    ? category
                    : "physical";
    var holder =
        l.registryAccess()
            .registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(
                ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, key)));
    target.hurt(new DamageSource(holder, direct, owner, origin), (float) (original * HP_SCALE));
  }

  public static void hitscan(
      ServerLevel l,
      Vec3 from,
      Vec3 aim,
      Entity owner,
      String scope,
      CombatProfile p,
      double amount,
      boolean automatic) {
    if (p.category().equals("fire")) {
      FlameSimulation.launch(l, from, aim, owner, scope, p, amount, automatic);
      return;
    }
    var impacts = new java.util.ArrayList<CombatEffects.Impact>();
    for (int pellet = 0; pellet < p.pellets(); pellet++) {
      Vec3 d =
          aim.add(
                  (l.random.nextDouble() - .5) * p.spread(),
                  (l.random.nextDouble() - .5) * p.spread(),
                  (l.random.nextDouble() - .5) * p.spread())
              .normalize();
      Vec3 end = loadedEnd(l, from, from.add(d.scale(p.range())));
      if (from.distanceToSqr(end) < .0001) continue;
      var block =
          l.clip(
              new ClipContext(
                  from,
                  end,
                  ClipContext.Block.COLLIDER,
                  ClipContext.Fluid.NONE,
                  net.minecraft.world.phys.shapes.CollisionContext.empty()));
      end = block.getLocation();
      double best = from.distanceToSqr(end);
      Entity hit = null;
      Vec3 impact = end;
      var area = new AABB(from, end).inflate(.25);
      for (var e :
          l.getEntities((Entity) null, area, e -> allowed(l, e, owner, scope, automatic))) {
        var point =
            e instanceof factorycraft.trains.StockEntity stock
                ? factorycraft.trains.TrainCollision.clip(stock, from, end)
                : e.getBoundingBox().inflate(.05).clip(from, end);
        if (point.isPresent() && from.distanceToSqr(point.get()) < best) {
          best = from.distanceToSqr(point.get());
          hit = e;
          impact = point.get();
        }
      }
      if (hit != null) {
        damageAt(l, hit, null, owner, scope, p.category(), amount, automatic, from);
        if (p.category().equals("fire")) hit.igniteForSeconds(3);
      }
      var normal =
          block.getType() == HitResult.Type.BLOCK && hit == null
              ? Vec3.atLowerCornerOf(block.getDirection().getNormal())
              : d.scale(-1);
      impacts.add(
          new CombatEffects.Impact(
              impact, normal, hit != null || block.getType() == HitResult.Type.BLOCK));
    }
    CombatEffects.emit(l, from, impacts, p.category().equals("laser") ? 1 : 0, automatic);
  }

  public static boolean fire(Player player, ItemStack weapon, String id) {
    if (!(player.level() instanceof ServerLevel l) || player.isSpectator() || !player.isAlive())
      return false;
    if (!(weapon.getItem() instanceof FirearmItem item) || !item.profile.equals(id)) return false;
    var p = CombatProfile.get(l, id);
    if (p == null) return false;
    var n = player.getPersistentData().getCompound("FactoryCombat");
    var clock = new ShotClock();
    if (n.contains("Due")) clock.restore(n.getLong("Due"), n.getLong("Last"));
    long tick = l.getGameTime();
    if (!clock.ready(tick)) return false;
    String scope = ResearchTeams.resolve(player).orElse("unavailable");
    boolean fired = false;
    for (int burst = 0; burst < 3 && clock.ready(tick); burst++) {
      if (p.category().equals("fire") && !FlameSimulation.canLaunch(l)) break;
      if (!LoadedMagazine.load(weapon, player, p.ammunition())) break;
      var ammo = LoadedMagazine.spend(weapon);
      if (ammo.isEmpty()) break;
      clock.firePeriod(tick, CombatResearch.period(l, scope, p));
      n.putLong("Due", clock.due());
      n.putLong("Last", clock.last());
      player.getPersistentData().put("FactoryCombat", n);
      // The round is now irreversibly owned by the shot, before any hit/projectile callbacks.
      double amount =
          p.damage()
              * MagazineItem.damage(ammo)
              * (1
                  + CombatResearch.bonus(
                      l,
                      scope,
                      "ammo-damage",
                      p.category().equals("fire") ? "flamethrower" : p.category()));
      weapon.set(factorycraft.registry.Content.SHOT_TIME.get(), tick);
      Vec3 from = player.getEyePosition(), aim = player.getLookAngle();
      if (p.category().equals("rocket")) {
        var shot = new CombatProjectile(l, player, ammo, scope, amount, 0);
        shot.range(p.range());
        shot.setPos(from);
        shot.shoot(aim.x, aim.y, aim.z, 1.2f, 0);
        l.addFreshEntity(shot);
        CombatEffects.emit(
            l,
            from,
            java.util.List.of(
                new CombatEffects.Impact(from.add(aim.scale(.7)), aim.scale(-1), false)),
            0,
            false);
      } else {
        var shotProfile = p;
        if (ammo.getItem() instanceof MagazineItem m && m.pellets > 0)
          shotProfile =
              new CombatProfile(
                  p.category(),
                  p.ammunition(),
                  p.cooldown(),
                  p.range(),
                  p.damage(),
                  m.pellets,
                  p.spread(),
                  p.energy());
        hitscan(l, from, aim, player, scope, shotProfile, amount, false);
      }
      fired = true;
    }
    if (fired) {
      player.getInventory().setChanged();
      player.containerMenu.broadcastChanges();
    }
    return fired;
  }

  @SubscribeEvent
  public static void login(PlayerEvent.PlayerLoggedInEvent e) {
    var p = e.getEntity();
    var data = p.getPersistentData();
    var saved = data.getCompound(Player.PERSISTED_NBT_TAG);
    if (!saved.getBoolean("FactoryPistol")) {
      saved.putBoolean("FactoryPistol", true);
      data.put(Player.PERSISTED_NBT_TAG, saved);
      var item =
          net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
              ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "pistol"));
      var stack = new ItemStack(item);
      if (!p.getInventory().add(stack)) p.drop(stack, false);
    }
  }

  private Combat() {}
}
