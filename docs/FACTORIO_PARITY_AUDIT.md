# Factorio parity audit — FactoryCraft 0.26.1

This is a current-content inventory, not a claim that all Factorio is implemented. Reference: **Factorio 2.1.24, normal quality**. Numeric source: [wube/factorio-data tag 2.1.24](https://github.com/wube/factorio-data/tree/2.1.24), specifically base/prototypes/entity/{entities,mining-drill}.lua, base/prototypes/recipe.lua and base/prototypes/technology.lua; cross-check with the official wiki links in STEAM_POWER.md / FURNACES.md / INSERTERS.md. No source graphics are copied. One FU=10 mB; 60→20 TPS is time conversion, not a second volume multiplier.

Categories: **Base**, **Deferred Space Age**, **Approved extension**, **Minecraft adaptation/internal**, **Incomplete**, **Removed**. Heights, finite FE buffers, vanilla materials/fuel and NeoForge capabilities are explicit adaptations. Entries marked incomplete are not advertised as parity-complete.

## Complete machine registries

All FactoryKind, FluidKind and ThermalKind entries follow. The three internal parts are not inventory items or additional machines. Original names use hyphens; FactoryCraft IDs remain the actual registered IDs.

| FactoryCraft ID | Classification | Original prototype | Current implementation / action |
|---|---|---|---|
| `transport_belt` | Base | `transport-belt` | Existing base three-tier two-lane carrier/stack transport; headway, filters/priorities and persistence unchanged. 3D slopes are MC adaptations; see BELTS.md. |
| `fast_transport_belt` | Base | `fast-transport-belt` | Existing base three-tier two-lane carrier/stack transport; headway, filters/priorities and persistence unchanged. 3D slopes are MC adaptations; see BELTS.md. |
| `express_transport_belt` | Base | `express-transport-belt` | Existing base three-tier two-lane carrier/stack transport; headway, filters/priorities and persistence unchanged. 3D slopes are MC adaptations; see BELTS.md. |
| `conveyor_lift` | Approved extension | `—` | Approved FactoryCraft 3D extension; all three transport speed tiers, no FE, same two-lane/stack/owned-carrier simulation. Not a Factorio entity. |
| `fast_conveyor_lift` | Approved extension | `—` | Approved FactoryCraft 3D extension; all three transport speed tiers, no FE, same two-lane/stack/owned-carrier simulation. Not a Factorio entity. |
| `express_conveyor_lift` | Approved extension | `—` | Approved FactoryCraft 3D extension; all three transport speed tiers, no FE, same two-lane/stack/owned-carrier simulation. Not a Factorio entity. |
| `underground_belt` | Base | `underground-belt` | Existing base three-tier two-lane carrier/stack transport; headway, filters/priorities and persistence unchanged. 3D slopes are MC adaptations; see BELTS.md. |
| `fast_underground_belt` | Base | `fast-underground-belt` | Existing base three-tier two-lane carrier/stack transport; headway, filters/priorities and persistence unchanged. 3D slopes are MC adaptations; see BELTS.md. |
| `express_underground_belt` | Base | `express-underground-belt` | Existing base three-tier two-lane carrier/stack transport; headway, filters/priorities and persistence unchanged. 3D slopes are MC adaptations; see BELTS.md. |
| `transport_part` | Minecraft adaptation/internal | `—` | Technical sole-owner redirect only; no independent item, inventory, fluids, FE or graph node. |
| `splitter` | Base | `splitter` | Existing base three-tier two-lane carrier/stack transport; headway, filters/priorities and persistence unchanged. 3D slopes are MC adaptations; see BELTS.md. |
| `inserter` | Base | `inserter` | Existing 0.21/0.23 fuel/FE hand, fixed-point speed, common five filters and researched capacity. See INSERTERS.md; no new arm mechanics in this release. |
| `electric_drill` | Base | `electric-mining-drill` | 3×3; area 5×5; .5 ore/s; 90 kW = 12 FE/t; 3 modules. Corrected paid fractional work; all 3 module effects allowed. |
| `burner_mining_drill` | Base | `burner-mining-drill` | 2×2; mining area 2×2; .25 ore/s; 150 kW heat = 5/3 native fuel ticks/t; 0 modules. Added with finite tile UUID/work ledger. |
| `electric_furnace` | Base | `electric-furnace` | 3×3; speed 2; 180 kW = 24 FE/t; 2 modules; native smelting/productivity retained. |
| `stone_furnace` | Base | `stone-furnace` | 2×2; speed 1; 90 kW solid heat; 0 modules; native smelting interop retained. |
| `assembling_machine` | Base | `assembling-machine-1` | 3×3; speed .5; 75 kW = 10 FE/t; 0 modules; dry recipes only; 16 physical inputs + 4 outputs. |
| `assembling_machine_2` | Base | `assembling-machine-2` | 3×3; speed .75; 150 kW = 20 FE/t; 2 modules; fluid-capable; 16 inputs + 4 outputs; inherited dry IDs. |
| `assembling_machine_3` | Base | `assembling-machine-3` | 3×3; speed 1.25; 375 kW = 50 FE/t; 4 modules; fluid-capable; 16 inputs + 4 outputs. Added; temporary blue-science Automation 3 gate. |
| `lab` | Base; incomplete | `lab` | 3×3; 60 kW = 8 FE/t before retained lab-efficiency adaptation. Three implemented science packs. INCOMPLETE: original two lab module slots/effects and full science tree are not implemented; FactoryKind slot metadata is not a claim of an accessible lab module inventory. |
| `beacon` | Base | `beacon` | 3×3; 480 kW = 64 FE/t; 2 speed/efficiency modules, no productivity; normal-quality 1.5/sqrt(n) transmission. No quality variants. |
| `electric_pole` | Base | `small-electric-pole` | 1×1, height 3; 5×5 supply, 7.5 wire; no FE/modules; explicit reciprocal wires. |
| `medium_electric_pole` | Base | `medium-electric-pole` | 1×1, height 3; 7×7 supply, 9 wire; no FE/modules; existing compact material substitutions remain. |
| `big_electric_pole` | Base | `big-electric-pole` | 2×2, height 5; 4×4 supply, 32 wire; no FE/modules; existing compact material substitutions remain. |
| `substation` | Base | `substation` | 2×2, height 4; 18×18 supply, 18 wire; no FE/modules; original supply/wire geometry retained. |
| `steel_furnace` | Base | `steel-furnace` | 2×2; speed 2; 90 kW solid heat; 0 modules; native smelting interop retained. |
| `machine_casing` | Minecraft adaptation/internal | `—` | Technical sole-owner redirect only; no independent item, inventory, fluids, FE or graph node. |
| `pumpjack` | Base | `pumpjack` | 3×3; speed 1; 90 kW = 12 FE/t; 2 modules, including productivity. Yield percentages/depletion remain 0.25; saved /10000 energy and productivity residues. |
| `oil_refinery` | Base | `oil-refinery` | 5×5; speed 1; 420 kW = 56 FE/t; 3 modules; five separate owned cells; fluid productivity now reserves bonus volume. |
| `chemical_plant` | Base | `chemical-plant` | 3×3; speed 1; 210 kW = 28 FE/t; 3 modules; two inputs/two outputs; fluid and allowed item productivity. |
| `refinery_casing` | Minecraft adaptation/internal | `—` | Technical sole-owner redirect only; no independent item, inventory, fluids, FE or graph node. |
| `fast_splitter` | Base | `fast-splitter` | Existing base three-tier two-lane carrier/stack transport; headway, filters/priorities and persistence unchanged. 3D slopes are MC adaptations; see BELTS.md. |
| `express_splitter` | Base | `express-splitter` | Existing base three-tier two-lane carrier/stack transport; headway, filters/priorities and persistence unchanged. 3D slopes are MC adaptations; see BELTS.md. |
| `fast_inserter` | Base | `fast-inserter` | Existing 0.21/0.23 fuel/FE hand, fixed-point speed, common five filters and researched capacity. See INSERTERS.md; no new arm mechanics in this release. |
| `long_inserter` | Base | `long-handed-inserter` | Existing 0.21/0.23 fuel/FE hand, fixed-point speed, common five filters and researched capacity. See INSERTERS.md; no new arm mechanics in this release. |
| `bulk_inserter` | Base | `bulk-inserter` | Existing 0.21/0.23 fuel/FE hand, fixed-point speed, common five filters and researched capacity. See INSERTERS.md; no new arm mechanics in this release. |
| `stack_inserter` | Deferred Space Age | `stack-inserter` | Real 6–16-item hand and 2–4-high belt stacks; creative/diagnostic access only; no early survival recipe. Space Age progression deferred. |
| `burner_inserter` | Base | `burner-inserter` | Existing 0.21/0.23 fuel/FE hand, fixed-point speed, common five filters and researched capacity. See INSERTERS.md; no new arm mechanics in this release. |
| `constant_combinator` | Base | `constant-combinator` | Existing typed direct four-color graph/program, sole saved owner; port staging retained. INCOMPLETE: arithmetic/decider electric draw and quality-dependent selectors; no new fake reader/controller. |
| `arithmetic_combinator` | Base; incomplete | `arithmetic-combinator` | Existing typed direct four-color graph/program, sole saved owner; port staging retained. INCOMPLETE: arithmetic/decider electric draw and quality-dependent selectors; no new fake reader/controller. |
| `decider_combinator` | Base; incomplete | `decider-combinator` | Existing typed direct four-color graph/program, sole saved owner; port staging retained. INCOMPLETE: arithmetic/decider electric draw and quality-dependent selectors; no new fake reader/controller. |
| `selector_combinator` | Base; incomplete | `selector-combinator` | Existing typed direct four-color graph/program, sole saved owner; port staging retained. INCOMPLETE: arithmetic/decider electric draw and quality-dependent selectors; no new fake reader/controller. |
| `fluid_pipe` | Base | `pipe` | Passive six-direction segment member; 1000-mB capacity; no FE/modules; no internal per-edge flow tick. |
| `fluid_tank` | Base | `storage-tank` | 3×3; passive shared segment member; 250000-mB capacity; sole checkpoint share on dismantle; no FE/modules. |
| `electric_pump` | Base | `pump` | 1×2; directed segment boundary; 600 mB/t nominal/pressure-limited; 30 kW = 4 FE/active tick. No world-block generation. |
| `offshore_pump` | Base | `offshore-pump` | 1×2; 600 mB/t; no FE/fuel/modules; land controller, rear intake above one source, inland outlet; independent saved source counter; no arbitrary minimum lake size. |
| `boiler` | Base | `boiler` | 3×2; 1800 kW heat; 3 water→30 steam mB/t; no modules. Solid fuel/remainders and physical BOTH water ports. |

| Removed ID | Reason / replacement |
|---|---|
| `coal_generator` | Invented generator removed, including config/assets/recipes. Real Offshore→Boiler→Steam Engines now boots the factory. Only controlled native invariant tests use finite precharged steam. |
| `circuit_reader`, `circuit_controller` | Obsolete remote devices removed. Canonical direct machine/chest/tank contacts and four real combinators remain. Former invariant tests now cover direct unknown-owner quarantine, exact components, menus, four colors and disk state. |
| `industrial_furnace`, `filter_inserter` | Earlier retirements preserved; three original furnaces and all-arm filtering remain. |

## Standalone items and geological blocks

| IDs (each listed item is independently registered) | Classification / canonical reference | Status |
|---|---|---|
| `iron_plate`, `copper_plate`, `steel_plate`, `iron_gear`, `copper_wire`, `electronic_circuit`, `advanced_circuit`, `processing_unit`, `engine_unit`, `electric_engine_unit`, `plastic_bar`, `sulfur`, `battery`, `solid_fuel` | Base; `iron-gear-wheel` / `copper-cable` aliases documented | Native counted production; essential circuit/engine/science timings corrected; solid fuel has all three original chemistry conversions and 3× native coal heat. |
| `speed_module`, `efficiency_module`, `productivity_module` | Base tier 1 | +20% speed/+50% consumption; −30% consumption; −5% speed/+40% consumption/+4% productivity. Original material recipe families; higher module tiers absent. Pumpjack accepts productivity; beacon rejects it. Labs remain incomplete. |
| `automation_science_pack`, `logistic_science_pack`, `chemical_science_pack` | Base | Original product families; exact known base crafting times, current simplified technology costs. Other science tiers not registered as placeholders. |
| `red_circuit_wire`, `green_circuit_wire` | Base feature / MC reusable tools | Existing direct wire tools; no item consumption per connection. |
| `blue_circuit_wire`, `white_circuit_wire` | **Approved extension** | Both colors and graph tests retained unchanged. |
| `copper_wire_tool`, `wrench`, `fluid_canister` | Minecraft adaptation | Separate copper connection tool (not production cable), safe dismantling/rotation and exact-component portable native-mB fluid storage. |
| `steam_bucket`, `crude_oil_bucket`, `petroleum_gas_bucket`, `heavy_oil_bucket`, `light_oil_bucket`, `lubricant_bucket`, `sulfuric_acid_bucket` | Minecraft adaptation | Actual 1000-mB NeoForge buckets. Component-bearing fluids cannot be erased into componentless buckets; use the canister. |
| `iron_ore`, `copper_ore`, `coal_deposit`, `stone_deposit`, `oil_deposit` (blocks; no regenerating block items) | Base resource families / Minecraft geometry adaptation | Finite UUID-owned solid tile stock in fixed-center irregular ellipsoids; percentage-yield oil points. Original resources use native Minecraft raw iron/copper/coal/cobblestone outputs. No uranium or late-game fake resource registration. |

## Recipes and progression

The generated counted assembler/chemical recipes use full ingredient counts, stable IDs and native mB. Nine-slot vanilla hand crafting for starting machines is explicitly compressed: Assembler 1 uses 6 plates/2 gears/1 circuit; Electric Drill 5 plates/2 gears/2 circuits; Steam Engine 4 plates/3 gears/2 pipes. The counted machine recipes retain their full 9/5/3, 10/5/3 and 10/8/5 original counts respectively. Stone Furnace uses native cobblestone. Starting circuits, poles, tools, frames and legacy refinery hand recipes also use documented Minecraft materials; these are not exact original hand-crafting counts. No invented new material was added. Upgrade recipes require empty native components, rejecting packed machines so inventories/fluids/FE cannot be consumed as crafting ingredients.

Base recipe time is in 20-TPS work units. Machine speed applies once, including inherited dry recipes in tiers 2/3. `energy` metadata is nominal primary-machine active FE/t, not total craft cost. Secondary assembler tiers use their own power. Exact current processing/chemistry/boiling definitions follow; ordinary hand recipes are the adaptations above.

| Recipe ID | Machine | Native counted inputs | Native outputs | Base ticks / nominal FE/t |
|---|---|---|---|---|
| `advanced_circuit` | assembling_machine | 2×factorycraft:electronic_circuit, 4×factorycraft:copper_wire, 2×factorycraft:plastic_bar | 1×factorycraft:advanced_circuit | 120 / 10 |
| `advanced_oil_processing` | oil_refinery | 1000 mB factorycraft:crude_oil, 500 mB minecraft:water | 550 mB factorycraft:petroleum_gas, 450 mB factorycraft:light_oil, 250 mB factorycraft:heavy_oil | 100 / 56 |
| `arithmetic_combinator` | assembling_machine | 5×factorycraft:copper_wire, 5×factorycraft:electronic_circuit | 1×factorycraft:arithmetic_combinator | 10 / 10 |
| `assembling_machine` | assembling_machine | 9×factorycraft:iron_plate, 3×factorycraft:electronic_circuit, 5×factorycraft:iron_gear | 1×factorycraft:assembling_machine | 10 / 10 |
| `assembling_machine_2` | assembling_machine | 2×factorycraft:steel_plate, 3×factorycraft:electronic_circuit, 5×factorycraft:iron_gear, 1×['factorycraft:assembling_machine'] | 1×factorycraft:assembling_machine_2 | 10 / 10 |
| `assembling_machine_3` | assembling_machine | 4×factorycraft:speed_module, 2×['factorycraft:assembling_machine_2'] | 1×factorycraft:assembling_machine_3 | 10 / 10 |
| `automation_science_pack` | assembling_machine | 1×factorycraft:iron_gear, 1×factorycraft:copper_plate | 1×factorycraft:automation_science_pack | 100 / 10 |
| `basic_oil_processing` | oil_refinery | 1000 mB factorycraft:crude_oil | 450 mB factorycraft:petroleum_gas | 100 / 56 |
| `battery` | chemical_plant | 1×factorycraft:iron_plate, 1×factorycraft:copper_plate, 200 mB factorycraft:sulfuric_acid | 1×factorycraft:battery | 80 / 28 |
| `beacon` | assembling_machine | 10×factorycraft:steel_plate, 10×factorycraft:electronic_circuit, 10×factorycraft:processing_unit | 1×factorycraft:beacon | 400 / 10 |
| `big_electric_pole` | assembling_machine | 5×factorycraft:steel_plate, 4×factorycraft:copper_wire, 4×factorycraft:iron_plate | 1×factorycraft:big_electric_pole | 20 / 10 |
| `boil_water` | boiler | 3 mB water + fuel | 30×factorycraft:steam | 1 / 0 |
| `boiler` | assembling_machine | 1×['factorycraft:stone_furnace'], 4×factorycraft:fluid_pipe | 1×factorycraft:boiler | 10 / 10 |
| `bulk_inserter` | assembling_machine | 1×['factorycraft:fast_inserter'], 15×factorycraft:iron_gear, 15×factorycraft:electronic_circuit, 1×factorycraft:advanced_circuit | 1×factorycraft:bulk_inserter | 10 / 10 |
| `burner_inserter` | assembling_machine | 1×factorycraft:iron_plate, 1×factorycraft:iron_gear | 1×factorycraft:burner_inserter | 10 / 10 |
| `burner_mining_drill` | assembling_machine | 3×factorycraft:iron_gear, 3×factorycraft:iron_plate, 1×['factorycraft:stone_furnace'] | 1×factorycraft:burner_mining_drill | 40 / 10 |
| `chemical_science_pack` | assembling_machine | 2×factorycraft:advanced_circuit, 3×factorycraft:engine_unit, 1×factorycraft:sulfur | 2×factorycraft:chemical_science_pack | 480 / 10 |
| `constant_combinator` | assembling_machine | 5×factorycraft:copper_wire, 2×factorycraft:electronic_circuit | 1×factorycraft:constant_combinator | 10 / 10 |
| `copper_plate` | smelting | 1×minecraft:raw_copper | 1×factorycraft:copper_plate | 64 / 24 |
| `copper_wire` | assembling_machine | 1×factorycraft:copper_plate | 2×factorycraft:copper_wire | 10 / 10 |
| `decider_combinator` | assembling_machine | 5×factorycraft:copper_wire, 5×factorycraft:electronic_circuit | 1×factorycraft:decider_combinator | 10 / 10 |
| `efficiency_module` | assembling_machine | 5×factorycraft:advanced_circuit, 5×factorycraft:electronic_circuit | 1×factorycraft:efficiency_module | 300 / 10 |
| `electric_drill` | assembling_machine | 5×factorycraft:iron_gear, 3×factorycraft:electronic_circuit, 10×factorycraft:iron_plate | 1×factorycraft:electric_drill | 40 / 10 |
| `electric_engine_unit` | assembling_machine_2 | 1×factorycraft:engine_unit, 2×factorycraft:electronic_circuit, 150 mB factorycraft:lubricant | 1×factorycraft:electric_engine_unit | 200 / 20 |
| `electric_furnace` | assembling_machine | 10×factorycraft:steel_plate, 5×factorycraft:advanced_circuit, 10×minecraft:stone_bricks | 1×factorycraft:electric_furnace | 100 / 10 |
| `electric_pole` | assembling_machine | 1×#minecraft:planks, 2×factorycraft:copper_wire | 2×factorycraft:electric_pole | 10 / 10 |
| `electronic_circuit` | assembling_machine | 1×factorycraft:iron_plate, 3×factorycraft:copper_wire | 1×factorycraft:electronic_circuit | 10 / 10 |
| `engine_unit` | assembling_machine | 1×factorycraft:steel_plate, 1×factorycraft:iron_gear, 2×factorycraft:fluid_pipe | 1×factorycraft:engine_unit | 200 / 10 |
| `express_splitter` | assembling_machine_2 | 1×['factorycraft:fast_splitter'], 10×factorycraft:iron_gear, 10×factorycraft:advanced_circuit, 800 mB factorycraft:lubricant | 1×factorycraft:express_splitter | 40 / 20 |
| `fast_inserter` | assembling_machine | 1×['factorycraft:inserter'], 2×factorycraft:iron_plate, 2×factorycraft:electronic_circuit | 1×factorycraft:fast_inserter | 10 / 10 |
| `fast_splitter` | assembling_machine | 1×['factorycraft:splitter'], 10×factorycraft:iron_gear, 10×factorycraft:electronic_circuit | 1×factorycraft:fast_splitter | 40 / 10 |
| `heavy_oil_cracking` | chemical_plant | 400 mB factorycraft:heavy_oil, 300 mB minecraft:water | 300 mB factorycraft:light_oil | 40 / 28 |
| `inserter` | assembling_machine | 1×factorycraft:iron_plate, 1×factorycraft:iron_gear, 1×factorycraft:electronic_circuit | 1×factorycraft:inserter | 10 / 10 |
| `iron_gear` | assembling_machine | 2×factorycraft:iron_plate | 1×factorycraft:iron_gear | 10 / 10 |
| `iron_plate` | smelting | 1×minecraft:raw_iron | 1×factorycraft:iron_plate | 64 / 24 |
| `light_oil_cracking` | chemical_plant | 300 mB factorycraft:light_oil, 300 mB minecraft:water | 200 mB factorycraft:petroleum_gas | 40 / 28 |
| `logistic_science_pack` | assembling_machine | 1×factorycraft:transport_belt, 1×factorycraft:inserter | 1×factorycraft:logistic_science_pack | 120 / 10 |
| `long_inserter` | assembling_machine | 1×['factorycraft:inserter'], 1×factorycraft:iron_plate, 1×factorycraft:iron_gear | 1×factorycraft:long_inserter | 10 / 10 |
| `lubricant` | chemical_plant | 100 mB factorycraft:heavy_oil | 100 mB factorycraft:lubricant | 20 / 28 |
| `medium_electric_pole` | assembling_machine | 2×factorycraft:steel_plate, 2×factorycraft:copper_wire, 2×factorycraft:iron_plate | 1×factorycraft:medium_electric_pole | 20 / 10 |
| `offshore_pump` | assembling_machine | 2×factorycraft:electronic_circuit, 1×factorycraft:fluid_pipe, 1×factorycraft:iron_gear | 1×factorycraft:offshore_pump | 10 / 10 |
| `plastic` | chemical_plant | 1×minecraft:coal, 200 mB factorycraft:petroleum_gas | 2×factorycraft:plastic_bar | 20 / 28 |
| `processing_unit` | assembling_machine_2 | 20×factorycraft:electronic_circuit, 2×factorycraft:advanced_circuit, 50 mB factorycraft:sulfuric_acid | 1×factorycraft:processing_unit | 200 / 20 |
| `productivity_module` | assembling_machine | 5×factorycraft:advanced_circuit, 5×factorycraft:electronic_circuit | 1×factorycraft:productivity_module | 300 / 10 |
| `selector_combinator` | assembling_machine | 2×factorycraft:advanced_circuit, 5×factorycraft:decider_combinator | 1×factorycraft:selector_combinator | 10 / 10 |
| `solid_fuel_from_heavy_oil` | chemical_plant | 200 mB factorycraft:heavy_oil | 1×factorycraft:solid_fuel | 20 / 28 |
| `solid_fuel_from_light_oil` | chemical_plant | 100 mB factorycraft:light_oil | 1×factorycraft:solid_fuel | 20 / 28 |
| `solid_fuel_from_petroleum_gas` | chemical_plant | 200 mB factorycraft:petroleum_gas | 1×factorycraft:solid_fuel | 20 / 28 |
| `speed_module` | assembling_machine | 5×factorycraft:advanced_circuit, 5×factorycraft:electronic_circuit | 1×factorycraft:speed_module | 300 / 10 |
| `steam_engine` | assembling_machine | 10×factorycraft:iron_plate, 8×factorycraft:iron_gear, 5×factorycraft:fluid_pipe | 1×factorycraft:steam_engine | 10 / 10 |
| `steel_furnace` | assembling_machine | 6×factorycraft:steel_plate, 10×minecraft:stone_bricks | 1×factorycraft:steel_furnace | 60 / 10 |
| `steel_plate` | smelting | 5×factorycraft:iron_plate | 1×factorycraft:steel_plate | 320 / 24 |
| `stone_brick` | smelting | 2×minecraft:cobblestone | 1×minecraft:stone_bricks | 64 / 24 |
| `substation` | assembling_machine | 10×factorycraft:steel_plate, 6×factorycraft:copper_wire, 5×factorycraft:advanced_circuit | 1×factorycraft:substation | 20 / 10 |
| `sulfur` | chemical_plant | 300 mB factorycraft:petroleum_gas, 300 mB minecraft:water | 2×factorycraft:sulfur | 20 / 28 |
| `sulfuric_acid` | chemical_plant | 5×factorycraft:sulfur, 1×factorycraft:iron_plate, 1000 mB minecraft:water | 500 mB factorycraft:sulfuric_acid | 20 / 28 |

Every current technology is listed below. This remains a three-science subset: costs/prerequisites are mostly compressed legacy progression, not the full Factorio technology graph. Automation 3 uses existing blue science until production science exists; Stack Inserter/belt stacking stays sandbox-only. `lab_efficiency` is a retained noncanonical repeatable diagnostic/progression adaptation, not an original Factorio technology. It and complete lab/module/science parity require a separately reviewed follow-up; no new science packs are silently introduced.

| Technology ID | Current prerequisites | Packs | Units × MC work ticks | Unlocks / status |
|---|---|---|---|---|
| `advanced_combinators` | factorycraft:circuit_networks, factorycraft:electronics, factorycraft:chemical_science | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 100 × 600 | factorycraft:selector_combinator |
| `advanced_electronics_2` | factorycraft:chemical_science, factorycraft:automation_2 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 50 × 120 | factorycraft:processing_unit |
| `advanced_material_processing` | factorycraft:steel_processing, factorycraft:logistics | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 75 × 600 | factorycraft:steel_furnace |
| `advanced_material_processing_2` | factorycraft:advanced_material_processing, factorycraft:chemical_science | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 250 × 600 | factorycraft:electric_furnace |
| `automation` | — | factorycraft:automation_science_pack:1 | 10 × 40 | factorycraft:logistic_science_pack |
| `automation_2` | factorycraft:electronics | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 20 × 60 | factorycraft:assembling_machine_2 |
| `automation_3` | factorycraft:automation_2, factorycraft:modules, factorycraft:advanced_electronics_2 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 150 × 1200 | factorycraft:assembling_machine_3 |
| `batteries` | factorycraft:oil_processing, factorycraft:electronics | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 20 × 80 | factorycraft:sulfuric_acid, factorycraft:battery |
| `bulk_inserters` | factorycraft:electric_inserters, factorycraft:logistics_2, factorycraft:electronics | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 150 × 600 | factorycraft:bulk_inserter |
| `chemical_science` | factorycraft:engine, factorycraft:batteries | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 30 × 80 | factorycraft:chemical_science_pack |
| `circuit_networks` | factorycraft:electronics, factorycraft:logistics | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 20 × 60 | factorycraft:constant_combinator, factorycraft:arithmetic_combinator, factorycraft:decider_combinator |
| `effect_transmission` | factorycraft:modules, factorycraft:advanced_electronics_2 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 40 × 120 | factorycraft:beacon |
| `electric_energy_distribution_1` | factorycraft:steel_processing, factorycraft:automation | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 120 × 600 | factorycraft:medium_electric_pole, factorycraft:big_electric_pole |
| `electric_energy_distribution_2` | factorycraft:electric_energy_distribution_1, factorycraft:chemical_science | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 100 × 900 | factorycraft:substation |
| `electric_engine` | factorycraft:chemical_science, factorycraft:automation_2 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 40 × 100 | factorycraft:electric_engine_unit |
| `electric_inserters` | factorycraft:automation | factorycraft:automation_science_pack:1 | 30 × 300 | factorycraft:long_inserter, factorycraft:fast_inserter |
| `electronics` | factorycraft:automation | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 15 × 60 | factorycraft:advanced_circuit |
| `engine` | factorycraft:steel_processing, factorycraft:logistics | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 20 × 60 | factorycraft:engine_unit |
| `inserter_capacity_bonus_1` | factorycraft:bulk_inserters | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 200 × 600 | capacity/effect |
| `inserter_capacity_bonus_2` | factorycraft:inserter_capacity_bonus_1 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 250 × 600 | capacity/effect |
| `inserter_capacity_bonus_3` | factorycraft:inserter_capacity_bonus_2, factorycraft:chemical_science | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 250 × 600 | capacity/effect |
| `inserter_capacity_bonus_4` | factorycraft:inserter_capacity_bonus_3 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 250 × 600 | capacity/effect |
| `inserter_capacity_bonus_5` | factorycraft:inserter_capacity_bonus_4 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 300 × 600 | capacity/effect |
| `inserter_capacity_bonus_6` | factorycraft:inserter_capacity_bonus_5 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 400 × 600 | capacity/effect |
| `inserter_capacity_bonus_7` | factorycraft:inserter_capacity_bonus_6 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 600 × 600 | capacity/effect |
| `lab_efficiency` | factorycraft:batteries | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 30 × 60 | capacity/effect; repeatable |
| `logistics` | factorycraft:automation | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 15 × 60 | capacity/effect |
| `logistics_2` | factorycraft:logistics, factorycraft:electronics | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 40 × 60 | factorycraft:fast_splitter |
| `logistics_3` | factorycraft:logistics_2, factorycraft:chemical_science | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 60 × 60 | factorycraft:express_splitter |
| `modules` | factorycraft:automation_2 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 30 × 60 | factorycraft:speed_module, factorycraft:efficiency_module |
| `oil_processing` | factorycraft:logistics | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 20 × 80 | factorycraft:advanced_oil_processing, factorycraft:heavy_oil_cracking, factorycraft:light_oil_cracking, factorycraft:lubricant, factorycraft:solid_fuel_from_petroleum_gas, factorycraft:solid_fuel_from_light_oil, factorycraft:solid_fuel_from_heavy_oil |
| `productivity` | factorycraft:modules | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 50 × 600 | factorycraft:productivity_module |
| `steel_processing` | factorycraft:automation | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1 | 10 × 60 | factorycraft:steel_plate |
| `transport_belt_capacity_1` | factorycraft:inserter_capacity_bonus_7 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 2000 × 1200 | capacity/effect; sandbox-only |
| `transport_belt_capacity_2` | factorycraft:transport_belt_capacity_1, factorycraft:inserter_capacity_bonus_7 | factorycraft:automation_science_pack:1, factorycraft:logistic_science_pack:1, factorycraft:chemical_science_pack:1 | 3000 × 1200 | capacity/effect; sandbox-only |

## Remaining differences outside 0.26.0

* Complete technology prerequisites/costs, production/utility/space science, module tiers 2/3, lab module inventories/research productivity and 1-kW combinator consumption remain incomplete. Current simplified research and hand-grid substitutions are transparent adaptations, not parity claims.
* Pole material substitutes (iron plate for missing iron sticks, cable/plate compression), some legacy tool/hand recipes and direct vanilla smelting are Minecraft adaptations. No recipe of a newly retired ID is kept.
* Only normal-quality base production is targeted. Quality, nuclear/heat pipes, advanced Space Age machines, spoilage, rails/robots/rockets/pollution and construction tools are absent or deliberately deferred. Stack Inserter is the implemented mechanical exception with sandbox-only progression. All three lifts and all four circuit colors are approved extensions.
* Offshore land-support/source/overhang checks and Minecraft Y/depth reach adapt a two-dimensional shore/mining model. No minimum lake size is invented. Steam has a fixed nominal temperature; finite FE buffer charging is explicitly described in STEAM_POWER.md.
* This audit does not establish TPS or correctness by itself. Executed invariants, live-client evidence and CI ownership belong in VALIDATION.md.

### 0.26.1 output adaptation

Both base drills now auto-deliver through one real front chute to compatible belt/handler/fuel inputs. Existing output slots are a bounded four-stack pending buffer; a missing receiver eventually blocks mining. Factorio's single ground ore is deliberately not represented by unbounded ItemEntity spawning. Minecraft inventory capacity/fuel and shore Y geometry remain explicit adaptations; fluid/production recipes and rates do not change in this patch.

Drill drop-lane correction: near perpendicular lane, right parallel/antiparallel lane. Inserter placement rules remain unchanged; pure rotational and three-speed native checks enforce the distinction.
