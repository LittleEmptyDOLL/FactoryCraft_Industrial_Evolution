package factorycraft.petroleum;

import factorycraft.FactoryCraft;
import factorycraft.registry.Content;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.registries.*;
import java.util.*;

/** Container-only petroleum fluids, using the exact target NeoForge fluid API. */
public final class PetroleumFluids {
    private static final DeferredRegister<FluidType> TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES,FactoryCraft.ID);
    private static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(Registries.FLUID,FactoryCraft.ID);
    public static final Kind CRUDE=new Kind("crude_oil",0xff403627,900);
    public static final Kind GAS=new Kind("petroleum_gas",0xffd6b97b,-10);
    public static final List<Kind> ALL=List.of(CRUDE,GAS);
    public static final class Kind {
        public final String id;public final int tint;
        public final DeferredHolder<FluidType,FluidType> type;
        public final DeferredHolder<Fluid,BaseFlowingFluid.Source> source;
        public final DeferredHolder<Fluid,BaseFlowingFluid.Flowing> flowing;
        public final DeferredItem<BucketItem> bucket;
        private Kind(String id,int tint,int density) {
            this.id=id;this.tint=tint;
            type=TYPES.register(id,()->new FluidType(FluidType.Properties.create().descriptionId("fluid.factorycraft."+id).density(density).canConvertToSource(false)) {
                @Override public ItemStack getBucket(FluidStack stack) { return stack.getComponentsPatch().isEmpty() ? super.getBucket(stack):ItemStack.EMPTY; }
            });
            source=FLUIDS.register(id,()->new BaseFlowingFluid.Source(properties()));flowing=FLUIDS.register("flowing_"+id,()->new BaseFlowingFluid.Flowing(properties()));
            bucket=Content.ITEMS.register(id+"_bucket",()->new BucketItem(source.get(),new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        }
        private BaseFlowingFluid.Properties properties() { return new BaseFlowingFluid.Properties(type,source,flowing).bucket(bucket); }
        public FluidStack stack(int amount) { return new FluidStack(source.get(),amount); }
    }
    public static void register(IEventBus bus) { TYPES.register(bus);FLUIDS.register(bus); }
    private PetroleumFluids() {}
}
