# Issue15 Gate2 — prepared no-wait waypoints

An entry with no wait conditions is a waypoint. Remove its last condition in the schedule editor to use it; adding a condition restores an ordinary stop. The server accepts this only from the current authorized nearby menu. Normal wait entries retain their existing behavior.

Before arrival, the existing Create-derived bounded search prepares a direction-locked continuation from the future leading-nose pose. A complete result extends the canonical historical TrainPath without altering source traction, braking, fuel, or any carriage's resources. A window contains at most64future markers, prepares at most8legs per call within the existing256nodes/train and2048nodes/world budget, and extends at least128blocks or stopping distance + whole-body length +32. This is a Minecraft adapter, not a claim about Factorio's private pathfinder.

Prepared targets hold actual stop-limit leases; references to the same train/stop count once. Lookahead yields new slots to indexed stopped dispatch contenders. A crossed marker atomically promotes the next commitment before native callbacks, without station-wait timers, while retaining entered logical blocks and chain exit leases until the tail clears. Unknown, unavailable or full continuations do not authorize motion beyond the last verified endpoint. A genuinely blocked waypoint may stop; it then retains its physical origin until actual movement and dispatches normally, including a legal stopped reversal. Positive-distance search prevents a single no-wait marker from repeatedly selecting itself at zero distance.

Only default-omitted `Upcoming`, `WaypointDirection` and stopped `DeparturePass` metadata are added to schema1; resources still live once on TrainData. The installed historical path supplies geometry. Restart reconstructs derived future slots; malformed ordering/directions are quarantined. Rename/removal/manual mode invalidate only appropriate future commitments and never release physical collision protection. Dynamic device loss is conservatively fail-closed; seamless continuation after arbitrary mid-chain infrastructure destruction is not promised.

Temporary go-to/cancel/return, the full component-aware wait editor and operational Gate3 groups/shared named interrupts/typed wildcards remain in the SAME Issue15. No0.33 or full0.32completion claim. New receipts: validation/v0320-pass.

---

# Issue15 Gate2 — priority at departure and occupied origins

A train whose wait conditions have completed continues to occupy its source station while the next destination is full, unreachable or still being searched. When a route is ready it can reserve the next station while physically remaining at the origin, including with no fuel. The origin is released on the first real displacement. Each station counts that train once; a single-stop loop cannot count it twice.

Competing ready trains use their origin station's priority, then persistent FIFO ready order and a stable UUID tie. FIFO and priority0 for a request without an origin are explicit FactoryCraft adapters; no hidden Factorio tie algorithm is claimed. Slower bounded searches retain their rank against faster completed searches. A stale search restarts without a rank gap. Actual no-path searches yield for one second before retry; unavailable contenders leave the derived queue. Candidate sets are conservative until a search resolves, then narrow to the selected physical stop. Independent target/name/scope indexes are not a global railway lock.

Changing the source priority affects pending dispatch; destination priority/limits/disable do not evict an already committed incoming train. A renamed same-scope origin retains its physical occupancy; removing either endpoint releases only that endpoint's reference. Manual mode and retirement clear automation commitments, while physical collision protection remains. Direct station circuit reads show cargo/train ID at the actual dock, not at the prospective target. Availability recovery reopens genuinely loaded idle ports.

Origin UUID/pose/direction and nonzero FIFO order are saved once on the existing train, without copying cargo/fuel/fluids. New-origin checkpoint fields are omitted by default. Existing schema1 resources remain intact; older pending departures without a saved origin are not guessed from nearby tracks. Native/restart receipts are in validation/v0320-dispatch. No new graphical-client pass is claimed: renderer, UI and protocol are unchanged.

Origin dispatch is now followed by the prepared pass-through extension above. Next in Issue15: temporary go-to/cancel/return and complete component-aware waits, then operational groups/named interrupts/typed wildcards/editors. Full0.32remains unfinished; no0.33.

---

# Issue15 Gate2 — route costs and stopped repath

