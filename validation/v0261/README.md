# FactoryCraft 0.26.1 validation — issue #6

Java21.0.13 / Minecraft1.21.1 / NeoForge21.1.252. Executed local results, not an assertion that CI has completed:

* Build: **226 JUnit** (224 baseline + two actual geometry/endpoint contract tests), zero failures/errors.
* **429 required native GameTests** (418 baseline + eleven), all thirty restored finite chains retained. Final optional-runtime passes and independent-JVM checkpoint verification include FTB Teams2101.1.12, FTB Library2101.1.37, Architectury13.0.8, EMI1.1.24, JEI19.21.1.248 and Jade15.10.6 together. Final runs: `v0261-optional-final.log` and `v0261-optional-accepted.log`; decisive excerpts [here](verification-excerpts.txt).
* `FACTORYCRAFT_V0261_DISK` preserves cross-chunk land/source/overhang, 4000mB source/counter, eight grade261 pending items, thirteen coal, exact belt diamond position and third pole ownership across JVMs. Existing saved checkpoints remain required.
* Real dedicated SMOKE [measure](smoke-measure.json) and [separate-JVM verify](smoke-verify.json): 100 belts /200 carriers, twenty producers, ten arms; iron2560 conserved, moving owners remain live. Short tick-work sample mean1.881870ms /p95 2.242578 /max27.705618. No universal TPS/improvement claim; full/MIXED/10000-pipe are CI gates.

## Real graphical client and disk census

Fresh normal-noise world `FactoryCraft0261-Natural-QA`, seed **26116112026**. Only vanilla generation settings were reused to initialize the new directory: no previous chunks, entities, inventories or FactoryCraft saved data. Client without optional mods, Xvfb/Mesa. This is creative/admin-assisted single-client QA, not a complete survival or multiplayer playthrough.

An actual naturally generated grassy coast at `(20,62,-256)` was left untouched. One item forms a supported land controller at `(20,63,-256)` and intake over water at `(19,63,-256)`; no foundation beneath the intake. An ordinarily placed inland pipe at `(21,63,-256)` receives water. The source creates **5000mB =4000 stored +1000 pipe**, zero FE, source water unchanged. After visiting the separate deck and returning, those values remain unchanged and the world saves normally.

* [Green shore-normal preview](natural-shore-preview.png).
* [Actual intake over natural water and inland pipe](natural-intake-pipe.png).
* [Source menu: 4000mB /600mB/t /0FE](natural-source-menu.png). Its generic XZ field describes the single owned fluid cell, not the complete physical machine footprint.

A separate newly built sky deck declares **eight finite ore units for each drill**, two coal for Burner and finite16000FE for Electric. No output items are injected and no extraction arm is installed. Burner delivers eight raw iron directly to a chest; Electric delivers eight one-item carriers to two belts. Both source tiles exhaust to air. Electric spends3840FE, retaining12160; Burner retains one coal and1600 thirds of unspent heat. [Raw read-only Anvil census](client-save.json) verifies these amounts after normal save.

* [Both drill chutes and actual belt cargo](drill-front-output.png).
* [Eight genuinely mined ores in the chest](burner-output-chest.png).
* [Small Poles, three blocks and real copper wires](small-poles-wires.png).
* [Two-block clearance rejected](small-pole-blocked.png) / [third block cleared, green preview](small-pole-preview.png). The temporary ceiling is an intentional obstruction test, not machine-state reset.

Initial UI focus/placement misclicks remain in an unused separate yard x40..85; the final declared-input yard is x95..135. No prior diagnostic world was reset/refilled. Original [0.26.0 CI failure](previous-ci-failure.txt) and [development failures](development-fixture-failures.txt) remain recorded; closed worlds were byte-verified losslessly archived. All screenshots are direct captures of original FactoryCraft models.

## Scope and remaining checks

Drills keep bounded four-stack pending output and stop when full; no receiver does not spawn endless loose entities. This is the documented safe fallback instead of Factorio's single ground ore. Shore support/source must share a block elevation. External handlers follow the standard committed-remainder contract; undisclosed third-party commits/exceptions cannot be made atomically recoverable across mods. Optional viewer/Jade client UI and an external pipe-pack visual test were not run locally. Three lifts and four circuit colors are unchanged.

CI is triggered on publication. [Workflow](https://github.com/LittleEmptyDOLL/FactoryCraft_Industrial_Evolution/actions/workflows/build.yml) repeats vanilla native/restart, optional runtime, full/MIXED and10000-pipe gates. Issue #6 explicitly requests its completed compatibility result; report actual CI outcomes after publication, never infer a pass from this record.
