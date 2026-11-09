# Architecture decisions — 0.9.0 (assembly/parts schema 3; native resource schema 1)

## Authority and persistence

All authoritative world access is on the server thread. Client renderers only extrapolate snapshots. Each block entity saves schema, inventory/carriers, phase/progress, selected/active recipe, fuel and energy as applicable. Vanilla block-entity update packets handle tracking clients; vanilla menus sync inventory and numeric data and validate recipe selection on the server. There is no client production simulation or per-carrier Entity.

An unloaded chunk pauses its machines. The power cache unregisters unloaded nodes. Belt scheduling discards unloaded references and reactivates loaded carriers. Neighbor lookups use `hasChunkAt`; production never force-loads a neighbor. The deliberate fixed-position persistence GameTest loads its fixture chunk, outside runtime simulation code.

Saving uses vanilla chunk persistence. Extraction into an inserter moves ownership from the source to the serialized hand. If a target disappears or fills, the hand keeps the item. Breaking a belt iterates until every carrier has been removed, avoiding index-shift loss. Breaking an inserter drops the held stack. Whole-machine demolition preserves resources in one portable controller item; parts never hold inventory copies.

## Transactions

Recipes first reserve counts in a non-mutating per-slot plan, simulate the entire output, and verify available energy. A completed recipe revalidates inputs/output, then commits input extraction and output creation in one server-thread operation. Recipe ingredients are not consumed while progress is partial. A blocked output spends neither input nor energy. Removing inputs pauses progress; changing selection resets progress without consuming the inputs. Ordered overlapping predicates may conservatively reject a valid allocation but cannot spend one count twice.

Inserters first simulate source extraction and target insertion. Actual extracted contents are held persistently; target insertion returns an unaccepted remainder, which stays in the hand. Runtime assumes a conforming NeoForge `IItemHandler`: external mods that lie about returned counts violate that API's contract and cannot be made transactional from outside.

This provides conservation during ordinary server ticks, saves and chunk unloads. It is not a cross-chunk write-ahead journal guaranteeing atomic disk recovery after a process crash mid-region-file write.

## Belts

An ordinary belt has two bounded lanes of four individual carriers; a splitter controller owns four lanes, addressed by two physical track positions. A segment has 96 integral coordinate units; spacing is 24 units. Nine server microsteps run inside each Minecraft tick (180 Hz); basic/fast/express carriers move 1/2/3 units per microstep. This preserves the existing 0.09375 / 0.1875 / 0.28125 blocks/tick speeds and gives exactly 15/30/45 items/s for two continuously supplied lanes in the independent kernel tests. Minecraft inventories and inserters operate at their own tick cadence; these kernel rates do not certify an entire in-game production line.

`BeltTransport` first projects all active lanes, then resolves cross-segment headway and destination reservations with an iterative worklist. Integral projections only decrease, bounding ordinary movement corrections to three units per carrier per microstep. It removes every outgoing carrier before inserting any incoming carrier, so a completely packed ring can move without requiring an artificial empty slot. Carriers retain their residual distance on transfer and are reused rather than allocated on each segment. At a speed transition the new speed applies on the next microstep (at most 1/180 s later); remaining spatial travel is retained.

Merges reserve the last quarter block before an entry. Competing sources wait before the junction and receive round-robin service; ordinary merging never moves a waiting item backwards. The physical 2×1 splitter has four lanes and alternates forward exits independently per lane, falling back to a free output. Routing includes already committed upstream approach reservations, rather than requiring the entire entry spacing before allowing an approach. This avoids wasting half the aggregate capacity with phase-shifted inputs while respecting quarter-block headway. Per-lane preferences persist. See SPLITTERS.md; filters, priorities and underground variants remain future work.

A per-level adapter caches loaded lane-to-lane links. Only placement, removal, load/unload and orientation changes refresh the affected block and four horizontal neighbors; there is no full-factory topology scan per tick. Facing invalidation occurs after `BlockEntity.setBlockState`, because Minecraft 1.21.1 calls `onPlace` before updating the existing block entity's state. Empty/jammed lanes sleep and are woken by inventory, position or connection changes. Neighbor lookups never force-load chunks.

Changes are batched once per block per game tick. Full block snapshots are sent on item-count changes and every four moving ticks. The client renderer extrapolates for up to four ticks and uses the complete block-length path instead of compressing it by 10%. Client prediction is visual, not an independent authoritative collision solver; curved junction paths and per-network delta packets remain open.

### Upgrade from 0.1.0

