# Release 0.25.0 — bounded resource and fluid observations

[Comparison, ranges and limitations](validation/v0250/comparison.md), [pure geometry driver](tools/profile_resources.py). Baseline is 0.24.1 `dd7c815`; resource geometry and fluid gameplay deliberately change, so this is not an optimization claim.

Three warmed isolated-JVM comparisons measure radius 20/depth 12 and radius 48/depth 64. Potential ore owners grow **976 → 7867** and **5634 → 228447**; sampling allocates about 58–60% more and timing varies substantially. Real native feature fixtures create deep resource BEs on flat/slope/cliff/river terrain and report CPU/allocation separately. Full natural-world chunk throughput and retained ore memory are unmeasured.

A real dedicated **10000-pipe** profile and independent-JVM hash reload pass with new 1:10 units and yield oil. Stable/construction/split mean ticks: **2.698 / 3.285 / 2.777 ms** in the final-code run; published 0.24.1 medians: **3.043 / 3.694 / 3.577 ms**. The earlier development run was 3.257 / 3.975 / 4.150 ms and is retained separately. Final stable maximum is 65.410 ms, above the 0.24.1 published median maximum 60.310 ms. Gameplay/rates differ and samples are limited; no faster TPS claim follows. Full/MIXED/optional jobs remain GitHub Actions work.

Historical 0.24.1 measurements below describe that earlier optimization and its unchanged hydraulics.

---

# Fluid performance 0.24.1 — 11 November 2026 (Europe/Moscow)

This compares the **0.24.0 segment runtime at e15bacd**, not the older 0.23 local-pressure model, against the optimized segment runtime. The hydraulic rates, 1000/16000/1000-mB capacities, 20-FE pump cost, pressure quotas, recipe amounts, filters, components, four circuit colors and schema-2 save format are unchanged.

[Complete tables and run ranges](validation/fluid-v0241/comparison.md), [kernel driver](tools/FluidTopologyProfile.java), [table generator](tools/analyze_fluid_performance.py). Raw JSON/JSONL and JFR hotspot extracts are in validation/fluid-v0241. JFR recordings remain with their isolated worlds under run-benchmark; neither failed worlds nor old checkpoints were reset.

## What profiling found

Before optimization, JFR attributes about **56.7% of sampled kernel allocation pressure to TreeMap entries**, 23.0% to visited-map resize and 11.2% to copied arrays. CPU samples include comparator calls, tree balancing/first-entry/deletion and visited-map lookup/resize. The world adapter additionally fanned out member dirty/sync notifications at every full freeze/publish. Stable port fill/drain already avoided rebuilds; `externalChanged` was an unconditional structural path for local-buffer/registration notifications, not proof that every ordinary flow rebuilt the graph.

The implementation now uses an indexed pending heap, sorted small neighbor arrays, build stamps, affected-build cancellation, an exact one-projection-per-member freeze, budgeted ordering/staging/cache preparation and one publication flag. Ordered traversal skips sorting. Compatible unfiltered true leaves and redundant in-pool edges update locally; ambiguous multi-neighbor placement retains the original conflict-boundary choice. Pump-boundary changes during staging cancel only affected prepared metadata; they do not expose partial pools. Counted chunk memberships and queued notifications avoid stale cache references and member-wide sync. Local prefix/bounds updates and unavoidable ownership freeze remain O(N); no hard CPU deadline is promised.

## Independent kernel results

Same Java 21, `-Xms256m -Xmx512m`, identical logical chain/reservoir layout, finite 8000-mB stock, three 1000-member warmups and three measured rounds at **100/1000/5000/10000**. Each round contains 10000 fill/drain pairs and 24 split/merge, leaf/tank add/remove and 16-member availability cycles. Operations include full settlement with a 256-unit advance budget; ledgers are checked after each lifecycle cycle. These are pure Java operations, **not Minecraft TPS**.

Selected 10000-member results (median of per-round means):

