# Fluid logistics — 0.19.0

0.17.0 adds nonmutating fluid circuit measurements through physical sided capabilities and threshold-controlled pumps. Fluid rates, capacities, costs and solver remain unchanged. See CIRCUITS.md for exact samples, unavailable readings and polling/overshoot limits.

Target: Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21. All quantities use integer millibuckets (mB); one vanilla bucket is 1000 mB. Simulation runs on the server at 20 ticks/second.

## Usable blocks

| Block | Capacity | Connections / behavior |
|---|---:|---|
| Fluid pipe | 1000 mB | six adjacent faces; passive finite flow |
| Fluid tank | 16000 mB | four horizontal BOTH faces in two opposite corner cells; passive finite flow |
| Electric pump | 1000 mB | horizontal back input/front output; no side fluid ports; 16000 FE buffer |

The arrow on the pump points toward its output; ordinary wrench mirrors physical ports; Shift-wrench returns a portable pump. Normal pole coverage powers pumps; guarded external NeoForge energy input also works. Passive pipes/tanks need no power. A pump spends **20 FE once per working tick**, whether one or both ports move, with a **100 mB/tick per-port upper bound** (2000 mB/s at 20 TPS). It stops without energy, supply or available capacity. Filling a free chamber can still consume energy while downstream is blocked; a full blocked chamber consumes none.

The pump accepts a genuine vanilla source-water block immediately behind it as unlimited offshore-style input; it does not remove that world source. Flowing/waterlogged blocks and other world fluids are not extracted. Finite containers are actually drained. Passive cells can hold any registered FluidStack, including vanilla lava and component-bearing fluids. Empty cells accept one identity; incompatible fluids or components never mix. Pump intake/output use the chamber present before that tick's port planning, so newly collected water cannot pass straight through an empty pump in the same tick.

Right-click with a bucket for standard NeoForge container interaction; right-click otherwise for a readonly gauge (and pump FE). Standard sided fluid capabilities allow other mods to push/pull; pipe/pump automatic neighbor discovery currently connects only FactoryCraft fluid nodes. Profiled machines expose no unsided fluid capability. Only listed physical cell/face pairs work; all cached views recheck current mapping and complete loaded ownership. Plain pipes retain six-face/unsided access.

Breaking a filled block returns one block item retaining the canonical native FluidStack (including components) and stored FE. Freshly crafted items stack normally; harvested structures retain controller NBT. Place the returned item to restore contents. Standard creative breaking produces no block item. Tanks are now 3×3×3 and pumps occupy 1×2, automatically formed from one item. Pipes remain one block. Only descriptor-defined tank/pump nozzles forward to one controller; see MACHINE_DIMENSIONS.md.

## Chosen transport model and Factorio 2.0 comparison

Factorio 2.0 groups connected pipes/storage into fluid segments with shared fluid levels and uses pumps between segments. FactoryCraft currently implements a **finite local-pressure approximation** with per-block buffers and explicit edge throughput. Distance and junction degree therefore affect equalization time, unlike a shared uniform-level segment. It is not exact Factorio 2.0 fluid parity. Reference: <https://wiki.factorio.com/Fluid_system> (2.0 model). No Factorio code or assets are used.

Adjacent passive cells form a cached graph; pump chambers separate passive components. For one edge with pre-phase amounts Va/Vb, capacities Ca/Cb and passive degrees da/db, request:

```
q = min(100, floor(abs(Va*Cb - Vb*Ca) / ((Ca+Cb)*max(1,da,db))))
```

Direction is from higher to lower fill fraction. Then clamp against reserved source volume and destination free space. Degree damping prevents the six-way-junction oscillation of simultaneous unrestricted pair equalization. Integer rounding can leave a small residual difference; exact uniformity and a fixed end-to-end throughput are not promised. Gravity, pressure damage, temperature and viscosity are not simulated. Pump pressure can fill a tank toward capacity rather than merely equalize the upstream fraction.

Each batch first reserves all transfers, then drains all sources, fills all targets, and only then invokes observers. A newly received parcel cannot relay in the same graph tick; observer failure cannot replay a committed plan. Fill/drain simulation does not mutate state. Restoring volume above nominal capacity preserves it as drainable overflow instead of deleting it; subsequent filling is blocked until space is available.