Belt schema 2 writes integral `Units` plus the legacy normalized `Position` field. The reader accepts schema 1 and copies the old splitter preference to both lanes when `AlternateRight` is absent. Actual 0.1.0 coordinates on the 1/32 grid convert exactly. Arbitrary edited legacy positions round to the nearest 1/96 block (maximum 1/192 block adjustment).

A legacy save or changed topology may already contain overlapping cross-block positions, which 0.1.0 allowed. On first simulation the solver conservatively shifts upstream carriers back to restore headway; no carrier is discarded. This one-time reconciliation can move an item back by up to a quarter block at a junction. Normal movement from a valid topology is forward only. Machine and inserter schema 1 data remain unchanged.

## Power

`simulation.power.PowerTopology` is independent of Minecraft. Loaded poles use a 3D spatial hash with cell size equal to the configured radius; only 27 neighboring cells are queried. Rebuilds proceed through capture, local candidate checks/union-find, component labels, device coverage and removal quarantine. Empty bucket probes and candidates both spend work units. `powerGraphWorkPerTick` limits logical work per level (default 4096); amortized Java collection operations mean this is not a hard wall-clock deadline.

Snapshots are published atomically. Additions keep the old valid snapshot serving existing consumers. Removing a pole immediately disables its previous component; unrelated components remain online. A builder can finish while additions continue: later additions are processed in the next snapshot, and a budgeted removal queue quarantines any obsolete edges before publication. Radius changes cancel the current calculation and suspend old coverage. This avoids starving graph publication during normal continuous loading. World access stays in the server adapter; the independent graph never loads chunks.

Consumer lists are shared per wire component. Routes carry a generation number rather than retaining obsolete full snapshots; detached capture links are released under the rebuild work budget. Each generator has only its covered component IDs, cursors and a bounded reusable recipient batch, rather than a copy of all reachable consumers. Demand preparation visits at most 32 candidates, deduplicates overlapping memberships and simulates acceptance. Delivery rechecks liveness and the snapshot/component before committing FE. Fuel ticks advance only when some energy was actually accepted. Sources and consumers do not merge otherwise disconnected pole networks merely because their coverage overlaps.

`PowerGrid` ticks the bounded rebuild after each server level tick. All node load/unload/place/break operations are O(1) registry updates; matching graph changes are processed incrementally. Coal generation remains an internal network source; steam-engine FE extraction is implemented. Batteries, solar and nuclear remain future work. Power coverage uses the lower horizontal center of a full-size machine. Diagnostics are available through `/factorycraft power`.

## Multiblock ownership and lifecycle — 0.6.0

MachineProfile defines all 13 current large footprints and explicit 3D heights. CuboidLayout rotates offsets from a front-left-bottom controller; AssemblyOwner/AssemblyAccess compose the existing machine, thermal, fluid and splitter kernels without duplicating resources. IndustrialAssembly (now generic) validates and caches canonical part identity/readiness. Parts store only anchor/UUID and expose guarded item, FE and fluid forwarding; they have no production tick.

MachineItem/PlacementPlan validate the entire loaded volume before writing. Normal NeoForge captured snapshots cover the controller and generated parts; cancellation restores cells and item count. The client PlacementPreview uses the same inspect function and draws volume/footprint/blocked cells; it never authorizes server writes. StructurePlacement is only an administrative fixture helper and is not the player placement path.

One portable machine item owns all inventory/fluid/FE/progress after any part/controller destruction or Shift-wrench. StructureSupport strips Assembly from the item so new placement binds a fresh UUID. Parts never drop as resources. Creative removal returns no machine item. Early AssemblyLifecycle chunk unload invalidates the owner and freezes ports before save; reload rechecks identities. Readiness scans parts only when topology/availability changes, not on every production tick.

0.6.0 intentionally starts new worlds: old compact assemblies are not migrated, as explicitly permitted by the user. Native processing/boiling/chemistry recipe formats remain unchanged. See MACHINE_DIMENSIONS.md and RESOURCE_FIELDS.md.

## Timing and performance evidence

All recipe durations are Minecraft ticks (20/s). Machine and inserter block tickers remain individual in this slice; active scheduling is implemented for belts, not all production. World mutation never runs on background threads.

The JUnit kernel benchmark now exercises 10000 complete two-lane segments (20000 lane nodes) for 100 Minecraft-equivalent ticks, with one carrier per lane in two closed rings. It verifies conservation and records CPU time without a hardware-dependent pass threshold. That kernel result excludes world costs. The separate `runBenchmarkServer` harness now exercises the actual 10000/1000/500 dedicated-world scenario; see BENCHMARKS.md for its deliberately finite inventory, no-client scope and measured results.

