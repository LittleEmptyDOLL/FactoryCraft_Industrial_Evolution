# Architecture decisions — schema 1

## Authority and persistence

All authoritative world access is on the server thread. Client renderers only extrapolate snapshots. Each block entity saves schema, inventory/carriers, phase/progress, selected/active recipe, fuel and energy as applicable. Vanilla block-entity update packets handle tracking clients; vanilla menus sync inventory and numeric data and validate recipe selection on the server. There is no client production simulation or per-carrier Entity.

An unloaded chunk pauses its machines. The power cache unregisters unloaded nodes. Belt scheduling discards unloaded references and reactivates loaded carriers. Neighbor lookups use `hasChunkAt`; production never force-loads a neighbor. The deliberate fixed-position persistence GameTest loads its fixture chunk, outside runtime simulation code.

Saving uses vanilla chunk persistence. Extraction into an inserter moves ownership from the source to the serialized hand. If a target disappears or fills, the hand keeps the item. Breaking a belt iterates until every carrier has been removed, avoiding index-shift loss. Breaking an inserter drops the held stack. Breaking a machine drops its inventory.

## Transactions

Recipes first reserve counts in a non-mutating per-slot plan, simulate the entire output, and verify available energy. A completed recipe revalidates inputs/output, then commits input extraction and output creation in one server-thread operation. Recipe ingredients are not consumed while progress is partial. A blocked output spends neither input nor energy. Removing inputs pauses progress; changing selection resets progress without consuming the inputs. Ordered overlapping predicates may conservatively reject a valid allocation but cannot spend one count twice.

Inserters first simulate source extraction and target insertion. Actual extracted contents are held persistently; target insertion returns an unaccepted remainder, which stays in the hand. Runtime assumes a conforming NeoForge `IItemHandler`: external mods that lie about returned counts violate that API's contract and cannot be made transactional from outside.

This provides conservation during ordinary server ticks, saves and chunk unloads. It is not a cross-chunk write-ahead journal guaranteeing atomic disk recovery after a process crash mid-region-file write.

## Belts

Each block has two bounded lanes of four individual carriers. Front-to-back positions use doubles in [0,1], minimum intra-segment separation 0.25. Straight connections preserve lane; side loading merges into the near lane and backpressures. A new arrival's tick stamp prevents a second movement in the same game tick, independent of block processing order.

Nominal base speeds are 0.09375 / 0.1875 / 0.28125 blocks per Minecraft tick. Their continuous-model two-lane rates are 15 / 30 / 45 items/s. **Actual inter-block throughput is reduced by 20 Hz boundary quantization and zero-position arrival; those exact nominal rates are not claimed as measured throughput.** Cross-boundary sub-tick spacing and residual distance are a next-stage correctness/performance task. Intra-segment spacing is enforced; the junction model is still discrete.

The one-block T-splitter alternates forward/right and falls back if the preferred exit is blocked. Filter/priority/underground variants are not registered as empty substitutes.

A per-level active queue updates moving belts. Empty and fully jammed belts sleep; insertion, extraction, topology/load and new downstream space wake neighbors. Updates use bounded arrays and reuse storage. Packets are sent on item-count changes/insertions/extractions and every four moving ticks. They currently contain a full snapshot of this small block, not network-wide deltas. Renderers extrapolate up to four ticks and respect local lane spacing.

## Power

Poles form a cached undirected graph of loaded nodes. Edges and coverage use a spherical configurable radius. Generator and consumer membership derives from nearby connected poles. Union-find rebuild occurs on node load/unload/place/break or a changed radius. It is synchronous and currently quadratic in pole count at rebuild time; a spatial index and deferred rebuild budget are required before claiming the full-scale target.

A generator walks at most 32 cached recipients per tick with a round-robin cursor. Machine buffers store up to 16000 FE; process ticks require the complete per-tick cost, so shortage slows average production. Generator fuel pauses when scanned recipients have no demand. Consumers expose NeoForge FE input capabilities. The generator is an internal network source; external FE extraction, batteries, steam and nuclear power are not implemented.

## Timing and performance evidence

All recipe durations are Minecraft ticks (20/s). Machine and inserter block tickers remain individual in this slice; active scheduling is implemented for belts, not all production. World mutation never runs on background threads.

The JUnit kernel benchmark exercises 10000 **lanes**, not 10000 complete in-game belt segments plus machines/inserters. It verifies conservation and records elapsed CPU time without a hardware-dependent pass threshold. Do not interpret it as a TPS certification. The required 10000/1000/500 in-game scenario remains open.

## Extension boundaries

`recipes` codecs support ordinary datapack replacement and `/reload`. `registry` owns NeoForge integration; `simulation` is independently testable; `production` adapts energy and menus; `energy` owns topology; `client` is client-only. Future fluids, research, circuits, trains, robots, pollution, combat and space require new packages and tests. No nonfunctional placeholder registrations represent these systems.
