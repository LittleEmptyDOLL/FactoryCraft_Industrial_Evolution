# Changelog

## 0.2.2 — 2026-11-08

* Added an opt-in dedicated-server load harness with real 10000 belts, 1000 producers, 500 inserters, 250 generators and a shared power grid. The marked `run-benchmark` world stays separate from normal development/GameTest saves.
* Added live equipment counters, item/FE conservation checks, graph split/rejoin, JSON tick percentiles and JFR CPU/allocation/vanilla-packet capture. The separate verify mode checks saved inventories, carriers and multiblock identities without reseeding. CI runs both full measurement and restart verification.
* Replaced profiled per-node iterator allocation in belt stepping with indexed access. Internal FE/progress/position persistence no longer sends unnecessary comparator-neighbor notifications; inventory mutations retain them, and dirty data/packet cadence remain intact.
* Added independent layout, real shared-grid connectivity and timing-summary tests; benchmark results and remaining multiplayer limits are documented.

## 0.2.1 — 2026-11-08

* Preserved the user's main-branch update to NeoForge 21.1.252 and validated the final release against that dependency.
* One industrial furnace item now automatically places/forms the entire 2x2x2 machine; no separate casing blocks are required. The crafting recipe uses ordinary stone bricks instead of casings.
* Validate every cell for loaded chunks, height/border, replaceability, existing block entities, collision and player permissions before placement. Normal NeoForge multi-place protection can cancel and restore the entire placement/item.
* Breaking any generated part removes the whole structure, returns at most one machine item, and drops controller inventory once. Added creative break/pick-block behavior, explosion conservation and late generated-orphan cleanup. Pistons cannot move the controller or parts.
* Assembly/casing metadata schema 2 reads legacy schema 1; old manual structures retain their original dismantling behavior and inventory. Generated parts remain internal and are not in the creative tab or crafting recipes.
* Added 16 GameTest scenarios for actual item placement, rejection/rollback, all cell breaks, production, persistence and resource conservation.

## 0.2.0 — 2026-11-08

* Added an operational 2x2x2 industrial furnace: one controller, seven casings, twice the processing speed with unchanged FE per result.
* All casing ports share the controller inventory/energy; no part owns duplicated resources. Formation checks the whole footprint, rejects overlapping owners, and persists a UUID and orientation.
* Added safe dismantling, cached-capability guards, pause/resume on partial chunk unload, mid-production restoration and deferred orphan repair. Sneak-wrench also works with an occupied offhand.
* Replaced quadratic all-pairs power rebuilding with spatial buckets, bounded incremental snapshots and shared component consumer lists. Independent networks remain online; removed wire components are quarantined immediately. Continuous additions do not starve snapshot publication.
* Added `powerGraphWorkPerTick`, `/factorycraft power`, and `/factorycraft smelter`, plus original casing/controller textures, direction arrow, EN/RU status text, crafting recipes and book unlocks.
* Added independent power/layout tests and 13 multiblock integration scenarios. Persistent GameTest worlds now use `run-gametest/`, separately from normal development worlds.

## 0.1.1 — 2026-11-07

* Replaced per-block, zero-residual belt movement with an independent phased transport kernel.
* Enforced 0.25-block spacing across connected segments, preserved boundary overshoot and prevented a second move of a newly transferred carrier in the same phase.
* Added fair merge reservations before junctions and simultaneous transfers for fully occupied rings.
* Retained all three speeds; saturated two-lane kernel tests measure exactly 15/30/45 items per second over a 60-second interval.
* Cached loaded lane links and corrected invalidation after programmatic/wrench block rotation.
* Retained sleeping/wakeup behavior and reused carrier objects across segments. Batched block changes once per game tick.
* Added belt NBT schema 2 with legacy schema-1 reading and per-lane splitter preferences; other inventories and schemas are unchanged.
* Added randomized conservation/topology tests, mixed-tier/rotation/migration GameTests, and a 10000 two-lane segment kernel benchmark.
* Client rendering uses the full block path length. Curved rendering and full world-scale profiling remain planned.

## 0.1.0 — 2026-11-07

* First playable iron/copper mining, belt/inserter, electric smelting and assembly factory.
* Coal generator, cached pole networks, item/energy capabilities, machine menus, counted datapack recipes and original EN/RU assets.
* Persistent inventories, belt positions, production state and inserter hand.
* First end-to-end factory and separate-process persistence checks.
