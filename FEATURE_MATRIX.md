# Feature matrix — 0.6.1

**Implemented** refers only to the explicit scope in the row. **Partial** is not the entire requested subsystem. Evidence: VALIDATION.md. This release delivers stage 1 and a playable part of stage 2; stages 3–8 are not complete. Version 0.6.1 adds large configurable surface fields and centered machine item placement while preserving 0.6.0 structures and finite stock. Stage 3 remains partial.

| Area | Implemented scope | Status / remaining work |
|---|---|---|
| Core | Java21/MC1.21.1/NeoForge21.1.252, ModDevGradle, registries, server config, creative tab | implemented; build + dedicated startup |
| UI languages/assets | EN/RU, original generated textures, active-machine texture animation, articulated inserter, item rendering | implemented first-slice visuals; not final art |
| Persistence/network | inventories, schema-2 lane positions (reads schema 1), hand, progress/recipe/fuel/energy; vanilla BE/menu packets | implemented; NBT and separate-process disk reload tested |
| Belts | 2 lanes, 3 speeds, residual travel, cross-block spacing, fair merges, full rings, congestion, sleeping/wakeup | implemented transport kernel; curves, advanced belt types and full-scale world profiling remain |
| Splitter | two-block 2×1 geometry, two forward exits, per-lane balancing and safe blocked-output fallback | partial; two shared internal lanes, full dual-belt throughput and priorities/filters absent |
| Advanced belts | none | planned: underground, filters, priorities |
| Inserters | mechanical, animated, source/destination capabilities, persistent hand, backpressure | partial; fast/long/bulk/stack/filter/energy/circuit models absent |
| Resources | finite per-tile iron/copper/coal/stone reserves, repeated manual harvesting, BE-aware large surface fields across chunks, radius/spacing/stock-range TOML and per-resource datapack settings | partial; uranium/late resources and world setup GUI absent |
| Mining | finite electric drill, 3×3 footprint, 5×5×8 search, energy and configurable duration | implemented first drill; finite oil pumpjack implemented; burner/advanced drills planned |
| Production | electric furnace + assembler + 3×3×3 industrial furnace, 4 input/4 output slots, selection, JSON recipes, FE/progress/GUI | partial; fixed-port mixed fluid/item machines added; modules and other advanced buildings remain planned |
| Components | iron/copper plates, gears, wire, circuits; survival crafting bootstrap | partial; plastic, sulfur and advanced circuits added; processors/engines/batteries/science/rocket components remain planned |
| Power | coal generator, spatial index, budgeted loaded-node snapshots, immediate split quarantine, merge/split, bounded distribution, FE input | partial; steam engine (up to 120 FE/t) and guarded FE output implemented; manual wires/graphs, nuclear/solar/accumulators/heat remain planned |
| Multiblocks | one item places every full-sized machine; centered green/red client preview; footprint validation + NeoForge cancellation, shared ports, one machine drop, creative/pick-block, generated orphan cleanup, new assembly schema 3, inactive-chunk pause, disk reload | all 13 existing large types implemented; geometry/height table in MACHINE_DIMENSIONS.md; layout is fixed in code, not a datapack pattern system |
| Fluids | six-way pipes, tanks, directional electric pumps, native water intake, buckets/portable filled blocks, component-sensitive NeoForge fluid capabilities, cached active-edge graph, GUI and restart conservation | implemented first fluid slice; see FLUIDS.md; underground pipes/external adjacency discovery/temperature remain planned |
| Thermal/fluid recipes | 3×2 fueled boiler, container-only steam, steam engine, backpressure, fuel remainders, portable full state, menus, sized fluid ingredients/tags and JSON boiling recipes | implemented water → steam → pole-powered factory; full thermal multiblocks and counted mixed item/fluid chemistry implemented; advanced refining/cracking/heat remain planned |
| Petroleum | finite BE-backed Overworld deposits with preserved exhaustion, electric pumpjack, 100 crude → 45 gas refinery, coal + gas → plastic, gas + water → sulfur, original resources/menus, atomic inputs/outputs and portable controller state | implemented first petroleum chain; heavy/light oil, cracking, lubricant, acid/batteries and sampled world distribution remain planned |
| Research/progression | none; first-slice crafting unlocked by acquiring vanilla iron | planned: teams, labs/packs, tree GUI, recipe locks, infinite research |
| Modules/beacons/quality | none | planned |
| Railways | none | planned: independent rails, wagons, signals, schedules, routing |
| Robots | none | planned: logistics/construction/charging/task allocation |
| Blueprints | none | planned: versioned import, selection, placement, undo/redo/book |
| Circuits | none | planned: red/green graphs, signals, combinators and controls |
| Pollution/enemies | none | planned: diffusion, absorption, evolution, original enemies/raids |
| Combat | none | planned: turrets/artillery/ammunition/armor/radar/shields |
| Vehicles | none | planned: car/tank/construction/remote control |
| Space Age | none | planned as independent module: rockets/platforms/asteroids/quality/four planets |
| Recipes/config/datapacks | counted ingredients, tag support, reload, JSON ores, server settings | implemented first-slice extension points |
| GUI/statistics | machine inputs/outputs/recipe preview/progress/energy/assembly status; `/factorycraft profile` and `/factorycraft power`, fluid gauges and `/factorycraft fluids` | partial; recipe browser tree, graphs, circuit/coverage panels absent |
| Multiplayer/server | server authority, validated menus, two viewers cannot duplicate output, dedicated startup | partial; actual two-client network play not exercised |
| Chunk lifecycle | unload pauses/registers topology; contents survive callbacks; separate-process disk persistence | verified callbacks + disk reload; natural chunk-distance unload scenario still manual |
| Performance | active/sleeping lanes, cached belt links, reused carriers, no per-item Entity, cached pole graph, 10000 two-lane segment kernel test | partial; profiled hot-loop/dirty-notification allocations reduced; individual machine/inserter tickers remain |
| Required 10000/1000/500 load | isolated real dedicated world, all belts move, all producers return results, all inserters deliver; shared grid split/rejoin; item/FE ledger, JSON/JFR and process reload | implemented repeatable server workload; finite prefilled stocks, no connected clients in local result; not a universal TPS/FPS guarantee |
| Compatibility | standard NeoForge item/FE/fluid capabilities, steam bucket and engine FE extraction and recipe tags | partial; JEI/EMI and other mod interoperability untested |
