# Architecture decisions — 0.6.0 (assembly/parts schema 3; native resource schema 1)

## Authority and persistence

All authoritative world access is on the server thread. Client renderers only extrapolate snapshots. Each block entity saves schema, inventory/carriers, phase/progress, selected/active recipe, fuel and energy as applicable. Vanilla block-entity update packets handle tracking clients; vanilla menus sync inventory and numeric data and validate recipe selection on the server. There is no client production simulation or per-carrier Entity.

An unloaded chunk pauses its machines. The power cache unregisters unloaded nodes. Belt scheduling discards unloaded references and reactivates loaded carriers. Neighbor lookups use `hasChunkAt`; production never force-loads a neighbor. The deliberate fixed-position persistence GameTest loads its fixture chunk, outside runtime simulation code.

Saving uses vanilla chunk persistence. Extraction into an inserter moves ownership from the source to the serialized hand. If a target disappears or fills, the hand keeps the item. Breaking a belt iterates until every carrier has been removed, avoiding index-shift loss. Breaking an inserter drops the held stack. Breaking a machine drops its inventory.

## Transactions

Recipes first reserve counts in a non-mutating per-slot plan, simulate the entire output, and verify available energy. A completed recipe revalidates inputs/output, then commits input extraction and output creation in one server-thread operation. Recipe ingredients are not consumed while progress is partial. A blocked output spends neither input nor energy. Removing inputs pauses progress; changing selection resets progress without consuming the inputs. Ordered overlapping predicates may conservatively reject a valid allocation but cannot spend one count twice.

Inserters first simulate source extraction and target insertion. Actual extracted contents are held persistently; target insertion returns an unaccepted remainder, which stays in the hand. Runtime assumes a conforming NeoForge `IItemHandler`: external mods that lie about returned counts violate that API's contract and cannot be made transactional from outside.

This provides conservation during ordinary server ticks, saves and chunk unloads. It is not a cross-chunk write-ahead journal guaranteeing atomic disk recovery after a process crash mid-region-file write.

## Belts

Each block has two bounded lanes of four individual carriers. A segment has 96 integral coordinate units; spacing is 24 units. Nine server microsteps run inside each Minecraft tick (180 Hz); basic/fast/express carriers move 1/2/3 units per microstep. This preserves the existing 0.09375 / 0.1875 / 0.28125 blocks/tick speeds and gives exactly 15/30/45 items/s for two continuously supplied lanes in the independent kernel tests. Minecraft inventories and inserters operate at their own tick cadence; these kernel rates do not certify an entire in-game production line.

`BeltTransport` first projects all active lanes, then resolves cross-segment headway and destination reservations with an iterative worklist. Integral projections only decrease, bounding ordinary movement corrections to three units per carrier per microstep. It removes every outgoing carrier before inserting any incoming carrier, so a completely packed ring can move without requiring an artificial empty slot. Carriers retain their residual distance on transfer and are reused rather than allocated on each segment. At a speed transition the new speed applies on the next microstep (at most 1/180 s later); remaining spatial travel is retained.

Merges reserve the last quarter block before an entry. Competing sources wait before the junction and receive round-robin service; ordinary merging never moves a waiting item backwards. The physical 2×1 splitter alternates between two forward exits independently per internal lane and safely falls back to a free exit. Its per-lane preferences are persisted. Its controller still contains two shared internal lanes; full two-belt throughput, filters, priorities and underground variants remain future work.

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
