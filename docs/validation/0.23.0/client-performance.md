# Bounded client comparison, 0.22.0 → 0.23.0

Original 0.22.0 jar, then current classes/resources; separate JVMs. The original runs in a copied world under `/workspace/work/transport-v0230-baseline`; it never edits the authoritative 0.23 world. The inherited world display name contains 0.22.0 in both captures; actual loaded mod versions are verified in the startup logs. View `(165.5,78,35.5)`, yaw/pitch 180/12, view distance 12, 854×480, Java 21, llvmpipe/software OpenGL, world simulation frozen, 10-second native F3+L captures. Both see 141 rendered chunk sections. No parallel Gradle/native job runs during the samples.

| Measure | Original 0.22 | Current 0.23 |
|---|---:|---:|
| Client frame samples | 301 | 280 |
| Mean frame time, ms | 32.999 | 35.461 |
| Median frame time, ms | 27.842 | 31.281 |
| p95 frame time, ms | 57.032 | 64.908 |
| Height-16 / turn-1 shaft baked quads, independent cost model | 1948 | 1796 |
| Same shaft vertex int[] payload bytes (not total heap) | 249344 | 229888 |
| Route segments sampled across the whole shaft | 4896 | 62 |

This single software-rendered comparison has a **slower new frame-time sample**. It does not prove performance parity or an FPS improvement; background/environment/JIT/GPU effects are not statistically isolated. The more detailed geometry is bounded and has fewer shaft quads, but that fact alone does not imply a faster frame. Full 10000/1000/500 and mixed *server* benchmarks remain CI work; they do not substitute for a large client-GPU benchmark.

The pure construction timings in geometry-checks.txt include reference/current face counting and instrumentation. They are not an isolated measurement of Minecraft chunk compilation. A requested reload was attempted, but dispatch metrics do not establish an isolated rebuild interval; no build-time gain is claimed. We did not instrument actual GL draw-call totals or total cache heap. The geometry loader stays in the existing solid chunk pass (no extra per-belt surface pass), and its cache is limited to 512 variants per baked model. These are architecture bounds, not measured GL/heap totals.

CSV ticktime values are nanoseconds per client frame, **not server TPS**. Raw client profiling text and chunk dispatch/render counters are retained beside this report. No optional viewer, FTB Teams or two-client combination was run here.