## Scheduling, persistence and diagnostics

Graph nodes/edges update on actual topology changes. Empty, balanced and incompatible edges sleep until content/topology changes; pumps sleep when blocked/unpowered/empty and wake on relevant fluid, energy or neighbor changes. `fluidWorkPerTick` defaults to 16384 (range 64–1048576), limiting passive edge visits and pump jobs separately per level tick. FIFO queues retain backlog; limiting work slows propagation, never discards volume. Cached component counts are computed on a diagnostic request after topology revision, rather than scanning the factory each tick.

Runtime never force-loads neighbors. Canonical loaded chunk/BE ownership is checked before access. An unavailable endpoint freezes transfers; early ChunkEvent.Unload detaches before chunk serialization, and every authoritative change marks its owned chunk unsaved immediately. This specifically covers the save-order race found by a four-chunk disk fixture. Unloaded fluid nodes do not advance offline.

New block entities use fluid schema 1 and the native FluidStack codec. Production/fluid/belt payloads stay native; assembly/part metadata is schema 3 in 0.6.0. Fluid snapshot changes are staggered per position to at most one BE packet per five ticks; opened menus use vanilla data slots and split 32-bit quantities safely. No measured multiplayer delta compression is claimed.

`/factorycraft fluids` reports loaded nodes, passive components/edges, pending edges, queued pumps, cumulative edge visits/transferred mB, pump chamber transfers and CPU time. Pump chamber transfers count both input and output movement, not unique water creation or end-to-end throughput.

`/factorycraft waterworks` requires an empty loaded 30×8×13 area beside the player and builds a real fueled generator → pole → pump → four pipes → tank chain with an enclosed source-water block. It does not fill the destination tank directly.

![Tank gauge in the actual Russian client](docs/screenshots/fluid-tank-0.3.0.png)

## Historical evidence and next work (current results in VALIDATION.md)

JUnit covers storage simulation/atomicity/overflow, component-style identities, sleeping/wakeup, topology/splits, budget fairness, blocked/unavailable endpoints, callback ordering/failure, six-way stability and a randomized 10000-cell conservation graph. Dedicated GameTests exercise real NeoForge ports, buckets and harvested-item placement, powered world-water supply, vertical flow, live NBT reload, early chunk unload and exact 8000 mB persistence across four chunks in separate JVMs. See VALIDATION.md for results and limits. The pure 10000-cell exercise is not an in-world fluid TPS benchmark.

Water-to-steam boilers and steam engines are delivered in 0.4.0: see STEAM_POWER.md. Thermal devices actively pull/push through their oriented FactoryCraft connections; they do not join passive pressure components. Mixed item/fluid recipes, finite oil, basic refining, plastic and sulfur are delivered in 0.5.0; see PETROLEUM.md. Advanced refining/cracking, lubricant, acid and batteries are delivered in 0.8.0; team research is next. Underground pipes, external-node discovery, pump circuit controls, fluid rendering within pipe models, temperature and production graphs remain unimplemented. Two real clients and natural player-distance unload still need separate validation.

### Native buckets and data components (0.4.0)

NeoForge's standard bucket wrapper cannot retain component-bearing FluidStacks. FactoryCraft empty-bucket block interaction refuses that lossy operation and leaves volume/components intact with a translated explanation. Plain water/lava/steam remain bucket-compatible. Carry the filled block or use a component-aware fluid container/capability for customized fluids. Steam also refuses this conversion through its FluidType bucket factory.

## 0.8.0 petroleum extension

Heavy oil, light oil, lubricant and sulfuric acid use the same component-sensitive finite cells, native guarded capabilities and plain buckets. No pressure/capacity/throughput solver change is made. The refinery adds only a fifth 4000-mB cell/third active 100-mB/t nozzle at its front-left controller face; legacy cells/ports do not move. Chemical plants keep two fluid inputs/two outputs, while refinery recipes reserve three results together. See PETROLEUM.md for exact recipes, coordinates, schema migration and full-line ledger. Neither this extension nor the automated example certifies a large petrochemical TPS target.

## 0.10.0 — exact-volume canister and input recovery

