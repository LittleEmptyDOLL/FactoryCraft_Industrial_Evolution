package factorycraft.rail;

import factorycraft.FactoryCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = FactoryCraft.ID)
public final class RailNetwork {
  public record Selection(net.minecraft.nbt.CompoundTag tag) implements CustomPacketPayload {
    public static final Type<Selection> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "rail_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Selection> CODEC =
        CustomPacketPayload.codec(
            (p, b) -> b.writeNbt(p.tag),
            b ->
                new Selection(
                    (net.minecraft.nbt.CompoundTag)
                        b.readNbt(net.minecraft.nbt.NbtAccounter.create(4096))));

    public Type<Selection> type() {
      return TYPE;
    }
  }

  public record Edit(int action) implements CustomPacketPayload {
    public static final Type<Edit> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "rail_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC =
        CustomPacketPayload.codec((p, b) -> b.writeVarInt(p.action), b -> new Edit(b.readVarInt()));

    public Type<Edit> type() {
      return TYPE;
    }
  }

  public static java.util.function.Consumer<Selection> clientReceiver = p -> {};

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent event) {
    var r = event.registrar("0.30.1");
    r.playToClient(
        Selection.TYPE, Selection.CODEC, (p, c) -> c.enqueueWork(() -> clientReceiver.accept(p)));
    r.playToServer(
        Edit.TYPE,
        Edit.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof net.minecraft.server.level.ServerPlayer player
                      && p.action >= 0
                      && p.action <= 2
                      && (!player.isSpectator())
                      && (p.action == 2
                          || player.getMainHandItem().is(RailContent.RAIL.get())
                          || player.getOffhandItem().is(RailContent.RAIL.get())))
                    RailPlanner.edit(player, p.action);
                }));
  }

  public static void send(net.minecraft.server.level.ServerPlayer p, RailPlanner.Selection s) {
    var n = new net.minecraft.nbt.CompoundTag();
    if (s != null) {
      n.put("Start", RailCodec.end(s.start()));
      n.putInt("Goal", s.goal());
      n.putUUID("Anchor", s.anchor());
      n.putInt("Slot", s.slot());
    }
    PacketDistributor.sendToPlayer(p, new Selection(n));
  }

  private RailNetwork() {}
}
