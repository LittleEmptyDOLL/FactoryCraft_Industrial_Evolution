package factorycraft.diagnostics;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.SavedData;

/** Operator-only flat-world visual fixture; never part of ordinary generation or survival tools. */
public final class ResourceShowcase {
  private static final class Runs extends SavedData {
    int next = 8000;

    static Runs load(CompoundTag t, HolderLookup.Provider r) {
      var d = new Runs();
      d.next = t.getInt("Next");
      return d;
    }

    public CompoundTag save(CompoundTag t, HolderLookup.Provider r) {
      t.putInt("Next", next);
      return t;
    }
  }

  public static int build(CommandSourceStack source) {
    var level = source.getLevel();
    if (!(level.getChunkSource().getGenerator()
            instanceof net.minecraft.world.level.levelgen.FlatLevelSource)
        || source.getPlayer() == null) {
      source.sendFailure(
          Component.literal(
              "Resource diagnostic requires an operator player in a separate flat QA world."));
      return 0;
    }
    var runs =
        level
            .getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(Runs::new, Runs::load),
                "factorycraft_v0251_resource_visuals");
    if (runs.next < 8000 || runs.next > 10000)
      throw new IllegalStateException("Visual fixture range exhausted");
    ResourceTerrainFixture.Result last = null;
    for (int mode : new int[] {0, 1, 2, 4}) {
      int index = runs.next++;
      runs.setDirty();
      last = ResourceTerrainFixture.build(level, index, mode, false);
      var r = last;
      source.sendSuccess(
          () ->
              Component.literal(
                  "Resource terrain "
                      + r.center().toShortString()
                      + ": "
                      + r.members()
                      + " owners, "
                      + r.deep()
                      + " deep"),
          false);
    }
    var p = last.center();
    source.getPlayer().connection.teleport(p.getX() - 20.5, p.getY() + 9, p.getZ() + .5, -90, 10);
    return 1;
  }

  private ResourceShowcase() {}
}
