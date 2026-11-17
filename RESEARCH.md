## 0.29 research and sampled production

Adds canonical Solar Energy, Accumulators, Concrete, Uranium Mining, the actual `mine:factorycraft:uranium_ore` trigger and Nuclear Power. `uranium_processing_research` is the research identity, distinct from the native probabilistic `uranium_processing` recipe. The virtual `uranium_ore` unlock gates drill capability, not a fabricated ore recipe. Trigger definitions carry a one-tick codec sentinel, never a paid science duration. Team namespaces/providers and saved ledgers remain unchanged.

Centrifuges use existing two-slot modules/beacons and common circuit gating; paid productivity credit earns an additional separately sampled output. EMI/JEI show mutually exclusive outcomes/probabilities and exact rational FE; the native centrifuge screen remains usable without viewers. Kovarex/reprocessing keep real missing late prerequisites and do not become survival production paths.

# Research — 0.28.0

## 0.28 military additions

The existing military science identity/slot now has genuine survival production. Military, Military 2, Stone Wall, Gun Turret and Military Science use original bills/prerequisites. Fifty-five source-derived finite combat/battery technology definitions share the existing per-force ledger and twelve science slots. Damage/speed effects apply to the correct ammo/turret categories; missing utility/space parents stay fail-closed. No infinite/offworld combat research is substituted. Source numerical manifest: tools/combat_reference.json; player guide: COMBAT.md.


