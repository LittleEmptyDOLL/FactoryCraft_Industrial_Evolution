# 0.30.0 Railway Foundation validation

Reference Factorio2.1.24 +Space Age; implementation limits are authoritative in ../../RAILWAYS.md. Existing487 native/260JUnit and thirty finite production chains remain enabled; old fixtures/artifacts were not reset.

## Executed server checks

* Base native command: `JAVA_HOME=/workspace/work/jdk21 GRADLE_USER_HOME=/workspace/work/gradle-home ./gradlew runGameTestServer -PgameTestFixture=v0300-dev2 --offline`.
* Final base process `v0300-native6-reload.log.gz`: **all504 required tests passed**, including17new railway cases. Separate JVM reload verified the original persisted UUID `0d825dce-84ed-4fb6-b7b8-ba2dc93ce3b0`, length37.05343953308822, paid19rails and exact custom component `disk rail thirty`. First seeded in native2; later reloads never reseeded.
* New invariants: genuine source crafting/research denial/debit; two-click continuation and cross-chunk parts; typed exact refunds/no paid state in parts; claims/blocked volume/inventory-callback rollback; two competing players/reach; curves/slopes; explicit branches/overpasses; canonical unload/reload callbacks; external demolition; later block-clearance denial; native dynamic shape; no second generic ItemStack placement cancellation after payment; bounded128-edge finite profile.
* Pure20rail tests cover16headings, source straight/diagonal/half-diagonal costs, reflected turns/S-bends, grade/radius/reverse/arc samples/up frames, clipped original mesh, graph topology/read-only work, duplicates/crossings/approach chains. Final `./gradlew build --offline` succeeds: **280JUnit, zero failures/errors/skips**, executable `factorycraft-0.30.0.jar`. `junit-summary.json` and compressed final build log retain the result. Rail resource generation is byte-idempotent across all runtime resources and keeps the original curated iron-stick artwork.
* Source numerical audits: `check_rail_reference.py`, `check_nuclear_reference.py`, `check_space_age_research.py` pass. Original recipes/resources outside the necessary explicit rail-selection category stay unchanged.

## Optional runtime

`./gradlew runGameTestServer -PgameTestFixture=v0300-optional -PwithViewers=true -PwithJade=true -PftbModsDir=build/optional-runtime --offline`: final retry **504/504required pass** in the same unreset diagnostic world. Real FTB Teams2101.1.12/FTB Library2101.1.37/Architectury13.0.8, EMI1.1.24, JEI19.21.1.248, Jade15.10.6. New distinct optional checkpoint UUID4a6a1b54-a3a9-47b4-825b-d2ec7508e186 seeded with exact19paid rails. The independent-JVM persistence proof above is the base fixture; optional full reload also remains in hosted CI. Native optional placement tail p99 22.669ms/max23.705ms was worse than base and is reported separately, not discarded.

## Development failures retained

`native1` failed eased-ramp foundation clipping and exposed an old module production line stealing new iron-stick recipes. Foundation fragments now stay clipped at existing supports; both ordinary and petroleum assembler dispatch require explicit railway recipe selection. The original module line again produces26/10gears; its assertion was not weakened.

`native3` exposed legitimate tiny connected approach collisions; explicit endpoint traversal now grants only a bounded8-block approach exemption. `native4` exposed untracked fixture entity chunks and asynchronous profile chunks. Dedicated test-only pins/wait were added and released. `native5` exposed unordered same-tick scheduled callbacks building edge3beforeedge2; a single sequential callback per four-edge batch fixes the fixture. Final native6 passes all cases. The first optional-mod run also failed all17new cases during FTB FakePlayer login synchronization: FakePlayer has no real Netty channel. The fixture now uses FTB's supported null-online-player personal-team registration, still obtaining a real persisted FTB research identity. Existing487optional cases passed that run. Full compressed logs retain those failures; no failed world was reset or refilled.

Closed worlds were losslessly archived only after every file was byte/SHA256 checked. `prior-world-archives.json`, `development-world-archives.json`, `validated-world-archives.json` list complete files/archive hashes; archives are in `/workspace/work/v0300-evidence`. The final archive preserves the same disk checkpoint and incomplete failed diagnostic arenas. Old0.29QA/player saves were never opened or altered.

## Real graphical checks

Actual Minecraft1.21.1/NeoForge21.1.252 client on Xvfb/Mesa, fresh `FactoryCraft0300-QA`, no old game copied. Diagnostic creative stone floor/end supports and temporary fixture chunk pins are explicitly setup conveniences, not runtime behavior. All rails placed by the actual two-click item, no raw rail blocks. F2 captures:

* `en-doubletrack`:4-block center spacing, centered2-block bed.
* `en-curve-green`, `en-s-bend-green`, `en-junction-curve`: real accepted curves/continuation/endpoint turnout. Earlier blocked-parallel captures retain the valid refusal to clip a neighboring line; the accepted curve begins beyond its envelope.
* `en-incline-green`, `en-bridge-incline`: eased6-block rise, separate horizontal line underneath, independently built endpoint piers.
* `en-tunnel-blocked`, `en-tunnel-green`, `en-tunnel-installed`: roof atY5rejected, roofY6accepted, no automatic terrain deletion.
* `en-machine-scale`: existing3×3assembler beside railway, unchanged machine geometry.
* `en-selected-miss-no-preview`: selected start plus sky MISS leaves no ghost. Native checks cover server reach/claims.
* `en-graph-diagnostic`: actual command state. Creative source claims are real tracked rail counts; finite survival accounting is tested natively rather than claimed from this scene.

RU optional client reopened the same saved scene in a separate JVM. `ru-restored-junction` /`ru-restored-bridge` show actual restored meshes; `ru-restored-graph` confirms13edges/20nodes/219ownedrails (loaded count correctly differs with view chunks). `client-ledger.json` records exact UUID/endpoints/paid counts and current saved-data SHA256. `ru-halfdiagonal-preview` and `ru-flipped-start-rejected` check control/validity localization; `ru-jade-owner-inspection` shows the Rail name and actual owner endpoint degree2/3, without a technical casing name. `ru-rail-tooltip`, `ru-rail-recipe-viewer`, `ru-rail-assembler-viewer` show native item controls and the real source crafting/assembler recipes with Railway requirement. Both clients saved and quit normally; temporary QA chunk pins were removed. These screenshots are actual F2 captures, not mockups.

Client emitted expected sandbox auth/audio warnings and the pre-existing missing offshore moving model warning; audio and0.28/other-machine visual polishing remain outside this task. The combined EMI/JEI development runtime also logs existing duplicate vanilla/tag and untranslated catalog warnings (348 shown), as in earlier milestones; no new rail recipe/model failure appeared. No real two-user remote graphical multiplayer session or real third-party land-claim plugin was available; native players/cancellation tests cover server authority, not full external plugin certification.

## Performance and scope

Pure and native measurements are separated in ../../BENCHMARKS.md. No per-track ticking/Entity or read-only topology rebuild; no measured TPS improvement claimed. Static native chunk meshes/changed projections are checked graphically. Full hosted factory/MIXED/large-pipe jobs remain CI. Literal fixed A/B footprints, at-grade diamonds, mid-edge splitting/movable blades, and Space Age elevated progression resolution remain documented limits; no trains/stations/signals implemented.
