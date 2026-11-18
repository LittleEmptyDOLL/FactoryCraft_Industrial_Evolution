package factorycraft.trains;

import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;

/**
 * One item -> one canonical car. Packing retires the saved owner before returning any resources.
 */
public final class StockPlacement {
  public static String place(ServerPlayer p, UseOnContext context, StockSpec kind) {
    var l = p.serverLevel();
    var data = TrainData.get(l);
    var rails = RailwayData.get(l);
    var stack = context.getItemInHand();
    var original = stack.copy();
    if (p.isSpectator()
        || !p.mayBuild()
        || p.getEyePosition().distanceToSqr(context.getClickLocation())
            > p.blockInteractionRange() * p.blockInteractionRange() + .01) return "permission";
    var id = rails.nearest(context.getClickedPos(), context.getClickLocation());
    if (id == null || !rails.available(id)) return "track";
    String scope = factorycraft.research.ResearchTeams.resolve(p).orElse("unavailable");
    if (!p.isCreative()
        && (scope.equals("unavailable")
            || !factorycraft.research.ResearchCatalog.get(l)
                .allowed(
                    scope,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "factorycraft", kind.id),
                    l))) return "research";
    var curve = rails.segment(id).curve;
    double best = Double.MAX_VALUE, center = 0;
    for (double at = 0; at <= curve.length(); at += .125) {
      var q = curve.sample(at, false).position();
      double ds = context.getClickLocation().distanceToSqr(q.x(), q.y(), q.z());
      if (ds < best) {
        best = ds;
        center = at;
      }
    }
    boolean reverse =
        TrainWorld.vec(curve.sample(center, false).tangent()).dot(p.getLookAngle()) < 0;
    if (reverse) center = curve.length() - center;
    TrainCar car;
    var custom =
        stack
            .getOrDefault(
                DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
            .copyTag();
    try {
      car =
          custom.contains("FactoryTrainStock")
              ? TrainCar.load(
                  custom.getCompound("FactoryTrainStock"), l.registryAccess(), data::setDirty, true)
              : new TrainCar(UUID.randomUUID(), kind, data::setDirty);
    } catch (RuntimeException invalid) {
      return "invalid";
    }
    if (custom.contains("FactoryTrainStock") && stack.getCount() != 1) return "invalid";
    if (car.kind != kind || car.health <= 0) return "destroyed";
    car.reversed = false;
    if (car.shell.isEmpty()) {
      car.shell = stack.copyWithCount(1);
      var shellData = custom.copy();
      shellData.remove("FactoryTrainStock");
      if (shellData.isEmpty()) car.shell.remove(DataComponents.CUSTOM_DATA);
      else
        car.shell.set(
            DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(shellData));
    }
    var path = new TrainPath(rails.graph, new RailGraph.Directed(id, reverse));
    var t = new TrainData.Train(UUID.randomUUID(), p.getUUID(), scope, path, center);
    t.cars.add(car);
    TrainData.Train neighbor = null;
    double nearest = 2.0;
    // Physical placement examines only trains incident to nearby route edges, not the whole world.
    var candidates = new HashSet<UUID>();
    for (var edge : rails.graph.approaches(curve, 16))
      candidates.addAll(data.occupancy.owners(edge));
    candidates.addAll(data.occupancy.owners(id));
    for (var key : candidates) {
      var old = data.train(key);
      if (old == null || !data.stopped(old) || !TrainWorld.mayUse(old, p)) continue;
      for (boolean front : new boolean[] {true, false}) {
        double slot = front ? old.head + 7 : old.center(old.cars.size() - 1) - 7;
        var trial = new TrainPath(old.path.legs());
        if (!extend(trial, rails.graph, slot - 3.5, slot + 3.5)) continue;
        var pose = BogiePose.sample(trial, rails.graph, slot, false);
        double distance =
            pose.center()
                .distance(
                    new RailVector(
                        context.getClickLocation().x,
                        context.getClickLocation().y,
                        context.getClickLocation().z));
        if (distance < nearest) {
          nearest = distance;
          neighbor = old;
          t.path = trial;
          t.head = slot;
          car.reversed = TrainWorld.vec(pose.forward()).dot(p.getLookAngle()) < 0;
        }
      }
    }
    if (!extend(t.path, rails.graph, t.rear(), t.front())) return "space";
    if (!TrainWorld.available(data, t)) return "unloaded";
    String reason =
        TrainWorld.inspect(data, t, t.head, t.head, neighbor == null ? null : neighbor.id);
    if (!reason.equals("ready")) return reason;
    var intervals = TrackOccupancy.span(t.path, t.rear(), t.front());
    if (!data.occupancy.canReserve(t.id, intervals)) return "occupied";
    // The unchanged foundation envelope is the claim surface, even though stock is an entity.
    var snapshots = new ArrayList<net.neoforged.neoforge.common.util.BlockSnapshot>();
    var box = TrainWorld.box(BogiePose.sample(t.path, rails.graph, t.head, car.reversed));
    for (var pos :
        BlockPos.betweenClosed(
            BlockPos.containing(box.minX, box.minY, box.minZ),
            BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
      if (!l.hasChunkAt(pos) || !l.mayInteract(p, pos) || !p.mayUseItemAt(pos, Direction.UP, stack))
        return "permission";
      snapshots.add(net.neoforged.neoforge.common.util.BlockSnapshot.create(l.dimension(), l, pos));
    }
    if (net.neoforged.neoforge.event.EventHooks.onMultiBlockPlace(p, snapshots, Direction.UP))
      return "permission";
    if (!ItemStack.matches(original, context.getItemInHand())) return "invalid";
    reason = TrainWorld.inspect(data, t, t.head, t.head, neighbor == null ? null : neighbor.id);
    if (!reason.equals("ready")) return reason;
    // Sole resource transfer precedes entity joins/capability/menu callbacks.
    t.provisional = true;
    if (!data.occupancy.canReserve(t.id, intervals)) return "occupied";
    data.add(t);
    TrainWorld.views(data, t);
    var view = l.getEntity(car.id);
    if (!(view instanceof StockEntity)
        || data.owner(car.id) != t
        || !TrainWorld.available(data, t)
        || !ItemStack.matches(original, context.getItemInHand())) {
      data.retire(t);
      if (view != null) view.discard();
      return "permission";
    }
    if (!p.isCreative() || custom.contains("FactoryTrainStock")) stack.shrink(1);
    t.provisional = false;
    data.setDirty();
    p.getInventory().setChanged();
    if (neighbor != null) couple(data, neighbor, t);
    return "placed";
  }

  static boolean extend(TrainPath path, RailGraph graph, double min, double max) {
    int count = 0;
    while (min < path.min() || max > path.max()) {
      if (++count > 128 || !path.extend(graph, max > path.max(), 0)) return false;
    }
    return true;
  }

  public static boolean couple(TrainData d, TrainData.Train a, TrainData.Train b) {
    if (a == b
        || !d.stopped(a)
        || !d.stopped(b)
        || a.cars.size() + b.cars.size() > TrainData.MAX_CARS
        || !a.scope.equals(b.scope)) return false;
    double shift = 0;
    int sign = 0;
    outer:
    for (var x : a.path.legs())
      for (var y : b.path.legs())
        if (x.edge().edge().equals(y.edge().edge())) {
          sign = x.edge().reverse() == y.edge().reverse() ? 1 : -1;
          shift = sign > 0 ? x.begin() - y.begin() : x.begin() + y.end();
          break outer;
        }
    if (sign == 0) return false;
    var legs = new ArrayList<>(a.path.legs());
    for (var y : b.path.legs()) {
      var edge = sign > 0 ? y.edge() : new RailGraph.Directed(y.edge().edge(), !y.edge().reverse());
      double begin = sign > 0 ? shift + y.begin() : shift - y.end();
      var mapped = new TrainPath.Leg(edge, begin, y.length());
      boolean same = false;
      for (var x : legs)
        if (Math.abs(x.begin() - begin) < 1e-6 && x.edge().equals(edge)) {
          same = true;
          break;
        }
      if (!same) legs.add(mapped);
    }
    legs.sort(Comparator.comparingDouble(TrainPath.Leg::begin));
    TrainPath path;
    try {
      path = new TrainPath(legs);
    } catch (IllegalArgumentException conflict) {
      return false;
    }
    record Member(double coordinate, TrainCar car, boolean flip) {}
    var members = new ArrayList<Member>();
    for (int i = 0; i < a.cars.size(); i++)
      members.add(new Member(a.center(i), a.cars.get(i), false));
    for (int i = 0; i < b.cars.size(); i++)
      members.add(new Member(sign * b.center(i) + shift, b.cars.get(i), sign < 0));
    members.sort(Comparator.comparingDouble(Member::coordinate).reversed());
    for (int i = 1; i < members.size(); i++)
      if (Math.abs(members.get(i - 1).coordinate - members.get(i).coordinate - 7) > .02)
        return false;
    d.retire(a);
    d.retire(b);
    a.path = path;
    a.head = members.getFirst().coordinate;
    a.cars.clear();
    for (var m : members) {
      if (m.flip) m.car.reversed = !m.car.reversed;
      a.cars.add(m.car);
    }
    d.add(a);
    TrainWorld.views(d, a);
    return true;
  }

  public static boolean split(TrainData d, TrainData.Train t, int after) {
    if (!d.stopped(t) || after < 0 || after >= t.cars.size() - 1) return false;
    var second =
        new TrainData.Train(
            UUID.randomUUID(), t.owner, t.scope, new TrainPath(t.path.legs()), t.center(after + 1));
    second.distance = t.distance;
    var tail = new ArrayList<>(t.cars.subList(after + 1, t.cars.size()));
    d.retire(t);
    t.cars.subList(after + 1, t.cars.size()).clear();
    second.cars.addAll(tail);
    d.add(t);
    d.add(second);
    TrainWorld.views(d, t);
    TrainWorld.views(d, second);
    return true;
  }

  public static boolean pack(TrainData d, TrainData.Train t, UUID id, ServerPlayer p) {
    if (t == null || t.provisional || d.train(t.id) != t) return false;
    if ((!d.stopped(t) && !(t.speed == 0 && t.status == TrainData.Status.BROKEN_TRACK))
        || !TrainWorld.mayUse(t, p)) return false;
    if (!(d.level.getEntity(id) instanceof StockEntity projection)
        || projection.isRemoved()
        || p.distanceToSqr(projection) > 64
        || !d.level.hasChunkAt(projection.blockPosition())) return false;
    var c = d.car(id);
    int index = t.cars.indexOf(c);
    if (index < 0) return false;
    if (d.level.getEntity(id) instanceof StockEntity view && !view.getPassengers().isEmpty())
      return false;
    var item = c.portable(d.level.registryAccess());
    remove(d, t, index);
    if (!p.addItem(item)) p.drop(item, false);
    return true;
  }

  static void remove(TrainData d, TrainData.Train t, int index) {
    var car = t.cars.get(index);
    var before = new ArrayList<>(t.cars.subList(0, index));
    var after = new ArrayList<>(t.cars.subList(index + 1, t.cars.size()));
    d.retire(t);
    if (!before.isEmpty()) {
      var front = new TrainData.Train(t.id, t.owner, t.scope, new TrainPath(t.path.legs()), t.head);
      front.cars.addAll(before);
      front.distance = t.distance;
      d.add(front);
      TrainWorld.views(d, front);
    }
    if (!after.isEmpty()) {
      var rear =
          new TrainData.Train(
              UUID.randomUUID(),
              t.owner,
              t.scope,
              new TrainPath(t.path.legs()),
              t.center(index + 1));
      rear.cars.addAll(after);
      rear.distance = t.distance;
      d.add(rear);
      TrainWorld.views(d, rear);
    }
    var entity = d.level.getEntity(car.id);
    if (entity != null) entity.discard();
  }

  public static boolean damage(TrainData d, StockEntity entity, DamageSource source, float amount) {
    var t = d.owner(entity.getUUID());
    var c = d.car(entity.getUUID());
    if (t == null || c == null || t.provisional) return false;
    if (source.getEntity() instanceof ServerPlayer p && !TrainWorld.mayUse(t, p)) return false;
    var kind = factorycraft.combat.ArmorAttributes.classify(source);
    double flat = 0, percent = 0;
    if (kind != null)
      switch (kind) {
        case FIRE -> {
          flat = 15;
          percent = .5;
        }
        case PHYSICAL, EXPLOSION -> {
          flat = 15;
          percent = .3;
        }
        case IMPACT -> {
          flat = 50;
          percent = .6;
        }
        case ACID -> {
          flat = 3;
          percent = .2;
        }
        default -> {}
      }
    double damage =
        factorycraft.simulation.combat.Resistance.apply(
            amount / factorycraft.combat.Combat.HP_SCALE, flat, percent);
    c.health = Math.max(0, c.health - damage);
    d.setDirty();
    if (c.health == 0) {
      var item = c.portable(d.level.registryAccess());
      var at = entity.position();
      remove(d, t, t.cars.indexOf(c));
      d.level.addFreshEntity(
          new net.minecraft.world.entity.item.ItemEntity(d.level, at.x, at.y, at.z, item));
    } else TrainWorld.views(d, t);
    return true;
  }

  private StockPlacement() {}
}