| Operation | 0.24.0 | 0.24.1 | Allocation old → new |
|---|---:|---:|---:|
| Add one terminal pipe | 2.197 ms | 0.0085 ms | 814.05 → 0.41 KiB |
| Remove terminal pipe | 2.117 ms | 0.0871 ms | 813.71 → 0.06 KiB |
| Add a leaf tank | 2.203 ms | 0.0028 ms | 814.05 → 0.41 KiB |
| Complete split | 2.056 ms | 2.689 ms | 797.32 → 148.34 KiB |
| Complete merge | 2.110 ms | 2.694 ms | 813.62 → 165.72 KiB |
| Availability cycle | 4.413 ms | 5.360 ms | 1610.82 → 314.02 KiB |

The tradeoff is explicit: complete split/merge work is about **28–31% slower** in this workload, although per-tick work is bounded and allocation is lower. Ten thousand content pairs cause **zero** connectivity rebuilds/freezes in regression tests; their measured CPU time is not claimed universally faster.

Three single-trial creation samples were unstable (old median 7.279 ms, new 17.938 ms). A separate followup uses eight size-matched warmups and **30 creations per size**, retaining both data sets. At 10000 members its mean is **9.109 → 7.374 ms**, median **8.864 → 2.457 ms**, but p95 worsens **13.653 → 20.907 ms** and maximum **14.451 → 21.591 ms**. Do not interpret the best creation trial as a guaranteed improvement.

## Actual dedicated Minecraft server

New `runFluidBenchmarkServer` profile uses **10000 main pipes, eight attached main tanks, forty branch pipes, sixteen independent powered/circuit-controlled pump systems and a real finite oil → refinery → chemical plant → plastic line**. In total there are **10151 fluid owners: 10095 pipes, 40 tanks, 16 pumps**, plus real generator/pole/circuit/machine/part/container blocks. Zero clients are connected; world/block/capability work runs on the normal server thread at the Minecraft scheduler's 20-TPS target.

The same marked, flat isolated layout and finite stocks run three cycles per world: A stable far-end source/consumer access; B sequential construction/demolition; C filled central split/replacement; D simultaneous independent systems/chemistry; E genuine probe-chunk unload/reload. An actual center chunk unload requires releasing its surrounding 5×5 forced-ticket neighborhood; surrounding chunks can be demoted too. Pinning belongs exclusively to this opt-in harness. The runtime does not acquire chunk tickets.

Two independent baseline worlds/JVMs (`before-e`, `before-f`) and two final worlds/JVMs (`after-f`, `after-g`) are compared. Each has 360 stable ticks plus construction, split/merge, natural unload and reload windows. Times are server-tick work, including scripted block operations and real simulation; scheduler sleep, initial chunk pin/generation, explicit warmup GC, checkpoint save and JSON/JFR writing are separated. Thread allocation is sampled at tick boundaries; warmed whole-server heap is measured after GC. Independent phase samples are not pooled into invented percentiles.

| Server metric (median per-run summary) | 0.24.0 | 0.24.1 | Change |
|---|---:|---:|---:|
| Stable mean | 2.784 ms | 3.043 ms | +9.3% |
| Stable p95 | 5.236 ms | 6.139 ms | +17.3% |
| Stable p99 | 13.982 ms | 13.337 ms | -4.6% |
| Stable maximum | 43.673 ms | 60.310 ms | +38.1% |
| Construction mean | 12.229 ms | 3.694 ms | -69.8% |
| Construction p95 | 25.794 ms | 10.166 ms | -60.6% |
| Construction p99 | 33.112 ms | 16.936 ms | -48.9% |
| Construction maximum | 40.705 ms | 49.344 ms | +21.2% |
| Split/merge-window mean | 5.908 ms | 3.577 ms | -39.4% |
| Split/merge-window p99 | 26.703 ms | 15.070 ms | -43.6% |
| Split/merge-window maximum | 31.054 ms | 27.301 ms | -12.1% |
| Unload-window mean | 10.504 ms | 10.850 ms | +3.3% |
| Reload-window mean | 9.283 ms | 4.166 ms | -55.1% |
Observed construction means range **11.722–12.736 / 3.442–3.946 ms**; stable means **2.654–2.913 / 2.989–3.097 ms**. One final construction run peaks at **74.983 ms**. Unload-window means also worsen **10.504 → 10.850 ms**, with few unload samples and a 50.418-ms observed maximum. The previous final-source development pair (`after-d/e`, retained as `server-after-pre-boundary*.json`) had construction mean 3.399 ms but a 67.043-ms split-window maximum. Earlier stable means varied 2.456–2.968 ms. None is substituted for the newest final pair; averages and tails remain variable. Improved topology averages do **not** establish better stable TPS or uniformly improved tails; precise slow-frame attribution remains followup work.

