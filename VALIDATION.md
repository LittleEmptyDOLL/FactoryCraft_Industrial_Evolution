# Validation — 7 November 2026

## Scope and environment

Base repository: `LittleEmptyDOLL/FactoryCraft_Industrial_Evolution`, initial commit `7040c68` (unmodified NeoForge MDK). Target Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21. Runtime APIs were checked against the 1.21.1 docs and the resolved NeoForge/Minecraft sources, then compiled and exercised on the server.

The cloud image initially provided a JRE without `javac` / `ct.sym`; NeoForm failed with `release version 21 not supported`. A full Temurin JDK 21.0.13 was downloaded into the task workspace. Proxy and JDK paths are local environment setup, not committed project requirements.

## Automated results

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
