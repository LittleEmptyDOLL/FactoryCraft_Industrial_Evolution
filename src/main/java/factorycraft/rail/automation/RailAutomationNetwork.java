package factorycraft.rail.automation;

import factorycraft.FactoryCraft;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = FactoryCraft.ID)
public final class RailAutomationNetwork {
  public record Edit(int menu, String text) implements CustomPacketPayload {
    public static final Type<Edit> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "rail_automation_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC =
        CustomPacketPayload.codec(
            (p, b) -> {
              b.writeVarInt(p.menu);
              b.writeUtf(p.text, 16384);
            },
            b -> new Edit(b.readVarInt(), b.readUtf(16384)));

    public Type<Edit> type() {
      return TYPE;
    }
  }

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent e) {
    e.registrar("0.32.0")
        .playToServer(
            Edit.TYPE,
            Edit.CODEC,
            (packet, context) ->
                context.enqueueWork(
                    () -> {
                      if (!(context.player() instanceof ServerPlayer p)
                          || !(p.containerMenu instanceof RailAutomationMenu menu)
                          || menu.containerId != packet.menu) return;
                      try {
                        menu.accepted = menu.edit(p, TagParser.parseTag(packet.text)) ? 1 : -1;
                      } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
                      }
                    }));
  }

  private RailAutomationNetwork() {}
}
