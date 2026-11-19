# Issue14 /0.31.1 evidence and explicit limits

Baseline: `37cd9988a65e13e18a2d4b8982fdd6d3b79a7ef5`. The original-gameplay client run added only the marked one-shot QA fixture/version metadata, before the collision/planner changes. Original-main CI44342213020 was already green. This directory preserves the new raw observations; earlier0.31 evidence/world archives are retained.

## Reported120km/h crash

No actual user crash report/stack was attached to the issue. It was requested during work and had not arrived. Actual Minecraft1.21.1/NeoForge21.1.252 clients and a dedicated server were run on original and changed gameplay in the new marked `network-v0311` world. **Neither run reproduced the reported crash. There is no observed first failing FactoryCraft frame and no asserted crash root cause.** No speed cap, disabled contacts, suppressed exception or fabricated crash log was used.

Original single locomotive trace crossed59.9/102.8/137.1/164.4/203.7km/h and later258.19km/h. A three-car original drive also passed120km/h. Changed-code single locomotive first reached255.35km/h and then the exact source1.2(259.2km/h) cap, eight-car consist134.46km/h, and the separate three-car annex crossed a90° curve, S/grade transition and a real tunnel. The changed three-car grade was recorded at143.99km/h and tunnel164.23km/h with a real RU observer spectating the synchronized locomotive. Forward/reverse/coast/brake and real edge/chunk crossings were exercised. The exact interactive1.2 peak is retained in `client-source-cap-server.log.gz` with stable train/edge IDs and in `source-cap15-native.png`; pure and native paid-motion tests independently cover that cap.

Files: `baseline-*.png`, baseline logs, `post-eight-car-threshold.png`, `final-forward*.png`, `final-curved-high-speed.png`, `final-curve-grade-drive.png`, `ru-observer-high-speed-native.png`, and closed final EN/RU/server logs. Offline authentication/public-key warnings and the pre-existing missing offshore-pump moving model are retained; they are not identified as the train crash.

## Geometry and client verification

Shared mesh audit: BODY half-width1.09000003m, length7m, highest roof3.42000008m; cargo2.73000002m/tanker3.17000008m; wheel bottom.02m. StockGeometry hull is2.25m×7m, bottom.005m and per-kind top3.45/2.76/3.20m. Source spacing remains7m. One OBB covers the full body/roof/couplers with small side/top allowance, not a shrunken invisible core.

Runtime world contact uses actual collision-shape subboxes and continuous translation with a measured per-interval corner-rotation bound. Adjacent block origins catch shapes extending from below/alongside their voxel. Construction uses the same MotionHull and two-bogie pose at≤.125m intervals. Physical bed sampling remains exactly the published.25m algorithm; a four-case golden hash test against37cd998 protects old curved/grade/half-diagonal cell/box records. Spatial reservations discover via a chunked grid, then use staged OBB/SAT. Exact native player/mob collision and both ProjectileUtil overloads are covered by native tests. A further native section-edge regression checks that a real coupler beyond Minecraft's original2m discovery margin is found. StockGeometry supplies bounded4.5/4.5/1m section-search padding; the existing final query-box filter and stock narrowphase still decide hits, with no remote loading. Native Level/projectile overload testing exposed a real missing adapter selector; it was repaired with explicit descriptors rather than weakening the expected miss/hit assertions.

F8 shows actual BODY edges pink, hull green, discovery AABB blue; proposed future hulls are amber. `ru-curved-day-mesh.png`, `ru-curved-day-debug.png`, `ru-curved-close.png` and native F2 captures show all three types on a real curve. The complete exact0/22.5/45/67.5/90° screenshot matrix and every long-consist curved visual scene are not claimed. Pure randomized pitched OBB tests, native diagonal wall/fence/picking checks and pre-existing grade/long-consist/junction tests provide independent geometric coverage. Wider issue13 route-aware high-speed client interpolation/template/repair limitations remain documented in TRAINS.md.

## Placement behavior matrix

| Behavior | Previous FactoryCraft | Create1.21.1 reference | FactoryCraft0.31.1 |
|---|---|---|---|
| Start | Real short paid starter | Real track start | Retained |
| Connect | Two clicked endpoints | Select and connect | Retained, server validated |
| Ground end tangent | Anchor-to-target chord | Camera direction | Camera yaw, independent of pitch |
| Heading families | Sixteen Factorio families | Create track directions | Same sixteen incl. actual1:2 half diagonals |
| Existing endpoint | Actual tangent | Actual track targeting | Retained actual tangent |
| Slope | Smooth cubic1:4 maximum | Create-specific connector | Existing Minecraft adaptation retained |
| Endpoint area | No region | Cached5×5 checkered cluster | Local cached5×5 green/amber/red,≤1new hint/tick |
| Invalid path | Red geometry/reason | Invalid preview | Red geometry/reason/cost retained |
| Plants/torches | Every nonair clearance block rejected | Replaceability/shape checks | Empty shapes accepted; protected eligible bed replacement |
| Finish/cancel | Clear after success, C/sneak | Select/cancel gestures | Retained R/X/C, automatic reset; no client-only-unload false cancellation |
| Payment/protection | Server atomic claim/debit | Server confirmation | Retained with deferred original-state NeoForge drops |
| Geometry |3×7×4 AABB voxel sweep | Contraption-specific | Mesh-sized bogie-oriented continuous clearance |

