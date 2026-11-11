# FactoryCraft: Industrial Evolution — implementation plan

Target: Minecraft **1.21.1**, NeoForge **21.1.252**, Java **21**, ModDevGradle **2.0.148**.

## Architecture and invariants

* `factorycraft.registry`: deferred blocks/items/block entities/menus/recipes; client registration is isolated.
* `simulation.belts`: independent two-lane kinematics; no item entities. One item per carrier, 0.25 block spacing within and across segments, backpressure and phased transfers preventing double advancement.
* `blockentity`, `production`, `recipes`: persistent item handlers, server-authoritative production, data-driven counted ingredients. Outputs are simulated before inputs are committed. Inputs are revalidated at completion.
* `energy` / `simulation.power`: spatially indexed, work-budgeted snapshots rebuilt only on topology changes. Removed components are quarantined immediately; independent components keep working. No forced chunk loads.
* `multiblock`: controller-owned resources and progress; casing parts have only an identity and stable guarded forwarding ports. Larger machine footprints may use this design, per the user’s instruction.
* `inserters`: a persistent held stack owns extracted items until the destination accepts them; breaking drops held items.
* `client`: renderers interpolate server snapshots; production menus use vanilla buttons; research uses bounded native payloads. Every request is validated server-side.
* Ordinary belt NBT schema 2 reads schema 1; splitters use schema 3/four lanes and read old two-lane data. Machine inventory stays schema 1, assembly/casing schema 4; refinery Petroleum schema 2 appends Fluid4 while other petroleum machines stay schema 1. Unloading pauses simulation. Whole-machine demolition retains resources in one portable owner.
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

Maintain the delivered automation/energy/fluid/chemistry slice and the first stage-4 labs, team science, research gates and optional recipe viewers. Stage 3 still lacks later power/heat equipment. Feature completion requires evidence; see FEATURE_MATRIX.md and VALIDATION.md. Later stages are not implied by block registration.

## Next session

Read AGENTS.md, MACHINE_DIMENSIONS.md, FLUIDS.md, PETROLEUM.md and the current VALIDATION.md entry. Schema-4 assemblies intentionally require fresh worlds; retain older world/checkpoint directories as archives and never reset/reseed them. Follow the active user-priority task at the end, not older roadmap headings. Full benchmark and optional-mod profiles remain CI work unless an affected behavior requires local execution.

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

1. Continue with counted bulk/stack inserter transfers (see final next-task section). Preserve four-color circuit bindings, splitter approach reservations, headway and legacy defaults.
2. Extend inserters with bulk/stack capacity only after a counted transfer/reservation model covers partial target acceptance, held batches, hand component equality, power budgets and portable/chunk/process state. Keep the existing four models and all diagnostic fixtures intact.
3. Extend modules/beacons and higher science tiers. Continue coverage tools and profile large research/petrochemical factories separately; keep robots/trains/hostile/space work honest in FEATURE_MATRIX.md.


## Delivered in 0.4.0 — steam power

Implemented compact single-block boiler and steam engine, container-only steam/bucket, standard guarded fluid/item/FE capabilities, original animated assets, EN/RU and working menus. The fueled boiler uses an independent sized/tagged fluid recipe type with atomic conversion, bounded codecs and canonical persisted recipe identity. Engine generation supplies the existing pole graph and preserves integer FE/steam balance. Fuel remainders, unspent burn ticks, progress, fluids/components and generated FE survive portable placement and separate server processes. Native buckets refuse lossy component transfer; filled tanks remain the portable option. See STEAM_POWER.md and VALIDATION.md for exact ratios, tests and limits. Next work remains mixed item/fluid transactions and finite oil/refining, as prioritized above.

## Delivered in 0.5.0 — petroleum

Implemented finite deposit → electric pumpjack → pipes → one-item 2×2×2 refinery → petroleum gas → coal-fed chemical plant → plastic, plus sulfur and assembler-made advanced circuits. Mixed recipes reserve all item/fluid outputs and consume counted inputs only on completion; per-tick FE and canonical recipe identity persist. New refinery parts forward to one controller; portable item NBT excludes the old assembly identity so placement binds fresh parts correctly. A BE-aware custom worldgen feature creates finite reservoirs; the native ore feature is unsuitable for direct BE placement. Tests cover complete material ledgers, blocked/underpowered operation, native codecs, cancellation, portable placement, worldgen and exact separate-JVM state across four chunks. See PETROLEUM.md and VALIDATION.md. Next work is advanced refining with a third output and extended chemistry as prioritized above.

## Completed milestone — 0.6.0 resource fields and real dimensions

User overrides petroleum expansion: replace each custom iron/copper/coal/stone tile with a persistent finite reserve, including transactional manual harvesting and drill extraction without removing a nonempty tile. Generalize automatic controller/part assemblies to all current machines (factory, thermal, tank/pump, splitter); use Factorio tile footprints with one tile = one Minecraft X/Z block. Share rotated footprints, complete loaded/collision/permission validation, item placement and a colored client volume preview. Heights are explicit Minecraft design choices because Factorio is 2D. Old saves may break by user instruction; update physical GameTests, example factories and full benchmark instead of retaining compact geometry. Verify actual large-machine automation, exact reserves, placement cancellation, full-state portable removal, chunk lifecycle and client preview.

