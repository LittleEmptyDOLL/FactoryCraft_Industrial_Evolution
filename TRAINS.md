# Rolling stock — 0.31.0 / issue #13

Reference: Factorio **2.1.24 with Space Age**, normal quality. This milestone introduces actual rolling stock on the existing railway graph. Signals, stations, schedules, artillery wagons and automated routes are not implemented.

## Source and units

| Stock | Weight | HP | Braking force | Air resistance | Storage |
|---|---:|---:|---:|---:|---|
| Locomotive | 2000 | 1000 | 10 | .0075 | Three chemical-fuel slots and a separate native-container remainder slot |
| Cargo wagon | 1000 | 600 | 3 | .01 | 40 slots, per-slot item filters and bar |
| Fluid wagon | 1000 | 600 | 3 | .01 | 50000 source units = **500000 mB** |

All cars have source friction .5; cargo/fluid contents do not increase entity weight. Center spacing is seven blocks and bogie metric offsets are ±2. Source maximum speed is 1.2 tiles/source tick, 600kW locomotive power, .6 reverse traction when no fueled locomotive faces travel. Coal is4MJ, solid fuel12MJ with1.2 acceleration/1.05 speed; optional future registered rocket/nuclear fuel IDs retain their source multipliers without inventing production. Vanilla logs are2MJ; other Minecraft furnace fuels use the documented coal-equivalent2500J/native burn tick. The global FE balance is unchanged.

`TrainMotion` applies **three ordered source60Hz steps per Minecraft20Hz tick**. In each powered/coasting step, subtract friction/weight, add source traction/weight if paid, then apply leading-car air drag. Braking subtracts summed braking force/weight instead. No slope gravity or derailment bonus is invented. Source-speed1.2 corresponds to259.2km/h. Runtime reserves30000J per actively powering locomotive per MC tick; a residual below one complete paid MC tick stays owned until refueling. This last partial-tick behavior is an explicit current deviation from source sub-tick burner exhaustion.

Exact recipes: locomotive20 engines+10 circuits+30 steel/4s; cargo10 gears+20 iron plates+20 steel/1s; fluid wagon10 gears+16 steel+8 pipes+1 tank/1.5s. Resource-owning pipes/tanks cannot be consumed. Railway unlocks locomotive/cargo; Fluid Wagon requires Railway+Fluid Handling and200red/green×30s. Both native handcraft and assembler recipes use existing components and research guards.

