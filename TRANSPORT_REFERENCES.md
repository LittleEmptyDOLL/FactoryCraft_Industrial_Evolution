# Transport 0.23 reference review

Reviewed 11 November 2026. This is a design comparison, not a code/assets import.

| Source | Reviewed scope | Useful idea used | Deliberately not transferred |
|---|---|---|---|
| [Minetorio](https://github.com/D1pex2/Minetorio/tree/ba979bf173f6e7e97029a5c2228ed2243b4f7b66) | `client/belt/BeltRenderer`, `LaneItemRenderer`, `LanePose`, `content/belt/BeltGeometry`, `art/gen_models.py`, `art/gen_belt_slopes.py` | Separate lane trajectory from visual pose; continuous directional surface coordinates; distinct metal rails and repeatable generated geometry | Factorio 1.1 behavior, its lane implementation, renderer/model code and all artwork. `TEMPLATE_LICENSE.txt` explicitly covers NeoForge template files only, not the whole project. |
| [Create](https://github.com/Creators-of-Create/Create/tree/4148fd8d5b05c85d88947edc00fb495a5074f57d) | `BeltVisual`, `BeltModel`, `BeltRenderer`, [license](https://github.com/Creators-of-Create/Create/blob/4148fd8d5b05c85d88947edc00fb495a5074f57d/LICENSE.md) | Keep cached static hardware separate from scrolling surface/material state; distinguish end/middle roles and pulley details | Kinetic power, single transported-stack architecture, Flywheel dependency and Sprite Shift implementation. Code is MIT; `assets` are All Rights Reserved. No source or assets copied. |
| User's two Satisfactory Conveyor Lift images | Form and construction only | Open service side, continuous shaft frame, colored side panels, repeated collars, distinct input/output mouths | Images/textures/mesh extraction, single-lane logistics, motor power model. FactoryCraft keeps two lanes, its existing metric path and one canonical owner. |
| [Factorio 2.1.24](https://github.com/wube/factorio-data/tree/2.1.24), [2.1 history](https://wiki.factorio.com/Version_history/2.1.0) | Inserter reflection and splitter-side changes; 2.1.15 belt-capacity dependency | Physical far lane for perpendicular feed; reflected choice only for ambiguous parallel axes; splitter input-end insertion; capacity 2 also requires bonus 7 | Proprietary assets and unrelated gameplay changes. Space Age capacity research remains sandbox-only. |

FactoryCraft uses its own pure `TransportMesh`, native chunk-baked quads and vanilla atlas animation. Geometry is never rebuilt each frame. Lift sections invert the monotonic route height and sample only their own interval: straight middle cells need one segment, bends/twists at most sixteen. There are no model/roller/item entities, no Create/Flywheel runtime dependency and no transport-length/headway/speed change.

Atlas animation is global, so a circuit-stopped belt's *surface texture* continues moving; its authoritative carriers and interpolated item display stop. This deliberate limitation avoids re-baking chunks on each motor state or adding per-belt surface draw calls. Vertical middle cells repeat their texture locally rather than stretch a single image over the whole shaft. Curves use a continuous normalized angular coordinate; inner/outer texture widths compress with the bend, without changing metric lane travel.

Future tasks, **not implemented here**:

* Drag/right-click line placement: useful, but needs shared path preview, protected-volume validation and atomic inventory use.
* Spaced mass pole placement: useful with the existing explicit copper graph; needs reach/coverage-aware preview and no auto-revival of deliberately removed edges.
* Route editing: useful for height/exit changes; occupied-route reservation/length validation must remain authoritative.
* Broader transport direction overlays: hover pickup/drop hints are implemented here; a factory-wide overlay needs bounded client indexing and zoom/visibility policy.
* Animated machine parts: continue the existing original partial-model approach, sharing immutable meshes and keeping production on the server.
* GPU instancing/advanced materials: an optional later renderer experiment, with measured compatibility and memory budgets; do not add a mandatory framework merely for belts.