The reference is **Factorio 2.1.24 WITH Space Age enabled, normal quality**. Space Age overrides: [base-data-updates.lua](https://github.com/wube/factorio-data/blob/2.1.24/space-age/base-data-updates.lua). Numerical sources: [Wube technology prototypes](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/technology.lua), [lab prototype](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/entity/entities.lua), [Lab](https://wiki.factorio.com/Lab), [Research](https://wiki.factorio.com/Research), [Science pack](https://wiki.factorio.com/Science_pack). No proprietary graphics/source were imported.

## Laboratory inventory and ownership

One 3×3 controller owns twelve stable science slots and **two separate singleton module slots**. Science slot order is automation/red, logistic/green, chemical/blue, military/grey, production/purple, utility/yellow, space/white, metallurgic, agricultural, electromagnetic, cryogenic, promethium. The original seven keep their indices; all five future packs already have separate physical slots. Keeping blue at index 2 is a layout choice, not an extra science type. Each science slot accepts only its own item identity; item components remain intact. Input/extraction capabilities expose the twelve science slots for physical inserters, including real lab-to-lab chaining through loaded casing contacts. Module slots are edited in the guarded lab menu; science transport cannot insert/extract modules by mistake. All parts delegate to the controller. Jade inspection includes installed modules without creating a writable capability or duplicate owner.

All twelve canonical packs are registered items with models, EN/RU names and creative/viewer visibility. Eight later packs still have no survival production. Red/green/blue/grey have genuine recipes; production/utility and later packs require their actual future industries. Future primary Space Science comes from orbital platforms and asteroid-derived materials. An explicitly approved additional Satellite route will produce **1000 packs per launch**, using a Space Age-cost rocket. Satellite is absent from original Space Age: this is a future FactoryCraft extension, not a base-game progression replacement. Neither source is implemented now. Five planetary sciences are identities/inventory metadata only. Research Productivity is part of the eventual Space Age progression, with its gameplay deferred. There is no standalone quality science pack.

`TechnologyRecipe` admits at most twelve known canonical Space Age pack identities (1–64 of each per unit), sixteen prerequisites, 64 unlocks, 100000 initial units and 72000 Minecraft ticks per unit. JSON and network validation use the same bounds as physical inventory. Other science identities/thirteenth packs fail closed: this bound already covers all canonical Space Age packs. Datapacks may define arbitrary subsets of these twelve and bounded technologies; noncanonical science identities require an explicit appended physical layout/registry extension. No accepted recipe can need more distinct packs than the lab owns slots. Catalogs allow 1024 technology definitions, coordinates 0–4096; the tree clips rendering/hit tests and derives pan limits from current definitions. Missing/cyclic dependencies retain visible disabled definitions and recipe locks; they are never treated as completed. High-tier definitions reference real future dependencies instead of substitute science technologies.

## Work, power and productivity

A base laboratory runs at speed 1 and **60 kW = 8 FE per active Minecraft tick** using the established 7.5 kW/FE convention. A technology's source seconds become seconds ×20 ticks; Factorio 60 TPS timings are not copied as Minecraft tick counts. Installed module modifiers are additive; the technology multiplier multiplies the resulting module/beacon speed factor. Energy is base 8 × additive module/beacon energy, with the original 20% minimum. Integer FE billing retains its denominator-10000 remainder across pauses, module edits, unloading and portable moves. No eligible packs/research, unavailable structure, circuit stop or insufficient FE means no paid work.

`LabWork` uses 100000000 units per base work tick, allowing the product of the two basis-point speed factors without rounding. Definition hashes identify saved per-lab partial work. Finite level keys stay distinct; repeatable research keeps one signature key because its per-unit pack/time cost is fixed while the team ledger grows the number of required units. This retains paid overshoot across mid-tick/multi-lab level completions without accumulating one history entry per repeat level. Switching selection keeps earlier partials. A bounded history limit stalls new definitions visibly rather than allocating unbounded history or charging for rejected work. Definition/cost edits cannot reuse incompatible old work; completed team levels survive reload. The schema stores at most 1024 partial records with bounded SHA-256 keys.

Productivity accrues proportionally to actual work with the installed module rate. The per-lab credit and exact per-definition integer residue persist. Whole bonus units commit through the **same team ledger** as paid units, without another pack debit. Normal units debit one full recipe pack set in a deferred-observer server transaction. Several labs cannot consume packs for an already completed final unit. Fast/short units retain work overshoot; pauses do not erase credit. This adapts Factorio to unit-by-unit item debit, rather than mutating partially consumed science-pack durability.

Loaded spatial beacon candidates include laboratories. Only paid, complete, block-ticking beacons broadcast speed/efficiency, with normal-quality `1.5 / sqrt(active beacons)` transmission. Productivity is never broadcast. No global laboratory scan or forced chunks is introduced.

## Force selection and progression

One selected technology belongs to each FTB Team; without FTB Teams, vanilla scoreboard teams or personal UUID scopes apply. An unavailable FTB API pauses rather than redirects research. Menu actions, science/module moves and network selection verify the current owner, physical reach, team and spectator status. Four circuit colors and direct research controls remain; trigger/sandbox/invalid technologies cannot be selected by these controls.

The former repeatable `lab_efficiency` power discount is removed. Six **finite** Lab Research Speed technologies map to Wube `research-speed-1..6`:

| Level | Added / cumulative speed | Units | Packs | Seconds/unit |
|---|---|---:|---|---:|
| 1 | 20% / 20% | 100 | red, green | 30 |
| 2 | 30% / 50% | 200 | red, green | 30 |
| 3 | 40% / 90% | 250 | red, green, blue | 30 |
| 4 | 50% / 140% | 500 | red, green, blue | 30 |
| 5 | 50% / 190% | 500 | red, green, blue, purple | 30 |
| 6 | 60% / 250% | 500 | red, green, blue, purple, yellow | 30 |

The six finite research-speed costs are unchanged by the Space Age prototype patch. Speed 1 requires Automation 2; speed 3 additionally requires chemical science, 5 production science, 6 utility science. The full actual definition/cost table is in `docs/RESEARCH_REFERENCE_0270.md`.

Original 2.1 startup triggers now count actual output: 50 iron plates → steam power; 10 copper plates → electronics; a crafted laboratory after both → automation science. FactoryCraft machines report only committed outputs; handcraft/smelting events report produced stacks. Scope is the producing machine's force or player team. Moving stocks/portable placement never reports production. Vanilla furnace output is attributed when a player actually takes it; unowned vanilla automation has no team attribution. Oil Processing triggers on the first successful pumpjack extraction after Oil Gathering. Trigger counters/levels live in the same persisted team ledger and do not steal current science selection.

Automation unlocks Assembler 1/long-handed inserter; Logistic Science is its own original 75-red-unit technology. Automation 2, processing units, chemistry, module families/tiers, fluid handling and existing logistics/circuit/pole research use pinned source costs/dependencies. Automation 3 retains its Space Age 500-unit cost and genuine purple dependency; beacons and late bonuses keep their real gates. Module tier 2 requires **200 red/green/blue/space units** and Space Science. Tier 3 adds genuine planetary/handling research and recipe materials; no base-game-only module unlock is used. They remain survival-locked until those chains exist. No fake late dependency definitions or early substitute costs are supplied.

Vanilla workbench/2×2 handcrafts are checked against the same recipe locks, via narrowly scoped server mixins before result publication and again at pickup/shift-click. A `craft_` recipe aliases its canonical factory recipe ID; creative is an explicit administrative exception. Starter recipes without an original technology lock remain available. Production machines retain their existing bound-team checks; old unowned diagnostic machines are an explicit admin-fixture behavior, not a survival unlock.

## Persistence and validation

Lab schema 3 writes all science, modules, FE/remainder, force, selected work key, per-definition partials and productive credit. The assembler/furnace/petroleum/drill resource owners remain unchanged. Old intermediate lab/research formats are intentionally unsupported; no worlds are deleted or migrated. See `VALIDATION.md` and `validation/v0270/` for executed tests and remaining checks. Native seven-pack definitions are injected only by tests in memory; they are not survival recipes or fake science sources.

The two pre-existing sandbox transport-capacity definitions also retain original Space Age seven-pack costs (including agricultural science); capacity 1 references the genuinely deferred Stack Inserter technology, and capacity 2 requires capacity 1 plus Inserter Capacity Bonus 7. They remain creative diagnostics until their real progression is implemented, without a new inserter recipe or industry.

The required research payload protocol is `0.27.0`, explicitly rejecting old peers before using the changed recipe/inventory wire layouts. Client and server must use the same release.
