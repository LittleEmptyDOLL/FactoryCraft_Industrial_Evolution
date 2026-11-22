# Issue15 Gate2 — origin dispatch and occupied departure leases

Mechanical reference: Factorio2.1.24 + Space Age, https://wiki.factorio.com/index.php?title=Train_stop&oldid=217465. Same-name destinations use destination priority/path cost. Waiting trains competing for a destination use their originating station's priority. Existing incoming trains survive reduced limits/disable. Train counts include approaching and docked trains.

|Source behavior|Create boundary reused|FactoryCraft implementation / adapter|
|---|---|---|
|Origin priority|Existing MIT-derived Navigation/RailPathSearch and native TrainRouteJob|New original indexed per-physical-target request arbitration; an unfinished high-priority search blocks a completed lower one, then narrows to its actual destination|
|Bounded search|Existing resumable directed frontier|Per-job cooperative allowance defaults256 and respects the shared world budget; a1-node allowance tests genuinely slower native work, without manufacturing routes or touching motion|
|Docked+incoming count|Existing canonical TrainData and RailStopSlots|One source reference plus one incoming reference; each station counts each train once. Source remains until first actual displacement, including no-fuel/blocked/full waits|
|Equal priority|No source hidden tie algorithm claimed|Persistent monotonic FIFO order, UUID final tie; this is the explicit deterministic FactoryCraft adapter|
|Origin display/circuits|Existing nonowning four-color contact|Read actual metric origin while still docked, never the next prospective target; unavailable physical train contents remain unknown|
|Chunk lifecycle|Existing availability epoch and bounded active rotation|Remove unavailable contenders; enroll eligible members before moving the existing128-active batch; do not replace rotation with a global priority sort|
|Persistence|Existing schema1 owners|Default-omitted origin UUID/pose/direction and FIFO order on sole train; one dimension-local next-order counter, derived queue/slots rebuilt from canonical records|

No second rail engine, chunk forcing, copied cargo/fuel/fluid, new Create code/art, recipes, UI or protocol. Existing MIT37ea4afc search notice/license remain bundled. Priority0 for a request with no originating station is a declared dispatch adapter. Manual/retirement clears automation leases; actual physical collision protection remains independent.

Origin fairness is this slice only. No-wait pass-through, temporary navigation/return, full component-aware waits and operational groups/interrupts/wildcards remain in Issue15. No0.33.
