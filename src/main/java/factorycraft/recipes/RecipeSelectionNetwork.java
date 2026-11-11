package factorycraft.recipes;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.MachineBlockEntity;
import factorycraft.petroleum.PetroleumMenu;
import factorycraft.production.FactoryMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Menu-scoped ID protocol; client list indices are never trusted. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class RecipeSelectionNetwork {
  public record Request(int menu, boolean select, ResourceLocation recipe)
      implements CustomPacketPayload {
    public static final Type<Request> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "recipe_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        CustomPacketPayload.codec(
            (p, b) -> {
              b.writeVarInt(p.menu);
              b.writeBoolean(p.select);
              b.writeBoolean(p.recipe != null);
              if (p.recipe != null) b.writeResourceLocation(p.recipe);
            },
            b ->
                new Request(
                    b.readVarInt(),
                    b.readBoolean(),
                    b.readBoolean() ? b.readResourceLocation() : null));

    public Type<Request> type() {
      return TYPE;
    }
  }

  public record State(int menu, CompoundTag data) implements CustomPacketPayload {
    public static final Type<State> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "recipe_state"));
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

  public static volatile State clientState = new State(-1, new CompoundTag());
  public static long generation;

  @SubscribeEvent
  public static void reload(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
    generation++;
  }

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent event) {
    var r = event.registrar("1");
    r.playToClient(State.TYPE, State.CODEC, (p, c) -> c.enqueueWork(() -> clientState = p));
    r.playToServer(
        Request.TYPE,
        Request.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) handle(player, p);
                }));
  }

  public static boolean handle(ServerPlayer player, Request packet) {
    var menu = player.containerMenu;
    MachineBlockEntity m;
    if (menu.containerId != packet.menu || player.isSpectator() || !menu.stillValid(player))
      return false;
    if (menu instanceof FactoryMenu f) m = f.machine;
    else if (menu instanceof PetroleumMenu p) m = p.machine;
    else return false;
    if (!m.loaded()
        || !factorycraft.multiblock.AssemblyAccess.ready(m)
        || !player.level().mayInteract(player, m.getBlockPos())
        || !m.kind().processing()) return false;
    boolean accepted = !packet.select || m.selectRecipe(packet.recipe);
    var data = new CompoundTag();
    data.putBoolean("Accepted", accepted);
    data.putString("Selected", m.selectedRecipeId());
    if (m.petroleum() != null)
      for (var h : m.petroleum().recipes())
        data.putBoolean(h.id().toString(), m.recipeAllowed(h.id()));
    else for (var h : m.recipes()) data.putBoolean(h.id().toString(), m.recipeAllowed(h.id()));
    PacketDistributor.sendToPlayer(player, new State(menu.containerId, data));
    return accepted;
  }

  private RecipeSelectionNetwork() {}
}
