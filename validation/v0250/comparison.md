# 0.25.0 bounded performance evidence

Baseline: `dd7c81596e9db216fb10d75748ed9479ced1ea07` (0.24.1). Java 21.0.13, same executor. These are different gameplay versions, not a fluid optimizer comparison. No universal TPS improvement is claimed.

## Pure resource geometry: separate JVMs, no Minecraft

`JAVA_HOME=/path/to/java21 python tools/profile_resources.py` compiles the exact baseline ResourceVeins from git and the current source separately. Each radius has 80 warmups and 30 measurements. Fixed seed/salt/patch and declared 500–5000 stock. Baseline samples one surface position; new sampling covers all depth positions and sums actual finite stock. No terrain, BlockEntity, IO or retained-heap measurement.

Ranges include all three completed warmed runs (`resource-kernel.jsonl`, `resource-kernel-repeat.jsonl`, `resource-kernel-final.jsonl`). Initial file is a retained six-warmup pilot. The third file overlapped compilation and is retained as a contaminated trial, excluded from this table. p99 is the maximum of only thirty samples, not a reliable population-tail estimate.

| Version / radius / depth | Potential owners | Mean range ms | Median range ms | p95 range ms | p99/max range ms | Allocated bytes/sample |
|---|---:|---:|---:|---:|---:|---:|
| 0.24.1 / 20 / 12 | 976 | 0.098–0.141 | 0.104–0.148 | 0.133–0.191 | 0.138–0.250 | 107584 |
| 0.24.1 / 48 / 64 | 5634 | 0.580–1.960 | 0.567–1.906 | 0.689–2.240 | 0.707–3.085 | 602176 |
| 0.25.0 / 20 / 12 | 7867 | 0.183–0.991 | 0.176–0.937 | 0.196–2.013 | 0.286–2.183 | 170048 |
| 0.25.0 / 48 / 64 | 228447 | 1.675–1.850 | 1.409–1.476 | 2.776–3.978 | 4.758–6.073 | 962752 |

New geometry creates **8.06×** potential resource owners at radius 20/depth 12 and **40.55×** at radius 48/depth 64. Sampling allocation rises about 58–60%; actual retained Minecraft resource-BE heap is **unmeasured**. Timing is unstable; do not infer improved generation throughput. Extreme radius/depth is an expensive opt-in setting.

## Real Minecraft feature calls: bounded native fixture

Real Feature calls in prepared 16-chunk regions create actual finite-stock BEs. Modes: 0 flat, 1 slope, 2 cliff, 3 river/cave/water/solid-obstacle exclusions. Surface reference replays the exact 0.24.1 surface loop and stock formula, one patch list per chunk, inside the current server. Independent patch coordinates mean counts are observations, not an equal-terrain percentage comparison. Heightmaps are primed throughout the supplied padded region. Timing excludes terrain/chunk setup.

| Mode | Surface owners / ms / allocation KiB | Volume owners (deep) / ms / allocation KiB |
|---|---:|---:|
| 0 | 355 / 6.316 / 955.1 | 1594 (841) / 10.257 / 1735.2 |
| 1 | 337 / 1.629 / 906.7 | 1642 (881) / 7.989 / 1452.3 |
| 2 | 399 / 1.913 / 766.7 | 1304 (812) / 3.746 / 1386.4 |
| 3 | 162 / 1.882 / 680.2 | 751 (463) / 2.415 / 1005.4 |

These are one-call bounded samples, not warmed feature percentiles. Dense summed-area smoothing replaces an aliased nine-sample pilot; prototype figures are not final evidence. Full natural-world generation throughput and retained ore heap remain unmeasured. Runtime drills use the existing scheduler; ore BEs have no individual production tick.

## Actual dedicated fluid server: separate from geometry/kernel

Same 10000-main-pipe physical script, eight main tanks, 16 powered/circuit pump systems and 10151 fluid owners total, plus real oil/refining/plastic machinery. Three lifecycle windows per run, zero clients. Capacities/pressure/oil yield differ deliberately from 0.24.1: not identical hydraulics. Baseline summaries are medians of published server-after-final.json and server-after-final-repeat.json; current table uses the **final code** run fluids-v0250-release-a. The earlier development run fluids-v0250-measure-a (before unchanged-source smelting-cache reuse and final UI/worldgen guards) remains in fluid-server-measure.json; do not pool different development snapshots as repeated identical-code samples.

| Scenario | 0.24.1 mean / p95 / p99 / max ms | 0.25.0 final mean / p95 / p99 / max ms | Current allocation KiB/tick |
|---|---:|---:|---:|
| build | 23.039 / 61.109 / 199.020 / 399.868 | 26.122 / 67.061 / 281.415 / 327.611 | 2348.6 |
| stable | 3.043 / 6.139 / 13.337 / 60.310 | 2.698 / 4.891 / 12.539 / 65.410 | 526.0 |
| construction | 3.694 / 10.166 / 16.936 / 49.344 | 3.285 / 7.600 / 24.074 / 36.046 | 850.6 |
| split-merge | 3.577 / 8.631 / 15.070 / 27.301 | 2.777 / 6.247 / 11.855 / 14.080 | 346.3 |
| unload | 10.850 / 32.974 / 32.974 / 32.974 | 6.014 / 17.374 / 17.374 / 17.374 | 2171.1 |
| reload | 4.166 / 9.863 / 13.132 / 13.132 | 3.361 / 7.395 / 11.291 / 11.291 | 527.2 |

Final stable/construction/split means are 2.698 / 3.285 / 2.777 ms; the earlier development observation was 3.257 / 3.975 / 4.150 ms. This is limited shared-host evidence with changed gameplay, not a TPS improvement claim. **Final stable maximum 65.410 ms is worse than the published 0.24.1 median maximum 60.310 ms**, despite a lower mean. Original development measurements are retained. Build/warmup maxima are 327.611 / 274.931 ms. Few unload samples cannot support reliable tail conclusions.

Final topology: 404186 visits, 69 rebuilds, 4478348 finalize units, 51 aborted builds, 10056 local edits, 479 coalesced chunk marks. 15070 BE sync requests; zero actual packets (no clients). Whole-server post-warm-GC heap 282443848 bytes is not retained ore memory.

All lifecycle ledgers pass: main 500000 + saved source bank 964000 + collected 36000 = **1500000 mB**; auxiliary 64000 including consumed 57600. Final plastic: **31 bars**. A separate JVM validates the unchanged final-world authoritative BE hash `5cbd087857d52c62db538f328319ed8b066b927c807d0ad8d094c5fb16808589`. This is a different new world from the development run, not a replacement expected hash; both checkpoints/worlds remain intact.

Natural generation stores a 16×16 frozen-height attachment per participating chunk (1024 uncompressed value bytes plus array/map overhead). Prepared ServerLevel feature fixtures exclude this WorldGenRegion-only snapshot cost. Ordinary client smoke additionally reloads 5132 exact existing resource/oil owners and 556 original-surface snapshots into a recovery copy, which grows by legitimate new generation to 78626 owners / 845 snapshots and then saves normally. Its shared development config retains spacing 6; shipped fresh default is 16. This demonstrates save/load, not a retained-heap or natural chunk-throughput benchmark. A premature generation-time shutdown hung in Minecraft unload retry and is retained separately in client evidence.

Full factory, MIXED 3D and optional-mod CI are not local passes. Their independent GitHub Actions jobs remain mandatory. No whole-factory TPS, multiplayer bandwidth or natural chunk-throughput improvement is claimed.
