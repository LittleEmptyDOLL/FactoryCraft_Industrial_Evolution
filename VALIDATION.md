# Validation — 0.3.0, 8 November 2026

Continues `main` commit `9f96696` (0.2.2). Target remains Minecraft **1.21.1 / NeoForge 21.1.252 / Java 21**. This release implements the first stage-3 fluid logistics slice, alongside the existing automated factory. It does not implement oil/chemistry or complete Factorio/Space Age.

## Automated checks

* `./gradlew build`: **62 JUnit tests**, zero failures/errors/skips. Seventeen new fluid tests cover simulated fill/drain, atomic ownership, incompatible identities, drainable overflow/int limits, sleeping/wakeup, graph split/reconnect, bounded FIFO work, callback ordering/failure, six-way pressure stability, unavailable endpoint freeze and randomized 10000-cell conservation. Existing belt/power/benchmark/layout tests remain green.
* `./gradlew runGameTestServer`: **62 required tests pass** after the final capability changes, followed by another separate server process with **62 passed**. Sixteen new fluid scenarios exercise finite pipe flow, powered/unpowered pumps, real generator/pole/source-water supply, component identity and defensive copies, cached sided handlers after rotation, blocked-output energy behavior/wakeup, unload/reconstruction, actual harvested-item placement, bucket interaction, two shared access ports, vertical transport, 32-bit menu quantities, live NBT wakeup, early ChunkEvent.Unload and the separate-process disk fixture. Existing production line and multiblock checks remain green.
* Separate JVMs seed, then verify a finite **8000 mB** component-bearing fluid fixture across **four chunks**. Verification reads actual region files without replenishing missing fluid. Existing belt/machine/held-item/manual/automatic-multiblock disk fixtures also report `VERIFIED`.
* `./gradlew runBenchmarkServer -PbenchmarkMode=verify`: **PASS** on the actual saved **0.2.2** full-factory world (10000 belts, 1000 producers, 500 inserters, 250 industrial multiblocks). Exact authoritative state hash matches before resuming, and resumed conservation/identity checks pass. The GridConsumer extension preserves existing production consumers. This compatibility check does not measure a large in-world fluid factory; the existing full benchmark has no fluid load.

* A fresh 0.3.0 full `./gradlew runBenchmarkServer` also reports **PASS**: all 10000 belts, 1000 producers and 500 inserters worked, item/FE ledgers remain exact, and shared-grid split/rejoin succeeds. The raw report is `docs/benchmarks/v030-full-measure.json`; a separate JVM verifies the new saved checkpoint. This local run averaged 34.474 ms with p95 76.391 ms and 103/600 ticks above 50 ms; a concurrent Gradle task listing affected part of the interval. It is a correctness regression check, not a controlled timing comparison or an under-50-ms guarantee. Zero clients; no fluid nodes are included.

## Problems found and resolved

The first four-chunk fluid fixture seeded 8000 mB but reloaded 7958 mB. In-memory round-trips had passed. Target ChunkMap source inspection showed ChunkEvent.Unload occurs before serialization and before later BE unload callbacks; the outgoing chunk can leave the visible cache while still being referenced by the fluid graph. Level.blockEntityChanged can then skip its dirty mark. The fix checks canonical chunk/BE ownership before simulation or capability mutations, marks the cached owning chunk unsaved immediately and freezes/detaches fluid nodes at the early chunk event before the snapshot. A regression test checks this earlier lifecycle boundary and stale handlers.

The failed local fixture was preserved separately for diagnosis; a fresh isolated GameTest world was then seeded and verified in subsequent JVMs, with exact 8000 mB retained. The failed checkpoint was not silently repaired. Degree-damped pressure was also added after a six-way junction test exposed oscillation in unrestricted simultaneous pair equalization. FLUIDS.md records the resulting equation and integer tolerance.

## Client and API checks

`./gradlew runClient -PsmokeWorld=FactoryCraftSmoke` runs the actual client under Xvfb/Mesa and loads the old 0.2.2 QA world. `/factorycraft waterworks` creates a powered source → pump → four pipes → tank chain. The tank filled automatically; its Russian menu showed water and a live 15823/16000 mB gauge. The world was saved with the ordinary Save and Quit menu, then opened with the final compiled capability changes: the same **15823 mB** remained, and the tank GUI opened correctly. A client screenshot is retained under [docs/screenshots](docs/screenshots/fluid-tank-0.3.0.png). Original pipe/device models, direction arrow, active texture and EN/RU resources are included. Audio uses the local null backend; unavailable Mojang service keys do not prevent local play.