Actual EN client: `starter-placed.png`, `grid-fixed-cell-north.png` vs `grid-fixed-cell-east.png` use the same targeted ground cell with different camera yaw; red radius/geometry and green accepted regions visibly update. `curve-thirdperson.png` records the committed server curve. `final128-built.png`/`final256-built.png` and the source-cap server log prove two real128m endpoint extensions across client view-distance boundaries:37→39edges and2186→2314canonical rail-item credits. Interactive placement QA used Creative; finite Survival payment/rollback is covered by native tests. Grid is advisory: unloaded/unknown claims remain server checked. The existing active64m construction ray can show an unreachable surface red; actual placement always uses ordinary interaction reach. MISS/air has no ghost.

## Ownership and persistence

`client-ledger.json` and the post-cap `client-final-ledger.json` audit closed real-world NBT: all original12car UUIDs retained,4trains/15cars,186iron,617285mBwater,512000000J across finite coal, burner buffers and spent energy, no wrecks. The annex explicitly added3cars/32coal/31iron/123457mB once; it never refilled/reset older owners. `owners-before` and `owners-final` contain the canonical snapshots. `client-world-independent-jvm.log.gz` loads the same real-client world in a separate server JVM, reports4trains/15cars, freezes simulation, saves and shuts down normally; `client-world-reload.json` confirms exact canonical NBT.

New native disk checkpoint separately saves a genuinely paid≥120km/h phase with named component-exact cargo/fluid, then checks its exact train/car/route/fuel/motion NBT in another JVM. Older independent-JVM fixtures and all thirty finite production chains remain enabled.

## Retained failures and validation boundaries

* Initial native550passed before new regressions; the subsequent556base run passed. New JUnit reference extents initially failed because the test still used old3×7×4 constants; its independent SAT computation now uses actual hull extents.
* `native-disk-full.log.gz`: a diagnostic run exhausted workspace disk; its world was retained in a byte-verified lossless archive. It is not reported as a pass. Older raw profile JSON/worlds were losslessly compressed with checksum/tar comparisons to free space, never reset/reseeded.
* `native-optional-stall.log.gz` /thread dump: one optional run stopped making progress while a pre-existing direct-circuit disk case waited in Minecraft IOWorker/worldgen. It was terminated and retained. A fresh run uses `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4`; this is a test-environment setting, not a gameplay workaround or claimed train-crash fix. The two hosted native jobs use that same explicit worker setting.
* `native-double-plant-failure.log.gz`: the double flower upper half survived a staged placement; deferred neighbor-shape updates, in addition to neighborChanged notifications, corrected the orphan. Its assertion was retained.
* `native-projectile-overload-failure.log.gz`: the added Level/projectile miss assertion failed; the corresponding failed world is retained. It led to explicit overload selectors in StockPickingMixin. No test was disabled.
* Client long-line QA also exposed the1.21.1 ClientLevel.hasChunk always-true API: a genuine non-creating client cache probe now separates an unloaded anchor from a removed one.
* An initial annex placement rejected an excessively expanded temporary construction hull at a grade foundation. The log and partial scene were retained; the actual shared continuous rotation bound replaced the constant construction expansion. The diagnostic resumed only missing rail segments and claimed new finite stock once. No prior resources were reset.

* `native-intermediate-bed-reload-failure.log.gz`: a checkpoint created by the temporary doubled physical-bed sampler had missing projections after restoring the original published bed discretization. It was retained, not repaired/reset to obtain a pass. A fresh final-format world is validated in two JVMs; golden hashes preserve the published0.31.0bed.

Final build/native/restart/workload results are recorded in VALIDATION.md and the final logs/reports next to this file. Hosted full/MIXED/fluid workloads remain GitHub Actions; an unexecuted or pending job is not a pass.

## Technical references

* Create mc1.21.1/dev `TrackPlacement.clientTick/tryConnect`, `TrackBlockItem`, carriage/bogie source: camera-sensitive cached endpoint selection,5×5 hints, separate graph/visual handles. Original FactoryCraft implementation/art, no Create dependency or imported code/assets.
* Superb Warfare tag `1.21-0.8.3`, commit`b794e22967c9b731d59cd9b5b90c4866d6e90c7d`, `tools/OBB.java`, OBBEntity/part interface (repository LGPL3): reviewed rotated local axes, SAT and ray/part separation. No source/assets copied; FactoryCraft uses independent scalar double math and its own hull.
* Immersive Vehicles master `BoundingBox`/JSONCollisionBox and `mcinterfaceneoforge1211/gradle.properties` were actually inspected. Current source declares25.0.0/MC1.21.1/NeoForge21.1.265, not the linked24.0.0. Local/global part positions are separated, but inspected box intersection is axis-aligned. It is not claimed to provide FactoryCraft's oriented voxel/SAT solution, and its code/art is not imported.

## Bounded workload result

Preserved artifact pair:8trains/148cars/6144m real rails,2542items/4814823mB/864MJexact, actual unload/reload. New-artifact separate-JVM exact-owner verification passes. Final stable whole-server mean20.869→19.688ms, p9545.010→44.682ms, p9963.211→65.274ms, max86.356→93.917ms; allocation3.164→4.467decimalGB/400ticks. Lower mean, worse p99/maximum/memory; no general TPS claim. The exact JARs are frozen in each fixture's mods directory and additionally retained externally with recorded hashes. Details and the more expensive bounded placement profile: [BENCHMARKS.md](../../BENCHMARKS.md).

Final local result:314JUnit/no skips,558fresh optional native +558in a second JVM, followed by559final native on that saved world after adding bounded section discovery. All pass, including the strengthened native no-tunneling assertion. The high-speed canonical checkpoint keeps UUIDbffee111-8f8a-4fed-9c0b-6e051b4d67cb across the three processes. The intermediate checkpoint snapshots are historical failed-fixture evidence; `native-checkpoint-final` contains the successful final one. The section-discovery-only adapter was added after interactive screenshots and then validated by the full native suite and final actual-server artifact profile; no new exact-angle client screenshot matrix is claimed.