Whole-run rebuilds drop **923–929 → 69**, vertex visits **2.555–2.560 million → 404189–404228**, and BE sync requests **1110579–1110581 → 15081**. New finalization moves (about 4.48 million) are counted separately; they are not free or old-equivalent vertex visits. Segment-wide setUnsaved(true) requests drop **69949–69976 → 472–478** because already-dirty chunks and bulk member notifications are deduplicated; these are not disk writes or all chunk mutations. Actual JFR sent packets are **zero with zero clients**, so no multiplayer packet-reduction percentage is claimed.

Construction thread allocation is **1605–1611 → 830–837 KiB/tick**; split windows **662–726 → 481–489 KiB/tick**; reload **722–993 → 215–285 KiB/tick**. Warmed whole-server heaps are about **270.2–270.6 / 269.2–269.3 MiB**: a small contextual difference, not a standalone retained-graph measurement.

Each final main pool retains 500000 mB, the saved finite external source bank 964000 mB and collected consumer output 36000 mB: **1500000 mB exactly**. Auxiliary systems retain their **64000-mB** initial balance including collected output. Native machine recipes really consume finite inputs and produce plastic; saved baseline output chests contain 30 bars, final profiles assert actual positive output (31 bars in the final runs). Chemistry has recipe-specific volume conversions rather than a false cross-fluid mass invariant.

Every successful fixture is verified in a **separate server JVM** against a SHA-256 of all canonical authoritative BE NBT, plus saved main ledger counters. The unchanged 0.24.0 `before-f` checkpoint also passes under the 0.24.1 runtime. Verification never refills, resets or regenerates a mismatched checkpoint.

## Run and repeat

```sh
./gradlew build
./gradlew runFluidBenchmarkServer -PfluidFixture=fluids-v0241-local-a
./gradlew runFluidBenchmarkServer -PfluidFixture=fluids-v0241-local-a -PfluidMode=verify
python tools/analyze_fluid_performance.py
```

Use a **new** validated fixture name for another measurement. The marked directory/loopback guards reject existing measure worlds or arbitrary directories; no reset task is invoked. Reports/JFR/checkpoint remain under that fixture. CI runs this separate profile and restart; existing full/MIXED/optional jobs remain independent. Regression criteria are exact ownership, nonstarving unrelated rebuild progress, bounded advance work and zero connectivity work for stable content operations, not processor-specific millisecond thresholds.

Known failures are preserved: `before-a` was a Gradle configuration-cache closure problem; `before-b` placed a stem in the tank's physical footprint; `before-c` released only a center ticket that neighboring forced chunks still retained; `before-d` originally checkpointed in Pre before the final legitimate world tick. The corrected harness checkpoints in Post, and **new** `before-e/f` worlds pass. The first optimization changed which side claimed an empty conflicting bridge; multi-neighbor deferral restores the original policy and the unchanged existing GameTest passes. None of these worlds/checkpoints was reset to hide a failure.

---

# Current 0.20.0 fixture notes (10 November 2026, Europe/Moscow)

Normal poles now have complete two-block structures and persisted explicit edges. The existing full plan remains 10000 belts / 1000 producers / 500 inserters / 250 generators; it adds 749 pole parts to the previous physical count: **45998 cells / 33249 parts / 1999 electrical nodes**. Full and mixed execution/reload are left to CI for this task; older timings below are historical and are not measurements of the new topology.

Each profile now has its own marked directory: `run-benchmark/full-v0200`, `smoke-v0200`, `mixed-v0200`. `-PbenchmarkFixture=smoke-v0200-probe` (or another validated `profile[-name]`) selects an independent fixture for *both* preparation and runtime. Marker, symlink, nonempty-world and existing-checkpoint guards remain. Do not reset old evidence to rerun a measurement.

