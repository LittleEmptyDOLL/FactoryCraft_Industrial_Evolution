package factorycraft.trains;

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
public final class TrainNetwork {
  public record Drive(int entity, int throttle, boolean brake, int branch)
      implements CustomPacketPayload {
    public static final Type<Drive> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "train_drive"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Drive> CODEC =
        CustomPacketPayload.codec(
            (p, b) -> {
              b.writeVarInt(p.entity);
              b.writeByte(p.throttle);
              b.writeBoolean(p.brake);
              b.writeByte(p.branch);
            },
            b -> {
              var p = new Drive(b.readVarInt(), b.readByte(), b.readBoolean(), b.readByte());
              if (Math.abs(p.throttle) > 1 || Math.abs(p.branch) > 1)
                throw new IllegalArgumentException("Train intent");
              return p;
            });

    public Type<Drive> type() {
      return TYPE;
    }
  }

  public static boolean control(
      ServerPlayer p, StockEntity entity, int throttle, boolean brake, int branch) {
    if (Math.abs(throttle) > 1
        || Math.abs(branch) > 1
        || p.getVehicle() != entity
        || entity.kind() != factorycraft.simulation.trains.StockSpec.LOCOMOTIVE) return false;
    var d = TrainData.get(p.serverLevel());
    var t = d.owner(entity.getUUID());
    var car = d.car(entity.getUUID());
    if (t == null
        || car == null
        || !TrainWorld.mayUse(t, p)
        || p.serverLevel().getEntity(entity.getUUID()) != entity
        || t.provisional) return false;
    if (t.automation.automatic) {
      if (throttle == 0 && !brake && branch == 0) return true;
      TrainAutomation.manual(d, t);
    }
    t.driver = p.getUUID();
    t.lastInput = p.serverLevel().getGameTime();
    t.desired = throttle * (car.reversed ? -1 : 1);
    if (t.branch != branch)
      t.path.discardUnoccupied(
          t.rear(), t.front(), t.speed == 0 && t.desired != 0 ? t.desired : t.direction);
    t.brake = brake;
    t.branch = branch;
    d.active.add(t.id);
    return true;
  }

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent e) {
    e.registrar("0.31.0")
        .playToServer(
            Drive.TYPE,
            Drive.CODEC,
            (packet, context) ->
                context.enqueueWork(
                    () -> {
                      if (!(context.player() instanceof ServerPlayer p)
                          || !(p.getVehicle() instanceof StockEntity entity)
                          || entity.getId() != packet.entity
                          || entity.kind() != factorycraft.simulation.trains.StockSpec.LOCOMOTIVE)
                        return;
                      control(p, entity, packet.throttle, packet.brake, packet.branch);
                    }));
  }
}
