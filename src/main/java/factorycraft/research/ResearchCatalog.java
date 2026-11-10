package factorycraft.research;

import factorycraft.registry.Content;
import factorycraft.simulation.research.ResearchLedger;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class ResearchCatalog {
  public record Entry(
      ResourceLocation id, TechnologyRecipe recipe, ResearchLedger.Technology logic) {}

  private static final Map<RecipeManager, ResearchCatalog> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());
  private long checked = Long.MIN_VALUE;
  private List<RecipeHolder<TechnologyRecipe>> source = List.of();
  private Map<ResourceLocation, Entry> entries = Map.of();
  private Set<ResourceLocation> gated = Set.of();

  public static ResearchCatalog get(Level level) {
    var c = CACHE.computeIfAbsent(level.getRecipeManager(), m -> new ResearchCatalog());
    c.refresh(level);
    return c;
  }

  private void refresh(Level level) {
    long now = level.getGameTime();
    if (checked != Long.MIN_VALUE && now - checked >= 0 && now - checked < 20) return;
    checked = now;
    var list =
        level.getRecipeManager().getAllRecipesFor(Content.RESEARCH.get()).stream()
            .sorted(Comparator.comparing(r -> r.id().toString()))
            .toList();
    if (list.equals(source)) return;
    source = list;
    if (list.size() > 128)
      throw new IllegalStateException(
          "FactoryCraft supports at most 128 technologies per datapack catalog");
    var result = new LinkedHashMap<ResourceLocation, Entry>();
    var locks = new HashSet<ResourceLocation>();
    for (var h : list) {
      var r = h.value();
      String signature =
          h.id()
              + "|"
              + new TreeMap<>(r.packs())
              + "|"
              + r.prerequisites()
              + "|"
              + r.units()
              + "|"
              + r.ticks()
              + "|"
              + r.repeatable();
      result.put(
          h.id(),
          new Entry(
              h.id(),
              r,
              new ResearchLedger.Technology(
                  h.id().toString(),
                  r.prerequisites().stream().map(Object::toString).toList(),
                  r.units(),
                  r.ticks(),
                  r.repeatable(),
                  signature)));
      locks.addAll(r.unlocks());
    }
    var invalid = new HashSet<ResourceLocation>();
    for (var id : result.keySet())
      if (!valid(id, result, new HashSet<>(), new HashSet<>())) invalid.add(id);
    invalid.forEach(result::remove);
    if (!invalid.isEmpty())
      com.mojang.logging.LogUtils.getLogger()
          .error("Disabled technologies with missing/cyclic prerequisites: {}", invalid);
    entries = Collections.unmodifiableMap(result);
    gated = Set.copyOf(locks);
  }

  private static boolean valid(
      ResourceLocation id,
      Map<ResourceLocation, Entry> all,
      Set<ResourceLocation> path,
      Set<ResourceLocation> done) {
    if (done.contains(id)) return true;
    if (!all.containsKey(id) || !path.add(id)) return false;
    for (var p : all.get(id).recipe.prerequisites()) if (!valid(p, all, path, done)) return false;
    path.remove(id);
    done.add(id);
    return true;
  }

  public Collection<Entry> entries() {
    return entries.values().stream().filter(e -> !e.recipe().sandboxOnly()).toList();
  }

  public Entry entry(ResourceLocation id) {
    return entries.get(id);
  }

  public boolean allowed(String scope, ResourceLocation recipe, Level level) {
    if (scope.isEmpty() || !gated.contains(recipe)) return true;
    if (!(level instanceof net.minecraft.server.level.ServerLevel server)
        || !ResearchTeams.exists(server.getServer(), scope)) return false;
    var ledger = ResearchData.get(server.getServer()).team(scope);
    return entries.values().stream()
        .anyMatch(e -> e.recipe.unlocks().contains(recipe) && ledger.level(e.id.toString()) > 0);
  }

  private ResearchCatalog() {}
}
