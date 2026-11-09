package factorycraft.circuits;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Coordinates never own a remote node: UUID and both physical port identities are mandatory. */
public record CircuitLink(BlockPos peer, UUID identity, int localPort, int remotePort, int color) {
  public CircuitLink {
    peer = peer.immutable();
    if (localPort < 0
        || localPort > 1
        || remotePort < 0
        || remotePort > 1
        || color < 0
        || color > 1) throw new IllegalArgumentException("Invalid circuit connector");
  }

  public CompoundTag save() {
    var t = new CompoundTag();
    t.putLong("Peer", peer.asLong());
    t.putUUID("Identity", identity);
    t.putInt("Local", localPort);
    t.putInt("Remote", remotePort);
    t.putInt("Color", color);
    return t;
  }

  public static CircuitLink load(CompoundTag t) {
    try {
      return new CircuitLink(
          BlockPos.of(t.getLong("Peer")),
          t.getUUID("Identity"),
          t.getInt("Local"),
          t.getInt("Remote"),
          t.getInt("Color"));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
