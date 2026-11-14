package factorycraft.simulation.resources;

import java.util.*;

/** One coordinate-seeded full ellipsoid; terrain clips solids, never deforms this geometry. */
public final class ResourceVeins {
  public record Settings(
      int minRadius,
      int maxRadius,
      int minStock,
      int maxStock,
      int spacingChunks,
      int minDepth,
      int maxDepth,
      double noise) {
    public Settings(int a, int b, int c, int d, int e) {
      this(a, b, c, d, e, 5, 8, .12);
    }

    public Settings {
      if (minRadius < 4
          || maxRadius > 48
          || minRadius > maxRadius
          || minStock < 1
          || maxStock > 10000000
          || minStock > maxStock
          || spacingChunks < 2
          || spacingChunks > 32
          || minDepth < 2
          || maxDepth > 64
          || minDepth > maxDepth
          || !Double.isFinite(noise)
          || noise < 0
          || noise > .3)
        throw new IllegalArgumentException("Invalid resource radius/stock/spacing bounds");
    }
  }

  public record Patch(
      int x, int z, int radius, int radiusZ, int radiusY, long seed, Settings settings) {
    public Body atHeight(int centerY) {
      return new Body(this, centerY);
    }

    /** Horizontal sampling helper at the equator, independent of any local surface. */
    public int stock(int x, int z) {
      return atHeight(0).stock(x, 0, z);
    }
  }

  public record Body(Patch patch, int centerY) {
    public Column column(int x, int z) {
      double dx = (x - patch.x) / (double) patch.radius,
          dz = (z - patch.z) / (double) patch.radiusZ;
      double angle = StrictMath.atan2(dz, dx), phase = unit(patch.seed) * StrictMath.PI * 2;
      double outline =
          .88 + .08 * StrictMath.sin(angle * 3 + phase) + .04 * StrictMath.sin(angle * 5 - phase);
      return new Column(
          this,
          (dx * dx + dz * dz) / (outline * outline),
          dx * dx + dz * dz,
          StrictMath.sin(phase),
          StrictMath.cos(phase));
    }

    public int stock(int x, int y, int z) {
      return column(x, z).stock(y);
    }

    public int minY() {
      return centerY - patch.radiusY + 1;
    }

    public int maxY() {
      return centerY + patch.radiusY - 1;
    }
  }

  /** Cached horizontal terms; smooth directional 3D modulation keeps the body star-convex. */
  public record Column(
      Body body, double horizontal, double radial, double phaseSine, double phaseCosine) {
    public int stock(int y) {
      var p = body.patch;
      double dy = (y - body.centerY) / (double) p.radiusY;
      if (horizontal >= 1 || dy <= -1 || dy >= 1) return 0;
      double total = radial + dy * dy,
          v = total == 0 ? 0 : dy / StrictMath.sqrt(total),
          w = 1 - v * v;
      // Bounded directional harmonic: sin(phase + 2*asin(v)), without per-voxel trig.
      double harmonic =
          phaseSine * (1 - 2 * v * v) + phaseCosine * 2 * v * StrictMath.sqrt(Math.max(0, w));
      double vertical = 1 - p.settings.noise * .5 * (.5 + .5 * harmonic) * w;
      double q = horizontal + dy * dy / (vertical * vertical);
      if (q >= 1) return 0;
      double richness = (1 - q) * (.925 + .075 * dy);
      return p.settings.minStock
          + (int) StrictMath.round((p.settings.maxStock - p.settings.minStock) * richness);
    }
  }

  public static Patch patch(long worldSeed, long salt, int gx, int gz, Settings s) {
    long seed = mix(worldSeed ^ salt ^ mix(((long) gx << 32) ^ (gz & 0xffffffffL)));
    int span = s.spacingChunks * 16;
    return new Patch(
        gx * span + (int) Math.floorMod(seed, span),
        gz * span + (int) Math.floorMod(mix(seed), span),
        s.minRadius + (int) Math.floorMod(mix(seed + 1), (long) s.maxRadius - s.minRadius + 1),
        s.minRadius + (int) Math.floorMod(mix(seed + 2), (long) s.maxRadius - s.minRadius + 1),
        s.minDepth + (int) Math.floorMod(mix(seed + 3), (long) s.maxDepth - s.minDepth + 1),
        seed,
        s);
  }

  public static List<Patch> inChunk(long seed, long salt, int cx, int cz, Settings s) {
    int x = cx * 16, z = cz * 16, r = s.maxRadius, span = s.spacingChunks * 16;
    var result = new ArrayList<Patch>();
    for (int gx = Math.floorDiv(x - r, span); gx <= Math.floorDiv(x + 15 + r, span); gx++)
      for (int gz = Math.floorDiv(z - r, span); gz <= Math.floorDiv(z + 15 + r, span); gz++) {
        var p = patch(seed, salt, gx, gz, s);
        if (p.x + p.radius >= x
            && p.x - p.radius <= x + 15
            && p.z + p.radiusZ >= z
            && p.z - p.radiusZ <= z + 15) result.add(p);
      }
    return result;
  }

  private static double unit(long b) {
    return (b >>> 11) * 0x1.0p-53;
  }

  private static long mix(long v) {
    v = (v ^ (v >>> 30)) * 0xbf58476d1ce4e5b9L;
    v = (v ^ (v >>> 27)) * 0x94d049bb133111ebL;
    return v ^ (v >>> 31);
  }

  private ResourceVeins() {}
}