NeoForge IFluidHandler/FluidStack/FluidUtil, data components, capability registration/invalidation, bucket interaction and vanilla menu/BlockEntityData/chunk-event APIs were checked against resolved **1.21.1 / 21.1.252** sources and compiled/exercised in the client and dedicated server. Client tint uses IClientFluidTypeExtensions and stays under `factorycraft.client`. No Forge-version substitutes or proprietary assets are used.

## Scope and continuation

The graph uses active edge/pump queues and cached loaded adjacency; no per-fluid entity or individual fluid-block ticker. See FLUIDS.md for capacities, the finite local-pressure approximation, work limits, pump energy and transport order. The natural water source is deliberately unlimited; finite inventory transfers conserve exact volume and components. Existing schemas remain unchanged; new fluid blocks use native FluidStack schema 1 and preserve drainable over-capacity state.

Two actual network clients, natural player-distance chunk unload, external-mod interoperability and fluid-world TPS remain unverified. Current bucket/capability tests use real server APIs; this is not proof of all mod combinations. Steam, oil, chemistry, underground pipes, temperature and fluid machine recipes remain planned. Next concrete task: transactional water-to-steam boiler/steam engine and fluid processing codecs, documented in IMPLEMENTATION_PLAN.md.

## Changed areas

Independent fluid cell/transport core; loaded world adapter and guarded persistent fluid BE; pipe/tank/pump blocks and block items; grid-consumer abstraction; fluid menu/client screen; registrations/configuration/commands/wrench; original textures/models/recipes/book unlocks/EN-RU; JUnit/GameTests; version 0.3.0 and continuation/model/validation documents.

---

# Historical validation — 0.2.2, 8 November 2026

Continues `main` commit `fd58d7f`. Target remains Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21. This milestone delivers the requested real-world factory benchmark and profile-guided hot-path optimizations; it does not implement fluids or claim complete Factorio/Space Age support.

## Automated checks

* `./gradlew build`: successful, **45 JUnit tests**, zero failures. Eight additional tests validate exact non-overlapping full/smoke layouts, closed real belt loops, inserter endpoints/industrial footprints, actual shared-grid connectivity, timing percentiles/partial windows and invalid inputs. Existing transport/power/layout/conservation tests remain.
* `./gradlew runGameTestServer`: **46 required tests passed** after the persistence/iterator changes, then **46 passed** in a subsequent server process. Existing belt/machine/hand and manual/automatic assembly disk fixtures report `VERIFIED`; schemas and first-factory behavior remain compatible.
* `./gradlew runBenchmarkServer`: full, normal dedicated server **PASS** with **10000 belt segments / 1000 production machines / 500 inserters**, plus 250 generators, 499 poles and 1750 non-ticking industrial parts. All 10000 segments changed; all producers completed recipes; all inserters delivered. Includes shared-grid removal/rejoin, 20000 copper carriers, 128000 iron-equivalent units and exact FE accounting. Setup uses its separate marked `run-benchmark` world, not client or GameTest saves.
* `./gradlew runBenchmarkServer -PbenchmarkMode=verify`: **PASS** in a new JVM. The complete canonical authoritative NBT hash matches before its first simulation tick (inventory, progress, FE, fuel, hands, carrier positions/preferences and part identities; client timestamps excluded). After resuming ten ticks, the inventory/identity audit passes and a new valid checkpoint is saved. A further verify also passes, proving checkpoint advancement works.
* Negative checks: attempting a second measurement without reset returns nonzero and preserves the successful measurement report; changing the expected state hash returns nonzero with `Exact persisted ... changed across restart` and does not silently rewrite the bad checkpoint. Restoring the original checkpoint permits a successful subsequent verification. Raw guard outcomes are committed under docs/benchmarks.
* The small 100-belt / 20-producer / 10-inserter prototype and a new-process reload passed before the full profile. It was used to validate wiring and lifecycle, not as evidence of full-load performance.

