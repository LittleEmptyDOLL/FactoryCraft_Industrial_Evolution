# Modules and beacons — 0.11.0

Target: Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21. This is the first working module tier. Quality, higher module tiers, drill/lab/thermal modules and coverage overlays remain future work.

| Module | Work speed | FE per active tick | Eligible productivity |
|---|---:|---:|---:|
| Speed | +20% | +50% | 0 |
| Efficiency | unchanged | −30% | 0 |
| Productivity | −5% | +40% | +4% |

Local effects add before limits: speed 20–500%, energy 20–1000%, productivity 0–100%. Work uses integer basis points and persists its fractional remainder; positive FE costs round **up per active tick**. Very short recipes complete at most once per tick; excess work at completion is clipped. Ordinary assembler/furnace/industrial furnace/assembler 2 have two slots; refinery/chemical plant have three. These are the current Minecraft balance choices. Generator, drill, pumpjack, laboratory, boiler/engine and fluid devices do not accept these slots yet.

Open a production machine and press **Modules**. Slots accept one module each. They are a separate controller-owned inventory; ordinary processing ports never expose copies. Module-menu clicks/shift moves validate the current menu, spectator state, range and complete loaded canonical assembly. One-item demolition/portable placement retains modules and work in the same controller item.

Productivity applies only to all-item outputs admitted by the datapack item tag **`factorycraft:productivity_outputs`**. Defaults include plates/components/science/plastic/sulfur/batteries; modules, machines and fluid outputs are excluded. Mixed input-fluid recipes producing eligible items can benefit. Credit accrues in proportion to actual completed work with that module installed, not merely the last tick of a cycle. A late insertion cannot claim the whole previous cycle. One module earns exactly one extra batch per 25 full cycles. Credit is keyed to the canonical recipe definition; starting another definition resets it. Sub-unit productive work is also persisted. The whole base/bonus output is reserved before FE; inputs, all results and credit commit before inventory/fluid observers. A blocked bonus pauses without spending resources. Bonus outputs are intentional productivity, not an unconditional item copy.

## Beacon

One `factorycraft:beacon` item forms a centered **3×3×3** structure with original antenna geometry. It has two speed/efficiency slots and a 16000-FE buffer connected through poles or standard FE capability. Productivity modules are rejected. An empty beacon sleeps; a powered installed beacon pays **40 FE/t**. This FE ratio is a Minecraft balance choice.

Coverage extends three blocks beyond the horizontal beacon footprint and ±3 in controller height; any overlap with the target machine footprint qualifies. Only powered, complete, block-ticking beacons with valid modules count. For normal quality, transmission follows the **Factorio 2.0 `1.5 / sqrt(n)` distribution rule**: sum the contributing beacon-module effects, then multiply by this factor. A single speed module in one beacon gives +30% speed/+75% consumption; four such beacons give +60%/+150% in total. Local modules then add and limits apply. The beacon menu displays the installed module base rates; recipient machines display the final combined rates. No quality scaling is implemented.

A server level pre-tick pays the current work lease before production, avoiding BE iteration-order dependence. No billing occurs while ticks are frozen or a controller chunk is merely loaded outside block-ticking range. Missing/unloaded parts and exhausted power remove effects. A per-level spatial bucket index caches candidate beacons per production machine; placement/removal invalidates nearby buckets only. Module changes recompute local totals; the hot path reuses unchanged effect records. No chunk is forced and no whole-factory recipient scan runs each tick.

## Progression, example and persistence

Three new technologies extend the existing twelve: Modules (red/green, Automation 2), Productivity (red/green/blue, Modules + Advanced electronics 2), Effect transmission (same prerequisites and packs). Production of all three module items and the beacon is research-gated on owned machines and appears in EMI/JEI. Existing legacy/admin ownership behavior is unchanged.

`/factorycraft moduleworks` builds a finite powered comparison in a free loaded 30×8×13 area. Fifty plates become **26 gears** with productivity; twenty plates become **10 gears** beside a speed beacon and local efficiency module. Chests, belts, inserters and a coal generator perform the transfers/power supply. The example supplies finite modules/components and grants no research.

Machine `Modules` schema 1 stores sole module items, canonical working recipe, fractional work, productivity credit and productive-work remainder. Empty/default module state writes no NBT field, preserving the old full-factory checkpoint. Beacon assembly schema 3 stores one inventory and FE; the paid lease is transient and is never restored as free power. This is ordinary server persistence, not a cross-file crash journal.

A new **unreleased** v1 diagnostic probe exposed premature billing in merely loaded chunks: its beacon dropped from 6312 to 32 FE. That probe remains untouched at (751,70,751)/(759,70,751), with a read-only evidence check; no energy was refilled and no old release checkpoint was reset. The corrected, strictly checked v2 checkpoint uses (815,70,815)/(823,70,815); a separate direct inactive-chunk regression ensures the exact 6312 FE survive 200 billing attempts. See VALIDATION.md for final process-reload and load-test results.
