# 0.26.0 local evidence — 16 November 2026

Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21.0.13; code reference is the commit containing this directory. This evidence does not represent completed GitHub Actions.

## Automated checks

* Build: **224 JUnit**, zero failures/errors.
* Native: **418/418 required** on `v0260-final`, repeated in an independent JVM against the same checkpoint. The final post-UI/research build also passes 418 and verifies existing disk state. All 406 baseline cases and thirty restored finite chains remain mandatory.
* `FACTORYCRAFT_V0260_DISK`: actual Offshore water/source accounting, 20-tick heat debit/3→30 steam/8-FE quantum, finite tile UUID and mining fuel thirds, ASM3 component-exact input and saved work fractions. Seeded once, subsequently verified without reset.
* Dedicated SMOKE: [measure](smoke-measure.json), [independent-JVM verify](smoke-verify.json). Finite precharged engine preparation is explicit, not a world-water source or a survival-bootstrap test. The separate native cold chain uses real shore/coal/heat/steam.
* New world paths: `run-gametest/v0260-final`, `run-benchmark/smoke-v0260-local`; prior failed worlds are preserved or byte-verified losslessly archived. No checkpoint was reset/refilled to mask a failure.

## Actual client

Real Minecraft rendered under Xvfb/Mesa with no optional mods. The separate `FactoryCraft0260-QA` world is a copy of the completed **same-version** SMOKE fixture, with a fresh explicit QA platform/shore and hand-placed machines away from it. Both new drills and all three assemblers were placed/interacted with. Coal and iron plates were inserted through ordinary menus; the electric drill reports real ore output/FE after connecting poles. Boiler/engines use actual shore water and coal. The world saved normally, reopened in another client JVM, and saved again. This is single-client creative/admin-assisted placement/UI QA, not a natural-shore survival playthrough. Native tests cover finite cold bootstrap and conservation separately.

* [Offshore green 2×1 preview](offshore-preview.png) and [4000-mB no-FE source menu](offshore-source.png).
* [Boiler with corrected heat units](boiler.png), [powered scene overview](overview.png).
* [Burner drill consuming coal](burner-drill.png), [electric drill with ore output](electric-drill.png).
* [Assembler 1 sixteen inputs and unobstructed progress](assembler-1.png), [Assembler 2](assembler-2.png), [Assembler 3](assembler-3.png), [four ASM3 module slots](assembler-3-modules.png).

Screenshots are direct captures; black borders in the second client come from its smaller actual window inside the X display. No Factorio art is used. Optional-mod runtime, external pipe-pack visual testing, multiplayer, full/MIXED/10000-pipe workloads remain outside these local captures. Full parity gaps are listed in [the audit](../../docs/FACTORIO_PARITY_AUDIT.md).