## Full-world performance evidence

See BENCHMARKS.md and committed JSON/JFR summaries in docs/benchmarks. Latest local run: two available vCPU, 2 GiB server heap, 65 forced ticking test chunks, one 1749-node electrical graph, 600 measured ticks, **zero connected clients**. Mean **24.494 ms**, p95 **32.395 ms**, p99 **56.240 ms**, maximum **91.566 ms**; eight ticks exceeded 50 ms. Every belt moved and performed **1129200** inter-segment transfers; all 1000 producers and 500 inserters performed actual work. The ledger and exact restart check pass.

In the pre-optimization development profile with the same layout/warmup/recording settings, mean was 32.153 ms and p95 42.985 ms; sampled allocation weights were about 5.700 GB versus 1.216 GB after. The profile located per-node ArrayList iterators and needless comparator-neighbor work during internal dirty marking. Indexed input loops and separated internal dirty/packet paths reduce those costs. These are local statistical observations, not a controlled universal speedup or a guarantee that every tick is under 50 ms. p99/maximum did not improve in this pair.

Vanilla JFR packet events are collected and filtered to the actual measurement interval. With zero clients, their counts are zero; this does **not** verify multiplayer bandwidth. BE sync request counts are retained separately and are not packet counts. Client rendering, player-driven chunk activity, other mods, long-duration factories and non-flat worlds remain different workloads. Correctness PASS has no hidden hardware timing threshold and does not skip equipment to inflate TPS.

## Client and API verification

`./gradlew runClient -PsmokeWorld=FactoryCraftSmoke` launched a real 0.2.2 client under Xvfb/Mesa, loaded the existing 0.2.1 QA save and opened the existing automatic industrial furnace through a part. The Russian GUI still showed formed status and 32 ticks / 20 FE. The world was saved using the menu; the benchmark remained inactive in this ordinary integrated world. Local Mojang services key retrieval remains unavailable as before, without preventing local play.

The used NeoForge events, server chunk pinning, persistence/neighbor APIs and Minecraft JFR packet event names/fields were checked against the resolved target sources and then exercised on dedicated servers. No Forge-version substitutions, extra dependencies or copied assets were introduced.

## Changed areas and next task

New isolated benchmark run/task guards, deterministic independent plan, timing summaries, opt-in telemetry, packet/CPU/allocation JFR recording, full-world audit and canonical restart checkpoint; reusable Python JFR analysis; typed belt/power snapshots; small hot-path persistence/loop changes; CI full measurement/reload and artifact reports; version/architecture/feature/continuation documentation. Runtime factory code still never force-loads neighbor chunks, and inventory mutations retain neighbor notifications. Game data schemas are unchanged.

Next: stage-3 fluid storage/transactions and usable pipe/tank/pump adapters, with a documented throughput model. Two real clients and natural-distance unload remain open; the new packet recording is available for that work.

---

# Historical validation — 0.2.1, 8 November 2026

Continues `main` commit `5321f9d`, including the user's NeoForge change from 21.1.265 to 21.1.252 on top of merged PR #2. Final target: Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21. This change implements the user's one-item/one-structure placement rule for the existing industrial furnace. Later fluid/research/space systems and the full factory TPS benchmark are not claimed.

## Automated results

* `./gradlew build` on NeoForge 21.1.252: successful; **37 JUnit tests**, zero failures. The unchanged independent transport/power/layout suite remains green.
* `./gradlew runGameTestServer`: **46 required tests passed**, followed by another separate process with **46 passed** on the final NeoForge 21.1.252 target. The same checks previously passed on 21.1.265 before integrating the user's remote update. All 30 existing tests remain, including old manual assembly behavior. Sixteen new tests cover actual survival/creative ItemStack placement, all four orientations, obstruction in every part cell, living-entity collision, full NeoForge multi-place cancellation with replaceable-plant restoration and item refund, breaking all eight cells, creative removal, cached forwarding ports and late generated orphans, occupied-offhand sneak-wrench, build-height rejection, unloaded-neighbor rejection without force loading, the complete automatic line, partial-progress BE reconstruction, four-chunk disk persistence, explosion callbacks over all cells and whole-machine pick-block.
* New disk fixture reports `FACTORYCRAFT_AUTOMATIC_DISK: SEEDED` then `VERIFIED generated identity and inventory across four chunks` in different JVMs. Existing belt/machine/inserter disk fixture and schema-1 manual-assembly fixture also report `VERIFIED`.
* The destruction test places/breaks eight complete structures, once through each possible cell, and checks exactly one machine, three raw iron and five diamonds per cycle, zero casing items, and no remaining machine blocks. Explosion verification invokes Minecraft's actual block explosion-hit callbacks over every cell with non-decaying drops; it is not a TNT trajectory/physics test.
* Placement rejection checks that the machine item count and all original blocks remain unchanged. The protection test uses the real `ItemStack.useOn` snapshot transaction and a temporary EntityMultiPlaceEvent cancellation listener, not a stand-in placement method.