The first smoke measurement caught a harness regression: the intentional pole-removal probe restored only the old one-block base, so the new upper cell was missing. The old failed world/log remain at `smoke-v0200` / `/workspace/work/v0200-smoke-measure.log`; a subsequent attempted named run still pointed runtime at that old world and correctly refused occupied cells. Both problems were fixed in the harness: explicit no-drop whole-pole removal, ordinary complete StructurePlacement restoration, and one shared validated directory configuration. No failed world was reset/refilled. The corrected measurement uses fresh **smoke-v0200-probe**, with **100 belts, 20 producers, 5 generators, 10 inserters, 14 two-block poles / 664 parts, 818 total cells, 7 harness-pinned chunks**. All moving segments/producers/inserters must work, and exact material/FE ledgers and reload hashes remain mandatory. Current results/evidence are in [VALIDATION.md](VALIDATION.md).

---

# Нагрузочный стенд FactoryCraft 0.13.0

Стенд запускает **настоящий выделенный Minecraft 1.21.1 / NeoForge 21.1.252**, размещает обычные блоки мода и использует штатный серверный цикл с целевой частотой 20 TPS. Это отдельная проверка мира, не замена GameTest или независимый тест математического ядра.

## Запуск

Нужен JDK 21 с JFR. Для первого запуска:

```sh
./gradlew build
./gradlew runBenchmarkServer
./gradlew runBenchmarkServer -PbenchmarkMode=verify
```

Сервер автоматически завершается после измерения или проверки. JSON и JFR находятся в `run-benchmark/reports/`. Gradle завершится с ошибкой при нарушении инварианта, несовпадении сохранения или отсутствии успешного отчёта.

Сохранённые диагностические миры нельзя сбрасывать или заполнять заново. Для нового смешанного измерения используйте отдельное имя, как показано ниже. Задача `resetBenchmarkWorld` предназначена только для явно разрешённого удаления одноразового стенда; при продолжении разработки существующие результаты сохраняются.

`resetBenchmarkWorld` удаляет только специально помеченный каталог `run-benchmark`, включая его отчёты. Скопируйте нужные результаты перед новым замером. Задачи отказываются использовать непустой непомеченный каталог или ссылку на другой каталог; существующий стенд не заполняется ресурсами повторно. Обычные `run/`, `run-gametest/` и пользовательские сохранения не используются. Тестовый сервер слушает только loopback, порт 25575.

Малый профиль `-PbenchmarkProfile=smoke` предназначен для отдельного свежего checkout с пустым `run-benchmark/`. FULL и SMOKE используют один корневой каталог, поэтому не переключайте их в сохранённом диагностическом мире. Для продолжения разработки оставляйте FULL нетронутым и используйте отдельный именованный MIXED-профиль ниже. В CI оба полных профиля и их повторные запуски выполняются на свежем checkout после обычных тестов; отчёты и JFR входят в артефакт `factorycraft-build`.

## Что реально работает

Полный профиль размещает 45 249 блоков в 200 принудительно тикающих чанках отдельного плоского мира:

| Оборудование | Количество |
|---|---:|
| Обычные двухполосные конвейеры | 10 000 |
| Обычные электрические печи | 500 |
| Сборочные автоматы | 250 |
| Промышленные печи 3×3×3 | 250 |
| Механические манипуляторы | 500 |
| Дополнительные угольные генераторы | 250 |
| Электрические столбы, включая соединительные | 749 |
| Входные сундуки | 250 |
| Нетикающие части всех 1250 машин | 32 500 |

Конвейеры образуют 100 замкнутых колец по 100 сегментов на десяти высотах. Изначально на каждой полосе каждого сегмента находится один медный слиток: всего 20 000 внутренних носителей, без ItemEntity. Проверяется движение **каждого** сегмента за окно замера и реальные передачи между сегментами.

Производственная ячейка: сундук → манипулятор → электропечь → манипулятор → сборщик; рядом работают вторая электропечь и промышленная печь. Печи получают по 64 сырой железной руды, сборщик — 256 пластин, сундук — 64 руды, генератор — четыре угля. Это ограниченный, заранее подготовленный запас для воспроизводимого замера, а не бесконечная подача предметов или готовой FE. Машины действительно исполняют JSON-рецепты, расходуют энергию и выдают результаты. Манипуляторы используют обычные capabilities. Дополнительные корпуса игроку не требуются; промышленная печь формируется по тем же правилам контроллера и частей.

