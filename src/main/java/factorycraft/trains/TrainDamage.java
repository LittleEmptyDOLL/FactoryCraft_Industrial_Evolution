package factorycraft.trains;

import factorycraft.combat.Combat;
import factorycraft.simulation.combat.Resistance;
import factorycraft.simulation.trains.TrainImpact;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Impact callbacks run only after movement/resource commit; no resource lives in a projection. */
@EventBusSubscriber(modid = factorycraft.FactoryCraft.ID)
public final class TrainDamage {
  private static final ThreadLocal<Attempt> ATTEMPT = new ThreadLocal<>();

  private static final class Attempt {
    final LivingEntity target;
    LivingDeathEvent death;

    Attempt(LivingEntity target) {
      this.target = target;
    }
  }

  @SubscribeEvent(receiveCanceled = true)
  public static void death(LivingDeathEvent event) {
    var attempt = ATTEMPT.get();
    if (attempt != null && attempt.target == event.getEntity()) attempt.death = event;
  }

  public static TrainImpact.LivingOutcome living(
      TrainData d,
      TrainData.Train t,
      TrainWorld.Contact hit,
      double mass,
      double speed,
      ServerPlayer driver) {
    var living = (LivingEntity) hit.entity();
    var car = t.cars.get(hit.car());
    var projection = d.level.getEntity(car.id);
    double before = living.getHealth() + living.getAbsorptionAmount();
    if (!Double.isFinite(before) || before <= 0)
      return TrainImpact.living(mass, speed, car.kind, 0, false, false);
    d.impactCallbacks++;
    var previous = ATTEMPT.get();
    var attempt = new Attempt(living);
    ATTEMPT.set(attempt);
    boolean accepted;
    try {
      accepted =
          Combat.damage(
              d.level,
              living,
              projection,
              driver,
              t.scope,
              "impact",
              TrainImpact.kinetic(mass, speed) / TrainImpact.energyPerHp(car.kind),
              false);
    } finally {
      if (previous == null) ATTEMPT.remove();
      else ATTEMPT.set(previous);
    }
    double after = Math.max(0, living.getHealth()) + living.getAbsorptionAmount();
    if (!Double.isFinite(after)) accepted = false;
    var outcome =
        TrainImpact.living(
            mass,
            speed,
            car.kind,
            Double.isFinite(after) ? Math.max(0, before - after) / Combat.HP_SCALE : 0,
            accepted,
            accepted
                && living.getHealth() <= 0
                && attempt.death != null
                && !attempt.death.isCanceled());
    // Death/mod callbacks may have retired, split or packed the source. Never hurt its successor.
    if (d.train(t.id) == t && d.owner(car.id) == t && d.car(car.id) == car)
      StockPlacement.hurt(d, car.id, Resistance.apply(outcome.recoilHp(), 50, .6));
    return outcome;
  }

  public static void impact(
      TrainData d,
      TrainData.Train t,
      TrainWorld.Contact hit,
      double mass,
      double speed,
      ServerPlayer driver) {
    if (d.train(t.id) != t || t.provisional || hit.car() < 0 || hit.car() >= t.cars.size()) return;
    var car = t.cars.get(hit.car());
    var projection = d.level.getEntity(car.id);
    var amount = TrainImpact.damage(mass, speed, car.kind, hit.entity() != null);
    // Snapshot IDs before callbacks: death listeners may split, retire or pack either owner.
    var otherId = hit.entity() instanceof StockEntity stock ? stock.getUUID() : null;
    if (hit.entity() instanceof LivingEntity living)
      Combat.damage(
          d.level, living, projection, driver, t.scope, "impact", amount.obstacle(), false);
    else if (otherId != null) {
      var other = d.owner(otherId);
      if (other != null && TrainWorld.available(d, other)) {
        other.speed = 0;
        other.desired = 0;
        other.status = TrainData.Status.BLOCKED;
        d.setDirty();
        StockPlacement.hurt(d, otherId, Resistance.apply(amount.obstacle(), 50, .6));
      }
    }
    // Native/Factorio impact resistance applies once, in source HP. No explosion/refill.
    StockPlacement.hurt(d, car.id, Resistance.apply(amount.vehicle(), 50, .6));
  }

  private TrainDamage() {}
}