## Client check

The initial client check on NeoForge 21.1.265 (`./gradlew runClient -PsmokeWorld=FactoryCraftSmoke`) loaded the existing QA save under Xvfb/Mesa. In survival, placing one industrial furnace from a stack of two created all eight blocks immediately and left exactly one item. The controller at (61,71,61) reported `Assembly: {Schema:2, Formed:1b, Automatic:1b}`. Clicking a generated part opened the Russian GUI showing `Собрана` and the 32 ticks / 20 FE recipe preview. Shift+wrench on a part, with a torch in the offhand, removed the whole structure and left one dropped industrial-furnace item. The original 0.2.0 fixture at (42,72,42) retained `Automatic:0b` and its formed identity. The final NeoForge 21.1.252 client also loaded that save and repeated actual survival placement in an isolated area at (81,71,61): a stack of two became one, all eight cells formed with `Automatic:1b`, and a generated part opened the `Собрана` GUI. The QA world is saved through the client menu after both checks.

The first broader test run caught part destruction before the controller's initial onLoad callback. Identity lookup for removal now works during that interval; automation still requires loaded/ready state. Subsequent full server runs pass. The isolated development environment still cannot retrieve Mojang services keys; local gameplay and dedicated tests work.

## Migration and limits

Assembly/casing metadata schema 2 reads schema 1, with missing `Automatic`/`VirtualPart` defaulting to false. Old manual structures, inventories and casing items are preserved. New placement uses one machine item and ordinary stone bricks in its crafting recipe, with no separate casing ingredients. Internal parts are hidden from the creative tab, never drop casing items, and self-remove when a genuinely missing controller becomes observable. Runtime code never loads a neighbor chunk to place, validate or demolish a machine.

Wrench packing/breaking drops inventory separately; it does not store FE or unfinished progress in the returned item. Stable placed-machine saves preserve both. This release has one fixed 2x2x2 large-machine pattern; generic JSON patterns and placement-volume overlays are not implemented. Natural-distance unload and two real network clients remain open, as does the 10000/1000/500 world benchmark. See IMPLEMENTATION_PLAN.md for the next task.

## Changed areas

One-item placement adapter and registration; generated-part identity/loot/lifecycle; automatic dismantling and creative/pick-block behavior; piston resistance; example command; crafting/book unlocks and EN/RU resources; 16 integration scenarios; version 0.2.1 and continuation/architecture documentation. No Powah code or assets were copied; its older ReactorItem/Builder interaction was used only as a reference, and actual integration APIs were verified against the resolved NeoForge 1.21.1 sources.

---

# Historical validation — 0.2.0, 8 November 2026

Continues the user's merged `main` commit `ec97810` (PR #1). Minecraft 1.21.1, NeoForge 21.1.265 and Java 21 remain unchanged. Larger machines may use multiblocks, as permitted by the user; this release implements a real 2x2x2 industrial furnace alongside the power topology work.

## Automated checks

