## 0.29 solar and tertiary storage

Reference: Wube 2.1.24 with Space Age, normal quality. Solar Panel occupies 3×3×1 and produces at most 60 kW (8 FE/t). Accumulator occupies 2×2×2, holds exactly 5 MJ, accepts/exports at most 300 kW (40 FE/t). Both use the same one-item assembly, physical-footprint pole coverage and explicit copper edges as existing machines.

The exact internal quantum is **25 J**: one FE = 375 J = 15 quanta. Fractional solar/charge/thermal conversions remain owned and saved; integer external FE never rounds a fractional claim upward. Battery charge, tick quota and direction lease survive packing/reload. All capability and grid transactions share the lease; a battery cannot charge and discharge in one tick. A battery intersecting disconnected pole components belongs to only one current component, preventing an overlap short circuit.

The cached graph maintains event-updated hungry consumers. Dispatch uses solar first, steam engines/turbines second, accumulators third; only unused primary production charges storage. A bounded incomplete demand pass conservatively defers storage. Generator buffers remain real controller-owned FE, not balances copied into poles. Levels containing only legacy engines retain their established dispatch path; with solar/storage/turbines loaded, existing engines join the priority dispatcher and share their export quota with external FE access.

Solar adaptation: Factorio's 2:5:2 dawn/full/dusk proportions are fitted into Minecraft sun-up ticks 0–12000; plateau around noon, zero at night. This deliberately does not preserve Factorio's 70% full-day average on a Minecraft half-day. A single center-above-footprint sky probe refreshes every 20 ticks. Other corners are not individually shaded. No weather multiplier, ceiling/fixed-time/no-skylight dimension generation, unloaded access or time catch-up. GUI reports actual last-tick generation, not hypothetical stored solar energy.

## 0.26.1 Small Pole height

Small Pole now occupies 1×1×3, original post/crossbeam/terminal geometry and copper attachment Y=2.88. One item installs all three cells; occupied third cell/build-limit rejects placement, any-part removal removes all cells. Supply remains 5×5 and wire7.5; Medium/Big/Substation and topology/energy recipes are unchanged.

# Electricity — 0.20.0

One Factorio tile is one horizontal Minecraft block. These are **normal-quality Factorio 2.0** parameters; height and vertical reach are Minecraft adaptations.

| Pole / block ID | Foundation | Height | Supply square | Maximum copper wire |
|---|---|---|---|---|
| Small / `electric_pole` | 1×1 | 2 | 5×5 | 7.5 |
| Medium / `medium_electric_pole` | 1×1 | 3 | 7×7 | 9 |
| Big / `big_electric_pole` | 2×2 | 5 | 4×4 | 32 |
| Substation / `substation` | 2×2 | 4 | 18×18 | 18 |

