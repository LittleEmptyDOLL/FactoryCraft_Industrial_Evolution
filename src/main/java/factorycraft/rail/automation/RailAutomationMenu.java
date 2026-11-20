package factorycraft.rail.automation;

import factorycraft.simulation.rail.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/**
 * Read-only resource view; edits are bounded configuration only, with current menu/owner checks.
 */
public final class RailAutomationMenu extends AbstractContainerMenu {
  public final UUID target;
  public final boolean train;
  public final BlockPos clicked;
  public CompoundTag initial;
  public final ContainerData state;
  public int accepted;

  public RailAutomationMenu(int id, Inventory inv, RegistryFriendlyByteBuf b) {
    this(id, inv, b.readUUID(), b.readBoolean(), b.readBlockPos());
    initial = b.readNbt();
    if (initial == null) initial = new CompoundTag();
  }

  public RailAutomationMenu(int id, Inventory inv, UUID target, boolean train, BlockPos clicked) {
    super(TracksideContent.MENU.get(), id);
    this.target = target;
    this.train = train;
    this.clicked = clicked;
    initial = inv.player instanceof ServerPlayer p ? snapshot(p, target, train) : new CompoundTag();
    state =
        inv.player instanceof ServerPlayer p
            ? new ContainerData() {
              public int getCount() {
                return 7;
              }

              public void set(int i, int value) {}

              public int get(int i) {
                if (i == 6) return accepted;
                var system = RailAutomationData.get(p.serverLevel());
                if (train) {
                  var t = TrainData.get(p.serverLevel()).train(target);
                  if (t == null) return 0;
                  return switch (i) {
                    case 0 -> t.automation.automatic ? 1 : 0;
                    case 1 -> t.automation.phase.ordinal();
                    case 2 -> t.automation.entry;
                    case 3 -> t.automation.stationTicks / 20;
                    case 4 -> t.automation.idleTicks / 20;
                    default -> (int) Math.round(t.speed * 216);
                  };
                }
                var d = system.device(target);
                if (d == null) return 0;
                return switch (i) {
                  case 0 -> system.aspect(d).ordinal();
                  case 1 -> system.slots.count(d.id);
                  case 2 -> system.stop(d).limit();
                  case 3 -> system.stop(d).priority();
                  default -> 0;
                };
              }
            }
            : new SimpleContainerData(7);
    addDataSlots(state);
  }

  public static CompoundTag snapshot(ServerPlayer p, UUID id, boolean train) {
    var n = new CompoundTag();
    var s = RailAutomationData.get(p.serverLevel());
    if (train) {
      var t = TrainData.get(p.serverLevel()).train(id);
      if (t == null || !TrainWorld.mayUse(t, p)) return n;
      n.put("Schedule", RailScheduleCodec.save(t.automation.schedule));
      n.putString("Kind", "train");
      n.putBoolean("Automatic", t.automation.automatic);
    } else {
      var d = s.device(id);
      if (d == null || !d.mayUse(p)) return n;
      n = d.settings();
      n.putString("Kind", d.kind.name());
    }
    return n;
  }

  public boolean stillValid(Player p) {
    if (p.level().isClientSide) return true;
    if (!(p instanceof ServerPlayer player)) return false;
    if (train) {
      var d = TrainData.get(player.serverLevel());
      var t = d.train(target);
      if (t == null || !TrainWorld.mayUse(t, player) || t.provisional) return false;
      for (var car : t.cars)
        if (player.serverLevel().getEntity(car.id) instanceof StockEntity entity
            && player.distanceToSqr(entity) <= 64) return true;
      return false;
    }
    var s = RailAutomationData.get(player.serverLevel());
    var d = s.device(target);
    return d != null
        && d.mayUse(player)
        && s.online(d.point)
        && d.cells().contains(clicked)
        && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(clicked)) <= 64;
  }

  public boolean edit(ServerPlayer p, CompoundTag n) {
    if (p.containerMenu != this || !stillValid(p)) return false;
    try {
      if (train) {
        var d = TrainData.get(p.serverLevel());
        var t = d.train(target);
        if (n.getString("Action").equals("mode")) {
          if (t.automation.automatic) TrainAutomation.manual(d, t);
          else return TrainAutomation.automatic(d, t);
        } else if (n.getString("Action").equals("schedule")) {
          var schedule = RailScheduleCodec.load(n.getCompound("Schedule"));
          // No pretend pass-through: Gate B requires explicit waits; advancing without braking is
          // Gate C.
          if (schedule.entries().stream().anyMatch(e -> e.waits().isEmpty())
              || !schedule.interrupts().isEmpty()) return false;
          TrainAutomation.manual(d, t);
          t.automation.schedule = schedule;
          t.automation.entry = 0;
          d.setDirty();
        } else return false;
      } else {
        var s = RailAutomationData.get(p.serverLevel());
        var d = s.device(target);
        if (n.getString("Action").equals("circuit"))
          return factorycraft.circuits.LogicMenu.open(s.entity(d).circuit(true), p, clicked);
        if (!n.getString("Action").equals("device") || d.kind != TracksideKind.STOP) return false;
        s.configure(d, n.getCompound("Settings"));
      }
      return true;
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  public ItemStack quickMoveStack(Player p, int slot) {
    return ItemStack.EMPTY;
  }
}
