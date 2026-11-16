# Research reference — 0.27.0

Source: [Wube base technology.lua, tag 2.1.24](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/technology.lua). Space Age ENABLED: apply [space-age/base-data-updates.lua](https://github.com/wube/factorio-data/blob/2.1.24/space-age/base-data-updates.lua), especially Automation 3 and module tiers 2/3. `tools/check_space_age_research.py` checks the source-derived release constants. Counts and seconds are original; ticks = seconds ×20. Trigger rows show counts of actual produced items/extractions, not science-unit times. Trigger `ticks:1` is an unused schema sentinel; the UI and lab never bill it as research. Existing FactoryCraft IDs are retained where practical.

Mappings: `electronics` → Wube advanced-circuit; `bootstrap_electronics` → electronics; `advanced_electronics_2` → processing-unit; `electric_inserters` → fast-inserter; `batteries` → battery; `circuit_networks` → circuit-network; `*_modules_N` → family-module[-N]; `lab_research_speed_N` → research-speed-N. New `electric_mining` maps electric-mining-drill. Recipe and technology IDs share Minecraft's recipe registry, so lubricant and advanced-oil-processing technologies use `_research` suffixes to avoid overwriting production definitions.

Late production/utility-science dependencies are intentionally absent definitions until real industry exists; dependent definitions remain visible, disabled and keep their recipes locked. Sandbox Space Age transport capacity definitions are pre-existing creative diagnostics, not base progression. No late science crafting recipes have been added; all twelve canonical identities/physical slots are already supported. Space Science primary platform production and the extra 1000-pack Satellite alternative are both deferred.

| FactoryCraft technology | Units / trigger count | Seconds/unit | Packs / trigger | Prerequisites |
|---|---:|---:|---|---|
| `advanced_combinators` | 50 | 30 | automation×1, logistic×1, chemical×1 | `circuit_networks`, `chemical_science` |
| `advanced_electronics_2` | 300 | 30 | automation×1, logistic×1, chemical×1 | `chemical_science` |
| `advanced_material_processing` | 75 | 30 | automation×1, logistic×1 | `steel_processing`, `logistic_science` |
| `advanced_material_processing_2` | 250 | 30 | automation×1, logistic×1, chemical×1 | `advanced_material_processing`, `chemical_science` |
| `advanced_oil_processing_research` | 75 | 30 | automation×1, logistic×1, chemical×1 | `chemical_science` |
| `automation` | 10 | 10 | automation×1 | `automation_science` |
| `automation_2` | 40 | 15 | automation×1, logistic×1 | `automation`, `steel_processing`, `logistic_science` |
| `automation_3` | 500 | 60 | automation×1, logistic×1, chemical×1, production×1 | `speed_modules_1`, `production_science_pack`, `electric_engine` |
| `automation_science` | 1 | 0.05 | craft:factorycraft:lab | `steam_power`, `bootstrap_electronics` |
| `batteries` | 150 | 30 | automation×1, logistic×1 | `sulfur_processing` |
| `bootstrap_electronics` | 10 | 0.05 | craft:factorycraft:copper_plate | — |
| `bulk_inserters` | 150 | 30 | automation×1, logistic×1 | `electric_inserters`, `logistics_2`, `electronics` |
| `chemical_science` | 75 | 10 | automation×1, logistic×1 | `electronics`, `sulfur_processing` |
| `circuit_networks` | 100 | 15 | automation×1, logistic×1 | `logistic_science` |
| `effect_transmission` | 75 | 30 | automation×1, logistic×1, chemical×1, production×1 | `advanced_electronics_2`, `production_science_pack` |
| `efficiency_modules_1` | 50 | 30 | automation×1, logistic×1 | `modules` |
| `efficiency_modules_2` | 200 | 30 | automation×1, logistic×1, chemical×1, space×1 | `efficiency_modules_1`, `space_science_pack` |
| `efficiency_modules_3` | 300 | 60 | automation×1, logistic×1, chemical×1, space×1, agricultural×1 | `efficiency_modules_2`, `agricultural_science_pack` |
| `electric_energy_distribution_1` | 120 | 30 | automation×1, logistic×1 | `steel_processing`, `logistic_science` |
| `electric_energy_distribution_2` | 100 | 45 | automation×1, logistic×1, chemical×1 | `electric_energy_distribution_1`, `chemical_science` |
| `electric_engine` | 50 | 30 | automation×1, logistic×1, chemical×1 | `lubricant_research` |
| `electric_inserters` | 30 | 15 | automation×1 | `automation_science` |
| `electric_mining` | 25 | 10 | automation×1 | `automation_science` |
| `electronics` | 200 | 15 | automation×1, logistic×1 | `plastics` |
| `engine` | 100 | 15 | automation×1, logistic×1 | `steel_processing`, `logistic_science` |
| `fluid_handling` | 50 | 15 | automation×1, logistic×1 | `automation_2`, `engine` |
| `inserter_capacity_bonus_1` | 200 | 30 | automation×1, logistic×1 | `bulk_inserters` |
| `inserter_capacity_bonus_2` | 250 | 30 | automation×1, logistic×1 | `inserter_capacity_bonus_1` |
| `inserter_capacity_bonus_3` | 250 | 30 | automation×1, logistic×1, chemical×1 | `inserter_capacity_bonus_2`, `chemical_science` |
| `inserter_capacity_bonus_4` | 250 | 30 | automation×1, logistic×1, chemical×1, production×1 | `inserter_capacity_bonus_3`, `production_science_pack` |
| `inserter_capacity_bonus_5` | 300 | 30 | automation×1, logistic×1, chemical×1, production×1 | `inserter_capacity_bonus_4` |
| `inserter_capacity_bonus_6` | 400 | 30 | automation×1, logistic×1, chemical×1, production×1 | `inserter_capacity_bonus_5` |
| `inserter_capacity_bonus_7` | 600 | 30 | automation×1, logistic×1, chemical×1, production×1, utility×1 | `inserter_capacity_bonus_6`, `utility_science_pack` |
| `lab_research_speed_1` | 100 | 30 | automation×1, logistic×1 | `automation_2` |
| `lab_research_speed_2` | 200 | 30 | automation×1, logistic×1 | `lab_research_speed_1` |
| `lab_research_speed_3` | 250 | 30 | automation×1, logistic×1, chemical×1 | `lab_research_speed_2`, `chemical_science` |
| `lab_research_speed_4` | 500 | 30 | automation×1, logistic×1, chemical×1 | `lab_research_speed_3` |
| `lab_research_speed_5` | 500 | 30 | automation×1, logistic×1, chemical×1, production×1 | `lab_research_speed_4`, `production_science_pack` |
| `lab_research_speed_6` | 500 | 30 | automation×1, logistic×1, chemical×1, production×1, utility×1 | `lab_research_speed_5`, `utility_science_pack` |
| `logistic_science` | 75 | 5 | automation×1 | `automation_science` |
| `logistics` | 20 | 15 | automation×1 | `automation_science` |
| `logistics_2` | 200 | 30 | automation×1, logistic×1 | `logistics`, `logistic_science` |
| `logistics_3` | 300 | 15 | automation×1, logistic×1, chemical×1, production×1 | `production_science_pack`, `lubricant_research` |
| `lubricant_research` | 50 | 30 | automation×1, logistic×1, chemical×1 | `advanced_oil_processing_research` |
| `modules` | 100 | 30 | automation×1, logistic×1 | `electronics` |
| `oil_gathering` | 100 | 30 | automation×1, logistic×1 | `fluid_handling` |
| `oil_processing` | 1 | 0.05 | mine:factorycraft:crude_oil | `oil_gathering` |
| `plastics` | 200 | 30 | automation×1, logistic×1 | `oil_processing` |
| `productivity_modules_1` | 50 | 30 | automation×1, logistic×1 | `modules` |
| `productivity_modules_2` | 200 | 30 | automation×1, logistic×1, chemical×1, space×1 | `productivity_modules_1`, `space_science_pack` |
| `productivity_modules_3` | 300 | 60 | automation×1, logistic×1, chemical×1, space×1, agricultural×1 | `productivity_modules_2`, `biter_egg_handling` |
| `speed_modules_1` | 50 | 30 | automation×1, logistic×1 | `modules` |
| `speed_modules_2` | 200 | 30 | automation×1, logistic×1, chemical×1, space×1 | `speed_modules_1`, `space_science_pack` |
| `speed_modules_3` | 2000 | 60 | automation×1, logistic×1, chemical×1, space×1, metallurgic×1 | `speed_modules_2`, `metallurgic_science_pack` |
| `steam_power` | 50 | 0.05 | craft:factorycraft:iron_plate | — |
| `steel_processing` | 50 | 5 | automation×1 | `automation_science` |
| `sulfur_processing` | 150 | 30 | automation×1, logistic×1 | `oil_processing` |

Existing sandbox transport capacity: level 1 costs 2000 units, level 2 3000 units, both 60 seconds/unit and red/green/blue/purple/yellow/space/agricultural science. Original prerequisites are Stack Inserter (1), then capacity 1 +Inserter Capacity Bonus 7 (2). Source: space-age/prototypes/technology.lua. Both remain explicitly sandbox-only; no fake missing technology definition is registered.
