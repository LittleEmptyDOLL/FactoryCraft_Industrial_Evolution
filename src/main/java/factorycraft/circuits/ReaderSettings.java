package factorycraft.circuits;

import factorycraft.items.FluidCanisterItem;
import factorycraft.registry.Content;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Immutable, nonowning native fluid sample; amounts never become actual stored fluid. */
public final class ReaderSettings {
  public enum Mode {
    ITEM,
    FLUID,
    INVALID
  }

  public static final ReaderSettings DEFAULT =
      new ReaderSettings(Mode.ITEM, false, FluidStack.EMPTY);
  private final Mode mode;
  private final boolean filtered;
  private final FluidStack filter;

  private ReaderSettings(Mode mode, boolean filtered, FluidStack filter) {
    this.mode = mode;
    this.filtered = filtered;
    this.filter = filter.isEmpty() ? FluidStack.EMPTY : filter.copyWithAmount(1);
  }

  public static ReaderSettings fluid(FluidStack filter) {
    return new ReaderSettings(Mode.FLUID, !filter.isEmpty(), filter);
  }

  public Mode mode() {
    return mode;
  }

  public boolean fluids() {
    return mode == Mode.FLUID;
  }

  public boolean valid() {
    return mode != Mode.INVALID && (!filtered || !filter.isEmpty());
  }

  public boolean isDefault() {
    return mode == Mode.ITEM && !filtered;
  }

  public boolean accepts(FluidStack stack) {
    return valid()
        && !stack.isEmpty()
        && (!filtered || FluidStack.isSameFluidSameComponents(filter, stack));
  }

  public ReaderSettings toggled() {
    return new ReaderSettings(mode == Mode.ITEM ? Mode.FLUID : Mode.ITEM, filtered, filter);
  }

  public ItemStack icon() {
    if (!filtered || filter.isEmpty()) return CircuitProgram.icon(CircuitProgram.A);
    var item = new ItemStack(Content.FLUID_CANISTER.get());
    FluidCanisterItem.handler(item)
        .fill(filter, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
    return item;
  }

  public CompoundTag save(HolderLookup.Provider registries) {
    var t = new CompoundTag();
    t.putInt("Mode", mode.ordinal());
    if (filtered) t.put("Filter", filter.saveOptional(registries));
    return t;
  }

  public static ReaderSettings load(CompoundTag t, HolderLookup.Provider registries) {
    if (t.isEmpty()) return DEFAULT;
    int m = t.getInt("Mode");
    var mode = m >= 0 && m < 2 ? Mode.values()[m] : Mode.INVALID;
    return new ReaderSettings(
        mode, t.contains("Filter"), FluidStack.parseOptional(registries, t.getCompound("Filter")));
  }
}