Все ячейки объединены в **одну** электросеть из 1 749 узлов. В середине измерения удаляется столб, через 20 тиков восстанавливается; рассчитываются реальные снимки графа, карантин и распределение энергии. Каждый из 1 000 производителей должен и работать, и завершить рецепт; каждый из 500 манипуляторов должен доставить предметы. Неактивная декорация не засчитывается как нагрузка.

Только этот явно запущенный диагностический сервер удерживает свои чанки. Производственная симуляция обычных сохранений не получает права принудительной загрузки чанков.

## Измерения и инварианты

После размещения выполняются 200 тиков прогрева и 200 тиков стабилизации после запуска JFR. Затем измеряются 600 серверных тиков — примерно 30 секунд работы. Размещение, перепись инвентарей, запуск/остановка JFR, запись отчётов и сохранение мира исключены из времени тика. Обработка обычных блоков/графов и удаление/восстановление контрольного столба входят в замер.

Отчёт содержит среднее, медиану, p95, p99, максимум и число тиков дольше 50 мс; частоту по настенным часам и целевую частоту сервера; GC, активность оборудования, передачи конвейеров, пересборки графа и переданную FE. Процентили рассчитываются методом ближайшего ранга. Наблюдаемая частота короткого окна может немного превышать 20 из-за границ окна или догоняющих тиков движка; она не означает более высокую настройку скорости игры.

Проверки:

* На конвейерах остаётся ровно 20 000 одиночных медных слитков, позиции допустимы, расстояние между носителями соблюдается.
* В инвентарях машин, сундуков и руках манипуляторов сохраняется ровно **128 000 железных эквивалентов**: руда/пластина = 1, шестерня = 2. Промежуточное нахождение предмета в руке входит в баланс.
* Начальная FE в буферах + реально доставленная FE = конечная FE + фактические затраты рецептов.
* Все части остаются привязаны к своим 250 контроллерам; граф снова стабилен и соединён; неожиданные ItemEntity отсутствуют.
* Перед остановкой сохраняется канонический SHA-256 авторитетных NBT-полей всех машин, конвейеров, рук, сундуков и частей. Порядок ключей CompoundTag нормализован; абсолютные клиентские временные метки исключены.
* Новый процесс сравнивает этот хеш **до первого тика симуляции**, затем даёт фабрике возобновить работу и повторяет агрегатный аудит. Успешная проверка записывает следующий контрольный снимок уже возобновлённого состояния; повторные `verify` допустимы. Несовпадение не исправляется и не заменяется новым эталоном.

JFR включает выборки CPU/выделения памяти/GC и встроенные события Minecraft PacketSent/PacketReceived. Сетевые счётчики отфильтрованы по временному окну измерения и считают закодированные байты пакетов, а не TCP-заголовки или предполагаемое количество сообщений. `beSyncRequests` — отдельный счётчик запросов синхронизации блоков, **не количество переданных пакетов**. Профиль выделения памяти статистический, его веса не являются точным счётчиком каждого выделенного объекта.

## Результаты этой версии

Linux amd64, 2 доступных vCPU, JDK 21.0.13, heap 2 GiB, 600 тиков, одна общая электросеть, **0 подключённых клиентов**. До/после использованы одинаковые параметры прогрева и JFR. Это пара локальных наблюдений в одной сессии; JIT, GC и нагрузка среды не фиксируются лабораторно.

| Метрика | До оптимизации | После |
|---|---:|---:|
| Среднее, мс | 32.153 | 24.494 |
| p95, мс | 42.985 | 32.395 |
| p99, мс | 50.375 | 56.240 |
| Максимум, мс | 76.040 | 91.566 |
| Тиков дольше 50 мс | 8 | 8 |
| Сборок мусора | 51 | 8 |
| Статистическая оценка выделений, GB | 5.700 | 1.216 |

Среднее время уменьшилось примерно на 24%, статистическая оценка выделений — примерно на 79%. p99 и максимум в этой паре не улучшились; восемь тиков остались дольше 50 мс. Это не гарантия отсутствия отдельных задержек.

Итоговый прогон: 1 129 200 межсегментных передач, результаты от всех 1 000 машин и доставки от всех 500 рук. Все 10 000 сегментов двигали предметы. 20 000 медных слитков и 128 000 железных эквивалентов сохранены; баланс FE точен. После перезапуска совпал полный хеш NBT до первого тика, затем прошёл повторный аудит. Отдельно проверены отказ повторно засевать мир, сохранение предыдущего отчёта, отклонение испорченного контрольного хеша и успешный следующий verify после восстановления исходного файла.

