# Issue13 continuation — server safety, damage and measured workload

This record supplements, rather than replaces, [the original0.31 client evidence](../v0310/README.md). No previous world or failed ledger was reset/refilled. Version stays0.31.0; no signals, stations, schedules or0.32 content.

## Actual validation

* `./gradlew build`: **302JUnit**,0failures/errors/skips. Adds source kinetic-budget tests,10000scalar/vector SAT comparisons, a warmed per-query allocation bound, repeated closed-loop history/self-overlap checks and rejected-merge ownership checks. Existing297tests remain.
* Base native development run:547registered,546passed; one new assertion wrongly required a completed turnout leg to remain after *all* cars had already left it. Corrected the assertion to require history only while occupied; the failed log/world are retained. This was not a production history rewrite.
* `runGameTestServer -PgameTestFixture=v0310-completion-checked`: **548/548base passes**. All539old cases and thirty finite chains remain required.
* Attempted second JVM on that base world **with FTB enabled**:548/549passed, old inserter persistence scope check failed. Saved data proves `player:a5f5d9fc-b951-37fe-899c-c73a530b1ba9`, energy731/fuel1237/stage2/phase3/typed hand and all9research levels remain. `ResearchTeams.exists` deliberately denies `player:`scopes while FTB is active; this backend switch is not an implemented migration. No rebind/refill/test disabling was performed. See the two `cross-backend-*.snbt` snapshots and retained archive/log.
* `runGameTestServer -PgameTestFixture=v0310-completion-optional -PwithViewers=true -PwithJade=true -PftbModsDir=build/optional-runtime`: **549/549fresh optional passes** with actual FTB Teams/Library/Architectury, EMI, JEI, Jade, Apothic Attributes and Placebo.
* Same optional world and dependency profile in an **independent JVM**, after the deleted-lookahead guard/intermediate sweep and its additional case: **550/550required passes**, exact persisted3-car owner/fuel/cargo/components/fluid/moving-pose checkpoint verified. The final coupling preflight was followed by302JUnit/build and is repeated in hosted native tests on the published tree; do not silently pretend it preceded this local native run.
* Train/rail source audits and workflow YAML validation pass. All seven hosted jobs are required for this user's requested CI result; final commit/run links are reported at publication.

The original CI armor failure is in [ci-armor-failure.txt](ci-armor-failure.txt): active sunlight fire caused0.7unrelated HP before the controlled physical hit. A non-armored carved pumpkin prevents native zombie ignition. The test asserts undamaged20HP/fire0/nativeArmor0 and retains exact19.44physical /17.44laser values. Gameplay armor/resistances were not weakened or reimplemented.

New native cases exercise: moving head-on and opposing fork approaches; three-car rear history after changing turnout intent;32-car push-pull movement/quarantine/serialization; walls and mobs with direct damage attribution; one component-exact destroyed wagon; cancelled wreck spawn/persisted claim/retry; stale/duplicate/invalid damage targets; actual manual hitscan and flame flight/burning; deleted cached future track without a server crash, phantom fuel debit or lost owner. Known reservations prevent contact and do not inflict fictional damage.

## Real Minecraft train workload

`runTrainBenchmarkServer` creates48actual paid-claim rail segments (6144blocks),8canonical consists sized1/3/8/16/32/64/8/16 and148native stock entities. Mounted synthetic native ServerPlayers issue guarded manual intents; this is a server harness, **not eight network clients** or an automated train feature. Twenty-seven locomotives own216finite coal (864000000J);82cargo wagons own2542named iron items;39tankers own4814823mB. Health, item components, fuel-buffer+spent+remaining-coal and fluid balances are audited every measured tick.

Every consist actually moves both directions. A moving16-car train's physical chunk naturally unloads after diagnostic tickets are removed; its paid state remains frozen, stale fuel access is denied, and148handles recover on reload. Production code never requests these tickets. Complete train/rail records compare exactly in independent JVMs for **all three successful profile worlds** (`before2`, `after1`, `after2`). `train-checkpoint.nbt` is the final after2 source record.

Three-vCPU host, no clients/other game process while measuring,120warmup +400stable ticks. See [BENCHMARKS.md](../../BENCHMARKS.md) for the table and definitions. Whole-server mean34.413→22.980–23.128ms, p9566.822→52.889–53.915ms, maximum99.249→75.167–84.319ms; train-adapter mean24.293→12.378–12.381ms. Exact main-thread allocation24.547→3.112–3.119decimalGB/400ticks. All three windows perform6326280adapter world reads. After2 has a worse maximum/adapter p99 than After1; both runs are retained. No universal20TPS, renderer or networking capacity claim.

JFR identifies per-voxel vector cross products/axis arrays as the dominant allocation site. Scalar SAT and packed-long visited cells remove those allocations without skipping collision checks. `before-jfr-allocation-samples.json` contains sampled whole-recording weights, which are distinct from exact per-stage ThreadMXBean byte counters. Curved/graded sweeps include intermediate orientations and a conservative quarter-block margin; flat straight motion uses an exact endpoint union.

Actual clients0 and JFR network packets0. Projection requests are not packet totals. Existing EN/RU/two-client train controls/menus/observer/audio evidence remains in the original0.31 record. No new client visual run is claimed by this continuation.

An earlier real workload failed because neighboring diagnostic lines kept the selected chunk loaded. That world/report/log is preserved as `trains-v0310-completion-before`; it is excluded from successful metrics. The corrected new fixtures separate lines by128blocks. No old world was modified to obtain a pass.

## Reproduction and retained artifacts

```sh
./gradlew build
./gradlew runGameTestServer -PgameTestFixture=v0310-new-name
./gradlew runGameTestServer -PgameTestFixture=v0310-new-name
# Keep a consistent research backend; use a separate fresh world for optional first/reload.
./gradlew runGameTestServer -PgameTestFixture=v0310-new-optional -PwithViewers=true -PwithJade=true -PftbModsDir=build/optional-runtime
./gradlew runTrainBenchmarkServer -PtrainFixture=trains-v0310-new-name
./gradlew runTrainBenchmarkServer -PtrainFixture=trains-v0310-new-name -PtrainMode=verify
```

Use new names, never a reset. Server modes refuse reseeding existing train worlds and require a fresh successful report from the current process. Raw successful distributions: `server-before-measure.json`, `server-after1-measure.json`, `server-after2-measure.json`; independent-JVM results: corresponding `*-verify.json`. Source JFRs remain in the three workload directories; compressed logs and final native checkpoint are retained here.

Disk space required lossless archival of **closed** native/client fixtures. The `*-archive.json` manifests record external archive locations, SHA256 and every original file hash. Every archive was read back and byte-verified before removing the expanded copy. These are preserved evidence, including failures, not discarded/reset worlds. New native optional archive includes its FTB team records, original checkpoints and all logs. Original0.31 and earlier manifests remain intact.

## Limits

The exact Factorio engine collision solver is closed; raw mass/speed/5-or6energy-per-HP data inform a documented bounded3D adapter. Impacts halt this tick; blocks are not destroyed. PvP/team/pet protections remain. Damaged wreck resources survive even when a native spawn is cancelled; repair/reuse is still absent. Ordinary orderly save/reload ownership is checked; no cross-file power-loss journal is claimed.

Native sparse high-speed interpolation can still cut tight curves. Full multi-wagon S-bend/bridge/tunnel visual acceptance, subjective art/audio review and fixed source A/B templates/diamonds/movable blades remain outside this checked server slice. Do not mark the entire broader visual/foundation parity complete or start0.32 automatically.
