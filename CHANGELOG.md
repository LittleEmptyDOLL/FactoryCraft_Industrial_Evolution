# Changelog

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
