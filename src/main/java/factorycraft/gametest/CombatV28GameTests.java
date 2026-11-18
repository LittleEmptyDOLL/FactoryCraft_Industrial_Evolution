package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blocks.*;
import factorycraft.combat.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.research.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class CombatV28GameTests {
  static ItemStack item(String name, int count) {
    return new ItemStack(
        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, name)),
        count);
  }

  static RecipeHolder<?> recipe(GameTestHelper h, String id) {
    return h.getLevel()
        .getRecipeManager()
        .byKey(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, id))
        .orElseThrow();
  }

  static TurretBlockEntity turret(GameTestHelper h, FactoryKind kind, BlockPos pos) {
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(kind).get().defaultBlockState());
    var be = (TurretBlockEntity) h.getLevel().getBlockEntity(pos);
    for (var p : be.assembly().positions()) h.getLevel().getBlockEntity(p).onLoad();
    be.onLoad();
    return be;
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 120)
  public static void finiteSteamAndRealPoleGridPowerLaserAndUnloadRevokesItsPort(GameTestHelper h) {
    var l = h.getLevel();
    var poleAt = h.absolutePos(new BlockPos(5, 2, 5));
    StructurePlacement.place(
        l, poleAt, Content.FACTORY.get(FactoryKind.MEDIUM_POLE).get().defaultBlockState());
    var pole = (factorycraft.blockentity.PoleBlockEntity) l.getBlockEntity(poleAt);
    for (var cell : pole.assembly().positions()) l.getBlockEntity(cell).onLoad();
    var source =
        FinitePowerFixture.place(l, h.absolutePos(new BlockPos(3, 2, 7)), Direction.NORTH, 4000);
    var be = turret(h, FactoryKind.LASER_TURRET, h.absolutePos(new BlockPos(8, 2, 5)));
    h.setBlock(new BlockPos(8, 1, 12), net.minecraft.world.level.block.Blocks.STONE);
    var target = h.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 12));
    target.setNoAi(true);
    target.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    h.runAfterDelay(
        65,
        () -> {
          h.assertTrue(
              source.steam.amount() < 4000
                  && be.paidShots > 0
                  && target.getHealth() < target.getMaxHealth(),
              "finite steam -> native grid -> paid laser failed");
          int held = be.power.getEnergyStored();
          be.onChunkUnloaded();
          h.assertTrue(
              be.receiveGridEnergy(100, false) == 0 && be.power.getEnergyStored() == held,
              "unloaded laser accepted phantom grid FE");
          be.onLoad();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void sourceArmorResistsPhysicalButNotLaserDamage(GameTestHelper h) {
    var target = h.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
    target.setNoAi(true);
    // CI runs in daylight. A non-armored, non-damageable head covering prevents native
    // Zombie.aiStep sunlight ignition without adding armor or changing the damage under test.
    target.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
    target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, item("heavy_armor", 1));
    target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
    target
        .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)
        .setBaseValue(0);
    h.runAfterDelay(
        4,
        () -> {
          h.assertTrue(
              Math.abs(
                      target.getAttributeValue(ArmorAttributes.flat(ArmorAttributes.Kind.PHYSICAL))
                          - 6)
                  < .001,
              "heavy chest armor did not equip flat resistance");
          h.assertTrue(
              Math.abs(
                      target.getAttributeValue(
                              ArmorAttributes.percent(ArmorAttributes.Kind.PHYSICAL))
                          - .30)
                  < .001,
              "heavy chest armor did not equip percent resistance");
          h.assertTrue(
              !target.isOnFire() && target.getHealth() == 20 && target.getArmorValue() == 0,
              "armor fixture took unrelated native damage before the controlled hit");
          Combat.damage(h.getLevel(), target, null, null, "", "bullet", 10, false);
          h.assertTrue(
              Math.abs(target.getHealth() - 19.44) < .001,
              "source physical flat6/30% resistance; actual="
                  + target.getHealth()
                  + " fire="
                  + target.getRemainingFireTicks()
                  + " nativeArmor="
                  + target.getArmorValue());
          Combat.damage(h.getLevel(), target, null, null, "", "laser", 10, false);
          h.assertTrue(
              Math.abs(target.getHealth() - 17.44) < .001,
              "laser was incorrectly reduced by physical armor");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void armorResistanceAttributesAppearOnItemsAndExpireOnUnequip(GameTestHelper h) {
    var target = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
    target.setNoAi(true);
    var heavy = item("heavy_armor", 1);
    var modifiers = heavy.getAttributeModifiers().modifiers();
    h.assertTrue(
        modifiers.stream()
            .anyMatch(
                e ->
                    e.attribute().equals(ArmorAttributes.flat(ArmorAttributes.Kind.EXPLOSION))
                        && e.modifier().amount() == 20),
        "the ordinary item attribute component does not expose Factorio protection");
    target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, heavy);
    h.runAfterDelay(
        4,
        () -> {
          h.assertTrue(
              Math.abs(
                      target.getAttributeValue(ArmorAttributes.percent(ArmorAttributes.Kind.ACID))
                          - .40)
                  < .001,
              "heavy armor lacks acid resistance");
          target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
        });
    h.runAfterDelay(
        9,
        () -> {
          h.assertTrue(
              target.getAttributeValue(ArmorAttributes.percent(ArmorAttributes.Kind.ACID)) == 0,
              "armor resistance remained after removing chest item");
          target.setItemSlot(
              net.minecraft.world.entity.EquipmentSlot.CHEST, item("light_armor", 1));
        });
    h.runAfterDelay(
        14,
        () -> {
          h.assertTrue(
              Math.abs(
                      target.getAttributeValue(ArmorAttributes.percent(ArmorAttributes.Kind.ACID))
                          - .20)
                  < .001,
              "light armor attributes did not replace heavy armor");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 140)
  public static void mineArmsOnceConsumesItsBlockAndStunsWithoutHarmingAnimals(GameTestHelper h) {
    var pos = new BlockPos(5, 2, 5);
    h.setBlock(pos, Content.LAND_MINE.get());
    h.setBlock(new BlockPos(5, 1, 6), net.minecraft.world.level.block.Blocks.STONE);
    h.setBlock(new BlockPos(4, 1, 6), net.minecraft.world.level.block.Blocks.STONE);
    var target = h.spawn(EntityType.RAVAGER, new BlockPos(5, 2, 6));
    target.setNoAi(true);
    var pig = h.spawn(EntityType.PIG, new BlockPos(4, 2, 6));
    pig.setNoAi(true);
    h.runAfterDelay(
        30,
        () ->
            h.assertTrue(
                h.getBlockState(pos).is(Content.LAND_MINE.get()) && target.getHealth() == 100,
                "mine attacked before arming"));
    h.runAfterDelay(
        55,
        () -> {
          h.assertTrue(
              h.getBlockState(pos).isAir() && Math.abs(target.getHealth() - 50) < .001,
              "mine must consume one block before one 250-source-damage blast");
          h.assertTrue(
              target.getPersistentData().contains("FactoryCombatStun")
                  && pig.getHealth() == pig.getMaxHealth(),
              "mine stun/friendly policy");
        });
    h.runAfterDelay(
        115,
        () -> {
          h.assertTrue(
              !target.getPersistentData().contains("FactoryCombatStun") && target.isNoAi(),
              "restore original AI after stun");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void grenadeDebitsOneItemBeforeProjectileAndRejectsReplay(GameTestHelper h) {
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setPos(h.absolutePos(new BlockPos(5, 2, 2)).getCenter());
    var stack = item("grenade", 3);
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
    var grenade = (GrenadeItem) stack.getItem();
    grenade.use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
    grenade.use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
    var shots =
        h.getLevel().getEntitiesOfClass(CombatProjectile.class, player.getBoundingBox().inflate(2));
    h.assertTrue(
        stack.getCount() == 2
            && shots.size() == 1
            && player.getPersistentData().getLong("FactoryGrenadeDue") > h.getLevel().getGameTime(),
        "grenade debit/replay");
    var n = new net.minecraft.nbt.CompoundTag();
    shots.getFirst().saveWithoutId(n);
    h.assertTrue(n.getBoolean("Paid") && n.getInt("Mode") == 1, "paid projectile persistence");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void turretCoverBlocksBothDamageAndAmmoUntilLineOfSightReturns(GameTestHelper h) {
    var be = turret(h, FactoryKind.GUN_TURRET, h.absolutePos(new BlockPos(5, 2, 5)));
    be.items.setStackInSlot(0, item("firearm_magazine", 1));
    h.setBlock(new BlockPos(5, 1, 11), net.minecraft.world.level.block.Blocks.STONE);
    var target = h.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 11));
    target.setNoAi(true);
    target.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    for (int x = 3; x <= 7; x++)
      for (int y = 1; y <= 4; y++)
        h.setBlock(new BlockPos(x, y, 8), net.minecraft.world.level.block.Blocks.STONE);
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              be.paidShots == 0 && target.getHealth() == target.getMaxHealth(),
              "turret shot through cover");
          for (int x = 3; x <= 7; x++)
            for (int y = 1; y <= 4; y++)
              h.setBlock(new BlockPos(x, y, 8), net.minecraft.world.level.block.Blocks.AIR);
        });
    h.runAfterDelay(
        70,
        () -> {
          h.assertTrue(
              be.paidShots > 0 && target.getHealth() < target.getMaxHealth(),
              "turret did not resume after cover removal");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void distinctFlameBuffersKeepComponentsInIndependentJvms(GameTestHelper h) {
    var l = h.getLevel();
    var at = new BlockPos(1536, 71, 1551);
    for (var cell : MachineProfile.FLAME_TURRET.cells(at, Direction.NORTH)) l.getChunkAt(cell);
    boolean fresh = l.getBlockEntity(at) == null;
    var be =
        fresh ? turret(h, FactoryKind.FLAME_TURRET, at) : (TurretBlockEntity) l.getBlockEntity(at);
    if (fresh) {
      var privateOil = factorycraft.petroleum.PetroleumFluids.LIGHT.stack(251);
      privateOil.set(DataComponents.CUSTOM_NAME, Component.literal("private v0280"));
      var pipe = factorycraft.petroleum.PetroleumFluids.LIGHT.stack(749);
      pipe.set(DataComponents.CUSTOM_NAME, Component.literal("pipe v0280"));
      be.oil.restore(new factorycraft.fluids.FluidKey(privateOil), 251);
      be.pipeOil.restore(new factorycraft.fluids.FluidKey(pipe), 749);
      be.sync();
    }
    h.assertTrue(
        be.oil.amount() == 251
            && be.pipeOil.amount() == 749
            && Component.literal("private v0280")
                .equals(be.oil.key().stack(1).get(DataComponents.CUSTOM_NAME))
            && Component.literal("pipe v0280")
                .equals(be.pipeOil.key().stack(1).get(DataComponents.CUSTOM_NAME)),
        "independent private/pass-through ownership");
    be.onChunkUnloaded();
    System.out.println(
        "FACTORYCRAFT_V0280_BUFFER_DISK: "
            + (fresh ? "SEEDED" : "VERIFIED")
            + " 251 private + 749 distinct typed mB");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void controllerAndPartOpenOneCanonicalTurretMenu(GameTestHelper h) {
    var be = turret(h, FactoryKind.GUN_TURRET, h.absolutePos(new BlockPos(5, 2, 5)));
    h.runAfterDelay(
        5,
        () -> {
          // Only network delivery is stubbed; actual block dispatch, provider and range guards run.
          var player =
              new net.neoforged.neoforge.common.util.FakePlayer(
                  h.getLevel(),
                  new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "TurretMenuQA")) {
                @Override
                public java.util.OptionalInt openMenu(
                    net.minecraft.world.MenuProvider provider,
                    java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf>
                        extra) {
                  containerMenu = provider.createMenu(1, getInventory(), this);
                  return java.util.OptionalInt.of(1);
                }
              };
          for (var cell : be.assembly().positions()) {
            player.setPos(cell.getCenter().add(0, 0, -2));
            var hit =
                new net.minecraft.world.phys.BlockHitResult(
                    cell.getCenter(), Direction.NORTH, cell, false);
            h.getLevel().getBlockState(cell).useWithoutItem(h.getLevel(), player, hit);
            h.assertTrue(
                player.containerMenu instanceof TurretMenu menu
                    && menu.turret == be
                    && menu.stillValid(player),
                "physical controller/part did not open canonical menu: " + cell);
            player.closeContainer();
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void flamePrivateBufferTransfersExactlyAndRejectsComponentMixing(GameTestHelper h) {
    var be = turret(h, FactoryKind.FLAME_TURRET, h.absolutePos(new BlockPos(5, 2, 5)));
    h.runAfterDelay(
        5,
        () -> {
          var oil =
              new factorycraft.fluids.FluidKey(
                  factorycraft.petroleum.PetroleumFluids.LIGHT.stack(1));
          be.pipeOil.restore(oil, 1000);
          be.tick();
          h.assertTrue(
              be.pipeOil.amount() == 975 && be.oil.amount() == 25, "source 25 mB/t fuel throttle");
          var n = be.saveWithoutMetadata(h.getLevel().registryAccess());
          var copy = new TurretBlockEntity(be.getBlockPos(), be.getBlockState());
          copy.loadWithComponents(n, h.getLevel().registryAccess());
          h.assertTrue(
              copy.pipeOil.amount() == 975 && copy.oil.amount() == 25,
              "two sole-owned buffers save");
          var changed = factorycraft.petroleum.PetroleumFluids.LIGHT.stack(1);
          changed.set(DataComponents.CUSTOM_NAME, Component.literal("incompatible"));
          be.pipeOil.restore(new factorycraft.fluids.FluidKey(changed), 975);
          be.tick();
          h.assertTrue(
              be.pipeOil.amount() == 975 && be.oil.amount() == 25, "components mixed internally");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void piercingShotgunUsesOriginalPelletsAndCannotRefillPartialShells(
      GameTestHelper h) {
    var ammo = item("piercing_shotgun_shell", 1);
    h.assertTrue(
        ammo.getItem() instanceof MagazineItem m
            && m.pellets == 18
            && MagazineItem.damage(ammo) == 12
            && MagazineItem.rounds(ammo) == 10,
        "source piercing shell stats");
    var r = (factorycraft.recipes.FactoryRecipe) recipe(h, "piercing_shotgun_shell").value();
    var partial = item("shotgun_shell", 1);
    MagazineItem.spend(partial);
    h.assertTrue(
        !r.inputs().getFirst().ingredient().test(partial),
        "upgrading partial shell refilled ammunition");
    var research = (TechnologyRecipe) recipe(h, "military_3").value();
    h.assertTrue(
        research
            .unlocks()
            .contains(
                ResourceLocation.fromNamespaceAndPath("factorycraft", "piercing_shotgun_shell")),
        "military 3 unlock");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void partialMagazineAndWeaponKeepExactComponentsAndSingleOwnership(
      GameTestHelper h) {
    var p = h.makeMockPlayer(GameType.SURVIVAL);
    var ammo = item("firearm_magazine", 3);
    ammo.set(DataComponents.CUSTOM_NAME, Component.literal("grade 7"));
    p.getInventory().setItem(0, ammo);
    var gun = item("pistol", 1);
    h.assertTrue(LoadedMagazine.load(gun, p, "bullet"), "load");
    h.assertTrue(
        ammo.getCount() == 2 && MagazineItem.rounds(LoadedMagazine.peek(gun)) == 10,
        "split one full magazine");
    for (int i = 0; i < 7; i++) h.assertTrue(!LoadedMagazine.spend(gun).isEmpty(), "paid shot");
    var copy =
        ItemStack.parse(h.getLevel().registryAccess(), gun.save(h.getLevel().registryAccess()))
            .orElseThrow();
    h.assertTrue(
        MagazineItem.rounds(LoadedMagazine.peek(copy)) == 3
            && Component.literal("grade 7")
                .equals(LoadedMagazine.peek(copy).get(DataComponents.CUSTOM_NAME)),
        "saved rounds/components");
    h.assertTrue(LoadedMagazine.unload(copy, p), "unload");
    int rounds = 0;
    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
      var s = p.getInventory().getItem(i);
      rounds += s.getCount() * MagazineItem.rounds(s);
    }
    h.assertTrue(rounds == 23 && LoadedMagazine.peek(copy).isEmpty(), "round ledger");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void fullInventoryCannotDeleteUnloadedPartialMagazine(GameTestHelper h) {
    var p = h.makeMockPlayer(GameType.SURVIVAL);
    for (int i = 0; i < p.getInventory().getContainerSize(); i++)
      p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    var gun = item("pistol", 1);
    var mag = item("piercing_rounds_magazine", 1);
    MagazineItem.spend(mag);
    LoadedMagazine.set(gun, mag);
    h.assertTrue(
        !LoadedMagazine.unload(gun, p) && MagazineItem.rounds(LoadedMagazine.peek(gun)) == 9,
        "full inventory lost partial magazine");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void partialMagazinesCannotBeRefilledByManufacturingAndMilitaryBillIsOriginal(
      GameTestHelper h) {
    var emptyPipe = item("fluid_pipe", 1);
    var filledPipe = emptyPipe.copy();
    var contents = new net.minecraft.nbt.CompoundTag();
    contents.put(
        "Fluid",
        factorycraft.petroleum.PetroleumFluids.LIGHT.stack(37).save(h.getLevel().registryAccess()));
    filledPipe.set(
        DataComponents.BLOCK_ENTITY_DATA,
        net.minecraft.world.item.component.CustomData.of(contents));
    var turretRecipe =
        (factorycraft.recipes.FactoryRecipe) recipe(h, "flamethrower_turret").value();
    var pipeIngredient =
        turretRecipe.inputs().stream()
            .map(factorycraft.recipes.FactoryRecipe.CountedIngredient::ingredient)
            .filter(i -> i.test(emptyPipe))
            .findFirst()
            .orElseThrow();
    h.assertTrue(!pipeIngredient.test(filledPipe), "turret manufacturing consumed portable fluid");
    var handcraft =
        (factorycraft.recipes.CountedCraftingRecipe) recipe(h, "craft_flamethrower_turret").value();
    var validInput =
        CraftingInput.of(
            2,
            2,
            java.util.List.of(
                item("steel_plate", 30),
                item("iron_gear", 15),
                emptyPipe.copyWithCount(10),
                item("engine_unit", 5)));
    var invalidInput =
        CraftingInput.of(
            2,
            2,
            java.util.List.of(
                item("steel_plate", 30),
                item("iron_gear", 15),
                filledPipe.copyWithCount(10),
                item("engine_unit", 5)));
    h.assertTrue(
        handcraft.matches(validInput, h.getLevel())
            && !handcraft.matches(invalidInput, h.getLevel())
            && filledPipe.has(DataComponents.BLOCK_ENTITY_DATA),
        "handcraft pipe reservation/ownership");
    var r = (factorycraft.recipes.FactoryRecipe) recipe(h, "piercing_rounds_magazine").value();
    var mag = item("firearm_magazine", 1);
    MagazineItem.spend(mag);
    h.assertTrue(
        !r.inputs().getFirst().ingredient().test(mag)
            && r.inputs().getFirst().ingredient().test(item("firearm_magazine", 1)),
        "upgrade refills used ammunition");
    var science = (factorycraft.recipes.FactoryRecipe) recipe(h, "military_science_pack").value();
    h.assertTrue(
        science.ticks() == 200
            && science.result().getCount() == 2
            && science.inputs().get(2).count() == 2,
        "military science bill");
    var tech = (TechnologyRecipe) recipe(h, "military_science").value();
    h.assertTrue(
        tech.units() == 30 && tech.ticks() == 300 && tech.packs().size() == 2,
        "military science technology");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void weaponsRequireARealRoundAndServerCooldownPreventsReplay(GameTestHelper h) {
    var p = h.makeMockPlayer(GameType.SURVIVAL);
    p.setPos(h.absolutePos(new BlockPos(3, 2, 3)).getCenter());
    p.setYRot(0);
    p.setXRot(0);
    var gun = item("pistol", 1);
    h.assertTrue(!Combat.fire(p, gun, "pistol"), "empty gun fires");
    p.getInventory().setItem(0, item("firearm_magazine", 2));
    h.assertTrue(Combat.fire(p, gun, "pistol"), "first paid shot");
    for (int i = 0; i < 100; i++) h.assertTrue(!Combat.fire(p, gun, "pistol"), "cooldown replay");
    h.assertTrue(
        p.getInventory().getItem(0).getCount() == 1
            && MagazineItem.rounds(LoadedMagazine.peek(gun)) == 9,
        "ammo debit");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void allCombatProfilesAndRecipeCodecsLoadAndMissingProfilesFailClosed(
      GameTestHelper h) {
    for (String n :
        java.util.List.of(
            "pistol",
            "submachine_gun",
            "shotgun",
            "combat_shotgun",
            "rocket_launcher",
            "flamethrower",
            "gun_turret",
            "laser_turret",
            "flamethrower_turret"))
      h.assertTrue(CombatProfile.get(h.getLevel(), n) != null, "missing " + n);
    h.assertTrue(CombatProfile.get(h.getLevel(), "removed") == null, "missing profile fallback");
    var c = Content.COMBAT_SERIALIZER.get().streamCodec();
    var b =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      var p = CombatProfile.get(h.getLevel(), "shotgun");
      c.encode(b, p);
      h.assertTrue(p.equals(c.decode(b)), "combat wire codec");
    } finally {
      b.release();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void armorHasNoDurabilityAndAlliedCreaturesAreNotAutomaticTargets(
      GameTestHelper h) {
    h.assertTrue(
        !item("light_armor", 1).isDamageableItem() && !item("heavy_armor", 1).isDamageableItem(),
        "source infinite armor");
    var pig = h.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
    var p = h.makeMockPlayer(GameType.SURVIVAL);
    h.assertTrue(
        !Combat.allowed(h.getLevel(), pig, p, "", true)
            && !Combat.allowed(h.getLevel(), p, p, "", false),
        "friendly target");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void extendedHeavyArmorHandcraftConsumesActualHundredCopperPlates(
      GameTestHelper h) {
    var r = (factorycraft.recipes.CountedCraftingRecipe) recipe(h, "craft_heavy_armor").value();
    var input =
        CraftingInput.of(
            3,
            1,
            java.util.List.of(
                item("copper_plate", 64), item("copper_plate", 36), item("steel_plate", 50)));
    h.assertTrue(
        java.util.Arrays.equals(r.plan(input), new int[] {64, 36, 50}), "original large bill");
    var shortInput =
        CraftingInput.of(
            3,
            1,
            java.util.List.of(
                item("copper_plate", 64), item("copper_plate", 35), item("steel_plate", 50)));
    h.assertTrue(!r.matches(shortInput, h.getLevel()), "underpaid armor");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void physicalFlamePortsAndStaleCapabilitiesRespectWholeAssembly(GameTestHelper h) {
    var be = turret(h, FactoryKind.FLAME_TURRET, h.absolutePos(new BlockPos(5, 2, 5)));
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(be.ready(), "formed turret");
          var p = be.assembly().profile();
          var f = p.facing(be.getBlockState());
          var spec = MachinePorts.definitions(p).getFirst();
          var pos = spec.position(p, be.getBlockPos(), f);
          var cap = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, spec.side(f));
          h.assertTrue(cap != null, "physical oil capability");
          var water =
              new net.neoforged.neoforge.fluids.FluidStack(
                  net.minecraft.world.level.material.Fluids.WATER, 100);
          h.assertTrue(
              cap.fill(
                      water,
                      net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
                  == 0,
              "water fueled flamethrower");
          var oil =
              new net.neoforged.neoforge.fluids.FluidStack(
                  factorycraft.petroleum.PetroleumFluids.CRUDE.source.get(), 100);
          h.assertTrue(
              cap.fill(
                      oil,
                      net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
                  > 0,
              "oil port");
          be.onChunkUnloaded();
          h.assertTrue(
              cap.drain(
                      100,
                      net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
                  .isEmpty(),
              "stale port");
          be.onLoad();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 160)
  public static void gunTurretConsumesRoundsAndNeverTargetsPassiveAnimals(GameTestHelper h) {
    var be = turret(h, FactoryKind.GUN_TURRET, h.absolutePos(new BlockPos(5, 2, 5)));
    be.items.setStackInSlot(0, item("firearm_magazine", 1));
    h.setBlock(new BlockPos(5, 1, 11), net.minecraft.world.level.block.Blocks.STONE);
    h.setBlock(new BlockPos(4, 1, 9), net.minecraft.world.level.block.Blocks.STONE);
    var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 11));
    zombie.setNoAi(true);
    zombie.setPersistenceRequired();
    var cow = h.spawn(EntityType.COW, new BlockPos(4, 2, 9));
    cow.setNoAi(true);
    float cowHealth = cow.getHealth();
    h.runAfterDelay(
        70,
        () -> {
          int rounds =
              be.items.getStackInSlot(0).getCount()
                      * MagazineItem.rounds(be.items.getStackInSlot(0))
                  + MagazineItem.rounds(be.items.getStackInSlot(1));
          h.assertTrue(
              rounds < 10 && zombie.getHealth() < zombie.getMaxHealth(),
              "gun turret did not debit and damage");
          h.assertTrue(cow.getHealth() == cowHealth, "turret targets cattle");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void turretPortableDropKeepsSupplyAndPartialMagazineExactlyOnce(GameTestHelper h) {
    var be = turret(h, FactoryKind.GUN_TURRET, h.absolutePos(new BlockPos(5, 2, 5)));
    be.items.setStackInSlot(0, item("firearm_magazine", 2));
    var partial = item("piercing_rounds_magazine", 1);
    for (int i = 0; i < 3; i++) MagazineItem.spend(partial);
    partial.set(DataComponents.CUSTOM_NAME, Component.literal("sole seven"));
    be.items.setStackInSlot(1, partial);
    h.runAfterDelay(
        5,
        () -> {
          var pos = be.assembly().positions().getLast();
          var part = (factorycraft.blockentity.CasingBlockEntity) h.getLevel().getBlockEntity(pos);
          part.genericForRemoval().assembly().removeWhole(true);
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(5),
                      e ->
                          e.getItem()
                              .is(Content.FACTORY.get(FactoryKind.GUN_TURRET).get().asItem()));
          h.assertTrue(
              drops.size() == 1 && drops.getFirst().getItem().getCount() == 1,
              "duplicate portable turret");
          var saved = drops.getFirst().getItem().get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
          var handler = new net.neoforged.neoforge.items.ItemStackHandler(2);
          handler.deserializeNBT(h.getLevel().registryAccess(), saved.getCompound("Ammo"));
          h.assertTrue(
              handler.getStackInSlot(0).getCount() == 2
                  && MagazineItem.rounds(handler.getStackInSlot(1)) == 7,
              "portable ammo ownership");
          h.assertTrue(
              Component.literal("sole seven")
                  .equals(handler.getStackInSlot(1).get(DataComponents.CUSTOM_NAME)),
              "portable components");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void militaryProductionReservesFiniteInputsAndRequiresAnExplicitRecipe(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(5, 2, 5));
    StructurePlacement.place(
        h.getLevel(), pos, Content.FACTORY.get(FactoryKind.ASSEMBLER).get().defaultBlockState());
    var m = (factorycraft.blockentity.MachineBlockEntity) h.getLevel().getBlockEntity(pos);
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(
              m.selectRecipe(
                  ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "military_science_pack")),
              "select military recipe");
          m.items.setStackInSlot(0, item("piercing_rounds_magazine", 1));
          m.items.setStackInSlot(1, item("grenade", 1));
          m.items.setStackInSlot(2, item("stone_wall", 2));
          m.power.restore(16000);
          for (int i = 0; i < 400; i++) m.tick();
          h.assertTrue(
              m.items.getStackInSlot(4).is(Content.SCIENCE_MILITARY.get())
                  && m.items.getStackInSlot(4).getCount() == 2,
              "real military recipe did not produce exactly two packs");
          h.assertTrue(
              m.items.getStackInSlot(0).isEmpty()
                  && m.items.getStackInSlot(1).isEmpty()
                  && m.items.getStackInSlot(2).isEmpty(),
              "inputs not consumed");
          h.assertTrue(m.power.getEnergyStored() == 12000, "assembler speed .5 and FE ledger");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void weaponsOilAndTurretStatePersistBetweenIndependentJvms(GameTestHelper h) {
    var l = h.getLevel();
    var at = new BlockPos(1536, 71, 1535);
    var barrel = at.west(6);
    for (var profile : java.util.List.of(MachineProfile.FLAME_TURRET, MachineProfile.GUN_TURRET))
      for (var cell :
          profile.cells(profile == MachineProfile.FLAME_TURRET ? at : at.east(8), Direction.NORTH))
        l.getChunkAt(cell);
    l.getChunkAt(barrel);
    if (l.getBlockEntity(barrel) == null) {
      h.assertTrue(
          l.getBlockEntity(at) == null && l.getBlockEntity(at.east(8)) == null,
          "incomplete fixture must not be reseeded");
      l.setBlockAndUpdate(
          barrel, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
      var chest =
          (net.minecraft.world.level.block.entity.BarrelBlockEntity) l.getBlockEntity(barrel);
      var gun = item("pistol", 1);
      var mag = item("firearm_magazine", 1);
      mag.set(DataComponents.CUSTOM_NAME, Component.literal("v0280 exact"));
      for (int i = 0; i < 7; i++) MagazineItem.spend(mag);
      LoadedMagazine.set(gun, mag);
      chest.setItem(0, gun);
      var be = turret(h, FactoryKind.FLAME_TURRET, at);
      var oil = factorycraft.petroleum.PetroleumFluids.LIGHT.stack(137);
      oil.set(DataComponents.CUSTOM_NAME, Component.literal("v0280 oil"));
      be.oil.restore(new factorycraft.fluids.FluidKey(oil), 137);
      be.sync();
      var gunTurret = turret(h, FactoryKind.GUN_TURRET, at.east(8));
      gunTurret.items.setStackInSlot(0, item("firearm_magazine", 2));
      gunTurret.items.setStackInSlot(1, mag.copy());
      gunTurret.yaw = 71;
      gunTurret.pitch = -13;
      gunTurret.sync();
      System.out.println(
          "FACTORYCRAFT_V0280_DISK: SEEDED exact 3-round pistol, 23 turret rounds and 137 typed"
              + " mB");
    } else {
      var chest =
          (net.minecraft.world.level.block.entity.BarrelBlockEntity) l.getBlockEntity(barrel);
      var held = LoadedMagazine.peek(chest.getItem(0));
      h.assertTrue(
          MagazineItem.rounds(held) == 3
              && Component.literal("v0280 exact").equals(held.get(DataComponents.CUSTOM_NAME)),
          "saved weapon ammunition");
      var be = (TurretBlockEntity) l.getBlockEntity(at);
      h.assertTrue(
          be.oil.amount() == 137
              && Component.literal("v0280 oil")
                  .equals(be.oil.key().stack(1).get(DataComponents.CUSTOM_NAME)),
          "saved exact oil");
      var gun = (TurretBlockEntity) l.getBlockEntity(at.east(8));
      h.assertTrue(
          gun.items.getStackInSlot(0).getCount() == 2
              && MagazineItem.rounds(gun.items.getStackInSlot(1)) == 3
              && gun.yaw == 71
              && gun.pitch == -13,
          "saved turret state");
      System.out.println(
          "FACTORYCRAFT_V0280_DISK: VERIFIED exact 3-round pistol, 23 turret rounds and 137 typed"
              + " mB");
    }
    for (var be :
        java.util.List.of(
            (TurretBlockEntity) l.getBlockEntity(at),
            (TurretBlockEntity) l.getBlockEntity(at.east(8))))
      for (var cell : be.assembly().positions()) {
        var part = l.getBlockEntity(cell);
        if (part != null) part.onChunkUnloaded();
        l.getChunkAt(cell).setUnsaved(true);
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void handheldFlameAmmunitionKeepsHundredChargesAcrossSerialization(
      GameTestHelper h) {
    var ammo = item("flamethrower_ammo", 1);
    MagazineItem.spend(ammo);
    var copy =
        ItemStack.parse(h.getLevel().registryAccess(), ammo.save(h.getLevel().registryAccess()))
            .orElseThrow();
    h.assertTrue(
        MagazineItem.rounds(copy) == 99, "handheld source has no vehicle-only 1.125 modifier");
    for (int n = 0; n < 99; n++) h.assertTrue(MagazineItem.spend(copy), "charge debit");
    h.assertTrue(!MagazineItem.spend(copy), "empty fuel fires");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void laserPaysOriginalIdleAndShotEnergyAndStopsAtZero(GameTestHelper h) {
    var be = turret(h, FactoryKind.LASER_TURRET, h.absolutePos(new BlockPos(5, 2, 5)));
    h.setBlock(new BlockPos(5, 1, 11), net.minecraft.world.level.block.Blocks.STONE);
    var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 11));
    zombie.setNoAi(true);
    h.runAfterDelay(5, () -> be.power.restore(2136));
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              be.power.getEnergyStored() == 0 && be.shotTick != Long.MIN_VALUE,
              "laser did not pay idle + shot FE");
          h.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "laser did not damage hostile");
          var n = be.saveWithoutMetadata(h.getLevel().registryAccess());
          h.assertTrue(
              n.getInt("ShotRemainder") == 125 && n.getInt("IdleRemainder") == 1,
              "fractional FE discarded");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 120)
  public static void flameStreamHasTravelAreaDamageAndPaidOilInsteadOfWater(GameTestHelper h) {
    var l = h.getLevel();
    var at = PowerV20GameTests.site(h, 112);
    var chunks = new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) {
        var cp = new net.minecraft.world.level.ChunkPos(at.offset(x * 16, 0, z * 16));
        chunks.add(cp);
        l.setChunkForced(cp.x, cp.z, true);
        l.getChunk(cp.x, cp.z);
      }
    StructurePlacement.place(
        l,
        at,
        Content.FACTORY
            .get(FactoryKind.FLAME_TURRET)
            .get()
            .defaultBlockState()
            .setValue(FactoryBlock.FACING, Direction.SOUTH));
    var be = (TurretBlockEntity) l.getBlockEntity(at);
    for (var part : be.assembly().positions()) l.getBlockEntity(part).onLoad();
    be.onLoad();
    var target = at.south(10);
    l.setBlockAndUpdate(
        target.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
    var zombie = EntityType.ZOMBIE.create(l);
    zombie.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(target));
    zombie.setNoAi(true);
    zombie.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    l.addFreshEntity(zombie);
    h.runAfterDelay(
        5,
        () -> {
          be.oil.restore(
              new factorycraft.fluids.FluidKey(
                  factorycraft.petroleum.PetroleumFluids.LIGHT.stack(1)),
              500);
          be.sync();
        });
    h.runAfterDelay(
        70,
        () -> {
          h.assertTrue(
              be.paidShots > 0 && 500 - be.oil.amount() == be.paidShots * 2,
              "paid oil ledger: "
                  + be.oil.amount()
                  + " ready="
                  + be.ready()
                  + " target="
                  + zombie.position()
                  + " shot="
                  + be.shotTick);
          h.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "flame did not reach hostile");
          for (var cp : chunks) l.setChunkForced(cp.x, cp.z, false);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void allSixWeaponsUseRealAmmunitionAndHostileHitboxes(GameTestHelper h) {
    var victims = new java.util.ArrayList<net.minecraft.world.entity.monster.Zombie>();
    int offset = 0;
    for (String name :
        java.util.List.of(
            "pistol",
            "submachine_gun",
            "shotgun",
            "combat_shotgun",
            "rocket_launcher",
            "flamethrower")) {
      int x = 3 + offset * 5;
      h.setBlock(new BlockPos(x, 1, 8), net.minecraft.world.level.block.Blocks.STONE);
      var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, 8));
      zombie.setNoAi(true);
      zombie.setItemSlot(
          net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
      victims.add(zombie);
      var p = h.makeMockPlayer(GameType.SURVIVAL);
      p.setPos(h.absolutePos(new BlockPos(x, 2, 2)).getCenter().add(0, -.5, 0));
      p.setYRot(0);
      p.setXRot(7);
      var ammo =
          item(
              name.contains("shotgun")
                  ? "shotgun_shell"
                  : name.equals("rocket_launcher")
                      ? "rocket"
                      : name.equals("flamethrower") ? "flamethrower_ammo" : "firearm_magazine",
              1);
      p.getInventory().setItem(1, ammo);
      var gun = item(name, 1);
      h.assertTrue(Combat.fire(p, gun, name), "weapon did not fire: " + name);
      offset++;
    }
    h.runAfterDelay(
        50,
        () -> {
          for (var victim : victims)
            h.assertTrue(
                victim.getHealth() < victim.getMaxHealth(),
                "weapon hitbox/trajectory failed at " + victim.blockPosition());
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "combat_v0280", timeoutTicks = 100)
  public static void gateOwnerAndStunRestoreAiWithoutGrantingFriendlyAccess(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(4, 2, 4));
    h.getLevel().setBlockAndUpdate(pos, Content.GATE.get().defaultBlockState());
    var gate = (GateBlockEntity) h.getLevel().getBlockEntity(pos);
    var owner = h.makeMockPlayer(GameType.SURVIVAL);
    var stranger = h.makeMockPlayer(GameType.SURVIVAL);
    gate.bind(owner);
    h.assertTrue(gate.friendly(owner) && !gate.friendly(stranger), "gate force access");
    h.assertTrue(!item("heavy_armor", 1).isDamageableItem(), "armor has durability");
    var target = h.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 8));
    var team = h.getLevel().getScoreboard().addPlayerTeam("combat28_" + target.getId());
    h.getLevel().getScoreboard().addPlayerToTeam(target.getStringUUID(), team);
    h.assertTrue(
        !Combat.allowed(h.getLevel(), target, null, "vanilla:" + team.getName(), true),
        "offline allied target");
    CombatStun.apply(target, 3);
    h.assertTrue(target.isNoAi(), "stun AI");
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(!target.isNoAi(), "stun expiry did not restore AI");
          h.succeed();
        });
  }
}
