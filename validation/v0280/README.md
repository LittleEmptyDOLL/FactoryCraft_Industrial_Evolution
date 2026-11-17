# FactoryCraft 0.28.0 / issue #8 — local evidence

Java21.0.13, Minecraft1.21.1, NeoForge21.1.252; Factorio2.1.24 **Space Age enabled**, normal quality. Previous v0270 checkpoints/tests remain required. No old world migration or reset/refill.

## Automated checks

* `./gradlew build --offline`: **245 JUnit**, zero failures/errors (240 preserved +5 cadence/replay/persistence/resistance invariants). [Build](build.txt).
* No-optional native profile, `v0280-release`: **467 required passes**, including all440 baseline checks, thirty former finite chains and a new finite military line. [Base](native-base.txt). Includes the finite steam-to-laser case.
* Actual FTB Teams2101.1.12 /FTB Library2101.1.37 /Architectury13.0.8 /EMI1.1.24 /JEI19.21.1.248 /Jade15.10.6 dedicated runtime: **467 required passes**. [Optional](native-optional.txt). Tests run in actual Minecraft, not mocked API compilation; no client-only combat/optional class is loaded on the dedicated server.
* Separate real JVMs read existing `FACTORYCRAFT_V0280_DISK`: exact named3-round pistol magazine,23 turret rounds,137 typed private mB, controller/part identities and aim. `V0280_BUFFER_DISK` additionally retains251private +749component-distinct pass-through mB without internal mixing; first seed and later VERIFIED run are distinct processes. Existing V0270 and older ownership checkpoints still verify. [Reload](native-reload.txt), [optional repeat](native-optional.txt).
* Twenty-seven new required native cases including the finite line cover all six actual weapons, projectile/stream travel and ammo, full inventory/unload, strict upgrades, component serialization, flame ports/water rejection/stale guards, controller/part menu dispatch, precise buffers/oil/FE residues, portable one-drop, real target/cover/policy, source armor/laser separation, grenade replay, mine arming/one blast/stun, and genuine finite steam →engine →pole →laser. Native menu dispatch stubs only network delivery; actual packet/menu operation is separately verified by the real client below.
* Genuine chain:18finite iron ore →solid-fuel furnaces →2ordinary magazines →2piercing magazines +2grenades +4walls →4military packs →belts/inserters/chest →four actual lab research units. Copper/steel/brick/coal are declared finite additional inputs; preparatory FE is explicitly finite diagnostic stock, not a hidden runtime source. No final products or scientific outputs are seeded. All thirty prior chains remain active.
* `tools/check_space_age_research.py` passes with genuine military science removed from the future-source prohibition.114 research definitions have distinct graph coordinates; future utility/space gates remain closed. Numerical manifest:23 manufacturing recipes and55finite combat/Battery technologies. No pistol recipe.
* [Bounded pure profile](combat-kernel.txt):21rounds after ten warmups,1.2million requests/round; median.359ms, p95 2.424, p99/max3.533,48allocated bytes/round. No Minecraft tick/TPS claim or0.27 speedup claim.

## Real client

Final graphical checks are recorded in [client-checklist.md](client-checklist.md). This is actual Minecraft under Xvfb/Mesa, not generated UI. The optional profile includes all four integrations. Public auth/key requests and the absent audio backend are environment limitations; local rendering, gameplay and saving run. Audio output is unverified. No real multiplayer survival playthrough is claimed.

## Preserved development failures

Initial worlds exposed old automatic assembler selection choosing new ammo, obsolete military-future assertions, self-obstructing full-cuboid turret collision, and a real null-Entity raycast crash. Fixes reserve military recipe autodetection, intentionally update the now-genuine science assertion, share physical base geometry and use explicit empty CollisionContext. The first graphical launch exposed duplicate item-extension registration; subsequent rendering exposed generated-item parents discarding explicit3D meshes. Both were corrected in actual client development.

New menu-test attempts exposed NeoForge FakePlayer's no-op openMenu and the missing channel of a manually constructed server player; the final native fixture stubs only network delivery while retaining real dispatch/provider/guard logic, with graphical verification separate. Earlier failed results/worlds remain evidence, not passed runs. [Lossless archive manifest](archived-worlds.json) includes SHA256 for every file and archive; closed v0210 was also byte-verified archived to preserve history under the disk budget. Current successful release/optional/client worlds remain separate. No required test was disabled. The final filled-pipe regression tests the actual counted-crafting matcher, including a valid empty-pipe control; the earlier display-ingredient assertion is recorded in [development-failures.txt](development-failures.txt).

## Limits / CI ownership

The final recipe audit additionally rejects filled portable pipes in both turret manufacturing and handcrafting. Native strict-ingredient regression retains the fluid-bearing stack instead of consuming it as material.

See [COMBAT.md](../../COMBAT.md) for explicit Minecraft HP/terrain/fire/storage/collision adaptations and source-exact numbers. Meshes/animations are basic original functional art; no proprietary assets. Ground flames collapse by block and use bounded work queues; source block HP, terrain explosions, tree ignition/spread and massive combat targets are not implemented. Later science/Combat Shotgun/infinite/offworld research remain genuinely locked, without substitute production. No0.29 features.

Full factory, MIXED,10000-pipe profiling/restart and hosted repeats remain GitHub Actions. CI will be queried once after publishing; pending is not success.
