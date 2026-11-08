# FactoryCraft: Industrial Evolution — 0.5.0

Minecraft **1.21.1**, NeoForge **21.1.252**, **Java JDK 21**. MIT; original procedural industrial textures. English and Russian UI.

Это играбельное ядро этапов 1–2 и начало энергетики и жидкостной логистики этапа 3, а не законченная Factorio/Space Age. Мод добывает конечную руду, питает машины через столбы и автоматически производит пластины, шестерни, провода и схемы. Двухполосные конвейеры отображают предметы без создания Entity, останавливаются при заторах и продолжают движение после освобождения выхода. Содержимое и прогресс сохраняются.

В 0.5.0 добавлена цепочка **конечная нефтяная залежь → нефтяная помпа → НПЗ → химический завод → пластик**. НПЗ 2×2×2 формируется одним предметом и сохраняет единственный инвентарь в контроллере. Есть сера, продвинутые схемы, JSON-рецепты с предметами и жидкостями, направленные порты и подсказки состава рецептов. `/factorycraft oilworks` строит настоящую линию с подачей угля и выгрузкой в сундук. См. [PETROLEUM.md](PETROLEUM.md).

В 0.4.0 добавлены **топливный котёл, пар и паровой двигатель**: вода превращается в пар по JSON-рецепту, двигатель расходует пар и питает старые машины и насос через столбы. Есть топливная автоматика, вёдра пара, интерфейсы и сохранение всех ресурсов в снятом блоке. `/factorycraft steamworks` строит пример с электропечью. Баланс и ограничения: [STEAM_POWER.md](STEAM_POWER.md).

В 0.3.0 добавлены **трубы, резервуар и электрический насос**. Насос берёт воду из природного источника, питается от существующей электросети и подаёт её по трубам; доступны вёдра, окно объёма и сохранение жидкости в снятом блоке. `/factorycraft waterworks` строит рабочую линию, `/factorycraft fluids` показывает диагностику. Модель, рецепты и ограничения: [FLUIDS.md](FLUIDS.md). Базовая нефтехимия добавлена в 0.5.0; расширенная перегонка и крекинг остаются в плане.

В 0.2.2 добавлен воспроизводимый нагрузочный стенд настоящего выделенного сервера: **10 000 конвейеров, 1 000 производственных машин и 500 манипуляторов** в общей электросети. Он проверяет работу оборудования, баланс предметов и FE, сохраняет JSON/JFR и проверяет мир повторным запуском. По результатам профилирования уменьшены выделения памяти в конвейерах и лишние уведомления соседей при сохранении внутреннего состояния. Методика и ограничения: [BENCHMARKS.md](BENCHMARKS.md).

В 0.2.1 промышленная электропечь **2×2×2 устанавливается одним предметом**: служебные части создаются автоматически, дополнительные корпуса не нужны. Проверяется весь объём; при нехватке места предмет не расходуется. Разрушение любой части возвращает одну машину, а содержимое выпадает один раз. Старые структуры 0.2.0 сохраняют прежнее поведение. Общие порты, GUI, скорость ×2 и пространственно индексированная электросеть сохранены. Подробности: [CHANGELOG.md](CHANGELOG.md).

## Установка

Установите Minecraft 1.21.1 и NeoForge 21.1.252, положите `factorycraft-0.5.0.jar` в `mods/` клиента и сервера. Новые руды генерируются в новых чанках обычного мира. Для сборки нужен полный JDK 21, одного JRE недостаточно.

```sh
./gradlew build
./gradlew runGameTestServer
./gradlew runGameTestServer  # второй процесс проверяет чтение состояния из region-файлов
./gradlew runClient
./gradlew runServer         # выделенный сервер разработки
```

На Windows: `gradlew.bat`. В облачной среде параметры HTTP-прокси и пути JDK задавались локально вне репозитория. Не копируйте их в пользовательскую конфигурацию.

## Первая фабрика

См. [QUICKSTART.md](QUICKSTART.md): направление, подключение энергии, слот топлива, рецепты и схема линии. В творческом мире с командами `/factorycraft demo` строит проверенную линию рядом с игроком, если место свободно. `/factorycraft profile` показывает конвейерную симуляцию, `/factorycraft power` — перестроение электросети. `/factorycraft smelter` создаёт отдельную автоматическую линию с мультиблочной печью. Интеграционные миры сохраняются отдельно в `run-gametest/world`.

