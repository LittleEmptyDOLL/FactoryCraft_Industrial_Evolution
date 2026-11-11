package factorycraft.modules;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.multiblock.MachineProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Loaded spatial buckets; only nearby recipients invalidate when a beacon changes. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class BeaconIndex {
  private static final Map<Level, State> STATES = new IdentityHashMap<>();

  private static final class State {
    final Map<Long, Set<MachineBlockEntity>> machines = new HashMap<>();
    final Map<Long, Set<BeaconBlockEntity>> buckets = new HashMap<>();
    final Set<BeaconBlockEntity> beacons = new LinkedHashSet<>();
    List<BeaconBlockEntity> snapshot = List.of();
  }

  private static long bucket(BlockPos p) {
    return BlockPos.asLong(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4);
  }

  public static void add(MachineBlockEntity machine) {
    if (!machine.kind().processing() && machine.kind() != factorycraft.blocks.FactoryKind.PUMPJACK)
      return;
    var s = STATES.computeIfAbsent(machine.getLevel(), l -> new State());
    s.machines.computeIfAbsent(bucket(machine.getBlockPos()), k -> new HashSet<>()).add(machine);
    machine.modules.dirty = true;
  }

  public static void remove(MachineBlockEntity machine) {
    var s = STATES.get(machine.getLevel());
    if (s != null) {
      var set = s.machines.get(bucket(machine.getBlockPos()));
      if (set != null) {
        set.remove(machine);
        if (set.isEmpty()) s.machines.remove(bucket(machine.getBlockPos()));
      }
    }
  }

  public static void add(BeaconBlockEntity beacon) {
    var s = STATES.computeIfAbsent(beacon.getLevel(), l -> new State());
    s.beacons.add(beacon);
    s.snapshot = List.copyOf(s.beacons);
    s.buckets.computeIfAbsent(bucket(beacon.getBlockPos()), k -> new HashSet<>()).add(beacon);
    changed(beacon);
  }

  public static void remove(BeaconBlockEntity beacon) {
    var s = STATES.get(beacon.getLevel());
    if (s != null) {
      s.beacons.remove(beacon);
      s.snapshot = List.copyOf(s.beacons);
      var set = s.buckets.get(bucket(beacon.getBlockPos()));
      if (set != null) {
        set.remove(beacon);
        if (set.isEmpty()) s.buckets.remove(bucket(beacon.getBlockPos()));
      }
      changed(beacon);
    }
  }

  private static List<Long> around(BlockPos pos) {
    var keys = new ArrayList<Long>(27);
    for (int x = -1; x <= 1; x++)
      for (int y = -1; y <= 1; y++)
        for (int z = -1; z <= 1; z++)
          keys.add(
              BlockPos.asLong((pos.getX() >> 4) + x, (pos.getY() >> 4) + y, (pos.getZ() >> 4) + z));
    return keys;
  }

  public static void changed(BeaconBlockEntity beacon) {
    var s = STATES.get(beacon.getLevel());
    if (s == null) return;
    for (long key : around(beacon.getBlockPos()))
      for (var m : s.machines.getOrDefault(key, Set.of())) m.modules.dirty = true;
  }

  public static List<BeaconBlockEntity> nearby(MachineBlockEntity machine) {
    var s = STATES.get(machine.getLevel());
    if (s == null || s.beacons.isEmpty()) return List.of();
    var result = new ArrayList<BeaconBlockEntity>();
    for (long key : around(machine.getBlockPos()))
      for (var b : s.buckets.getOrDefault(key, Set.of())) if (covers(b, machine)) result.add(b);
    return List.copyOf(result);
  }

  private static boolean covers(BeaconBlockEntity beacon, MachineBlockEntity machine) {
    var mp = MachineProfile.of(machine.getBlockState());
    var p = machine.getBlockPos();
    var q =
        mp.offset(
            p, mp.facing(machine.getBlockState()), mp.layout.width() - 1, 0, mp.layout.depth() - 1);
    var b = beacon.getBlockPos();
    var bp = MachineProfile.BEACON;
    var c = bp.offset(b, bp.facing(beacon.getBlockState()), 2, 0, 2);
    return Math.abs(b.getY() - p.getY()) <= 3
        && Math.min(p.getX(), q.getX()) <= Math.max(b.getX(), c.getX()) + 3
        && Math.max(p.getX(), q.getX()) >= Math.min(b.getX(), c.getX()) - 3
        && Math.min(p.getZ(), q.getZ()) <= Math.max(b.getZ(), c.getZ()) + 3
        && Math.max(p.getZ(), q.getZ()) >= Math.min(b.getZ(), c.getZ()) - 3;
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Pre event) {
    if (event.getLevel().isClientSide
        || !((net.minecraft.server.level.ServerLevel) event.getLevel())
            .getServer()
            .tickRateManager()
            .runsNormally()) return;
    var s = STATES.get(event.getLevel());
    if (s != null) for (var beacon : s.snapshot) beacon.payForTick();
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload event) {
    STATES.remove(event.getLevel());
  }

  private BeaconIndex() {}
}
