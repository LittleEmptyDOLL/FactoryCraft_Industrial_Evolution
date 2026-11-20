# Third-party code notices

## Create — MIT code adaptations

Copyright (c) The Create Team / The Creators of Create.

Upstream: https://github.com/Creators-of-Create/Create
Pinned revision: `37ea4afcfe5bf85875d4b0749798a45330070a1f` (`mc1.21.1/dev`).
Full permission/warranty notice: [third_party/Create-MIT.txt](third_party/Create-MIT.txt), also included in the distributed JAR with this notice. The upstream license restricts `src/main/resources/assets/` separately to All Rights Reserved; no such assets are included.

Adaptations currently implemented:

* `graph/EdgeData.java` → `factorycraft.simulation.rail.RailEdgePoints`: ordered metric edge-point insertion/next lookup, adapted to existing immutable UUID edges, both directions, no Catnip/Guava/Create graph dependencies. Bounded binary lookup and deterministic UUID ties added.

* `signal/SignalPropagator.java` → `RailSignalBlocks`: local boundary-stopped flood, adapted to immutable fractional interval spans with bounded publication and fail-closed invalidation.
* `entity/Navigation.java` → `RailPathSearch`: directed frontier/predecessor route search, isolated from Create classes and extended with budget continuation, deterministic ties, initial-edge loop revisits, fractional destinations and Factorio stop priorities.

Additional ports will be listed only when implemented. Comparative design and explicit Factorio differences are in docs/RAIL_AUTOMATION_DESIGN.md. Public Factorio prototype numbers are references, not a claim of copying its closed-source routing/collision solver.
