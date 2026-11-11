# Fluid 0.24.0 → 0.24.1 comparison

Kernel: identical 256-MiB initial / 512-MiB max heaps, three 1000-node warmups, three measured rounds per size. Times include complete settlement with a 256-unit advance budget. Values below are medians of per-round means/quantiles, not pooled percentiles. Allocation is whole-operation thread allocation, not retained heap.

| Members | Operation | 0.24.0 mean ms | 0.24.1 mean ms | Change | Old/new allocation KiB |
|---:|---|---:|---:|---:|---:|
| 100 | create | 0.131614 | 0.165324 | +25.6% | 64.02 / 46.17 |
| 100 | 10000_fill_drain | 1.979057 | 1.473505 | -25.5% | 0.24 / 0.24 |
| 100 | split | 0.032741 | 0.017243 | -47.3% | 14.55 / 3.29 |
| 100 | merge | 0.038148 | 0.008964 | -76.5% | 13.45 / 2.14 |
| 100 | leaf_add | 0.035067 | 0.001338 | -96.2% | 13.88 / 0.37 |
| 100 | leaf_remove | 0.034316 | 0.003825 | -88.9% | 13.55 / 0.07 |
| 100 | tank_add | 0.040059 | 0.001427 | -96.4% | 13.88 / 0.37 |
| 100 | tank_remove | 0.042027 | 0.002649 | -93.7% | 13.55 / 0.07 |
| 100 | availability_16 | 0.067588 | 0.032800 | -51.5% | 26.90 / 5.96 |
| 1000 | create | 1.511909 | 0.724840 | -52.1% | 666.62 / 492.57 |
| 1000 | 10000_fill_drain | 1.712975 | 0.629579 | -63.2% | 0.24 / 0.24 |
| 1000 | split | 0.492374 | 0.170000 | -65.5% | 119.07 / 14.65 |
| 1000 | merge | 0.461203 | 0.170167 | -63.1% | 118.91 / 15.45 |
| 1000 | leaf_add | 0.477616 | 0.003334 | -99.3% | 119.38 / 0.41 |
| 1000 | leaf_remove | 0.500978 | 0.034088 | -93.2% | 119.03 / 0.09 |
| 1000 | tank_add | 0.517693 | 0.002027 | -99.6% | 119.38 / 0.41 |
| 1000 | tank_remove | 0.473915 | 0.032120 | -93.2% | 119.03 / 0.09 |
| 1000 | availability_16 | 0.978966 | 0.355573 | -63.7% | 238.23 / 30.93 |
| 5000 | create | 4.255853 | 4.474695 | +5.1% | 2849.15 / 2154.66 |
| 5000 | 10000_fill_drain | 0.549960 | 0.542731 | -1.3% | 0.24 / 0.24 |
| 5000 | split | 1.167431 | 1.088495 | -6.8% | 392.59 / 66.95 |
| 5000 | merge | 1.086986 | 1.143236 | +5.2% | 398.76 / 74.19 |
| 5000 | leaf_add | 1.153433 | 0.009351 | -99.2% | 399.20 / 0.41 |
| 5000 | leaf_remove | 1.074038 | 0.045044 | -95.8% | 398.85 / 0.06 |
| 5000 | tank_add | 1.139247 | 0.004395 | -99.6% | 399.20 / 0.41 |
| 5000 | tank_remove | 1.111674 | 0.038466 | -96.5% | 398.85 / 0.06 |
| 5000 | availability_16 | 2.264021 | 2.197563 | -2.9% | 791.23 / 141.09 |
| 10000 | create | 7.279268 | 17.937867 | +146.4% | 5738.35 / 4368.19 |
| 10000 | 10000_fill_drain | 0.315234 | 0.345571 | +9.6% | 0.24 / 0.24 |
| 10000 | split | 2.056115 | 2.688750 | +30.8% | 797.32 / 148.34 |
| 10000 | merge | 2.109523 | 2.694470 | +27.7% | 813.62 / 165.72 |
| 10000 | leaf_add | 2.197295 | 0.008525 | -99.6% | 814.05 / 0.41 |
| 10000 | leaf_remove | 2.117228 | 0.087071 | -95.9% | 813.71 / 0.06 |
| 10000 | tank_add | 2.203082 | 0.002796 | -99.9% | 814.05 / 0.41 |
| 10000 | tank_remove | 2.232932 | 0.087212 | -96.1% | 813.71 / 0.06 |
| 10000 | availability_16 | 4.413275 | 5.360043 | +21.5% | 1610.82 / 314.02 |

## Kernel tails, 10000 members

| Operation | Old/new median ms | Old/new p95 ms | Old/new p99 ms | Observed old/new maximum ms |
|---|---:|---:|---:|---:|
| create | 7.2793 / 17.9379 | 7.2793 / 17.9379 | 7.2793 / 17.9379 | 9.5938 / 21.3927 |
| split | 2.0377 / 2.6656 | 2.3186 / 3.2600 | 2.4860 / 3.3375 | 5.0128 / 4.0697 |
| merge | 2.1263 / 2.5788 | 2.5730 / 3.2750 | 2.8873 / 3.5295 | 8.6485 / 3.8653 |
| leaf_add | 2.1228 / 0.0061 | 2.5954 / 0.0221 | 3.6590 / 0.0264 | 7.4947 / 0.0366 |
| leaf_remove | 2.0577 / 0.0759 | 2.5414 / 0.1087 | 3.2166 / 0.1349 | 9.2567 / 0.2298 |
| availability_16 | 4.1107 / 5.2599 | 7.1437 / 6.3916 | 8.6911 / 7.4980 | 19.5937 / 7.8283 |

