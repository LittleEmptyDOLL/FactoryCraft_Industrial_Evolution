# Fluid logistics — 0.3.0

Target: Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21. All quantities use integer millibuckets (mB); one vanilla bucket is 1000 mB. Simulation runs on the server at 20 ticks/second.

## Usable blocks

| Block | Capacity | Connections / behavior |
|---|---:|---|
| Fluid pipe | 1000 mB | six adjacent faces; passive finite flow |
| Fluid tank | 16000 mB | six adjacent faces; passive finite flow |
| Electric pump | 1000 mB | horizontal back input/front output; no side fluid ports; 16000 FE buffer |

The arrow on the pump points toward its output; right-click with the factory wrench rotates it. Normal pole coverage powers pumps; guarded external NeoForge energy input also works. Passive pipes/tanks need no power. A pump spends **20 FE once per working tick**, whether one or both ports move, with a **100 mB/tick per-port upper bound** (2000 mB/s at 20 TPS). It stops without energy, supply or available capacity. Filling a free chamber can still consume energy while downstream is blocked; a full blocked chamber consumes none.

The pump accepts a genuine vanilla source-water block immediately behind it as unlimited offshore-style input; it does not remove that world source. Flowing/waterlogged blocks and other world fluids are not extracted. Finite containers are actually drained. Passive cells can hold any registered FluidStack, including vanilla lava and component-bearing fluids. Empty cells accept one identity; incompatible fluids or components never mix. Pump intake/output use the chamber present before that tick's port planning, so newly collected water cannot pass straight through an empty pump in the same tick.

Right-click with a bucket for standard NeoForge container interaction; right-click otherwise for a readonly gauge (and pump FE). Standard sided fluid capabilities allow other mods to push/pull; pipe/pump automatic neighbor discovery currently connects only FactoryCraft fluid nodes. Unsided pump access exposes maintenance fill/drain; sided access enforces back/front roles even on a previously cached handler after rotation.

Breaking a filled block returns one block item retaining the canonical native FluidStack (including components) and stored FE. Empty/unpowered block items stack normally. Place the returned item to restore contents. Standard creative breaking produces no block item. This slice uses single-block devices; future large fluid machines follow the one-item automatic-multiblock rule.

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

New block entities use fluid schema 1 and the native FluidStack codec. Existing production/multiblock/belt schemas are unchanged. Fluid snapshot changes are staggered per position to at most one BE packet per five ticks; opened menus use vanilla data slots and split 32-bit quantities safely. No measured multiplayer delta compression is claimed.

`/factorycraft fluids` reports loaded nodes, passive components/edges, pending edges, queued pumps, cumulative edge visits/transferred mB, pump chamber transfers and CPU time. Pump chamber transfers count both input and output movement, not unique water creation or end-to-end throughput.

`/factorycraft waterworks` requires an empty loaded 9×4×5 area beside the player and builds a real fueled generator → pole → pump → four pipes → tank chain with an enclosed source-water block. It does not fill the destination tank directly.

![Tank gauge in the actual Russian client](docs/screenshots/fluid-tank-0.3.0.png)

## Evidence and next work

JUnit covers storage simulation/atomicity/overflow, component-style identities, sleeping/wakeup, topology/splits, budget fairness, blocked/unavailable endpoints, callback ordering/failure, six-way stability and a randomized 10000-cell conservation graph. Dedicated GameTests exercise real NeoForge ports, buckets and harvested-item placement, powered world-water supply, vertical flow, live NBT reload, early chunk unload and exact 8000 mB persistence across four chunks in separate JVMs. See VALIDATION.md for results and limits. The pure 10000-cell exercise is not an in-world fluid TPS benchmark.

Next: water-to-steam boiler and steam engine, transactional item/fluid recipe support, then oil deposits/refining/cracking/chemistry. Underground pipes, external-node discovery, pump circuit controls, fluid rendering within pipe models, temperature and production graphs remain unimplemented. Two real clients and natural player-distance unload still need separate validation.
