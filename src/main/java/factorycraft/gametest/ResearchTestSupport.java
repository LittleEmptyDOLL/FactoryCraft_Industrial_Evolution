package factorycraft.gametest;

import factorycraft.research.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Explicit already-researched fixture preconditions; never generates products or changes gameplay.
 */
public final class ResearchTestSupport {
  public static void bootstrap(ServerLevel level, String scope) {
    var ledger = ResearchData.get(level.getServer()).team(scope);
    var levels = new HashMap<>(ledger.levels());
    for (String id : List.of("bootstrap_electronics", "steam_power", "automation_science"))
      levels.put("factorycraft:" + id, 1);
    ledger.restore(ledger.selected(), levels, ledger.progress());
  }

  public static void grant(ServerLevel level, String scope, String id) {
    grant(level, scope, ResourceLocation.parse("factorycraft:" + id), new HashSet<>());
  }

  private static void grant(
      ServerLevel level, String scope, ResourceLocation id, Set<ResourceLocation> visiting) {
    var ledger = ResearchData.get(level.getServer()).team(scope);
    if (ledger.level(id.toString()) > 0 || !visiting.add(id)) return;
    var entry = ResearchCatalog.get(level).definition(id);
    if (entry == null) throw new IllegalStateException("Missing fixture prerequisite " + id);
    for (var p : entry.recipe().prerequisites()) grant(level, scope, p, visiting);
    var levels = new HashMap<>(ledger.levels());
    levels.put(id.toString(), 1);
    ledger.restore(ledger.selected(), levels, ledger.progress());
  }

  private ResearchTestSupport() {}
}