## Delivered in 0.6.0 — finite resource tiles and actual footprints

Replaced one-drop industrial ores with persistent finite iron/copper/coal/stone tile stocks, repeated survival harvesting, 5×5×8 drill access and BE-aware generation. All 13 current larger machine types now use MachineProfile footprint/height definitions and a common one-item protected placement transaction. PlacementPreview shows the same validated volume, blocked cells and controller anchor. Every part forwards to one canonical owner; physical fluid nozzles and power coverage use the real structure. Cross-chunk availability stops the whole machine before serialization.

Adapted the iron, industrial, water, steam and petroleum examples/integration fixtures, including portable inventories/fluids and full-size production. The opt-in benchmark now builds 45249 cells (32500 structural parts), retaining 10000 belts/1000 producers/500 inserters and one shared grid. Old worlds are intentionally unsupported under the user's explicit scope. Remaining visual shells and splitter throughput limits are documented; see VALIDATION.md for evidence.

## Delivered in 0.6.1 — large veins and centered placement

User priority: generate large surface resource fields with configurable size/spacing and finite per-tile stock ranges; replace corner-based item targeting with the horizontal center of the machine base. Preserve the existing internal controller anchor and schema-3 identities so placed 0.6.0 structures/resources remain intact. Share exact center-to-controller conversion between preview and the native BlockItem placement pipeline, including protection, portable NBT and rollback. For even dimensions use the right/back middle cell. Verify all profiles/facings, obstructed corners, native terrain generation across chunk seams, configured stock bounds, actual client placement and existing factories/disk fixtures.

Completed deterministic organic surface veins with COMMON radius/stock/spacing configuration, per-resource datapack overrides, chunk-local generation writes and callback-free initial stock. Added centered base targeting/preview while preserving schema-3 controller positions and portable NBT. 87 JUnit and 115 GameTests cover shapes, stock ranges, connected fields/seams, native generation, codecs, existing factories, all 13 profiles/facings, obstructed/unloaded corners, protection, portable state and separate-process reserve persistence. Existing full benchmark checkpoint remains valid. See VALIDATION.md for client/natural-world checks.

0.6.1 continuation is delivered in 0.7.0 below. Retain centered targeting, large finite fields and one-item full structures in subsequent milestones.

## Delivered milestone — 0.7.0 unified machines and full splitter tracks

Original geometry now covers all 13 profiles through native per-cell chunk meshes, with activity-driven moving components, matching 3D items and physically placed nozzle markings. Surface splitting preserves UVs and emits no artificial internal caps. Static bodies render independently of the controller chunk. Full collision volumes, centered placement and schema-3 assembly identities remain.

The splitter now owns two physical tracks/four lanes, each independently persistent; both native inputs and forward outputs work, with balancing/backpressure and local inserter pickup. Reservation-aware approach routing sustains two full basic belts across 24 input phases without discarding or overlapping carriers. Legacy two-lane saves and full portable state remain readable; cached unloaded ports and part-track pickup were fixed. Added a finite-input admin showroom. See VALIDATION.md, MACHINE_VISUALS.md and SPLITTERS.md for checks and limitations.

The advanced-refining continuation is delivered in 0.8.0 below; retain the 0.7.0 geometry/transport guarantees in subsequent work.

## Delivered milestone — 0.8.0 advanced refining and chemistry

Delivered advanced oil, both cracking steps, lubricant, sulfuric acid and batteries through native JSON chemistry recipes. The full-size refinery owns five cells/three real outlets; old Fluid0..3 and ports remain, refinery Petroleum schema 2 appends an empty Fluid4 for old worlds. Other machines retain schema 1; assembly/part schema 3, centered placement and 0.7.0 meshes/transport remain. All results reserve before FE, and all owners commit before observers.

Added localized named recipes/port help, five-row menus, a third marked nozzle, native buckets/original assets and an actual finite oil/water/raw-plate → sulfur → acid → battery example. Independent five-owner tests and native blocked/mismatched/underpowered/nozzle/bucket/codec/portable/live/disk scenarios plus a full multi-product/plate material ledger pass. The preserved 0.7.0 full factory also verifies on 0.8.0. See PETROLEUM.md and VALIDATION.md for exact checks, client evidence and limits.

The team labs/science/research continuation is delivered in 0.9.0 below. Do not reset checkpoints or silently alter bootstrap/old recipe availability. Lubricant consumers, accumulator/engine recipes, steam heat/temperature, huge petrochemical profiling, actual two-network-client play and the remaining initial scope are still incomplete.

## Delivered milestone — 0.9.0 team research and optional recipe viewers

