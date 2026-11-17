package factorycraft.combat;

import com.google.gson.*;
import factorycraft.research.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Source-derived effect categories, isolated from unrelated Minecraft damage. */
public final class CombatResearch {
  private record Effect(String technology, String kind, String category, double modifier) {}

  private static final List<Effect> EFFECTS;

  static {
    var out = new ArrayList<Effect>();
    try (var stream =
            Objects.requireNonNull(
                CombatResearch.class.getResourceAsStream("/factorycraft/combat_upgrades.json"));
        var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      for (var value : JsonParser.parseReader(reader).getAsJsonArray()) {
        var o = value.getAsJsonObject();
        double amount = o.get("modifier").getAsDouble();
        if (!Double.isFinite(amount) || amount < 0 || amount > 10)
          throw new IllegalArgumentException("Invalid combat upgrade");
        out.add(
            new Effect(
                o.get("technology").getAsString(),
                o.get("kind").getAsString(),
                o.get("category").getAsString(),
                amount));
      }
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
    EFFECTS = List.copyOf(out);
  }

  public static double bonus(ServerLevel l, String scope, String kind, String category) {
    if (scope.isEmpty() || !ResearchTeams.exists(l.getServer(), scope)) return 0;
    var ledger = ResearchData.get(l.getServer()).team(scope);
    double total = 0;
    for (var effect : EFFECTS)
      if (effect.kind.equals(kind)
          && effect.category.equals(category)
          && ledger.level(effect.technology) > 0) total += effect.modifier;
    return total;
  }

  public static int period(ServerLevel l, String scope, CombatProfile p) {
    return Math.max(
        1,
        (int) Math.round(p.cooldown() * 1000 / (1 + bonus(l, scope, "gun-speed", p.category()))));
  }

  private CombatResearch() {}
}
