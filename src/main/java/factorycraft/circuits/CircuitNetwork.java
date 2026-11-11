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

  public record Snapshot(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload {
    public static final Type<Snapshot> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "logic_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
        CustomPacketPayload.codec(
            (p, b) -> b.writeNbt(p.data),
            b ->
                new Snapshot(
                    (net.minecraft.nbt.CompoundTag)
                        b.readNbt(net.minecraft.nbt.NbtAccounter.create(65536))));

    public Type<Snapshot> type() {
      return TYPE;
    }
  }

  public record Configure(int menu, java.util.UUID identity, net.minecraft.nbt.CompoundTag data)
      implements CustomPacketPayload {
    public static final Type<Configure> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, "logic_configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Configure> CODEC =
        CustomPacketPayload.codec(
            (p, b) -> {
              b.writeVarInt(p.menu);
              b.writeUUID(p.identity);
              b.writeNbt(p.data);
            },
            b ->
                new Configure(
                    b.readVarInt(),
                    b.readUUID(),
                    (net.minecraft.nbt.CompoundTag)
                        b.readNbt(net.minecraft.nbt.NbtAccounter.create(65536))));

    public Type<Configure> type() {
      return TYPE;
    }
  }

  public static java.util.function.Consumer<Snapshot> clientReceiver = p -> {};

  public static net.minecraft.nbt.CompoundTag menuState(CircuitAttachment n) {
    return menuState(n, 0);
  }

  public static net.minecraft.nbt.CompoundTag menuState(CircuitAttachment n, int page) {
    var t = new net.minecraft.nbt.CompoundTag();
    t.put("Settings", n.settings().save());
    t.put("Program", SignalProgramData.save(n.program()));
    t.putInt("InputCount", n.input.size());
    t.putInt("OutputCount", n.output.size());
    t.put("Input", preview(n.input, page));
    t.put("Output", preview(n.output, page));
    t.putInt("Page", page);
    features(n, t);
    t.putBoolean("Ready", n.inputReady);
    t.putBoolean("Allowed", n.permitted);
    t.putBoolean("Invalid", n.invalid());
    t.putBoolean("Limited", n.overCapacity);
    t.putString(
        "Device",
        net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(n.owner().getBlockState().getBlock())
            .toString());
    if (n.owner() instanceof factorycraft.blockentity.LabBlockEntity l)
      t.putBoolean("ResearchControlled", CircuitDevices.researchControlled(n.level(), l.scope()));
    return t;
  }

  private static net.minecraft.nbt.ListTag preview(
      factorycraft.simulation.circuits.SignalSet set, int page) {
    var t = new net.minecraft.nbt.ListTag();
    set.values().entrySet().stream()
        .sorted(java.util.Map.Entry.comparingByKey())
        .skip((long) page * 6)
        .limit(6)
        .forEach(
            e -> {
              var row = SignalProgramData.key(e.getKey());
              row.putInt("Value", e.getValue());
              t.add(row);
            });
    return t;
  }

  public static void features(CircuitAttachment n, net.minecraft.nbt.CompoundTag t) {
    var b = n.owner();
    t.putBoolean("Combinator", n.combinator());
    t.putBoolean("CanRead", CircuitDevices.readable(b));
    t.putBoolean("CanState", CircuitDevices.stateful(b));
    t.putBoolean("CanGate", CircuitDevices.gateable(b));
    t.putBoolean("CanFilter", b instanceof factorycraft.blockentity.InserterBlockEntity);
    t.putBoolean(
        "CanRecipe",
        b instanceof factorycraft.blockentity.MachineBlockEntity m
            && (m.kind() == factorycraft.blocks.FactoryKind.ASSEMBLER
                || m.kind() == factorycraft.blocks.FactoryKind.ADVANCED_ASSEMBLER
                || m.kind() == factorycraft.blocks.FactoryKind.REFINERY
                || m.kind() == factorycraft.blocks.FactoryKind.CHEMICAL_PLANT));
    t.putBoolean("CanIngredients", b instanceof factorycraft.blockentity.MachineBlockEntity);
    t.putBoolean("CanResearch", b instanceof factorycraft.blockentity.LabBlockEntity);
    t.putBoolean("CanPulse", b instanceof factorycraft.blockentity.BeltBlockEntity);
  }

  public static net.minecraft.nbt.CompoundTag visual(CircuitAttachment n) {
    var t = new net.minecraft.nbt.CompoundTag();
    t.putLong("Anchor", n.pos().asLong());
    if (n.identity() != null) t.putUUID("Identity", n.identity());
    t.putBoolean("Available", n.active);
    var points = new net.minecraft.nbt.ListTag();
    for (int p = 0; p < n.ports(); p++) {
      var row = new net.minecraft.nbt.CompoundTag();
      var point = n.point(p, 0);
      row.putDouble("X", point.x);
      row.putDouble("Y", point.y);
      row.putDouble("Z", point.z);
      points.add(row);
    }
    t.put("Points", points);
    t.putLongArray(
        "Cells", n.cells().stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
    var links = new net.minecraft.nbt.ListTag();
    for (var link : n.links) links.add(link.save());
    t.put("Links", links);
    return t;
  }

  public static void changed(CircuitAttachment n) {
    if (!(n.level() instanceof net.minecraft.server.level.ServerLevel level)
        || n.identity() == null) return;
    var packet = new Snapshot(visual(n));
    var chunks = new java.util.HashSet<Long>();
    for (var cell : n.cells())
      chunks.add(net.minecraft.world.level.ChunkPos.asLong(cell.getX() >> 4, cell.getZ() >> 4));
    for (long chunk : chunks)
      net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(
          level, new net.minecraft.world.level.ChunkPos(chunk), packet);
  }

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent e) {
    // Typed maps, direct contacts and structured menus require matching 0.22 clients.
    e.registrar("4")
        .playToClient(
            Snapshot.TYPE, Snapshot.CODEC, (p, c) -> c.enqueueWork(() -> clientReceiver.accept(p)))
        .playToServer(
            Configure.TYPE,
            Configure.CODEC,
            (p, c) ->
                c.enqueueWork(
                    () -> {
                      if (c.player() instanceof ServerPlayer player
                          && player.containerMenu instanceof LogicMenu menu
                          && menu.containerId == p.menu) {
                        boolean ok = menu.apply(player, p.identity, p.data);
                        var reply = menuState(menu.owner, menu.signalPage);
                        reply.putInt("Menu", menu.containerId);
                        reply.putBoolean("Accepted", ok);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            player, new Snapshot(reply));
                      }
                    }))
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