## Extension boundaries

`recipes` codecs support ordinary datapack replacement and `/reload`. `registry` owns NeoForge integration; `simulation` is independently testable; `production` adapts energy and menus; `energy` owns topology; `client` is client-only. Future fluids, research, circuits, trains, robots, pollution, combat and space require new packages and tests. No nonfunctional placeholder registrations represent these systems.

## Opt-in full factory profiling (0.2.2)

The Gradle `benchmarkServer` run uses a separate marked directory, flat void world and loopback port. `FactoryBenchmark` is inert unless its JVM property and directory/marker checks match. The deterministic independent `BenchmarkPlan` has 100 closed 100-block rings over ten elevations, 250 production cells and a shared graph of 499 poles. Each cell contains two ordinary furnaces, one assembler, one automatically formed industrial furnace, two real inserters, a source chest and a fueled generator. Total: 14249 blocks, including 1750 non-ticking parts. Setup uses 256 placements per tick; only this explicit test harness pins its 65 chunks. No normal factory path is granted chunk-loading behavior.

After warmup and JFR settling, ServerTickEvent.Pre/ Post capture 600 work durations (sleep is excluded). Census and report serialization are outside those durations. A pole is removed/replaced inside sampled ticks; real graph rebuild/distribution costs remain included. `FactoryTelemetry` adds opt-in counters to existing active work, not a whole-factory scan per tick. Dirty belt notifications prove every segment changed; actual completed recipes/deliveries prove producer/inserter liveness. Audits scan fixed fixture positions before/after sampling and during explicit restart verification. Raw iron/plates count as one unit, gears as two; items held by inserters are included. Stored FE plus delivered FE must equal final FE plus actual recipe spending. Generation still burns real coal.

`BenchmarkRecording` retains CPU/allocation/GC samples and Minecraft PacketSent/PacketReceived events. Packet totals are filtered to the measured wall interval and count encoded bytes, not TCP framing or estimated updates; a zero-client run must report zero clients explicitly. JFR setup/catch-up is allowed to settle before measurement. Reports include raw observed tick frequency (which can briefly exceed the configured target during engine catch-up), percentiles, outliers, GC, graph work and BE sync request counts. The benchmark PASS criterion is correctness/activity; it never silently skips jammed/idle equipment to obtain faster timings and never declares a hardware-independent TPS pass.

Profile-guided hot-path changes preserve ownership/speeds: index incoming-node lists in the belt worklist, avoid per-node iterators, mark internal motion/progress/FE dirty without inventory comparator fanout, and avoid marking twice when sending an already-dirty belt update. Real inventory changes still use ordinary setChanged/sync neighbor notifications; states with an analog output fall back to them. Persistence formats and authoritative simulation are unchanged.

The diagnostic checkpoint additionally hashes canonical authoritative NBT before shutdown and verifies it before the next process's first tick. Compound keys are sorted; generated client snapshot/animation timestamps are excluded. After an exact match and resumed audit, verify atomically advances its checkpoint to the legitimately resumed state after flushing the world. It never accepts a mismatched hash by recomputing the expected value. This metadata is confined to the disposable fixture; normal game NBT schemas are unchanged.

## Fluid slice (0.3.0)

`simulation.fluids` is Minecraft-independent: one canonical integer-mB cell, immutable fluid identity, reserved phased transfers and a cached active-edge FIFO graph. The world adapter under `fluids` keeps loaded adjacency and pump queues; no fluid block has a ticker. `FluidKey` owns a copied FluidStack and compares fluid plus data components. `FluidBlockEntity` exposes stable, lifecycle/orientation-checked NeoForge ports and one persisted cell. `GridConsumer` lets existing power snapshots supply either production machines or pumps without duplicating the grid.

Before mutation, the adapter checks the canonical LevelChunk/BE identity through ServerChunkCache.getChunkNow. Dirty changes mark the owned chunk unsaved immediately. ChunkEvent.Unload freezes/removes fluid nodes before ChunkMap serializes outgoing chunks; ordinary BE unload callbacks occur too late for this guarantee. Client reads use the client mirror, while all mutations require the server-owned loaded node.