Pathfinding now prices intermediate directed stops (2000), circuit-forced red (1000), actual occupied-region length/distance and canonical train state. Stopped manual trains cost7000 without a real passenger or2000 with one; stopped station trains cost500 or1000 for a terminal schedule, arriving trains100, no-path trains1000. Signal waiting adds0.3per Minecraft20Hz tick to100 (the source rate is0.1at60Hz). The destination UUID is excluded from passing-stop costs; one logical region continuing over several saved physical edges is not repeatedly charged. No extra generic1000cost is imposed just for an unoccupied prospective lease.

A really stopped train at a refused chain may search again after5seconds when multiple same-name stations exist, or30seconds for one. Ordinary held signals retry at a bounded5seconds; this polling interval is a FactoryCraft adapter, not a claim of exact source event timing. Searching retains the original incoming station slot and all existing claims; failure leaves the old route intact. A successful complete route acquires its replacement slot before replacing future physical legs. An entered chain's outstanding safe-exit leases prohibit reconsideration until covered by the body. Preserving HeldSignal on a blocked replacement prevents paid creep. No movement is issued while a replacement job is pending.

Nonzero `SignalWaitTicks` is additive/default-omitted schema1 metadata on the existing train, never another owner. Unknown/unloaded topology/physical attachments are not priced as free track. Source physics/fuel, full-body claims, component-exact cargo/fluid, ordinary reach and all four logic colors remain unchanged. Prefix pricing is nonmutating and budgeted by the existing2048nodes/tick shared frontier.

**Adaptations:** this remains the existing Create-derived directed Dijkstra, not proprietary Factorio A*. A branching region's metric length is the cached sum of its physical spans; one cheapest prefix is retained per directed physical edge, so path-dependent block-discount weights are not claimed to reproduce the source's hidden global optimizer. Route costs observe train states on expansion and all prospective movement is rechecked by the existing atomic scout. Terminal classification currently recognizes distinct schedule station names, without recursively searching every other schedule entry during pricing. Origin fairness and prepared pass-through are implemented in the later extensions above. Temporary stops, complete component-aware wait editing and Gate3 remain pending in Issue15.

---

## Issue15 traffic acceptance and profiler

An explicit native automatic workload is available with `-PtrainControl=automatic`; see BENCHMARKS.md for fresh-world commands and limits. It uses two-ended3/3/8/16/32/64/8/16consists and real named terminals/signals, finite fuel/cargo/tankers and actual chunk lifecycle. The original manual8/148fixture remains unchanged. The automatic fixture's balanced locomotive orientations prove substantial travel both ways instead of relying only on a nonzero velocity sample.

New native shared-junction, overpass, route deletion/recovery, maximum-speed congested-entry and occupied-claim independent-JVM checks are recorded in validation/v0320-traffic. They retain physical interval/hull protection, single resource ownership and same-name stop commitment rules. No player controls, recipes or production train motion were changed in this slice. Gate2/3runtime features and the broad visual matrix remain in Issue15.

---

# 0.32.0 — basic automatic named-stop service

After real source braking stops an automatic train at a refused signal, its optional saved `HeldSignal` identity prevents repeated tiny paid acceleration/braking attempts. Passage is retried by the existing bounded scout, but stopped traction stays unpaid until the selected route is actually available. Manual mode and a successful passage scout clear this waiting identity; a replacement search preserves it until passage is proved. No fuel, cargo, motion or claim owner is copied. Unknown topology/body reconstruction affects only dependent regions, not every automatic train in the dimension.

Open a stopped locomotive's ordinary inventory, then **Schedule**. Add/reorder named stops and explicit waits, save, and switch Manual/Automatic. Automatic motion does not require a rider; entering a train does not stop it. Explicit driver input takes over in Manual and cancels prospective automatic/stop commitments while preserving physical body claims and cargo. Saving a changed schedule deliberately switches to Manual before replacing the definition.

Stations use names within the train's research/team scope. The dispatcher chooses the highest reachable destination priority, then metric cost and stable UUID ties. A station limit counts each approaching/docked train once; reducing it or disabling a stop preserves an already committed arrival. Missing paths and saturated destinations are distinct waiting states and do not skip schedule entries. Rename/removal invalidates the indexed commitment and brakes before re-dispatch. Reverse departure requires an actual outward-facing locomotive at that end.

