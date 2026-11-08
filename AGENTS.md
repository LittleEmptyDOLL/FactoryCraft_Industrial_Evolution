# Development rules

* Target Java 21 / Minecraft 1.21.1 / NeoForge 21.1.252 only. Confirm APIs against this dependency's sources and https://docs.neoforged.net/docs/1.21.1/.
* Inspect existing behavior before edits. Keep a buildable playable slice; no empty registered feature placeholders.
* Server owns inventories, recipes, mining and energy. Client code belongs under `factorycraft.client` and must never load on a dedicated server.
* Preserve item/energy conservation. Transfer simulation must not mutate source state; extracted inserter contents must have a persistent owner. Never force-load neighbor chunks.
* Persist all authoritative changes. 0.6.0 intentionally requires new worlds under the user override below. Preserve 0.6.0 data in future changes unless explicitly authorized otherwise.
* Graph topology caches invalidate on load/unload/place/break only. No global whole-world scan per tick or async access to Level.
* Add meaningful JUnit or GameTests for changes affecting inventories, movement, recipes and persistence. Run `./gradlew build` and integration tests; record exact results and unresolved limits in VALIDATION.md.
* Update FEATURE_MATRIX.md honestly: implemented / partial / planned; registration alone never counts as implemented.
* Original/properly licensed assets only. Keep scripts reproducible. No extracted Factorio assets.
* Keep the next concrete task in IMPLEMENTATION_PLAN.md. Do not push or publish without user authorization.

* Larger machine footprints may use controller/part multiblocks, as requested by the user. The controller alone owns inventory, energy, recipe and progress. Parts store an identity/anchor and expose guarded forwarding capabilities; never serialize copies of the controller inventory in parts.
* Separate temporary chunk inactivity from a destroyed structure. Pause production while any required part is unavailable, keep identity across load ordering, invalidate cached capabilities on unload/break, and repair real orphans without forcing chunks to load.
* Keep GameTest fixtures in `run-gametest/`; do not reuse the ordinary client/server development world for destructive integration fixtures.

* All newly item-placed large machines must form the complete structure from exactly one machine item, per the user's 2026-11-08 instruction. Generated parts are internal, not additional ingredients or independently dropped resources. Validate the complete loaded footprint before any writes; retain normal NeoForge multi-place cancellation/rollback.
* Assembly/part schema 3 identifies current profiles. Any generated-part destruction removes the whole machine and returns at most one portable controller item holding the sole inventory/fluids/FE/progress; never also drop loose copies.
* The user explicitly authorizes publishing tested changes directly to `main` in this session. Preserve remote updates and use a fast-forward with an expected-head lease; never force-push over unrelated work.

* Keep the full diagnostic factory in the marked `run-benchmark/` directory only. Chunk pinning is explicitly confined to this opt-in harness; runtime factory simulation must never force chunks. Never reuse ordinary saves or silently reseed an existing benchmark fixture.
* Preserve neighbor notifications for inventory mutations. Internal position/progress/FE persistence may use `markPersistentState` (with analog-output fallback); do not eliminate dirty marking or conflate BE sync requests with actual packets. Benchmark success means liveness/conservation, not passing a universal hardware timing threshold.

* Fluid storage has one canonical component-sensitive cell. Simulate without mutation; reserve/drain/fill all endpoints before notifying observers. Never mix FluidStacks with different components or truncate restored volume to a smaller capacity.
* Fluid availability must check canonical loaded chunk ownership without loading it. Freeze/detach at ChunkEvent.Unload before serialization (BE unload is later); mark the owned chunk unsaved immediately. Preserve the separate-process four-chunk finite-volume fixture without reseeding failures.
* Keep FLUIDS.md synchronized with throughput/energy/capacity changes. The current finite local-pressure solver is an explicit approximation, not exact Factorio 2.0 segment behavior. New large fluid machines must follow the existing one-item multiblock rule.

* Thermal recipes use their own boiling type; do not break legacy processing JSON/stream format. A canonical serialized definition is the persisted recipe identity. Update STEAM_POWER.md before changing fuel/fluid/FE ratios.
* Boiler conversion must commit both fluid cells and fuel/progress before observers; refueling defers inventory notifications until burn ticks and container remainder are stored. Engine FE is owned before steam observers run; never consume steam for less than a complete 12-FE quantum or accept FE into a generator.
* Preserve thermal schema-1 native FluidStacks, fuel inventory/remainders, progress, unspent burn ticks and generated FE, including portable block state. Thermal fixtures are isolated and intentionally detached to test exact disk values; keep live reconstruction and powered-line tests alongside them.

* New mixed chemistry must reserve all item/fluid outputs and counted inputs before work, then commit with deferred inventory callbacks. Keep old processing/boiling codecs readable and selected recipes keyed by ID; changing a definition must not reuse another definition's partial progress.
* Refinery parts contain no resource copies. Portable controller NBT must omit Assembly before one-item placement; importing an old UUID after new parts bind creates orphans. Preserve cancellation, current one-item portable semantics and cached-port guards.
* BE-bearing deposits must use ResourceVeinFeature (solid surface fields) or OilDepositFeature (oil)/worldgen-region writes, not vanilla OreFeature direct section writes. Persist Initialized separately from Remaining; initialized zero is exhausted. No deposit block item/craft may regenerate oil. Update PETROLEUM.md for extraction, recipe/port or generation changes.
* Only facing changes invalidate petroleum nozzle/target caches. Lit/active-state updates must not reset mining targets or throttle extraction. Canonical unload guards and immediate owning-chunk dirty marks are mandatory for all new fluid owners.