The reusable `factorycraft:fluid_canister` holds **4000 mB**, has a standard NeoForge item fluid capability and stores native `SimpleFluidContent` in the persistent/network-synchronized `factorycraft:canister_fluid` data component. Fluid components and any sub-bucket volume survive item serialization and networking. The craft uses seven iron plates, one pipe and one glass. Right-click physical fluid ports/tanks to transfer through the existing native FluidUtil interaction; directional machine inputs remain fill-only for ordinary external capabilities.

To remove leftover fluid from either input of a refinery, chemical plant or assembling machine 2, carry a canister **on the mouse cursor** and press the small ↑ button beside that input row. The server validates current menu, range, spectator state, canonical loaded controller and complete assembly. It fills a private canister copy, commits the source remainder and cursor owner before fluid observers, and then lets ordinary menu synchronization send the new item. A full/mismatched canister changes nothing. Overflow restored in a machine is drained only up to available canister space; e.g. 5001 mB becomes 4000 + 1001, never truncated or rounded to buckets. Partial recipe work stays paused until adequate input returns; recovery neither refunds FE nor grants a result. This is a synchronous game-state transaction, not a cross-file crash write-ahead journal.

The cursor remains vanilla container ownership: closing/disconnecting returns it according to Minecraft's menu rules. Recovery does not discard fluid, open input drains to pipe networks, change the finite-pressure solver, or create fluid entities. Pumpjacks have no recovery buttons because their only active reservoir is an output. Canisters work with normal output/tank interactions as well.

## Physical ports — schema 4

The shared descriptor is `src/main/resources/factorycraft/machine_layouts.json`, derived from numeric `fluid_boxes/pipe_connections` in Factorio **2.0.77**, commit `ce6741a54c3199caad4ed147e9987fd8d5653fdf`. Canonical NORTH coordinates use layout origin, not controller position; rotate every point/face together. All ports are at Y=0. Minecraft rounds the boiler/pump half-tile rows into their two-block depth.

| Machine | Local cells and faces | Channels / mode |
|---|---|---|
| Boiler | (0,0,1) W; (2,0,1) E; (1,0,0) N | water BOTH 0/0 on both sides; steam OUTPUT 1 |
| Steam engine | (1,0,0) N; (1,0,4) S | steam BOTH 1/1 at both ends; rejects water |
| Tank | (0,0,0) N/W; (2,0,2) S/E | four BOTH 0/0 connections to the sole tank |
| Pump | (0,0,1) S; (0,0,0) N | INPUT 0; OUTPUT 0 |
| Assembler 2 | (1,0,0) N; (1,0,2) S | INPUT 0; OUTPUT 2 |

Refinery/chemical/pumpjack maps are in PETROLEUM.md. One physical BOTH view may expose two different internal channels; fill reaches only input, drain only output. Components and incompatible fluids never mix. Real pipe shapes look up the actual physical part and face, and the existing finite-pressure graph links remote owner cells through those parts. External conforming NeoForge handlers see the same mapping. Automatic neighbor transfers still target FactoryCraft endpoints; third-party pipes must push/pull through the standard capability.

Ordinary wrench reflects port positions and east/west faces across local X; repeated use restores the canonical map. Stored fluids do not move between channels. No FE is charged. The physical/visual mapping and portable setting persist. Ports are static for the selected physical configuration; assembler 2 retains existing auto-recipe behavior and does not yet hide unused ports as Factorio's `off_when_no_fluid_recipe` does. The existing pipe graph solver, rate/capacity and production balance remain. Same-cell BOTH adapters exchange at most 100 mB/t according to positive fill-fraction difference: `q = clamp((Va*Cb−Vb*Ca)/(Ca+Cb),0,100)`. This avoids returning a freshly pulled batch through the same port before a boiler can accumulate its recipe. Distinct input/output channels retain independent directional transfers. A native pipe→boiler→pipe→engine fixture checks useful production and an exact water/steam/FE ledger.

Previous version-labelled evidence/maintenance descriptions above are historical; this section supersedes old all-face/unsided access and old nozzle coordinates. Thirty obsolete coordinate/port diagnostics remain archived source rather than passed tests; see validation/assembly-v4-archive.md.