Implement real 3×3 one-item labs, automation/logistic science packs, bounded datapack technologies, persistent shared team units/prerequisites/unlocks and a searchable dependency UI. Resolve FTB Teams via its verified 1.21.1 public API when installed, otherwise vanilla scoreboard teams, otherwise player UUID. Keep provider namespaces and never silently merge/copy progress on membership changes; unavailable installed providers fail closed. Labs retain fixed team binding/sole pack/FE/work ownership through portable placement/unload. Complete units atomically against shared state before callbacks. Existing 0.6.0–0.8.0 machines/admin fixtures remain legacy-ungated; new player-owned machines enforce explicit technology recipe locks. Optional EMI and JEI integrations expose item/fluid/thermal/research recipes without client-class loading on dedicated servers. Verify native transactions, team isolation/change, gating/migration, codecs, separate-process saves, viewer-absent/present startup and actual graphical UI. Publish tested work to main with expected-head lease; next tasks stay documented.

Delivered 14th full-size profile (lab), red/green science, six bounded native-synced technologies, independent research ledger, overworld SavedData, fixed team lab binding, guarded transactions and production recipe unlocks. FTB Teams' verified 1.21.1 public API is isolated behind optional reflective linkage; absent FTB uses vanilla teams/personal UUIDs. Joining/leaving/providers do not copy progress. Existing machines without ResearchScope stay legacy-ungated, preserving benchmark hashes and 0.6.0+ factories. New player machines bind at native post-NBT placement; portable binding persists.

Added authoritative menu payloads and team/distance/prerequisite validation, dependency/search/pan UI, full-size lab visuals/animation, optional native EMI/JEI categories and a real automated science example. Tests cover shared last units, science/FE, unload/portable/disk, team changes, stale menu extraction, gating and actual automation. Both absent and installed optional-mod dedicated profiles pass. See VALIDATION.md and the new research/viewer guides for exact evidence/limitations. Next tasks are the current priorities above; do not reset diagnostic checkpoints or silently merge team research.

## Delivered milestone — 0.10.0 chemical science and fluid recovery

Add steel/engines, a second-tier 3×3×3 fluid assembler using the existing mixed transaction engine, electric engines/lubricant and processors/acid, chemical science and six bounded prerequisite technologies. Keep old assembler logic, recipe codecs and exact legacy NBT unchanged. Add a component-preserving 4000-mB canister with NeoForge item fluid capability and validated petroleum-menu recovery for input leftovers; never discard/round fluid or allow stale/spectator/unloaded menu mutation. Test actual production/gating, backpressure/energy, full-size placement/portable/chunk/disk state, native item components and GUI/viewers, then build, dedicated profiles and the preserved full benchmark. Publish tested changes to main with an expected-head lease.

Implemented steel/engines, second fluid assembler with inherited dry recipes, electric engines/processors, chemical science and six new technologies. Added native component-preserving canister, exact-volume input recovery and a finite component-fed engine/science example. Existing assembler and six technology definitions remain unchanged. Native tests cover three-pack shared completion, gates/energy/backpressure, pipes/canister return, stale/unloaded menus, placement/geometry, portable/live reconstruction and exact four-chunk disk state. Final commands/results/client limits are recorded in VALIDATION.md. Next session starts at priority 1 above; do not reset any existing diagnostic fixture.

## Delivered milestone — 0.11.0 modules and beacons

Implement real speed/efficiency/productivity modules with separate persistent slots, bounded fixed-point work, recipe-specific productivity credit, complete output reservations and server-owned menus. Preserve the exact empty-module legacy path/NBT/checkpoints. Add powered 3×3×3 one-item beacons with local spatial invalidation, no forced chunks, unavailable-part guards and speed/efficiency-only transmission. Verify independent arithmetic, native production/FE/backpressure/recipes/portable/chunk/disk, optional dependencies, graphical menus and benchmark compatibility before publishing main.

Delivered local module arithmetic/GUI, canonical actual-work productivity, powered spatial beacons, progression and finite comparison factory. Follow MODULES.md and the exact validation results/limits. Next session starts with the underground-belt task above; do not reset old checkpoints or the retained unreleased v1 module failure evidence.

## Delivered milestone — 0.12.0 unified 3D transport

User scope: directional automatic curves, physically length-aware up/down slopes, two-stage one-item lifts (up/down, configurable max 16), then Factorio-style virtual underground pairs (4/6/8 hidden cells). Extend BeltLane/BeltTransport with per-lane metric lengths and capacities, retaining ordinary 96-unit lanes/NBT and 15/30/45 item/s. Separate immutable route geometry, loaded-only cached routing, physical auxiliary-part ownership and client meshes. Keep two independent lanes, exact headway/order/backpressure, protected all-cell placement, sole-controller content, safe dismantling and no forced chunk loads. Verify kernels, packed rings/merges/splitters, native item placement/protection, inserter ports, unload and separate-JVM saves, actual graphical paths and full legacy checkpoint before publishing. Preserve upstream artwork.


Delivered variable metric lanes in the original simultaneous kernel, directional curves and up/down clearance-backed slopes, all-tier two-stage one-item up/down lifts and virtual underground pairs. All generated parts are identity-only; ordinary default NBT remains exact. Underground endpoints choose roles/pairs automatically and guard cached output capabilities. Wrench controls, original chunk-baked ribbons, interpolation, item recipes, EN/RU and `/factorycraft transportworks` are included. See BELTS.md and VALIDATION.md for geometry, distance, migration, protected placement, exact cross-process checkpoints and graphical evidence. Next session starts with the mixed-world profiling/natural-unload work above, then filter/priority controls; do not reset fixtures.