Сырые данные: [до](docs/benchmarks/0.2.2-before.json), [после](docs/benchmarks/0.2.2-after.json), [перезапуск](docs/benchmarks/0.2.2-restart.json), [проверки отказа](docs/benchmarks/0.2.2-guards.json), [выделения до](docs/benchmarks/0.2.2-before-allocations.json), [после](docs/benchmarks/0.2.2-after-allocations.json). Исходный профиль «до» снят в ходе разработки, до замены обходов/уведомлений; код до оптимизации не является отдельным релизом.

Для сводки собственного JFR (только стандартная библиотека Python):

```sh
python tools/analyze_benchmark.py run-benchmark/reports/full-measure.json
```

CI повторяет сценарий на другом оборудовании; его тайминги могут отличаться.

## Границы результата

`PASS` означает работоспособность, сохранность ресурсов/энергии и проверенное сохранение. Автоматического универсального порога времени нет: медленная машина не получает ложный провал корректности, а быстрый результат не выдаётся за гарантию TPS для любой фабрики.

Локальное измерение выполняется **без подключённых клиентов**. Нулевые сетевые пакеты соответствуют этому условию; пропускная способность для двух игроков и клиентский FPS пока не подтверждены. Запасы конечны, измерение короткое, мир плоский и без мобов/случайных тиков. Длинная работа, добыча/генерация обычного мира, другие моды и сложные развилки конвейеров могут давать другую нагрузку. Загрузки чанков по расстоянию игрока остаются отдельным сценарием.

Профилирование 0.2.2 уменьшило итераторы во вложенных обходах конвейера и уведомления соседей при сохранении внутреннего состояния. Частота отправки состояния конвейеров не изменена. Следующая проверка сети должна использовать реальных клиентов; затем можно обоснованно внедрять пакетные обновления.

## 0.3.0 compatibility/regression run

The old 0.2.2 saved full-factory checkpoint passes exact-state verification on 0.3.0, before the old marked world is preserved separately. A fresh full 0.3.0 workload and a new-process reload also pass. Raw results: [measurement](docs/benchmarks/v030-full-measure.json), [restart](docs/benchmarks/v030-full-verify.json).

The fresh local 600-tick run averaged 34.474 ms (p95 76.391 ms, p99 101.825 ms, max 290.202 ms, 103 ticks above 50 ms). A concurrent Gradle task listing overlapped the measurement; this is not a controlled performance comparison with 0.2.2. All belts moved, all producers returned results and all inserters delivered, with exact material/FE conservation. It contains no fluid nodes or connected clients. The new 10000-cell fluid JUnit exercise proves finite-volume conservation, not in-world fluid throughput/TPS.

## 0.4.0 compatibility check

`runBenchmarkServer -PbenchmarkMode=verify` passes on the saved 0.3.0 full-factory checkpoint with the new GridGenerator source classification. Exact authoritative state hashes, resumed material/FE ledgers and all multiblock identities remain valid. This workload still has no active thermal machines, so it does not establish thermal-world TPS or a new timing comparison. CI continues running a fresh full measurement and separate-process verification.

## 0.5.0 compatibility check

The saved 0.3.0/0.4.0 full-factory checkpoint passes `runBenchmarkServer -PbenchmarkMode=verify` on 0.5.0: exact authoritative hash, material/FE and multiblock identity checks remain valid. The full benchmark still contains no petroleum machines or oil deposits; this is old-factory compatibility, not measured petroleum TPS. CI retains the fresh full measurement/reload scenario.

## 0.6.0 full-footprint run

The new-world fixture expands every producer and generator to its real 3×3×3 volume: 45249 cells, 32500 non-ticking parts, 200 pinned chunks, 1999 electrical nodes. Two physical side ports connect the inserters; there is no compact test bypass. 10000 belts, all 1000 producers and all 500 inserters worked; 24069 recipes completed in the sampled window. Both measurement and a new-process exact-state reload pass. Material remains 20000 carriers and 128000 iron-equivalent units. Raw evidence: [measurement](docs/benchmarks/v060-full-measure.json), [restart](docs/benchmarks/v060-full-verify.json).

