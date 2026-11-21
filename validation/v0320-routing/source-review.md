# Source route review — 22 November 2026

Factorio2.1.24 + Space Age remains the requested mechanical target. Public wiki references were read for this change; they describe behavior, not licensed proprietary implementation code.

* Exact numeric prototype: https://github.com/wube/factorio-data/blob/2.1.24/core/prototypes/utility-constants.lua#L318
* https://wiki.factorio.com/index.php?title=Railway/Train_path_finding&oldid=215144
* https://wiki.factorio.com/index.php?title=Train_stop&oldid=217465

| Behavior | Existing Create-derived boundary | This FactoryCraft change / limits |
|---|---|---|
| Directed metric paths | MIT Navigation-derived RailPathSearch, pinned37ea4afc in existing notices | Retain sole search, node budget, canonical fractional UUID edges and physical TrainPath |
| Stop/circuit/train penalties | Rules callback | New source scalar table and nonmutating contextual prefix adapter; not the closed-source Factorio A* |
| Occupied block distance discount | Published logical RailSignalBlocks | Count actual visited region transitions, sum cached physical spans as region length, charge an encountered region once over consecutive edges; branching-region lengths and one cheapest-prefix label per directed edge are explicit Minecraft/Dijkstra adaptations |
| Source wait surcharge | Existing HeldSignal protects paid motion | .1 per60Hz source tick becomes .3 per20Hz waiting tick; persist nonzero ticks on sole train |
| Chain repath | Existing two-ended bounded TrainRouteJob and safe physical scout |5s with several same-name stops,30s with one; stop first and retain all old commitments until successful replacement; retain entered-chain safe exit |
| Source stopped manual/station/arrival costs | Canonical indexed occupants only | Observe real passengers, owner mode/state and schedules; never access another resource copy or load chunks |
| Origin-priority, pass-through, groups/interrupts | Not part of this slice | Remain in Issue15; no completion claim |

Costs are preferences, not passage authorization. Direction, actual loaded attachment, whole-body occupancy, spatial hulls and atomic logical claims still decide safety. Prospective reservations without an actual train body do not receive the occupied-train surcharge. Stop priority alone does not reroute an existing cruising commitment. A station at the exact search origin is excluded by the existing strictly-ahead point index; the destination is excluded by UUID rather than excluded for all searches on the same edge.

No new Create code/art was copied. Existing licensed search was extended in place; original notice and bundled MIT text remain required.
