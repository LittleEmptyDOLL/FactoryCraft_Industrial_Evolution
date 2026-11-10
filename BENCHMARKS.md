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