600 sampled ticks: mean 47.553 ms, p95 85.619 ms, p99 142.174 ms, maximum 172.466 ms; 265 ticks exceeded 50 ms. A software-rendered client and Gradle work ran concurrently. This is a correctness/liveness regression run, not a controlled speed comparison or guaranteed 20 TPS on arbitrary hardware. There were zero connected benchmark players; multiplayer packet throughput remains unmeasured. The larger thermal/oil/fluid network is covered by physical GameTests, not this item-only workload.

## 0.6.1 compatibility run

The unchanged 0.6.0 full checkpoint passes exact pre-tick hash verification and resumed resource/identity audits on 0.6.1: 10000 belts, 1000 producers, 500 inserters and 32500 parts. Centered item targeting leaves existing controller/part positions untouched. See [reload evidence](docs/benchmarks/v061-full-verify.json). This is a compatibility check, not a new timing comparison; CI additionally runs a fresh full factory and reload.

## 0.7.0 compatibility and full rerun

The preserved 0.6.0/0.6.1 full checkpoint passes exact pre-tick state verification on 0.7.0 (`docs/benchmarks/v070-compat-verify.json`). It was moved intact to a separate workspace backup before preparing a fresh marked benchmark world. Full measurement and independent reload pass (`v070-full-measure.json`, `v070-full-verify.json`): all 10000 belts, 1000 producers and 500 inserters work, with 32500 parts and exact material/FE/identity preservation. No ordinary world was touched or compact fixture substituted.

In a local two-processor run concurrent with Gradle/GameTests, 600 measured ticks averaged 66.874 ms, p95 167.252 ms, with 14.80 observed TPS and zero clients. This misses sustained 20 TPS and is not comparable to an isolated previous run. Four-lane throughput is separately proven by the kernel tests; the full fixture contains ordinary belts, not a huge splitter grid. These reports establish workload liveness/conservation/reload, not graphical performance, network bandwidth or a universal timing target.

## 0.8.0 saved-factory compatibility

The unchanged full 0.7.0 checkpoint passes 0.8.0 verification with exact pre-tick NBT, all 10000 belts/1000 producers/500 inserters, 32500 part identities and FE/material preservation (`docs/benchmarks/v080-compat-verify.json`). No local fresh timing comparison is claimed. Advanced chemistry has separate actual connected-line and five-cell persistence tests; the full standard fixture is not a giant petrochemical workload. CI continues fresh full measurement and independent reload for each published commit.

## 0.13.0 — mixed 3D workload and lifecycle

The legacy FULL checkpoint stays in `run-benchmark/` and is verified unchanged. MIXED uses a separately marked child, default `run-benchmark/mixed`, and never resets or reseeds an existing world:

```sh
./gradlew runBenchmarkServer -PbenchmarkProfile=mixed
./gradlew runBenchmarkServer -PbenchmarkProfile=mixed -PbenchmarkMode=verify
# A new measurement uses a genuinely new name, not an old failed fixture:
./gradlew runBenchmarkServer -PbenchmarkProfile=mixed -PbenchmarkFixture=mixed-new-run
./gradlew runBenchmarkServer -PbenchmarkProfile=mixed -PbenchmarkFixture=mixed-new-run -PbenchmarkMode=verify
```

It contains 10,000 transport controllers: 9200 ordinary segments, 100 ascending/100 descending slopes, 100 up/100 down lifts, 200 underground inputs and 200 nonowning outputs. There are 800 identity-only transport parts and 400 solid blocks inside virtual underground spans, in addition to the original full-size 1000 producers, 500 inserters, 250 generators, 32500 machine parts and 200 chunks. All three speed tiers are used. Exactly 9800 moving owners must work, all producers must produce and all inserters must deliver; the ledger preserves 20000 copper carriers and 128000 iron-equivalent units plus paid/generated FE.

After sampling, the harness releases its own tickets and counts native `ChunkEvent.Unload` callbacks for all 200 chunks, saves, reloads and audits resources and identities. A new JVM verifies the exact pre-tick hash before resuming. Runtime factories never force chunks; only the opt-in harness pins its declared footprint.

