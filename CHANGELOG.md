# 0.16.0 — bounded circuit networks (9 November 2026)

* Added red/green reusable wiring tools, cached bounded components, signed int32 scalar logic, constant/arithmetic/decider programs, inventory readers and adjacent inserter/pump controllers. Combinators have separate physical ports and one-item centered 1×2 placement; smaller nodes occupy 1×1.
* Added guarded native program menus/payloads, visible original wire geometry, research/manufacturing recipes and EN/RU UI. UUID-bound reciprocal links/programs and explicit device bindings survive restart; portable relocation clears old coordinates while retaining programs and resources. Closed gates preserve paid hands/FE and finite fluids.
* Added `/factorycraft circuitworks`, a real coal/pole-powered finite eight-item feedback example and staged two-color logic, plus `/factorycraft circuits` statistics. Independent graph/math and native lifecycle/menu/placement/actuator/persistence tests cover the slice.
* Logic itself currently needs no FE. CIRCUITS.md documents bounded polling latency and downstream output sample-hold. Fluid signals, wildcards, multiple rules and other device conditions remain planned.

# 0.11.0 — productive modules and powered beacons (8 November 2026)

* Added tier-1 speed/efficiency/productivity items, separate guarded production-machine slots/menu, bounded fixed-point speed/energy and recipe-specific actual-work productivity credit. Both item and mixed fluid production reserve the complete base/bonus output before energy and commit all owners before observers. Empty/default module state preserves legacy NBT/work.
* Added a centered one-item 3×3×3 beacon, sole module/FE owner, standard ports, original animated antenna, normal-quality diminishing transmission and local spatial recipient caching. Only speed/efficiency transmit; powered block-ticking complete beacons pay 40 FE/t before work. Inactive/frozen/unloaded equipment cannot provide free effects or consume standby energy.
* Added three technologies/recipes, productive-output datapack tag and `/factorycraft moduleworks`, a finite chest/belt/inserter/coal-powered comparison factory. Native and independent tests cover arithmetic, gates/output backpressure, mixed FE/fluid ledgers, late insertion, portable/live state, range/power/unload and disk persistence. An unreleased v1 probe's premature debit is retained as evidence; the corrected v2 checkpoint is separate and strict.
* Preserved main's JEI 19.21.1.248 choice and corrected its API-only runtime dependency to the actual viewer. Higher module tiers/quality, drill/lab/thermal effects and coverage overlays remain future work.

# 0.10.0 — chemical science and exact fluid recovery (8 November 2026)

* Added steel/engines, electric engines, processors and chemical science with real production recipes and six prerequisite technologies. Original six technologies/partial work stay intact.
* Added a one-item centered 3×3×3 assembling machine 2 with original marked geometry/moving press, item/FE/fluid ports, mixed recipe transactions and inherited dry assembler recipes. The old assembler's simulation/NBT remain unchanged. EMI/JEI expose the new mixed category and the second machine as a catalyst for original dry recipes.
* Added a native component-preserving 4000-mB canister and server-validated input-recovery buttons. Sub-bucket volumes, components, full-container remainders and restored overflow are conserved; stale/distant/spectator/unloaded menus cannot drain fluid or ordinary item slots.
* Added `/factorycraft chemicalworks`, a finite powered component→engine→blue-science line, plus production/gating/energy/pipe/three-pack research/portable/live/disk/capability/network/asset tests. JSON validation now reports malformed definitions as codec errors; numeric tier profile names are covered by bundled geometry validation.
* Fixed research text entry consuming the inventory shortcut and wrapped long JEI/EMI requirements inside recipe cards with full hover help.
* Later modules/beacons, further science and advanced component consumers remain planned. See PROGRESSION.md and VALIDATION.md for balance, evidence and limits.

# 0.9.0 — shared research and recipe viewers (8 November 2026)

* Added a centered one-item 3×3×3 laboratory with original geometry, animated vessels, FE/science inventories and a searchable dependency graph. Six datapack technologies, red/green science production and repeatable lab efficiency form the first research progression.
* Research is shared through optional FTB Teams; without it vanilla scoreboard teams or individual players own progress. Fixed provider-prefixed bindings persist in overworld SavedData and portable labs; party changes never copy progress. Server checks protect packets and ordinary inventory clicks. Concurrent labs commit each shared unit before spending its packs.
* New player-placed machines respect technology unlocks for green science, advanced circuits, advanced oil/cracking/lubricant and acid/batteries. Existing/admin machines retain their legacy operation and exact saved benchmark hash. `/factorycraft researchworks` builds actual automatic red-science production from finite gears/plates.
* Added optional isolated EMI/JEI plugins for native processing, chemistry, boiling and research recipes, with alternatives, counts, component-sensitive fluids, catalysts, energy/time and research requirements. Added independent runtime profiles and hash-checked optional FTB test dependencies; jars are not bundled.
* Validation: 101 JUnit / 147 required GameTests, separate-process research persistence, actual FTB/viewer dedicated profile, preserved full-factory checkpoint and real graphical clients. See VALIDATION.md for evidence and limits; full research/modules/beacons and automatic viewer recipe transfer remain future work.