Time/inactivity advance only while loaded and really stopped at the destination; inactivity watches actual cargo changes and excludes locomotive fuel. Native cargo/filter/component stacks and tanker fluid remain owned by TrainData. Waits use AND groups separated by OR. Fluid thresholds are Factorio units multiplied by10 for comparison to native mB, never truncated division. The native adapters also observe full/empty/has cargo, typed item/fluid counts, fuel, passengers, named-stop availability and known circuit input. A malformed/unavailable circuit sample is unknown, including for NE. Full component-specific condition editing and comprehensive advanced-condition acceptance remain pending.

Regular signals split logical regions at metric positions; opposite sides enable two-way passage. Chains reserve the selected route through a regular exit with enough space for every car. A signal's global blue indication is distinct from permission for a particular chosen route. Actual interval/hull collisions still protect manual traffic. Paused/unloaded trains do not spend movement fuel or simulate offline cargo transfers. Head/nose targeting uses the source braking recurrence and0.06-block alignment tolerance.

This historical basic checkpoint required explicit waits. The later extensions above add origin-priority dispatch and real empty-condition pass-through. Temporary go-to actions, synchronized groups and shared named interrupts/wildcards remain **not yet playable**; definition codecs/pure wildcard helpers do not implement them. Issue15 remains open; no0.33 work is included. See validation/v0320 for precise actual checks and limitations.

---

# 0.31.3 — momentum-preserving living contacts

A lethal ordinary mob impact no longer unconditionally zeroes speed. Server contact detection orders real swept entries, commits full reservations/fuel/last-clear pose, invokes native impact hurt with driver/direct-stock attribution, and checks both actual health and the final cancellable death event. Surviving, immune, allied/pet/PvP-protected or cancelled targets stop the train; dead bodies and item drops do not become walls. Wall/stock/nonliving structural contacts keep their distinct blocking model.

**Documented Minecraft adapter, not the closed-source Factorio collision formula:** use public source masses and5movement-energy/HP (6for fluid cars). Kinetic energy is weight×sourceSpeed²/2. Actual removed native health+absorption divided by0.2 gives sourceHP; only the finite corresponding energy is charged for an accepted lethal hit. Remaining speed is sqrt(2remainingEnergy/weight). Recoil uses the charged sourceHP through the existing50flat/60%impact resistance. Resistance has Factorio's nonzero low-damage tail: a10HPcow removes50sourceHP and inflicts0.2sourceHP recoil, rather than half the entire moving train's energy. Resistant survivors absorb the remaining motion. Cancelled hurt has no self-damage debit. This does not make stock invulnerable or infer a kill from attempted overkill.

Remaining movement continues in the same Minecraft tick. The interrupted60Hz substep retains its remaining time, later substeps reapply the original paid recurrence, and fuel is charged once for the20Hz tick. No-contact source recurrence/max speeds remain unchanged. A bounded16-contact loop resumes dense crowds on later ticks with residual velocity; no permanent target immunity is stored. Native callbacks that change canonical owners/path/pose abort the old advance; retired owners cannot be billed/moved again. Each continuation resamples the world, including newly created walls and chunk unavailability. All train/car IDs, inventories/fluids/fuel, health, paid phase and schema1 remain canonical/persistent.

New checks include three-mob source-cap travel order, cancelled hurt/death, heavy survivors, death-created walls, source retirement/reentrant callbacks, a dense crowd budget, curve+grade contacts and exact rear-wagon reverse-impact JVM checkpoints. A continuing impact immediately restores the projection's positive speed through its visual throttle: the callback's temporary zero must not escape as a false HUD/audio stall. See [validation/v0313](validation/v0313). Existing0.31.2 reversed-fractional interval protection and every earlier finite production chain are retained.

---

# 0.31.2 — exact canonical edge intervals

`TrainPath` keeps a historical metric origin per leg. A legal fractional edge at nonzero origin may satisfy `(begin + length) - begin > length` by an ULP. In0.31.1, reverse mapping changed that excess into a negative minimum and `TrackOccupancy.Interval` correctly rejected it, crashing the server tick. The user-supplied report identifies precisely this call path; a deterministic test fails on f17c22f before the correction.

