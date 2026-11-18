package factorycraft.combat;

import factorycraft.FactoryCraft;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.*;

/** Nonowning, bounded S2C confirmation of real paid shots. Never requests damage from a client. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class CombatEffects {
  private static final DeferredRegister<SoundEvent> SOUNDS =
      DeferredRegister.create(Registries.SOUND_EVENT, FactoryCraft.ID);
  public static final DeferredHolder<SoundEvent, SoundEvent> GUN = sound("gun_shot"),
      LASER = sound("laser_shot"),
      FLAME = sound("flame_shot");

  private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
    return SOUNDS.register(
        name,
        () ->
            SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, name), 40));
  }

  public static void register(IEventBus bus) {
    SOUNDS.register(bus);
  }

  public record Impact(Vec3 end, Vec3 normal, boolean hit) {}

  public record Effect(Vec3 from, List<Impact> impacts, int kind, long tick)
      implements CustomPacketPayload {
    public static final Type<Effect> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "combat_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Effect> CODEC =
        CustomPacketPayload.codec(
            (p, b) -> {
              b.writeDouble(p.from.x);
              b.writeDouble(p.from.y);
              b.writeDouble(p.from.z);
              b.writeVarInt(p.kind);
              b.writeLong(p.tick);
              b.writeVarInt(p.impacts.size());
              for (var i : p.impacts) {
                b.writeDouble(i.end.x);
                b.writeDouble(i.end.y);
                b.writeDouble(i.end.z);
                b.writeFloat((float) i.normal.x);
                b.writeFloat((float) i.normal.y);
                b.writeFloat((float) i.normal.z);
                b.writeBoolean(i.hit);
              }
            },
            b -> {
              var from = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
              int kind = b.readVarInt();
              long tick = b.readLong();
              int count = b.readVarInt();
              if (count < 1 || count > 18 || kind < 0 || kind > 2 || !finite(from))
                throw new IllegalArgumentException("Invalid combat projection");
              var list = new ArrayList<Impact>(count);
              for (int n = 0; n < count; n++) {
                var end = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
                var normal = new Vec3(b.readFloat(), b.readFloat(), b.readFloat());
                boolean hit = b.readBoolean();
                if (!finite(end)
                    || !finite(normal)
                    || from.distanceToSqr(end) > 128 * 128
                    || normal.lengthSqr() > 1.01)
                  throw new IllegalArgumentException("Invalid impact");
                list.add(new Impact(end, normal, hit));
              }
              return new Effect(from, List.copyOf(list), kind, tick);
            });

    public Type<Effect> type() {
      return TYPE;
    }
  }

  private static boolean finite(Vec3 v) {
    return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
  }

  public static java.util.function.Consumer<Effect> client = e -> {};

  @SubscribeEvent
  public static void payloads(RegisterPayloadHandlersEvent e) {
    e.registrar("0.30.1")
        .playToClient(Effect.TYPE, Effect.CODEC, (p, c) -> c.enqueueWork(() -> client.accept(p)));
  }

  private static final Map<ServerLevel, Map<Long, Long>> recent = new WeakHashMap<>();

  public static void emit(
      ServerLevel l, Vec3 from, List<Impact> impacts, int kind, boolean automatic) {
    if (impacts.isEmpty()) return;
    var map = recent.computeIfAbsent(l, k -> new HashMap<>());
    long key = net.minecraft.core.BlockPos.containing(from).asLong();
    long now = l.getGameTime();
    int interval = kind == 0 ? 2 : 4;
    if (map.getOrDefault(key, Long.MIN_VALUE) != Long.MIN_VALUE && now - map.get(key) < interval)
      return;
    if (map.size() > 8192) map.clear();
    map.put(key, now);
    var sound = kind == 1 ? LASER : kind == 2 ? FLAME : GUN;
    l.playSound(
        null,
        from.x,
        from.y,
        from.z,
        sound.get(),
        automatic ? SoundSource.BLOCKS : SoundSource.PLAYERS,
        .75f,
        1);
    PacketDistributor.sendToPlayersNear(
        l,
        null,
        from.x,
        from.y,
        from.z,
        64,
        new Effect(from, List.copyOf(impacts.subList(0, Math.min(18, impacts.size()))), kind, now));
  }

  private CombatEffects() {}
}
