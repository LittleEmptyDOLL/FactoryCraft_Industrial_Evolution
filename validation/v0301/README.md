# 0.30.1 / issue #12 — integrated fix validation

Base main: `fcd3309454c15bc560016ae63aef1163f9598c76`. Existing armor PR #11 (`bb95537bf204ec74df6bf28cfd3e7ba5fabe358e`) is integrated once by local merge `59115be`; no replacement attribute system. Minecraft1.21.1 /NeoForge21.1.252 /Java21. Source mechanics: Factorio2.1.24 with Space Age. Old worlds/tests/evidence are retained.

## Automated checks

Final build: **285 JUnit, zero failed/error/skipped** (280 baseline +3 exact laser-ledger +2 preview-only geometry cases). Final native suite: **524 required passes**, retaining all504 baseline +PR armor case +19 new focused cases and thirty finite production chains. A second independent JVM on the same final world passed **524/524** and verified the exact new checkpoint plus all old checkpoints. The final optional-mod runtime also passed **524/524**; its result is recorded independently below.

Commands (Java21 and cached Gradle dependencies):

```sh
./gradlew build --offline
./gradlew runGameTestServer -PgameTestFixture=v0301-final --offline
./gradlew runGameTestServer -PgameTestFixture=v0301-final --offline
./gradlew runGameTestServer -PgameTestFixture=v0301-final-optional -PwithViewers=true -PwithJade=true -PftbModsDir=build/optional-runtime --offline
```

New checks cover: finite real steam → explicit grid → three successive laser victims, disconnection/no free shot/reconnect;251mB oil → three flame victims below restart threshold; owner on opposite side of physical knockback; nearby hits with unavailable distant tails and real cover; grenade arcs beyond15blocks, real wall/ground/mob contact, saved37→99→100 fuse, bounded unavailable/corrupt projectiles; generic armor component overrides, all16 native attributes, ordinary percentages and type tags, neutral damage and other armor, exact acid damage and NBT; actual uranium3→2→0 with real acid; protected paid rail starts/anchors/connect-and-clear/deletion/item/air cancellation; invalid ghost exclusion from saved owners; bounded effect codec; and a damage listener dismantling the laser into exactly one fully billed portable owner.

New `FACTORYCRAFT_V0301_DISK` records exact post-combat oil/priming/FE/debt/shot-residue/cooldown/aim owners in one process and verifies them in another. Existing V0270–V0300 fixtures remain required, including unchanged rail UUIDs/material claims. This is separate from the actual player-world grenade save/reopen described below.

## Real two-client QA

Fresh, marked `run-gametest/network-v0301` only, loopback port25586, synthetic FactoryOne(EN) and FactoryTwo(RU), distinct Xvfb displays, Mesa software rendering. Both are actual Minecraft clients on a real dedicated server. Installed optional runtime: FTB Teams/Library/Architectury, EMI, JEI, Jade, Apothic Attributes2.11.0 and Placebo9.9.4. No Curios, TaCZ, Superb Warfare or Create dependency. Development/offline network warnings (Mojang publickeys, Placebo patron cosmetics, optional refmaps) are retained; cosmetic HTTP failure is not misreported as a gameplay crash.

The QA floor, fixed daylight and temporary chunk tickets are explicit diagnostic setup. Machine items, ammunition and raw finite steam/oil are declared administrative inputs to inspect behavior, not a claim of survival production. Native finite production chains supply the separate full production proof. Two4000mB steam inputs drove actual placed engines/substations/laser; three separately tagged20HP enemies were eliminated,22 paid shots total including earlier eligible targets, and both steam inputs exhausted. A separate251mB light-oil input entered the physical pass-through box: three sequential targets were eliminated,18 paid attacks, **215mB remaining =251−18×2**, priming retained. No refilling concealed failures.

Evidence inspected:

* `en-fp-{pistol,submachine_gun,shotgun,combat_shotgun,rocket_launcher,flamethrower}.png`: actual F2 for each corrected first-person weapon; `en-offhand-{pistol,shotgun}.png`; `en-thirdperson-shotgun-heavy.png` and RU observer show holding/armor. Original blocky art is retained; this is an orientation fix, not a high-detail model replacement.
* `en-heavy-vanilla-modifier-tooltip.png`, `en-light-vanilla-modifier-tooltip.png`: six nonzero standard modifiers with correct flat/percent values and no duplicated stat paragraph. `en-apothic-heavy-changed-attributes.png`: real Apothic GUI shows Acid40%, Explosion20+30%, Fire30%, Physical6+30%; unfiltered screen also exposes zero values. Server attribute queries confirmed6 and0.3. Actual lifecycle/reload follow-up is recorded below.
* `en-paid-smg-hit.png`, `ru-observer-smg-hit.png`: paid hits, trace, real victim and both players. `ru-laser-sequence-2.png`: coherent red laser and impacted hostile seen by the other client. `ru-flame-paid-*.png`: travelling flames/impact field. Both client logs confirm paid effect kinds0/1/2 at identical server ticks21160/24933/27121; no client supplied hit/damage.
* Actual OpenAL **Wave File Writer** initialized on both clients. Six `*-{gun,laser,flame}-playback.ogg` clips preserve native rendered playback for shooter/observer. `audio-playback.json` records nonzero decoded peak/RMS for every clip. Full float WAV captures were losslessly gzip-archived and SHA256 verified before removing their uncompressed copies; see `audio-archive.json`. These checks establish actual playback/network delivery; subjective speaker mix/loudness acceptance is **not claimed** in this headless environment.
* `en-rail-new-anchor-selected.png`, `en-rail-obstacle-red-entire-route.png`, `en-rail-obstacle-removed-green.png`, `en-rail-connected-selection-cleared.png`, `en-rail-reselected.png`, `en-rail-air-cancel-no-ghost.png`: physical initial piece, canonical reselect, persistent red obstruction reason, green after actual removal, real completion without forced continuation and explicit cancellation. Diagnostic obstacle was added/removed without altering a paid segment. Additional final RU/curve/reload views follow below.

