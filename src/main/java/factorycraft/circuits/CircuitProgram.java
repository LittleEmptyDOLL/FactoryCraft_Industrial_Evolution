package factorycraft.circuits;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/** Two type-only ghost signals and a signed scalar. Signal A is the original virtual signal. */
public record CircuitProgram(
    ResourceLocation input,
    ResourceLocation output,
    int operation,
    int operand,
    boolean outputCount) {
  public static final ResourceLocation A =
      ResourceLocation.fromNamespaceAndPath("factorycraft", "signal_a");
  public static final CircuitProgram DEFAULT = new CircuitProgram(A, A, 0, 1, false);

  public CircuitProgram {
    if (input == null || output == null || operation < 0 || operation > 9)
      throw new IllegalArgumentException("Invalid circuit program");
  }

  public static ItemStack icon(ResourceLocation signal) {
    if (signal.equals(A)) {
      var stack = new ItemStack(Items.PAPER);
      stack.set(
          net.minecraft.core.component.DataComponents.CUSTOM_NAME,
          net.minecraft.network.chat.Component.translatable("gui.factorycraft.circuit_a"));
      return stack;
    }
    return BuiltInRegistries.ITEM.getOptional(signal).map(ItemStack::new).orElse(ItemStack.EMPTY);
  }

  public CompoundTag save() {
    var t = new CompoundTag();
    t.putString("Input", input.toString());
    t.putString("Output", output.toString());
    t.putInt("Operation", operation);
    t.putInt("Operand", operand);
    t.putBoolean("OutputCount", outputCount);
    return t;
  }

  public static CircuitProgram load(CompoundTag t) {
    var in = ResourceLocation.tryParse(t.getString("Input"));
    var out = ResourceLocation.tryParse(t.getString("Output"));
    return new CircuitProgram(
        in == null ? A : in,
        out == null ? A : out,
        Math.clamp(t.getInt("Operation"), 0, 9),
        t.getInt("Operand"),
        t.getBoolean("OutputCount"));
  }
}
