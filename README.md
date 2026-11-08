# FactoryCraft: Industrial Evolution — 0.6.1

Minecraft **1.21.1**, NeoForge **21.1.252**, **Java JDK 21**. MIT; original procedural industrial textures. English and Russian UI.

Это играбельное ядро этапов 1–2 и начало энергетики и жидкостной логистики этапа 3, а не законченная Factorio/Space Age. Мод добывает конечную руду, питает машины через столбы и автоматически производит пластины, шестерни, провода и схемы. Двухполосные конвейеры отображают предметы без создания Entity, останавливаются при заторах и продолжают движение после освобождения выхода. Содержимое и прогресс сохраняются.

В 0.6.1 железо, медь, уголь и камень генерируются **крупными поверхностными жилами** с настраиваемыми размером, частотой и диапазоном запаса на плитку. По умолчанию радиус 8–20 блоков, запас 500–5000 единиц, центр богаче края. Настройки: `config/factorycraft-worldgen.toml`; новые жилы появляются в новых чанках. Большие машины устанавливаются **от центрального блока основания**, синяя ячейка превью показывает выбранный центр. Все габариты, защита установки и один предмет на целую структуру сохранены. Миры 0.6.0 продолжают работать; для версий до 0.6.0 нужен новый мир. См. [RESOURCE_FIELDS.md](RESOURCE_FIELDS.md) и [MACHINE_DIMENSIONS.md](MACHINE_DIMENSIONS.md).

Доступны вода → насос → трубы → резервуар, вода → котёл → пар → двигатель → электросеть и конечная нефть → помпа → НПЗ → газ → химический завод → пластик/сера. Примеры `/factorycraft demo`, `smelter`, `waterworks`, `steamworks`, `oilworks` перестроены под полные габариты. Нужна загруженная свободная область **30×8×13** рядом с игроком. Нагрузочный стенд сохраняет 10 000 лент, 1000 производителей и 500 манипуляторов, включая настоящие объёмы всех машин.

## Установка

Установите Minecraft 1.21.1 и NeoForge 21.1.252, положите `factorycraft-0.6.1.jar` в `mods/` клиента и сервера. Новые руды генерируются в новых чанках обычного мира. Для сборки нужен полный JDK 21, одного JRE недостаточно.

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

0.6.1 generates large deterministic surface veins with configurable radius, spacing and per-tile stock ranges. Large machines are item-placed around the selected horizontal base center, with matching green/red volume previews. One item builds the complete protected multiblock; one controller owns all resources. Machine harvesting keeps inventory, fluids, FE and progress in one portable item. Existing 0.6.0 structures and reserves are preserved; new veins generate in new chunks. Worlds older than 0.6.0 require a fresh start. Minecraft heights are original 3D design choices. See MACHINE_DIMENSIONS.md and RESOURCE_FIELDS.md.

Existing automated iron/component, water/steam/electricity and oil/plastic chains remain playable, with EN/RU menus and original temporary industrial art. Splitter geometry is now 2×1 with two forward exits; full dual-belt throughput, filters and priorities remain partial. Research, railways, robots, combat and space remain planned. Build with JDK21 using the commands above.