## Документация и продолжение

* [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) — архитектура, этапы, следующая задача.
* [FEATURE_MATRIX.md](FEATURE_MATRIX.md) — фактическая реализация и ещё отсутствующие системы.
* [PETROLEUM.md](PETROLEUM.md) — залежи, НПЗ, жидкостные порты и смешанные рецепты.
* [STEAM_POWER.md](STEAM_POWER.md) — паровая энергетика, запуск и формат рецептов.
* [FLUIDS.md](FLUIDS.md) — жидкостная логистика, пропускная способность и ограничения.
* [BENCHMARKS.md](BENCHMARKS.md) — отдельный нагрузочный мир, методика и результаты.
* [VALIDATION.md](VALIDATION.md) — команды, автоматические проверки и ограничения доказательств.
* [ARCHITECTURE.md](ARCHITECTURE.md) — сохранение, транзакции, конвейерная и энергетическая модели.
* [AGENTS.md](AGENTS.md) — правила следующих изменений.

## English

Version 0.5.0 adds finite oil reservoirs, an electric pumpjack, a one-item automatic 2x2x2 refinery and mixed item/fluid chemistry. Crude becomes gas, gas plus coal becomes plastic, gas plus water makes sulfur, and the existing assembler can build advanced circuits. Dedicated tests cover resource ledgers, portable refinery identity, protection rollback, generated reserves and separate-process persistence. `/factorycraft oilworks` builds the working line. See PETROLEUM.md for ports, recipes and remaining scope.

Version 0.4.0 adds fuel-fired boilers, container-only steam and steam engines feeding the existing pole graph or FE extraction. Counted fluid recipes use datapack JSON and NeoForge sized fluid ingredients/tags. Fuel remainders, progress, fluids and generated energy persist; full outputs/buffers pause work. `/factorycraft steamworks` builds an example. See STEAM_POWER.md for the balance and bootstrap.

Version 0.3.0 adds six-way pipes, tanks and directional electric pumps connected to the existing power grid. Pumps feed source water through pipes into tanks; bucket interaction, portable filled blocks, component-sensitive NeoForge fluid capabilities, volume/energy UI and separate-process fluid persistence are covered. `/factorycraft waterworks` builds the powered example; `/factorycraft fluids` reports graph activity. See FLUIDS.md for the finite-throughput model. Steam power is delivered in 0.4.0; advanced oil chemistry remains planned.

Version 0.2.2 adds an isolated dedicated-server benchmark with 10000 real belt segments, 1000 production machines, 500 inserters and one shared power grid. It checks motion, production, item/FE conservation and separate-process persistence, exports JSON/JFR, and reduces profiled iterator/comparator-notification allocation in the factory hot paths. See BENCHMARKS.md for scope and reproducible commands.

Version 0.2.1 places a complete 2x2x2 industrial furnace from one item, with full-footprint validation and normal NeoForge placement protection. Breaking any part returns one machine and drops its contents once. Generated parts are not craftable resources. Existing manually assembled 0.2.0 structures retain their behavior; shared guarded ports, chunk-safe persistence and budgeted power rebuilding remain available. `/factorycraft smelter` builds an automated example in an empty area.

A working first-factory slice: finite electric mining → two-lane belts → mechanical inserters → electric smelting → assembly → a vanilla chest. Fuel the coal generator and place poles within their configured coverage (default 6 blocks). Face inserters from their source toward their destination; rotate with the factory wrench. Machine left slots accept ingredients, right slots expose outputs, and the bottom slot accepts generator fuel. Right-click a machine for recipes, energy and progress. Recipes and ore placement are datapack JSON. Later research, general chemical processing, railways, robots, combat and space systems are explicitly unimplemented.

The project includes JUnit kernel tests and server GameTests. `tools/generate_resources.py` regenerates the original textures and first-factory JSON files using Python's standard library. It replaces generated resource files; hand-authored recipe overrides should live in a separate datapack.
