package factorycraft.registry;

import factorycraft.FactoryCraft;
import factorycraft.blocks.FactoryBlock;
import factorycraft.blocks.FactoryKind;
import factorycraft.blockentity.*;
import factorycraft.production.FactoryMenu;
import factorycraft.recipes.FactoryRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;
import java.util.*;

public final class Content {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryCraft.ID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,FactoryCraft.ID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.feature.Feature<?>,factorycraft.worldgen.OilDepositFeature> OIL_FEATURE=FEATURES.register("oil_deposit",factorycraft.worldgen.OilDepositFeature::new);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryCraft.ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryCraft.ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryCraft.ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, FactoryCraft.ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, FactoryCraft.ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FactoryCraft.ID);
    public static final Map<FactoryKind, DeferredBlock<FactoryBlock>> FACTORY = new EnumMap<>(FactoryKind.class);
    public static final Map<factorycraft.fluids.FluidKind,DeferredBlock<Block>> FLUID_BLOCKS=new EnumMap<>(factorycraft.fluids.FluidKind.class);
    static {
        for(var kind:factorycraft.fluids.FluidKind.values()) {
            DeferredBlock<Block> block=BLOCKS.register(kind.id,()->{
                var properties=BlockBehaviour.Properties.of().strength(2.5f).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
                return kind==factorycraft.fluids.FluidKind.PIPE ? new factorycraft.blocks.FluidPipeBlock(properties):new factorycraft.blocks.FluidDeviceBlock(kind,properties);
            });
            FLUID_BLOCKS.put(kind,block);ITEMS.register(kind.id,()->kind==factorycraft.fluids.FluidKind.PIPE ? new factorycraft.items.FluidBlockItem(block.get(),new Item.Properties()):new factorycraft.items.MachineItem(block.get(),new Item.Properties()));
        }
    }
    public static final Map<factorycraft.thermal.ThermalKind,DeferredBlock<Block>> THERMAL_BLOCKS=new EnumMap<>(factorycraft.thermal.ThermalKind.class);
    static {
        for(var kind:factorycraft.thermal.ThermalKind.values()) {
            var block=BLOCKS.<Block>register(kind.id,()->new factorycraft.blocks.ThermalBlock(kind,BlockBehaviour.Properties.of().strength(3).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
            THERMAL_BLOCKS.put(kind,block);ITEMS.register(kind.id,()->new factorycraft.items.MachineItem(block.get(),new Item.Properties()));
        }
    }
    static {
        for (var kind : FactoryKind.values()) {
            var block = BLOCKS.register(kind.id, () -> new FactoryBlock(kind, BlockBehaviour.Properties.of().strength(2.5f).noOcclusion()
                    .pushReaction(kind.casing() || kind.large() ? net.minecraft.world.level.material.PushReaction.BLOCK:net.minecraft.world.level.material.PushReaction.NORMAL)));
            FACTORY.put(kind, block);
            ITEMS.register(kind.id, () -> kind.large()
                    ? new factorycraft.items.MachineItem(block.get(),new Item.Properties())
                    : new factorycraft.items.FactoryBlockItem(block.get(),new Item.Properties()));
        }
    }
    public static final DeferredBlock<factorycraft.blocks.OilDepositBlock> OIL_DEPOSIT=BLOCKS.register("oil_deposit",()->new factorycraft.blocks.OilDepositBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<OilDepositBlockEntity>> OIL_DEPOSIT_ENTITY=ENTITIES.register("oil_deposit",()->BlockEntityType.Builder.of(OilDepositBlockEntity::new,OIL_DEPOSIT.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<factorycraft.petroleum.PetroleumMenu>> PETROLEUM_MENU=MENUS.register("petroleum",()->IMenuTypeExtension.create(factorycraft.petroleum.PetroleumMenu::new));
    public static final DeferredHolder<RecipeType<?>,RecipeType<factorycraft.recipes.ChemicalRecipe>> CHEMISTRY=RECIPE_TYPES.register("chemistry",()->new RecipeType<>() { public String toString() { return "factorycraft:chemistry"; } });
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<factorycraft.recipes.ChemicalRecipe>> CHEMISTRY_SERIALIZER=SERIALIZERS.register("chemistry",factorycraft.recipes.ChemicalRecipe.Serializer::new);
    public static final Map<factorycraft.resources.ResourceKind,DeferredBlock<factorycraft.blocks.ResourceDepositBlock>> RESOURCES=new EnumMap<>(factorycraft.resources.ResourceKind.class);
    static {
        for(var kind:factorycraft.resources.ResourceKind.values())RESOURCES.put(kind,BLOCKS.register(kind.id,()->new factorycraft.blocks.ResourceDepositBlock(kind,BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))));
    }
    public static final DeferredBlock<factorycraft.blocks.ResourceDepositBlock> IRON_ORE=RESOURCES.get(factorycraft.resources.ResourceKind.IRON),COPPER_ORE=RESOURCES.get(factorycraft.resources.ResourceKind.COPPER);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ResourceDepositBlockEntity>> RESOURCE_DEPOSIT_ENTITY=ENTITIES.register("resource_deposit",()->BlockEntityType.Builder.of(ResourceDepositBlockEntity::new,RESOURCES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<net.minecraft.world.level.levelgen.feature.Feature<?>,factorycraft.worldgen.OilDepositFeature> RESOURCE_PATCH_FEATURE=FEATURES.register("resource_patch",factorycraft.worldgen.OilDepositFeature::new);
    static {
        for (String name : List.of("iron_plate", "copper_plate", "iron_gear", "copper_wire", "electronic_circuit","plastic_bar","sulfur","advanced_circuit"))
            ITEMS.registerSimpleItem(name, new Item.Properties());
    }
    public static final DeferredItem<factorycraft.items.FactoryWrenchItem> WRENCH=ITEMS.register("wrench",()->new factorycraft.items.FactoryWrenchItem(new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeltBlockEntity>> BELT = ENTITIES.register("belt", () -> BlockEntityType.Builder.of(BeltBlockEntity::new, blocks(FactoryKind.BELT, FactoryKind.FAST_BELT, FactoryKind.EXPRESS_BELT, FactoryKind.SPLITTER)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InserterBlockEntity>> INSERTER = ENTITIES.register("inserter", () -> BlockEntityType.Builder.of(InserterBlockEntity::new, blocks(FactoryKind.INSERTER)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>> MACHINE = ENTITIES.register("machine", () -> BlockEntityType.Builder.of(MachineBlockEntity::new, blocks(FactoryKind.DRILL, FactoryKind.FURNACE, FactoryKind.ASSEMBLER, FactoryKind.GENERATOR, FactoryKind.INDUSTRIAL_FURNACE,FactoryKind.PUMPJACK,FactoryKind.REFINERY,FactoryKind.CHEMICAL_PLANT)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PoleBlockEntity>> POLE = ENTITIES.register("pole", () -> BlockEntityType.Builder.of(PoleBlockEntity::new, blocks(FactoryKind.POLE)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CasingBlockEntity>> CASING = ENTITIES.register("casing", () -> BlockEntityType.Builder.of(CasingBlockEntity::new,blocks(FactoryKind.CASING,FactoryKind.REFINERY_CASING)).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<FactoryMenu>> MENU = MENUS.register("machine", () -> IMenuTypeExtension.create(FactoryMenu::new));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<FluidBlockEntity>> FLUID=ENTITIES.register("fluid",()->BlockEntityType.Builder.of(FluidBlockEntity::new,FLUID_BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<factorycraft.fluids.FluidMenu>> FLUID_MENU=MENUS.register("fluid",()->IMenuTypeExtension.create(factorycraft.fluids.FluidMenu::new));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ThermalBlockEntity>> THERMAL=ENTITIES.register("thermal",()->BlockEntityType.Builder.of(ThermalBlockEntity::new,THERMAL_BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<factorycraft.thermal.ThermalMenu>> THERMAL_MENU=MENUS.register("thermal",()->IMenuTypeExtension.create(factorycraft.thermal.ThermalMenu::new));
    public static final DeferredHolder<RecipeType<?>,RecipeType<factorycraft.recipes.BoilingRecipe>> BOILING=RECIPE_TYPES.register("boiling",()->new RecipeType<>() { public String toString() { return "factorycraft:boiling"; } });
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<factorycraft.recipes.BoilingRecipe>> BOILING_SERIALIZER=SERIALIZERS.register("boiling",factorycraft.recipes.BoilingRecipe.Serializer::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<FactoryRecipe>> PROCESSING = RECIPE_TYPES.register("processing", () -> new RecipeType<>() { public String toString() { return "factorycraft:processing"; } });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FactoryRecipe>> PROCESSING_SERIALIZER = SERIALIZERS.register("processing", FactoryRecipe.Serializer::new);
    static {
        TABS.register("factorycraft", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.factorycraft"))
                .icon(() -> new ItemStack(FACTORY.get(FactoryKind.ASSEMBLER).get()))
                .displayItems((p, out) -> ITEMS.getEntries().stream().filter(i->!java.util.List.of(FactoryKind.CASING.id,FactoryKind.REFINERY_CASING.id).contains(i.getId().getPath())).forEach(i -> out.accept(i.get()))).build());
    }
    private static Block[] blocks(FactoryKind... kinds) { return Arrays.stream(kinds).map(k -> FACTORY.get(k).get()).toArray(Block[]::new); }
    public static void register(IEventBus bus) {
        factorycraft.thermal.SteamFluids.register(bus);factorycraft.petroleum.PetroleumFluids.register(bus);
        FEATURES.register(bus);BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus);
        RECIPE_TYPES.register(bus); SERIALIZERS.register(bus); TABS.register(bus);
        bus.addListener(Content::capabilities);
    }
    private static void capabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.FluidHandler.BLOCK,MACHINE.get(),(be,side)->factorycraft.multiblock.AssemblyAccess.fluid(be,be.getBlockPos(),side));
        e.registerBlockEntity(Capabilities.FluidHandler.BLOCK,CASING.get(),(be,side)->be.fluid(side));
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK,THERMAL.get(),(be,side)->be.automation());
        e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,THERMAL.get(),(be,side)->be.generatesElectricity() ? be.energyPort:null);
        e.registerBlockEntity(Capabilities.FluidHandler.BLOCK,THERMAL.get(),(be,side)->be.structurePort(be.getBlockPos(),side));
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BELT.get(), (be, side) -> be.inventory());
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, INSERTER.get(), (be, side) -> be.inventory());
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MACHINE.get(), MachineBlockEntity::automation);
        e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MACHINE.get(), (be, side) -> be.energyPort);
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK,CASING.get(),(be,side)->be.inventory());
        e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,CASING.get(),(be,side)->be.energy);
        e.registerBlockEntity(Capabilities.FluidHandler.BLOCK,FLUID.get(),(be,side)->be.structurePort(be.getBlockPos(),side));
        e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,FLUID.get(),(be,side)->be.kind()==factorycraft.fluids.FluidKind.PUMP ? be.energyPort:null);
    }
    private Content() {}
}
