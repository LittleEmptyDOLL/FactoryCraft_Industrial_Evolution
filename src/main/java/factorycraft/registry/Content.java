package factorycraft.registry;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.FactoryBlock;
import factorycraft.blocks.FactoryKind;
import factorycraft.production.FactoryMenu;
import factorycraft.recipes.FactoryRecipe;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class Content {
  public static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks(FactoryCraft.ID);
  public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>>
      FEATURES = DeferredRegister.create(Registries.FEATURE, FactoryCraft.ID);
  public static final DeferredHolder<
          net.minecraft.world.level.levelgen.feature.Feature<?>,
          factorycraft.worldgen.OilDepositFeature>
      OIL_FEATURE = FEATURES.register("oil_deposit", factorycraft.worldgen.OilDepositFeature::new);
  public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryCraft.ID);
  public static final DeferredRegister<BlockEntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryCraft.ID);
  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(Registries.MENU, FactoryCraft.ID);
  public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
      DeferredRegister.create(Registries.RECIPE_TYPE, FactoryCraft.ID);
  public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
      DeferredRegister.create(Registries.RECIPE_SERIALIZER, FactoryCraft.ID);
  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FactoryCraft.ID);
  public static final Map<FactoryKind, DeferredBlock<FactoryBlock>> FACTORY =
      new EnumMap<>(FactoryKind.class);
  public static final Map<factorycraft.fluids.FluidKind, DeferredBlock<Block>> FLUID_BLOCKS =
      new EnumMap<>(factorycraft.fluids.FluidKind.class);

  static {
    for (var kind : factorycraft.fluids.FluidKind.values()) {
      DeferredBlock<Block> block =
          BLOCKS.register(
              kind.id,
              () -> {
                var properties =
                    BlockBehaviour.Properties.of()
                        .strength(2.5f)
                        .noOcclusion()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
                return kind == factorycraft.fluids.FluidKind.PIPE
                    ? new factorycraft.blocks.FluidPipeBlock(properties)
                    : new factorycraft.blocks.FluidDeviceBlock(kind, properties);
              });
      FLUID_BLOCKS.put(kind, block);
      ITEMS.register(
          kind.id,
          () ->
              kind == factorycraft.fluids.FluidKind.PIPE
                  ? new factorycraft.items.FluidBlockItem(block.get(), new Item.Properties())
                  : new factorycraft.items.MachineItem(block.get(), new Item.Properties()));
    }
  }

  public static final Map<factorycraft.thermal.ThermalKind, DeferredBlock<Block>> THERMAL_BLOCKS =
      new EnumMap<>(factorycraft.thermal.ThermalKind.class);

  static {
    for (var kind : factorycraft.thermal.ThermalKind.values()) {
      var block =
          BLOCKS.<Block>register(
              kind.id,
              () ->
                  new factorycraft.blocks.ThermalBlock(
                      kind,
                      BlockBehaviour.Properties.of()
                          .strength(3)
                          .noOcclusion()
                          .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
      THERMAL_BLOCKS.put(kind, block);
      ITEMS.register(
          kind.id, () -> new factorycraft.items.MachineItem(block.get(), new Item.Properties()));
    }
  }

  static {
    for (var kind : FactoryKind.values()) {
      var block =
          BLOCKS.register(
              kind.id,
              () ->
                  new FactoryBlock(
                      kind,
                      BlockBehaviour.Properties.of()
                          .strength(2.5f)
                          .noOcclusion()
                          .pushReaction(
                              kind.casing() || kind.large()
                                  ? net.minecraft.world.level.material.PushReaction.BLOCK
                                  : net.minecraft.world.level.material.PushReaction.NORMAL)));
      FACTORY.put(kind, block);
      if (kind == FactoryKind.TRANSPORT_PART || kind.casing()) continue;
      ITEMS.register(
          kind.id,
          () ->
              kind.large()
                  ? new factorycraft.items.MachineItem(block.get(), new Item.Properties())
                  : kind.lift()
                      ? new factorycraft.items.LiftItem(block.get(), new Item.Properties())
                      : kind.belt()
                          ? new factorycraft.items.BeltItem(block.get(), new Item.Properties())
                          : new factorycraft.items.FactoryBlockItem(
                              block.get(), new Item.Properties()));
    }
  }

  public static final DeferredBlock<factorycraft.blocks.OilDepositBlock> OIL_DEPOSIT =
      BLOCKS.register(
          "oil_deposit",
          () ->
              new factorycraft.blocks.OilDepositBlock(
                  BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                      .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OilDepositBlockEntity>>
      OIL_DEPOSIT_ENTITY =
          ENTITIES.register(
              "oil_deposit",
              () ->
                  BlockEntityType.Builder.of(OilDepositBlockEntity::new, OIL_DEPOSIT.get())
                      .build(null));
  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<factorycraft.circuits.CircuitNodeBlockEntity>>
      CIRCUIT =
          ENTITIES.register(
              "circuit",
              () ->
                  BlockEntityType.Builder.of(
                          factorycraft.circuits.CircuitNodeBlockEntity::new,
                          Arrays.stream(FactoryKind.values())
                              .filter(FactoryKind::circuit)
                              .map(k -> FACTORY.get(k).get())
                              .toArray(Block[]::new))
                      .build(null));
  public static final DeferredHolder<MenuType<?>, MenuType<factorycraft.circuits.CircuitMenu>>
      CIRCUIT_MENU =
          MENUS.register(
              "circuit", () -> IMenuTypeExtension.create(factorycraft.circuits.CircuitMenu::new));
  public static final DeferredItem<factorycraft.circuits.CircuitWireItem>
      RED_CIRCUIT_WIRE =
          ITEMS.register(
              "red_circuit_wire",
              () ->
                  new factorycraft.circuits.CircuitWireItem(0, new Item.Properties().stacksTo(1))),
      GREEN_CIRCUIT_WIRE =
          ITEMS.register(
              "green_circuit_wire",
              () ->
                  new factorycraft.circuits.CircuitWireItem(1, new Item.Properties().stacksTo(1))),
      BLUE_CIRCUIT_WIRE =
          ITEMS.register(
              "blue_circuit_wire",
              () ->
                  new factorycraft.circuits.CircuitWireItem(2, new Item.Properties().stacksTo(1))),
      WHITE_CIRCUIT_WIRE =
          ITEMS.register(
              "white_circuit_wire",
              () ->
                  new factorycraft.circuits.CircuitWireItem(3, new Item.Properties().stacksTo(1)));
  public static final DeferredHolder<MenuType<?>, MenuType<factorycraft.inserters.InserterMenu>>
      INSERTER_MENU =
          MENUS.register(
              "inserter",
              () -> IMenuTypeExtension.create(factorycraft.inserters.InserterMenu::new));
  public static final DeferredHolder<MenuType<?>, MenuType<factorycraft.logistics.SplitterMenu>>
      SPLITTER_MENU =
          MENUS.register(
              "splitter",
              () -> IMenuTypeExtension.create(factorycraft.logistics.SplitterMenu::new));
  public static final DeferredHolder<MenuType<?>, MenuType<factorycraft.petroleum.PetroleumMenu>>
      PETROLEUM_MENU =
          MENUS.register(
              "petroleum",
              () -> IMenuTypeExtension.create(factorycraft.petroleum.PetroleumMenu::new));
  public static final DeferredHolder<RecipeType<?>, RecipeType<factorycraft.recipes.ChemicalRecipe>>
      CHEMISTRY =
          RECIPE_TYPES.register(
              "chemistry",
              () ->
                  new RecipeType<>() {
                    public String toString() {
                      return "factorycraft:chemistry";
                    }
                  });
  public static final DeferredHolder<
          RecipeSerializer<?>, RecipeSerializer<factorycraft.recipes.ChemicalRecipe>>
      CHEMISTRY_SERIALIZER =
          SERIALIZERS.register("chemistry", factorycraft.recipes.ChemicalRecipe.Serializer::new);
  public static final Map<
          factorycraft.resources.ResourceKind,
          DeferredBlock<factorycraft.blocks.ResourceDepositBlock>>
      RESOURCES = new EnumMap<>(factorycraft.resources.ResourceKind.class);

  static {
    for (var kind : factorycraft.resources.ResourceKind.values())
      RESOURCES.put(
          kind,
          BLOCKS.register(
              kind.id,
              () ->
                  new factorycraft.blocks.ResourceDepositBlock(
                      kind,
                      BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)
                          .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))));
  }

  public static final DeferredBlock<factorycraft.blocks.ResourceDepositBlock>
      IRON_ORE = RESOURCES.get(factorycraft.resources.ResourceKind.IRON),
      COPPER_ORE = RESOURCES.get(factorycraft.resources.ResourceKind.COPPER);
  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<ResourceDepositBlockEntity>>
      RESOURCE_DEPOSIT_ENTITY =
          ENTITIES.register(
              "resource_deposit",
              () ->
                  BlockEntityType.Builder.of(
                          ResourceDepositBlockEntity::new,
                          RESOURCES.values().stream().map(DeferredBlock::get).toArray(Block[]::new))
                      .build(null));
  public static final DeferredHolder<
          net.minecraft.world.level.levelgen.feature.Feature<?>,
          factorycraft.worldgen.ResourceVeinFeature>
      RESOURCE_PATCH_FEATURE =
          FEATURES.register("resource_patch", factorycraft.worldgen.ResourceVeinFeature::new);

  static {
    for (String name :
        List.of(
            "iron_plate",
            "copper_plate",
            "iron_gear",
            "copper_wire",
            "electronic_circuit",
            "plastic_bar",
            "sulfur",
            "advanced_circuit",
            "battery")) ITEMS.registerSimpleItem(name, new Item.Properties());
  }

  public static final DeferredItem<factorycraft.items.FactoryWrenchItem> WRENCH =
      ITEMS.register(
          "wrench", () -> new factorycraft.items.FactoryWrenchItem(new Item.Properties()));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeltBlockEntity>> BELT =
      ENTITIES.register(
          "belt",
          () ->
              BlockEntityType.Builder.of(
                      BeltBlockEntity::new,
                      blocks(
                          Arrays.stream(FactoryKind.values())
                              .filter(FactoryKind::belt)
                              .toArray(FactoryKind[]::new)))
                  .build(null));
  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<factorycraft.blockentity.TransportPartBlockEntity>>
      TRANSPORT_PART =
          ENTITIES.register(
              "transport_part",
              () ->
                  BlockEntityType.Builder.of(
                          factorycraft.blockentity.TransportPartBlockEntity::new,
                          blocks(FactoryKind.TRANSPORT_PART))
                      .build(null));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InserterBlockEntity>>
      INSERTER =
          ENTITIES.register(
              "inserter",
              () ->
                  BlockEntityType.Builder.of(
                          InserterBlockEntity::new,
                          Arrays.stream(FactoryKind.values())
                              .filter(FactoryKind::inserter)
                              .map(k -> FACTORY.get(k).get())
                              .toArray(Block[]::new))
                      .build(null));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>>
      MACHINE =
          ENTITIES.register(
              "machine",
              () ->
                  BlockEntityType.Builder.of(
                          MachineBlockEntity::new,
                          blocks(
                              FactoryKind.DRILL,
                              FactoryKind.FURNACE,
                              FactoryKind.ASSEMBLER,
                              FactoryKind.GENERATOR,
                              FactoryKind.INDUSTRIAL_FURNACE,
                              FactoryKind.PUMPJACK,
                              FactoryKind.REFINERY,
                              FactoryKind.CHEMICAL_PLANT,
                              FactoryKind.ADVANCED_ASSEMBLER))
                      .build(null));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PoleBlockEntity>> POLE =
      ENTITIES.register(
          "pole",
          () ->
              BlockEntityType.Builder.of(
                      PoleBlockEntity::new,
                      blocks(
                          FactoryKind.POLE,
                          FactoryKind.MEDIUM_POLE,
                          FactoryKind.BIG_POLE,
                          FactoryKind.SUBSTATION))
                  .build(null));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CasingBlockEntity>>
      CASING =
          ENTITIES.register(
              "casing",
              () ->
                  BlockEntityType.Builder.of(
                          CasingBlockEntity::new,
                          blocks(FactoryKind.CASING, FactoryKind.REFINERY_CASING))
                      .build(null));
  public static final DeferredHolder<MenuType<?>, MenuType<FactoryMenu>> MENU =
      MENUS.register("machine", () -> IMenuTypeExtension.create(FactoryMenu::new));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidBlockEntity>> FLUID =
      ENTITIES.register(
          "fluid",
          () ->
              BlockEntityType.Builder.of(
                      FluidBlockEntity::new,
                      FLUID_BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new))
                  .build(null));
  public static final DeferredHolder<MenuType<?>, MenuType<factorycraft.fluids.FluidMenu>>
      FLUID_MENU =
          MENUS.register(
              "fluid", () -> IMenuTypeExtension.create(factorycraft.fluids.FluidMenu::new));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThermalBlockEntity>>
      THERMAL =
          ENTITIES.register(
              "thermal",
              () ->
                  BlockEntityType.Builder.of(
                          ThermalBlockEntity::new,
                          THERMAL_BLOCKS.values().stream()
                              .map(DeferredBlock::get)
                              .toArray(Block[]::new))
                      .build(null));
  public static final DeferredHolder<MenuType<?>, MenuType<factorycraft.thermal.ThermalMenu>>
      THERMAL_MENU =
          MENUS.register(
              "thermal", () -> IMenuTypeExtension.create(factorycraft.thermal.ThermalMenu::new));
  public static final DeferredHolder<RecipeType<?>, RecipeType<factorycraft.recipes.BoilingRecipe>>
      BOILING =
          RECIPE_TYPES.register(
              "boiling",
              () ->
                  new RecipeType<>() {
                    public String toString() {
                      return "factorycraft:boiling";
                    }
                  });
  public static final DeferredHolder<
          RecipeSerializer<?>, RecipeSerializer<factorycraft.recipes.BoilingRecipe>>
      BOILING_SERIALIZER =
          SERIALIZERS.register("boiling", factorycraft.recipes.BoilingRecipe.Serializer::new);
  public static final DeferredHolder<RecipeType<?>, RecipeType<FactoryRecipe>> PROCESSING =
      RECIPE_TYPES.register(
          "processing",
          () ->
              new RecipeType<>() {
                public String toString() {
                  return "factorycraft:processing";
                }
              });
  public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FactoryRecipe>>
      PROCESSING_SERIALIZER = SERIALIZERS.register("processing", FactoryRecipe.Serializer::new);
  public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>>
      COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, FactoryCraft.ID);
  public static final DeferredHolder<
          net.minecraft.core.component.DataComponentType<?>,
          net.minecraft.core.component.DataComponentType<
              net.neoforged.neoforge.fluids.SimpleFluidContent>>
      CANISTER_FLUID =
          COMPONENTS.register(
              "canister_fluid",
              () ->
                  net.minecraft.core.component.DataComponentType
                      .<net.neoforged.neoforge.fluids.SimpleFluidContent>builder()
                      .persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
                      .networkSynchronized(
                          net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC)
                      .build());
  public static final DeferredItem<factorycraft.items.FluidCanisterItem> FLUID_CANISTER =
      ITEMS.register(
          "fluid_canister",
          () -> new factorycraft.items.FluidCanisterItem(new Item.Properties().stacksTo(1)));
  public static final DeferredItem<Item> STEEL_PLATE = ITEMS.registerSimpleItem("steel_plate");
  public static final DeferredItem<Item> ENGINE_UNIT = ITEMS.registerSimpleItem("engine_unit");
  public static final DeferredItem<Item> ELECTRIC_ENGINE_UNIT =
      ITEMS.registerSimpleItem("electric_engine_unit");
  public static final DeferredItem<Item> PROCESSING_UNIT =
      ITEMS.registerSimpleItem("processing_unit");
  public static final DeferredItem<factorycraft.modules.ModuleItem> SPEED_MODULE =
      ITEMS.register(
          "speed_module",
          () ->
              new factorycraft.modules.ModuleItem(
                  factorycraft.modules.ModuleItem.Kind.SPEED, new Item.Properties()));
  public static final DeferredItem<factorycraft.modules.ModuleItem> EFFICIENCY_MODULE =
      ITEMS.register(
          "efficiency_module",
          () ->
              new factorycraft.modules.ModuleItem(
                  factorycraft.modules.ModuleItem.Kind.EFFICIENCY, new Item.Properties()));
  public static final DeferredItem<factorycraft.modules.ModuleItem> PRODUCTIVITY_MODULE =
      ITEMS.register(
          "productivity_module",
          () ->
              new factorycraft.modules.ModuleItem(
                  factorycraft.modules.ModuleItem.Kind.PRODUCTIVITY, new Item.Properties()));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeaconBlockEntity>>
      BEACON =
          ENTITIES.register(
              "beacon",
              () ->
                  BlockEntityType.Builder.of(BeaconBlockEntity::new, blocks(FactoryKind.BEACON))
                      .build(null));
  public static final DeferredHolder<MenuType<?>, MenuType<factorycraft.modules.ModuleMenu>>
      MODULE_MENU =
          MENUS.register(
              "modules", () -> IMenuTypeExtension.create(factorycraft.modules.ModuleMenu::new));
  public static final DeferredItem<Item> SCIENCE_BLUE =
      ITEMS.registerSimpleItem("chemical_science_pack");
  public static final DeferredItem<Item> SCIENCE_RED =
      ITEMS.registerSimpleItem("automation_science_pack");
  public static final DeferredItem<Item> SCIENCE_GREEN =
      ITEMS.registerSimpleItem("logistic_science_pack");
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabBlockEntity>> LAB =
      ENTITIES.register(
          "lab",
          () ->
              BlockEntityType.Builder.of(LabBlockEntity::new, blocks(FactoryKind.LAB)).build(null));
  public static final DeferredHolder<MenuType<?>, MenuType<factorycraft.research.ResearchMenu>>
      RESEARCH_MENU =
          MENUS.register(
              "research", () -> IMenuTypeExtension.create(factorycraft.research.ResearchMenu::new));
  public static final DeferredHolder<
          RecipeType<?>, RecipeType<factorycraft.research.TechnologyRecipe>>
      RESEARCH = RECIPE_TYPES.register("research", () -> new RecipeType<>() {});
  public static final DeferredHolder<
          RecipeSerializer<?>, RecipeSerializer<factorycraft.research.TechnologyRecipe>>
      RESEARCH_SERIALIZER =
          SERIALIZERS.register("research", factorycraft.research.TechnologyRecipe.Serializer::new);

  static {
    TABS.register(
        "factorycraft",
        () ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.factorycraft"))
                .icon(() -> new ItemStack(FACTORY.get(FactoryKind.ASSEMBLER).get()))
                .displayItems(
                    (p, out) ->
                        ITEMS.getEntries().stream()
                            .filter(
                                i ->
                                    !java.util.List.of(
                                            FactoryKind.CASING.id,
                                            FactoryKind.REFINERY_CASING.id,
                                            FactoryKind.TRANSPORT_PART.id)
                                        .contains(i.getId().getPath()))
                            .forEach(i -> out.accept(i.get())))
                .build());
  }

  private static Block[] blocks(FactoryKind... kinds) {
    return Arrays.stream(kinds).map(k -> FACTORY.get(k).get()).toArray(Block[]::new);
  }

  public static void register(IEventBus bus) {
    factorycraft.thermal.SteamFluids.register(bus);
    factorycraft.petroleum.PetroleumFluids.register(bus);
    COMPONENTS.register(bus);
    FEATURES.register(bus);
    BLOCKS.register(bus);
    ITEMS.register(bus);
    ENTITIES.register(bus);
    MENUS.register(bus);
    RECIPE_TYPES.register(bus);
    SERIALIZERS.register(bus);
    TABS.register(bus);
    bus.addListener(Content::capabilities);
  }

  private static void capabilities(RegisterCapabilitiesEvent e) {
    e.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK, TRANSPORT_PART.get(), (be, side) -> be.inventory());
    e.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK, BEACON.get(), (be, side) -> be.inventory());
    e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BEACON.get(), (be, side) -> be.energy);
    e.registerItem(
        Capabilities.FluidHandler.ITEM,
        (stack, context) -> factorycraft.items.FluidCanisterItem.handler(stack),
        FLUID_CANISTER.get());
    e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LAB.get(), (be, side) -> be.inventory());
    e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LAB.get(), (be, side) -> be.energy);
    e.registerBlockEntity(
        Capabilities.FluidHandler.BLOCK,
        MACHINE.get(),
        (be, side) -> factorycraft.multiblock.AssemblyAccess.fluid(be, be.getBlockPos(), side));
    e.registerBlockEntity(
        Capabilities.FluidHandler.BLOCK, CASING.get(), (be, side) -> be.fluid(side));
    e.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK, THERMAL.get(), (be, side) -> be.automation());
    e.registerBlockEntity(
        Capabilities.EnergyStorage.BLOCK,
        THERMAL.get(),
        (be, side) -> be.generatesElectricity() ? be.energyPort : null);
    e.registerBlockEntity(
        Capabilities.FluidHandler.BLOCK,
        THERMAL.get(),
        (be, side) -> be.structurePort(be.getBlockPos(), side));
    e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BELT.get(), (be, side) -> be.inventory());
    e.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK, INSERTER.get(), (be, side) -> be.inventory());
    e.registerBlockEntity(
        Capabilities.EnergyStorage.BLOCK,
        INSERTER.get(),
        (be, side) -> be.electric() ? be.energyPort : null);
    e.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK, MACHINE.get(), MachineBlockEntity::automation);
    e.registerBlockEntity(
        Capabilities.EnergyStorage.BLOCK, MACHINE.get(), (be, side) -> be.energyPort);
    e.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK, CASING.get(), (be, side) -> be.inventory());
    e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CASING.get(), (be, side) -> be.energy);
    e.registerBlockEntity(
        Capabilities.FluidHandler.BLOCK,
        FLUID.get(),
        (be, side) -> be.structurePort(be.getBlockPos(), side));
    e.registerBlockEntity(
        Capabilities.EnergyStorage.BLOCK,
        FLUID.get(),
        (be, side) -> be.kind() == factorycraft.fluids.FluidKind.PUMP ? be.energyPort : null);
  }

  public static final DeferredItem<factorycraft.energy.PowerWireTool> POWER_WIRE_TOOL =
      ITEMS.register(
          "copper_wire_tool",
          () -> new factorycraft.energy.PowerWireTool(new Item.Properties().stacksTo(1)));
  public static final DeferredHolder<MenuType<?>, MenuType<factorycraft.energy.PoleMenu>>
      POLE_MENU =
          MENUS.register(
              "pole", () -> IMenuTypeExtension.create(factorycraft.energy.PoleMenu::new));

  private Content() {}
}
