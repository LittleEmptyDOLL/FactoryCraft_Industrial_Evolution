# Issue15 Gate2 — origin priority and docked/incoming ownership

This is a working tested continuation, not all Gate2/3 or full0.32. Base8663251passed all eight hosted jobs (44957031847). No0.33.

* `checks.json`, `environment.json`:360JUnit, initial600fresh/600independent then final600fresh/600independent, all596old cases/thirty finite chains retained, exact release JAR SHA and controls. Final fresh adds a real stale-frontier rank race; final independent also uses the public repeated-Automatic action to check cache invalidation. Saved resource/origin/order format is unchanged between those last runs.
* `native-first-failed.log.gz`:600complete, two new observer/status assertions failed; old596and both persistence seeds passed. The observer now follows the remaining train after packing the first; no-path is remembered when actually observed rather than demanded after the destination becomes full. No ownership/ledger assertion was removed.
* `native-fresh-600.log.gz`, `native-independent-600.log.gz`: real shared endpoint-junction priority, slower real path search, no-path fallthrough, actual unload/freeze/loaded-port recovery, FIFO and disabled dual-lease restart, real1coal fuel handoff and finite arrivals.
* `native-final-fresh-600.log.gz`, `native-final-independent-600.log.gz`, `build-final.log.gz`: final accepted runtime/metadata/cache checks, all600required/native and360JUnit. The1-node allowance only limits cooperative search work, never grants a fake route/motion/reservation.
* `fifo-*.dat`, `dual-*.dat`, `*-after-trains.dat`, both restart ledgers: exact frozen canonical snapshots, departure poses, positive distinct FIFO orders, no duplicate/phantom incoming IDs, retired owner absence. FIFO contains6cars/14component-bearing iron/128MJ; dual contains3cars/7typed iron/0initial train energy, then one real external coal supplies4MJ. Actual chest transfer, paid movement and final energy are asserted in native execution; the read-only parser validates the records and must not be mistaken for physical simulation.
* `source-review.md`: Factorio origin priority, explicit FIFO/no-origin-priority0/failure cooldown adapters, existing licensed MIT search, source lease/counting model. No new Create source/art was copied; existing37ea4afc notices/license remain packaged.
* Archive manifests retain the failed and final diagnostic worlds and three prior automatic worlds in lossless external archives. Each closed tree was byte-compared with tar before removing only its expanded copy for disk headroom. All previous raw reports/checkpoints remain committed. Do not run an archived fixture name without restoring its exact archive; do not reset/refill diagnostic or user worlds.
* `auto-measure.json`, `auto-verify.json`, `auto-checkpoint.nbt`, compressed logs: unchanged8train/150car/6144m/72device real profile, both directions/stop visits, actual chunk unload and exact separate-JVM owners;2573items/4814823mB/896MJ. Stable mean16.243ms/p9963.028ms/max69.750ms, allocation/p99higher than prior sample; no TPS improvement claim. Raw JFR/world remain in the marked current benchmark directory.

Reproduce with unused names, then reopen the same world:

```sh
./gradlew build
./gradlew runGameTestServer -PgameTestFixture=v0320-dispatch-example
./gradlew runGameTestServer -PgameTestFixture=v0320-dispatch-example
python tools/check_origin_dispatch_checkpoint.py validation/v0320-dispatch/fifo-final-before.dat validation/v0320-dispatch/fifo-final-after.dat validation/v0320-dispatch/dual-final-before.dat validation/v0320-dispatch/dual-final-after.dat validation/v0320-dispatch/final-after-trains.dat --output /tmp/origin-dispatch-ledger.json
```

The parser requires nbtlib. UI/render/protocol unchanged; no new graphical acceptance claimed. Current full factory/MIXED/fluid/manual/optional-mod runs remain hosted and are reported once after publication, not awaited.

Next in this same Issue15: real no-wait pass-through, temporary navigation/return, full component-aware waits; then operational groups/named interrupts/typed wildcards/editors and their client/restart acceptance. Broad16-heading/grade/arrival-art and congested-eight-train coverage remain incomplete.