* `./gradlew build`: successful, **37 JUnit tests**, zero failures. Existing belt/energy tests remain. Fifteen power topology tests cover negative coordinates, spherical coverage, splits/merges, independent network availability, stale-route protection, generation conservation, overlapping coverage, bounded/fair recipient scanning, configuration changes, mutations at every rebuild phase, continuous additions, brute-force connectivity comparison and a 10000-pole budget exercise. Three layout tests cover all orientations, a non-square cuboid and invalid inputs.
* `./gradlew runGameTestServer`: **30 required tests pass**. The previous 17 transport/production tests remain. Thirteen additional scenarios exercise incomplete/atomic formation, overlapping ownership rejection, all four orientations, controller/part inventory sharing, cached ports after destruction/unload, shared FE, pause/resume, one-time controller drops, automatic production through a casing, restoration of actual partial progress, orphan repair, an occupied-offhand sneak-wrench and saved identities across four chunks.
* Separate server processes emit `FACTORYCRAFT_MULTIBLOCK_DISK: SEEDED` then `VERIFIED`. The fixed 2x2x2 fixture crosses four chunk boundaries and preserves the controller inventory and seven matching UUID/anchor pairs. Existing belt/machine/hand disk persistence also continues to pass.
* Integration fixtures now use **`run-gametest/world`**, separate from the ordinary development server and `run/saves/FactoryCraftSmoke`. A new isolated fixture was seeded after correcting load-order handling; verification runs use the persisted fixture without silently repairing it.
* `git diff --check`: clean. Generated JSON and the packaged mod are checked during final packaging.

The independent 10000-pole test uses 4096 logical work units per call. A representative run completes in **84 calls**, checks **30002 spatial candidates**, and uses approximately 40–133 ms aggregate local CPU time, depending on concurrent load. The budget includes empty bucket probes. This is not a hard real-time deadline and is not the Minecraft 10000-belt / 1000-machine / 500-inserter test. The belt kernel load exercise remains independent of world/network costs.

## Client checks

A real 0.2.0 client under Xvfb/Mesa loaded the existing 0.1.1 QA world. `/factorycraft smelter` built a controller plus seven casings, generator/pole and two chest/inserter endpoints. The output chest contained **12 iron plates** from the source chest's 12 raw iron, without hand-filling the machine. Observed the 2x2x2 model, original textures, animated hands, active-state graphics and the Russian controller GUI showing shared 16000 FE, formed status and 32 ticks / 20 FE per working tick. Casing interaction opens the same controller menu.

The item-first wrench handler supports Shift+right-click even with a torch in the offhand; GameTest covers the callback with that occupied offhand. The integrated world is saved using the actual client menu. As in earlier checks, unavailable Mojang authentication in the isolated dev environment does not prevent local play; null OpenAL is used for the smoke run.

## Architectural safeguards and limits

Graph rebuilds are spatial and incremental, and independent valid networks remain online. A removed wire component is quarantined immediately; it resumes after a valid snapshot. Continuous additions do not restart ordinary rebuilds, but coverage changes do. Shared component lists avoid per-generator copies of every consumer. Per-generator demand work remains bounded to 32 candidates.

Casings have no inventory, energy storage or ticker. The controller keeps one canonical transaction. Port access and temporary chunk inactivity are guarded separately from identity/orphan checks, so a loading controller is not mistaken for a destroyed one. All world access is on the server thread; runtime simulation never force-loads chunks.

Lifecycle callbacks, actual in-memory reconstruction mid-production and four-chunk process restarts are tested. Natural player-distance unload and two real connected clients remain unverified. Pattern dimensions are fixed for this first machine; JSON-defined multiblock patterns, all later generators, fluids, research, trains, robots, pollution/combat and space remain planned. The full in-game load harness is the next task in IMPLEMENTATION_PLAN.md.

## Changed areas

New independent power and cuboid-layout models; rewritten server power adapter and configuration/diagnostics; industrial assembly controller composition and casing block entity/capabilities; guarded machine lifecycle and wrench interaction; the 2x recipe work rate and GUI status; two block/item registrations, original resources, recipes and translations; isolated GameTest run configuration; tests and continuation documentation. Existing IDs and inventories are retained.

---

# Historical validation — 0.1.1, 7 November 2026

Continues the existing Git repository and 0.1.0 commit `3f39357`; no new MDK was created. Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 remain fixed. The new transport kernel is independent of Minecraft; the adapter remains entirely server-authoritative.

## Current checks

