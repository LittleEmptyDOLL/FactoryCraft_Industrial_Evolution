# FactoryCraft: Industrial Evolution — implementation plan

Target: Minecraft **1.21.1**, NeoForge **21.1.252**, Java **21**, ModDevGradle **2.0.148**.

## Architecture and invariants

* `factorycraft.registry`: deferred blocks/items/block entities/menus/recipes; client registration is isolated.
* `simulation.belts`: independent two-lane kinematics; no item entities. One item per carrier, 0.25 block spacing within and across segments, backpressure and phased transfers preventing double advancement.
* `blockentity`, `production`, `recipes`: persistent item handlers, server-authoritative production, data-driven counted ingredients. Outputs are simulated before inputs are committed. Inputs are revalidated at completion.
* `energy` / `simulation.power`: spatially indexed, work-budgeted snapshots rebuilt only on topology changes. Removed components are quarantined immediately; independent components keep working. No forced chunk loads.
* `multiblock`: controller-owned resources and progress; casing parts have only an identity and stable guarded forwarding ports. Larger machine footprints may use this design, per the user’s instruction.
* `inserters`: a persistent held stack owns extracted items until the destination accepts them; breaking drops held items.
* `client`: renderers interpolate server snapshots; menus send only vanilla menu button requests, validated server-side.
* Belt NBT schema 2 reads schema 1; machine inventory stays schema 1; assembly/casing metadata schema 2 reads schema 1. Unloading pauses simulation; it never converts inventory to transient entities. Breaking blocks drops their contents once.
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

## Delivered in 0.2.0

Completed the power rebuild task: independent spatial index and incremental snapshot builder, bounded empty-bucket/candidate work, immediate removed-component quarantine, unchanged-network availability, shared consumer lists rather than generator-by-consumer route copies, and progress during continuous topology additions. Added a work-budget configuration and power diagnostics.

Added the first real multiblock as allowed by the user: industrial furnace controller + seven casings (2x2x2), shared item/FE ports and GUI, 2x throughput at the same FE per result, atomic formation and overlap checks, persistent identity/orientation, inventory-preserving dismantling, unload guards, orphan recovery and cross-chunk restart tests. Casings have no ticker or resource storage. Added recipes, original textures with a direction arrow, EN/RU status and `/factorycraft smelter`.

See VALIDATION.md for checks and limits; no full Factorio/Space Age completion is implied.

## Delivered in 0.2.1

Followed the user's one-item/one-structure requirement: item placement validates the complete rotated footprint (loaded chunks, height, border, replaceability, entities, permissions), then creates/binds all seven internal parts inside the ordinary NeoForge multi-place snapshot transaction. No separate casing ingredient or placement is required. Protection cancellation restores the original cells and item. Destruction or sneak-wrench of any generated part removes the whole machine, returns one machine item, and drops controller contents once. Creative break and pick-block use whole-machine semantics. Inactive chunks still pause; late generated orphans self-remove. Old manual assemblies retain their original behavior using backward-readable schema-2 flags. Removed the casing crafting recipe/creative entry and updated EN/RU instructions, recipe and example command.

Tests include actual ItemStack placement, protected cancellation, all break positions, creative placement, collision/height/unloaded rejection, cached ports, late orphans, explosion callbacks, partial-progress reconstruction, a separate-process four-chunk fixture and the complete automatic line. See VALIDATION.md for exact results.

## Next concrete task (priority order)

1. Implement an explicit, isolated in-game benchmark harness for 10000 moving belt segments / 1000 working machines / 500 inserters. Measure server tick time, graph rebuilds, packets and conservation; do not substitute kernel timings for TPS. Include industrial furnaces as controllers with non-ticking casing parts.
2. Begin stage 3 fluids: independent storage/transfer model and NeoForge fluid capability adapters, pipes/tanks/pumps, then data-driven oil/chemistry recipes. Document the intended Factorio 2.0 throughput model before claiming parity.
3. Exercise natural player-distance chunk unload and two real clients. The existing lifecycle and multi-process tests do not cover a live network session or every chunk-order combination.
4. Continue underground belts, two-block splitters and inserter variants. Keep future space mechanics independent from basic production. Extend larger machines through controller/part layouts rather than duplicating inventories.
