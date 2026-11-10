package factorycraft.inserters;

import factorycraft.research.*;
import factorycraft.simulation.research.ResearchLedger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Explicit creative diagnostic grant, never an early survival recipe for Space Age equipment. */
public final class InserterResearch {
  private static void finish(ResearchCatalog catalog, ResearchLedger ledger, ResourceLocation id) {
    if (ledger.level(id.toString()) > 0) return;
    var e = catalog.entry(id);
    if (e == null) throw new IllegalStateException("Missing technology " + id);
    for (var parent : e.recipe().prerequisites()) finish(catalog, ledger, parent);
    if (!ledger.select(e.logic())) throw new IllegalStateException("Unavailable research " + id);
    for (int i = 0; i < e.recipe().units(); i++)
      if (!ledger.completeUnit(e.logic(), () -> {}))
        throw new IllegalStateException("Rejected research unit");
  }

  public static int grant(ServerPlayer player, int capacity, int belt) {
    if (!player.getAbilities().instabuild) return 0;
    var scope = ResearchTeams.resolve(player);
    if (scope.isEmpty()) return 0;
    var data = ResearchData.get(player.server);
    var ledger = data.team(scope.get());
    var catalog = ResearchCatalog.get(player.level());
    if (capacity > 0)
      finish(
          catalog,
          ledger,
          ResourceLocation.fromNamespaceAndPath(
              "factorycraft", "inserter_capacity_bonus_" + capacity));
    if (belt > 0)
      finish(
          catalog,
          ledger,
          ResourceLocation.fromNamespaceAndPath("factorycraft", "transport_belt_capacity_" + belt));
    data.setDirty();
    return 1;
  }

  private InserterResearch() {}
}
