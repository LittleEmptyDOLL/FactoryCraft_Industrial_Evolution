# Conveyor logistics — 0.12.0

Minecraft 1.21.1 / NeoForge 21.1.252. Factorio 2.0 remains the rate/lane model; slopes and lifts adapt it to Minecraft's three dimensions. No proprietary assets are used.

## Building and controls

* Ordinary basic/fast/express items automatically form directional straight or quarter-circle sections. A neighbor is an input only when its output actually points into this cell. One side input without a rear input forms a curve; multiple inputs use the existing straight side-loading/merge rules. Normal wrench use preserves facing rotation.
* Sneak-wrench cycles **automatic → horizontal → up → down** on ordinary belts. A horizontal receiving belt takes precedence over elevated neighbors. Otherwise a compatible receiving cell one block above/below selects an incline when its physical clearance is free. Up/down sections reserve a generated cell above/below the controller, respectively. Both cells are removed together; a reserve cannot replace terrain or another machine. Clear the space first.
* Lift items exist at all three tiers. First use selects the input cell and direction. Second use targets a height (the target's X/Z do not move the column) and uses your looking direction for the exit. Aim at a scaffold/wall beside the column to choose the height. Green/red outlines use the same all-cell plan as server validation. Sneak-use cancels the selection. `conveyorLiftMaxHeight` in the server TOML defaults to 16 (allowed 1–32). Height may be positive or negative, never zero. One item creates the entire column; lifts require no FE.
* Wrench an **empty** lift to rotate its exit; sneak-wrench packs the whole structure. A portable occupied lift must be placed with its saved signed height and relative exit turn. This prevents changing the length underneath stored items. Breaking any cell returns one portable controller containing both lanes, not loose duplicate contents. Creative destruction removes the structure without dropping it.
* Underground items exist at all tiers. Place the input, then place a terminal ahead on the same horizontal axis facing the same direction; the second terminal chooses output automatically. Sneak-wrench changes an empty terminal's input/output role; normal wrench rotates it. Pairing requires the same tier, height and facing and mutual nearest matching terminals. The first same-tier/same-facing terminal blocks further searching, including a terminal with an unsuitable role. A populated input keeps its original route length until drained; moving its partner to a different distance cannot resize stored transit.

## Underground distance model

| Tier | Hidden cells between terminals | Maximum endpoint separation | Full path at maximum |
|---|---:|---:|---:|
| Basic | 4 | 5 blocks | 6 blocks |
| Fast | 6 | 7 blocks | 8 blocks |
| Express | 8 | 9 blocks | 10 blocks |

The path includes both exposed terminal tiles. Items travel its actual length at the tier's surface speed. Intervening cells are **never read for clearance, removed or replaced**. Stone, machines, air and perpendicular underground routes may occupy them. This is a virtual Factorio link, not an excavated Minecraft tunnel. Ordinary belts can still be built in real tunnels.

The input exclusively owns both long lanes. The output owns no transport node or inventory; its guarded capability forwards only arriving items to the live paired input. Breaking the output leaves all transit at the input, frozen. Restoring the matching output at the original distance resumes it. Breaking the input drops its items once plus the terminal item. An unloaded end freezes transit without forcing its chunk. Output capabilities retained by other machines reject operations after unload/demolition.

## Simulation and compatibility

`BeltLane` and `BeltTransport` remain the single transport kernel. Nine simultaneous microsteps per Minecraft tick move 1/2/3 integral units, with 96 units per block and 24-unit minimum center spacing. Nominal two-lane throughput remains 15/30/45 items/s. Kernels reserve all transfers before moving ownership, including filled cycles and merges. Straight/splitter behavior retains its original metric and schema.

`BeltRoute` separately supplies metric lane lengths, positions and tangents. Quarter-circle inner/outer paths are 42/109 units, slopes 136, underground `(separation+1)*96`; lift lengths include both bends and the exit twist. Capacity follows actual lane length, not four hard-coded slots. Rendering interpolates server snapshots along these paths and never moves resources. Underground interior items are hidden. No moving resource uses a Minecraft Entity. Item entities are used only for ordinary demolition drops.

`BeltSimulation` caches node links. World lookups/pair searches occur on placement, rotation, load/unload or structural changes, never per microstep. Long-route position packets are bounded to once per four ticks; initial/topology packets remain immediate. Auxiliary cells store only controller identity and baked-geometry data. Invalid/orphaned generated cells are removed without creating items or force-loading the controller.

Existing schema-2 ordinary and schema-3 splitter NBT and legacy normalized positions remain readable. New `Route` metadata defines lengths before restoring integral `Units`; default flat paths add no fields. Growing/shrinking a live route anchors positions at its output and preserves order/spacing. A crowded old corner cannot safely fit four carriers into a 42-unit inner lane: its previous shape is retained until enough space exists, then the cached new shape applies. Items are never truncated to force migration. A completely packed old ring may need room made manually. Unchanged legacy flat paths retain their exact diagnostic NBT hash.

Inserters sample the last 24 units at physical outputs, including lift heads and underground terminals. Middle lift cells are not inventory ports. Splitter track-local capabilities and lane balancing remain supported.

## Reproducible checks and limits

`/factorycraft transportworks` builds a finite 32-iron/32-copper chest route through all three belt speeds, curves, a slope, solid-filled virtual underground and a lift. Its output chest is offset `(13,5,6)` from the first equipment layer. It uses real inserters and transport, with no injected intermediate/result items.

JUnit covers metric paths, all speed tiers, packed nonuniform rings, throughput/delay, resizing and long-position round trips. Native tests cover directional curves, slopes/clearance, up/down lifts, jams, protected two-click placement, portable identity, physical inserter sampling, stale capabilities, terminal unload/destruction/replacement, crossings and full-chain delivery. The strict separate-JVM checkpoint at `(1007,70,1007)`, `(1016,70,1007)`, `(1007,70,1023)` and `(1023,78,1023)` retains 160 saturated carriers across four horizontal chunks. Never reseed a partial/failed checkpoint. Old checkpoints remain intact.

Limits: natural player-distance unload and two real network clients remain manual QA; callbacks and full process reload are automated. The large legacy benchmark verifies compatibility, not 10,000 mixed lifts/underground pairs. Advanced splitter priorities/filtering, belt dragging, custom lift dimensions above the configured limit and final decorative artwork remain future work.
