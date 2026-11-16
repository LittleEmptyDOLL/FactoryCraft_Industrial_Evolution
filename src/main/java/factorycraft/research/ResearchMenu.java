package factorycraft.research;

import factorycraft.blockentity.LabBlockEntity;
import factorycraft.registry.Content;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class ResearchMenu extends AbstractContainerMenu {
  public static final int LAB_SLOTS = SciencePacks.COUNT + 2;
  public final LabBlockEntity lab;
  private final Player player;
  private CompoundTag sent = new CompoundTag();
  private long last;

  public ResearchMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
    this(id, inv, (LabBlockEntity) inv.player.level().getBlockEntity(buf.readBlockPos()));
  }

  public ResearchMenu(int id, Inventory inv, LabBlockEntity lab) {
    super(Content.RESEARCH_MENU.get(), id);
    this.lab = lab;
    player = inv.player;
    for (int i = 0; i < SciencePacks.COUNT; i++)
      addSlot(new SlotItemHandler(lab.items, i, 8 + (i % 6) * 18, 139 + (i / 6) * 18));
    for (int i = 0; i < 2; i++) addSlot(new SlotItemHandler(lab.modules, i, 154 + i * 18, 139));
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 190 + col * 18, 164 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 190 + col * 18, 222));
  }

  public boolean stillValid(Player p) {
    if (p.level().isClientSide) return true;
    return lab.ready()
        && p.level() == lab.getLevel()
        && factorycraft.multiblock.MachineInteraction.inRange(lab, p)
        && ResearchTeams.member(p, lab.scope());
  }

  public void broadcastChanges() {
    super.broadcastChanges();
    if (player instanceof ServerPlayer p
        && stillValid(p)
        && lab.getLevel().getGameTime() - last >= 5) {
      last = lab.getLevel().getGameTime();
      var tag = snapshot();
      if (!tag.equals(sent)) {
        sent = tag.copy();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            p, new ResearchNetwork.State(containerId, tag));
      }
    }
  }

  private CompoundTag snapshot() {
    var tag = new CompoundTag();
    tag.putString("Team", lab.scope());
    tag.putString(
        "TeamLabel",
        lab.scope().startsWith("ftb:")
            ? factorycraft.compatibility.FtbTeamsBridge.label(
                java.util.UUID.fromString(lab.scope().substring(4)))
            : lab.scope().startsWith("vanilla:")
                ? lab.scope().substring(8)
                : player.getName().getString());
    tag.putInt("FE", lab.power.getEnergyStored());
    tag.putInt("Work", lab.progress());
    var effects = lab.effects();
    tag.putInt("Rate", (int) ((long) effects.speed() * lab.researchSpeed() / 10000));
    tag.putInt("EnergyRate", 8 * effects.energy());
    tag.putInt("Productivity", effects.productivity());
    tag.putInt("Bonus", (int) (lab.productivityCredit() / 10000));
    tag.putString("Status", lab.workState());
    var data = ResearchData.get(player.getServer()).team(lab.scope());
    tag.putString("Selected", data.selected());
    var nodes = new CompoundTag();
    var catalog = ResearchCatalog.get(lab.getLevel());
    for (var e : catalog.entries()) {
      var node = new CompoundTag();
      node.putInt("Level", data.level(e.id().toString()));
      node.putInt("Units", data.units(e.logic()));
      node.putInt("Cost", e.logic().cost(data.level(e.id().toString())));
      node.putBoolean(
          "Available",
          !catalog.disabled(e.id()) && e.recipe().trigger().isEmpty() && data.available(e.logic()));
      node.putBoolean("Disabled", catalog.disabled(e.id()));
      node.putBoolean(
          "Future", e.recipe().packs().keySet().stream().anyMatch(SciencePacks::future));
      nodes.put(e.id().toString(), node);
    }
    tag.put("Nodes", nodes);
    return tag;
  }

  public void clicked(int slot, int button, ClickType type, Player p) {
    if (stillValid(p) && !p.isSpectator()) super.clicked(slot, button, type, p);
  }

  public ItemStack quickMoveStack(Player p, int index) {
    if (index < 0 || index >= slots.size() || !stillValid(p) || p.isSpectator())
      return ItemStack.EMPTY;
    var slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    var stack = slot.getItem();
    var copy = stack.copy();
    if (!moveItemStackTo(
        stack,
        index < LAB_SLOTS ? LAB_SLOTS : 0,
        index < LAB_SLOTS ? slots.size() : LAB_SLOTS,
        index < LAB_SLOTS)) return ItemStack.EMPTY;
    if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }
}
