# FactoryCraft: Industrial Evolution — 0.1.0

Minecraft **1.21.1**, NeoForge **21.1.265**, **Java JDK 21**. MIT; original procedural industrial textures. English and Russian UI.

Это первая рабочая часть этапов 1–2, а не законченная Factorio/Space Age. Мод добывает конечную руду, питает машины через столбы и автоматически производит пластины, шестерни, провода и схемы. Двухполосные конвейеры отображают предметы без создания Entity, останавливаются при заторах и продолжают движение после освобождения выхода. Содержимое и прогресс сохраняются.

## Установка

Установите Minecraft 1.21.1 и NeoForge 21.1.265, положите `factorycraft-0.1.0.jar` в `mods/` клиента и сервера. Новые руды генерируются в новых чанках обычного мира. Для сборки нужен полный JDK 21, одного JRE недостаточно.

```sh
./gradlew build
./gradlew runGameTestServer
./gradlew runGameTestServer  # второй процесс проверяет чтение состояния из region-файлов
./gradlew runClient
./gradlew runServer         # выделенный сервер разработки
```

На Windows: `gradlew.bat`. В облачной среде параметры HTTP-прокси и пути JDK задавались локально вне репозитория. Не копируйте их в пользовательскую конфигурацию.

## Первая фабрика

См. [QUICKSTART.md](QUICKSTART.md): направление, подключение энергии, слот топлива, рецепты и схема линии. В творческом мире с командами `/factorycraft demo` строит проверенную линию рядом с игроком, если место свободно. `/factorycraft profile` показывает число загруженных/активных конвейеров и накопленное время их симуляции.

## Документация и продолжение

* [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) — архитектура, этапы, следующая задача.
* [FEATURE_MATRIX.md](FEATURE_MATRIX.md) — фактическая реализация и ещё отсутствующие системы.
* [VALIDATION.md](VALIDATION.md) — команды, автоматические проверки и ограничения доказательств.
* [ARCHITECTURE.md](ARCHITECTURE.md) — сохранение, транзакции, конвейерная и энергетическая модели.
* [AGENTS.md](AGENTS.md) — правила следующих изменений.

## English

A working first-factory slice: finite electric mining → two-lane belts → mechanical inserters → electric smelting → assembly → a vanilla chest. Fuel the coal generator and place poles within their configured coverage (default 6 blocks). Face inserters from their source toward their destination; rotate with the factory wrench. Machine left slots accept ingredients, right slots expose outputs, and the bottom slot accepts generator fuel. Right-click a machine for recipes, energy and progress. Recipes and ore placement are datapack JSON. Later research, fluids, railways, robots, combat and space systems are explicitly unimplemented.

The project includes JUnit kernel tests and server GameTests. `tools/generate_resources.py` regenerates the original textures and first-factory JSON files using Python's standard library. It replaces generated resource files; hand-authored recipe overrides should live in a separate datapack.
