package factorycraft.fluids;

import net.neoforged.neoforge.fluids.FluidStack;

/** Private immutable normalized template preserves registry identity AND NeoForge data components. */
public final class FluidKey {
    private final FluidStack template;
    public FluidKey(FluidStack stack) { if(stack.isEmpty())throw new IllegalArgumentException("Empty fluid identity");template=stack.copyWithAmount(1); }
    public FluidStack stack(int amount) { return amount<=0 ? FluidStack.EMPTY:template.copyWithAmount(amount); }
    @Override public boolean equals(Object other) { return other instanceof FluidKey key && FluidStack.isSameFluidSameComponents(template,key.template); }
    @Override public int hashCode() { return FluidStack.hashFluidAndComponents(template); }
}
