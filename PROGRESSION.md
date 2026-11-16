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

Assembler 2 remains behind Automation 2. New Assembler 3 is made from four Speed Modules and two empty Assembler 2 items and requires Automation 3. Its current research uses existing red/green/blue packs, with Automation 2, Modules and Advanced Electronics 2 prerequisites: production science is not implemented, so this gate is explicitly temporary. Stack Inserter/stack-height research remains sandbox-only until its real Space Age progression exists. Higher science/module tiers and full original technology costs/prerequisites remain incomplete. Existing lab-efficiency research is a noncanonical retained adaptation; labs now use the 60-kW /8-FE base before that effect, but their two original module slots/effects remain future work.

Machines bind to the invoking player's current vanilla/FTB research team as before. Team labs share authoritative progress and reserve the last science unit once. Diagnostic/admin unbound machines remain ungated for controlled fixtures. The audit distinguishes these diagnostics from survival progression.

The standalone recipe browser remains usable without optional mods. EMI/JEI show native ingredients, base work time, nominal primary-machine FE and research; Assembler 3 is a workstation for the existing dry and fluid assembler categories, avoiding duplicate synthetic recipe IDs. The real machine GUI reports that machine's speed/FE.
