# Development rules

* Target Java 21 / Minecraft 1.21.1 / NeoForge 21.1.252 only. Confirm APIs against this dependency's sources and https://docs.neoforged.net/docs/1.21.1/.
* Inspect existing behavior before edits. Keep a buildable playable slice; no empty registered feature placeholders.
* Server owns inventories, recipes, mining and energy. Client code belongs under `factorycraft.client` and must never load on a dedicated server.
* Preserve item/energy conservation. Transfer simulation must not mutate source state; extracted inserter contents must have a persistent owner. Never force-load neighbor chunks.
* Persist all authoritative changes. Keep schema versions compatible; document migrations before changing them.
* Graph topology caches invalidate on load/unload/place/break only. No global whole-world scan per tick or async access to Level.
* Add meaningful JUnit or GameTests for changes affecting inventories, movement, recipes and persistence. Run `./gradlew build` and integration tests; record exact results and unresolved limits in VALIDATION.md.
* Update FEATURE_MATRIX.md honestly: implemented / partial / planned; registration alone never counts as implemented.
* Original/properly licensed assets only. Keep scripts reproducible. No extracted Factorio assets.
* Keep the next concrete task in IMPLEMENTATION_PLAN.md. Do not push or publish without user authorization.

* Larger machine footprints may use controller/part multiblocks, as requested by the user. The controller alone owns inventory, energy, recipe and progress. Parts store an identity/anchor and expose guarded forwarding capabilities; never serialize copies of the controller inventory in parts.
* Separate temporary chunk inactivity from a destroyed structure. Pause production while any required part is unavailable, keep identity across load ordering, invalidate cached capabilities on unload/break, and repair real orphans without forcing chunks to load.
* Keep GameTest fixtures in `run-gametest/`; do not reuse the ordinary client/server development world for destructive integration fixtures.

* All newly item-placed large machines must form the complete structure from exactly one machine item, per the user's 2026-11-08 instruction. Generated parts are internal, not additional ingredients or independently dropped resources. Validate the complete loaded footprint before any writes; retain normal NeoForge multi-place cancellation/rollback.
* Keep legacy schema-1 manual structures readable. Schema-2 assembly `Automatic` and casing `VirtualPart` distinguish one-item structures; never reinterpret old casings as generated resources. Generated-part destruction removes the whole machine, returns at most one machine item, and drops the sole controller inventory once.
* The user explicitly authorizes publishing tested changes directly to `main` in this session. Preserve remote updates and use a fast-forward with an expected-head lease; never force-push over unrelated work.

* Keep the full diagnostic factory in the marked `run-benchmark/` directory only. Chunk pinning is explicitly confined to this opt-in harness; runtime factory simulation must never force chunks. Never reuse ordinary saves or silently reseed an existing benchmark fixture.
* Preserve neighbor notifications for inventory mutations. Internal position/progress/FE persistence may use `markPersistentState` (with analog-output fallback); do not eliminate dirty marking or conflate BE sync requests with actual packets. Benchmark success means liveness/conservation, not passing a universal hardware timing threshold.
