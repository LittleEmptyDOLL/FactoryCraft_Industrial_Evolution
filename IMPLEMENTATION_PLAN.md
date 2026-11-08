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

Read MACHINE_DIMENSIONS.md, RESOURCE_FIELDS.md, VALIDATION.md and FEATURE_MATRIX.md. Start a **new** 0.6.0 world. Reproduce build, two GameTest processes and benchmark/reload. Current geometry and stock work supersedes compact fixtures; no old-save migration is required by the user.

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

1. Continue stage 3 advanced refining: water + crude → heavy/light oil + gas, then cracking, lubricant, acid/batteries. Reserve every output; add a third physical outlet on the 5×5 refinery. Keep basic refining readable and usable, extend native codecs/menus/ports without losing stored fluids. Add blocked third-output, full material ledger, portable and separate-process tests.
2. After chemistry, implement team research/labs/science packs and recipe gating with a usable bilingual research interface. Keep underground/priority/filter belts and curved-path polish visible as incomplete work in FEATURE_MATRIX.md.


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

Next session: implement priority 1 above (advanced refining with a third real outlet), then research. Do not reset existing diagnostic checkpoints, reduce footprints or replace finite resources with one-drop ores. Filters/priority/underground belts, exact curved full-flow paths, final art, actual two-network-client tests and the later roadmap remain incomplete.