# 0.8.0 — advanced refining and usable chemistry (8 November 2026)

* Added heavy/light oil, lubricant and sulfuric acid with native buckets/original assets, plus batteries. Six new JSON recipes extend basic oil/plastic/sulfur: advanced refining, both cracking steps, lubricant, acid and batteries.
* The existing 5×5 refinery now has two inputs/three outputs and a real front-left third nozzle marked 3. All outputs reserve before FE/work; blocked or component-mismatched products pause without consuming inputs. Old port order/Fluid0..3 remain; missing Fluid4 loads empty, portable state keeps all five cells/components/overflow.
* Added five-row menus, localized recipe names with datapack fallback, physical-port hover help and third-nozzle geometry. Centered one-item placement, footprints and sole controller ownership remain.
* `/factorycraft petrochemistry` builds a powered finite oil → sulfur → acid → battery line with actual source water, raw plates, inserters and separate byproduct tanks. Added complete material ledgers, five-cell disk/portable/live reconstruction, blocked/underpowered/nozzle/codec/bucket regressions and independent five-owner atomicity tests.
* Basic oil, previous factories/saves and full benchmark checkpoints remain supported. Team research/labs/science are next; heat, later lubricant/battery consumers and large petrochemical TPS claims remain incomplete.

# 0.7.0 — unified machines and four-lane splitters (8 November 2026)

* Added original unified geometry and normalized 3D item models for all 13 large machine types. Static surfaces are split into native chunk meshes, preserving silhouettes/UVs across parts and chunk borders. Existing footprints, complete collision volumes, centered previews and single-controller ownership remain.
* Added activity-driven drill/press/fan/rocker/agitator/flywheel details and furnace/boiler heat, colored arrow-marked physical fluid nozzles and bilingual tooltips. `/factorycraft showroom` builds all types with finite supplies and actual recipes.
* Completed both physical splitter tracks/four lanes, two incoming belts/two forward outputs, local capabilities, 16 persistent carriers and schema-1/2 migration. Approach reservations now sustain 30 item/s basic aggregate independent-kernel throughput across 24 shifted input phases while conserving every carrier. Blocked exits retain contents and resume.
* Fixed cached extraction after ordinary-belt unload and inserter pickup-position checks at a splitter part. Added geometry, four-lane flow, blocked/unloaded, portable, native part metadata and actual separate-process persistence regressions. Existing factories and 0.6.0+ data remain supported.
* Priorities, filters, underground belts, final art, advanced refining/research and later systems remain planned. See VALIDATION.md for exact checks and limits.

# 0.6.1 — large resource veins and centered placement (8 November 2026)

* Replaced tiny underground solid-resource blobs with large organic surface fields spanning chunks. Configurable nominal radius 8–20, tile stock 500–5000 and spacing six chunks by default; centers are richer than edges. Added COMMON `factorycraft-worldgen.toml`, per-resource datapack overrides and strict codec bounds.
* Each generation call writes only its own chunk using the supplied WorldGenLevel. Pure deterministic sampling removes chunk-order seams; generated stock initializes without unsafe server callbacks, persists and never refills on decoration/reload.
* Large items now target the horizontal base center, with the blue preview cell marking it. Even dimensions use the right/back middle cell. Full collision/protection/rollback and portable native NBT remain; obstructed controller corners cannot move placement upward.
* Kept internal controllers, existing 0.6.0 worlds, reserves and schema-3 part identities unchanged. Natural fields appear in new chunks; no retrogen or compact-world migration. Updated fixtures, instructions and original-client screenshots; 87 JUnit / 115 GameTests pass.

# 0.6.0 — finite resource tiles and full machine footprints (8 November 2026)

