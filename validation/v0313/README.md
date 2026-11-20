# 0.31.3 — issue14 follow-up, from 529feef

Scope: sections6–7 of the issue revision updated19November2026 17:52UTC, preserved in `issue-body.txt`. No0.32 feature is included. Previous0.31.1 oriented hulls/placement and0.31.2 exact reverse intervals remain in place; their historic evidence is not relabeled as this run.

## Behavior and ownership

Ordinary lethal contacts debit finite kinetic energy from the actual health/absorption removed by an accepted native hurt and uncanceled death. Public source weight and energy_per_hit_point are inputs; this is an explicitly documented Minecraft adapter, not the closed-source Factorio solver. Removed native HP is converted with Combat.HP_SCALE; overkill is not charged. Source impact resistance is applied to the actual struck car. Cow kills incur the existing small resistance-tail recoil, not zero train damage. A survivor, canceled hurt/death, immune/protected entity or structural obstacle does not become a pass-through.

The first safe pose, sole fuel payment and full swept reservation commit before native callbacks. Canonical train/car/path/pose are checked afterwards; reentrant ticking cannot debit again. Each continuing hit rescans the world, and retains exact interrupted60Hz substep time. The loop is bounded at16hits/tick; at the cap it retains residual speed at the safe pose for later ticks. Item drops/dead bodies do not become new barriers. No permanent hit immunity, world access off-thread, additional entities or forced runtime chunks.

The selected source stores its physical saved edge and both real outward ends, separately from the resolved source. The shared pure resolver chooses a lawful end using the hovered geometry; it never invents a midpoint or changes existing track tangents. X cycles Auto/Other/Selected/Auto, R remains an end-heading override, C/sneak/item change cancel. Client ghost, bounded5x5 hint cache and paid server placement share the request/result. Hint identity includes both source ends, side, anchor and revision. The long EN/RU HUD wraps within the viewport.

## Interaction comparison

| Gesture | Old0.31.2 | Create1.21.1 source reference | New FactoryCraft |
|---|---|---|---|
| Plain ground click | Paid gaze-directed short rail | Axis selected using look, then placement | Same intentional gaze-directed paid starter |
| Select physical source | One stored end/tangent | Selected axis, first axis may flip toward destination | Saved edge plus both actual outward ends |
| Hover opposite side | Often rejected by fixed start | First axis sign follows connection geometry | Auto resolves other lawful endpoint |
| Destination on ground | Camera16-heading tangent | Camera/geometry resolves second axis | Camera axis sign resolved toward destination; R can lock it |
| Destination on rail | Nearest end alone can be unusable | Physical axis/curve targeting | Ordered real incoming tangents, first connectable pair |
| Existing curve/junction | Endpoint/axis tie limitations | Physical curve picker | Bounded curve-distance samples, gaze tie, stable edge UUID |
| Hint/ghost | Bounded cached5x5, fixed source | Cached local5x5 plus curve | Same resolver and side-aware cache; advisory protection |
| Invalid/manual side | Fixed red path | Invalid connection feedback | Red ghost and explicit source-direction/geometry reason |
| Finish/cancel/payment | Protected atomic transaction | Finish/cancel material transaction | Retained; pure/native verify exact refunds/claims |
| Radius/grade/headings | Factorio geometry,16families | Different Create geometry | Retained Factorio >=13radius/3D grades/16families |
| Plant/torch vs wall |0.31.1 protected physical bed logic | Physical placement/collision | Retained native coverage; no new relaxed clearance |

Reference files were inspected from the actual mc1.21.1/dev TrackBlockItem, TrackPlacement and TrackBlockOutline implementation. No Create code/art or runtime dependency is added. A separate Create runtime test world has not been run, so this table is a source/behavior comparison rather than full Create gameplay certification.

## Automated checks

`build`:326JUnit (317retained+9new), zero failures/skips. Packaged artifact hash and JUnit counts are in artifact-and-junit.json; exact native revision boundaries are in VALIDATION.md.

New pure cases cover exact kinetic debit, no overkill, canceled/surviving outcomes, repeated hits, tanker coefficients, interrupted60Hz force/time, both source endpoints for all16headings, camera-vs-fixed end direction, real connectable target ends, curve tangents and hint-mode invalidation.

New native cases cover travel-ordered multiple kills; canceled hurt and heavy survivor; callback-created wall; source retirement plus reentrant tick; dense24mob/4engine cap; reverse3-car rear-tanker hit with named31diamonds,234567mB, finite fuel and exact saved checkpoint; actual curved grade contact; canceled death; both paid source directions; explicit source-side failure and canceled protected placement. The old cow test now checks the correct continuing motion and unchanged exact cargo/attribution, rather than deleting its previous guarantees. Every prior native scenario and thirty finite production chains remains mandatory.

Final native run uses FTBTeams2101.1.12, Library2101.1.37, Architectury13.0.8, EMI1.1.24, JEI19.21.1.248, Jade15.10.6 and ApothicAttributes2.11.0/Placebo9.9.4. The first candidate passed on the same untouched fixture in another JVM. A third full run checks the projection-resume correction; a fourth separately named GameTestServer run passes571/571on the exact release with the final canceled-death next-sweep guard. No switching research backends on an existing checkpoint.

