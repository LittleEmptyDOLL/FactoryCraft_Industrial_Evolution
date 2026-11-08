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

Maintain the delivered stages 1–2 vertical slice while expanding stage 3 with usable, tested fluid logistics. Feature completion requires evidence; see FEATURE_MATRIX.md and VALIDATION.md. Later stages are not implied by block registration.

## Next session

Read VALIDATION.md, FEATURE_MATRIX.md, FLUIDS.md and STEAM_POWER.md first. Reproduce `./gradlew build` and two separate `./gradlew runGameTestServer` processes before extending stage 3. Preserve existing factory, fluid and multiblock fixtures. Do not claim the full Factorio/Space Age implementation is complete.

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

## Delivered in 0.2.2

Executed the requested real-world 10000/1000/500 workload on an isolated dedicated server, with a shared 1749-node electrical graph, 250 actual industrial multiblocks, finite stocks and normal 20 Hz scheduling. The harness builds incrementally, pins only its own test chunks, warms up, samples full server tick work, confirms every segment/producer/inserter actually works, perturbs/restores a pole, and checks iron-equivalent material plus FE conservation. Separate processes verify 20000 belt carriers, all inventories and 250 controller identities/seven bound parts per controller. JSON and JFR CPU/allocation/vanilla-packet results are retained; CI runs the full scenario and restart check. No fixed hardware timing threshold masks correctness failures.

JFR located nested ArrayList iterator allocation in belt stepping and unnecessary comparator-neighbor notifications during internal persistence. Indexed incoming-lane loops and separate state/packet/inventory paths reduce those costs while keeping normal inventory notifications, dirty chunks, full belt behavior and existing NBT formats. See BENCHMARKS.md and VALIDATION.md for measured results and limits; zero connected clients is not network-throughput validation.

## Delivered in 0.3.0

Usable six-way pipes, tanks and directional electric pumps; finite component-sensitive fluid storage, budgeted cached active-edge transport, native source-water intake, existing pole-grid power and standard NeoForge sided fluid/FE ports. Added buckets, portable filled-block contents, readonly volume/energy GUI, original textures, EN/RU, survival recipes and waterworks/diagnostic commands. Separate-process four-chunk finite-volume tests exposed and fixed pre-serialization chunk-unload timing; canonical chunk guards and early detach preserve contents. The documented local-pressure model uses degree damping at junctions. See FLUIDS.md and VALIDATION.md; oil, steam power and chemistry are not yet implemented.

## Next concrete task (priority order)

1. Continue stage 3 with mixed item/fluid processing transactions, oil deposits/pumpjack and refinery/chemistry. Keep old item-processing and boiling JSON readable. Define counted inputs/outputs, multiple fluid ports and reserved output space before adding machines; test blocked outputs, power loss, fluid components and restart. Large refineries use the existing one-item automatic multiblock layout. Oil must have a real finite resource source, not a free tank-fill tool.
2. Exercise two real connected clients and natural-distance chunk unload. Use the new JFR packet capture to measure tracking/synchronization, then implement batching/deltas from measured evidence. BE sync request counts do not measure wire traffic. The dedicated no-client workload does not establish client FPS or multiplayer bandwidth.
3. Continue underground belts, two-block splitters and inserter variants. Keep future space mechanics independent from basic production. Extend larger machines through one-item controller/part layouts rather than duplicating inventories.

## Delivered in 0.4.0 — steam power

Implemented compact single-block boiler and steam engine, container-only steam/bucket, standard guarded fluid/item/FE capabilities, original animated assets, EN/RU and working menus. The fueled boiler uses an independent sized/tagged fluid recipe type with atomic conversion, bounded codecs and canonical persisted recipe identity. Engine generation supplies the existing pole graph and preserves integer FE/steam balance. Fuel remainders, unspent burn ticks, progress, fluids/components and generated FE survive portable placement and separate server processes. Native buckets refuse lossy component transfer; filled tanks remain the portable option. See STEAM_POWER.md and VALIDATION.md for exact ratios, tests and limits. Next work remains mixed item/fluid transactions and finite oil/refining, as prioritized above.