## Delivered milestone — 0.13.0 mixed transport validation and splitter controls

Create an opt-in MIXED full-size benchmark in run-benchmark/mixed with its own marker/checkpoint, real slopes/up-down lifts/virtual terminals and 10000 transport controllers, existing 1000 production machines and 500 inserters. Audit exact material/FE/identities and every moving resource owner, native ticket-release chunk unload/reload, then a separate JVM. Keep legacy full world/hash untouched. Add splitter output/input priorities and an item filter through the existing reservation kernel, persistent settings, guarded server menu and original EN/RU UI. Preserve default NBT/throughput. Verify pure routing/headway/fairness, native configuration/portable/unload/menu and integrated production; real client and optional dependencies; publish tested main. Actual two-network-client coverage is described below.

Delivered the mixed physical workload, native 200-chunk unload/reload and exact separate-process checkpoints. A cached ticket/readiness index now pauses belts outside block-ticking chunks, including both splitter cells and underground ends. Retained the original unload failure and resumed the same world without refilling. JFR-guided single-route optimizations retain exact differential equivalence; measured mean tick work improves from 61.809 to 48.372 ms on this two-processor workload (p95 still 62.721 ms).

Basic splitters now have persistent input/output priority, strict complementary item-ID filters and native shared menus. Existing committed approaches finish safely after settings change. `/factorycraft sortingworks` demonstrates real finite-source sorting. Two actual loopback Minecraft clients exercised simultaneous settings, ghost-item conservation, natural player-distance unload/return and saved-world server restart in RU/EN. See NETWORK_QA.md, SPLITTERS.md, BENCHMARKS.md and VALIDATION.md. Continue with priority 1 above; do not reset retained checkpoints or claim the remaining Factorio systems are complete.

## Delivered milestone — 0.14.0 fast and express splitters

Append fast/express kinds and profiles without renumbering existing geometry IDs. Reuse the sole four-lane BeltBlockEntity, shared settings/menu/reservations, centered one-item protected placement, low collision silhouette and original baked meshes. Add distinct red/blue markings, research-gated assembler recipes (express consumes lubricant), bilingual recipe/viewer entries, finite tiered sorting example. Verify exact saturated 30/60/90 item/s kernel throughput and all-tier policies/mixed tiers; native placement/menus/parts/portable/production/gating/live/disk, preserved old checkpoints and both load worlds. Launch an actual client and optional mod server, then publish tested main with an expected-head lease. Do not reset/reseed diagnostic worlds.

Delivered appended fast/express kinds/profiles with 2×1 centered one-item protected structures, low casing collisions, four sole-owned lanes, shared menus/filters/priorities and matching red/blue geometry. Existing enum/profile ordering and all older mesh definitions stay unchanged. Added Logistics 2/3, a dry fast recipe and lubricant-fed express recipe in existing assemblers/viewers. Native strict component ingredients reject resource-owning portable bases without consuming their cargo. Added `/factorycraft tieredsortingworks` with three finite-source real sorting lines. Exact 30/60/90 item/s saturated throughput, all 27 tier combinations, priority/backpressure, native placement/production/gating/portable/chunk/disk and old checkpoints are covered; see VALIDATION.md for final results and graphical limits. Continue with priority 1 above, preserving every existing diagnostic world.

## Delivered milestone — 0.15.0 electric inserters

Implement fast (reach 1, swing 3 ticks, 4 FE/work tick), long (reach 2, swing 7, 3 FE/work tick) and filter (reach 1, swing 3, 4 FE/work tick) variants through the original persistent-hand model. Keep the mechanical swing-12/free path and legacy NBT exact. Add guarded pole/FE input, early chunk/freeze gating, one-owner portable electric demolition, live filter/enable menus, reach-scaled client arms, additive research/assembler recipes and a finite powered example. Check pure cycle math, native transactions/parts/belt positions/power starvation/menus, exact separate-JVM hand/FE/settings and old checkpoint compatibility. Run affected local validation; leave independent full CI jobs to GitHub and hand off with their pending status rather than polling. Circuits/bulk/stack are separate future work.

Delivered fast/long/filter electric arms in the original persistent-hand adapter, independent paid ArmCycle, pole/guarded FE input, all-direction cached reach, server ghost/hand menus, early chunk/freeze guards and exactly one portable electric device on demolition. Added original colored bases/reach-scaled animation, one additive technology, component-strict assembler recipes and the finite coal/pole-powered `/factorycraft inserterworks`. Mechanical NBT/timing and old factories remain compatible. See INSERTERS.md and VALIDATION.md for measured costs, actual checks and remaining CI status. The next task is priority 1 above; do not start it automatically.

## Delivered slice — 0.16.0 bounded circuit networks

Delivered reusable red/green wire tools, constant/arithmetic/decider programs, inventory readers and adjacent electrical inserter/pump controllers. New one-item profiles have centered previews and distinct physical IN/OUT cells. Cached independently bounded components retain reciprocal UUID links, integer wrap semantics and staged signal outputs. Native menus validate ordinary slots/buttons and signed payloads; portable devices clear old coordinates without losing programs/resources. Added the circuit research, manufacturing recipes, original geometry/wires, EN/RU text, diagnostics and finite coal-powered eight-item feedback example. CIRCUITS.md defines polling/overload latency, direct fail-closed behavior, downstream sample-hold and the deliberate zero-FE logic limitation. Native persistence checks retain separate circuit/link and controller/held-item checkpoints. See VALIDATION.md for exact checks; independent optional/full CI jobs remain separate.

