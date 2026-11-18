package factorycraft.combat;

import factorycraft.FactoryCraft;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Source-defined Factorio resistances as ordinary, synchronized NeoForge attributes.
 *
 * <p>Values are Factorio HP units (flat) and fractions of one (percent). Damage conversion
 * happens only in ArmorProtection. Nothing changes the native Minecraft armor attribute.
 */
public final class ArmorAttributes {
  public enum Kind {
    PHYSICAL("physical"),
    EXPLOSION("explosion"),
    FIRE("fire"),
    ACID("acid"),
    IMPACT("impact"),
    POISON("poison"),
    LASER("laser"),
    ELECTRIC("electric");

    public final String id;

    Kind(String id) {
      this.id = id;
    }
  }

  public static final DeferredRegister<Attribute> ATTRIBUTES =
      DeferredRegister.create(Registries.ATTRIBUTE, FactoryCraft.ID);
  private static final Map<Kind, DeferredHolder<Attribute, Attribute>> FLAT =
      new EnumMap<>(Kind.class);
  private static final Map<Kind, DeferredHolder<Attribute, Attribute>> PERCENT =
      new EnumMap<>(Kind.class);
  private static final Map<Kind, TagKey<DamageType>> DAMAGE_TAGS = new EnumMap<>(Kind.class);
  private static final Map<Kind, ResourceKey<DamageType>> DAMAGE_KEYS = new EnumMap<>(Kind.class);

  static {
    for (Kind kind : Kind.values()) {
      String flat = kind.id + "_flat_resistance";
      String percent = kind.id + "_percent_resistance";
      FLAT.put(
          kind,
          ATTRIBUTES.register(
              flat,
              () ->
                  new RangedAttribute(
                          "attribute.name.factorycraft." + flat, 0.0, 0.0, 100000.0)
                      .setSyncable(true)));
      PERCENT.put(
          kind,
          ATTRIBUTES.register(
              percent,
              () ->
                  new PercentageAttribute(
                          "attribute.name.factorycraft." + percent, 0.0, 0.0, 1.0)
                      .setSyncable(true)));
      DAMAGE_TAGS.put(
          kind,
          TagKey.create(
              Registries.DAMAGE_TYPE,
              ResourceLocation.fromNamespaceAndPath(
                  FactoryCraft.ID, "damage_types/" + kind.id)));
      DAMAGE_KEYS.put(
          kind,
          ResourceKey.create(
              Registries.DAMAGE_TYPE,
              ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, kind.id)));
    }
  }

  public static void register(IEventBus bus) {
    ATTRIBUTES.register(bus);
    bus.addListener(ArmorAttributes::attachToLiving);
  }

  private static void attachToLiving(EntityAttributeModificationEvent event) {
    for (var type : event.getTypes()) {
      for (Kind kind : Kind.values()) {
        if (!event.has(type, flat(kind))) event.add(type, flat(kind));
        if (!event.has(type, percent(kind))) event.add(type, percent(kind));
      }
    }
  }

  public static Holder<Attribute> flat(Kind kind) {
    return FLAT.get(kind);
  }

  public static Holder<Attribute> percent(Kind kind) {
    return PERCENT.get(kind);
  }

  /** Custom damage-type tags win over vanilla fallbacks and enable datapack integrations. */
  public static Kind classify(DamageSource source) {
    for (Kind kind : Kind.values())
      if (source.is(DAMAGE_TAGS.get(kind)) || source.is(DAMAGE_KEYS.get(kind))) return kind;

    if (source.is(DamageTypeTags.IS_EXPLOSION)) return Kind.EXPLOSION;
    if (source.is(DamageTypeTags.IS_FIRE)) return Kind.FIRE;
    if (source.is(DamageTypes.LIGHTNING_BOLT)) return Kind.ELECTRIC;
    if (source.is(DamageTypes.FLY_INTO_WALL)) return Kind.IMPACT;
    if (source.is(DamageTypeTags.IS_PROJECTILE)
        || source.is(DamageTypes.MOB_ATTACK)
        || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
        || source.is(DamageTypes.PLAYER_ATTACK)
        || source.is(DamageTypes.STING)) return Kind.PHYSICAL;
    // Falling, starvation, void, magic, poison and unspecified damage are deliberately
    // unaffected until an explicit datapack tag maps them to a Factorio damage category.
    return null;
  }

  private static void add(
      ItemAttributeModifiers.Builder builder, Kind kind, double flat, double percent) {
    if (flat > 0)
      builder.add(
          flat(kind),
          new AttributeModifier(
              ResourceLocation.fromNamespaceAndPath(
                  FactoryCraft.ID, "armor_" + kind.id + "_flat"),
              flat,
              AttributeModifier.Operation.ADD_VALUE),
          EquipmentSlotGroup.CHEST);
    if (percent > 0)
      builder.add(
          percent(kind),
          new AttributeModifier(
              ResourceLocation.fromNamespaceAndPath(
                  FactoryCraft.ID, "armor_" + kind.id + "_percent"),
              percent,
              AttributeModifier.Operation.ADD_VALUE),
          EquipmentSlotGroup.CHEST);
  }

  /** Original Factorio 2.1.24 + Space Age armor data (normal quality). */
  public static ItemAttributeModifiers armor(boolean heavy) {
    var builder = ItemAttributeModifiers.builder();
    if (heavy) {
      add(builder, Kind.PHYSICAL, 6, .30);
      add(builder, Kind.EXPLOSION, 20, .30);
      add(builder, Kind.FIRE, 0, .30);
      add(builder, Kind.ACID, 0, .40);
    } else {
      add(builder, Kind.PHYSICAL, 3, .20);
      add(builder, Kind.EXPLOSION, 2, .20);
      add(builder, Kind.FIRE, 0, .10);
      add(builder, Kind.ACID, 0, .20);
    }
    return builder.build();
  }

  private ArmorAttributes() {}
}
