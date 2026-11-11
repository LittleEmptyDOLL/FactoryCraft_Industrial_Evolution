package factorycraft.client.screens;

import factorycraft.research.ResearchCatalog;
import factorycraft.simulation.circuits.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.fluids.FluidStack;

/** UI-only registry index; never consulted by server graph evaluation. */
public final class SignalIcons {
  public static Component name(SignalKey key) {
    var id = ResourceLocation.parse(key.id());
    return switch (key.type()) {
      case ITEM ->
          BuiltInRegistries.ITEM
              .getOptional(id)
              .map(i -> new ItemStack(i).getHoverName())
              .orElse(Component.literal(key.id()));
      case FLUID ->
          BuiltInRegistries.FLUID
              .getOptional(id)
              .map(f -> new FluidStack(f, 1).getHoverName())
              .orElse(Component.literal(key.id()));
      case RECIPE ->
          Component.translatableWithFallback(
              "recipe." + id.getNamespace() + "." + id.getPath(), key.id());
      case TECHNOLOGY ->
          Component.translatableWithFallback(
              "technology." + id.getNamespace() + "." + id.getPath(), key.id());
      case VIRTUAL ->
          Component.translatableWithFallback(
              "signal." + id.getNamespace() + "." + id.getPath(), id.getPath().replace('_', ' '));
    };
  }

  public static ItemStack icon(SignalKey key) {
    var id = ResourceLocation.parse(key.id());
    if (key.type() == SignalKey.Type.ITEM)
      return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    if (key.type() == SignalKey.Type.FLUID)
      return BuiltInRegistries.FLUID
          .getOptional(id)
          .map(f -> new ItemStack(f.getBucket()))
          .orElse(ItemStack.EMPTY);
    return new ItemStack(
        key.type() == SignalKey.Type.TECHNOLOGY
            ? Items.BOOK
            : key.type() == SignalKey.Type.RECIPE ? Items.CRAFTING_TABLE : Items.PAPER);
  }

  public static List<SignalKey> choices(SignalKey.Type type) {
    var result = new ArrayList<SignalKey>();
    var level = Minecraft.getInstance().level;
    if (type == SignalKey.Type.ITEM)
      BuiltInRegistries.ITEM.keySet().forEach(id -> result.add(new SignalKey(type, id.toString())));
    else if (type == SignalKey.Type.FLUID)
      BuiltInRegistries.FLUID.keySet().stream()
          .filter(id -> !id.getPath().startsWith("flowing_") && !id.getPath().equals("empty"))
          .forEach(id -> result.add(new SignalKey(type, id.toString())));
    else if (type == SignalKey.Type.VIRTUAL) {
      for (char c = 'a'; c <= 'z'; c++) result.add(SignalKey.virtual("signal_" + c));
      for (int i = 0; i < 10; i++) result.add(SignalKey.virtual("signal_" + i));
      for (String s :
          List.of(
              "state",
              "energy",
              "progress",
              "game_tick",
              "day_tick",
              "day_length",
              "signal_count",
              "hand_size",
              "research_cost",
              "research_units",
              "research_level")) result.add(SignalKey.virtual(s));
    } else if (level != null && type == SignalKey.Type.TECHNOLOGY)
      ResearchCatalog.get(level)
          .entries()
          .forEach(e -> result.add(new SignalKey(type, e.id().toString())));
    else if (level != null)
      level.getRecipeManager().getRecipes().stream()
          .filter(r -> r.id().getNamespace().equals("factorycraft"))
          .forEach(r -> result.add(new SignalKey(type, r.id().toString())));
    return result.stream().sorted().toList();
  }

  private SignalIcons() {}
}