### Completed continuation after 0.16.0

Extend existing circuit readers with explicit item/fluid modes and component-sensitive fluid readings, preserving unavailable-as-unknown semantics and strict saved binding checkpoints. Add real pump-tank feedback, native guarded menu tests and an honest sensor-latency budget. Consider powered logic and wildcard rules only as separately validated additions. Do not start robots/trains/blueprints automatically. See CIRCUITS.md for the exact delivered scalar scope and output sample-hold behavior.

## Delivered slice — 0.17.0 fluid circuit readers

Extend the existing reader with item/fluid modes and component-sensitive native fluid samples, bounded physical-side capability reads and a server-guarded mode/filter menu. Keep default 0.16 circuit NBT unchanged, omit absent settings, and retain invalid/unavailable readings as unknown. Add finite coal-powered tank/pump feedback, cross-chunk sensor quarantine, portable/strict separate-JVM state and real client checks. Reuse the existing graph/controller path; no second circuit engine, logic FE or wildcard expansion in this slice.

Delivered native item/fluid reader settings, exact-component sample selection, physical-port measurements, unknown-state UI, target-chunk dependency quarantine, configurable polling and finite coal-powered pump feedback. Existing 0.16 defaults and earlier diagnostic worlds remain readable. See CIRCUITS.md and VALIDATION.md for actual evidence and last-packet/queue limits.

### Completed continuation after 0.17.0

Add circuit enable conditions to the existing splitter controls without duplicating the transport simulation. Define disabled-segment/headway and already committed approach behavior, preserve settings/portable ownership/default NBT, and test all-tier saturated routing, signal changes under congestion, chunk unload and separate-JVM persistence. Keep powered logic, wildcards, trains/robots/blueprints as separate future work.

## Delivered slice — 0.18.0 splitter circuit gates and four wire colors

Use existing scalar controllers to gate all splitter tiers. Add a separate motor-enable bit in the original kernel, keeping physical connections, exact stopped positions and committed approach claims; no item may enter/exit a halted splitter. Cache event-driven gates and loaded binding indexes, avoiding world access in microsteps or scans of all belts. Preserve default NBT and portable ownership. Per the user's new request, append blue/white as independent color IDs 2/3; keep red/green IDs 0/1, item recipes and all legacy links. Verify paused merges/rings/throughput, native gates/four-color menus/portable/chunks, exact separate-JVM saves and actual client rendering.

Delivered cached event-driven gates on all splitter tiers, a distinct kernel motor bit with static occupancy/approach claims, occupied-chain wakeup for packed rings, native menu status and portable/default-NBT compatibility. Appended blue/white colors preserve red/green link IDs and per-color graph independence; original assets, recipes and native tools are included. See VALIDATION.md for actual checks and retained failure evidence.

### Next concrete task after 0.18.0

Add bulk/stack electric inserters with counted hand ownership and partial target acceptance through the existing ArmCycle. Define exact energy/stack/quality-component rules, retain paid phase and held remainders through gate changes/chunks/portable state, and test real belt/machine/chest integration plus separate-JVM saves. Keep per-input circuit color masks, powered logic, wildcard rules, robots/trains/blueprints as separate future work.

## Active user-priority overhaul — 0.19.0 schema-4 machines

Explicitly supersedes the bulk/stack continuation. Separate layout origin/controller/placement anchors; unify Factorio-2.0.77 physical fluid ports (INPUT/OUTPUT/BOTH, distinct cell channels) and geometry from one descriptor. Fix real-hit/reach preview, whole-machine interaction and one-owner portable teardown. Preserve production kernels. Old schema-3 worlds/checkpoints are archived, not migrated/reset; new native fixtures use an isolated v0190 directory. Check all profiles/facings, physical capabilities/pipe exchange, no mixing, protection/rollback, menus, chunk lifecycle and separate-JVM persistence plus a real client. No unrelated progression/circuit/pole/deposit features.

## Delivered 0.19.0 — independent multiblock geometry and physical ports

Controller-local positions and placement anchors are separate from layout origin. Shared validated JSON covers eight fluid machines, guarded INPUT/OUTPUT/BOTH views, same-point distinct channels, physical pipe shapes and baked markers. Wrench X reflection persists and preserves channel contents; one portable item carries all state with a new placement identity. Menus retain clicked-part contacts and module navigation. Real-hit/reach checks remove MISS/air preview. Small/medium/large placement modes share PlacementPlan. Technical parts have no items/loot. New schema-4 worlds/checkpoints are isolated from preserved archives.

### Next task within this overhaul

