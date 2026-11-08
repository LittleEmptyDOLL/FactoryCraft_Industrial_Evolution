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
            FLUID_BLOCKS.put(kind,block);ITEMS.register(kind.id,()->new factorycraft.items.FluidBlockItem(block.get(),new Item.Properties()));
        }
    }
    static {
        for (var kind : FactoryKind.values()) {
            var block = BLOCKS.register(kind.id, () -> new FactoryBlock(kind, BlockBehaviour.Properties.of().strength(2.5f).noOcclusion()
                    .pushReaction(kind==FactoryKind.CASING || kind==FactoryKind.INDUSTRIAL_FURNACE ? net.minecraft.world.level.material.PushReaction.BLOCK:net.minecraft.world.level.material.PushReaction.NORMAL)));
            FACTORY.put(kind, block);
            ITEMS.register(kind.id, () -> kind==FactoryKind.INDUSTRIAL_FURNACE
                    ? new factorycraft.items.IndustrialFurnaceItem(block.get(),new Item.Properties())
                    : new factorycraft.items.FactoryBlockItem(block.get(),new Item.Properties()));
        }
    }
    public static final DeferredBlock<Block> IRON_ORE = BLOCKS.registerSimpleBlock("iron_ore", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE));
    public static final DeferredBlock<Block> COPPER_ORE = BLOCKS.registerSimpleBlock("copper_ore", BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_ORE));
    static {
        ITEMS.registerSimpleBlockItem(IRON_ORE);
        ITEMS.registerSimpleBlockItem(COPPER_ORE);
        for (String name : List.of("iron_plate", "copper_plate", "iron_gear", "copper_wire", "electronic_circuit"))
            ITEMS.registerSimpleItem(name, new Item.Properties());
    }
    public static final DeferredItem<factorycraft.items.FactoryWrenchItem> WRENCH=ITEMS.register("wrench",()->new factorycraft.items.FactoryWrenchItem(new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeltBlockEntity>> BELT = ENTITIES.register("belt", () -> BlockEntityType.Builder.of(BeltBlockEntity::new, blocks(FactoryKind.BELT, FactoryKind.FAST_BELT, FactoryKind.EXPRESS_BELT, FactoryKind.SPLITTER)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InserterBlockEntity>> INSERTER = ENTITIES.register("inserter", () -> BlockEntityType.Builder.of(InserterBlockEntity::new, blocks(FactoryKind.INSERTER)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>> MACHINE = ENTITIES.register("machine", () -> BlockEntityType.Builder.of(MachineBlockEntity::new, blocks(FactoryKind.DRILL, FactoryKind.FURNACE, FactoryKind.ASSEMBLER, FactoryKind.GENERATOR, FactoryKind.INDUSTRIAL_FURNACE)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PoleBlockEntity>> POLE = ENTITIES.register("pole", () -> BlockEntityType.Builder.of(PoleBlockEntity::new, blocks(FactoryKind.POLE)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CasingBlockEntity>> CASING = ENTITIES.register("casing", () -> BlockEntityType.Builder.of(CasingBlockEntity::new,blocks(FactoryKind.CASING)).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<FactoryMenu>> MENU = MENUS.register("machine", () -> IMenuTypeExtension.create(FactoryMenu::new));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<FluidBlockEntity>> FLUID=ENTITIES.register("fluid",()->BlockEntityType.Builder.of(FluidBlockEntity::new,FLUID_BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<factorycraft.fluids.FluidMenu>> FLUID_MENU=MENUS.register("fluid",()->IMenuTypeExtension.create(factorycraft.fluids.FluidMenu::new));
    public static final DeferredHolder<RecipeType<?>, RecipeType<FactoryRecipe>> PROCESSING = RECIPE_TYPES.register("processing", () -> new RecipeType<>() { public String toString() { return "factorycraft:processing"; } });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FactoryRecipe>> PROCESSING_SERIALIZER = SERIALIZERS.register("processing", FactoryRecipe.Serializer::new);
    static {
        TABS.register("factorycraft", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.factorycraft"))
                .icon(() -> new ItemStack(FACTORY.get(FactoryKind.ASSEMBLER).get()))
                .displayItems((p, out) -> ITEMS.getEntries().stream().filter(i->!i.getId().getPath().equals(FactoryKind.CASING.id)).forEach(i -> out.accept(i.get()))).build());
    }
    private static Block[] blocks(FactoryKind... kinds) { return Arrays.stream(kinds).map(k -> FACTORY.get(k).get()).toArray(Block[]::new); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus);
        RECIPE_TYPES.register(bus); SERIALIZERS.register(bus); TABS.register(bus);
        bus.addListener(Content::capabilities);
    }
    private static void capabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BELT.get(), (be, side) -> be.inventory());
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, INSERTER.get(), (be, side) -> be.inventory());
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MACHINE.get(), MachineBlockEntity::automation);
        e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MACHINE.get(), (be, side) -> be.energyPort);
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK,CASING.get(),(be,side)->be.inventory());
        e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,CASING.get(),(be,side)->be.energy);
        e.registerBlockEntity(Capabilities.FluidHandler.BLOCK,FLUID.get(),(be,side)->side==null || factorycraft.fluids.FluidBlocks.port(be.getBlockState(),side) ? be.port(side):null);
        e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,FLUID.get(),(be,side)->be.kind()==factorycraft.fluids.FluidKind.PUMP ? be.energyPort:null);
    }
    private Content() {}
}