Reference: [Electric system](https://wiki.factorio.com/Electric_system), [small](https://wiki.factorio.com/Small_electric_pole), [medium](https://wiki.factorio.com/Medium_electric_pole), [big](https://wiki.factorio.com/Big_electric_pole), [substation](https://wiki.factorio.com/Substation), and [Factorio 2.0.77 prototypes, ce6741a](https://github.com/wube/factorio-data/tree/ce6741a54c3199caad4ed147e9987fd8d5653fdf/base/prototypes). Only numeric/gameplay definitions inform this implementation; all models/textures are FactoryCraft originals.

## Placement and ownership

One item installs the whole structure using the existing schema-4 PlacementPlan/MachineItem transaction. The chosen cell is local controller `(0,0,0)`, including 2×2 poles; rotation applies to the entire foundation and attachment. Loaded volume, world border/height, permissions, entities and NeoForge multi-place cancellation are checked. Upper parts contain only controller identity/local visual metadata, never FE or independent graph nodes. Breaking any part or Shift-wrench dismantles the whole device, at most one item. Portable poles lose physical wires/old UUID; reinstallation creates a new owner and runs ordinary placement wiring.

`pole_geometry.json` is the single source of original boxes, clipped per-cell physical shapes and wire attachment points. Full bounding volume remains reserved even around open framework. An unloaded/damaged part makes the whole pole electrically unavailable. Normal simulation never force-loads chunks.

## Copper edges and tools

Only reciprocal, identity-bound copper edges join pole networks. Covering the same ground does not join poles. The distance is Euclidean between **foundation centers in XYZ**, limited by the **smaller** range of the two poles. Different head heights do not change reach; this is an explicit Minecraft adaptation. There is no five-connection cap, resistance or per-wire FE buffer.

At initial placement, candidates are taken from the loaded-pole chunk index and ordered by distance/position. Nearby candidates already joined by a shorter reciprocal connection suppress redundant triangles. This nearest-first rule is an approximation to Factorio's placement convenience, not a claim to duplicate its closed engine algorithm. It cannot join unloaded/damaged structures. The resulting edges are ordinary saved wires, not a continuing proximity rule.

Use **Copper Power Wire Tool** (`copper_wire_tool`, reusable, distinct from production `copper_wire`):

1. Right-click any physical part of the first pole.
2. Walk to and right-click the second pole. Both clicks must be in normal player interaction reach at their time; both identities, dimensions, loaded complete structures, permissions and mutual wire reach are checked server-side.
3. An existing wire is removed; an absent eligible wire is created. Clicking the first again cancels selection.
4. Shift-right-click clears all connections of the selected pole. The wrench retains its existing functions.

Selection is transient server state, not a stored connection. Wire edits synchronize through ordinary controller packets. Manually removed wires **do not return** on chunk/world reload. Connections to unloaded ends remain stored but do not conduct. A reverse reference index reconciles tags arriving in either order; definite destruction, replacement UUIDs and one-sided stale edges are retired. Loading a chunk does not trigger all-world discovery.

## Supply coverage and FE

Coverage is a square around the **true foundation center**, with exact half-size (including even-size centers/areas). A device is covered if any positive area of its actual rotated horizontal foundation overlaps that square. Touching only the mathematical outer boundary is not overlap. A 5×5 refinery can receive FE when only a corner cell overlaps; its controller/center need not be inside. Poles are not connected by this test.

`powerVerticalReach` (SERVER config, default **4**, range 0–64) limits the absolute difference of device and pole **base Y**. Head height never shrinks the horizontal square into a sphere. Old `poleRadius` is removed; it determines neither coverage nor wire reach. Changing the vertical setting invalidates old cached coverage.

All existing GridConsumer/GridSource adapters retain their FE behavior: steam generation (Coal Generator removed in 0.26.0), drills, furnaces, assemblers, petroleum/chemical production, labs, electric inserters/pumps and beacons. The distribution algorithm is unchanged; 0.26.0 corrects per-kind loads and source ratios as documented in STEAM_POWER.md and the parity audit. Distribution reuses bounded snapshots, shared recipient lists and round-robin acceptance (up to 32 candidates per preparation). A shared covered device may receive from multiple networks without merging their poles or receiving duplicate FE from a single delivery. Cut/unavailable components are quarantined immediately, independent components keep operating, and rebuild work remains bounded by `powerGraphWorkPerTick`.

## Visuals and statistics

Original copper-colored sagging curves attach to actual insulators (1.88/2.88/4.88/3.88 blocks above base), with 16 cached line segments per edge. The lower-position controller renders a reciprocal edge once. No wire Entity, global per-frame neighbor search or client energy simulation is used. Both complete endpoints must be loaded. Red/green/blue/white circuit wires retain their existing separate behavior.

Holding a pole shows its PlacementPlan plus exact blue supply square and green proposed connections, including a base-plane projection for downward views. Hovering an installed pole shows its current square. Actual wires are copper-colored. MISS, air and out-of-reach targets show no preview. Candidate previews use a local cache; no terrain/supply-area scan occurs each frame.

Right-click a pole with an empty hand for type, supply size, wire range, active/saved connection counts, and actual poles/generators/consumers in its current component. During quarantine it reports no current component. Generation/demand rates are not displayed because the present kernel does not maintain those rates. `/factorycraft power` reports nodes, explicit copper edges, components, rebuild budget/work and delivered FE. Admin `/factorycraft powerworks` builds a small loaded protected showcase; its inspection stocks are diagnostic and do not claim a raw-material ledger.

## Recipes and progression

Factory assembler recipes (one product except Small gives two):

| Pole | FactoryCraft ingredients | Gate |
|---|---|---|
| Small ×2 | 1 plank + 2 copper wire | starter |
| Medium | 2 steel + 2 copper wire + 2 iron plates | electric energy distribution 1 |
| Big | 5 steel + 4 copper wire + 4 iron plates | electric energy distribution 1 |
| Substation | 10 steel + 6 copper wire + 5 advanced circuits | electric energy distribution 2 |

These follow original quantities, with **4/8 iron sticks replaced by 2/4 iron plates**, their original material equivalent; no new stick component is introduced. Planks stand for wood. FactoryCraft already requires an assembler to make copper wire, so a temporary **handcraft 1 plank + 1 vanilla copper ingot → 2 Small poles** preserves the existing bootstrap route. It is not the literal Factorio small-pole recipe. Wire-tool crafting (1 copper ingot + 1 iron ingot) is also a local reusable-tool adaptation.

Distribution 1 requires steel processing + automation (the existing green-science unlock), costs 120 red/green units, 600 Minecraft ticks per unit (30 s). Distribution 2 requires distribution 1 + chemical science, costs 100 red/green/blue units, 900 ticks per unit (45 s). These match original research counts/times; prerequisites are mapped to existing FactoryCraft technology IDs. Recipe work uses the existing FactoryCraft FE/time model rather than introducing Factorio joules. Quality scaling and later power equipment are outside this release.

## Persistence and inspection

`ElectricPole` schema 1 stores pending first-placement wiring and reciprocal `{Pos, Identity}` endpoints in the sole controller; block ID stores pole type. Assembly schema remains 4. Old one-block/proximity pole worlds are not migrated and may not work with the new structure. No worlds are deleted or reset; new diagnostics use `run-gametest/v0200` and `run-benchmark/*-v0200`.

Machine names now resolve identically through controllers and casings. Optional Jade **15.10.6+neoforge** reads a guarded readonly owner snapshot for items (both input/output), FE, native fluids and available production progress. Standard Jade discovers outward unsided automation views, which intentionally do not expose all internal fluid cells; widening them would bypass physical ports. The plugin replaces stock storage rows to avoid duplicates and changes no capabilities. It stores no content in parts and loads no chunks. Build normally without Jade; use `-PwithJade=true` for the pinned optional development runtime. See [VALIDATION.md](VALIDATION.md) for actual present/absent client/server checks and remaining CI work.

0.21.0 adds Standard, Bulk and Stack to the existing electric-arm consumers; Long/Fast retain the same grid adapter. Burner is fuel-only and has no FE capability. No pole, explicit-wire, coverage or production-machine balance changes accompany the inserter overhaul. Local arm cycle FE is documented in INSERTERS.md; native tests exercise all four supply types and cut/resume with an owned hand.

## Logic relay contacts — 0.22.0

All four existing pole controllers also accept red/green/blue/white direct circuit contacts. Their signal graphs and masks are independent from copper power edges: adding/removing logic wires cannot connect FE networks or change coverage. Pole heads provide separate visible signal studs; upper parts redirect to the single canonical owner. See CIRCUITS.md.
