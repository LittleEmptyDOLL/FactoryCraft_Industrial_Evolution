package factorycraft.rail.automation;

import factorycraft.circuits.*;
import factorycraft.trains.RailAutomationData;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Identity and circuit metadata only. Device configuration is owned once by RailAutomationData. */
public final class TracksideBlockEntity extends BlockEntity {
  public UUID identity = UUID.randomUUID();
  private CircuitAttachment circuit;
  public CompoundTag visual = new CompoundTag();

  public TracksideBlockEntity(BlockPos p, BlockState s) {
    super(TracksideContent.ENTITY.get(), p, s);
  }

  public TracksideKind kind() {
    return ((TracksideBlock) getBlockState().getBlock()).kind;
  }

  public List<BlockPos> cells() {
    var out = new ArrayList<BlockPos>();
    for (int i = 0; i < kind().parts(); i++)
      out.add(TracksideBlock.offset(worldPosition, kind(), i));
    return List.copyOf(out);
  }

  public boolean ready() {
    if (isRemoved() || !(level instanceof ServerLevel l)) return false;
    var data = RailAutomationData.peek(l);
    var device = data == null ? null : data.device(identity);
    if (device == null || !device.anchor.equals(worldPosition) || device.kind != kind())
      return false;
    for (int i = 0; i < kind().parts(); i++) {
      var p = TracksideBlock.offset(worldPosition, kind(), i);
      var chunk = l.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
      if (chunk == null
          || !l.shouldTickBlocksAt(p)
          || chunk.getBlockState(p).getBlock() != getBlockState().getBlock()
          || chunk.getBlockState(p).getValue(TracksideBlock.PART) != i) return false;
    }
    return factorycraft.fluids.FluidConnections.at(level, worldPosition) == this;
  }

  public boolean mayUse(net.minecraft.world.entity.player.Player p) {
    if (!(p instanceof net.minecraft.server.level.ServerPlayer player)) return false;
    var data = RailAutomationData.peek(player.serverLevel());
    var d = data == null ? null : data.device(identity);
    return d != null && d.mayUse(player);
  }

  public CircuitAttachment circuit(boolean create) {
    if (circuit == null && create) circuit = new CircuitAttachment(this);
    return circuit;
  }

  public void onLoad() {
    super.onLoad();
    if (level instanceof ServerLevel l) {
      RailAutomationData.get(l).deviceLoaded(identity);
      if (circuit != null) CircuitSimulation.add(circuit);
    }
  }

  public void setRemoved() {
    if (circuit != null && level instanceof ServerLevel) CircuitSimulation.remove(circuit);
    super.setRemoved();
  }

  public void onChunkUnloaded() {
    if (circuit != null && level instanceof ServerLevel) CircuitSimulation.remove(circuit);
    super.onChunkUnloaded();
  }

  public void sync() {
    setChanged();
    if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
  }

  protected void saveAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.saveAdditional(n, r);
    n.putUUID("Device", identity);
    if (circuit != null && (circuit.configured || !circuit.links.isEmpty()))
      n.put("DirectCircuit", circuit.save());
  }

  protected void loadAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.loadAdditional(n, r);
    if (n.hasUUID("Device")) identity = n.getUUID("Device");
    if (n.contains("DirectCircuit")) {
      circuit = new CircuitAttachment(this);
      circuit.load(n.getCompound("DirectCircuit"));
    }
    visual = n.getCompound("Visual").copy();
    if (level != null && level.isClientSide)
      for (var cell : cells())
        if (level.hasChunkAt(cell)) {
          var state = level.getBlockState(cell);
          level.sendBlockUpdated(cell, state, state, 8);
        }
  }

  public CompoundTag getUpdateTag(HolderLookup.Provider r) {
    var n = super.getUpdateTag(r);
    n.putUUID("Device", identity);
    if (level instanceof ServerLevel l) {
      var data = RailAutomationData.peek(l);
      if (data != null) n.put("Visual", data.visual(identity));
    }
    return n;
  }

  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