New fluid NBT schema 1 uses the native FluidStack codec, retaining data components. Restored over-capacity volume remains drainable instead of being discarded. Harvested fluid blocks use vanilla BLOCK_ENTITY_DATA; freshly crafted empty blocks are stackable; harvested structures carry controller NBT. Assembly/parts use schema 3; native resource payloads stay unchanged. Menu volume/capacity/registry IDs are split into 16-bit words for vanilla data slots; BE snapshots are staggered to at most once per five ticks for fluid changes. This is full BE synchronization, not a measured delta protocol. FLUIDS.md specifies pressure math, pump order, energy and the distinction from Factorio 2.0.

## Thermal slice (0.4.0)

`simulation.thermal.BoilerProcess` holds unspent fuel heat, recipe-definition identity and progress. `FluidCell.convertTo` validates/reserves and commits both fluid stores before observers; input is consumed only when the full result fits. Fuel and final progress are updated before conversion callbacks. `SteamEngine` owns generated FE before draining/notifying the steam cell; integer 12-FE quanta avoid discarded steam at a nearly full buffer. Oversized saved FE remains extractable.

`ThermalBlockEntity` owns its sole inventory, two cells and both kernels. Directional transfer resolves only its two precomputed neighboring positions through getChunkNow and canonical BE identity; no forced loads or world scan. It pulls/pushes finite volumes into the existing fluid cells, rather than adding a second pressure model. Stable capability objects recheck lifecycle and facing on each mutation. ThermalLifecycle detaches at the pre-serialization chunk event. Inventory notifications are deferred during refueling until the fuel remainder and stored burn ticks have an owner, then normal inventory notifications resume.

The independent `factorycraft:boiling` recipe type uses SizedFluidIngredient.FLAT_CODEC, native FluidStack components and a bounded validated stream codec. Existing item-processing codecs are unchanged. Sorted definitions refresh at most once/20 ticks per boiler; a canonical codec-encoded definition in saved recipe identity prevents object identities from corrupting restart progress or edited datapacks from reusing another recipe's work.

GridGenerator classifies the existing coal generator and new steam engine as sources in the same cached graph; GridConsumer behavior is retained. Engine output uses stored generated FE only. The 0.4.0 dedicated benchmark reload checks old authoritative hashes. Thermal menus use validated vanilla slots/data and split 32-bit values; only changed state requests BE synchronization on a five-tick cadence. New equipment has normal per-BE server tickers; large fluid/thermal-world profiling and batched thermal scheduling remain future optimization work.

## Petroleum composition (0.5.0)

MachineBlockEntity owns an optional PetroleumProduction composition for pumpjack/refinery/chemical plant. The old production and boiling codecs/data stay unchanged. Shared IndustrialAssembly/one-item placement now accepts another large controller kind and distinct internal refinery parts; generated parts serialize only anchor/identity/flags. Portable refinery NBT carries controller resources but omits Assembly, so the native block-item merge preserves a newly formed identity rather than importing stale part ownership.

BatchInventory plans all eight item slots independently of Minecraft. Chemical recipes use fixed numbered fluid cells, bounded native codecs and a registry-aware ChemicalInput. FluidCell.commitBatch validates distinct cell mutations and applies all fluid owners, then a validated callback-free item/progress commit, before observers. Machine inventory notifications defer during this commit; normal inventory neighbor notifications resume only after the full result is owned. Observer errors cannot replay a completed recipe. Per-tick FE pays for retained progress; no counted input is spent before completion.

FluidConnections resolves only canonical loaded positions through getChunkNow. Processor nozzle positions are cached until facing changes; active/lit updates do not invalidate geometry/targets. Pumpjack search is bounded to 72 loaded positions on an occasional retry. Deposit block entities do not tick. PetroleumLifecycle detaches controllers/reservoirs and invalidates affected refinery parts before outgoing chunk serialization; dirty changes mark the owning chunk immediately. Casing fluid capability objects revalidate canonical part/owner/readiness every operation.

OilDepositFeature uses bounded worldgen-region writes because vanilla ore generation writes chunk sections directly without registering BE-bearing reservoirs. Stock initialization is one-time, independent from remaining amount; initialized zero never becomes a new reserve. Static fluid/recipe keys and old schemas remain compatible. The new Petroleum subtag uses schema 1 with Fluid0..Fluid3, progress/duration, selected recipe ID and canonical active-definition string. Future extra fluid outputs must preserve those indices and default added tanks to empty.

## Large surface fields and centered placement (0.6.1)

