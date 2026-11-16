# Production progression — 0.27.0

Startup remains playable: finite mining and solid-fuel stone furnaces produce plates. Actual 50 iron plates unlock steam power; 10 copper plates unlock electronics. Build a laboratory from its original **10 circuits +10 gears +4 belts** (no assembler prerequisite), then automation science becomes available. Automation costs 10 red units ×10 seconds and unlocks Assembler 1 /long-handed inserter. Logistic Science separately costs 75 red units ×5 seconds. The existing electric drill now has its original 25-red-unit ×10-second technology. The first lab and Assembler 1 use counted native handcrafts so a component stack supplies the original quantity instead of a simplified 3×3 shape. Put one stack of each distinct component in the crafting grid; insufficient counts produce no result.

Steel, engine/fluid handling/oil gathering, first successful oil extraction, oil processing/plastics/sulfur, advanced circuits, chemistry and processing units follow the pinned Space Age-enabled 2.1.24 technology costs. Only module family tier 1 is reachable pre-space. Tier 2 requires real space science; tier 3 adds planet materials and research. Automation 3 uses the Space Age 500-unit cost and true purple dependency; beacons, research-speed 5–6 and later bonuses keep their genuine gates; they are visible and locked rather than made available through substitute science. All twelve science identities/physical slots are ready; beyond red/green/blue they are infrastructure/creative only. Future primary space science comes from orbital platforms. An approved additional Satellite route yields 1000 packs per launch using Space Age rocket costs; neither source is implemented in 0.27.0.

Lab research speed is multiplicative with module speed; productivity modules save science by granting scientific bonus units. See RESEARCH.md, MODULES.md and docs/RESEARCH_REFERENCE_0270.md for exact source mappings, costs and current bounds. Vanilla handcraft result/pickup guards share team production locks; creative/admin fixtures are explicitly separate from survival progression. Manufacturing recipes outside this task retain prior FactoryCraft adaptations unless specifically corrected here.

## Retained production behavior / earlier milestones

## 0.26.1 drill automation

Both drills now auto-unload through the front chute to belts, compatible inventories/furnaces and valid burner fuel slots. An inserter is optional for mining output; the cold-bootstrap fixture uses drill → two belts → furnace-loading arm. Existing recipe/research costs, mining/assembly speeds and FE/fuel rates are unchanged. See RESOURCE_FIELDS.md for the bounded-buffer/no-ground-entity adaptation.

# Production progression — 0.26.0

The implemented game remains a three-science subset of Factorio 2.1.24. The complete current recipe and research inventory, source references and remaining deviations are in [docs/FACTORIO_PARITY_AUDIT.md](docs/FACTORIO_PARITY_AUDIT.md). Old FactoryCraft formats are unsupported; no old diagnostic world is migrated/refilled.

| Machine | Base work speed | Active FE/t | Physical module slots | Item inputs / fluid support |
|---|---|---|---|---|
| Assembling Machine 1 | .5 | 10 | 0 | 16 / dry only |
| Assembling Machine 2 | .75 | 20 | 2 | 16 / fluids |
| Assembling Machine 3 | 1.25 | 50 | 4 | 16 / fluids |
| Electric Drill | .5 basic ore/s | 12 | 3 | finite 5×5 area, depth up to 8 |
| Burner Mining Drill | .25 basic ore/s | native solid heat | 0 | finite 2×2 area, depth up to 8 |
| Oil Refinery | 1 | 56 | 3 | existing five fluid channels |
| Chemical Plant | 1 | 28 | 3 | existing mixed recipes/physical ports |
| Pumpjack | 1 yield cycle/s | 12 | 2 | original percentage oil-yield owner |

The sixteen assembler inputs are real slots available to players and IItemHandler automation. Requirements exceeding one stack can reserve several slots; output/fuel indices never become recipe inputs. Stable recipe-ID selection, Auto, search, component-exact transactions and team unlocks apply across tiers. An unchanged definition preserves paid work; a different definition cannot borrow it. Tiers 2/3 inherit dry recipes under the same IDs and execute them only once through the mixed-production component.

Times in recipe JSON are **base work ticks**. A 20-tick recipe needs 40 / 26⅔ / 16 MC ticks at the three nominal speeds; work and module residues carry through consecutive crafts without per-craft rounding. Energy is machine power per active real tick. Modules modify the base speed/power once. Output reservations include productivity bonus items and fluids. No resources, power or burner heat are consumed while the complete next result is blocked.

Essential base timings now include gear/cable/electronic circuit .5 s (10 ticks), advanced circuit 6 s, processing unit/engine/electric engine 10 s, chemical science 24 s. Existing 1:10 fluid ingredients are retained. Solid Fuel has all three one-second Chemical Plant recipes: 200 mB gas, 100 mB light oil or 200 mB heavy oil → one fuel; 4800 native smelting-fuel ticks, three coals. No additional material was invented. Empty-component upgrade ingredients reject packed resource owners.

Early survival can hand-craft Stone Furnace and Burner Drill, use finite natural ore, make circuits and an Offshore Pump, then fuel a Boiler and supply two Steam Engines and real poles. Only Offshore Pump creates water from a valid loaded shore, without FE; the ordinary electric Pump cannot solve bootstrap by generating water. See [STEAM_POWER.md](STEAM_POWER.md). Nine-cell Minecraft hand recipes compress starting-machine counts and use only the corresponding material families; full counted industrial machine recipes are listed in the audit. This compression is an explicit adaptation, not original Factorio hand-crafting parity.

Assembler 2 remains behind Automation 2. New Assembler 3 is made from four Speed Modules and two empty Assembler 2 items and requires Automation 3. Automation 3 now retains its original production-science dependency and is survival-locked until that industry exists. Stack Inserter/stack-height research remains sandbox-only until its real Space Age progression exists. The current 0.27.0 laboratory/module/research implementation is described above; this paragraph otherwise records 0.26 machine behavior.

Machines bind to the invoking player's current vanilla/FTB research team as before. Team labs share authoritative progress and reserve the last science unit once. Diagnostic/admin unbound machines remain ungated for controlled fixtures. The audit distinguishes these diagnostics from survival progression.

The standalone recipe browser remains usable without optional mods. EMI/JEI show native ingredients, base work time, nominal primary-machine FE and research; Assembler 3 is a workstation for the existing dry and fluid assembler categories, avoiding duplicate synthetic recipe IDs. The real machine GUI reports that machine's speed/FE.
