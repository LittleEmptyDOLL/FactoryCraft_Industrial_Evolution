package factorycraft.trains;

import factorycraft.simulation.trains.StockSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.*;

/** Ordinary native slots; every click resolves the sole stopped owner again. */
public final class TrainMenu extends AbstractContainerMenu {
  public final StockEntity entity;
  public final StockSpec kind;
  public final ContainerData data;
  public final int stockSlots;
  public boolean filters;

  public TrainMenu(int id, Inventory inv, RegistryFriendlyByteBuf b) {
    this(
        id,
        inv,
        inv.player.level().getEntity(b.readInt()) instanceof StockEntity e ? e : null,
        StockSpec.values()[Math.clamp(b.readVarInt(), 0, 2)],
        null);
  }

  public TrainMenu(int id, Inventory inv, StockEntity e) {
    this(
        id,
        inv,
        e,
        e.kind(),
        TrainData.get((net.minecraft.server.level.ServerLevel) e.level()).car(e.getUUID()));
  }

  private TrainMenu(int id, Inventory inv, StockEntity e, StockSpec kind, TrainCar car) {
    super(TrainContent.MENU.get(), id);
    entity = e;
    this.kind = kind;
    stockSlots = kind == StockSpec.LOCOMOTIVE ? 4 : kind.slots;
    IItemHandler handler = car == null ? new ItemStackHandler(stockSlots) : TrainPorts.inventory(e);
    for (int slot = 0; slot < stockSlots; slot++) {
      final int index = slot;
      addSlot(
          new SlotItemHandler(handler, slot, 16 + (slot % 8) * 18, 40 + (slot / 8) * 18) {
            public boolean mayPlace(ItemStack s) {
              return !(kind == StockSpec.LOCOMOTIVE && index == 3) && super.mayPlace(s);
            }

            // Capability adapters intentionally expose no arbitrary setter. Native container
            // clicks need a private, guarded setter, not a cast to IItemHandlerModifiable.
            public void set(ItemStack stack) {
              if (car == null) ((IItemHandlerModifiable) handler).setStackInSlot(index, stack);
              else if (TrainPorts.ready(e)
                  && TrainData.get((net.minecraft.server.level.ServerLevel) e.level()).car(car.id)
                      == car) car.items.setStackInSlot(index, stack);
              setChanged();
            }

            public void initialize(ItemStack stack) {
              set(stack);
            }
          });
    }
    if (kind == StockSpec.CARGO) {
      ItemStackHandler ghosts =
          car == null
              ? new ItemStackHandler(40)
              : new ItemStackHandler(40) {
                public ItemStack getStackInSlot(int slot) {
                  var id = net.minecraft.resources.ResourceLocation.tryParse(car.filters[slot]);
                  var item =
                      id == null
                          ? null
                          : net.minecraft.core.registries.BuiltInRegistries.ITEM
                              .getOptional(id)
                              .orElse(null);
                  return item == null ? ItemStack.EMPTY : new ItemStack(item);
                }

                public void setStackInSlot(int slot, ItemStack stack) {}

                public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                  return stack;
                }

                public ItemStack extractItem(int slot, int n, boolean simulate) {
                  return ItemStack.EMPTY;
                }
              };
      for (int i = 0; i < 40; i++)
        addSlot(
            new SlotItemHandler(ghosts, i, -10000, -10000) {
              public boolean mayPlace(ItemStack s) {
                return false;
              }

              public boolean mayPickup(Player p) {
                return false;
              }
            });
    }
    int base = kind == StockSpec.CARGO ? 150 : 114;
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, base + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, base + 58));
    data =
        car == null
            ? new SimpleContainerData(13)
            : new ContainerData() {
              public int getCount() {
                return 13;
              }

              public void set(int i, int v) {}

              public int get(int i) {
                var d = TrainData.get((net.minecraft.server.level.ServerLevel) e.level());
                var t = d.owner(car.id);
                return switch (i) {
                  case 0 -> (int) car.health;
                  case 1 -> (int) (car.fuelJ & 0xffff);
                  case 2 -> (int) (car.fuelJ >>> 16 & 0xffff);
                  case 3 -> car.fluid.amount() & 0xffff;
                  case 4 -> car.fluid.amount() >>> 16;
                  case 5 -> car.bar;
                  case 6 -> t == null ? 0 : t.cars.size();
                  case 7 -> t == null ? 0 : t.status.ordinal();
                  case 8 -> filters ? 1 : 0;
                  case 9 -> car.color & 0xffff;
                  case 10 ->
                      car.fluid.amount() == 0
                          ? 0
                          : net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(
                              car.fluid.key().stack(1).getFluid());
                  case 11 -> (int) (car.fuelJ >>> 32 & 0xffff);
                  default -> car.color >>> 16;
                };
              }
            };
    addDataSlots(data);
  }

  public boolean stillValid(Player p) {
    if (p.level().isClientSide) return entity != null;
    var d = TrainData.get((net.minecraft.server.level.ServerLevel) p.level());
    var t = entity == null ? null : d.owner(entity.getUUID());
    return p instanceof ServerPlayer s
        && t != null
        && !entity.isRemoved()
        && p.distanceToSqr(entity) <= 64
        && TrainWorld.mayUse(t, s)
        && TrainPorts.ready(entity)
        && d.stopped(t);
  }

  public void clicked(int slot, int button, ClickType type, Player p) {
    if (!p.level().isClientSide && (p.containerMenu != this || !stillValid(p))) return;
    if ((filters || data.get(8) != 0) && kind == StockSpec.CARGO && slot >= 0 && slot < 40) {
      if (type != ClickType.PICKUP || button < 0 || button > 1) return;
      if (p instanceof ServerPlayer s) {
        var c = TrainData.get(s.serverLevel()).car(entity.getUUID());
        c.filters[slot] =
            button == 1 || getCarried().isEmpty()
                ? ""
                : net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(getCarried().getItem())
                    .toString();
        c.changed();
      }
      return;
    }
    super.clicked(slot, button, type, p);
  }

  public boolean clickMenuButton(Player p, int button) {
    if (!(p instanceof ServerPlayer s) || p.containerMenu != this || !stillValid(p)) return false;
    var d = TrainData.get(s.serverLevel());
    var t = d.owner(entity.getUUID());
    var c = d.car(entity.getUUID());
    int i = t.cars.indexOf(c);
    switch (button) {
      case 0 -> filters = !filters;
      case 1 -> {
        if (c.kind == StockSpec.CARGO) c.bar = Math.max(0, c.bar - 1);
      }
      case 2 -> {
        if (c.kind == StockSpec.CARGO) c.bar = Math.min(40, c.bar + 1);
      }
      case 3 -> StockPlacement.split(d, t, i);
      case 4 -> {
        for (var other : java.util.List.copyOf(d.trains()))
          if (other != t && TrainWorld.mayUse(other, s) && StockPlacement.couple(d, t, other))
            break;
      }
      case 5 -> {
        c.color =
            switch (c.color) {
              case 0xb45c27 -> 0x456e7b;
              case 0x456e7b -> 0x8d3b32;
              default -> 0xb45c27;
            };
        TrainWorld.views(d, t);
      }
      default -> {
        return false;
      }
    }
    c.changed();
    return true;
  }

  public ItemStack quickMoveStack(Player p, int slot) {
    if (!p.level().isClientSide && p.containerMenu != this
        || data.get(8) != 0
        || !stillValid(p)
        || slot < 0
        || slot >= slots.size()
        || slot >= stockSlots && slot < (kind == StockSpec.CARGO ? 80 : stockSlots))
      return ItemStack.EMPTY;
    var from = slots.get(slot);
    var item = from.getItem();
    var copy = item.copy();
    if (!moveItemStackTo(
        item,
        slot < stockSlots ? stockSlots : 0,
        slot < stockSlots ? slots.size() : stockSlots,
        false)) return ItemStack.EMPTY;
    if (item.isEmpty()) from.setByPlayer(ItemStack.EMPTY);
    else from.setChanged();
    return copy;
  }

  public long fuel() {
    return (data.get(1) & 0xffffL) | (data.get(2) & 0xffffL) << 16 | (data.get(11) & 0xffffL) << 32;
  }

  public int volume() {
    return (data.get(3) & 0xffff) | (data.get(4) & 0xffff) << 16;
  }
}
