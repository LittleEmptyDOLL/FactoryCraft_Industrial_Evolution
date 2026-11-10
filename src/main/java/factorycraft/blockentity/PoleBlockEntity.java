package factorycraft.blockentity;

import factorycraft.energy.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.simulation.power.PoleSpec;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.*;

/** One assembled pole owns its identity and persistent copper edges, never FE. */
public final class PoleBlockEntity extends FactoryBlockEntity
    implements AssemblyOwner, net.minecraft.world.MenuProvider {
  private static final IItemHandler EMPTY = new ItemStackHandler(0);
  private final IndustrialAssembly assembly;
  private final Map<BlockPos, PowerWires.Link> wires = new LinkedHashMap<>();
  private final Collection<PowerWires.Link> wireView =
      Collections.unmodifiableCollection(wires.values());
  private boolean loaded, pendingAuto;
  private boolean visualDirty = true, visualReady;
  private long wireRevision;

  public long wireRevision() {
    return wireRevision;
  }

  public PoleBlockEntity(BlockPos p, BlockState s) {
    super(Content.POLE.get(), p, s);
    assembly = new IndustrialAssembly(this);
  }

  public IndustrialAssembly assembly() {
    return assembly;
  }

  public PoleSpec spec() {
    return ((factorycraft.blocks.FactoryBlock) getBlockState().getBlock()).kind.poleType();
  }

  public boolean loaded() {
    return loaded && !isRemoved();
  }

  public void invalidateVisual() {
    visualDirty = true;
  }

  public boolean visualAvailable() {
    if (level == null || !level.isClientSide) return available();
    if (!loaded() || !assembly.formed()) return false;
    if (!visualDirty) return visualReady;
    visualDirty = false;
    visualReady = false;
    if (!level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this)
      return false;
    for (var pos : assembly.positions())
      if (!level.hasChunkAt(pos)
          || !(level.getBlockEntity(pos) instanceof CasingBlockEntity part)
          || !part.loaded()
          || !part.boundTo(worldPosition, assembly.identity())) return false;
    visualReady = true;
    return true;
  }

  public boolean available() {
    return loaded() && assembly.ready();
  }

  public Collection<PowerWires.Link> links() {
    return wireView;
  }

  public PowerWires.Link linkAt(BlockPos pos) {
    return wires.get(pos);
  }

  public boolean hasLink(PoleBlockEntity other) {
    var l = wires.get(other.getBlockPos());
    return l != null && other.assembly().owns(l.identity());
  }

  public void putLink(PoleBlockEntity other) {
    var l = new PowerWires.Link(other.getBlockPos().immutable(), other.assembly().identity());
    if (!l.equals(wires.put(l.pos(), l))) {
      PowerWires.reference(this, l.pos(), true);
      wireRevision++;
      sync();
    }
  }

  public void eraseLink(BlockPos p) {
    if (wires.remove(p) != null) {
      PowerWires.reference(this, p, false);
      wireRevision++;
      sync();
    }
  }

  public void requestAutoConnections() {
    pendingAuto = true;
    setChanged();
    PowerGrid.refreshPole(this);
  }

  public void completeAutoConnections() {
    if (pendingAuto && available()) {
      pendingAuto = false;
      setChanged();
      PowerWires.autoConnect(this);
    }
  }

  public void showInfo(Player p) {
    showInfo(p, getBlockPos());
  }

  public void showInfo(Player p, BlockPos physical) {
    MachineInteraction.open(this, p, physical);
  }

  public Component getDisplayName() {
    return getBlockState().getBlock().getName();
  }

  public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
      int id, net.minecraft.world.entity.player.Inventory inventory, Player p) {
    var data =
        new net.minecraft.world.inventory.ContainerData() {
          public int getCount() {
            return 6;
          }

          public void set(int i, int v) {}

          public int get(int i) {
            var n = PowerGrid.info(PoleBlockEntity.this);
            return switch (i) {
              case 0 -> links().size();
              case 1 -> n.available() ? 1 : 0;
              case 2 -> n.poles();
              case 3 -> n.sources();
              case 4 -> n.consumers();
              case 5 ->
                  (int)
                      links().stream()
                          .filter(
                              l -> {
                                var b = PowerWires.at(level, l.pos());
                                return b != null && PowerGrid.hasEdge(PoleBlockEntity.this, b);
                              })
                          .count();
              default -> 0;
            };
          }
        };
    return new PoleMenu(id, inventory, this, data);
  }

  public IItemHandler inventory() {
    return EMPTY;
  }

  public void onLoad() {
    super.onLoad();
    loaded = true;
    PoleIndex.add(this);
    PowerWires.track(this);
    if (!level.isClientSide) {
      assembly.invalidate();
      PowerGrid.refreshPole(this);
    }
  }

  private void detach() {
    PowerWires.untrack(this);
    loaded = false;
    PoleIndex.remove(this);
    if (level != null && !level.isClientSide) {
      assembly.invalidate();
      PowerGrid.remove(this);
    }
  }

  public void setRemoved() {
    if (level != null
        && !level.isClientSide
        && level.hasChunkAt(worldPosition)
        && level.getBlockState(worldPosition).getBlock() != getBlockState().getBlock())
      PowerWires.clear(this);
    detach();
    super.setRemoved();
  }

  public void onChunkUnloaded() {
    detach();
    super.onChunkUnloaded();
  }

  public void dropContents() {
    if (level != null && !level.isClientSide) {
      PowerWires.clear(this);
      assembly.removeWhole(true);
    }
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.saveAdditional(tag, r);
    assembly.save(tag);
    var t = new CompoundTag();
    t.putInt("Schema", 1);
    t.putBoolean("AutoPending", pendingAuto);
    var links = new ListTag();
    for (var l : wires.values()) {
      var e = new CompoundTag();
      e.putLong("Pos", l.pos().asLong());
      e.putUUID("Identity", l.identity());
      links.add(e);
    }
    t.put("Links", links);
    tag.put("ElectricPole", t);
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
    boolean restore = level != null && !level.isClientSide && loaded();
    if (restore) {
      PowerWires.untrack(this);
      PowerGrid.remove(this);
    }
    super.loadAdditional(tag, r);
    assembly.load(tag);
    var t = tag.getCompound("ElectricPole");
    pendingAuto = t.getBoolean("AutoPending");
    visualDirty = true;
    wireRevision++;
    wires.clear();
    for (var raw : t.getList("Links", Tag.TAG_COMPOUND)) {
      var e = (CompoundTag) raw;
      if (e.hasUUID("Identity")) {
        var p = BlockPos.of(e.getLong("Pos"));
        wires.put(p, new PowerWires.Link(p, e.getUUID("Identity")));
      }
    }
    if (restore) {
      PowerWires.track(this);
      PowerGrid.refreshPole(this);
    }
  }
}