ResourceVeins is a world-independent deterministic grid/contour/stock sampler. ResourceVeinFeature writes only its decorating chunk through WorldGenLevel, resolves the terrain surface and initializes new resource BEs without server callbacks from generation workers. COMMON startup config factorycraft-worldgen.toml supplies bounded radius/stock ranges and spacing; configured-feature JSON can override them per resource. Existing initialized stock is never overwritten. No ticking world-global vein registry or forced chunks are introduced.

Player targeting is the horizontal base center. MachineProfile.anchorAtCenter converts it to the unchanged schema-3 corner controller, including a deterministic right/back choice for even dimensions. CenteredPlacementContext keeps native BlockItem components/NBT/sound/cancellation at that controller and forces the exact coordinate; BlockPlaceContext.at would shift upward from an obstructed corner. PlacementPlan accepts raw centered or already converted contexts exactly once, and the renderer highlights the selected center. Saved 0.6.0 worlds and the full benchmark remain physically unchanged.

## Unified visual geometry (0.7.0)

MachineGeometryLoader bakes CellMesh surface fragments into the native chunk model. ModelData carries immutable local profile/right/up/back coordinates for a part; the model performs no world access during getQuads. Geometry and original textures are reproducibly generated from MachineProfile dimensions by tools/machine_geometry.py. Rotation and active indicator variants are baked once. Static surfaces render in their own chunks even if the controller is unloaded. Only moving details use MachineAnimationRenderer; phases are client-only, freeze when idle and have no production authority. Complete collision/placement volumes stay unchanged. See MACHINE_VISUALS.md.

## Advanced refining and third output (0.8.0)

PetroleumProduction composes five cells only for a refinery; chemical plants/pumpjacks remain four. Existing Fluid0..3 persist at their original indices; refinery Petroleum schema 2 appends Fluid4, empty for legacy saves, and preserves overflow/components. Assembly schema 3 remains unchanged. Fixed old nozzle transforms stay; new output 3 is the controller's front face (front-left of the whole 5×5 machine). Codec/wire arrays support up to three refinery results, while a chemical plant constructor rejects three. Every output participates in pre-work capacity/component reservation and the same multi-cell/slot/progress commit. The fifth tank does not become a copied part owner or passive graph node.

Five-row UI/hover help uses the existing BE sync/menu data; localized recipe labels fall back to output names for datapacks. The example's water/byproduct pipes use separated height levels to prevent fluid mixing. Complete line tests count every owned chemical and plate, not only output arrival. See PETROLEUM.md and VALIDATION.md.

## Team research and optional viewers — 0.9.0

ResearchLedger is independent unit/prerequisite/level logic; ResearchData persists provider-prefixed scopes in overworld SavedData. LabBlockEntity owns packs/FE/canonical work and uses ordinary assembly lifecycle/power. Packs commit only after validation of the team's still-selected unfinished unit, with all item callbacks deferred. Changing teams/providers never implicitly copies units. FTB is linked only behind runtime detection/reflection of its verified public API; unavailable installed API fails closed. Vanilla names and personal UUIDs are explicit fallbacks. New native item-placed machines bind scope after portable NBT; legacy omitted scope stays ungated and serializes no new empty field.

TechnologyRecipe uses native recipe sync with bounded JSON/wire definitions. ResearchCatalog caches sorted definitions/canonical signatures and rejects missing/cyclic prerequisite paths. Menu snapshots are sent only to valid viewers, throttled and change-filtered; C2S IDs are validated against current lab/menu/distance/team/prerequisites. Ordinary slot clicks also validate membership to close a stale-menu race. Server snapshots drive the searchable dependency UI. EMI/JEI share a client-only display projection and never execute recipes; optional classes are absent from common simulation linkage.

## 0.10.0 progression and fluid ownership

The second assembler composes PetroleumProduction instead of changing legacy assembler ownership/tickers/NBT. It projects dry processing recipes into its mixed executor at cache refresh, preserving native IDs/gates and canonical definitions, and also accepts native chemistry definitions for its machine ID. Original chemistry fields/wire order stay intact; untrusted JSON constructor validation is converted to DataResult errors. A typed SimpleFluidContent component and standard NeoForge item capability own canister contents. Recovery stages a private carried-stack copy and commits that owner with the source-cell remainder before observers; menu/current-player/range/assembly/canonical unload checks remain server-side. No forced chunk loads or new async world access are introduced.

## 0.11.0 module transactions and beacon scheduling

