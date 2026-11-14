# 0.25.1 bounded resource observations

Baseline: exact 0.25.0 ResourceVeins/SurfaceWindow from `1132d39689c24ae5df46091076ece4559f875bdd`; new source in this release. Java 21.0.13, same two-vCPU Cloud host. This is a correctness change with different geometric volume and work, not a TPS optimization claim.

## Pure kernel, no Minecraft

Three sequential isolated-JVM trials, with no client/server/build running during these selected trials. Each scenario has 60 warmups and 30 measurements, fixed seed 42/salt 19, stock 500–5000. Default-range example fixes horizontal radius 20 and new vertical radius 8; old downward depth is 12. Maximum case fixes horizontal radius 48 and old depth/new vertical radius 64. Synthetic terrain clips existing solids: 0 flat; 1 slope; 2 a ten-block higher cliff. p99 equals max in only thirty samples, not a reliable population tail estimate. These timings exclude generator anchor lookup, chunks, BlockEntity and IO.

| Version / horizontal radius / vertical parameter / terrain | Potential BEs | Mean range ms | Median range ms | p95 range ms | Max range ms | Allocated bytes/sample |
|---|---:|---:|---:|---:|---:|---:|
| 0.25.0 / 20 / 12 / 0 | 7867 | 0.304–0.371 | 0.265–0.414 | 0.476–0.556 | 0.501–0.615 | 197040–197040 |
| 0.25.0 / 20 / 12 / 1 | 7867 | 0.235–0.370 | 0.213–0.358 | 0.378–0.474 | 0.420–0.585 | 197040–197040 |
| 0.25.0 / 20 / 12 / 2 | 7262 | 0.235–0.943 | 0.230–0.930 | 0.255–1.220 | 0.260–1.227 | 197040–197040 |
| 0.25.0 / 48 / 64 / 0 | 228447 | 2.169–2.401 | 2.132–2.362 | 2.303–2.744 | 2.306–2.902 | 1066800–1066800 |
| 0.25.0 / 48 / 64 / 1 | 228447 | 2.599–2.772 | 2.564–2.710 | 2.914–3.046 | 2.980–3.998 | 1066800–1066800 |
| 0.25.0 / 48 / 64 / 2 | 226994 | 2.415–2.603 | 2.322–2.519 | 2.709–3.057 | 3.463–4.230 | 1066800–1066800 |
| 0.25.1 / 20 / 8 / 0 | 5605 | 0.310–0.364 | 0.272–0.340 | 0.410–0.473 | 1.000–1.619 | 295880–295880 |
| 0.25.1 / 20 / 8 / 1 | 5211 | 0.470–0.526 | 0.506–0.522 | 0.600–0.729 | 0.682–0.994 | 295880–295880 |
| 0.25.1 / 20 / 8 / 2 | 7767 | 0.390–0.468 | 0.380–0.435 | 0.444–0.595 | 0.555–0.623 | 295880–295880 |
| 0.25.1 / 48 / 64 / 0 | 238707 | 6.307–6.614 | 6.286–6.530 | 6.670–7.467 | 6.909–7.559 | 1656008–1656008 |
| 0.25.1 / 48 / 64 / 1 | 236555 | 6.806–7.677 | 6.607–6.988 | 7.754–15.051 | 8.447–24.085 | 1656008–1656008 |
| 0.25.1 / 48 / 64 / 2 | 265242 | 6.877–7.512 | 6.765–7.484 | 7.594–8.738 | 8.015–10.190 | 1656008–1656008 |

New full geometry is more expensive per sampled voxel than the old column-depth approximation. At maximum settings it retains more potential owners and allocates more during sampling; the exact numbers above do not measure retained Minecraft heap. Directional deformation uses a bounded algebraic harmonic with cached horizontal trigonometry, avoiding a per-voxel trigonometric call. Earlier development timing files remain outside this selected comparison; trials overlapping compilation/native servers are excluded rather than pooled.

## Native Minecraft Feature calls

Prepared finite stone terrain, original air/cave/water/gold/chest exclusions, real feature writes and resource BlockEntities. Modes 0/1/2/3/4 = flat/slope/lower cliff/underwater solids/higher cliff. Both orientations of the cliff are tested; higher ground contains the upper hemisphere. The 0.25.0 reference replays its downward-body/smoothing formula inside the current server, not an old-version binary. Independent fresh patch coordinates mean counts are observations, not identical-seed speed ratios. Native time/allocation exclude prepared terrain construction. No sustained chunk-generation/TPS comparison follows.

```text
FACTORYCRAFT_ELLIPSOID_MAX_SLICE: owners=18646 volume=381353 nanos=74759796 chunksWritten=1
FACTORYCRAFT_TERRAIN_PROFILE: mode=0 legacy0250=true owners=4068 deep=2589 chunks=16 nanos=29817965 allocated=11220904
FACTORYCRAFT_TERRAIN_PROFILE: mode=0 legacy0250=false owners=1930 deep=659 chunks=16 nanos=6860773 allocated=2014864
FACTORYCRAFT_TERRAIN_PROFILE: mode=1 legacy0250=true owners=2810 deep=1936 chunks=16 nanos=18697873 allocated=6983296
FACTORYCRAFT_TERRAIN_PROFILE: mode=1 legacy0250=false owners=2623 deep=1204 chunks=16 nanos=10983064 allocated=2458080
FACTORYCRAFT_TERRAIN_PROFILE: mode=2 legacy0250=true owners=2751 deep=2054 chunks=16 nanos=8874630 allocated=7116776
FACTORYCRAFT_TERRAIN_PROFILE: mode=2 legacy0250=false owners=969 deep=287 chunks=16 nanos=2241238 allocated=1357664
FACTORYCRAFT_TERRAIN_PROFILE: mode=3 legacy0250=true owners=2417 deep=1635 chunks=16 nanos=9664792 allocated=5073504
FACTORYCRAFT_TERRAIN_PROFILE: mode=3 legacy0250=false owners=1327 deep=545 chunks=16 nanos=2490432 allocated=1451536
FACTORYCRAFT_TERRAIN_PROFILE: mode=4 legacy0250=true owners=3894 deep=3165 chunks=16 nanos=9240374 allocated=9269104
FACTORYCRAFT_TERRAIN_PROFILE: mode=4 legacy0250=false owners=2080 deep=1292 chunks=16 nanos=2614252 allocated=1971992
```

The maximum-setting native test creates an actual one-chunk slice, not a complete maximum-size field. It checks every voxel against the canonical body, repeat generation, another ore identity, a chest, water and a cave; its distant center chunk remains unloaded. Whole-field maximum native throughput and retained ore heap are unmeasured. Such settings can create tens of thousands of non-ticking owners per chunk and remain expensive.

## Factory liveness and independent process reload

The limited `smoke-v0251-release` dedicated profile passes with 100 belts, 20 producers (including five fueled steel furnaces), ten inserters, 603 parts and a shared explicit power network. Real finite items/fuel, FE accounting, every producer/inserter liveness and pole removal/restoration are checked. The exact persisted state hash passes in another JVM. This smoke run does not replace full/MIXED CI or establish comparative factory TPS. Two prior census failures (coal treated as iron; obsolete FE-node count including a burner) are retained as JSON and original worlds; checks were adapted to actual fuel and FE roles, not removed.
