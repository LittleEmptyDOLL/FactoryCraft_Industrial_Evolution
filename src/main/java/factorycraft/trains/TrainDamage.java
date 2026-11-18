package factorycraft.trains;

import factorycraft.combat.Combat;
import factorycraft.simulation.combat.Resistance;
import factorycraft.simulation.trains.TrainImpact;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Impact callbacks run only after movement/resource commit; no resource lives in a projection. */
public final class TrainDamage {
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
