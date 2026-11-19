# 0.31.2 — supplied Track interval crash

Baseline: published `f17c22fb64aa8633b3473033185cf8f4853e41fb` (0.31.1). This is a focused server interval-conversion correction; source physics, collisions, rail geometry, network/rendering and train NBT schema1 are unchanged. No0.32 work.

## Actual report and deterministic cause

The user supplied `crash-2026-11-19_13.34.30-server.txt`, UUID `53abbe4a-fc30-46a9-9c84-4ba5d256a3e3`, Minecraft1.21.1 /NeoForge21.1.252 /Java21.0.11 on an integrated server. The first FactoryCraft frames are `TrackOccupancy.Interval:10 -> span:109 -> TrainSimulation.tick:136 -> TrainData.tick:293`. [reported-stack.txt](reported-stack.txt) retains the exact exception stack and original attachment SHA256; unrelated player/hardware/world-seed details are omitted.

`TrainPath.Leg.end()` is `begin + length`. Even valid route data can have `(begin + length) - begin > length` because the sum/subtraction rounds at the accumulated origin's precision. For a reversed leg, the former conversion `min = length - ((end) - begin)` then becomes slightly negative and the strict `Interval` constructor throws. This depends on route direction/length/origin and covering the endpoint, rather than an actual 120km/h speed limit.

Before the correction, the newly added deterministic test using `begin=32`, `length=13.1` fails with precisely `IllegalArgumentException: Track interval`; a second seeded guard catches noncanonical full-edge endpoints. The unmodified production source for that run is f17c22f; the before-run log and XML are retained as `junit-before.*.gz`. No exception was invented/caught to obtain a pass.

The correction preserves exact0/length when clipping at leg endpoints, clamps only the global-to-local coordinate conversion to the physical edge domain, then reverses. `Interval` remains strict; negative/nonfinite/inverted claims still throw. No claims are omitted, speeds capped, fuel refunded, collisions skipped, or runtime chunks forced.

## Checks

Commands use Java21, NeoForge21.1.252 and the existing Gradle cache:

```
./gradlew test --tests factorycraft.simulation.trains.TrainKernelTest
./gradlew build
JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 ./gradlew runGameTestServer -PgameTestFixture=v0311-intervalfix
JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 ./gradlew runGameTestServer -PgameTestFixture=v0311-intervalfix
```

* Before-fix focused JUnit:20tests,2failures retained. After-fix focused JUnit:20pass.
* Full build/JUnit:317pass,0failure/error/skips;314previous cases retained. Seeded coverage checks4000 shifted fractional/curved/directional combinations, partial clips, reversed query bounds, exact full-edge bounds and rejection of another owner's overlap. Strict malformed-interval rejection is independent of clipping.
* First fresh native world:560/560required pass, retaining all559previous cases and thirty finite production chains. No existing test or evidence was disabled/deleted/reset.
* Independent-JVM full native reload:560/560required pass on the same untouched world. The reverse-boundary checkpoint UUID and exact canonical state verify after real TrainData loading. Both runs shut down/saved normally.

The added native test uses actual protected rail placement and item-paid stock/coupling. A one-time declared setup reframes the three-car consist onto a real reverse curve and seeds7coal/31named diamonds/234567named mBwater. It starts from an explicit120km/h moving phase, executes three real `TrainNetwork.control` / `TrainSimulation.tick` cycles across the fractional end, checks valid/exclusive canonical claims, unchanged health/no wrecks and the exact28MJ fuel+buffer+spent ledger. It then quarantines the saved consist via normal unload handling, never refilling it later.

Actual native failing-math input: `begin=128`, `length=36.893655439306016`, old reversed minimum `-1.4210854715202004E-14`. Checkpoint train UUID `cfa8d23d-0c73-47c4-aae8-5877a083da85`; the saved three-car body straddles this boundary. Another JVM must load it through the real `TrainData.load -> TrackOccupancy.span` path and compare exact complete train/car/route/component/fuel/motion NBT. `reverse-checkpoint-created.dat` retains the initial checkpoint without copying/resetting the whole world.

## Limits

The user's actual save/rail scene was not attached and has not been replayed. The deterministic and real-world native reproducer targets the supplied failing server path; it is not a claim that every unrelated possible crash has been eliminated. No new graphical client or observer test is required/claimed for this server-only arithmetic change; the previous release's actual client/maximum-speed/visual evidence remains historical. There is no performance improvement claim or repeated full factory/MIXED benchmark. Hosted optional/full/MIXED/fluid/train jobs remain GitHub Actions. The new diagnostic world is separate from every historical fixture and user save.

Final native first/reload logs are retained next to this file. `checkpoint-comparison.json` records native canonical equality and the two checkpoint hashes; the diagnostic world remains intact at `run-gametest/v0311-intervalfix/world`. No benchmark world or prior fixture was removed.
