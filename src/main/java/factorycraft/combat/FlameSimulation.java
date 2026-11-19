package factorycraft.combat;

import factorycraft.FactoryCraft;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Saved ballistic flame packets, bounded ground fires and stickers, without particle entities. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class FlameSimulation {
  private static final int LIMIT = 4096;
  private static final ResourceLocation SLOW =
      ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "fire_sticker");

  private record Flight(
      Vec3 from, Vec3 end, long due, UUID owner, String scope, double damage, boolean automatic) {}

  private static final class Burn {
    UUID target, owner;
    String scope;
    boolean automatic;
    long until, next;
    double scale;
  }

  private static final class Patch {
    Vec3 pos;
    UUID owner;
    String scope;
    boolean automatic;
    long until, lastFuel;
    double scale, strength;
  }

  public static final class Data extends SavedData {
    final PriorityQueue<Flight> flights =
        new PriorityQueue<>(Comparator.comparingLong(Flight::due));
    final Map<UUID, Burn> burns = new HashMap<>();
    final Map<BlockPos, Patch> patches = new HashMap<>();

    static Data get(ServerLevel l) {
      return l.getDataStorage()
          .computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load), "factorycraft_flames");
    }

    static Vec3 vec(CompoundTag n, String key) {
      var p = n.getCompound(key);
      return new Vec3(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z"));
    }

    static void vec(CompoundTag n, String key, Vec3 v) {
      var p = new CompoundTag();
      p.putDouble("X", v.x);
      p.putDouble("Y", v.y);
      p.putDouble("Z", v.z);
      n.put(key, p);
    }

    static UUID uuid(CompoundTag n, String key) {
      return n.hasUUID(key) ? n.getUUID(key) : null;
    }

    static boolean valid(double n) {
      return Double.isFinite(n) && n > 0 && n <= 10000;
    }

    static boolean position(Vec3 v) {
      return Double.isFinite(v.x)
          && Double.isFinite(v.y)
          && Double.isFinite(v.z)
          && Math.abs(v.x) <= 30000000
          && Math.abs(v.z) <= 30000000
          && Math.abs(v.y) <= 30000000;
    }

    static Data load(CompoundTag n, HolderLookup.Provider registry) {
      var d = new Data();
      var list = n.getList("Flights", 10);
      if (list.size() > LIMIT) throw new IllegalArgumentException("Flame flight bound");
      for (int i = 0; i < list.size(); i++) {
        var t = list.getCompound(i);
        var a = vec(t, "From");
        var b = vec(t, "End");
        double amount = t.getDouble("Damage");
        if (position(a) && position(b) && a.distanceToSqr(b) <= 128 * 128 && valid(amount))
          d.flights.add(
              new Flight(
                  a,
                  b,
                  t.getLong("Due"),
                  uuid(t, "Owner"),
                  t.getString("Force"),
                  amount,
                  t.getBoolean("Automatic")));
      }
      list = n.getList("Burns", 10);
      if (list.size() > LIMIT) throw new IllegalArgumentException("Flame burn bound");
      for (int i = 0; i < list.size(); i++) {
        var t = list.getCompound(i);
        if (!t.hasUUID("Target") || !valid(t.getDouble("Scale"))) continue;
        var b = new Burn();
        b.target = t.getUUID("Target");
        b.owner = uuid(t, "Owner");
        b.scope = t.getString("Force");
        b.scale = t.getDouble("Scale");
        b.until = t.getLong("Until");
        b.next = t.getLong("Next");
        b.automatic = t.getBoolean("Automatic");
        d.burns.put(b.target, b);
      }
      list = n.getList("Patches", 10);
      if (list.size() > LIMIT) throw new IllegalArgumentException("Ground flame bound");
      for (int i = 0; i < list.size(); i++) {
        var t = list.getCompound(i);
        var p = new Patch();
        p.pos = vec(t, "At");
        p.scale = t.getDouble("Scale");
        p.strength = t.getDouble("Strength");
        if (!position(p.pos) || !valid(p.scale) || !valid(p.strength) || p.strength > 6) continue;
        p.owner = uuid(t, "Owner");
        p.scope = t.getString("Force");
        p.automatic = t.getBoolean("Automatic");
        p.until = t.getLong("Until");
        p.lastFuel = t.getLong("LastFuel");
        d.patches.put(BlockPos.containing(p.pos), p);
      }
      return d;
    }

    public CompoundTag save(CompoundTag n, HolderLookup.Provider registry) {
      n.putInt("Schema", 1);
      var flightsN = new ListTag();
      for (var f : flights) {
        var t = new CompoundTag();
        vec(t, "From", f.from);
        vec(t, "End", f.end);
        t.putLong("Due", f.due);
        if (f.owner != null) t.putUUID("Owner", f.owner);
        t.putString("Force", f.scope);
        t.putDouble("Damage", f.damage);
        t.putBoolean("Automatic", f.automatic);
        flightsN.add(t);
      }
      n.put("Flights", flightsN);
      var burnsN = new ListTag();
      for (var b : burns.values()) {
        var t = new CompoundTag();
        t.putUUID("Target", b.target);
        if (b.owner != null) t.putUUID("Owner", b.owner);
        t.putString("Force", b.scope);
        t.putDouble("Scale", b.scale);
        t.putLong("Until", b.until);
        t.putLong("Next", b.next);
        t.putBoolean("Automatic", b.automatic);
        burnsN.add(t);
      }
      n.put("Burns", burnsN);
      var patchesN = new ListTag();
      for (var p : patches.values()) {
        var t = new CompoundTag();
        vec(t, "At", p.pos);
        if (p.owner != null) t.putUUID("Owner", p.owner);
        t.putString("Force", p.scope);
        t.putDouble("Scale", p.scale);
        t.putDouble("Strength", p.strength);
        t.putLong("Until", p.until);
        t.putLong("LastFuel", p.lastFuel);
        t.putBoolean("Automatic", p.automatic);
        patchesN.add(t);
      }
      n.put("Patches", patchesN);
      return n;
    }
  }

  public static boolean canLaunch(ServerLevel l) {
    return Data.get(l).flights.size() < LIMIT;
  }

  public static void launch(
      ServerLevel l,
      Vec3 from,
      Vec3 aim,
      Entity owner,
      String scope,
      CombatProfile spec,
      double amount,
      boolean automatic) {
    var d = Data.get(l);
    if (d.flights.size() >= LIMIT) return;
    var end = from.add(aim.normalize().scale(spec.range()));
    // Stop at the first unloaded boundary; never ask the world to load a future projectile path.
    Vec3 last = from;
    int steps = (int) Math.ceil(spec.range() * 2);
    for (int i = 1; i <= steps; i++) {
      var p = from.lerp(end, i / (double) steps);
      if (!l.hasChunkAt(BlockPos.containing(p))) break;
      last = p;
    }
    end = last;
    if (from.distanceToSqr(end) < (automatic ? 36 : 9)) return;
    var block =
        l.clip(
            new ClipContext(
                from,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()));
    end = block.getLocation();
    double distance = from.distanceToSqr(end);
    for (var e :
        l.getEntities(
            (Entity) null,
            new AABB(from, end).inflate(.25),
            e -> Combat.allowed(l, e, owner, scope, automatic))) {
      var hit =
          e instanceof factorycraft.trains.StockEntity stock
              ? factorycraft.trains.TrainCollision.clip(stock, from, end)
              : e.getBoundingBox().clip(from, end);
      if (hit.isPresent() && from.distanceToSqr(hit.get()) < distance) {
        end = hit.get();
        distance = from.distanceToSqr(end);
      }
    }
    if (distance < (automatic ? 36 : 9)) return;
    long due = l.getGameTime() + Math.max(1, (long) Math.ceil(Math.sqrt(distance) / .75));
    d.flights.add(
        new Flight(
            from, end, due, owner == null ? null : owner.getUUID(), scope, amount, automatic));
    d.setDirty();
    CombatEffects.emit(
        l,
        from,
        List.of(
            new CombatEffects.Impact(
                end, aim.normalize().scale(-1), block.getType() == HitResult.Type.BLOCK)),
        2,
        automatic);
    var flash = from.add(aim.normalize().scale(.7)).add(0, -.2, 0);
    l.sendParticles(
        ParticleTypes.FLAME, flash.x, flash.y, flash.z, 1, aim.x * .1, aim.y * .1, aim.z * .1, .05);
  }

  private static Entity owner(ServerLevel l, UUID id) {
    return id == null ? null : l.getEntity(id);
  }

  private static void impact(ServerLevel l, Data d, Flight f) {
    if (!Combat.loadedRay(l, f.from, f.end)) return;
    var at =
        l.clip(
                new ClipContext(
                    f.from,
                    f.end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()))
            .getLocation();
    var source = owner(l, f.owner);
    double scale = f.damage / (f.automatic ? 3 : 2);
    for (var e :
        l.getEntities(
            (Entity) null,
            new AABB(at, at).inflate(2.5),
            e -> Combat.allowed(l, e, source, f.scope, f.automatic)))
      if (e.getBoundingBox().getCenter().distanceToSqr(at) <= 2.5 * 2.5 && Combat.sight(l, at, e)) {
        Combat.damageAt(l, e, null, source, f.scope, "fire", f.damage, f.automatic, at);
        if (d.burns.size() < LIMIT || d.burns.containsKey(e.getUUID())) {
          var b = d.burns.computeIfAbsent(e.getUUID(), k -> new Burn());
          b.target = e.getUUID();
          b.owner = f.owner;
          b.scope = f.scope;
          b.scale = scale;
          b.automatic = f.automatic;
          b.until = l.getGameTime() + 600;
          if (b.next == 0) b.next = l.getGameTime() * 3;
        }
      }
    var key = BlockPos.containing(at);
    var p = d.patches.get(key);
    if (p == null && d.patches.size() < LIMIT) {
      p = new Patch();
      p.pos = at;
      p.until = l.getGameTime() + 40;
      p.strength = f.automatic ? 1 : 2;
      d.patches.put(key, p);
    }
    if (p != null) {
      if (l.getGameTime() - p.lastFuel >= 2) {
        p.until = Math.min(l.getGameTime() + 600, p.until + 50);
        p.strength = Math.min(6, p.strength + 1);
        p.lastFuel = l.getGameTime();
      }
      p.owner = f.owner;
      p.scope = f.scope;
      p.scale = scale;
      p.automatic = f.automatic;
    }
    l.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 5, .4, .2, .4, .02);
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel l)) return;
    var d = Data.get(l);
    long tick = l.getGameTime();
    boolean changed = false;
    int budget = 256;
    while (budget-- > 0 && !d.flights.isEmpty() && d.flights.peek().due <= tick) {
      impact(l, d, d.flights.remove());
      changed = true;
    }
    for (var it = d.burns.values().iterator(); it.hasNext(); ) {
      var b = it.next();
      var entity = l.getEntity(b.target);
      if (tick >= b.until || entity instanceof LivingEntity living && !living.isAlive()) {
        if (entity instanceof LivingEntity living) {
          var speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
          if (speed != null) speed.removeModifier(SLOW);
        }
        it.remove();
        changed = true;
        continue;
      }
      if (!(entity instanceof LivingEntity) && !(entity instanceof factorycraft.trains.StockEntity))
        continue;
      if (entity instanceof factorycraft.trains.StockEntity stock
          && !factorycraft.trains.TrainPorts.canonicalView(stock)) continue;
      var source = owner(l, b.owner);
      if (!Combat.allowed(l, entity, source, b.scope, b.automatic)) {
        var speed =
            entity instanceof LivingEntity living
                ? living.getAttribute(Attributes.MOVEMENT_SPEED)
                : null;
        if (speed != null) speed.removeModifier(SLOW);
        it.remove();
        changed = true;
        continue;
      }
      var speed =
          entity instanceof LivingEntity living
              ? living.getAttribute(Attributes.MOVEMENT_SPEED)
              : null;
      if (speed != null && !speed.hasModifier(SLOW))
        speed.addTransientModifier(
            new AttributeModifier(SLOW, -.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
      if (tick * 3 >= b.next) {
        Combat.damageAt(
            l,
            entity,
            null,
            source,
            b.scope,
            "fire",
            100.0 / 6 * b.scale,
            b.automatic,
            entity.position());
        b.next = (tick * 3 - b.next < 3 ? b.next : tick * 3) + 10;
        changed = true;
      }
    }
    int visuals = 64;
    for (var it = d.patches.values().iterator(); it.hasNext(); ) {
      var p = it.next();
      if (tick >= p.until) {
        it.remove();
        changed = true;
        continue;
      }
      p.strength = Math.max(1, p.strength - .015);
      changed = true;
      if (!l.hasChunkAt(BlockPos.containing(p.pos))) continue;
      if (tick % 4 == 0) {
        var source = owner(l, p.owner);
        for (var e :
            l.getEntities(
                (Entity) null,
                new AABB(p.pos, p.pos).inflate(.8),
                e -> Combat.allowed(l, e, source, p.scope, p.automatic)))
          if (Combat.sight(l, p.pos, e))
            Combat.damageAt(
                l,
                e,
                null,
                source,
                p.scope,
                "fire",
                13.0 / 5 * p.strength * p.scale,
                p.automatic,
                p.pos);
      }
      if (tick % 10 == 0 && visuals-- > 0)
        l.sendParticles(ParticleTypes.FLAME, p.pos.x, p.pos.y, p.pos.z, 1, .15, .05, .15, .01);
    }
    if (changed) d.setDirty();
  }

  private FlameSimulation() {}
}