* User scope override for 0.6.0: old-save compatibility is explicitly not required. Replace compact machine footprints and one-block/one-ore deposits. All current machines must use the Factorio footprint table; keep one-item automatic formation and server-authoritative preview validation. Do not retain compact test fixtures or a compact benchmark to bypass real dimensions. Preserve old worlds only as diagnostic files, never silently migrate/reseed them.

* MachineProfile is the single source for dimensions; PlacementPlan is shared by client preview and server item validation. New physical fixtures must use complete footprints. Resource tiles own finite initialized stock, have no regenerating block item and use BE-aware worldgen; never silently refill zero reserves.

* Item placement targets the horizontal base center; internal schema-3 controller/parts stay corner-anchored for 0.6.0 compatibility. Use MachineProfile.anchorAtCenter and CenteredPlacementContext once; never let a blocked corner silently shift the structure up. Preview must share the same conversion. Administrative fixtures still name internal controllers.
* Large veins sample deterministic world coordinates but write only the decorating chunk through WorldGenLevel. Generated BE reserve initialization must not call ServerLevel/neighbor callbacks on a worldgen worker. Keep radius/stock/spacing bounded, validate JSON ranges, preserve initialized stock and test seams plus process reload. Worldgen settings are COMMON startup configuration; existing worlds/chunks are never retro-generated.

* Static machine geometry is native chunk-baked per-cell surface data; keep parts independently renderable without controller-chunk reads. Keep moving-component renderers client-only and activity-driven. Use MachineProfile dimensions and regenerate original assets with tools/generate_resources.py; do not add tiled full-body copies or hidden simulation to renderers.
* Splitters own four lanes in schema 3; ordinary belts keep schema 2. Preserve all positions, preferences and track-local capabilities when portable/unloaded. Inserter sampling must resolve the physical source track, including parts. Route changes must pass shifted saturated four-lane throughput plus randomized/headway tests and the saved full-factory hash check.

* Refinery Petroleum schema 2 adds Fluid4 as front-left output 3. Never renumber old Fluid0..3 or infer oil type while migrating. Chemical plants/pumpjacks retain four cells/schema 1. Bounded chemistry recipes allow three refinery outputs but only two plant outputs; reserve all products before FE. Keep native codec field order and canonical partial-work identity backward readable.
* Keep the five-cell components/overflow/progress disk fixture intact and verify every observer sees all inputs/results committed. The petrochemistry example/test starts from finite oil, native source water and raw plates/coal; preserve the full crude/gas/sulfur/acid/battery/byproduct/plate ledger rather than seeding intermediate chemicals to bypass a broken line.

* In example/fixture pipe routing, both path overlap and face adjacency connect fluids. Preserve the offset heavy-oil riser beside the water bus; verify correct products actually reach separate tanks, not just a conserved global ledger. Allow the documented finite-pressure initial fill rather than injecting chemicals to conceal broken routing.

* ResearchData is overworld server SavedData. Provider-prefixed team keys are fixed lab bindings; never copy/merge progress on party/team/provider changes. Installed FTB with unavailable API pauses research instead of falling through to another owner. Vanilla team names are their persistent identity; deleting/reusing a name reuses that scope.
* Science packs remain in the sole lab inventory until one shared unit commits. Validate selection/prerequisites/final unit before consuming, defer inventory observers, preserve canonical work through portable/chunk state, and validate membership/distance/menu for packets AND ordinary slot clicks. Legacy machines omit ResearchScope; do not write an empty field or break the exact benchmark hash. New player machines bind only after native portable NBT import.
* EMI/JEI classes are optional client-only entrypoints; common simulation/registration must not link to their classes. Build profiles recipeViewer=jei/emi/both and ftbModsDir exercise real dependencies without bundling jars. Keep exact dependency versions/hash-checked download URLs in the optional-mod lock file and preserve viewer-free dedicated startup.

* Preserve curated upstream item artwork listed in tools/item_artwork.json. Resource generation must not overwrite those PNGs or silently replace missing curated art with a procedural fallback. Unregistered future-item textures do not count as implemented gameplay.

* Second assembler inherits dry recipes under original IDs/research gates; do not generate duplicate viewer recipe IDs or alter old assembler NBT. Preserve old chemistry JSON/wire field order and canonical signatures. Numeric profile IDs must have bundled geometry.
* Canister recovery commits a private item copy and exact input remainder before observers. Keep native SimpleFluidContent/components, capacity remainder/overflow, current-menu/range/spectator/assembly guards, and ordinary stale slot protection. Never replace sub-bucket fluid with a lossy bucket or discard it when changing recipes. Preserve the four-chunk chemical-science/canister checkpoint without reseeding.

* Research search owns non-Escape keyboard input while focused; do not let container or viewer shortcuts close the menu when typing technology names. Keep a manual typed-query/pan check when changing this screen.
