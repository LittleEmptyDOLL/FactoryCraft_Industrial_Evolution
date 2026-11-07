# FactoryCraft: Industrial Evolution — implementation plan

Target: Minecraft **1.21.1**, NeoForge **21.1.265**, Java **21**, ModDevGradle **2.0.148**.

## Architecture and invariants

* `factorycraft.registry`: deferred blocks/items/block entities/menus/recipes; client registration is isolated.
* `simulation.belts`: independent two-lane kinematics; no item entities. One item per carrier, 0.25 block spacing within and across segments, backpressure and phased transfers preventing double advancement.
* `blockentity`, `production`, `recipes`: persistent item handlers, server-authoritative production, data-driven counted ingredients. Outputs are simulated before inputs are committed. Inputs are revalidated at completion.
* `energy`: loaded-node pole graph rebuilt only on topology changes; no forced chunk loads. Machine buffers expose NeoForge FE.
* `inserters`: a persistent held stack owns extracted items until the destination accepts them; breaking drops held items.
* `client`: renderers interpolate server snapshots; menus send only vanilla menu button requests, validated server-side.
* Belt NBT schema 2 reads schema 1; other block entities keep schema 1. Unloading pauses simulation; it never converts inventory to transient entities. Breaking blocks drops their contents once.
* No off-thread world access. 20 Minecraft ticks = one simulation second. Belt basic/fast/express rates target 15/30/45 individual items per second across two lanes.

## Stages

1. Working core: rename MDK, registries, resources, configuration, inventory persistence, capabilities, client/server startup checks.
2. First factory: ores/world generation, coal generator + poles, electric drill, three belt speeds, turns, balancing splitter, inserter, furnace, assembler, recipes and machine GUI. Verify build, JUnit and integration GameTests.
3. Expand power/fluid systems: production statistics, realistic generators, accumulators, fluid graphs, pumps, chemistry. Document fluid model against Factorio 2.0.
4. Research/progression: teams, technology definitions and GUI, lab, modules, beacons.
5. Large logistics: underground/filter/priority belts, inserter variants, circuits, robots, blueprints, railway graph/signals/trains.
6. Pollution, enemies, combat and vehicles.
7. Independent space module: rockets, platforms, quality and four planetary dimensions.
8. Visual polish, compatibility, multiplayer, profiling and 10k/1k/500 benchmark.

## Current work

Develop stages 1–2 as a working vertical slice. Feature completion requires evidence; see FEATURE_MATRIX.md and VALIDATION.md. Later stages are not implied by block registration.

## Next session

Read VALIDATION.md and FEATURE_MATRIX.md first, reproduce `./gradlew build` and `./gradlew runGameTestServer`, then close the remaining first-factory integration/manual checks before adding stage 3. Add underground transport and inserter variants only after conservation and unload tests pass. Do not claim the full Factorio/Space Age implementation is complete.

## Delivered in 0.1.0

Stages 1 and a playable stage-2 slice are implemented: 10 factory block variants, 2 generated ores, 6 items, 5 processing recipes, survival crafting, original textures, EN/RU GUI, server configuration, active/sleeping two-lane transport, T-splitter, mechanical inserter, electric drill/furnace/assembler, coal generator and cached pole networks. Independent energy/belt kernels have JUnit coverage. Server GameTests include the complete automatic iron-to-gear chain, menu conservation and actual disk persistence between JVM runs. Client launch, integrated world, machine GUI and save/reload were exercised with software OpenGL. See VALIDATION.md for exact limitations.

## Delivered in 0.1.1

Implemented the first next-session task: independent phased transport kernel, nine exact microsteps per game tick, cross-block spacing, residual travel, fair merge reservations, fully packed cycles, blocked-splitter fallback and cached lane links. Empty and jammed lanes still sleep. Carrier objects are reused across segments. Added property tests for randomized graph changes and exact 15/30/45 item/s saturated two-lane throughput, plus GameTests for mixed-tier headway, state rotation and schema-1 migration. The original end-to-end factory remains covered. See VALIDATION.md and CHANGELOG.md.

## Next concrete task (priority order)

1. Replace the quadratic power topology rebuild with spatial buckets and a bounded dirty-graph rebuild; preserve split/merge correctness and loaded-only operation. Add independent power graph tests before connecting new generators.
2. Build the actual in-game 10000 segments / 1000 working machines / 500 inserters benchmark. The 10000-segment kernel test excludes world/network costs and is not a TPS guarantee.
3. Exercise natural chunk-distance unload/reload and two connected clients. Profile full per-block snapshots and add deltas only if justified. Refine client rendering of curved junctions independently of server authority.
4. Complete stage 2 with underground belts, a two-block splitter and inserter variants, then begin stage 3 fluids. Keep future space mechanics independent from basic production.

The UI, graph and timing boundaries in ARCHITECTURE.md are limits of this slice, not completed Factorio parity.
