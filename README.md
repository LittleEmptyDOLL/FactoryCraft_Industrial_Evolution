# FactoryCraft: Industrial Evolution — 0.8.0

Minecraft **1.21.1**, NeoForge **21.1.252**, **Java JDK 21**. MIT; original procedural industrial textures. English and Russian UI.

Это играбельное ядро этапов 1–2 и начало энергетики и жидкостной логистики этапа 3, а не законченная Factorio/Space Age. Мод добывает конечную руду, питает машины через столбы и автоматически производит пластины, шестерни, провода и схемы. Двухполосные конвейеры отображают предметы без создания Entity, останавливаются при заторах и продолжают движение после освобождения выхода. Содержимое и прогресс сохраняются.

В 0.8.0 доступны **продвинутая переработка нефти**, тяжёлая/лёгкая нефть, оба шага крекинга, смазка, серная кислота и аккумуляторы. НПЗ получил третий настоящий выход спереди слева, отмеченный **3**, без переноса прежних портов и жидкостей. Пять ёмкостей и именованные рецепты видны в меню. `/factorycraft petrochemistry` строит рабочую линию **конечная нефть + вода + пластины → сера → кислота → аккумуляторы**, с отводом побочных продуктов; нужна свободная загруженная область **30×8×16**. См. [PETROLEUM.md](PETROLEUM.md).

В 0.7.0 все 13 крупных типов получили **собственные цельные модели**, объёмные предметы, обозначения жидкостных патрубков и анимацию работающих механизмов. Размеры, установка от центра и один предмет на целую машину сохранены. Разделитель теперь имеет **две физические ленты / четыре полосы**, независимые порты манипуляторов и сохраняет до 16 предметов. Проверенная суммарная скорость насыщенного базового разделителя в транспортном ядре — 30 предметов/с. Приоритеты и фильтры пока отсутствуют. См. [MACHINE_VISUALS.md](MACHINE_VISUALS.md) и [SPLITTERS.md](SPLITTERS.md).

В 0.6.1 железо, медь, уголь и камень генерируются **крупными поверхностными жилами** с настраиваемыми размером, частотой и диапазоном запаса на плитку. По умолчанию радиус 8–20 блоков, запас 500–5000 единиц, центр богаче края. Настройки: `config/factorycraft-worldgen.toml`; новые жилы появляются в новых чанках. Большие машины устанавливаются **от центрального блока основания**, синяя ячейка превью показывает выбранный центр. Все габариты, защита установки и один предмет на целую структуру сохранены. Миры 0.6.0 продолжают работать; для версий до 0.6.0 нужен новый мир. См. [RESOURCE_FIELDS.md](RESOURCE_FIELDS.md) и [MACHINE_DIMENSIONS.md](MACHINE_DIMENSIONS.md).

Доступны вода → насос → трубы → резервуар, вода → котёл → пар → двигатель → электросеть и конечная нефть → помпа → НПЗ → газ → химический завод → пластик/сера. Примеры `/factorycraft demo`, `smelter`, `waterworks`, `steamworks`, `oilworks` перестроены под полные габариты. Нужна загруженная свободная область **30×8×13** рядом с игроком. Нагрузочный стенд сохраняет 10 000 лент, 1000 производителей и 500 манипуляторов, включая настоящие объёмы всех машин.

`/factorycraft showroom` создаёт выставку всех 13 машин с конечными запасами и настоящими рецептами; нужна загруженная свободная область 32×6×32. Команда доступна администратору.

## Установка

Установите Minecraft 1.21.1 и NeoForge 21.1.252, положите `factorycraft-0.8.0.jar` в `mods/` клиента и сервера. Новые руды генерируются в новых чанках обычного мира. Для сборки нужен полный JDK 21, одного JRE недостаточно.

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

0.8.0 adds advanced oil processing (crude + water → gas/light/heavy), both cracking recipes, lubricant, sulfuric acid and batteries. The full-size refinery gains a fifth persistent cell and a real front-left third output, retaining all old ports/contents. Five-cell menus show localized recipe names and port tooltips. `/factorycraft petrochemistry` builds a finite powered oil-to-battery line with native water intake, inserter-fed plates/sulfur and isolated byproduct storage; no intermediate chemicals are injected. Old basic processing and 0.6.0+ data remain supported. See PETROLEUM.md.

0.7.0 adds original unified chunk-baked geometry for all 13 large machine types, 3D item models, marked fluid nozzles and activity-driven moving components. Shapes use the existing MachineProfile dimensions; collision still reserves the complete volume, including visible gaps. Mesh fragments render with their own chunks; controller-only animated components do not own simulation. See MACHINE_VISUALS.md.

The 2×1 splitter owns two independent physical tracks/four lanes (16 carriers), two forward outputs, balancing, blocked-output fallback and track-local inserter ports. The independent kernel sustains 30 items/s in 24 shifted saturated basic-belt scenarios; native inventory/inserter cadence can limit a real line. Splitter schema 3 reads old two-lane data without inventing new carriers; ordinary belts remain schema 2. See SPLITTERS.md.

Large configurable surface veins, centered item placement, protected one-item multiblocks and portable inventories/fluids/FE/progress remain. Existing 0.6.0/0.6.1 structures and finite stocks are preserved; worlds older than 0.6.0 require a fresh start. Automatic iron/components, water/steam/power and oil/plastic chains remain playable with EN/RU menus. Research, railways, robots, combat and space are still planned. Build and launch with JDK21 using the commands above.
