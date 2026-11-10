package factorycraft.compatibility.jade;

import factorycraft.blocks.*;
import factorycraft.multiblock.MachineInspection;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.*;

/** All client-only API use is isolated from the optional common plugin. */
final class FactoryJadeClient {
  private static final IBlockComponentProvider REQUEST =
      new IBlockComponentProvider() {
        public ResourceLocation getUid() {
          return ResourceLocation.fromNamespaceAndPath("factorycraft", "machine_inspection");
        }

        public void appendTooltip(ITooltip t, BlockAccessor a, IPluginConfig c) {}

        public boolean isRequired() {
          return true;
        }
      };

  static void register(IWailaClientRegistration r) {
    r.registerBlockComponent(REQUEST, FactoryBlock.class);
    r.registerBlockComponent(REQUEST, FluidDeviceBlock.class);
    r.registerBlockComponent(REQUEST, ThermalBlock.class);
    r.addTooltipCollectedCallback(
        1000,
        (box, accessor) -> {
          if (!(accessor instanceof BlockAccessor a)) return;
          var name = MachineInspection.name(a.getLevel(), a.getPosition());
          if (name == null) return;
          var tooltip = box.getTooltip();
          tooltip.replace(JadeIds.CORE_OBJECT_NAME, name);
          var data = a.getServerData();
          if (!data.contains(FactoryJadePlugin.KEY)) return;
          var snapshot = data.getCompound(FactoryJadePlugin.KEY);
          tooltip.remove(JadeIds.UNIVERSAL_ITEM_STORAGE);
          tooltip.remove(JadeIds.UNIVERSAL_FLUID_STORAGE);
          tooltip.remove(JadeIds.UNIVERSAL_ENERGY_STORAGE);
          tooltip.replace(
              JadeIds.CORE_OBJECT_NAME, Component.translatable(snapshot.getString("Name")));
          var block =
              BuiltInRegistries.BLOCK.get(ResourceLocation.parse(snapshot.getString("Block")));
          box.setIcon(IElementHelper.get().item(new ItemStack(block)));
          var row = new ArrayList<IElement>();
          for (var raw : snapshot.getList("Items", Tag.TAG_COMPOUND)) {
            var item =
                ItemStack.parseOptional(
                    a.getLevel().registryAccess(), ((CompoundTag) raw).getCompound("Stack"));
            if (!item.isEmpty()) row.add(IElementHelper.get().item(item));
            if (row.size() == 6) {
              tooltip.add(row);
              row = new ArrayList<>();
            }
          }
          if (!row.isEmpty()) tooltip.add(row);
          if (snapshot.contains("Energy"))
            tooltip.add(
                Component.translatable(
                    "gui.factorycraft.inspection_energy", snapshot.getInt("Energy")));
          for (var raw : snapshot.getList("Fluids", Tag.TAG_COMPOUND)) {
            var fluid = FluidStack.parseOptional(a.getLevel().registryAccess(), (CompoundTag) raw);
            if (!fluid.isEmpty())
              tooltip.add(
                  Component.literal(fluid.getAmount() + " mB ").append(fluid.getHoverName()));
          }
          if (snapshot.getInt("Progress") > 0 && snapshot.getInt("Duration") > 0)
            tooltip.add(
                Component.translatable(
                    "gui.factorycraft.inspection_progress",
                    snapshot.getInt("Progress"),
                    snapshot.getInt("Duration")));
        });
  }

  private FactoryJadeClient() {}
}
