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
* Ordinary belt NBT schema 2 reads schema 1; splitters use schema 3/four lanes and read old two-lane data. Machine inventory stays schema 1, assembly/casing schema 3; refinery Petroleum schema 2 appends Fluid4 while other petroleum machines stay schema 1. Unloading pauses simulation. Whole-machine demolition retains resources in one portable owner.
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

Read AGENTS.md, PETROLEUM.md, MACHINE_DIMENSIONS.md, RESOURCE_FIELDS.md, VALIDATION.md and FEATURE_MATRIX.md. Follow the current priority list below for the next progression/fluid-recovery work. Preserve existing 0.6.0+ worlds and exact diagnostic checkpoints; do not reset or silently reseed a failed fixture. A fresh world is required only for compact versions older than 0.6.0. Build and run relevant native tests, including separate processes for saved-data changes and the marked benchmark compatibility check.

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

1. Add underground belts with bounded loaded-only pairing, both independent lanes, stored in-flight ownership, blocked/unloaded output conservation and real inserter/port integration; then priority/filter splitter controls. Preserve module/research/chemistry fixtures and legacy checkpoint hashes.
2. Extend modules/beacons and higher science tiers, then logistics/circuits. Continue UI polish/coverage tools and profile large research/petrochemical factories separately; keep robots/trains/hostile/space work honest in FEATURE_MATRIX.md.


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
