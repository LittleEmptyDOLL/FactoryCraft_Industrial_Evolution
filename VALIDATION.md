# Validation — 0.1.1, 7 November 2026

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
