package factorycraft.research;

import factorycraft.FactoryCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = FactoryCraft.ID)
public final class ResearchNetwork {
  public record Select(int menu, ResourceLocation technology) implements CustomPacketPayload {
    public static final Type<Select> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "research_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC =
        CustomPacketPayload.codec(
            (p, b) -> {
              b.writeVarInt(p.menu);
              b.writeResourceLocation(p.technology);
            },
            b -> new Select(b.readVarInt(), b.readResourceLocation()));

    public Type<Select> type() {
      return TYPE;
    }
  }

  public record State(int menu, CompoundTag data) implements CustomPacketPayload {
    public static final Type<State> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "research_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
        CustomPacketPayload.codec(
            (p, b) -> {
              b.writeVarInt(p.menu);
              b.writeNbt(p.data);
            },
            b -> new State(b.readVarInt(), b.readNbt()));

    public Type<State> type() {
      return TYPE;
    }
  }

  /** Client snapshot cache contains no simulation or client-only class linkage. */
  public static State clientState = new State(-1, new CompoundTag());

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent event) {
    var r = event.registrar("1");
    r.playToClient(State.TYPE, State.CODEC, (p, c) -> c.enqueueWork(() -> clientState = p));
    r.playToServer(
        Select.TYPE,
        Select.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) select(player, p);
                }));
  }

  public static boolean select(ServerPlayer player, Select p) {
    if (!(player.containerMenu instanceof ResearchMenu menu)
        || menu.containerId != p.menu
        || !menu.stillValid(player)
        || player.isSpectator()) return false;
    var e = ResearchCatalog.get(player.level()).entry(p.technology);
    if (e == null || e.recipe().sandboxOnly()) return false;
    var data = ResearchData.get(player.server);
    boolean selected = data.team(menu.lab.scope()).select(e.logic());
    if (selected) data.setDirty();
    return selected;
  }

  private ResearchNetwork() {}
}
