package factorycraft.multiblock;

import factorycraft.blockentity.*;
import factorycraft.fluids.FluidConnections;
import factorycraft.simulation.multiblock.CuboidLayout;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;

/**
 * Generic automatic assembly. Parts own identity only; kernels/inventories remain in one
 * controller.
 */
public final class IndustrialAssembly {
  public static final CuboidLayout LAYOUT = MachineProfile.FURNACE.layout;
  public static final int UNFORMED = 0, WAITING = 1, READY = 2;
  private final AssemblyOwner owner;
  private final FactoryBlockEntity controller;
  private final MachineProfile profile;
  private UUID identity;
  private Direction facing;
  private boolean formed, ready, forming, mirrored, dirty = true;
  private List<BlockPos> parts;

  public IndustrialAssembly(AssemblyOwner owner) {
    this.owner = owner;
    controller = owner.factoryEntity();
    profile = Objects.requireNonNull(MachineProfile.of(controller.getBlockState()));
    facing = profile.facing(controller.getBlockState());
  }

  public record PortSite(
      MachinePorts.Port port, BlockPos position, BlockPos adjacent, Direction side) {}

  private List<PortSite> portSites;
  private Direction portFacing;
  private boolean portMirror;

  /** Immutable endpoints rebuild only when orientation/port configuration changes. */
  public List<PortSite> ports() {
    var f = profile.facing(controller.getBlockState());
    if (portSites == null || portFacing != f || portMirror != mirrored) {
      var sites = new ArrayList<PortSite>();
      for (var p : MachinePorts.definitions(profile, mirrored)) {
        var at = p.position(profile, controller.getBlockPos(), f);
        var side = p.side(f);
        sites.add(new PortSite(p, at, at.relative(side).immutable(), side));
      }
      portFacing = f;
      portMirror = mirrored;
      portSites = List.copyOf(sites);
    }
    return portSites;
  }

  public MachineProfile profile() {
    return profile;
  }

  public boolean mirrored() {
    return mirrored;
  }

  public void refreshParts() {
    var level = controller.getLevel();
    if (level == null || level.isClientSide) return;
    for (var p : positions())
      if (level.hasChunkAt(p)
          && level.getBlockEntity(p) instanceof CasingBlockEntity part
          && part.boundTo(controller.getBlockPos(), identity)) part.refreshAppearance();
    invalidate();
    controller.sync();
  }

  public boolean mirrorPorts() {
    var level = controller.getLevel();
    if (level == null
        || level.isClientSide
        || MachinePorts.definitions(profile).isEmpty()
        || !ready()) return false;
    mirrored = !mirrored;
    for (var p : positions()) ((CasingBlockEntity) level.getBlockEntity(p)).refreshAppearance();
    invalidate();
    // Shape notifications are explicit even though the solid casing state did not change.
    for (var p : profile.cells(controller.getBlockPos(), facing))
      level.getBlockState(p).updateNeighbourShapes(level, p, 3);
    controller.sync();
    return true;
  }

  public boolean formed() {
    return formed;
  }

  public boolean automatic() {
    return formed;
  }

  public UUID identity() {
    return identity;
  }

  public List<BlockPos> positions() {
    if (parts == null) parts = profile.parts(controller.getBlockPos(), facing);
    return parts;
  }

  public static List<BlockPos> positions(BlockPos origin, Direction facing) {
    return MachineProfile.FURNACE.parts(origin, facing);
  }

  public boolean owns(UUID id) {
    return formed && identity != null && identity.equals(id);
  }

  public void invalidate() {
    ready = false;
    dirty = true;
    var level = controller.getLevel();
    if (level != null && !level.isClientSide) {
      if (level.hasChunkAt(controller.getBlockPos()))
        level.invalidateCapabilities(controller.getBlockPos());
      for (var p : positions()) if (level.hasChunkAt(p)) level.invalidateCapabilities(p);
    }
    AssemblyAccess.changed(owner);
  }

  public boolean form() {
    return formAutomatic();
  }

  public boolean formAutomatic() {
    var level = controller.getLevel();
    if (level == null || level.isClientSide) return false;
    if (formed) return ready();
    facing = profile.facing(controller.getBlockState());
    parts = profile.parts(controller.getBlockPos(), facing);
    for (var p : parts)
      if (!level.hasChunkAt(p)
          || !(level.getBlockEntity(p) instanceof CasingBlockEntity part)
          || !part.availableFor(controller.getBlockPos(), identity)) return false;
    identity = UUID.randomUUID();
    formed = true;
    forming = true;
    ready = false;
    dirty = true;
    try {
      for (var p : parts)
        ((CasingBlockEntity) level.getBlockEntity(p))
            .bind(controller.getBlockPos(), identity, true);
      for (var p : parts) ((CasingBlockEntity) level.getBlockEntity(p)).refreshAppearance();
    } finally {
      forming = false;
    }
    controller.sync();
    AssemblyAccess.changed(owner);
    return true;
  }

  public boolean ready() {
    if (!formed || forming || !owner.loaded() || !FluidConnections.canonical(controller))
      return false;
    if (!dirty) return ready;
    dirty = false;
    for (var p : positions()) {
      var be = FluidConnections.at(controller.getLevel(), p);
      if (!(be instanceof CasingBlockEntity part)
          || !part.loaded()
          || !part.boundTo(controller.getBlockPos(), identity)) return false;
    }
    ready = true;
    return true;
  }

  public int status() {
    return !formed ? UNFORMED : ready() ? READY : WAITING;
  }

  public void dismantle() {
    removeWhole(true);
  }

  public void removeWhole(boolean dropMachine) {
    if (!formed) return;
    formed = false;
    ready = false;
    dirty = true;
    var level = controller.getLevel();
    if (level == null || level.isClientSide) return;
    for (var p : positions())
      if (level.hasChunkAt(p)
          && level.getBlockEntity(p) instanceof CasingBlockEntity part
          && part.boundTo(controller.getBlockPos(), identity)) level.removeBlock(p, false);
    if (level.getBlockEntity(controller.getBlockPos()) == controller)
      level.destroyBlock(controller.getBlockPos(), dropMachine);
    controller.setChanged();
    AssemblyAccess.changed(owner);
  }

  public void save(CompoundTag parent) {
    var tag = new CompoundTag();
    tag.putInt("Schema", 4);
    tag.putBoolean("Formed", formed);
    tag.putBoolean("MirroredPorts", mirrored);
    tag.putString("Profile", profile.id);
    tag.putInt("Facing", facing.get2DDataValue());
    if (identity != null) tag.putUUID("Identity", identity);
    parent.put("Assembly", tag);
  }

  public void load(CompoundTag parent) {
    var tag = parent.getCompound("Assembly");
    mirrored =
        parent.contains("MachinePortMirrored")
            ? parent.getBoolean("MachinePortMirrored")
            : tag.getBoolean("MirroredPorts");
    formed =
        tag.getInt("Schema") == 4
            && tag.getBoolean("Formed")
            && profile.id.equals(tag.getString("Profile"));
    identity = tag.hasUUID("Identity") ? tag.getUUID("Identity") : null;
    if (identity == null) formed = false;
    facing = Direction.from2DDataValue(tag.getInt("Facing"));
    parts = null;
    ready = false;
    dirty = true;
  }
}