Review the new CI results once requested. Migrate the thirty explicitly archived hardcoded showcase/old-port diagnostics to actual descriptor endpoints and restore their full end-to-end layouts, starting with thermal, oil and fluid-circuit administrative examples. Do not mark them passed meanwhile. Consider recipe-dependent assembler port visibility without breaking existing auto selection. These are remaining limitations, not permission to start inserters, poles, circuits/selectors, recipe UI, worldgen or progression work. Local results and exact follow-up scope are recorded in VALIDATION.md.

Local validation for 0.19.0: 152 JUnit / 260 required native tests, repeated process persistence including mirror state; smoke measure/reload PASS; real client placement/MISS/blocked/part menu/pipe/wrench reflection and clean save/reopen. Full/mixed and optional profiles remain CI. See VALIDATION.md and the explicit 30-scenario archive before continuing.

## Delivered 0.20.0 — power distribution and information parity

Extend the existing incremental topology with explicit reciprocal pole edges; separate exact Factorio area/wire metrics and finite vertical base reach. Reuse schema-4 one-owner assemblies for four pole footprints/heights. Add reusable copper wiring tool, pole status, cached visible sagging wires and coverage preview. Resolve machine names/readonly Jade data through the canonical owner without exposing broader automation capabilities. Preserve recipes/speeds/fluids/circuit colors. New fixtures/benchmarks are isolated; optional Jade and normal client/server must be tested. Publish only this scope to main.


Four poles now form full schema-4 structures from one item, with exact independent normal-quality supply/wire parameters. The existing budgeted topology uses saved reciprocal UUID-bound edges, spatial footprint coverage and immediate cut quarantine. Automatic placement wiring, reusable manual toggle/clear, pole status, original clipped geometry, cached sagging wires and real-hit square/proposed-link overlays are integrated. Controller/part names use one canonical path; optional Jade 15.10.6 reads sole-owner snapshots without changing fluid/item/FE automation ports. New recipes and two technology gates use documented material/bootstrap adaptations. See ELECTRICITY.md and VALIDATION.md for mechanics and actual checks.

### Handoff — stop at this requested scope

Do not begin the next roadmap item automatically. Full/mixed benchmark/reload and combined FTB Teams + EMI + JEI + Jade remain CI work; no full-CI wait is required for this publication. Existing thirty schema-3-coordinate showcase diagnostics remain explicitly archived, not newly passed. On a later authorized maintenance task, migrate those physical port routes and restore their end-to-end ledgers. Later powered equipment, richer electrical production charts, quality-scaled poles and broader mod interoperability remain separate work. Intermediate one-block/proximity pole saves are not migrated or deleted; new diagnostic paths and failure evidence are retained.

Local validation for 0.20.0: **157 JUnit / 278 required native tests**, repeated with Jade 15.10.6 in another JVM; exact reciprocal wire/731-FE/3-diamond checkpoint verified. Bounded smoke measure and separate-process reload pass in `smoke-v0200-probe`; failed harness/arena evidence preserved. Real clients with/without Jade, controller/part full inspection, live wire toggle/clear, coverage/MISS, saved wire restore and nonresurrection after manual removal checked. Resource generation is byte-idempotent. No full/mixed local rerun or complete CI wait. All implementation and current limits are documented in VALIDATION.md.


## Active 0.21.0 — unified Factorio 2.0 inserters and restored integration

1. Audit original 2.0.77 prototype numbers, old single-filter/single-item assumptions and all thirty archived physical scenarios.
2. Replace ArmCycle with paid 60-Hz phase units at 20 TPS, one counted component-exact hand, fuel/FE adapters, universal five filters, team capacity effects/manual limits and partial delivery. Standard becomes electric; Burner replaces free mechanical bootstrap; remove Filter type.
3. Extend the existing ItemStack carrier adapter (not transport solver) for bounded 2–4-item stacks, partial extraction, stack-only deposition, real vertical render layers and item/carrier accounting. Integrate all route forms/splitters/circuit gates.
4. Update unified menu/animations/original models, recipes and capacity research; keep Space Age Stack unavailable through an invented early survival recipe.
5. Restore archived finite input → production/research/thermal/chemical/circuit/module scenarios at actual schema-4 physical ports; track each original guarantee explicitly.
6. Run build, targeted JUnit/native tests, separate-JVM state, bounded benchmark and real clients; update evidence/limits, publish to main, stop.

Implemented: six shared paid-phase devices, solid self-fueling, five filters, component-exact counted/partial hands, team/manual capacity, native physical stacks and partial extraction, all-route integration, unified guarded menus and original animated models, recipes and explicitly gated Space Age diagnostics. The thirty archived scenarios are required again with real schema-4 ports, explicit power and finite chain ledgers. The old separate filter device/recipe and unused archive annotation are removed.

Validated: 160 JUnit; 336/336 required native tests and new-process exact six-hand/three-stack fixtures (details in VALIDATION.md); bounded SMOKE/verify with weighted carrier/item and arm FE ledgers; real EN/RU clients and saved stack restore. Old diagnostic failures/worlds are retained. Full/MIXED and optional-mod combination profiles stay CI.

### Handoff — finish only this requested scope

Publish 0.21.0 normally to main after final checks; report the workflow once and do not wait for all CI jobs. No next roadmap item has been started. A later **explicitly authorized** task can replace the documented late-science mapping and creative-only Stack/height definitions when actual Space Age progression exists. Direct machine circuit wires, Selector Combinator, recipe-selection GUI, ore/oil generation, robots/trains and unrelated power/machine work remain separate tasks. Preserve all new resource invariants and restored required tests in future work.


