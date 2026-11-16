# 0.27.0 validation — authoritative Space Age scope

17 November 2026 (Moscow). Issue #7 was updated during development: **Space Age enabled** replaces the initial base-only interpretation. Final sources/criteria are in `design-checklist.txt`, `reference-sha256.json` and `tools/check_space_age_research.py`. The `base-spec-archive/` captures are superseded development evidence, not release acceptance.

## Completed local checks

- Final Java 21 /NeoForge 21.1.252 `./gradlew build`: **240 JUnit, zero failures/errors**. `junit-summary.json`, compressed build log.
- Native server: **440/440 required tests**, without optional mods and with FTB Teams +EMI +JEI +Jade. All 429 original cases remain, including the thirty archived/restored scenarios; `legacy-thirty-status.txt` verifies their required annotations and inclusion. No old test was disabled.
- Independent JVMs on `v0270-sa-final` and `v0270-sa-optional`: both `FACTORYCRAFT_V0261_DISK` and `FACTORYCRAFT_V0270_DISK` verified. New lab fixture checks all twelve component-bearing stacks, two modules, exact FE/remainder, both saved partial work values, productive credit and team selection. The earlier 438-case reloads already verified checkpoints; the final 440-case runs repeat and strengthen these checks.
- New coverage includes 7/12-pack JSON and stream bounds, individual filters, menu capacity 12+2+36, real physical lab-to-lab inserter transport, nine module profiles on relevant machines, beacon membership/payment/unload/no productivity broadcast, exact productive FE/science ledgers, zero-power pauses, repeat-level overshoot, original late gates and real vanilla result-slot/counted crafting.
- `tools/check_space_age_research.py`: all nine original SA module recipes/technologies/categories, research speeds 1–6, existing key costs, Automation 3=500, sandbox transport source gates and absence of fake future science/material production.
- Real client **EN without optional mods**: twelve-slot lab, source gates/search, Speed 3 recipe with tungsten carbide, and actual survival 2×2 handcraft: 10 circuits +10 gears +4 belts →one lab with all inputs debited. Original startup preconditions were explicitly supplied by the diagnostic factory command.
- Real client **RU with all four optional integrations**: localized tree/statistics/ID search, owner/casing Jade inventory/modules/FE, all nine modules found through tags in EMI, original tier-3 recipe shown in the viewer. Same current-format world was reopened in another client JVM after the required protocol bump; existing team ownership/modules/sciences loaded and research completed, retaining productive credit.

GUI worlds are separate fresh `FactoryCraft0270-SA-Base-QA` /`FactoryCraft0270-SA-Optional-QA`. Screenshots intentionally use explicit creative/admin science/module stock to inspect all future slots; they do not demonstrate survival production of future packs. Finite production-chain proofs are the native tests. EN search was exercised by localized name; RU by stable ID (synthetic xdotool Cyrillic typing dropped characters). Both localized UI sets and source names rendered correctly.

## Limits / outstanding CI

- No full factory/MIXED/10000-pipe benchmark duplicated locally and no TPS claim. GitHub Actions performs these independent release checks.
- No two-client cross-version negotiation test. Required research protocol `0.27.0` is declared incompatible with old peers; real current-version integrated client/server loading and menu snapshots were verified.
- Combined EMI/JEI dev mode logs duplicate **vanilla tag** recipe warnings; the changed FactoryCraft recipe card and metadata work. Headless OpenAL/Mojang service warnings and the existing missing `offshore_pump_moving` model warning remain in raw client logs; not represented as fixed here.
- Future planetary science/material items are metadata only. Real late industries, spoilage/quality/Research Productivity and both platform/satellite science sources remain deferred. Existing unrelated Minecraft manufacturing adaptations are not claimed identical to Factorio.
- Science mapping safely covers all twelve canonical IDs. Adding a noncanonical thirteenth type requires an explicit appended inventory/layout extension. Current generic repeatable cost growth is the existing bounded linear datapack model; future original infinite technology formulas remain a separate milestone.

## Evidence integrity

Compressed `logs/*.log.gz` retain complete raw logs, with concise markers in `verification-excerpts.txt`. Earlier failed/superseded worlds were never reset/refilled. Closed base-spec worlds are preserved byte-for-byte in verified `/workspace/work/v0260-evidence/v0270-base-spec-worlds-verified.tar.xz`; older large diagnostic world archives remain there too. Current SA fixtures remain in `run-gametest/`. Failures caused by old fixture timing/unlocks, ambiguous automatic sulfur selection and unbound optional-mod fake players were corrected in setup/expectations without injecting terminal products. The original incorrect Lab↔Assembler bootstrap recipe was replaced with source-defined counted components, not an early simplified unlock.
