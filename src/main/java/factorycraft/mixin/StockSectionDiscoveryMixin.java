package factorycraft.mixin;

import factorycraft.simulation.trains.StockGeometry;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Expand only section discovery. EntitySection still applies the original exact query bounds. */
@Mixin(EntitySectionStorage.class)
abstract class StockSectionDiscoveryMixin {
  @ModifyConstant(
      method = "forEachAccessibleNonEmptySection",
      constant = @Constant(doubleValue = 2.0),
      require = 4)
  private double factorycraft$horizontalDiscovery(double original) {
    return StockGeometry.DISCOVERY_HORIZONTAL;
  }

  @ModifyConstant(
      method = "forEachAccessibleNonEmptySection",
      constant = @Constant(doubleValue = 4.0),
      require = 1)
  private double factorycraft$aboveOriginDiscovery(double original) {
    return StockGeometry.DISCOVERY_ABOVE;
  }

  @ModifyConstant(
      method = "forEachAccessibleNonEmptySection",
      constant = @Constant(doubleValue = 0.0),
      require = 1)
  private double factorycraft$belowOriginDiscovery(double original) {
    return StockGeometry.DISCOVERY_BELOW;
  }
}