## Active 0.22.0 — Circuit Network 2.1 overhaul

Reference Factorio 2.1.24, upstream tag commit acf5a342afcb66c066b9d88d202e8622112da117. Extend existing CircuitGraph (typed sparse maps, cached components, bounds) and CircuitSimulation (universal attachments, four-color masks, staged outputs/bounded sensors). Canonical machine controllers own state; external containers use FactoryCraft SavedData/capability opt-in. Preserve power/fluid/production kernels and archived finite chain guarantees.

1. Typed signal algebra and structured constant/arithmetic/decider/selector programs, pure tests.
2. Universal contacts/lifecycle/saving and explicit direct wires; existing legacy objects remain archival adapters in the same graph.
3. Read-only device sensing and guarded enable/filter/hand/recipe/research actions; indexed finite-pipeline aggregation without hydraulic changes.
4. Structured EN/RU settings/signal selection/diagnostic menus, server validation and client-only contact/wire rendering.
5. Direct finite factories, required native invariants, cross-JVM fixtures, bounded circuit benchmark and real clients; publish only verified work and record limits.

Implemented: typed multi-signal contributions in the existing graph; canonical direct four-color contacts and independent masks; full structured Constant/Arithmetic/Decider/Else/Selector/Time programs; guarded device/filter/hand/recipe/next-team-research controls; delta/budgeted loaded pipeline totals; EN/RU structured menus and cached camera-relative wires. Reader/Controller recipes/unlocks are retired; all three diagnostic examples now use direct contacts.

Validated: build / 176 JUnit; all 357 required native cases and clean separate-JVM `v0220-checked` reload, including all thirty restored production guarantees, typed INT_MIN/unknown IDs, independent masks, SavedData-owned chest links, exact persisted feedback-port output maps and output-limit recovery without truncation. The bounded 1024-port/128-signal benchmark changes values without topology visits. Client evidence and final publication are recorded in VALIDATION.md. The intermediate shutdown crash is preserved separately and fixed by no-load shape queries and demoted-belt synchronization guards.

### Handoff after this requested scope

This requested development stage is complete within the documented limits. Real EN/RU client checks, separate-process world reopening, direct machine/lab controls and four-color wires are recorded in VALIDATION.md and screenshots. The final handoff reports the normally published main commit and workflow once; do not wait for all CI jobs or start another stage. Do not start the next roadmap task. A future explicitly requested stage can address **physical Factorio 2.1 hydraulic mixing/segment rules**; no such solver or new machines/progression were implemented here. Quality/rocket-weight selectors wait for actual corresponding subsystems. Preserve the twenty-one new direct-circuit cases, all old required fixtures and the final strict cross-JVM checkpoint; never reset an old failed world.


## Active 0.23.0 — Transport 2.1 and original industrial visuals

1. Audit current transport and Minetorio/Create references/licenses; keep numerical transport geometry authoritative.
2. Add persisted six-type inserter reflection, physical far-lane / ambiguous-axis selection and strict splitter input deposition, guarded menu and hover guidance. Add capacity-2 prerequisite without removing sandbox gating.
3. Build original bounded cell-local belt/lift meshes, distinct frames/rails/rollers/terminals, tier animation and continuous local lift sections; no per-frame model reconstruction.
4. Validate pure/native/cross-JVM conservation and geometry; real client placement, ports, all route forms/stacks and screenshots; record measured geometry costs and unmeasured limits.
5. Publish checked changes to main without force and stop. Preserve prior diagrams/checkpoints.

Implemented: six-kind additive reflected state/native control/swing/physical hover hints, exclusive far/ambiguous lanes and splitter-input deposits, unchanged paid transport kernels; original tier surfaces, frames, rails, rollers, terminal hoods, splitter mechanism and modular lift entry/shaft/rotated exit meshes. Lift mesh work is cell-local and its cache bounded; closed-box backfaces are culled. Capacity 2 now explicitly lists bonus 7 while remaining sandbox-only. A separate operator `transportlab` creates finite visual cargo and real source-fed arm examples in a checked clear volume.

Validation/handoff: see VALIDATION.md for final JUnit/native/separate-JVM results, old/new geometry and real-client profiling, screenshot evidence and limits. Minetorio/Create review and **future proposals only** are in TRANSPORT_REFERENCES.md. Once this checked 0.23.0 stage is published, report CI once and stop. Do not automatically start fluids, drag building, pole spacing, route editing, robots or Space Age progression. Later visual work may measure a larger hardware-rendered client stand and replace global atlas animation with a stop-aware shader only if its complexity and cost are justified.

Final local mechanics evidence: **181 JUnit / 371 required GameTests twice in separate JVMs**, normal shutdown, all old production checkpoints retained; additive reflected state also checked in filled portable devices. Real-client tier/route/lift/mirror/placement checks and original-0.22 client comparison are recorded. Geometry work/quads decrease, but the one llvmpipe frame-time sample is slower (33.0 → 35.5 ms): **client performance parity remains unproven**, with hardware/large-scene follow-up explicitly outstanding. Publication must retain this limitation, not claim an FPS improvement.


