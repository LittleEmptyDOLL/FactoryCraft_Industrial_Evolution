package factorycraft.circuits;

import factorycraft.FactoryCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = FactoryCraft.ID)
public final class CircuitNetwork {
  public record Operand(int menu, int value) implements CustomPacketPayload {
    public static final Type<Operand> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "circuit_operand"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Operand> CODEC =
        CustomPacketPayload.codec(
            (p, b) -> {
              b.writeVarInt(p.menu);
              b.writeInt(p.value);
            },
            b -> new Operand(b.readVarInt(), b.readInt()));

    public Type<Operand> type() {
      return TYPE;
    }
  }

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent e) {
    // Menu mode/readiness data slots changed in 0.18; require matching circuit clients.
    e.registrar("3")
        .playToServer(
            Operand.TYPE,
            Operand.CODEC,
            (p, c) ->
                c.enqueueWork(
                    () -> {
                      if (c.player() instanceof ServerPlayer player
                          && player.containerMenu instanceof CircuitMenu menu
                          && menu.containerId == p.menu) menu.operand(player, p.value);
                    }));
  }
}