The first run retained all resources but timed out unloading 11 of 200 chunks. Its world/report were preserved. The fix caches block-ticking ticket/readiness state and freezes owners outside it. `-PbenchmarkMode=resume` is narrowly allowed for that failed unreleased MIXED unload report, performs no seed/reset and embeds the original measurement. It passed all 200 unloads. A fresh named fixture then repeated the complete measurement/unload/restart successfully. Evidence: `v0130-mixed-unload-before-fix.json`, `v0130-mixed-resume.json`.

JFR identified reservation/approach decisions and wrapped lane-list access as hot paths. The single-route optimization is compared against the general kernel at every step in a deterministic randomized test. Two isolated, otherwise equivalent 600-tick measurements on the same two-processor / 2-GiB server:

| Tick work | Before | After |
|---|---:|---:|
| Mean | 61.809 ms | 48.372 ms |
| p95 | 77.513 ms | 62.721 ms |
| p99 | 103.525 ms | 77.463 ms |
| Ticks over 50 ms | 599 / 600 | 162 / 600 |
| Kernel CPU over window | 28376.732 ms | 20261.826 ms |

Mean tick work fell 21.7%, kernel CPU 28.6%, with identical measured transfer/work/production counts. The observed optimized wall-window rate was 20.65 TPS (target 20; scheduling can catch up); this is not a claim that the server sustains more than 20 simulation ticks per second or that arbitrary machines guarantee 20 TPS. p95 still exceeds 50 ms. There are no benchmark clients or large chemistry networks, so client FPS, packet throughput and those factories are not established by this run. See `v0130-mixed-before-optimization.json`, `v0130-mixed-measure.json`, `v0130-mixed-verify.json`. Separate actual two-client QA is in NETWORK_QA.md.

## 0.14.0 — saved-world compatibility

The preserved legacy FULL and optimized MIXED checkpoints both pass exact pre-tick hash verification and resumed material/FE/identity audits on 0.14.0. See `docs/benchmarks/v0140-compat-verify.json` and `v0140-mixed-verify.json`. No existing world was reset/refilled. These runs establish compatibility; they do not measure a giant network of the new splitters or a new speed improvement. All-tier splitter throughput and mixed-tier headway/conservation are independently exercised, and a finite native three-tier sorting line delivers 32 iron/32 copper per tier. CI continues fresh FULL/MIXED measurements, unload/reload and separate-JVM checks.

## 0.15.0 — mechanical adapter compatibility

The preserved FULL checkpoint passes exact old pre-tick NBT and resumed material/FE/identity checks with the common inserter adapter extended for electric models (`docs/benchmarks/v0150-compat-verify.json`). Mechanical swing-12 timing and grid membership remain unchanged. This is a targeted saved-world regression, not a new giant electric-arm timing claim. Fresh FULL/MIXED measurements and optional profiles remain independent GitHub Actions jobs; they are not duplicated locally for this release.

## 0.21.0 workload adaptation

The main inserter ID is now electric. The actual workload therefore includes 500 additional grid consumers: FULL has 2499 graph nodes (749 Medium poles, 250 sources, 1500 consumers), 46747 physical cells and 33998 generated parts. Medium foundations/head heights are included; first supply poles moved one block to cover the actual input arms. The buffer ledger includes all arm FE, and measured arm work is debited separately as `inserterEnergySpentFE`. This changes the workload; historic timing reports are not a controlled comparison.

Audit JSON now reports both `beltItems` (weighted ItemStack counts) and `beltCarriers` (physical positions). Counted payloads 1–4 are valid; the standard CI seed remains one item/carrier, so full-profile totals remain 20000 carriers/items and 128000 iron equivalents. New pure/native stacked-payload tests cover the multiplied quantity separately. No additional hot-loop scan is introduced.

The bounded SMOKE measure and a separate-process verify passed in the separately marked `run-benchmark/smoke-v0210` world on 10 November 2026. It has 100 belt segments, 20 producers, 10 electric arms, 14 Medium poles/49 graph nodes, 832 physical cells/678 parts. Both quantity/FE ledgers and exact pre-tick restart hash passed. Another diagnostic process was active during measurement, so its 1.767-ms mean is evidence of this bounded run only, not an inserter performance comparison or a full-factory TPS claim. Full/MIXED measure/reload remain GitHub Actions work; old worlds/reports remain intact.
