package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.research.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/**
 * Finite ore -> burner drills/furnaces -> ammunition/components -> belts/arms -> science -> lab.
 */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class MilitaryV28Chain {
  static ResourceLocation id(String n) {
    return ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, n);
  }

  static ItemStack item(String n, int count) {
    return new ItemStack(BuiltInRegistries.ITEM.get(id(n)), count);
  }

  @GameTest(template = "empty", batch = "combat_v0280_chain", timeoutTicks = 2800)
  public static void finiteOreBecomesMilitaryScienceThroughRealMachinesBeltsArmsAndLaboratory(
      GameTestHelper h) {
    var l = h.getLevel();
    var o = PowerV20GameTests.site(h, 110);
    var chunks = new ArrayList<net.minecraft.world.level.ChunkPos>();
    // Explicit isolated diagnostic tickets only. Production code never loads or pins chunks.
    for (int x = -1; x <= 2; x++)
      for (int z = 0; z <= 2; z++) {
        var cp = new net.minecraft.world.level.ChunkPos(o.offset(x * 16, 0, z * 16));
        chunks.add(cp);
        l.setChunkForced(cp.x, cp.z, true);
        l.getChunk(cp.x, cp.z);
      }
    var machines = new ArrayList<MachineBlockEntity>();
    var arms = new ArrayList<InserterBlockEntity>();
    var ores = new ArrayList<ResourceDepositBlockEntity>();
    java.util.function.BiFunction<BlockPos, FactoryKind, MachineBlockEntity> machine =
        (relative, kind) -> {
          var pos =
              StructurePlacement.fromOrigin(
                  l, o.offset(relative), Content.FACTORY.get(kind).get().defaultBlockState());
          var m = (MachineBlockEntity) l.getBlockEntity(pos);
          machines.add(m);
          return m;
        };
    java.util.function.BiConsumer<BlockPos, Direction> arm =
        (p, d) -> {
          var pos = o.offset(p);
          l.setBlockAndUpdate(
              pos,
              Content.FACTORY
                  .get(FactoryKind.INSERTER)
                  .get()
                  .defaultBlockState()
                  .setValue(FactoryBlock.FACING, d));
          arms.add((InserterBlockEntity) l.getBlockEntity(pos));
        };
    java.util.function.BiConsumer<BlockPos, Direction> belt =
        (p, d) ->
            l.setBlockAndUpdate(
                o.offset(p),
                Content.FACTORY
                    .get(FactoryKind.BELT)
                    .get()
                    .defaultBlockState()
                    .setValue(FactoryBlock.FACING, d));
    var magazine = machine.apply(new BlockPos(0, 0, 19), FactoryKind.ASSEMBLER);
    var piercing = machine.apply(new BlockPos(6, 0, 19), FactoryKind.ASSEMBLER);
    var grenade = machine.apply(new BlockPos(-3, 0, 13), FactoryKind.ASSEMBLER);
    var wall = machine.apply(new BlockPos(15, 0, 24), FactoryKind.ASSEMBLER);
    var science = machine.apply(new BlockPos(22, 0, 19), FactoryKind.ASSEMBLER);
    var furnaces = new ArrayList<MachineBlockEntity>();
    int[] reserves = {8, 10};
    int index = 0;
    for (int z : new int[] {19, 13}) {
      var f = machine.apply(new BlockPos(-6, 0, z), FactoryKind.STONE_FURNACE);
      furnaces.add(f);
      var drillAt = o.offset(-7, 0, z + 1);
      StructurePlacement.place(
          l,
          drillAt,
          Content.FACTORY
              .get(FactoryKind.BURNER_DRILL)
              .get()
              .defaultBlockState()
              .setValue(FactoryBlock.FACING, Direction.EAST));
      var drill = (MachineBlockEntity) l.getBlockEntity(drillAt);
      machines.add(drill);
      var rp = drill.assembly().profile().center(drillAt, Direction.EAST).below();
      l.setBlockAndUpdate(rp, Content.IRON_ORE.get().defaultBlockState());
      var ore = (ResourceDepositBlockEntity) l.getBlockEntity(rp);
      ore.initializeReserve(reserves[index++]);
      ore.onLoad();
      ores.add(ore);
      drill.items.setStackInSlot(8, new ItemStack(Items.COAL, 2));
      f.items.setStackInSlot(8, new ItemStack(Items.COAL, 1));
    }
    arm.accept(new BlockPos(-4, 0, 20), Direction.EAST);
    belt.accept(new BlockPos(-3, 0, 20), Direction.EAST);
    belt.accept(new BlockPos(-2, 0, 20), Direction.EAST);
    arm.accept(new BlockPos(-1, 0, 20), Direction.EAST);
    arm.accept(new BlockPos(-4, 0, 14), Direction.EAST);
    arm.accept(new BlockPos(3, 0, 20), Direction.EAST);
    belt.accept(new BlockPos(4, 0, 20), Direction.EAST);
    arm.accept(new BlockPos(5, 0, 20), Direction.EAST);
    arm.accept(new BlockPos(9, 0, 20), Direction.EAST);
    for (int x = 10; x <= 18; x++) belt.accept(new BlockPos(x, 0, 20), Direction.EAST);
    arm.accept(new BlockPos(0, 0, 14), Direction.EAST);
    for (int x = 1; x < 18; x++) belt.accept(new BlockPos(x, 0, 14), Direction.EAST);
    belt.accept(new BlockPos(18, 0, 14), Direction.SOUTH);
    for (int z = 15; z < 20; z++) belt.accept(new BlockPos(18, 0, z), Direction.SOUTH);
    arm.accept(new BlockPos(16, 0, 23), Direction.NORTH);
    belt.accept(new BlockPos(16, 0, 22), Direction.NORTH);
    belt.accept(new BlockPos(16, 0, 21), Direction.NORTH);
    arm.accept(new BlockPos(19, 0, 20), Direction.EAST);
    l.setBlockAndUpdate(o.offset(20, 0, 20), Blocks.CHEST.defaultBlockState());
    arm.accept(new BlockPos(21, 0, 20), Direction.EAST);
    arm.accept(new BlockPos(25, 0, 20), Direction.EAST);
    belt.accept(new BlockPos(26, 0, 20), Direction.EAST);
    belt.accept(new BlockPos(27, 0, 20), Direction.EAST);
    arm.accept(new BlockPos(28, 0, 20), Direction.EAST);
    var labAt =
        StructurePlacement.fromOrigin(
            l, o.offset(29, 0, 19), Content.FACTORY.get(FactoryKind.LAB).get().defaultBlockState());
    var lab = (LabBlockEntity) l.getBlockEntity(labAt);
    var scope = ResearchGameTests.personal(h, "finite-military28");
    lab.bind(scope);
    var technology = id("qa/v0280/finite_military");
    var manager = l.getRecipeManager();
    if (manager.byKey(technology).isEmpty()) {
      var recipes = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
      recipes.add(
          new RecipeHolder<>(
              technology,
              new TechnologyRecipe(
                  List.of(), Map.of(SciencePacks.IDS.get(3), 1), 4, 10, false, List.of(), 0, 0)));
      manager.replaceRecipes(recipes);
      ResearchCatalog.invalidate(l);
    }
    var ledger = ResearchData.get(l.getServer()).team(scope);
    ledger.select(ResearchCatalog.get(l).entry(technology).logic());
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(
              magazine.selectRecipe(id("firearm_magazine"))
                  && piercing.selectRecipe(id("piercing_rounds_magazine"))
                  && grenade.selectRecipe(id("grenade"))
                  && wall.selectRecipe(id("stone_wall"))
                  && science.selectRecipe(id("military_science_pack")),
              "explicit military recipes");
          piercing.items.setStackInSlot(1, item("steel_plate", 1));
          piercing.items.setStackInSlot(2, item("copper_plate", 2));
          grenade.items.setStackInSlot(1, new ItemStack(Items.COAL, 20));
          wall.items.setStackInSlot(0, new ItemStack(Items.STONE_BRICKS, 20));
          for (var m : machines) if (!m.kind().burner()) m.power.restore(16000);
          for (var a : arms) a.power.restore(16000);
          lab.power.restore(16000);
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              ledger.level(technology.toString()) == 1,
              "waiting for four genuinely manufactured science packs");
          h.assertTrue(ores.stream().allMatch(r -> r.remaining() == 0), "finite ore not exhausted");
          h.assertTrue(
              magazine.items.getStackInSlot(0).isEmpty()
                  && grenade.items.getStackInSlot(0).isEmpty(),
              "raw plates remain");
          h.assertTrue(
              magazine.power.getEnergyStored() < 16000 && lab.power.getEnergyStored() < 16000,
              "free production/research");
          h.assertTrue(lab.items.getStackInSlot(3).isEmpty(), "science not consumed");
          System.out.println(
              "FACTORYCRAFT_V0280_CHAIN: 18 finite ore -> smelting -> 2 magazines -> 2 piercing"
                  + " magazines + 2 grenades + 4 walls -> 4 military packs -> 4 research units;"
                  + " finite coal/FE consumed");
          for (var cp : chunks) l.setChunkForced(cp.x, cp.z, false);
        });
  }
}