First-pass uranium screenshots deliberately retain the missing acid translation discovered in live QA. Code now reuses the existing EN/RU `tooltip.factorycraft.uranium_acid`, rather than publishing that key as text. The first RU attempt was obstructed by the other player; it is not a passing uranium view. Final rebuilt screenshots must be used for acceptance.

## Failure integrity

`v0301-native1.log.gz` retains two initial fixture failures: old solar test requested100FE/1500units with only1320units of actual surplus after asynchronous chunk promotion; new nearby-hit fixture assumed entity-section tracking after six ticks. Solar now waits for real sufficient surplus before the unchanged exact100FE debit/disconnection/shade assertions; hit test waits for actual tracked enemy before its one paid shot. No generator, refill, relaxed ledger or disabled case was introduced. Same failed world then passed all520 cases and separate-JVM verification (`v0301-native2-reload.log.gz`). Earlier optional520+520 passes are retained; final19-case suite adds genuine ground/mob/unloaded/corrupt-age/reentrant ownership coverage, not disabled old tests.

The initial GUI hotbar-selection attempts, blocked placements and untranslated stock captures remain as diagnostic images; filenames alone are not passing assertions. Historical closed worlds0.30 optional, first0.30.1 base and first0.30.1 optional were byte-verified in lossless tar.xz archives; manifests here list every original file hash. No previous world/checkpoint was reset or reseeded.

## Remaining scope / acceptance

No trains/signals/robots/0.31, new combat balance, recipe or nuclear/production redesign. Existing variable cubic rail adaptation, endpoint-only junctions, no mid-edge split/at-grade diamonds/movable blades, source elevated-progression gap and independent player bridge supports remain explicitly documented in RAILWAYS.md. Grenade5-second fuse and strict friendly protection remain Minecraft adaptations. Existing0.28 basic artwork is retained. Full factory/MIXED/10000pipe release regression runs in GitHub Actions; local bounded profiles are not TPS measurements. Pending hosted CI and subjective audio quality/user gameplay sign-off are never called passed.

## Bounded profiling

Three standalone warmed JVMs after actual server/client exit: exact laser idle ledger median3.246–3.403ns/modeled tick,160 measured allocated bytes across2.1million ticks, exact6720000FE per run; no negative debt/free work. Existing rail100/1000/4000-edge graph profile retains zero idle rebuilds. Raw tails are retained, including a5.906ms scheduling outlier; no before/after speedup or Minecraft TPS improvement is claimed. See `kernel-profiles.txt` and BENCHMARKS.md. Full hosted workloads remain CI.

## Final rebuilt client and actual server reload

Final optional native suite: **524/524** with FTB Teams/Library/Architectury, EMI, JEI, Jade, Apothic Attributes and Placebo. All285 JUnit remain green in the final packaged build; exact JAR hash is in `check-summary.json`.

The same running clients survived the environment-control outage; no world or inventory was reset. `en-uranium-localized-final.png` / `ru-uranium-localized-final.png` confirm317 exact remaining units and the translated acid requirement in both actionbar and Jade. Real armor queries remained6 flat/0.3 after relog and dimension travel, while an ordinary iron helmet contributed its normal2 armor. Actual death dropped exactly one Heavy Armor; respawn read0, pickup of the existing dropped item and normal shift-equip restored6. Diagnostic teleport gathered the16 existing scattered item entities without creating replacement items. The first scripted dimension transition was still on its loading screen; its premature follow-on commands were repeated after terrain was actually ready, not counted as a successful death.

`en-rail-curve-final-preview.png` and `en-curved-rail-built-auto-clear.png` show a valid19-rail curved connection, then the placed curve with automatic deselection. `ru-curved-body-canonical-end-selected.png` confirms actual curved-body targeting selects a canonical endpoint; C cancels it. `ru-flame-status-menu-reloaded.png` shows215mB retained fuel and the real localized searching status after reload. Original straight/red-obstruction/clear/cancel evidence remains above.

The first player-world grenade replay continued while the environment was disconnected and before clients finished joining; its eventual absence is **not** a precise timed-reload acceptance. A second genuine paid throw used one of the two remaining grenades (2→1, no refill), was frozen/saved atAge37, then reopened in another dedicated-server JVM with automatic freeze-on-ready. `network-guarded-reload.log.gz` confirms **Age37**, position(80.5,122.49149416677355,16.265647225299645), and the unchanged215mB flame owner. This is real world entity NBT persistence, in addition to the native exact-owner checkpoint. Subsequent live continuation is recorded below.

Live continuation after reload: server count was exactly1 atAge57, position(80.5,107.75729890962238,9.702715449533558), well beyond the old15-block path boundary. After43 more ticks it was0; the player's remaining grenade was still1 and the flame owner still215mB. The automatic guard and exact server output are retained in `guarded_reload_harness.py` / `network-guarded-reload.log.gz`. Native tests separately prove actual ground/mob/wall contact and exact100-tick airborne fallback; these screenshots do not isolate a single exact impact frame. The diagnostic forced tickets were removed, normal ticking restored and the same world saved/closed normally.

The RU observer received a native Netty `connection reset by peer` disconnect **after the explicit server stop**, producing a retained network diagnostic report; it stayed responsive and quit normally. No FactoryCraft packet-decode exception or in-game compatibility crash is reported. This shutdown observation is preserved rather than described as an ideal graceful disconnect. Full final client logs are retained. All original and final audio captures are losslessly archived with hashes; no subjective speaker/loudness review is claimed.