## Bounded actual-server profile

Same192m real track/site,24fresh finite locomotive trials:8warmup+16measured, each1coal and3ordinary cows. Explicit initial source-cap kinetic phase is declared QA input; this benchmark is not proof of survival acceleration. One1GiB server, no clients, same JVM/cgroup. Source hashes in `baseline-source.json`; `impact-profile.json` contains all samples. Prep/braking/packing/cleanup are outside the measured single movement tick. Packed outputs remain in the world, never refilled.

| Measured work | 529feef |0.31.3 |
|---|---:|---:|
| Mean movement call,ms |3.224367 |2.799230 |
| Median,ms |1.085538 |2.702067 |
| p95/p99/max,ms (n16) |35.363842 |3.686128 |
| World reads,total |6720 |24000 |
| Actual native damage callbacks/deaths |16/16 |48/48 |
| Continuing trains |0/16 |16/16 |
| End source speed |0 |0.8335542629763285 |

Work differs because the correct result reaches all three mobs and checks remaining obstacles. Median cost rises; the old mean/tail includes one outlier. This paired measurement predates the subsequent zero-to-positive visual-throttle correction; it compares the collision implementation, not the byte-exact final presentation artifact. No general TPS or memory improvement, whole-server percentile guarantee or large-crowd scalability claim is made. Full hosted workloads remain CI.

## Evidence integrity and limits

Initial before/native failures, incorrect fixture assumptions and the oversized old profile OOM are retained in compressed logs and archive manifests. The latter is excluded from profile statistics; the bounded single-site pair completed normally. Incorrect native fixtures were corrected for real braking distance, ambiguous shared-node clicks, source resistance-tail damage, mob-drop-vs-wreck classification and packing only stopped trains. No old test was removed, failed save reset or output artificially filled.

During real-client QA the launcher's original `spawn-animals=false` caused summoned cows to be discarded by vanilla ServerLevel.shouldDiscardEntity, independently of impacts. Those two attempts are retained and are not counted as kills. A normal zombie remained valid, died with the driver's native Monster Hunter attribution, and changed source speed1.2→0.9717247150936152 while advancing3.0079196927871blocks in the same paid tick. That check also exposed a brief false-zero HUD from the existing three-tick projection throttle. A zero-to-positive transition now bypasses the throttle, with a native assertion comparing projected and canonical positive speed. The313 QA default permits animals for the final replay. Native/client logs before that final presentation correction are explicitly named as such.

`v0312-world-archive.json`, `baseline-world-archive.json` and `failed-world-archives.json` record byte-compared lossless archives outside git, not deleted user worlds. The current final fixture and real-client world remain separate. Initial real EN screenshots precede only the later HUD wrapping improvement. Final client/reload results must be read from VALIDATION.md; screenshots alone do not certify every curve/junction/angle/audio case. Previous full hull-angle/moving multi-consist visual acceptance is not silently promoted to complete by this focused follow-up.

## Final actual-client and saved-world checks

A separate release JVM first retained exact pre-presentation-fix stopped state. The EN release client then accelerated normally from rest with the same finite original coal, using W and ordinary 20TPS steps (270 ticks, not a velocity/NBT injection). At source1.2/259.2km/h it killed a cosmetic named ordinary10HP cow: native death log, positive source1.0921516470508803/235.9km/h and immediate236km/h HUD. The next three ordinary cows died in travel order in one paid tick, leaving0.6707071644230977/144.9km/h and continued travel. Screenshots show positive HUD and native engine particles. Normal unfrozen movement and braking followed; all three saved trains stopped before exit. Sound assets are unchanged and this environment's audio-backend warnings prevent a new subjective audio pass claim.

`client-ledger.json` verifies an independent release-server JVM on the same untouched real-client world: exact full canonical tags for3trains/12cars,155iron,493828mB and384000000J coal+buffer+spent, including55560000J spent; no wrecks. `client-rail-ledger.json` verifies all30paid rail owners and actual EN/RU x0/x8 starter plus both extensions with matching immutable endpoints, headings, costs and UUIDs. This is a real player-world check in addition to the native momentum checkpoint. Packed profile outputs in separate distant chests are preserved, outside the declared twelve-car scene ledger.

The initial regular-server focused TestCommand attempts are retained, not certified: without a nearby player/full template ticking chunks some structures stayed gray in preparation; a same-JVM arena retry also produced a retained old-case failure. Final guards therefore run in the actual GameTestServer on a separately named fresh fixture. No test is disabled and no failed world or resource checkpoint is reset. `focused-beacons-incomplete.json` explicitly records the uncertified attempt; the helper script is a read-only diagnostic, not a successful test report.

Final certificate: native-release.log.gz records571/571required native tests and BUILD SUCCESSFUL on the release code, with the optional runtime above. New checkpoint26864cdd-55fa-43bf-9f9a-cd371bd3593e is retained as native-release-momentum-checkpoint.dat; the prior exact independent-JVM checkpoint remains in the byte-compared archived three-run world. The final world is separate and retained live. No manual-stand success is substituted for this native release result.
