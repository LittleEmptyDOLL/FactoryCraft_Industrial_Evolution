package factorycraft.combat;

import factorycraft.blockentity.FactoryBlockEntity;
import factorycraft.blocks.*;
import factorycraft.energy.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.production.PowerBuffer;
import factorycraft.registry.Content;
import factorycraft.simulation.combat.ShotClock;
import factorycraft.simulation.fluids.FluidCell;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.*;

/** One controller owns magazines, fractional oil/idle debit, FE and aim. Parts only forward. */
public final class TurretBlockEntity extends FactoryBlockEntity
    implements AssemblyOwner, GridConsumer, MenuProvider {
  private final IndustrialAssembly assembly = new IndustrialAssembly(this);
  private boolean loaded, flameActive, ticking, pendingSync;
  public final PowerBuffer power = new PowerBuffer(2136, this::markPersistentState);

  /**
   * Private fuel buffer and physically accessible pass-through fluid box, each 100 source units.
   */
  public final FluidCell<FluidKey> oil = new FluidCell<>(1000, this::fluidChanged);

  public final FluidCell<FluidKey> pipeOil = new FluidCell<>(1000, this::fluidChanged);
  public final ItemStackHandler items =
      new ItemStackHandler(2) {
        public int getSlotLimit(int s) {
          return s == 1 ? 1 : 10;
        }

        public boolean isItemValid(int s, ItemStack stack) {
          return s == 0
              && kind() == FactoryKind.GUN_TURRET
              && stack.getItem() instanceof MagazineItem m
              && m.category.equals("bullet")
              && MagazineItem.rounds(stack) > 0;
        }

        protected void onContentsChanged(int s) {
          sync();
        }
      };
  public String scope = "unavailable";
  public UUID owner;
  private final ShotClock clock = new ShotClock();
  private int target = -1, idleRemainder, shotRemainder, searchDelay;
  public float yaw, pitch;
  public long shotTick = Long.MIN_VALUE;
  public long paidShots;

  public TurretBlockEntity(BlockPos p, net.minecraft.world.level.block.state.BlockState s) {
    super(Content.TURRET.get(), p, s);
  }

  public FactoryKind kind() {
    return ((FactoryBlock) getBlockState().getBlock()).kind;
  }

  public boolean laser() {
    return kind() == FactoryKind.LASER_TURRET;
  }

  public boolean flame() {
    return kind() == FactoryKind.FLAME_TURRET;
  }

  public IndustrialAssembly assembly() {
    return assembly;
  }

  public boolean loaded() {
    return loaded && !isRemoved();
  }

  public boolean ready() {
    return loaded() && FluidConnections.canonical(this) && assembly.ready();
  }

  public PowerBuffer gridBuffer() {
    return power;
  }

  public int receiveGridEnergy(int n, boolean sim) {
    return laser() && ready() ? power.receiveEnergy(n, sim) : 0;
  }

  public final IEnergyStorage energy =
      new IEnergyStorage() {
        public int receiveEnergy(int n, boolean sim) {
          return receiveGridEnergy(n, sim);
        }

        public int extractEnergy(int n, boolean sim) {
          return 0;
        }

        public int getEnergyStored() {
          return ready() && laser() ? power.getEnergyStored() : 0;
        }

        public int getMaxEnergyStored() {
          return laser() ? 2136 : 0;
        }

        public boolean canExtract() {
          return false;
        }

        public boolean canReceive() {
          return laser() && ready();
        }
      };

  public boolean accepts(FluidKey key) {
    if (!flame() || key == null) return false;
    var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(key.stack(1).getFluid());
    return id.getNamespace().equals("factorycraft")
        && java.util.Set.of("crude_oil", "heavy_oil", "light_oil").contains(id.getPath());
  }

  private void fluidChanged() {
    markPersistentState();
    if (level != null && !level.isClientSide) FluidSimulation.machineChanged(this);
  }

  private final IItemHandler port =
      new IItemHandler() {
        public int getSlots() {
          return 2;
        }

        public ItemStack getStackInSlot(int s) {
          return ready() ? items.getStackInSlot(s).copy() : ItemStack.EMPTY;
        }

        public ItemStack insertItem(int s, ItemStack n, boolean sim) {
          return ready() && s == 0 ? items.insertItem(s, n, sim) : n;
        }

        public ItemStack extractItem(int s, int n, boolean sim) {
          return ready() ? items.extractItem(s, n, sim) : ItemStack.EMPTY;
        }

        public int getSlotLimit(int s) {
          return items.getSlotLimit(s);
        }

        public boolean isItemValid(int s, ItemStack n) {
          return ready() && items.isItemValid(s, n);
        }
      };

  public IItemHandler inventory() {
    return port;
  }

  private boolean validTarget(ServerLevel l, LivingEntity v, Entity shooter, CombatProfile spec) {
    var delta = v.getBoundingBox().getCenter().subtract(muzzle());
    if (flame()
        && (delta.lengthSqr() < 36
            || Math.abs(
                    net.minecraft.util.Mth.wrapDegrees(
                        (float) Math.toDegrees(Math.atan2(-delta.x, delta.z))
                            - getBlockState().getValue(FactoryBlock.FACING).toYRot()))
                > 60)) return false;
    return Combat.allowed(l, v, shooter, scope, true)
        && delta.lengthSqr() <= spec.range() * spec.range()
        && Combat.sight(l, muzzle(), v);
  }

  public Vec3 muzzle() {
    var p = assembly.profile();
    var f = p.facing(getBlockState());
    var r = f.getClockWise();
    return p.origin(worldPosition, f)
        .getCenter()
        .add(
            r.getStepX() * (p.layout.width() / 2.0 - .5)
                - f.getStepX() * (p.layout.depth() / 2.0 - .5),
            1,
            r.getStepZ() * (p.layout.width() / 2.0 - .5)
                - f.getStepZ() * (p.layout.depth() / 2.0 - .5));
  }

  @Override
  public void sync() {
    if (ticking) pendingSync = true;
    else super.sync();
  }

  public void tick() {
    if (ticking) return;
    ticking = true;
    try {
      tickWork();
    } finally {
      ticking = false;
      if (pendingSync) {
        pendingSync = false;
        if (ready()) super.sync();
      }
    }
  }

  private void tickWork() {
    if (!(level instanceof ServerLevel l) || !ready()) return;
    if (flame()) pipeOil.transferTo(oil, 25, false);
    var spec = CombatProfile.get(l, kind().id);
    if (spec == null) return;
    // 24 kW / 7.5 kW per FE/t = 3.2 FE/t, retain fifths exactly while idle.
    if (laser()) {
      int owed = (idleRemainder + 16) / 5;
      if (!power.spend(owed)) return;
      idleRemainder = (idleRemainder + 16) % 5;
      markPersistentState();
    }
    Entity shooter = owner == null ? null : l.getPlayerByUUID(owner);
    var entity = l.getEntity(target);
    LivingEntity victim =
        entity instanceof LivingEntity v && validTarget(l, v, shooter, spec) ? v : null;
    if (victim == null && --searchDelay <= 0) {
      searchDelay = 10;
      double distance = spec.range() * spec.range();
      for (var v :
          l.getEntitiesOfClass(
              LivingEntity.class,
              new AABB(muzzle(), muzzle()).inflate(spec.range()),
              e -> Combat.allowed(l, e, shooter, scope, true))) {
        double d = v.distanceToSqr(muzzle());
        if (d < distance && validTarget(l, v, shooter, spec)) {
          distance = d;
          victim = v;
        }
      }
      target = victim == null ? -1 : victim.getId();
    }
    if (victim == null) {
      flameActive = false;
      return;
    }
    Vec3 delta = victim.getBoundingBox().getCenter().subtract(muzzle());
    float desired = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
    float dp = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
    float limit = laser() ? 10.8f : 16.2f;
    float change = net.minecraft.util.Mth.wrapDegrees(desired - yaw);
    float next = yaw + net.minecraft.util.Mth.clamp(change, -limit, limit);
    if (Math.abs(next - yaw) > .01 || Math.abs(dp - pitch) > .01) {
      yaw = next;
      pitch = net.minecraft.util.Mth.clamp(dp, -80, 80);
      sync();
    }
    if (Math.abs(change) > limit || !clock.ready(l.getGameTime())) return;
    double damage = spec.damage();
    if (laser()) {
      int debit = shotRemainder + spec.energy();
      int need = debit / 375;
      if (!power.spend(need)) return;
      shotRemainder = debit % 375;
    } else if (flame()) {
      // Prototype fluid_consumption is per attack: 0.2 units × 10 mB/unit.
      if (!FlameSimulation.canLaunch(l)) return;
      int need = 2;
      if (!flameActive && oil.amount() < 250) return;
      if (oil.amount() < need || !accepts(oil.key())) {
        flameActive = false;
        return;
      }
      flameActive = true;
      String id =
          net.minecraft.core.registries.BuiltInRegistries.FLUID
              .getKey(oil.key().stack(1).getFluid())
              .getPath();
      if (id.equals("heavy_oil")) damage *= 1.05;
      else if (id.equals("light_oil")) damage *= 1.1;
      oil.drain(need, false);
    } else {
      if (items.getStackInSlot(1).isEmpty()) {
        var one = items.extractItem(0, 1, false);
        if (one.isEmpty()) return;
        items.setStackInSlot(1, one);
      }
      var round = items.getStackInSlot(1).copy();
      if (!MagazineItem.spend(round)) return;
      damage *= MagazineItem.damage(round);
      items.setStackInSlot(1, MagazineItem.rounds(round) == 0 ? ItemStack.EMPTY : round);
    }
    damage *= 1 + CombatResearch.bonus(l, scope, "ammo-damage", spec.category());
    if (flame())
      damage *=
          (1 + CombatResearch.bonus(l, scope, "ammo-damage", "flamethrower"))
              * (1 + CombatResearch.bonus(l, scope, "turret-attack", "flamethrower-turret"));
    if (kind() == FactoryKind.GUN_TURRET)
      damage *= 1 + CombatResearch.bonus(l, scope, "turret-attack", "gun-turret");
    clock.firePeriod(l.getGameTime(), CombatResearch.period(l, scope, spec));
    shotTick = l.getGameTime();
    paidShots++;
    Combat.hitscan(l, muzzle(), delta.normalize(), shooter, scope, spec, damage, true);
    sync();
  }

  public void onLoad() {
    super.onLoad();
    loaded = true;
    assembly.invalidate();
    if (!level.isClientSide && laser()) PowerGrid.add(this);
  }

  public void onChunkUnloaded() {
    loaded = false;
    assembly.invalidate();
    if (!level.isClientSide) PowerGrid.remove(this);
    super.onChunkUnloaded();
  }

  public void setRemoved() {
    loaded = false;
    assembly.invalidate();
    if (level != null && !level.isClientSide) PowerGrid.remove(this);
    super.setRemoved();
  }

  public void dropContents() {
    assembly.removeWhole(true);
  }

  public Component getDisplayName() {
    return Component.translatable("block.factorycraft." + kind().id);
  }

  public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
    return new TurretMenu(id, inv, this);
  }

  protected void saveAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.saveAdditional(n, r);
    assembly.save(n);
    n.put("Ammo", items.serializeNBT(r));
    n.putInt("Energy", power.getEnergyStored());
    if (oil.amount() > 0) n.put("Oil", oil.key().stack(oil.amount()).save(r));
    if (pipeOil.amount() > 0) n.put("PipeOil", pipeOil.key().stack(pipeOil.amount()).save(r));
    n.putString("Force", scope);
    if (owner != null) n.putUUID("Owner", owner);
    n.putLong("Due", clock.due());
    n.putLong("Last", clock.last());
    n.putInt("IdleRemainder", idleRemainder);
    n.putInt("ShotRemainder", shotRemainder);
    n.putBoolean("FlameActive", flameActive);
    n.putFloat("Yaw", yaw);
    n.putFloat("Pitch", pitch);
    n.putLong("ShotTick", shotTick);
    n.putLong("PaidShots", paidShots);
  }

  protected void loadAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.loadAdditional(n, r);
    assembly.load(n);
    flameActive = n.getBoolean("FlameActive");
    if (n.contains("Ammo")) {
      if (n.getCompound("Ammo").getInt("Size") != 2)
        throw new IllegalArgumentException("Invalid turret inventory");
      items.deserializeNBT(r, n.getCompound("Ammo"));
    }
    power.restore(n.getInt("Energy"));
    var f = FluidStack.parseOptional(r, n.getCompound("Oil"));
    if (!f.isEmpty()) {
      if (f.getAmount() > 1000) throw new IllegalArgumentException("Turret oil overflow");
      oil.restore(new FluidKey(f), f.getAmount());
    } else oil.restore(null, 0);
    var pipe = FluidStack.parseOptional(r, n.getCompound("PipeOil"));
    if (!pipe.isEmpty()) {
      if (pipe.getAmount() > 1000)
        throw new IllegalArgumentException("Turret pass-through overflow");
      pipeOil.restore(new FluidKey(pipe), pipe.getAmount());
    } else pipeOil.restore(null, 0);
    scope = n.getString("Force");
    owner = n.hasUUID("Owner") ? n.getUUID("Owner") : null;
    clock.restore(n.getLong("Due"), n.getLong("Last"));
    idleRemainder = Math.floorMod(n.getInt("IdleRemainder"), 5);
    shotRemainder = Math.floorMod(n.getInt("ShotRemainder"), 375);
    yaw = n.getFloat("Yaw");
    pitch = n.getFloat("Pitch");
    shotTick = n.getLong("ShotTick");
    paidShots = Math.max(0, n.getLong("PaidShots"));
  }
}
