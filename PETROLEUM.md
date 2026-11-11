# Petroleum and mixed processing — 0.25.0

Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21. Reference: [Factorio 2.1.24 resources](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/entity/resources.lua), [recipes](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/recipe.lua), [Pumpjack](https://wiki.factorio.com/Pumpjack). Only numeric mechanics were consulted; no third-party code/art is shipped. **Breaking release: no pre-0.25 FactoryCraft migration.** Old diagnostic evidence is retained, never reset.

## Production balance (all fluid columns in native mB)

| Recipe | Inputs | Products | MC ticks | FE/work tick |
|---|---|---|---:|---:|
| Basic refining | 1000 crude | 450 gas | 100 | 30 |
| Advanced refining | 1000 crude + 500 water | 550 gas + 450 light + 250 heavy | 100 | 30 |
| Heavy cracking | 400 heavy + 300 water | 300 light | 40 | 20 |
| Light cracking | 300 light + 300 water | 200 gas | 40 | 20 |
| Lubricant | 100 heavy | 100 lubricant | 20 | 20 |
| Plastic | 200 gas + coal | 2 plastic | 20 | 20 |
| Sulfur | 300 gas + 300 water | 2 sulfur | 20 | 20 |
| Acid | 1000 water + 5 sulfur + iron plate | 500 acid | 20 | 20 |
| Battery | 200 acid + iron/copper plate | 1 battery | 80 | 20 |
| Electric engine | 150 lubricant + engine + 2 circuits | 1 electric engine | 200 | 30 |
| Processor | 50 acid + 20 circuits + 2 advanced circuits | 1 processor | 200 | 40 |

Fluid quantities are encoded as actual mB in datapacks, wire codecs, inventories, statistics and circuit readings. One source unit converts to ten mB only while generating bundled JSON; imported recipes/fluids are not multiplied at runtime. Mixed recipe reservations remain all-or-nothing across every input, output and component-sensitive cell. Full products block work/FE before completion; modules use existing work/energy/productivity rules for processors.

## Percentage oil points

OilDepositBlockEntity owns an immutable persisted point UUID, initialized flag, initial/current yield, successful cycle count, fractional output residue and generated-oil counter. It has **no FluidCell or fluid capability**. The point generates no transferable oil by itself. Initial yield is configurable in hundredths of a percent; current yield uses exact 1/1500-percentage-point units.

At normal speed a pumpjack completes one cycle per 20 Minecraft ticks. Its base output is `min(10000, yieldPercent)` mB/cycle: 100% gives **100 mB/s = 5 mB/t average**, 200% gives 200 mB/s. A fixed-point remainder preserves fractional output such as 115.5%. Each successful cycle reduces yield by exactly 1/300 percentage point, stopping at `max(20%, initial/5)`. Floor points continue producing forever; there is no DEPLETED block state.

The installation target is the exposed point immediately below the **base center**, independent of controller position. Shared preview/server PlacementPlan rejects missing points; overlapping structures prevent two owners at the same point. Runtime additionally checks the canonical loaded point. Pending work stores its point UUID and cannot be reused on a replaced point.

Every work tick requires the prospective full cycle energy debt and complete upcoming output to remain available. Energy is charged **once at successful completion** (300 FE at normal speed), together with output creation and depletion before observers run. Circuit-disabled, unavailable, unpowered or blocked devices do not advance; existing partial work/debt and fractional oil persist through portable state and restart. Speed/efficiency modules and beacon speed/energy effects are supported. **Pumpjack productivity modules are rejected**; productivity extraction remains a documented scope-limited gap rather than silently ignored bonuses. The menu, direct probing and optional Jade show initial/current yield and nominal normal-speed mB/s; the progress bar reflects actual module speed.

Surface field settings live in factorycraft-worldgen.toml: spacing, point count, field radius/grid density, and initial yield range. See [RESOURCE_FIELDS.md](RESOURCE_FIELDS.md). Generation is coordinate/seed deterministic, confined to the decorating chunk, skips fluid-covered/occupied/nonreplaceable ground and structure-referenced chunks, and never loads neighbors. Existing initialized points never regenerate when decorated again.

## One item / whole structure and physical ports

One item forms the complete 5×4×5 refinery using the current shared PlacementPlan anchor modes. Controller locations and placement anchors follow the independent assembly-4 descriptors; their coordinates are not the player's targeting origin. Protection, loaded-volume validation, centered previews and snapshot rollback remain. Parts contain position/UUID only. Breaking any part or removing with a wrench returns at most one portable machine with the sole inventory/fluids/FE/progress; placement binds fresh identities. See MACHINE_DIMENSIONS.md.

Canonical NORTH ports below are relative to **layout origin**, independent of the base-center refinery controller. All local Y=0. Numeric definitions follow Factorio 2.0.77 (`base/prototypes/entity/entities.lua`, `mining-drill.lua`), pinned commit `ce6741a54c3199caad4ed147e9987fd8d5653fdf`; only coordinates were consulted, no code/art is distributed.

| Device / port | Local (X,Y,Z) | Face | Mode / owned cell |
|---|---|---|---|
| Refinery crude | (1,0,4) | S | INPUT Fluid0 |
| Refinery water | (3,0,4) | S | INPUT Fluid1 |
| Refinery heavy | (0,0,0) | N | OUTPUT Fluid4 |
| Refinery light | (2,0,0) | N | OUTPUT Fluid3 |
| Refinery gas | (4,0,0) | N | OUTPUT Fluid2 |
| Chemical input A/B | (0,0,0) / (2,0,0) | N | INPUT Fluid0 / Fluid1 |
| Chemical output A/B | (0,0,2) / (2,0,2) | S | OUTPUT Fluid2 / Fluid3 |
| Pumpjack oil | (2,0,0) | N | OUTPUT Fluid2 |

Pumpjack's original per-orientation offsets are not a simple rotated point: for EAST/WEST its canonical point becomes (0,0,0) before rotation. `odd_at` records this explicitly. Wrench reflection maps local X to width−1−X and reverses E/W faces before orientation; channels keep their identity and contents. This lets players swap left/right process connections without moving the controller or mixing cells. Boiler/engine/tank/pump/assembler 2 maps are in FLUIDS.md.

Heavy/light cracking takes oil at chemical input A and water at B; lubricant takes heavy oil at A. Sulfur takes gas at A/water at B; acid takes water at A plus item-fed sulfur/iron; batteries take acid at A plus iron/copper. Basic refining uses crude/Fluid0 and gas/Fluid2. Existing recipe/output indexing and stoichiometry are retained; new physical positions do not reassign stored products.

Each mapped input/output uses its 40000-mB buffer-derived nominal limit (120000 mB/Minecraft tick); the pumpjack has 10000 mB, additionally constrained by the adjacent segment’s fill/free pressure budget. There is no per-internal-pipe edge rate. Selected recipes validate the actual fluid input channel; automatic recipe mode retains permissive input selection. Fluid amounts are scaled once by ten; recipe durations and existing non-pumpjack FE costs remain. NeoForge fluid views exist only at the mapped physical cell/face, with strict INPUT/OUTPUT/BOTH behavior, UUID/canonical/loaded full-assembly guards and component-sensitive storage. Arbitrary casing, vertical and unsided maintenance access are removed. UI canister recovery remains a separately authorized menu operation. External pipes may use the exact sided capabilities. Rendering, pipe shapes, active transfer and menu coordinate tooltips share MachinePorts definitions.

Crude, gas, heavy/light oil, lubricant and acid have native plain buckets and original textures. Component-bearing fluids refuse lossy bucket conversion; portable machines/tanks preserve full native components and restored overflow. Lubricant feeds electric engines and express logistics; acid feeds batteries and processors in the existing progression.


## Recipe interface, diagnostics and persistence

Normal and mixed production menus open the same searchable recipe selection screen. It shows item/fluid requirements and products, category, research status and time/FE; Auto remains available. Requests identify the actual open menu and a stable ResourceLocation, with server reach/permission/loaded/research checks. Old next-index GUI actions are removed. Selected IDs and canonical recipe work definitions persist; removed/changed definitions cannot silently reuse another recipe's progress.

`/factorycraft oilworks` and `petrochemistry` build real powered lines using actual crude extraction, water, pipes, inserters and finite item/fuel inputs. Their explicitly diagnostic point starts at **2000%** to keep bounded integration windows; normal generated points use configured yields. Ledgers compare **generated crude** against remaining crude and actual chemical products, rather than adding an imaginary finite reserve. No final products or intermediate chemicals are injected.

Oil schema 2 stores Yield/UUID/cycles/residue/generated counters; machine petroleum state also stores partial pump work/cost/source UUID. Four/five native cells and physical ports still have exactly one controller owner. Parts store no resource copies. Filled pipes/tanks/pumps/machines retain sole ownership when packed. No current diagnostic checkpoint is reset or accepted with a rewritten expected hash. See VALIDATION.md for actual results and remaining CI.
