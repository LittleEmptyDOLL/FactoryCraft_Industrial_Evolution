# Fluid segments — 0.25.0

Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21. Server-authoritative, integer mB; one vanilla bucket is 1000 mB. Reference mechanics: **Factorio 2.1.24**, especially [FFF-442](https://factorio.com/blog/post/fff-442), alongside [FFF-416](https://factorio.com/blog/post/fff-416), [FFF-430](https://factorio.com/blog/post/fff-430), [Fluid system](https://wiki.factorio.com/Fluid_system), [Pump](https://wiki.factorio.com/Pump) and [2.1 history](https://wiki.factorio.com/Version_history/2.1.0). Older 1.1 mixing and 2.0 fixed-100-port descriptions are not the chosen rules. No Factorio source code or art is copied.

## Shared storage and physical boundaries

Connected compatible **pipes and tanks** share one `PipelineNetwork.Segment`. Its identity is runtime-local; its capacity is the sum of physical members, with one component-sensitive `FluidKey` and one long volume. Fluid entering one end is immediately available at every loaded valid port after the normal topology update. Turns, branching and intermediate pipes impose no internal edge throughput limit. All six Minecraft faces work, including vertical routes.

| Device | Physical checkpoint capacity | Role |
|---|---:|---|
| Pipe | 1000 mB | six-face passive segment member |
| Tank | 250000 mB | passive member; four descriptor-defined horizontal BOTH ports on its 3×3×3 structure |
| Electric pump | 4000 mB | separately owned chamber; directional back input/front output; 16000 FE buffer |

Refinery, chemical and thermal recipe buffers are 40000 mB per channel; pumpjack channels are 10000 mB. A bucket remains 1000 mB and the Minecraft canister remains 4000 mB, preserving actual external volumes. Chemical/boiling codecs accept up to 40000 mB per fluid ingredient/product; all bundled chemistry/boiling amounts have been converted consistently, including fluid assembler and express splitter recipes. Pre-0.25 FactoryCraft formats are not supported or migrated; use a new world. Old diagnostic worlds are retained as evidence.

Machines retain their existing separate recipe buffers. Only valid physical `MachinePorts` faces connect; casing forwards to the sole controller. Boiler water and steam never join a common passive pool. BOTH ports with distinct internal channels retain separate views. An unselected automatic recipe retains its existing permissive input behavior; a specifically selected petroleum/assembler recipe rejects fluids outside its actual channel ingredient. No arbitrary casing, unsided machine access or invisible connection is added.

A passive merge is refused when occupied identities differ, including NeoForge Data Components, or bidirectional filters disagree. The physical block remains, but that edge is hydraulically inactive. Each compatible side retains its contents and can work independently. The menu reports a conflict; emptying a conflicting side reevaluates the cached boundary and can reconnect it. Boiler/engine BOTH filters propagate to attached passive components. Conflicting filters on the same pipe quarantine that pipe instead of silently choosing one. Pumps and directional machine ports never merge the two passive networks. Incompatible pump output blocks without deleting chamber contents.

## Units, pressure and pump rate

`FluidUnits` centralizes the adaptation:

* **1 Factorio fluid unit = 10 mB**. FluidUnits.mb converts source definitions once; shipped recipe JSON/codecs, storage, pressure, capabilities, circuits and counters all use mB. Datapacks and foreign mods supply actual mB directly; there is no runtime automatic multiplication.
* Factorio runs at 60 ticks/s; Minecraft at 20. A buffer already measured as `B` mB has nominal boundary budget `3 × B` mB per Minecraft tick, before pressure and device constraints.
* Pump normal-quality reference 1200 units/s becomes **600 mB/Minecraft tick**. FE remains **20 once per working tick**, including when only its chamber fills. Full blocked chambers and unpowered/disabled pumps do no work and incur no charge.
* Normal capacities follow the same 1:10 rule: pipe 100 units = 1000 mB; tank 25000 units = 250000 mB; pump chamber 400 units = 4000 mB; pumpjack chamber 1000 units = 10000 mB. Machine buffers are explicitly 40000 mB per channel; a Minecraft bucket/canister stays 1000/4000 native mB.

For a passive physical boundary, the nominal rate derives from the larger of the device buffer and its adjacent valid machine channel buffer. Input quota accrues as `R × (1 − segmentFill)` and output quota as `R × segmentFill`. Ordinary machine capability boundaries likewise derive from their channel buffer. There is **no segment-wide 100 mB/tick cap**. Several physical ports have independent quotas but debit the same pool sequentially on the server thread. Source and target reservations commit before observer notifications; aliases of the same owner cannot transfer to themselves.

A passive face attached to a pump uses a pressure budget of `5 × pumpRate`; the pump itself still clamps to 600 mB/tick. This reaches full pump speed at 20% source fill / 80% destination fill, with lower flow near empty/full. Small fractional allowances accumulate in fixed-point micro-mB credit, capped at one nominal tick; offline elapsed ticks never mint catch-up flow. SIMULATE does not consume quota, FE or fluid. The deterministic server scheduling order is not an equal-share allocation guarantee under sustained starvation.

A pump outputs its previously held chamber contents before refilling; new input does not bypass an empty chamber in the same operation. Adjacent pumps must face the same direction. Direct external automatic access to pump ports also requires power and its circuit gate; several operations share one paid-tick reservation. Manual bucket/canister interaction is an explicit atomic Minecraft operation: it bypasses pressure quotas and pump FE, **not** physical side, canonical ownership, matching-fluid or structure availability checks.

The existing offshore-style water feature remains: a genuine vanilla source-water block immediately behind a powered pump is an unlimited source; flowing/waterlogged blocks and other world fluids are not extracted. Finite inventories/segments are actually debited.

## Extent, rebuilding and availability

Inclusive horizontal bounding coverage must be at most **320×320 blocks**, using actual footprints, not member count. A segment spanning 321 cells on either axis retains all fluid and readable diagnostics but rejects automated fill/drain until split or shortened. Pumps separate segments. Vertical span is reported separately and is limited only by Minecraft world bounds; no spherical radius or invented small Y limit applies.

Connectivity-changing edits freeze affected pools and queue deterministic, budgeted reconstruction. In 0.24.1, a compatible unfiltered true-leaf attach/remove and a redundant edge within one existing segment avoid that reconstruction entirely. Known multi-neighbor placement defers ambiguous grouping to preserve 0.24.0 conflict-boundary choice. During reconstruction the segment is unavailable; capabilities return no transferable fluid and circuit samples are **unknown**, not a fabricated zero. Once resolved, too-long networks remain readable with their exact mass while transfer is closed. Overflow checkpoints retain fluid and allow draining, while filling waits for real capacity.

`fluidWorkPerTick` defaults to 16384 and separately bounds reconstruction queue visits and pump jobs. No internal passive edge is processed each tick. Idle pumps sleep and wake on cached adjacent segment/FE/circuit changes. Ordinary pool mutations do not recompute connectivity or walk every pipe. They mark the cached owning chunks unsaved at most once per game tick. Topology, serialization and explicit diagnostics may walk members. The budget counts traversal, merge-sort moves, staged membership preparation and abort cleanup. Already ordered membership skips sorting. Staged raw checkpoints remain authoritative and ports stay closed until one final publication flag; only then do raw bytes become inert. Atomic freezing and short cached-chunk dirty batches remain nonpreemptible, so there is no hard wall-time deadline. No runtime path force-loads chunks or accesses the world from worker threads.

## Ownership, chunk saves and portable devices

The runtime segment is the **sole active volume owner**. Physical cells expose lazy deterministic checkpoint shares, ordered by signed packed controller position. For member capacity `c`, preceding capacity `p`, segment volume `V` and capacity `C`:

```
share = floor(V × (p + c) / C) − floor(V × p / C)
```

The sum is exactly `V`, including rounding remainders. No pipe serializes a full copy of the pool. A member's native FluidStack contains only its share and exact components. General splits/unloads materialize every affected share before retiring the pool. Local leaf packing materializes and debits only the removed share atomically; its surviving pool stays active. New components sum their own surviving shares. A frozen/unloaded member's share is unavailable to loaded components and rejoins only on valid reload. Early chunk-unload and ticket-demotion hooks handle part-only tank chunks too.

Fluid schema **2** persists a canonical `FluidOwner` UUID plus the local native FluidStack and FE/circuit/assembly data. Cached capabilities check the current owner, physical face and full loaded structure. Packing detaches the owner, emits only its share, removes the old live fluid/FE and omits the old UUID from the portable payload. Repeated exports cannot re-export the same resources. One tank/pump item forms the existing whole structure; service parts have no resource copies.

Independent chunk snapshots, orderly saves/restarts and separate-process reconstruction are supported. As with vanilla chunk persistence, there is **no crash-atomic journal across region files**: arbitrary process termination amid a partially written multi-region save is not a transactional database guarantee. Old intermediate worlds are not migrated/reset; archived diagnostics remain available in [the 0.23 reference](validation/fluid-v0230-reference.md).

## Capabilities, circuits and inspection

Every valid pipe/tank IFluidHandler reads the current common pool. `getTanks`, fluid copies, capacity, validity, fill and matching/amount drain preserve simulation semantics and component identity. NeoForge int APIs expose at most Integer.MAX_VALUE mB per call; authoritative pool totals, checkpoint arithmetic and menu totals use long. Retained stale owner references cannot debit a replacement or packed device. Pumps remain one chamber, not a pooled member.

Direct red/green/blue/white circuit contacts are unchanged. `circuitContents` reads the same segment as fluid capabilities; the old independent aggregate-index runtime is removed. Signals remain typed int32, so totals use the existing int32 signal arithmetic. Different component-sensitive segments are no longer reported as one combined fluid network. Machine/Jade inspection is readonly and shows the pool at pipes/tanks, not an independent physical share; it does not broaden transfer permissions.

## Interface and diagnostics

Right-click opens a readonly EN/RU menu: fluid, shared long amount/capacity, fill percentage, member count, runtime segment ID, status, conflict observations and horizontal/vertical extent. The button highlights a server-supplied nearby membership snapshot for ten seconds: up to 128 controllers within two chunks, with at most 2048 visited index entries. It is deliberately bounded and does not search the entire network every frame; tank markers identify controllers, not every casing cell. The snapshot is refreshed by reopening the menu.

`/factorycraft fluids` reports loaded nodes, pump count/chamber volume, quarantined physical shares, segments, active and candidate passive edges, conflict observations, overflow/too-long counts, volumes, topology backlog/visits, queued pump work, transferred mB, generated world water and cumulative runtime CPU time. Conflict observations may count both sides of a boundary; pump transfer counts intake and output, not unique production. No fluid-void/flush action is provided.

## Verification and remaining scope

Current commands/evidence are in VALIDATION.md. Pure tests cover exact pool/split shares, aliases, component-style keys, filters, extent 319/320/321, cycles, budget interruption and pressure credit. Native tests retain all thirty required finite production chains and add immediate six-face access, conflicting component bridges, portable-share demolition, UUID replacement, pump boundaries and a normal-capacity four-chunk separate-JVM ledger.

The previous `FluidTransport` kernel remains only for archived tests and the isolated [old/new benchmark](tools/FluidSegmentBenchmark.java); it is not a second runtime solver. Underground pipes, automatic discovery/ticking of arbitrary external machine nodes, temperature, viscosity, full production graphs and a transactional crash journal remain outside this release. Foreign mods can use the actual sided NeoForge capabilities. Pipe art and conveyor-lift geometry are not redesigned here.

## 0.24.1 performance paths

`contentChanged` observes an already committed owner mutation; ordinary fill/drain never invalidates connectivity. Identity transitions revisit cached conflict boundaries only when necessary; filters and availability have their own structural entrypoints. The legacy `externalChanged` entrypoint forwards to content notification rather than unconditional topology invalidation. Unfinished builds are cancelled only when touched vertices/boundaries change, not by unrelated construction elsewhere.

A stable indexed heap replaces per-invalidation pending-tree entries; sorted small neighbor arrays replace per-node edge trees, and build stamps replace allocated visited maps. Signed packed-position ordering and exact prefix shares are unchanged. Freeze computes each successive prefix boundary once, carrying the exact remainder implicitly; overflow uses integer quotient/remainder decomposition and BigInteger only when necessary. No approximate division is introduced.

Local leaf edits preserve one pool and update its sorted prefix representation atomically without extracting all member fluids. They still cost O(N) in the worst case for prefix/extent updates. Filters, conflicts, overflow, extent violations and genuine bridges/splits use the general guarded path. Removed members receive exactly their prior share. Chunk memberships are reference-counted, so a removed final member does not leave a stale chunk in a live segment cache.

Rebuild preparation fills chunk/pump caches within the same work budget. A changed pump boundary cancels affected unpublished metadata preparation without freezing a ready pool. Freeze/publish no longer fan out BE updates to every member. Cached chunks are persisted; already-unsaved chunks need no repeated setUnsaved(true). GUI data slots and readonly inspection remain server-derived; circuits report unknown throughout incomplete rebuilding. Pump wakeups are queued once per changed segment; static machine/nozzle models need no bulk content packet. No client production state is inferred from stale raw checkpoints.

The measured benefits concern topology, allocation and update requests. Stable server means/maxima and total split/merge completion do **not** uniformly improve; budgeted reconstruction can take more ticks. See [BENCHMARKS.md](BENCHMARKS.md), [raw comparison](validation/fluid-v0241/comparison.md) and VALIDATION.md. No rates, capacities, recipes, FE, components, physical ports or NBT schema were changed.