* `./gradlew build`: successful, 19 JUnit tests with zero failures. Covers exact saturated two-lane rates 15/30/45 items/s over 60 simulated seconds, residual travel with carrier object reuse, cross-block headway, unequal tiers, fair merges without backward movement, splitter fallback, packed rings, scheduling, disconnect/reconnect, legacy overlap reconciliation, activation order and randomized graph topology changes.
* `./gradlew runGameTestServer`: all 17 required tests pass. The previous 14 scenarios (including the automatic iron-to-gear factory) remain; three new scenarios exercise mixed-tier cross-block headway, cached connection invalidation after both source and target rotation, and schema-1 NBT migration including splitter state.
* The repeated server process reports `FACTORYCRAFT_DISK_PERSISTENCE: VERIFIED`, reloading the prior saved fixture with eight carriers and persisted machine/hand state. This also exercises an existing 0.1.0 save.
* `./gradlew runClient -PsmokeWorld=FactoryCraftSmoke`: the actual graphical client loads the old QA world with version 0.1.1 under Xvfb/Mesa. Observed the operating factory, visible belt carriers, animated arms and machine states. The source-only changes to the renderer compile against the exact target API.
* `git diff --check`: clean. Code and documentation are versioned in the supplied repository; CHANGELOG.md records both milestones.

The new 10000-segment kernel test uses **20000 lane nodes**, one carrier per lane, two closed rings, and 100 Minecraft-equivalent ticks (900 microsteps). Representative runs took 1.6–3.2 seconds for the loop depending on concurrent server/client load. These are informational local CPU measurements with no hardware pass threshold. They exclude the Minecraft world, network, 1000 machines and 500 inserters. No full-factory TPS claim is made.

## Problems found and resolved

The new tests exposed an accidentally shared single-lane terminal in the two-lane throughput test; the pure collector now explicitly accepts independent outputs without imposing a fictitious shared lane. Splitters retain their approach reservation but can switch safely to a free branch when blocked. A rotation GameTest caught that `LevelChunk` invokes `onPlace` before updating the existing block entity's facing: invalidation now occurs after `setBlockState`, covering command-driven rotation as well as the wrench.

All outgoing reservations are checked before any carrier changes ownership. All outgoing carriers are then removed before any incoming carrier is inserted, which permits fully occupied cycles and avoids double advancement. The hot transfer path reuses the existing carrier object.

## Migration and remaining limits

Belt schema 2 reads schema 1, preserves old 1/32-grid positions exactly, stores both `Units` and compatible `Position`, and restores old splitter preference for both lanes. Non-grid edited coordinates round by at most 1/192 block. Existing cross-block overlaps are conservatively reconciled on initial simulation without dropping items. Other machine/inserter schemas are unchanged.

Exact transport rates are measured at a continuously supplied 180 Hz kernel input, not asserted for an arbitrary Minecraft machine/inserter line. At a tier boundary the new speed applies on the next microstep. Client extrapolation and curved junction visuals are not an authoritative collision model. Natural-distance chunk unload, real two-client multiplayer, spatially indexed power rebuilds and the full in-game 10000/1000/500 benchmark remain open. Later fluids/research/robots/trains/combat/space systems are not part of this patch.

## Changed areas

`simulation/belts/BeltLane` and new `BeltTransport`; loaded topology/scheduling adapter `BeltSimulation`; `BeltBlockEntity` persistence, inventory and state invalidation; the inserter's position lookup and belt renderer; JUnit and GameTests; version properties, architecture/feature/continuation documents and changelog. The next concrete task is the power graph spatial index with split/merge regression tests.

---

# Historical validation — 0.1.0

## 0.1.0 baseline — scope and environment

Base repository: `LittleEmptyDOLL/FactoryCraft_Industrial_Evolution`, initial commit `7040c68` (unmodified NeoForge MDK). Target Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21. Runtime APIs were checked against the 1.21.1 docs and the resolved NeoForge/Minecraft sources, then compiled and exercised on the server.

The cloud image initially provided a JRE without `javac` / `ct.sym`; NeoForm failed with `release version 21 not supported`. A full Temurin JDK 21.0.13 was downloaded into the task workspace. Proxy and JDK paths are local environment setup, not committed project requirements.

## 0.1.0 automated results

