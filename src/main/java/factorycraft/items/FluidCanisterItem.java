package factorycraft.items;

import factorycraft.registry.Content;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;
import java.util.List;

/** Reusable partial-volume container. Native SimpleFluidContent preserves fluid components. */
public final class FluidCanisterItem extends Item {
    public static final int CAPACITY = 4000;

    public FluidCanisterItem(Properties properties) {
        super(properties);
    }

    public static FluidHandlerItemStack handler(ItemStack stack) {
        return new FluidHandlerItemStack(Content.CANISTER_FLUID, stack, CAPACITY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        var fluid = handler(stack).getFluid();
        lines.add(Component.translatable("tooltip.factorycraft.canister_contents",
                fluid.isEmpty() ? Component.translatable("gui.factorycraft.fluid_empty") : fluid.getHoverName(),
                fluid.getAmount(), CAPACITY));
        lines.add(Component.translatable("tooltip.factorycraft.canister_recovery"));
    }
}
