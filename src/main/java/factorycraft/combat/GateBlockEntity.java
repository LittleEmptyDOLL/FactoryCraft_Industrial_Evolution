package factorycraft.combat;

import factorycraft.registry.Content;
import factorycraft.research.ResearchTeams;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Identity only: gates do not store or copy industrial resources. */
public final class GateBlockEntity extends BlockEntity {
  private java.util.UUID owner;
  private String scope = "";

  public GateBlockEntity(BlockPos p, BlockState s) {
    super(Content.GATE_ENTITY.get(), p, s);
  }

  public void bind(Player p) {
    owner = p.getUUID();
    scope = ResearchTeams.resolve(p).orElse("unavailable");
    setChanged();
  }

  public boolean friendly(Player p) {
    return owner == null || owner.equals(p.getUUID()) || ResearchTeams.member(p, scope);
  }

  protected void saveAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.saveAdditional(n, r);
    if (owner != null) n.putUUID("Owner", owner);
    n.putString("Force", scope);
  }

  protected void loadAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.loadAdditional(n, r);
    owner = n.hasUUID("Owner") ? n.getUUID("Owner") : null;
    scope = n.getString("Force");
  }
}