## Creation follow-up: eight size-matched warmups, thirty new networks per size

The three single-trial creation results above were unstable, so this separate longer run is retained alongside them. Do not substitute its best trial for the original data.

| Members | Old/new mean ms | Old/new median ms | Old/new p95 ms | Old/new p99 / maximum ms |
|---:|---:|---:|---:|---:|
| 100 | 0.5498 / 0.2626 | 0.5050 / 0.2147 | 0.8461 / 0.6045 | 0.8975 / 0.6488 |
| 1000 | 2.2025 / 0.8508 | 1.4993 / 0.6769 | 4.2265 / 1.8616 | 7.0686 / 3.7429 |
| 5000 | 15.0823 / 3.9866 | 14.0060 / 4.0434 | 19.6262 / 4.8678 | 19.7947 / 7.0097 |
| 10000 | 9.1093 / 7.3743 | 8.8641 / 2.4571 | 13.6529 / 20.9068 | 14.4513 / 21.5906 |

## Real dedicated server: two independent JVM/world runs per version

Each run contains three scenario cycles. Entries are median per-run summaries with the observed run range. They are not merged p95/p99 distributions. Startup/build, warmup, checkpoint saving and steady ticks are separated. Zero connected clients in every run.

| Scenario / metric | 0.24.0 ms (range) | 0.24.1 ms (range) | Median change |
|---|---:|---:|---:|
| build / meanMs | 30.353 (30.019–30.688) | 23.039 (22.354–23.724) | -24.1% |
| build / p95Ms | 71.472 (69.218–73.726) | 61.109 (55.094–67.124) | -14.5% |
| build / p99Ms | 198.444 (189.860–207.029) | 199.020 (186.709–211.330) | +0.3% |
| build / maxMs | 450.736 (381.864–519.607) | 399.868 (378.862–420.874) | -11.3% |
| stable / meanMs | 2.784 (2.654–2.913) | 3.043 (2.989–3.097) | +9.3% |
| stable / p95Ms | 5.236 (4.697–5.774) | 6.139 (6.031–6.247) | +17.3% |
| stable / p99Ms | 13.982 (11.969–15.995) | 13.337 (12.338–14.335) | -4.6% |
| stable / maxMs | 43.673 (38.741–48.606) | 60.310 (51.288–69.333) | +38.1% |
| construction / meanMs | 12.229 (11.722–12.736) | 3.694 (3.442–3.946) | -69.8% |
| construction / p95Ms | 25.794 (24.570–27.017) | 10.166 (10.017–10.315) | -60.6% |
| construction / p99Ms | 33.112 (32.498–33.726) | 16.936 (14.395–19.476) | -48.9% |
| construction / maxMs | 40.705 (40.346–41.065) | 49.344 (23.705–74.983) | +21.2% |
| split-merge / meanMs | 5.908 (5.695–6.120) | 3.577 (3.467–3.687) | -39.4% |
| split-merge / p95Ms | 21.791 (20.016–23.566) | 8.631 (8.587–8.676) | -60.4% |
| split-merge / p99Ms | 26.703 (24.831–28.575) | 15.070 (14.318–15.823) | -43.6% |
| split-merge / maxMs | 31.054 (29.964–32.145) | 27.301 (18.241–36.362) | -12.1% |
| unload / meanMs | 10.504 (10.076–10.933) | 10.850 (8.226–13.474) | +3.3% |
| unload / p95Ms | 15.953 (14.778–17.128) | 32.974 (15.530–50.418) | +106.7% |
| unload / p99Ms | 15.953 (14.778–17.128) | 32.974 (15.530–50.418) | +106.7% |
| unload / maxMs | 15.953 (14.778–17.128) | 32.974 (15.530–50.418) | +106.7% |
| reload / meanMs | 9.283 (8.712–9.854) | 4.166 (3.908–4.424) | -55.1% |
| reload / p95Ms | 22.488 (21.053–23.923) | 9.863 (9.304–10.423) | -56.1% |
| reload / p99Ms | 26.392 (26.202–26.582) | 13.132 (13.071–13.192) | -50.2% |
| reload / maxMs | 26.392 (26.202–26.582) | 13.132 (13.071–13.192) | -50.2% |

| Whole-run count / memory | 0.24.0 range | 0.24.1 range |
|---|---:|---:|
| visits | 2554909–2560201 | 404189–404228 |
| rebuilds | 923–929 | 69–69 |
| chunkMarks | 69949–69976 | 472–478 |
| rawChanges | 20021–20021 | 20021–20021 |
| syncRequests | 1110579–1110581 | 15081–15081 |
| heapAfterWarmGc | 283370352–283729376 | 282239408–282340408 |
| stable thread allocation KiB/tick | 518.6–519.9 | 504.5–516.8 |
| construction thread allocation KiB/tick | 1605.4–1611.4 | 830.1–837.4 |
| split-merge thread allocation KiB/tick | 661.6–726.2 | 480.5–488.5 |
| reload thread allocation KiB/tick | 722.1–993.4 | 214.6–285.0 |

`chunkMarks` counts segment-adapter setUnsaved(true) requests, not disk writes or all world chunk mutations. `syncRequests` counts BE update requests; JFR records **zero actual sent packets with zero clients**. New `finalizeWork`, `localEdits`, `frozenMembers` and `aborts` have no matching old counters and must be reported separately. The heap sample is the entire warmed server after explicit GC, not retained graph size. No universal TPS or peak-latency claim follows from these samples.