Pinned sources: [trains.lua](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/entity/trains.lua), [recipes](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/recipe.lua), [technologies](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/technology.lua), [Space Age updates](https://github.com/wube/factorio-data/blob/2.1.24/space-age/base-data-updates.lua), [locomotive acceleration formula](https://wiki.factorio.com/Locomotive). Wiki raw-page access was used where normal pages rejected automated reads. Create1.21.1 `Carriage.travel`, `CarriageBogey` traveling points/coupling anchors and `Train` are technical references for independent wheel positions and ownership, with no imported code/assets or dependency.

## Player controls

Use a stock item on actual rail; look along its intended front. The server chooses metric position/tangent, validates complete loaded clearance, claim permissions, research and occupancy, then transfers exactly one item. Nearby compatible stopped ends snap at seven-block spacing and automatically couple. Placement never creates a train from air or an arbitrary neighboring block.

Use a locomotive to enter. **W/S** apply power in the cab's forward/reverse direction; release to coast, **Space** brakes, **A/D** request left/right at the approaching leading junction, **Shift** exits. A direction reversal waits for a full stop. Only a current canonical rider's intent is accepted; clients cannot submit train positions. Missing rider/input triggers braking. Changing A/D may replace only unoccupied lookahead; even a parked train can revise its next branch. Rear wheels follow the historical chosen route, never a freshly selected branch. Unconnected crossings and different levels never become turnouts.

Sneak-use a locomotive to open fuel; use a wagon for cargo/tank. Native slots (with private guarded menu setters, never a writable public capability), shift transfer, bar/filter/color and couple/uncouple buttons revalidate the current menu, owner/team, distance and halt. Cargo ghost filters sample cursor item types without consuming components or moving actual cargo. Fuel/transported stacks preserve native components. A fluid wagon has one exact compatible identity, including components.

Wrench-use retrieves one portable car with its exact cargo/fluid/fuel/health/settings; placing a filled portable item consumes that sole owner even in creative mode, while ordinary empty stock retains normal creative placement. sneak-wrench uncouples behind that car. Retrieval requires a stopped, unoccupied cab. Environmental rail loss quarantines the train and still permits loaded physical handles to be recovered with their sole resources. Destroyed stock is retained as a zero-health portable wreck with contents; repair/reuse of wrecks is not implemented, and placement refuses to silently heal them.

## Ownership, movement and logistics

Dimension-local `TrainData` schema1 owns train/car UUIDs, car order, route legs, metric head, direction/speed/distance, fuel joules/spent ledger, cargo, native fluid, health, color, filters/bar and portable-shell components. `StockEntity` saves only identity and the native nonowning pose; it never serializes a second inventory or tank. Current rider intents are deliberately volatile. Restart restores paid state and applies fail-closed braking when loaded, rather than resuming unattended throttle.

Movement reserves canonical edge intervals and spatial swept bounds before fuel and position commit. Known end/edge occupancy uses source braking distance; unexpected blocks/entities use quarter-block swept collision checks and a safe refusal. Conservative3×7×4 clearance now includes body pitch in both the rail planner and train runtime; visible modeled stock is approximately2.16×7×3.42. SAT removes false ground collisions from the broadphase pitched box. This conservative clearance can deny tight configurations where the visible body would fit. Train/mob impact damage and complete source collision consequences remain unfinished; obstacles currently stop the train without destroying contents.

Availability requires actual loaded/block-ticking body chunks and valid loaded bed projections; no runtime chunk is forced. Ticket demotion quarantines access immediately; bounded promotion readiness wakes indexed owners. Edge deletion retains last recoverable poses. A maximum64cars/consist and128active-train steps/tick are explicit initial bounds. Dormant consists sleep; there is no global rail traversal per tick. Access guards cache availability per tick/topology epoch, but recheck exact car/projection UUID and halt for every operation.

Stopped cargo exposes guarded native entity `IItemHandler`; existing inserters resolve only the physically adjacent side of a stopped car after native block capability lookup. Fluid wagon sides expose one guarded native tank, with SIMULATE leaving it untouched. FactoryCraft pumps find the physical central/end nozzle; passive pipes never add a wagon to their shared pipeline segment. Pump direction, energy and circuit conditions remain authoritative. A halt transition wakes only nearby pumps. Red/green/blue/white networks and three conveyor-lift tiers are unchanged.

## Client implementation and diagnostics

Original cached modeled locomotive, open cargo body and rounded tanker include independently posed trucks, rotating wheels, couplers, exhaust, cab openings/interior, handrails and emissive lamps. Body position uses native bounded tracking/interpolation; visual bogie/wheel metadata is published every three ticks with immediate stop/color/health changes, interpolated locally. Native sparse positional interpolation can cut tight curves at high speed; route-aware render interpolation remains future acceptance work. A procedural diesel/rolling sound loop and smoke are client-only; no item/fluid simulation runs on the client. No imported art/audio or segment entities are used. Real visual/audio acceptance is recorded separately in [validation/v0310](validation/v0310).

`/factorycraft trains` reports canonical train/car counts, active steps, world reads, projection update requests and measured server-adapter nanoseconds. Projection update requests are not actual packet counts. `trainqa`/`trainstockqa` are creative-OP-only **marked isolated network-world** fixtures, reserve one-shot markers before setup, declare finite administrative inputs and refuse reset/refill. Their explicit diagnostic chunk tickets are not a production movement feature.

Final local evidence:297JUnit, full538native, plus focused parked-turnout native success;539registered cases remain for CI. The full acceptance checklist, actual checks and unresolved visual/multiplayer/performance scenarios remain explicit. Do not infer a complete issue13 pass from compilation or kernel tests alone.
