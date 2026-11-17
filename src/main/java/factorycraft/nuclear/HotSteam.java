package factorycraft.nuclear;

import factorycraft.FactoryCraft;
import factorycraft.registry.Content;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.registries.*;

/** Temperature-distinct 500 C steam; no world/source block and no conversion from 165 C steam. */
public final class HotSteam {
  private static final DeferredRegister<FluidType> TYPES =
      DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, FactoryCraft.ID);
  private static final DeferredRegister<Fluid> FLUIDS =
      DeferredRegister.create(Registries.FLUID, FactoryCraft.ID);
  public static final DeferredHolder<FluidType, FluidType> TYPE =
      TYPES.register(
          "hot_steam",
          () ->
              new FluidType(
                  FluidType.Properties.create()
                      .descriptionId("fluid.factorycraft.hot_steam")
                      .density(-100)
                      .temperature(773)
                      .viscosity(100)
                      .canConvertToSource(false)) {
                public ItemStack getBucket(FluidStack stack) {
                  return stack.getComponentsPatch().isEmpty()
                      ? super.getBucket(stack)
                      : ItemStack.EMPTY;
                }
              });
  public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STEAM =
      FLUIDS.register("hot_steam", () -> new BaseFlowingFluid.Source(properties()));
  public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING =
      FLUIDS.register("flowing_hot_steam", () -> new BaseFlowingFluid.Flowing(properties()));
  public static final DeferredItem<BucketItem> BUCKET =
      Content.ITEMS.register(
          "hot_steam_bucket",
          () ->
              new BucketItem(
                  STEAM.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

  private static BaseFlowingFluid.Properties properties() {
    return new BaseFlowingFluid.Properties(TYPE, STEAM, FLOWING).bucket(BUCKET);
  }

  public static void register(IEventBus bus) {
    TYPES.register(bus);
    FLUIDS.register(bus);
  }

  private HotSteam() {}
}