`TrackOccupancy.span` now uses exact0/length at clipped leg endpoints and bounds the global-to-local subtraction to the physical edge domain before reversing. It does not relax `Interval` validation, swallow errors, reduce speed, release reservations or skip collision work. Full-edge claims use exact endpoints in both directions; other trains still cannot claim an occupied boundary. Train schema1, UUIDs, finite fuel, cargo/fluid components and paid phase are unchanged, so existing0.31.1 worlds require no conversion.

Native reverse-curve crossing/restart evidence and precise coverage: [validation/v0311-intervalfix](validation/v0311-intervalfix). The historical0.31.1 report below predates the supplied crash stack. No client rendering or protocol changes, extra runtime tickets or global performance claims.

---

# 0.31.1 precise stock geometry and hotfix validation

The single `StockGeometry` definition covers the existing original meshes: X±1.09m, length7m, wheel bottom.02m; locomotive roof3.42m, cargo2.73m, tanker3.17m. Collision uses half-width1.125m (3.5cm side allowance), half-length3.5m, bottom.005m and per-kind top3.45/2.76/3.20m. It preserves source seven-metre spacing, full chassis/roof/couplers and grade pitch. No model rescaling, speed cap or collision disabling was used.

`StockEnvelope` provides scalar OBB/AABB and OBB/OBB SAT and local-frame ray clipping. Entity and spatial-grid AABBs discover candidates only. Per-car staged `SweptHull` samples retain exclusive route/headway/junction rules and reject only actual oriented overlaps. Flat longitudinal motion has exact overlapping endpoint coverage; curves/grades retain≤.125m stages and an8.5cm reservation allowance. `MotionHull` handles block/entity contact and rail construction with continuous translational SAT plus a bound from the measured change of body axes, avoiding per-voxel temporary hulls. Actual VoxelShape sub-boxes are cached per contact operation, then retested at every relevant pose; fences originating in neighboring voxels are included.

Minecraft1.21.1 section discovery originally searches only2m horizontally around a query and can miss the end of a7m car across a16m section boundary. A shared bounded margin (4.5m horizontal/above origin,1m below origin) covers every allowed yaw/1:4grade. Only existing accessible sections are visited; EntitySection retains the original query-box filter, followed by exact stock SAT/ray checks. No chunks are loaded or global entity lists scanned. A native tail-at-section-edge regression and a pure all-yaw/grade bound guard cover this adapter.

Native Minecraft entity motion, `noCollision`/dismount checks and ProjectileUtil picking have guarded stock-specific narrowphase adapters. Other entities retain vanilla behavior. Stock interactions and direct melee require a real chassis ray within ordinary reach; manual FactoryCraft hitscan/flame also clip the hull. Projectile/blast damage still passes through the sole canonical owner. The discovery AABB is not an authoritative body. Arbitrary third-party code that directly treats bounding boxes as final hits is outside this adapter's guarantee; no external vehicle mod is required.

Resources/NBT schema stay unchanged. Train/car UUIDs, component-exact cargo/fluid/fuel and ownership remain in TrainData; projections carry no duplicate contents. Missing/unloaded track pauses at the paid valid pose without runtime tickets. Existing split/merge/wreck/impact/native protection tests remain enabled.

Real original-main and changed-code client drives crossed120km/h without the reported crash; logs, finite ledgers, EN/RU client/observer screenshots and the exact unverified acceptance items are in [validation/v0311](validation/v0311). **No actual crash stack was supplied or reproduced, so this release does not assert a discovered crash cause.** The broader issue13 client interpolation/template/repair limits below remain open. No signals/stops/schedules/0.32.

---

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

Movement reserves canonical edge intervals and staged oriented bodies before fuel and position commit. Known end/edge occupancy uses source braking distance; unexpected blocks/entities use continuous flat translation and≤.125m curved/grade intervals, then a last-clear-pose impact commit. Runtime and planner use the shared2.25m-wide,7m-long pitched hull with per-kind height (visible locomotive approximately2.18×7×3.42). AABBs discover candidates; SAT/subshape tests decide contact. The single full-body hull intentionally does not model every underframe/cab opening as triangle-level collision. A prevented interval/spatial conflict has no impact damage. Unexpected physical contacts stop at the last clear sample: fuel, distance and canonical owners settle before native hurt/death callbacks. Missing/unloaded track never consumes movement fuel or damages an unseen target.