* `./gradlew build`: successful; 8 JUnit tests, 0 failures. Includes energy simulation/capacity/conservation, congestion, exact position restoration, rejection of invalid lane state, new-arrival tick ordering, randomized unique-item conservation, and a 10000-lane kernel exercise.
* `./gradlew runGameTestServer`: all 14 required tests passed. Repeated after the transport scheduler and GUI/recipe changes.
* Separate server invocations emitted `FACTORYCRAFT_DISK_PERSISTENCE: SEEDED` and then `VERIFIED`. The fixture at absolute (80,70,80) is outside test structures and actually reloaded from saved region files in a new JVM. It checks eight carriers/positions, machine input/progress/energy and a diamond in the inserter hand.
* `./gradlew runServer`: dedicated server reached `Done (1.060s)!` on Minecraft 1.21.1 with the mod loaded. It was stopped with SIGTERM after the smoke check; the shutdown hook saved chunks. This intentional termination gives Gradle exit 143, rather than a successful interactive `stop` exit. No client-class loading error occurred.
* `git diff --check`: clean.

Server GameTests:

1. Belts transfer around a corner and preserve item count at a merge.
2. T-splitter distributes eight items 4/4 without duplication.
3. Mechanical inserter transfers eight items between vanilla chests.
4. Furnace remains stopped without energy, then consumes two raw iron and produces two plates.
5. Blocked output consumes neither input nor energy.
6. Pole graph delivers power, separates on removal and rejoins on replacement.
7. Belt/machine/hand NBT round-trip preserves state.
8. Full line mines finite ore, transports it, smelts, assembles and deposits at least three gears in a vanilla chest; no machine input is manually filled.
9. Actual saved region-file state survives a separate server JVM.
10. Sleeping jammed belts wake after downstream extraction.
11. Chunk unload/load callbacks preserve inventories and reactivate scheduling.
12. Breaking a full belt drops every carrier exactly once.
13. Overlapping ingredient predicates cannot spend a slot count twice.
14. Two menus sharing one machine cannot withdraw the same result twice.

The timed kernel exercise is **10000 lanes × 200 ticks**, not the requested full Minecraft workload. It has no hardware-specific timing pass threshold. A representative run took a few hundred milliseconds for the loop; this is not a TPS guarantee or a substitute for 10000 segments + 1000 machines + 500 inserters in game.

## Graphical client

Initial `./gradlew runClient` failed at `glfwInit` because DISPLAY was absent. A local Xvfb display and existing Mesa software OpenGL enabled a real client launch; no headless mock replaced the client. The window loaded the mod resources, entered a copied GameTest save through `-PsmokeWorld=FactoryCraftSmoke`, and ran `/factorycraft demo`.

Observed in the client: original industrial textures, articulated hands, items rendered on belts, automatic iron processing, working machine menu with inventory/progress/energy/recipe preview, and gears in the output chest (`/data get block` confirmed the item ID/count). Used the actual Save and Quit menu, relaunched the client and reloaded the saved factory. English and Russian interfaces were checked. The initial auto-mode preview selected the first recipe regardless of current input; this was fixed by synchronizing the server-selected current recipe index through menu data; the GameTest also checks the preview. Slot borders were made visible after inspection.

The client logs include unavailable Mojang authentication requests in this isolated dev environment; local play continued. A null OpenAL backend was used for the final UI smoke check. Network authentication, sound playback and actual two-client multiplayer are not validated here.

## Coverage limits

* The chunk callback test is not a natural player-distance unload test. Separate-process persistence is real, but does not prove every chunk-border timing combination.
* Shared-menu tests use two mock players on one server thread; no real network clients were connected.
* Worldgen JSON decoded during loading, but ore frequency/distribution was not sampled in an ordinary generated Overworld. Existing chunks are not retro-generated.
* Cross-segment spacing/throughput still use a discrete boundary model. See ARCHITECTURE.md; nominal Factorio rates are not exact measured rates.
* No fluids, research, modules, trains, robots, blueprints, circuits, pollution, combat, vehicles or space implementation is claimed.

## Changed files and continuation

The example mod was replaced by `src/main/java/factorycraft/` (core, registry, blocks/entities, recipes, production, energy, simulation, client and GameTests), `src/test/java/factorycraft/simulation/`, generated assets/datapack resources, server config, build metadata and CI. `tools/generate_resources.py` reproduces the original assets. README, QUICKSTART, ARCHITECTURE, IMPLEMENTATION_PLAN, FEATURE_MATRIX and AGENTS record the working slice and next steps.
