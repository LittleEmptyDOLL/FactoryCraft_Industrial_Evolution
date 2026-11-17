package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.research.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class ResearchV27GameTests {
  static ResourceLocation id(String name) {
    return ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, name);
  }

  static TechnologyRecipe technology(int ticks) {
    var packs = new LinkedHashMap<ResourceLocation, Integer>();
    for (var id : SciencePacks.IDS) packs.put(id, 1);
    return new TechnologyRecipe(List.of(), packs, 20, ticks, false, List.of(), 0, 0);
  }

  static ResourceLocation install(GameTestHelper h, String key, int ticks) {
    var id = id("qa/v0270/" + key);
    var manager = h.getLevel().getRecipeManager();
    if (manager.byKey(id).isEmpty()) {
      var list = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
      list.add(new RecipeHolder<>(id, technology(ticks)));
      manager.replaceRecipes(list);
      ResearchCatalog.invalidate(h.getLevel());
    }
    return id;
  }

  static LabBlockEntity lab(GameTestHelper h, BlockPos pos, String scope) {
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(FactoryKind.LAB).get().defaultBlockState());
    var lab = (LabBlockEntity) h.getLevel().getBlockEntity(pos);
    lab.bind(scope);
    return lab;
  }

  static void fill(LabBlockEntity lab, int count) {
    for (int i = 0; i < SciencePacks.COUNT; i++) {
      var stack = new ItemStack(BuiltInRegistries.ITEM.get(SciencePacks.IDS.get(i)), count);
      stack.set(DataComponents.CUSTOM_NAME, Component.literal("Exact science " + i));
      lab.items.setStackInSlot(i, stack);
    }
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 100)
  public static void twelvePackJsonAndStreamAreBoundedAndTwoModulesHaveSeparateSlots(
      GameTestHelper h) {
    var recipe = technology(10);
    var ops =
        net.minecraft.resources.RegistryOps.create(
            com.mojang.serialization.JsonOps.INSTANCE, h.getLevel().registryAccess());
    var json = TechnologyRecipe.CODEC.codec().encodeStart(ops, recipe).getOrThrow();
    h.assertTrue(
        TechnologyRecipe.CODEC.codec().parse(ops, json).getOrThrow().packs().size()
            == SciencePacks.COUNT,
        "twelve-pack codec");
    var buf =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      var codec = Content.RESEARCH_SERIALIZER.get().streamCodec();
      codec.encode(buf, recipe);
      h.assertTrue(codec.decode(buf).packs().equals(recipe.packs()), "twelve-pack stream");
    } finally {
      buf.release();
    }
    var initial = new LinkedHashMap<ResourceLocation, Integer>();
    for (var pack : SciencePacks.IDS.subList(0, SciencePacks.INITIAL_COUNT)) initial.put(pack, 1);
    var seven = new TechnologyRecipe(List.of(), initial, 1, 10, false, List.of(), 0, 0);
    h.assertTrue(
        TechnologyRecipe.CODEC
                .codec()
                .parse(ops, TechnologyRecipe.CODEC.codec().encodeStart(ops, seven).getOrThrow())
                .getOrThrow()
                .packs()
                .size()
            == 7,
        "initial seven-pack recipe lost");
    var bad = new HashMap<>(recipe.packs());
    bad.put(id("fake_science"), 1);
    try {
      new TechnologyRecipe(List.of(), bad, 1, 1, false, List.of(), 0, 0);
      h.fail("thirteenth pack accepted");
    } catch (IllegalArgumentException expected) {
    }
    var lab =
        lab(h, h.absolutePos(new BlockPos(4, 2, 4)), ResearchGameTests.personal(h, "slots27"));
    var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    var menu = new ResearchMenu(1, player.getInventory(), lab);
    h.assertTrue(
        lab.items.getSlots() == SciencePacks.COUNT
            && lab.modules.getSlots() == 2
            && menu.slots.size() == SciencePacks.COUNT + 2 + 36,
        "physical/menu slot bounds");
    h.assertTrue(
        lab.modules.getSlotLimit(0) == 1 && !lab.modules.isItemValid(0, new ItemStack(Items.DIRT)),
        "module validation");
    for (int slot = 0; slot < SciencePacks.COUNT; slot++)
      for (int item = 0; item < SciencePacks.COUNT; item++)
        h.assertTrue(
            lab.items.isItemValid(
                    slot, new ItemStack(BuiltInRegistries.ITEM.get(SciencePacks.IDS.get(item))))
                == (slot == item),
            "individual science filter");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 460)
  public static void realInserterChainsTwelveComponentExactPacksThroughPhysicalLabParts(
      GameTestHelper h) {
    String scope = ResearchGameTests.personal(h, "chain27");
    var a = lab(h, h.absolutePos(new BlockPos(4, 2, 4)), scope);
    var b = lab(h, h.absolutePos(new BlockPos(4, 2, 8)), scope);
    var armPos = h.absolutePos(new BlockPos(4, 2, 7));
    h.getLevel()
        .setBlockAndUpdate(
            armPos,
            Content.FACTORY
                .get(FactoryKind.INSERTER)
                .get()
                .defaultBlockState()
                .setValue(FactoryBlock.FACING, Direction.SOUTH));
    h.runAfterDelay(
        5,
        () -> {
          fill(a, 1);
          ((InserterBlockEntity) h.getLevel().getBlockEntity(armPos)).power.restore(16000);
        });
    h.runAfterDelay(
        420,
        () -> {
          for (int i = 0; i < SciencePacks.COUNT; i++) {
            h.assertTrue(a.items.getStackInSlot(i).isEmpty(), "source science remained");
            var s = b.items.getStackInSlot(i);
            h.assertTrue(
                s.getCount() == 1
                    && Component.literal("Exact science " + i)
                        .equals(s.get(DataComponents.CUSTOM_NAME)),
                "science count/components changed");
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 100)
  public static void allModuleTiersUseRealProfilesOnMachineBeaconAndLaboratory(GameTestHelper h) {
    var origin = h.absolutePos(new BlockPos(4, 2, 4));
    var lab = lab(h, origin, ResearchGameTests.personal(h, "profiles27"));
    for (String family : List.of("speed", "efficiency", "productivity"))
      for (int tier = 1; tier <= 3; tier++) {
        var item =
            BuiltInRegistries.ITEM.get(id(family + "_module" + (tier == 1 ? "" : "_" + tier)));
        h.assertTrue(item instanceof factorycraft.modules.ModuleItem, "missing module registry");
        var module = (factorycraft.modules.ModuleItem) item;
        lab.modules.setStackInSlot(0, new ItemStack(item));
        var effect = lab.effects();
        h.assertTrue(
            effect.speed() == 10000 + module.profile.speed()
                && effect.energy() == 10000 + module.profile.energy()
                && effect.productivity() == module.profile.productivity(),
            "tier effect mismatch");
      }
    for (var kind :
        List.of(
            FactoryKind.ASSEMBLER,
            FactoryKind.ADVANCED_ASSEMBLER,
            FactoryKind.ASSEMBLER_3,
            FactoryKind.DRILL,
            FactoryKind.FURNACE,
            FactoryKind.PUMPJACK,
            FactoryKind.REFINERY,
            FactoryKind.CHEMICAL_PLANT,
            FactoryKind.BURNER_DRILL,
            FactoryKind.STONE_FURNACE,
            FactoryKind.STEEL_FURNACE)) {
      var machine =
          new MachineBlockEntity(
              BlockPos.ZERO, Content.FACTORY.get(kind).get().defaultBlockState());
      h.assertTrue(machine.modules.items.getSlots() == kind.moduleSlots(), "module slots " + kind);
      if (kind.moduleSlots() > 0)
        for (String family : List.of("speed", "efficiency", "productivity"))
          for (int tier = 1; tier <= 3; tier++) {
            var item =
                (factorycraft.modules.ModuleItem)
                    BuiltInRegistries.ITEM.get(
                        id(family + "_module" + (tier == 1 ? "" : "_" + tier)));
            machine.modules.items.setStackInSlot(0, new ItemStack(item));
            var effect = machine.modules.effects();
            h.assertTrue(
                effect.speed() == 10000 + item.profile.speed()
                    && effect.energy() == 10000 + item.profile.energy()
                    && effect.productivity() == item.profile.productivity(),
                "machine tier effect " + kind);
          }
    }
    var beacon =
        new BeaconBlockEntity(
            BlockPos.ZERO, Content.FACTORY.get(FactoryKind.BEACON).get().defaultBlockState());
    for (String family : List.of("speed", "efficiency", "productivity"))
      for (int tier = 1; tier <= 3; tier++) {
        var item =
            BuiltInRegistries.ITEM.get(id(family + "_module" + (tier == 1 ? "" : "_" + tier)));
        h.assertTrue(
            beacon.modules.isItemValid(0, new ItemStack(item)) != family.equals("productivity"),
            "beacon permitted module tiers");
      }
    lab.modules.setStackInSlot(0, new ItemStack(Content.EFFICIENCY_MODULE_3.get()));
    lab.modules.setStackInSlot(1, new ItemStack(Content.EFFICIENCY_MODULE_3.get()));
    h.assertTrue(lab.effects().energy() == 2000, "lab efficiency floor");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 100)
  public static void twoProductivityModulesPayExactFeAndSevenPackLedger(GameTestHelper h) {
    var techId = install(h, "ledger", 10);
    String scope = ResearchGameTests.personal(h, "ledger27");
    var lab = lab(h, h.absolutePos(new BlockPos(4, 2, 4)), scope);
    h.runAfterDelay(
        5,
        () -> {
          fill(lab, 20);
          lab.modules.setStackInSlot(0, new ItemStack(Content.PRODUCTIVITY_MODULE_3.get()));
          lab.modules.setStackInSlot(1, new ItemStack(Content.PRODUCTIVITY_MODULE_3.get()));
          lab.power.restore(16000);
          var ledger = ResearchData.get(h.getLevel().getServer()).team(scope);
          var tech = ResearchCatalog.get(h.getLevel()).entry(techId).logic();
          ledger.select(tech);
          // Exactly 100 paid ticks: module speed .7, seven base units + one bonus, 20.8 FE/t.
          for (int tick = 0; tick < 100; tick++) lab.tick();
          h.assertTrue(ledger.units(tech) == 8, "productive scientific units");
          for (int slot = 0; slot < SciencePacks.COUNT; slot++)
            h.assertTrue(lab.items.getStackInSlot(slot).getCount() == 13, "bonus debited science");
          h.assertTrue(lab.power.getEnergyStored() == 13920, "fractional active FE ledger");
          h.assertTrue(lab.productivityCredit() == 40_000_000L, "productive credit residue");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 100)
  public static void originalModuleRecipesAndLateScienceLocksCannotBeBypassed(GameTestHelper h) {
    var catalog = ResearchCatalog.get(h.getLevel());
    String scope = ResearchGameTests.personal(h, "gates27");
    for (String name :
        List.of(
            "automation_3",
            "effect_transmission",
            "speed_modules_2",
            "efficiency_modules_2",
            "productivity_modules_2",
            "speed_modules_3",
            "efficiency_modules_3",
            "productivity_modules_3",
            "lab_research_speed_5",
            "lab_research_speed_6"))
      h.assertTrue(catalog.disabled(id(name)), "future dependency silently available: " + name);
    for (String family : List.of("speed", "efficiency", "productivity"))
      for (int tier = 1; tier <= 3; tier++) {
        String name = family + "_module" + (tier == 1 ? "" : "_" + tier);
        var r =
            (factorycraft.recipes.FactoryRecipe)
                h.getLevel().getRecipeManager().byKey(id(name)).orElseThrow().value();
        h.assertTrue(
            r.categories().equals(List.of("crafting", "electromagnetics")),
            "Space Age categories lost");
        h.assertTrue(
            r.ticks() == new int[] {300, 600, 1200}[tier - 1]
                && !catalog.allowed(scope, id(name), h.getLevel()),
            "module time/gate");
        if (tier > 1)
          h.assertTrue(
              r.inputs().getFirst().count() == 4, "module upgrade requires four preceding modules");
      }
    h.assertTrue(
        h.getLevel().getRecipeManager().byKey(id("lab_efficiency")).isEmpty(),
        "invented lab technology retained");
    // 0.28 adds the genuine military chain; the other late sciences remain unavailable.
    for (int i = 4; i < SciencePacks.COUNT; i++) {
      var pack = SciencePacks.IDS.get(i);
      for (var recipe : h.getLevel().getRecipeManager().getRecipes())
        h.assertTrue(
            !recipe
                .value()
                .getResultItem(h.getLevel().registryAccess())
                .is(BuiltInRegistries.ITEM.get(pack)),
            "fake late science recipe");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 100)
  public static void repeatLevelsKeepPaidOvershootAndExactScienceDebit(GameTestHelper h) {
    var techId = id("qa/v0270/repeat");
    var manager = h.getLevel().getRecipeManager();
    if (manager.byKey(techId).isEmpty()) {
      var recipe =
          new TechnologyRecipe(
              List.of(), Map.of(SciencePacks.IDS.getFirst(), 1), 1, 1, true, List.of(), 0, 0);
      var recipes = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
      recipes.add(new RecipeHolder<>(techId, recipe));
      manager.replaceRecipes(recipes);
      ResearchCatalog.invalidate(h.getLevel());
    }
    String scope = ResearchGameTests.personal(h, "repeat27");
    var lab = lab(h, h.absolutePos(new BlockPos(4, 2, 4)), scope);
    h.runAfterDelay(
        5,
        () -> {
          lab.items.setStackInSlot(0, new ItemStack(Content.SCIENCE_RED.get(), 64));
          lab.modules.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE_3.get()));
          lab.power.restore(16000);
          var ledger = ResearchData.get(h.getLevel().getServer()).team(scope);
          var tech = ResearchCatalog.get(h.getLevel()).entry(techId).logic();
          ledger.select(tech);
          for (int n = 0; n < 30; n++) lab.tick();
          h.assertTrue(
              ledger.level(techId.toString()) == 9 && ledger.units(tech) == 0,
              "level transition lost paid overshoot");
          h.assertTrue(
              lab.items.getStackInSlot(0).getCount() == 19 && lab.power.getEnergyStored() == 15592,
              "repeat science/FE ledger");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 100)
  public static void paidBeaconUsesLabFootprintAndNeverBroadcastsProductivity(GameTestHelper h) {
    var lab =
        lab(h, h.absolutePos(new BlockPos(4, 2, 4)), ResearchGameTests.personal(h, "beacon27"));
    var pos = h.absolutePos(new BlockPos(9, 2, 4));
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(FactoryKind.BEACON).get().defaultBlockState());
    var beacon = (BeaconBlockEntity) h.getLevel().getBlockEntity(pos);
    h.runAfterDelay(
        5,
        () -> {
          lab.modules.setStackInSlot(0, new ItemStack(Content.PRODUCTIVITY_MODULE_3.get()));
          lab.modules.setStackInSlot(1, new ItemStack(Content.PRODUCTIVITY_MODULE_3.get()));
          beacon.modules.setStackInSlot(0, new ItemStack(Content.SPEED_MODULE_3.get()));
          beacon.modules.setStackInSlot(1, new ItemStack(Content.EFFICIENCY_MODULE_3.get()));
          beacon.power.restore(1000);
          beacon.payForTick();
          var effects = lab.effects();
          h.assertTrue(
              effects.speed() == 14500
                  && effects.energy() == 29000
                  && effects.productivity() == 2000,
              "lab footprint/beacon transmission");
          beacon.power.restore(0);
          beacon.payForTick();
          h.assertTrue(lab.effects().speed() == 7000, "unpaid beacon effect");
          beacon.power.restore(1000);
          beacon.payForTick();
          var part =
              (CasingBlockEntity)
                  h.getLevel().getBlockEntity(beacon.assembly().positions().getLast());
          part.onChunkUnloaded();
          h.assertTrue(
              lab.effects().speed() == 7000 && lab.effects().productivity() == 2000,
              "unloaded beacon effect");
          part.onLoad();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 100)
  public static void countedLabHandcraftDebitsOriginalComponentsWithoutAnAssembler(
      GameTestHelper h) {
    var player =
        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LabCraft27"));
    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    var input =
        new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu, 3, 3);
    // Offset deliberately: trimmed crafting inputs must map back to the physical 3×3 slots.
    input.setItem(4, new ItemStack(BuiltInRegistries.ITEM.get(id("electronic_circuit")), 12));
    input.setItem(5, new ItemStack(BuiltInRegistries.ITEM.get(id("iron_gear")), 13));
    input.setItem(7, new ItemStack(Content.FACTORY.get(FactoryKind.BELT).get(), 6));
    var holder = h.getLevel().getRecipeManager().byKey(id("craft_lab")).orElseThrow();
    var recipe = (factorycraft.recipes.CountedCraftingRecipe) holder.value();
    h.assertTrue(
        recipe.matches(input.asCraftInput(), h.getLevel()), "canonical lab inputs not recognized");
    var result = new net.minecraft.world.inventory.ResultContainer();
    result.setRecipeUsed(holder);
    result.setItem(0, recipe.assemble(input.asCraftInput(), h.getLevel().registryAccess()));
    var slot = new net.minecraft.world.inventory.ResultSlot(player, input, result, 0, 0, 0);
    var crafted = slot.remove(1);
    slot.onTake(player, crafted);
    h.assertTrue(
        crafted.is(Content.FACTORY.get(FactoryKind.LAB).get().asItem()) && crafted.getCount() == 1,
        "real lab result missing");
    h.assertTrue(
        input.getItem(4).getCount() == 2
            && input.getItem(5).getCount() == 3
            && input.getItem(7).getCount() == 2,
        "counted craft lost or copied inputs");
    h.assertTrue(
        !recipe.matches(input.asCraftInput(), h.getLevel()),
        "insufficient remaining components matched");
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 100)
  public static void realVanillaResultSlotCannotBypassTeamResearchOnPickup(GameTestHelper h) {
    var player =
        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Research27"));
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    var recipe =
        h.getLevel().getRecipeManager().byKey(id("craft_assembling_machine")).orElseThrow();
    var result = new net.minecraft.world.inventory.ResultContainer();
    result.setRecipeUsed(recipe);
    result.setItem(0, new ItemStack(Content.FACTORY.get(FactoryKind.ASSEMBLER).get()));
    var input =
        new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu, 3, 3);
    var slot = new net.minecraft.world.inventory.ResultSlot(player, input, result, 0, 0, 0);
    h.assertTrue(!slot.mayPickup(player), "vanilla result slot bypassed research");
    ResearchTeams.resolve(player)
        .ifPresent(
            scope -> {
              ResearchTestSupport.grant(h.getLevel(), scope, "automation");
              h.assertTrue(slot.mayPickup(player), "actual unlock kept result blocked");
            });
    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    h.assertTrue(slot.mayPickup(player), "creative diagnostic exception blocked");
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 100)
  public static void zeroPowerPreservesPaidFractionalEnergyAndComponents(GameTestHelper h) {
    var techId = install(h, "fractional", 10);
    String scope = ResearchGameTests.personal(h, "fraction27");
    var lab = lab(h, h.absolutePos(new BlockPos(4, 2, 4)), scope);
    h.runAfterDelay(
        5,
        () -> {
          fill(lab, 1);
          lab.modules.setStackInSlot(0, new ItemStack(Content.EFFICIENCY_MODULE_3.get()));
          lab.modules.setStackInSlot(1, new ItemStack(Content.EFFICIENCY_MODULE_3.get()));
          var ledger = ResearchData.get(h.getLevel().getServer()).team(scope);
          ledger.select(ResearchCatalog.get(h.getLevel()).entry(techId).logic());
          lab.power.restore(1);
          lab.tick();
          for (int n = 0; n < 100; n++) lab.tick();
          h.assertTrue(
              lab.progress() == 1 && lab.power.getEnergyStored() == 0, "free motion without FE");
          lab.power.restore(2);
          lab.tick();
          for (int n = 0; n < 100; n++) lab.tick();
          var saved = lab.saveWithoutMetadata(h.getLevel().registryAccess());
          h.assertTrue(
              lab.progress() == 2
                  && lab.power.getEnergyStored() == 0
                  && saved.getInt("EnergyRemainder") == 2000,
              "paid FE residue lost");
          for (int slot = 0; slot < 7; slot++)
            h.assertTrue(
                lab.items.getStackInSlot(slot).getCount() == 1, "paused lab consumed science");
          var port = lab.inventory();
          var part =
              (CasingBlockEntity) h.getLevel().getBlockEntity(lab.assembly().positions().getLast());
          part.onChunkUnloaded();
          h.assertTrue(port.extractItem(0, 1, false).isEmpty(), "stale unloaded part capability");
          part.onLoad();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "research_v0270", timeoutTicks = 100)
  public static void currentFormatLaboratoryKeepsModulesComponentsPartialWorkAndBonusAcrossJvms(
      GameTestHelper h) {
    var first = install(h, "disk_a", 10);
    var second = install(h, "disk_b", 20);
    var pos = new BlockPos(1453, 71, 1453);
    for (var cell : MachineProfile.LAB.cells(pos, Direction.NORTH)) h.getLevel().getChunkAt(cell);
    String scope =
        h.getLevel().getBlockEntity(pos) instanceof LabBlockEntity existing
            ? existing.scope()
            : ResearchGameTests.personal(h, "disk27-stable");
    var data = ResearchData.get(h.getLevel().getServer());
    var ledger = data.team(scope);
    if (h.getLevel().getBlockEntity(pos) == null) {
      var lab = lab(h, pos, scope);
      for (var cell : lab.assembly().positions()) h.getLevel().getBlockEntity(cell).onLoad();
      lab.onLoad();
      fill(lab, 4);
      lab.modules.setStackInSlot(0, new ItemStack(Content.PRODUCTIVITY_MODULE_3.get()));
      lab.modules.setStackInSlot(1, new ItemStack(Content.PRODUCTIVITY_MODULE_3.get()));
      lab.power.restore(16000);
      ledger.select(ResearchCatalog.get(h.getLevel()).entry(first).logic());
      for (int i = 0; i < 5; i++) lab.tick();
      ledger.select(ResearchCatalog.get(h.getLevel()).entry(second).logic());
      for (int i = 0; i < 7; i++) lab.tick();
      data.setDirty();
      System.out.println(
          "FACTORYCRAFT_V0270_DISK: SEEDED twelve component packs, two modules, two partial"
              + " definitions, FE and bonus");
    } else {
      var lab = (LabBlockEntity) h.getLevel().getBlockEntity(pos);
      var tag = lab.saveWithoutMetadata(h.getLevel().registryAccess());
      h.assertTrue(
          lab.scope().equals(scope) && lab.power.getEnergyStored() == 15751 && lab.progress() == 4,
          "lab saved FE/work/team");
      h.assertTrue(
          tag.getList("PartialWork", 10).size() == 2 && lab.productivityCredit() == 11_900_000L,
          "lab saved partial/bonus");
      var savedWork =
          tag.getList("PartialWork", 10).stream()
              .map(t -> ((CompoundTag) t).getLong("Work"))
              .sorted()
              .toList();
      h.assertTrue(
          savedWork.equals(List.of(350_000_000L, 490_000_000L))
              && tag.getInt("EnergyRemainder") == 6000,
          "per-definition work or FE residue changed");
      for (int i = 0; i < SciencePacks.COUNT; i++)
        h.assertTrue(
            lab.items.getStackInSlot(i).getCount() == 4
                && Component.literal("Exact science " + i)
                    .equals(lab.items.getStackInSlot(i).get(DataComponents.CUSTOM_NAME)),
            "saved science components");
      for (int i = 0; i < 2; i++)
        h.assertTrue(
            lab.modules.getStackInSlot(i).is(Content.PRODUCTIVITY_MODULE_3.get())
                && lab.modules.getStackInSlot(i).getCount() == 1,
            "saved modules");
      h.assertTrue(ledger.selected().equals(second.toString()), "saved team selection");
      System.out.println(
          "FACTORYCRAFT_V0270_DISK: VERIFIED twelve component packs, two modules, two partial"
              + " definitions, FE and bonus");
    }
    for (var cell : MachineProfile.LAB.cells(pos, Direction.NORTH)) {
      var be = h.getLevel().getBlockEntity(cell);
      if (be != null) be.onChunkUnloaded();
      h.getLevel().getChunkAt(cell).setUnsaved(true);
    }
    h.succeed();
  }
}