* Iron/copper/coal/stone tiles hold finite initialized quantities (default 1000). Manual mining and powered drills extract one unit at a time; exhausted tiles disappear. Full destinations and unavailable chunks never consume stock. Added BE-aware datapack generation, stock interaction and richness configuration.
* Every existing large machine now occupies its documented Factorio horizontal footprint: drill/furnace/assembler 3×3, refinery 5×5, boiler 3×2, engine 3×5, tank 3×3, pump 1×2, splitter 2×1. Heights are original Minecraft choices; custom generator/industrial furnace are explicitly named adaptations.
* One-item automatic formation now shares validation, ownership, physical fluid ports and chunk lifecycle across production, thermal, fluid and splitter controllers. Green/red placement preview displays the occupied volume, individual obstructions, dimensions and controller anchor. Dismantling keeps all resources in one portable machine item.
* Rebuilt all example lines, physical integration fixtures and the full 10000/1000/500 benchmark for actual geometry. Removed obsolete industrial-only placement/fluid wrappers. Added finite-stock and all-profile rotation/preview tests; retained existing recipe, transport and chemistry coverage.
* **New world required.** Old compact saves are intentionally unsupported under the user's explicit request. Visual shells remain temporary original tiled models; full four-lane splitter throughput, priorities/filters and later progression remain unfinished.

# 0.5.0 — finite petroleum and mixed chemistry (8 November 2026)

* Added persistent finite oil deposits, bounded BE-aware Overworld generation, configurable initial richness and a 10 mB/t / 15 FE/t electric pumpjack. Exhausted reserves do not refill.
* Added a one-item automatic 2×2×2 oil refinery using the existing protected placement/identity system, guarded nozzle ports and dedicated internal parts. Portable removal keeps the sole inventory, FE, progress and fluids while placement assigns fresh part identities.
* Added counted mixed item/fluid recipes, two input/two output fluid ports, atomic result reservations and deferred inventory callbacks. Basic refining, plastic, sulfur and assembler-made advanced circuits are usable in survival.
* Added original animated textures, buckets, EN/RU menus/tooltips and recipe requirement previews; `/factorycraft oilworks` builds a powered finite-source line with inserter-fed coal and chest output.
* Added 8 independent JUnit and 16 GameTests, including full-chain conservation, protection/portable placement, generated reserves and exact four-chunk process reload. Fixed an active-state update resetting the pump's target cache, exposed by the rate tests.
* Heavy/light oil, advanced refining/cracking, acid/batteries, research and the other later systems remain planned; see PETROLEUM.md.

# 0.4.0 — steam power (8 November 2026)

* Added compact fuel-fired boiler, container-only steam/steam bucket and steam engine, original animated textures/models, EN/RU tooltips and fuel/fluid/FE menus. `/factorycraft steamworks` builds a working source → pump → boiler → steam → engine → pole-powered furnace example.
* Boiler defaults to 200 mB water → 200 mB steam over 20 fueled ticks; blocks safely on insufficient input/output. Engine consumes up to 10 mB/t at 12 FE/mB (120 FE/t), retains generated energy, stops before overflow and supplies existing poles or guarded FE extraction.
* Added independent boiling JSON recipes with NeoForge sized/tagged fluid ingredients, native component-bearing results, codec bounds and stream synchronization. Existing item recipes/schemas stay unchanged.
* Atomic fluid conversion, stored unspent fuel heat/progress, fuel-container remainders, directional cached-port guards, early chunk freeze and portable full machine contents.
* Added 11 independent JUnit and 14 GameTests for conversion/energy ledgers, backpressure, fuel, buckets, rotation/unload, portable actual placement, codecs, partial production, a self-sustaining pump/furnace line and exact separate-process persistence.
* Prevented native buckets from silently erasing fluid components in block interaction; component-bearing steam refuses native bucket conversion. Filled portable blocks retain the original stack.
* Oil, general mixed item/fluid chemistry, temperature/heat networks and large thermal footprints remain planned; see STEAM_POWER.md.

# 0.3.0 — fluid logistics (8 November 2026)

* Added six-way fluid pipes (1000 mB), tanks (16000 mB) and directional electric pumps (1000 mB, 20 FE/working tick, up to 100 mB/port/tick), native water intake and integration with the existing pole grid.
* Independent finite-volume storage and budgeted active-edge transport; component-sensitive fluid identity, atomic phased transfers, backpressure, junction degree damping and loaded-only graph caches. No per-fluid Entity or block ticker.
* Standard guarded NeoForge fluid/FE capabilities, buckets, portable contents/FE on block harvesting, readonly volume/energy menus, EN/RU and original procedural assets/recipes. Added `/factorycraft waterworks` and `/factorycraft fluids`.
* Fixed an outgoing-chunk serialization race found by the new finite-volume disk fixture: canonical chunk ownership checks, immediate owned-chunk dirty marking and pre-snapshot unload detachment.
* Added 17 JUnit and 16 dedicated GameTests, including separate-JVM four-chunk fluid conservation, live NBT reload, early unload, capabilities, bucket/filled-block placement and powered water supply. Existing production, belt, multiblock and benchmark saves remain readable.
* Stage 3 remains partial: steam power, oil, chemistry, underground pipes and automatic discovery of external fluid inventories are future work. Model and limits are specified in FLUIDS.md.

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