`TrainImpact` is an explicit bounded3D adaptation, not a claim to reproduce Factorio's closed-source collision solver. Raw movement energy is `sum(weight) * sourceSpeed² / 2`; source `energy_per_hit_point` is5 for locomotive/cargo and6 for fluid stock. Against an entity, half the raw budget goes to the obstacle and half to the contacting car; an immutable Minecraft wall absorbs the whole vehicle budget. Source impact resistance50/60% applies once. Native impact DamageSource preserves direct stock and current driver attribution, PvP/team/pet protections. All impacts halt for the current Minecraft tick; a cleared obstruction permits later paid motion. Blocks are not griefed. There is no invented fuel/velocity at zero energy or damage for a reservation that prevented contact. Prototype semantics: [VehiclePrototype.energy_per_hit_point](https://lua-api.factorio.com/2.1.24/prototypes/VehiclePrototype.html#energy_per_hit_point).

Canonical stock now participates in manual FactoryCraft hitscan, explosive direct/area damage and flame flights/burning. Automatic defenses still use their existing enemy/friendly-fire policy. Detached/duplicate/warped handles and unavailable owners cannot redirect damage. On death every remaining car is reassigned before callbacks. A cancelled native wreck spawn retains one persisted `TrainData.Wrecks` claim; an eight-attempt rotating queue retries only loaded entity chunks. Accepted native items replace the claim, never a second inventory/tank owner. Orderly saves/reloads are supported; this is not a cross-file crash journal.

Availability requires actual loaded/block-ticking body chunks and valid loaded bed projections; no runtime chunk is forced. Ticket demotion quarantines access immediately; bounded promotion readiness wakes indexed owners. Edge deletion retains last recoverable poses. A maximum64cars/consist and128active-train steps/tick are explicit initial bounds. Dormant consists sleep; there is no global rail traversal per tick. Access guards cache availability per tick/topology epoch, but recheck exact car/projection UUID and halt for every operation.

Stopped cargo exposes guarded native entity `IItemHandler`; existing inserters resolve only the physically adjacent side of a stopped car after native block capability lookup. Fluid wagon sides expose one guarded native tank, with SIMULATE leaving it untouched. FactoryCraft pumps find the physical central/end nozzle; passive pipes never add a wagon to their shared pipeline segment. Pump direction, energy and circuit conditions remain authoritative. A halt transition wakes only nearby pumps. Red/green/blue/white networks and three conveyor-lift tiers are unchanged.

## Client implementation and diagnostics

Original cached modeled locomotive, open cargo body and rounded tanker include independently posed trucks, rotating wheels, couplers, exhaust, cab openings/interior, handrails and emissive lamps. Body position uses native bounded tracking/interpolation; visual bogie/wheel metadata is published every three ticks with immediate stop/color/health changes, interpolated locally. Native sparse positional interpolation can cut tight curves at high speed; route-aware render interpolation remains future acceptance work. A procedural diesel/rolling sound loop and smoke are client-only; no item/fluid simulation runs on the client. No imported art/audio or segment entities are used. Real visual/audio acceptance is recorded separately in [validation/v0310](validation/v0310).

`/factorycraft trains` reports canonical train/car counts, active steps, world reads, projection update requests and measured server-adapter nanoseconds. Projection update requests are not actual packet counts. `trainqa`/`trainstockqa` are creative-OP-only **marked isolated network-world** fixtures, reserve one-shot markers before setup, declare finite administrative inputs and refuse reset/refill. Their explicit diagnostic chunk tickets are not a production movement feature.

The continuation's exact checks and measured real-server workload are recorded in [validation/v0310-completion](validation/v0310-completion). Historical client evidence remains in validation/v0310. Unchanged limits include source fixed A/B templates/diamonds, repair of portable wrecks, fractional burner exhaustion and high-speed client interpolation/art review; signals/stops/schedules remain outside0.31.
