package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.combat.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.rail.*;
import factorycraft.registry.Content;
import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class PostReleaseV301GameTests {
  static ResourceLocation id(String value) {
    return ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, value);
  }

  static final Map<ServerLevel, BlockPos> finishedFlames = new WeakHashMap<>(),
      finishedLasers = new WeakHashMap<>();

  static Set<Long> pin(GameTestHelper h, BlockPos center, int radius) {
    var set = new HashSet<Long>();
    int x = center.getX() >> 4, z = center.getZ() >> 4;
    for (int a = -radius; a <= radius; a++)
      for (int b = -radius; b <= radius; b++) {
        h.getLevel().getChunk(x + a, z + b);
        h.getLevel().setChunkForced(x + a, z + b, true);
        set.add(new net.minecraft.world.level.ChunkPos(x + a, z + b).toLong());
      }
    return set;
  }

  static void release(GameTestHelper h, Set<Long> set) {
    for (long p : set) h.getLevel().setChunkForced((int) p, (int) (p >> 32), false);
  }

  static Husk enemy(ServerLevel l, Vec3 at, float health, boolean fixed) {
    var target = EntityType.HUSK.create(l);
    target.setPos(at);
    target.setNoAi(true);
    target.setNoGravity(true);
    target.setPersistenceRequired();
    target.getAttribute(Attributes.ARMOR).setBaseValue(0);
    if (fixed) target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
    target.setHealth(health);
    l.addFreshEntity(target);
    return target;
  }

  static TurretBlockEntity turret(GameTestHelper h, FactoryKind kind, BlockPos at) {
    StructurePlacement.place(
        h.getLevel(),
        at,
        Content.FACTORY
            .get(kind)
            .get()
            .defaultBlockState()
            .setValue(FactoryBlock.FACING, Direction.SOUTH));
    var be = (TurretBlockEntity) h.getLevel().getBlockEntity(at);
    for (var p : be.assembly().positions()) h.getLevel().getBlockEntity(p).onLoad();
    be.onLoad();
    return be;
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 500)
  public static void laserKillsThreeSequentialTargetsOnFiniteSteamAndResumesAfterDisconnect(
      GameTestHelper h) {
    var l = h.getLevel();
    var at = PowerV20GameTests.site(h, 251);
    var pins = pin(h, at, 1);
    StructurePlacement.place(
        l, at, Content.FACTORY.get(FactoryKind.SUBSTATION).get().defaultBlockState());
    var pole = (PoleBlockEntity) l.getBlockEntity(at);
    for (var p : pole.assembly().positions()) l.getBlockEntity(p).onLoad();
    pole.onLoad();
    var source = FinitePowerFixture.place(l, at.west(5).south(3), Direction.NORTH, 4000);
    var source2 = FinitePowerFixture.place(l, at.west(5).north(5), Direction.NORTH, 4000);
    var be = turret(h, FactoryKind.LASER_TURRET, at.east(5).south(2));
    var current = new Husk[1];
    int[] kills = {0}, stage = {0};
    long[] due = {0}, shots = {0};
    h.runAfterDelay(8, () -> current[0] = enemy(l, be.muzzle().add(0, -1, 10), 3.9f, true));
    h.onEachTick(
        () -> {
          if (current[0] == null) return;
          if (stage[0] == 0 && !current[0].isAlive()) {
            kills[0]++;
            if (kills[0] < 3)
              current[0] =
                  enemy(l, be.muzzle().add(kills[0] % 2 == 0 ? -2 : 2, -1, 10), 3.9f, true);
            else {
              source.onChunkUnloaded();
              source2.onChunkUnloaded();
              shots[0] = be.paidShots;
              due[0] = l.getGameTime() + 30;
              stage[0] = 1;
              current[0] = enemy(l, be.muzzle().add(0, -1, 10), 3.9f, true);
            }
          } else if (stage[0] == 1 && l.getGameTime() >= due[0]) {
            h.assertTrue(
                be.paidShots == shots[0] && current[0].isAlive(),
                "disconnected laser emitted a free shot");
            source.onLoad();
            source2.onLoad();
            stage[0] = 2;
          } else if (stage[0] == 2 && !current[0].isAlive()) {
            h.assertTrue(
                be.paidShots >= 4 && source.steam.amount() < 4000 && source2.steam.amount() < 4000,
                "real finite source did not pay four targets");
            be.onChunkUnloaded();
            source.onChunkUnloaded();
            source2.onChunkUnloaded();
            finishedLasers.put(l, be.getBlockPos());
            release(h, pins);
            h.succeed();
          }
        });
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 100)
  public static void laserCommitsIdleBeforeReentrantDamageListenerPacksItsOnlyOwner(
      GameTestHelper h) {
    var l = h.getLevel();
    var at = PowerV20GameTests.site(h, 258);
    var pins = pin(h, at, 1);
    var be = turret(h, FactoryKind.LASER_TURRET, at);
    var victim = enemy(l, be.muzzle().add(0, -1, 10), 3.9f, true);
    boolean[] packed = {false};
    java.util.function.Consumer<
            net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent>
        listener =
            e -> {
              if (e.getEntity() != victim) return;
              be.assembly().removeWhole(true);
              var drops =
                  l.getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new AABB(at).inflate(5),
                      item ->
                          item.getItem()
                              .is(Content.FACTORY.get(FactoryKind.LASER_TURRET).get().asItem()));
              h.assertTrue(drops.size() == 1, "reentrant damage duplicated portable laser");
              var tag = drops.getFirst().getItem().get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
              h.assertTrue(
                  tag.getLong("PaidShots") == 1
                      && tag.getInt("Energy") == 0
                      && tag.getInt("IdleDebt") >= 0
                      && tag.getInt("IdleDebt") <= 4,
                  "portable owner was captured before its paid tick's idle debit");
              packed[0] = true;
            };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
    h.runAfterDelay(
        90, () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener));
    h.onEachTick(
        () -> {
          if (packed[0]) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            release(h, pins);
            h.succeed();
          } else {
            // Declared bounded test supply: only a full source-sized buffer, never a free shot.
            be.power.restore(2136);
            be.tick();
          }
        });
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 450)
  public static void flameKeepsPrimingAcrossThreeTargetsWithFiniteSubthresholdRemainder(
      GameTestHelper h) {
    var l = h.getLevel();
    var at = PowerV20GameTests.site(h, 252);
    var pins = pin(h, at, 1);
    var be = turret(h, FactoryKind.FLAME_TURRET, at);
    be.oil.restore(new FluidKey(factorycraft.petroleum.PetroleumFluids.LIGHT.stack(251)), 251);
    var current = new Husk[1];
    int[] kills = {0};
    h.runAfterDelay(8, () -> current[0] = enemy(l, be.muzzle().add(0, -1, 12), .5f, true));
    h.onEachTick(
        () -> {
          if (current[0] == null || current[0].isAlive()) return;
          if (++kills[0] < 3)
            current[0] = enemy(l, be.muzzle().add(kills[0] == 1 ? 4 : -4, -1, 14), .5f, true);
          else {
            var n = be.saveWithoutMetadata(l.registryAccess());
            h.assertTrue(
                n.getBoolean("FlameActive") && be.oil.amount() < 250 && be.paidShots >= 3,
                "lost legitimate priming between victims");
            h.assertTrue(
                be.pipeOil.amount() == 0 && 251 - be.oil.amount() == be.paidShots * 2,
                "finite per-attack oil ledger");
            be.onChunkUnloaded();
            finishedFlames.put(l, be.getBlockPos());
            release(h, pins);
            h.succeed();
          }
        });
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 50)
  public static void physicalMuzzleOverridesRemoteOwnerKnockbackWithoutLosingKillAttribution(
      GameTestHelper h) {
    var l = h.getLevel();
    var target = h.spawn(EntityType.HUSK, new BlockPos(5, 2, 5));
    target.setNoAi(true);
    target.setNoGravity(true);
    target.getAttribute(Attributes.ARMOR).setBaseValue(0);
    var owner =
        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "RemoteOwner301"));
    var center = target.getBoundingBox().getCenter();
    owner.setPos(center.add(5, 0, 0));
    var muzzle = center.add(-4, 0, 0);
    Combat.hitscan(
        l,
        muzzle,
        new Vec3(1, 0, 0),
        owner,
        "",
        new CombatProfile("bullet", "bullet", 6, 12, 1, 1, 0, 0),
        10,
        true);
    h.assertTrue(target.getDeltaMovement().x > 0, "push followed owner instead of turret muzzle");
    h.assertTrue(
        target.getLastDamageSource().getEntity() == owner
            && target.getLastDamageSource().getSourcePosition().equals(muzzle),
        "owner credit or physical origin lost");
    target.setDeltaMovement(Vec3.ZERO);
    target.invulnerableTime = 0;
    Combat.damageAt(l, target, null, owner, "", "explosion", 1, true, muzzle);
    h.assertTrue(
        target.getDeltaMovement().x > 0, "radial explosion push followed the remote owner");
    target.setDeltaMovement(Vec3.ZERO);
    target.invulnerableTime = 0;
    Combat.damageAt(l, target, null, owner, "", "fire", 1, true, muzzle);
    h.assertTrue(
        target.getDeltaMovement().equals(Vec3.ZERO),
        "fire damage-over-time creates owner-directed pushes");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 80)
  public static void shortHitSurvivesUnloadedMaximumRangeAndStopsEffectsAtCover(GameTestHelper h) {
    var l = h.getLevel();
    var at = PowerV20GameTests.site(h, 253);
    var pins = pin(h, at, 0);
    var muzzle = at.getCenter().add(2, 2, 2);
    var target = enemy(l, muzzle.add(4, -.9, 0), 20, true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    l.getEntitiesOfClass(
                            Husk.class, new AABB(muzzle, muzzle.add(8, 0, 0)).inflate(2))
                        .contains(target),
                    "waiting for native entity section tracking"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  !Combat.loadedRay(l, muzzle, muzzle.add(128, 0, 0)),
                  "fixture must have an unloaded tail");
              var profile = new CombatProfile("laser", "", 40, 128, 20, 1, 0, 800000);
              Combat.hitscan(l, muzzle, new Vec3(1, 0, 0), null, "", profile, 20, true);
              h.assertTrue(
                  target.getHealth() < 20,
                  "near victim suppressed: position="
                      + target.position()
                      + " muzzle="
                      + muzzle
                      + " loadedEnd="
                      + Combat.loadedEnd(l, muzzle, muzzle.add(128, 0, 0)));
              float held = target.getHealth();
              target.invulnerableTime = 0;
              l.setBlockAndUpdate(
                  BlockPos.containing(muzzle.add(2, 0, 0)), Blocks.STONE.defaultBlockState());
              Combat.hitscan(l, muzzle, new Vec3(1, 0, 0), null, "", profile, 20, true);
              h.assertTrue(target.getHealth() == held, "beam passed through solid cover");
              release(h, pins);
              h.succeed();
            });
  }

  static CombatProjectile grenade(GameTestHelper h, Vec3 at, Vec3 velocity) {
    var owner =
        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Grenade301"));
    var projectile =
        new CombatProjectile(h.getLevel(), owner, CombatV28GameTests.item("grenade", 1), "", 0, 1);
    projectile.setPos(at);
    projectile.setDeltaMovement(velocity);
    return projectile;
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 50)
  public static void grenadeCrossesFifteenBlocksWithoutFabricatedImpact(GameTestHelper h) {
    var at = h.absoluteVec(new Vec3(4, 55, 4));
    var grenade = grenade(h, at, new Vec3(.8, .65, 0));
    double distance = 0;
    for (int t = 0; t < 45; t++) {
      var before = grenade.position();
      grenade.tick();
      distance += before.distanceTo(grenade.position());
      h.assertTrue(!grenade.isRemoved(), "open-air grenade exploded at a fake range boundary");
    }
    h.assertTrue(distance > 20, "arc did not exercise former 15-block boundary");
    grenade.discard();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 50)
  public static void grenadeFuseIsTimeBasedAndExactAfterNbtTransfer(GameTestHelper h) {
    var original = grenade(h, h.absoluteVec(new Vec3(3, 40, 3)), Vec3.ZERO);
    original.setNoGravity(true);
    for (int i = 0; i < 37; i++) original.tick();
    var saved = original.saveWithoutId(new CompoundTag());
    original.discard();
    var loaded = new CombatProjectile(Content.COMBAT_PROJECTILE.get(), h.getLevel());
    loaded.load(saved);
    for (int i = 37; i < 99; i++) {
      loaded.tick();
      h.assertTrue(!loaded.isRemoved(), "restored fuse reset or shortened");
    }
    loaded.tick();
    h.assertTrue(loaded.isRemoved(), "100-tick safety fuse did not detonate exactly once");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 50)
  public static void grenadeAndRocketUseRealWallCollisionWhileRocketExpiryIsNotImpact(
      GameTestHelper h) {
    var at = h.absoluteVec(new Vec3(3, 30, 3));
    var wall = BlockPos.containing(at.add(3, 0, 0));
    for (int y = -1; y <= 1; y++)
      h.getLevel().setBlockAndUpdate(wall.offset(0, y, 0), Blocks.STONE.defaultBlockState());
    var grenade = grenade(h, at, new Vec3(.8, 0, 0));
    for (int i = 0; i < 8 && !grenade.isRemoved(); i++) grenade.tick();
    h.assertTrue(grenade.isRemoved(), "real wall did not detonate grenade");
    var owner = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
    var rocket =
        new CombatProjectile(h.getLevel(), owner, CombatV28GameTests.item("rocket", 1), "", 200, 0);
    rocket.setPos(at.add(0, 10, 0));
    rocket.range(1);
    rocket.setDeltaMovement(new Vec3(2, 0, 0));
    rocket.tick();
    h.assertTrue(rocket.isRemoved(), "range-exhausted rocket leaked an entity");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 100)
  public static void armorExposesAllSixteenAttributesAndGenericItemComponentOverridesDefaults(
      GameTestHelper h) {
    var target = h.spawn(EntityType.HUSK, new BlockPos(3, 2, 3));
    target.setNoAi(true);
    target.getAttribute(Attributes.ARMOR).setBaseValue(0);
    for (var k : ArmorAttributes.Kind.values())
      h.assertTrue(
          target.getAttribute(ArmorAttributes.flat(k)) != null
              && target.getAttribute(ArmorAttributes.percent(k)) != null,
          "living attribute map incomplete");
    var armor = CombatV28GameTests.item("heavy_armor", 1);
    h.assertTrue(
        armor.getAttributeModifiers().modifiers().size() == 6,
        "zero or duplicate armor modifiers exposed");
    var custom =
        ItemAttributeModifiers.builder()
            .add(
                ArmorAttributes.flat(ArmorAttributes.Kind.PHYSICAL),
                new AttributeModifier(
                    id("component_test"), 7, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.CHEST)
            .build();
    armor.set(DataComponents.ATTRIBUTE_MODIFIERS, custom);
    target.setItemSlot(EquipmentSlot.CHEST, armor);
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(
              target.getAttributeValue(ArmorAttributes.flat(ArmorAttributes.Kind.PHYSICAL)) == 7
                  && target.getAttributeValue(
                          ArmorAttributes.percent(ArmorAttributes.Kind.PHYSICAL))
                      == 0,
              "static armor code bypassed the public item component");
          Combat.damage(h.getLevel(), target, null, null, "", "bullet", 10, false);
          h.assertTrue(
              Math.abs(target.getHealth() - 19.4) < .001,
              "generic component did not receive source low-damage resistance exactly once");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 100)
  public static void standardArmorTooltipPercentagesAndNeutralDamageRemainNative(GameTestHelper h) {
    var stack = CombatV28GameTests.item("light_armor", 1);
    var lines =
        stack
            .getTooltipLines(Item.TooltipContext.of(h.getLevel()), null, TooltipFlag.NORMAL)
            .stream()
            .map(Component::getString)
            .toList();
    h.assertTrue(
        lines.stream().anyMatch(s -> s.contains("20%"))
            && lines.stream().anyMatch(s -> s.contains("10%")),
        "standard tooltip failed percentage formatting");
    for (var type :
        List.of(
            DamageTypes.FALL,
            DamageTypes.MAGIC,
            DamageTypes.STARVE,
            DamageTypes.FELL_OUT_OF_WORLD,
            DamageTypes.GENERIC)) {
      var source =
          new DamageSource(
              h.getLevel()
                  .registryAccess()
                  .registryOrThrow(Registries.DAMAGE_TYPE)
                  .getHolderOrThrow(type));
      h.assertTrue(
          ArmorAttributes.classify(source) == null,
          "untagged neutral damage gained invented immunity");
    }
    for (var entry :
        Map.of(
                DamageTypes.ARROW,
                ArmorAttributes.Kind.PHYSICAL,
                DamageTypes.EXPLOSION,
                ArmorAttributes.Kind.EXPLOSION,
                DamageTypes.IN_FIRE,
                ArmorAttributes.Kind.FIRE)
            .entrySet()) {
      var source =
          new DamageSource(
              h.getLevel()
                  .registryAccess()
                  .registryOrThrow(Registries.DAMAGE_TYPE)
                  .getHolderOrThrow(entry.getKey()));
      h.assertTrue(
          ArmorAttributes.classify(source) == entry.getValue(), "source-level damage tag mapping");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 100)
  public static void heavyArmorAcidAndOrdinaryArmorComposeAndEntityNbtDoesNotDuplicateModifiers(
      GameTestHelper h) {
    var target = h.spawn(EntityType.HUSK, new BlockPos(3, 2, 3));
    target.setNoAi(true);
    target.getAttribute(Attributes.ARMOR).setBaseValue(0);
    target.setItemSlot(EquipmentSlot.CHEST, CombatV28GameTests.item("heavy_armor", 1));
    h.runAfterDelay(
        5,
        () -> {
          Combat.damage(h.getLevel(), target, null, null, "", "acid", 10, false);
          h.assertTrue(
              Math.abs(target.getHealth() - 18.8) < .001, "acid40% was not applied exactly once");
          target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        });
    h.runAfterDelay(
        10,
        () -> {
          h.assertTrue(
              target.getAttributeValue(Attributes.ARMOR) == 2,
              "chest attributes disabled ordinary helmet armor");
          var saved = target.saveWithoutId(new CompoundTag());
          var copy = EntityType.HUSK.create(h.getLevel());
          copy.load(saved);
          copy.tick();
          h.assertTrue(
              copy.getAttributeValue(ArmorAttributes.flat(ArmorAttributes.Kind.PHYSICAL)) == 6,
              "entity NBT duplicated flat chest modifier");
          copy.discard();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 100)
  public static void uraniumReadoutTracksActualAcidPaidExtractionAndCanonicalJadeSnapshot(
      GameTestHelper h) {
    var at = PowerV20GameTests.site(h, 254);
    MachineBlockEntity drill = NuclearV29GameTests.place(h, FactoryKind.DRILL, at, Direction.NORTH);
    var oreAt = drill.assembly().profile().center(at, Direction.NORTH).below();
    h.getLevel()
        .setBlockAndUpdate(
            oreAt,
            Content.RESOURCES
                .get(factorycraft.resources.ResourceKind.URANIUM)
                .get()
                .defaultBlockState());
    var ore = (ResourceDepositBlockEntity) h.getLevel().getBlockEntity(oreAt);
    ore.initializeReserve(3);
    ore.onLoad();
    drill.power.restore(16000);
    drill.miningAcid.restore(
        new FluidKey(factorycraft.petroleum.PetroleumFluids.ACID.stack(100)), 100);
    for (int i = 0; i < 80; i++) drill.tick();
    var message =
        Component.Serializer.toJson(
            ore.resourceBlock().reserveMessage(ore), h.getLevel().registryAccess());
    h.assertTrue(
        ore.remaining() == 2 && message.contains("uranium_acid") && message.contains("2"),
        "uranium message hid the actual paid remaining reserve");
    var player =
        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Uranium301"));
    player.setPos(oreAt.getCenter().add(0, 1, 0));
    var snapshot = MachineInspection.snapshot(h.getLevel(), oreAt, player);
    h.assertTrue(
        snapshot.getInt("ResourceRemaining") == 2 && snapshot.getBoolean("AcidRequired"),
        "Jade snapshot used stale or unavailable stock");
    var copy =
        (ResourceDepositBlockEntity)
            BlockEntity.loadStatic(
                oreAt,
                ore.getBlockState(),
                ore.saveWithFullMetadata(h.getLevel().registryAccess()),
                h.getLevel().registryAccess());
    h.assertTrue(
        copy.remaining() == 2 && !copy.available(),
        "resource stock or canonical guard lost in NBT");
    drill.miningAcid.restore(
        new FluidKey(factorycraft.petroleum.PetroleumFluids.ACID.stack(200)), 200);
    for (int i = 0; i < 160; i++) drill.tick();
    h.assertTrue(
        h.getLevel().getBlockState(oreAt).isAir()
            && drill.miningAcid.amount() == 0
            && NuclearV29GameTests.count(drill) == 3,
        "finite acid exhaustion ledger");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 100)
  public static void starterRailIsPaidAndConnectionsClearWhileDestroyedAnchorsCancel(
      GameTestHelper h) {
    var p = RailwayV30GameTests.plan(PowerV20GameTests.site(h, 255), 0, 0, -24, 0);
    RailwayV30GameTests.prepare(h, p);
    var player = RailwayV30GameTests.player(h, p, true);
    var start = p.curve.start;
    var shortCurve = RailPlanner.starter(start);
    RailwayV30GameTests.prepare(h, new RailPlan(shortCurve));
    player.setPos(start.x() + .5, start.y() + 2, start.z() + .5);
    RailwayV30GameTests.use(
        h, player, new BlockPos(start.x(), start.y() - 1, start.z()), start.y());
    h.assertTrue(
        RailPlanner.get(player) == null && RailPlacement.count(player) == 63,
        "initial ground use was an unpaid invisible point");
    var at = new BlockPos(shortCurve.end.x(), shortCurve.end.y(), shortCurve.end.z());
    player.setPos(at.getX() + .5, at.getY() + 2, at.getZ() + .5);
    RailwayV30GameTests.use(h, player, at, at.getY() + .3);
    h.assertTrue(RailPlanner.get(player) != null, "existing physical endpoint did not select");
    var goal = p.curve.end;
    player.setPos(goal.x() + .5, goal.y() + 2, goal.z() + .5);
    RailwayV30GameTests.use(h, player, new BlockPos(goal.x(), goal.y() - 1, goal.z()), goal.y());
    h.assertTrue(
        RailPlanner.get(player) == null && RailPlacement.count(player) == 52,
        "connection did not auto-clear or exact price changed");
    player.setPos(at.getX() + .5, at.getY() + 2, at.getZ() + .5);
    RailwayV30GameTests.use(h, player, at, at.getY() + .3);
    var selection = RailPlanner.get(player);
    h.assertTrue(selection != null, "cannot reselect after connection");
    RailwayData.get(h.getLevel()).remove(selection.anchor());
    h.assertTrue(RailPlanner.get(player) == null, "destroyed anchor left a stale selection");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 100)
  public static void cancelWorksInAirAndOnItemSwitchAndPreviewCanNeverOwnMaterials(
      GameTestHelper h) {
    var p = RailwayV30GameTests.plan(PowerV20GameTests.site(h, 256), 0, 0, -8, 0);
    RailwayV30GameTests.prepare(h, p);
    var player = RailwayV30GameTests.player(h, p, true);
    var edge = RailwayV30GameTests.placed(h, p, player);
    var at = new BlockPos(p.curve.end.x(), p.curve.end.y(), p.curve.end.z());
    player.setPos(at.getCenter().add(0, 2, 0));
    RailwayV30GameTests.use(h, player, at, at.getY() + .3);
    player.setShiftKeyDown(true);
    player.getMainHandItem().getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
    player.setShiftKeyDown(false);
    h.assertTrue(RailPlanner.get(player) == null, "air cancellation failed");
    RailwayV30GameTests.use(h, player, at, at.getY() + .3);
    player.getInventory().selected = 1;
    RailPlanner.tick(player);
    h.assertTrue(RailPlanner.get(player) == null, "held item switch retained selection");
    player.getInventory().selected = 0;
    var invalid =
        new RailPlan(
            RailCurve.preview(
                p.curve.end,
                new RailEnd(
                    p.curve.end.x() + 4,
                    p.curve.end.y(),
                    p.curve.end.z() - 4,
                    new RailHeading(4))));
    int held = RailPlacement.count(player);
    h.assertTrue(
        RailPlacement.place(player, invalid).equals("geometry")
            && RailPlacement.count(player) == held,
        "red preview was published as a real paid edge");
    h.assertTrue(
        RailwayData.get(h.getLevel()).segment(edge) != null,
        "preview cancellation removed real rail");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 100)
  public static void effectPacketIsBoundedAndCarriesPhysicalImpactNotResources(GameTestHelper h) {
    var from = new Vec3(1, 70, 2);
    var effect =
        new CombatEffects.Effect(
            from,
            List.of(new CombatEffects.Impact(new Vec3(4, 70, 2), new Vec3(-1, 0, 0), true)),
            1,
            99);
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      CombatEffects.Effect.CODEC.encode(buffer, effect);
      var decoded = CombatEffects.Effect.CODEC.decode(buffer);
      h.assertTrue(decoded.equals(effect), "confirmed impact packet changed geometry");
    } finally {
      buffer.release();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 80)
  public static void grenadeDetonatesOnActualGroundAndMobContacts(GameTestHelper h) {
    var l = h.getLevel();
    var at = h.absoluteVec(new Vec3(3, 30, 3));
    l.setBlockAndUpdate(BlockPos.containing(at).below(3), Blocks.STONE.defaultBlockState());
    var falling = grenade(h, at, new Vec3(0, -.8, 0));
    for (int n = 0; n < 8 && !falling.isRemoved(); n++) falling.tick();
    h.assertTrue(falling.isRemoved(), "actual ground collision did not detonate");
    var target = enemy(l, at.add(4, 0, 0), 1, true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    l.getEntitiesOfClass(Husk.class, target.getBoundingBox()).contains(target),
                    "waiting for real mob tracking"))
        .thenExecute(
            () -> {
              var shot =
                  grenade(h, target.getBoundingBox().getCenter().add(-2, 0, 0), new Vec3(.8, 0, 0));
              for (int n = 0; n < 8 && !shot.isRemoved(); n++) shot.tick();
              h.assertTrue(
                  shot.isRemoved() && !target.isAlive(),
                  "actual mob collision did not commit grenade blast");
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 50)
  public static void unloadedProjectilePathNeverBecomesAnImpactOrForcesChunks(GameTestHelper h) {
    var l = h.getLevel();
    var at = PowerV20GameTests.site(h, 257);
    l.getChunkAt(at);
    var start = at.getCenter().add(0, 30, 0);
    var end = start.add(128, 0, 0);
    h.assertTrue(!Combat.loadedRay(l, start, end), "fixture must cross unloaded chunks");
    var shot = grenade(h, start, new Vec3(128, 0, 0));
    for (int n = 0; n < 110; n++) {
      shot.tick();
      h.assertTrue(
          !shot.isRemoved() && shot.position().equals(start),
          "unloaded path produced an explosion or movement");
    }
    h.assertTrue(
        !l.hasChunkAt(BlockPos.containing(end)), "projectile force-loaded its destination");
    for (int n = 110; n < 401; n++) shot.tick();
    h.assertTrue(shot.isRemoved(), "unavailable projectile exceeded bounded lifetime");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 50)
  public static void corruptProjectileAgeCannotCreateImmortalPaidShots(GameTestHelper h) {
    var original = grenade(h, h.absoluteVec(new Vec3(3, 20, 3)), Vec3.ZERO);
    var saved = original.saveWithoutId(new CompoundTag());
    original.discard();
    saved.putInt("Age", Integer.MIN_VALUE);
    var copy = new CombatProjectile(Content.COMBAT_PROJECTILE.get(), h.getLevel());
    copy.load(saved);
    copy.tick();
    h.assertTrue(copy.isRemoved(), "corrupt negative age created an immortal shot");
    h.succeed();
  }

  static final class Disk extends net.minecraft.world.level.saveddata.SavedData {
    CompoundTag data = new CompoundTag();

    static Disk load(CompoundTag n, HolderLookup.Provider r) {
      var d = new Disk();
      d.data = n.getCompound("Checkpoint");
      return d;
    }

    public CompoundTag save(CompoundTag n, HolderLookup.Provider r) {
      n.put("Checkpoint", data);
      return n;
    }
  }

  @GameTest(template = "empty", batch = "post_v0301", timeoutTicks = 550)
  public static void multiTargetOwnersRetainExactFuelEnergyAndPrimingAcrossIndependentJvm(
      GameTestHelper h) {
    var l = h.getLevel();
    var disk =
        l.getDataStorage()
            .computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(Disk::new, Disk::load),
                "factorycraft_v0301_checkpoint");
    if (!disk.data.getBoolean("Ready")) {
      h.succeedWhen(
          () -> {
            h.assertTrue(
                finishedFlames.containsKey(l) && finishedLasers.containsKey(l),
                "waiting for actual finite multi-target owners");
            for (var entry :
                Map.of("Flame", finishedFlames.get(l), "Laser", finishedLasers.get(l)).entrySet()) {
              var be = (TurretBlockEntity) l.getBlockEntity(entry.getValue());
              disk.data.putLong(entry.getKey() + "Pos", entry.getValue().asLong());
              disk.data.put(entry.getKey() + "State", be.saveWithoutMetadata(l.registryAccess()));
            }
            disk.data.putBoolean("Ready", true);
            disk.setDirty();
            System.out.println(
                "FACTORYCRAFT_V0301_DISK: SEEDED real post-combat owners, exact"
                    + " oil/FE/priming/remainders");
          });
      return;
    }
    for (String kind : List.of("Flame", "Laser")) {
      var pos = BlockPos.of(disk.data.getLong(kind + "Pos"));
      l.getChunkAt(pos);
      var be = (TurretBlockEntity) l.getBlockEntity(pos);
      var expected = disk.data.getCompound(kind + "State");
      var actual = be.saveWithoutMetadata(l.registryAccess());
      for (String key :
          List.of(
              "Energy",
              "Oil",
              "PipeOil",
              "FlameActive",
              "IdleDebt",
              "IdleRemainder",
              "ShotRemainder",
              "PaidShots"))
        h.assertTrue(
            Objects.equals(expected.get(key), actual.get(key)),
            kind + " post-combat checkpoint changed " + key);
    }
    System.out.println(
        "FACTORYCRAFT_V0301_DISK: VERIFIED exact post-combat owner resource/phase state across"
            + " JVM");
    h.succeed();
  }
}