## Active 0.24.0 — pooled fluid segments

1. Verify 2.1.24 sources; replace passive equalization with a bounded incremental PipelineNetwork and one runtime pool per compatible connected segment. Keep the old kernel only as a regression/benchmark reference.
2. Preserve physical ownership with lazy capacity-prefix projections for chunk save and eager materialization only on topology changes; atomic boundary transfer and packing, UUID/canonical/no-load guards.
3. Integrate six-way pipes/tanks, directional powered pumps, real machine ports, pressure-scaled per-boundary limits, shared circuit readings, statuses/menu/nearby membership preview.
4. Test compatibility, extent 319/320/321, components, finite chains, unload/restart/portable transactions and bounded 0.23/new workload; actual client; document limits and publish main.

Chosen ownership: attached physical FluidCells are projections of a single pool, never duplicated authoritative stores. Saves derive exact per-member shares by stable capacity-prefix differences of floor(total*prefix/capacity). Structural invalidation freezes those shares before membership changes; ordinary volume operations are O(1), and persistence dirty marking is batched over cached owning chunks rather than all pipes. No crash-atomic cross-region journal is claimed. Reference FFF-442 takes precedence over 416/430 and stale wiki passages for 2.1 mixing/filter/port scaling.

### 0.24.0 delivery / handoff

Implemented the single passive pipe/tank pool, stable checkpoint shares, guarded shared capabilities, component/filter conflict boundaries, extent quarantine, powered directional pump quotas, actual machine input filters, direct circuit readings and EN/RU segment menu/nearby overlay. No new production machines, recipe rebalance, circuit graph rewrite, construction tool or lift-art work was started. Fluid schema 2 is the new checkpoint format; assembly 4 remains unchanged. The old finite-pressure kernel/document is retained for comparison, not runtime.

Final evidence: build with **196 JUnit**, **380 required native cases twice in separate JVMs** at `v0240-finalguard`; all thirty restored finite production chains remain active. Four-chunk **3943-mB grade-24** shares/UUIDs restore exactly. Actual client waterworks, survival bucket/vertical branch, conflict warning, pump/menu/highlight and separate-client-JVM reload were checked. Offline client ledger is **22400 mB across fifteen unchanged owners**. Resource regeneration is byte-identical; raw bounded old/new results and limitations are in VALIDATION.md / validation/fluid-segments-v0240-kernel.txt. Full/MIXED/optional CI remains a publication check, not a locally claimed pass.

Next work **only after a new user request**: profile very large in-world pipe/pump topologies and bound atomic freeze/final-commit spikes without weakening independent chunk ownership; investigate conservative unfinished-build restarts under unrelated construction churn. The researched two-point construction design and separate lift-model followup are recorded in docs/design/construction-followup.md, with no implementation in this release. Do not automatically move to machines, progression, trains, robots or Space Age.

## Active 0.24.1 — fluid performance

1. Baseline unchanged 0.24.0 kernel at 100/1000/5000/10000 and opt-in dedicated pipe/tank/pump/recipe/copper/circuit workload; preserve raw samples/JFR and independently saved fixture.
2. Use profiles to target needless invalidation/allocation and unbounded finalization; keep exact physical checkpoint projections and unavailable rebuild state.
3. Regression-test work bounds/no rebuild on 10000 content operations, component conflicts, unrelated topology churn, partial finalize/save/unload, and all existing required native chains/restart.
4. Repeat comparable kernel/server workloads, document all tails/tradeoffs, update architecture/benchmark/validation, publish main. No following roadmap task starts automatically.

### 0.24.1 delivery / handoff

Implemented content-only notifications; conservative local leaf/in-component-edge edits; indexed pending heap and small sorted edge arrays; build stamps and affected-only cancellation (including staged boundary metadata); one-division-per-member exact freeze; ordered-traversal sort skip, budgeted merge moves/staging/cache preparation and one publication flag; counted chunk membership, dirty-flag and pump/sync deduplication. Fluid rates, FE, recipes, components, physical ports and schema-2 NBT remain unchanged. Attached raw cells are inert; owned() is the sole save view.

Evidence: **204 JUnit / 381 required native cases**, including all old 380 and thirty finite production chains. Warm kernel profiles cover 100/1000/5000/10000 and thirty-creation followups. Two real 10000-pipe baseline worlds and two final worlds run three event cycles, sixteen real pumps, finite chemistry and separate-process full-NBT/ledger verification; an unchanged 0.24.0 checkpoint also verifies with 0.24.1. Raw measurements and honest regressions are in BENCHMARKS.md, VALIDATION.md and validation/fluid-v0241. Full/MIXED/optional CI stays independent.

**Next only on a new user request:** attribute the observed 60–75-ms stable/construction tails with timestamped slow-tick/JFR correlation; reduce total general split/merge CPU and atomic O(N) freeze/prefix costs without weakening independent chunk ownership. Stable mean and some tails regressed, so do not claim an unconditional TPS improvement. No building tool, lift art, progression, new machines, robots/trains or Space Age work starts automatically.
