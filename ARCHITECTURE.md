# Architecture decisions — 0.2.0 (belt schema 2; machine/assembly/casing schema 1)

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

Merges reserve the last quarter block before an entry. Competing sources wait before the junction and receive round-robin service; ordinary merging never moves a waiting item backwards. The one-block T-splitter alternates forward/right independently per lane and safely falls back to a free exit. Its per-lane preferences are persisted. Filters, priorities, underground variants and two-block Factorio geometry remain future work.

A per-level adapter caches loaded lane-to-lane links. Only placement, removal, load/unload and orientation changes refresh the affected block and four horizontal neighbors; there is no full-factory topology scan per tick. Facing invalidation occurs after `BlockEntity.setBlockState`, because Minecraft 1.21.1 calls `onPlace` before updating the existing block entity's state. Empty/jammed lanes sleep and are woken by inventory, position or connection changes. Neighbor lookups never force-load chunks.

Changes are batched once per block per game tick. Full block snapshots are sent on item-count changes and every four moving ticks. The client renderer extrapolates for up to four ticks and uses the complete block-length path instead of compressing it by 10%. Client prediction is visual, not an independent authoritative collision solver; curved junction paths and per-network delta packets remain open.

### Upgrade from 0.1.0

Belt schema 2 writes integral `Units` plus the legacy normalized `Position` field. The reader accepts schema 1 and copies the old splitter preference to both lanes when `AlternateRight` is absent. Actual 0.1.0 coordinates on the 1/32 grid convert exactly. Arbitrary edited legacy positions round to the nearest 1/96 block (maximum 1/192 block adjustment).

A legacy save or changed topology may already contain overlapping cross-block positions, which 0.1.0 allowed. On first simulation the solver conservatively shifts upstream carriers back to restore headway; no carrier is discarded. This one-time reconciliation can move an item back by up to a quarter block at a junction. Normal movement from a valid topology is forward only. Machine and inserter schema 1 data remain unchanged.

## Power

`simulation.power.PowerTopology` is independent of Minecraft. Loaded poles use a 3D spatial hash with cell size equal to the configured radius; only 27 neighboring cells are queried. Rebuilds proceed through capture, local candidate checks/union-find, component labels, device coverage and removal quarantine. Empty bucket probes and candidates both spend work units. `powerGraphWorkPerTick` limits logical work per level (default 4096); amortized Java collection operations mean this is not a hard wall-clock deadline.

Snapshots are published atomically. Additions keep the old valid snapshot serving existing consumers. Removing a pole immediately disables its previous component; unrelated components remain online. A builder can finish while additions continue: later additions are processed in the next snapshot, and a budgeted removal queue quarantines any obsolete edges before publication. Radius changes cancel the current calculation and suspend old coverage. This avoids starving graph publication during normal continuous loading. World access stays in the server adapter; the independent graph never loads chunks.

Consumer lists are shared per wire component. Routes carry a generation number rather than retaining obsolete full snapshots; detached capture links are released under the rebuild work budget. Each generator has only its covered component IDs, cursors and a bounded reusable recipient batch, rather than a copy of all reachable consumers. Demand preparation visits at most 32 candidates, deduplicates overlapping memberships and simulates acceptance. Delivery rechecks liveness and the snapshot/component before committing FE. Fuel ticks advance only when some energy was actually accepted. Sources and consumers do not merge otherwise disconnected pole networks merely because their coverage overlaps.

`PowerGrid` ticks the bounded rebuild after each server level tick. All node load/unload/place/break operations are O(1) registry updates; matching graph changes are processed incrementally. The generator is still an internal network source; external FE extraction, batteries, steam and nuclear power remain future work. Diagnostics are available through `/factorycraft power`.

## Multiblock ownership and lifecycle

An industrial furnace is one controller and seven casings in a rotated 2x2x2 cuboid. `CuboidLayout` defines offsets; `IndustrialAssembly` owns shape validation, UUID/orientation and formed/readiness state. The controller alone stores inputs, outputs, FE, selection and progress. Casings store only the controller anchor and assembly UUID. Their stable item/energy forwarding ports resolve a live, ready owner for each operation, so a cached port cannot access a destroyed or unloaded inventory.

Formation validates every required loaded cell and ownership before binding any part. All seven identities are then reserved before emitting any block/neighbor updates; capabilities remain suspended until the formation transaction ends. Incomplete and overlapping structures fail without claiming a partial footprint. Casings have no ticker. A ready controller uses its cached validation; part removal/load/unload or controller changes invalidate it. Suspended validation examines only the fixed seven positions, retries at most once per second unless an event wakes it, and never force-loads a chunk.

Temporary controller inactivity is distinct from orphaning. A part loaded before its controller becomes active retains a matching UUID while capabilities remain unavailable. An actually missing or mismatched controller clears an orphan binding when both locations are loaded. Dismantling clears loaded bindings, leaves all resources in the controller, and lets remaining unloaded parts repair later. Breaking the controller drops its own inventory exactly once; breaking a casing never drops a copy of that inventory. Appearance updates check the current world block before writing, so unbinding cannot resurrect a casing already being destroyed.

The controller processes two recipe work units per server tick, charging only for those units; odd-duration recipes charge a smaller final tick. This doubles furnace throughput without changing FE per result. Assembly metadata is an optional schema-1 subcompound; existing single-block machines and their schema-1 inventories remain readable. Facing changes dismantle the old footprint. Sneak-wrench is handled in the item-first NeoForge hook, including when the offhand is occupied.

The current pattern is fixed in code; a datapack pattern system is not claimed. The user allows multiblocks for larger footprints, and future large machines can extend controller/part composition while retaining single ownership and bounded validation.

## Timing and performance evidence

All recipe durations are Minecraft ticks (20/s). Machine and inserter block tickers remain individual in this slice; active scheduling is implemented for belts, not all production. World mutation never runs on background threads.

The JUnit kernel benchmark now exercises 10000 complete two-lane segments (20000 lane nodes) for 100 Minecraft-equivalent ticks, with one carrier per lane in two closed rings. It verifies conservation and records CPU time without a hardware-dependent pass threshold. It excludes Minecraft block entities, networking, 1000 machines and 500 inserters; the full in-game 10000/1000/500 scenario remains open.

## Extension boundaries

`recipes` codecs support ordinary datapack replacement and `/reload`. `registry` owns NeoForge integration; `simulation` is independently testable; `production` adapts energy and menus; `energy` owns topology; `client` is client-only. Future fluids, research, circuits, trains, robots, pollution, combat and space require new packages and tests. No nonfunctional placeholder registrations represent these systems.
