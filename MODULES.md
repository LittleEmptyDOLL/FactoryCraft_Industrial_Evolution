## 0.29 centrifuge and construction

Centrifuge uses two existing module slots and the common beacon effects. Source uranium processing is productive: paid fractional credit can earn another categorical isotope result, with its own committed roll. Output space is reserved for either possible isotope before payment. Fuel-cell production uses normal assembler productivity; hardware and concrete do not gain unearned productive outputs. Large hardware bills use saved ordinary-stack construction escrow and the existing machine speed/energy effects. All nine module definitions and late Space Age gates are retained.

# Modules — 0.27.0

Reference: **Factorio 2.1.24 with Space Age enabled, normal quality**. [Wube recipes](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/recipe.lua), [technology](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/technology.lua), [Module](https://wiki.factorio.com/Module), [Beacon](https://wiki.factorio.com/Beacon). Space Age overrides: [pinned base-data-updates.lua](https://github.com/wube/factorio-data/blob/2.1.24/space-age/base-data-updates.lua), lines 249–263 and 669–740. Existing FactoryCraft module textures are used; no Factorio graphics/source are copied.

| Family / tier | Speed | Energy | Productivity |
|---|---:|---:|---:|
| Speed 1 | +20% | +50% | 0 |
| Speed 2 | +30% | +60% | 0 |
| Speed 3 | +50% | +70% | 0 |
| Efficiency 1 | 0 | −30% | 0 |
| Efficiency 2 | 0 | −40% | 0 |
| Efficiency 3 | 0 | −50% | 0 |
| Productivity 1 | −5% | +40% | +4% |
| Productivity 2 | −10% | +60% | +6% |
| Productivity 3 | −15% | +80% | +10% |

All nine registry items have immutable family/tier profiles, original models/textures, localized names/effect tooltips and datapack family tags `factorycraft:modules/{speed,efficiency,productivity}`. Stack components cannot counterfeit a different profile. Machine/BE inventories validate singleton module stacks, including loaded data. Effects add before clamping; minimum energy/speed are 20%. Previous artificial 5× speed/10× power ceilings were raised to technical bounds above physically achievable normal-quality arrays, so legitimate tier-3 beacon setups are not clipped. No base machine speed, fuel balance or hydraulic quantity changes.

## Crafting and technology

Every tier 1 costs 5 electronic + 5 advanced circuits and 15 seconds. Tier 2 costs **4 preceding modules + 5 advanced circuits + 5 processing units**, taking 30 seconds. Tier 3 takes 60 seconds and adds the Space Age input: Speed 3 **1 tungsten carbide**, Productivity 3 **1 biter egg**, Efficiency 3 **5 spoilage**, in addition to 4 preceding modules and 5+5 circuits. These three material items currently represent deferred input identities with original temporary FactoryCraft icons, no survival source and no spoilage/lifecycle gameplay. Factory recipes use seconds ×20 ticks and retain existing FE conversion. Module crafting never allows productivity.

The Modules technology requires Advanced Circuits (100 red+green units, 30 s/unit), then each family tier 1 has its own 50 red+green-unit, 30 s/unit research. Tier 2 requires its preceding family and **Space Science**: 200 red+green+blue+space units, 30 s/unit. Speed 3 additionally requires Metallurgic Science: 2000 red+green+blue+space+metallurgic units, 60 s/unit. Efficiency 3 requires Agricultural Science, Productivity 3 Biter Egg Handling; both cost 300 red+green+blue+space+agricultural units, 60 s/unit. All recipes exist, but tiers 2/3 remain survival-locked until real space/offworld supplies exist. Recipe JSON/network metadata preserves the original `crafting` and `electromagnetics` categories; existing assemblers execute crafting, with no fictional EM plant introduced. There are no substitute packs or early unlocks. Creative/admin testing remains possible.

## Inventories, productivity and beacons

Slots: Assembler 1 **0**, Assembler 2 **2**, Assembler 3 **4**, electric drill **3**, electric furnace **2**, pumpjack **2**, refinery **3**, chemical plant **3**, lab **2**, beacon **2**, burner machines **0**. All slots hold one item. Standard machine module menus remain; the laboratory has its two separate slots in its research menu.

Existing recipe-specific productive-output tags and whole-output reservation remain authoritative for items, fluids, mining and oil yield. Placeable machines/modules cannot become productive merely because higher module tiers are registered. Extra outputs must fit before charging/depleting inputs, pending outputs retain exact components, and partial-work credit is proportional to actual work. The existing 1:10 fluid scale, oil yield/depletion, drill chute and furnace state remain unchanged. Laboratory productivity grants research units through the shared ledger rather than generating an item; see `RESEARCH.md`.

A beacon keeps two speed/efficiency slots of any tier, rejects productivity, and pays its existing **64 FE/t** active lease. Its 3×3 physical structure, ±3 controller-height adaptation and three-block horizontal reach beyond the actual foundation remain. Coverage uses physical footprint overlap, including labs. Combined transmission is `1.5 / sqrt(n)` for n active paid beacons. Only speed/energy are broadcast; own productivity is never amplified by beacons. Cached spatial recipients and paid lease checks avoid global scans and unloaded/free effects.

Single controller ownership, portable stacks, module inventories, fractional work/FE and credit survive the new format. Empty machine module state remains compact; laboratory schema 3 always declares exactly twelve science and two module slots. Previous intermediate lab formats are unsupported. This is normal independent chunk/world saving, not a cross-file crash journal. See current validation evidence for checks actually executed.

Speed-module quality penalties and Quality-module gameplay are deferred to the full quality subsystem; normal-quality speed/energy/productivity effects are implemented now.