MachineModules composes a separate fixed-slot inventory and recipe-keyed fractional/productive work into the controller. Empty state emits no NBT and preserves the old production path. Output reservations include a pending bonus before any FE debit; item/fluid/credit commits precede observers. BeaconIndex buckets loaded controllers/recipients, invalidates local candidates and bills block-ticking beacons at level pre-tick using a paid work lease. Candidate readiness is rechecked for part unload; inactive chunks/frozen ticks cannot spend FE. Normal quality uses 1.5/sqrt(n) transmission. No per-item entities, forced chunks or asynchronous Level access are added.


## 0.12.0 metric transport

The existing simultaneous BeltTransport kernel now uses lane-local integer lengths/capacities, including all reservation/headway/ring calculations. BeltRoute owns geometry only; BeltSimulation resolves loaded-only cached topology. Physical lift/incline parts forward guarded ports and store no item copies. Underground inputs own the entire virtual route; outputs have no simulation nodes and dynamically guarded delegation. Default flat NBT is unchanged. See BELTS.md for distance, migration, placement/protection and demolition semantics.

## Transport policies and block-ticking lifecycle (0.13.0)

The original `BeltTransport` kernel accepts an optional item routing policy. SplitterPolicy maps each head to allowed physical outputs and an optional preferred output; an item filter selects complementary routes, never a second inventory. Input priority participates in approach reservations. Once a head has a committed output, an incompatible live settings change lets that approach finish before the new policy applies to the next head. Four saved commitments survive disconnect/reconstruction. Default settings add no NBT, preserving the old full-factory hash.

SplitterMenu uses native server menu buttons/data slots and a nonextractable item-ID preview. Clicking the ghost never transfers or consumes the carried stack. Every mutation and ordinary inventory click checks the current menu, canonical loaded structure, player distance and spectator state. Each viewer receives the same authoritative state. Item components are intentionally ignored by this first filter.

BeltSimulation indexes physical ownership by chunk, including both splitter cells and both underground endpoints. `ChunkTicketLevelUpdatedEvent` demotes affected owners immediately; promotions wait for native block-ticking readiness through a pending-chunk set. Only pending chunks are checked each tick. Frozen ticks do not advance the transport kernel. Chunk unload detaches owners before serialization; temporarily inactive routes retain resources and approach reservations. Runtime transport never pins chunks.

The common single-output/single-input kernel path skips redundant routing choice and reservation work, using direct lane access. It retains the general algorithm for merges/forks/filters. A differential test compares every carrier ID, integral position, order and output for 12 random seeds × 3000 steps, including tier/length changes, priorities and inactive nodes. World lookups stay outside microsteps.

## Tiered splitters (0.14.0)

FactoryKind.splitter() and MachineProfile.splitter() define all three variants. Fast/express enum/profile entries are appended, preserving existing profile order/IDs. Every variant uses the original BeltBlockEntity four-lane owner, SplitterMenu/Policy and cached transport; there is no second simulation or inventory. Tier enters the existing speed parameter. Parts remain identity-only and use matching low collision/mesh profiles. Static meshes reuse original materials with red/blue trim and direction markings; existing profile geometry is unchanged.

New processing/chemistry recipes use the verified NeoForge 21.1.252 DataComponentIngredient (`neoforge:components`, strict true, empty component predicate) for the base device. It matches the native default ItemStack including its normal component defaults, rejecting all extra payloads. This prevents consuming portable cargo/settings as a manufacturing ingredient. Existing recipe codecs, old definitions and technology signatures remain unchanged. Logistics 2/3 are additive datapack technologies; native machine selection/research gates and existing EMI/JEI categories discover them normally.

## Electric arms (0.15.0)

FactoryKind.inserter() registers four models through the existing BE/renderer. ArmCycle holds only paid phase math; InserterBlockEntity owns the single native ItemStack hand. FE is committed per movement/action quantum, with no idle/blocked billing. Cached source/target positions invalidate on facing changes; capabilities are queried at transfer opportunities, not on each paid swing tick. Filter changes affect only future pickups. A reentry guard prevents synchronous handler callbacks from running a second transfer.

Electric arms alone register as grid consumers and write Actuator schema 1. Mechanical serialized keys/timing remain unchanged. Native loaded/canonical/block-ticking/freeze guards protect tick and cached FE receivers; early ChunkEvent.Unload detaches before serialization. Electric block loot is empty because onRemove emits a single packed owner holding native hand components, FE, phase and configuration; the old owner is cleared/quarantined. Native menus use standard bounded buttons/data/ghost-slot packets and validate ordinary inventory clicks too. Arbitrary foreign handler callbacks and JVM crash journaling are not claimed atomic across files.
