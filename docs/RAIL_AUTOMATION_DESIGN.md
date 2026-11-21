# Railway automation / issue15 — implementation map

This checkpoint implements the basic physical point/region/search/stop/schedule adapters below. Shared groups/interrupts, pass-through and the advanced native/client matrix remain planned in this same issue. Create revision37ea4afcfe5bf85875d4b0749798a45330070a1f was read before starting. Its MIT code may be adapted; no protected assets or runtime dependency is used.

| Factorio behavior | Create source / reuse | FactoryCraft integration and necessary differences |
|---|---|---|
| Interior signal/stop points | EdgeData ordered points; adapt | RailEdgePoints uses existing UUID+metric offsets; never split paid physical rails |
| Unsignaled block flood | SignalPropagator.walkSignals; adapt | Budgeted local physical interval components; two-way sides and original/blue chain semantics |
| Atomic train passage | SignalEdgeGroup occupancy; adapt pattern | Current exact intervals/oriented SpatialReservations remain hard safety; new logical claims include every wagon |
| Route and braking scout | Navigation.search/scout; adapt | Bounded directed Dijkstra on RailGraph, genuine source metric lengths, no global other-train scan; existing three-substep motion only |
| Same-name stop capacity | GlobalStation commitments; adapt pattern | Factorio0–255 priority/default50, approaching+docked slot once; disabled committed arrivals retained |
| Wait timing/inactivity | ScheduleRuntime, IdleCargoCondition; adapt pattern | Native cargo-only transfer revisions, paused/unloaded time excluded; Factorio DNF, not Create GUI semantics |
| Groups/interrupts/wildcards | Factorio-specific new code | Shared scoped definitions, typed substitutions, bounded detours/return; never advertise a no-op control |
| Trackside UI/circuits | StationBlockEntity attachment ideas | Guarded current physical owners, four independent wire colors, no inventory/cargo copies |

One dimension-local automation SavedData owns canonical trackside configuration/shared schedules. TrainData owns individual mode, schedule/runtime/stop commitment, train resources and pose. Part BEs have identities and nonowning visual snapshots; stale UUID or unloaded owners fail closed. New fields are additive/default-omitted in schema1 saves. Derived region/route indexes are rebuilt under budget and never publish partial ready state. Local holds distinguish completed topology from pending body reconstruction; only the reconciler may inspect held topology. The movement adapter consults its existing immutable body-region index, not a dimension-wide busy flag. Bounded affected-edge notifications update client/circuit aspects after publication or hold release. Unrelated edits preserve an active flood. An optional canonical HeldSignal identity latches a real stopped red wait and prevents paid creep; no resource ownership is added.

Grades and current endpoint-only turnouts remain the existing FactoryCraft geometry. No at-grade diamond, hidden mid-edge junction, movable blade, fake rider or automatic chunk loading is added. Manual trains may ignore signal colors; original physical claims still prevent collisions. The stop marker targets the leading locomotive nose with seven-block car spacing and0.06-block tolerance, using the existing braking recurrence. Search uses metric distance plus a documented1000-per-block occupancy/reservation adaptation; full source penalties and origin dispatch priorities remain pending.
