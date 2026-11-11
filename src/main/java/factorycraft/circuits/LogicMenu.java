package factorycraft.circuits;

import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Settings/signal inspection only; no machine resources or held hand are mutable menu slots. */
public final class LogicMenu extends AbstractContainerMenu {
  public final CircuitAttachment owner;
  public final BlockPos interaction;
  public final UUID identity;
  public CompoundTag clientState = new CompoundTag();
  public Boolean lastAccepted;
  public int signalPage;
  private int lastHash;
  private long sentAt = Long.MIN_VALUE;

  public LogicMenu(int id, Inventory inv, RegistryFriendlyByteBuf buffer) {
    this(id, inv, null, buffer.readBlockPos(), buffer.readUUID());
    clientState = buffer.readNbt();
  }

  public LogicMenu(int id, Inventory inv, CircuitAttachment owner, BlockPos interaction) {
    this(id, inv, owner, interaction, owner.identity());
  }

  private LogicMenu(
      int id, Inventory inv, CircuitAttachment owner, BlockPos interaction, UUID identity) {
    super(Content.LOGIC_MENU.get(), id);
    this.owner = owner;
    this.interaction = interaction.immutable();
    this.identity = identity;
    for (int x = 0; x < 9; x++) addSlot(new Slot(inv, x, 8 + 18 * x, 211));
    if (owner != null) clientState = CircuitNetwork.menuState(owner);
  }

  public boolean stillValid(Player player) {
    if (player.level().isClientSide) return true;
    return owner != null
        && owner.level() == player.level()
        && owner.available()
        && identity.equals(owner.identity())
        && owner.portAt(interaction) >= 0
        && player.canInteractWithBlock(interaction, 0)
        && player.level().mayInteract(player, interaction)
        && player.level().mayInteract(player, owner.pos());
  }

  public boolean allowed(Player player) {
    return !player.isSpectator()
        && player.getAbilities().mayBuild
        && (!(owner.owner() instanceof factorycraft.blockentity.LabBlockEntity lab)
            || factorycraft.research.ResearchTeams.member(player, lab.scope()))
        && player.containerMenu == this
        && stillValid(player);
  }

  public boolean apply(Player player, UUID id, CompoundTag tag) {
    if (tag == null || !allowed(player) || !identity.equals(id) || tag.toString().length() > 32768)
      return false;
    if (tag.contains("InspectPage")) {
      signalPage = Math.clamp(tag.getInt("InspectPage"), 0, 682);
      return true;
    }
    try {
      return owner.configure(
          CircuitSettings.load(tag.getCompound("Settings")),
          SignalProgramData.load(tag.getCompound("Program")));
    } catch (RuntimeException malformed) {
      return false;
    }
  }

  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }

  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!player.level().isClientSide && !allowed(player)) return;
    super.clicked(slot, button, type, player);
  }

  public void broadcastChanges() {
    super.broadcastChanges();
    if (owner == null || !(owner.level() instanceof net.minecraft.server.level.ServerLevel level))
      return;
    long now = level.getGameTime();
    if (sentAt != Long.MIN_VALUE && now - sentAt < 5) return;
    var state = CircuitNetwork.menuState(owner, signalPage);
    int hash = state.hashCode();
    if (hash != lastHash || sentAt == Long.MIN_VALUE) {
      state.putInt("Menu", containerId);
      boolean delivered = false;
      if (level.getServer().getPlayerList() != null)
        for (var player : level.getServer().getPlayerList().getPlayers())
          if (player.containerMenu == this) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player, new CircuitNetwork.Snapshot(state));
            delivered = true;
          }
      // ServerPlayer.initMenu broadcasts before assigning its new containerMenu.
      if (delivered) {
        lastHash = hash;
        sentAt = now;
      }
    }
  }

  public static boolean open(CircuitAttachment owner, Player player, BlockPos hit) {
    if (player.level().isClientSide) return true;
    if (player.isSpectator()
        || !player.getAbilities().mayBuild
        || owner == null
        || !owner.available()
        || owner.portAt(hit) < 0
        || !player.canInteractWithBlock(hit, 0)
        || !player.level().mayInteract(player, hit)
        || !player.level().mayInteract(player, owner.pos())) return false;
    owner.configured = true;
    CircuitSimulation.add(owner);
    player.openMenu(
        new SimpleMenuProvider(
            (id, inv, p) -> new LogicMenu(id, inv, owner, hit),
            owner.owner().getBlockState().getBlock().getName()),
        buffer -> {
          buffer.writeBlockPos(hit);
          buffer.writeUUID(owner.identity());
          var initial = CircuitNetwork.menuState(owner);
          buffer.writeNbt(initial);
        });
    return true;
  }
}
