## 0.30.1 uranium reserve readout

The former uranium interaction returned after its acid warning and never reached the generic reserve message. One canonical `reserveMessage` now supplies exact remaining units **and** the existing localized acid-drill requirement, for both use and rejected hand mining. Jade reads the same loaded authoritative owner through a read-only `MachineInspection` snapshot. It neither duplicates stock in a technical part nor enables extraction. Native tests run real 100-mB-acid-paid extraction from 3 → 2 → exhausted, then check both message/snapshot. Uninitialized/unavailable owners remain unavailable; finite reserves, generation, research triggers and drilling costs are unchanged. Actual EN/RU client acceptance is recorded in [validation/v0301](validation/v0301).

## 0.29 finite uranium

Uranium uses the existing deterministic irregular full ellipsoid, vertical radius 5–8, canonical terrain anchor and replaceable-natural-solids rules. Separate restart-required defaults: candidate spacing 24 chunks, horizontal radius 6–12, per-cell reserve 500–3000. These are explicit Minecraft rarity/richness adaptations, not literal Wube autoplace parity. Existing iron/copper/coal/stone settings are unchanged. No generation into water, air, structures or unavailable decorating chunks.

Only Electric Mining Drill extracts uranium: source mining time 2 s, drill speed 0.5, **100 native mB sulfuric acid per base extraction**. No acid, valid output reservation or power means no paid work/depletion. Mining productivity is an earned additional output without another base extraction/acid debit, as in the source model. Hand/burner/creative inventories do not emit the mine-entity research event. Real extraction is atomic with acid and finite tile ownership and records the team-scoped uranium-processing trigger. The established directed chute feeds the centrifuge or other compatible item handlers without mandatory inserters.

Mining chutes touch the **near lane** of perpendicular belts; parallel/antiparallel belts use the right lane. This differs from inserters and does not change belt direction, headway, stack height or the inserter rule. All four rotations have pure tests; all three speeds have native perpendicular-output cases. The parallel client deck remains unchanged. Reference: [original result drop vector](https://lua-api.factorio.com/latest/prototypes/MiningDrillPrototype.html#vector_to_place_result), [Factorio Access tutorial on physical drop lanes](https://github.com/Factorio-Access/FactorioAccess/blob/main/docs/tutorial-transcript.md).

## 0.26.1 drill delivery

Both drills automatically deliver pending ore through their single front chute to real adjacent belts or sided item handlers, including furnaces and other burner drills' valid fuel slot. Electric chute is front-center; Burner front-right. Burner mining coverage now rotates with its 2×2 body. Existing finite per-tile ore identity/debit and fixed ellipsoid generation are unchanged. No compatible receiver: at most the existing four output stacks are buffered, then ore/FE/fuel work stops. No unbounded ground item entities. See MACHINE_DIMENSIONS.md and VALIDATION.md.

# Ресурсные месторождения — 0.25.1

Железо, медь, уголь и камень образуют полные неправильные эллипсоиды с единственным центром `(x,y,z)`. Существующая порода обрезает тело: на равнине обычно остаётся нижняя часть глубиной 5–8 блоков, на склоне или обрыве может обнажаться верхняя половина. Форма и богатство не следуют локальной поверхности. Каждый блок владеет собственным конечным запасом и прежней механикой добычи.

## Конфигурация новых чанков

`config/factorycraft-worldgen.toml`, общая конфигурация с перезапуском:

```toml
minVeinRadius = 8
maxVeinRadius = 20
minTileStock = 500
maxTileStock = 5000
veinSpacingChunks = 16
minVeinDepth = 5
maxVeinDepth = 8
veinShapeNoise = 0.12
oilFieldSpacingChunks = 16
minOilPoints = 4
maxOilPoints = 12
oilFieldRadius = 24
minOilYield = 10000
maxOilYield = 30000
```

Горизонтальные радиусы X/Z независимо выбираются из диапазона 4–48 блоков (по умолчанию 8–20). Вертикальный **радиус**, не диаметр, выбирается из `minVeinDepth..maxVeinDepth`: 2–64, по умолчанию 5–8; полный диаметр приблизительно 10–16. Шум: 0–0,3. Запас блока: 1–10 000 000. Интервал сетки центров по умолчанию 16 чанков. JSON использует `min_depth`, `max_depth`, `noise`; прежние `depth` и `terrainSmoothing` удалены.

Нефть использует отдельные поверхностные точки с процентной отдачей, а не конечные миллибакеты. Значения 10000/30000 означают 100%/300%. Точки выбираются без повторов на сетке с шагом 6 блоков внутри заданной области; число точек ограничено числом доступных клеток. Вода, занятая поверхность и строения могут уменьшать фактическое число точек. Насосная помпа ставится центром строго над точкой. Подробности: [PETROLEUM.md](PETROLEUM.md).

## Геометрия и безопасность

`ResourceVeins` вычисляет полную форму и запас из seed и мировых координат. Горизонтальный контур сохраняет прежние гармоники; дополнительная гладкая ограниченная деформация по вертикальному направлению не создаёт случайных изолированных вкраплений. Богатство уменьшается к краям и вниз относительно центра, а не относительно поверхности.

`GeneratorTerrain` использует `ChunkGenerator.getBaseHeight(OCEAN_FLOOR_WG)` в единственной точке центра. Исходные RandomState/границы высоты фиксируются при загрузке уровня, результаты центров кэшируются с пределом 4096. Это исходный базовый рельеф генератора до декорации, деревьев и структур; поздние поверхностные feature/carver-изменения не перемещают центр. Запрос работает и вне доступного 3×3 региона без загрузки чанка и без чтения соседних heightmap. Пользовательские генераторы должны корректно реализовывать этот стандартный API. Необычные генераторы с изменяемым внешним состоянием требуют отдельной проверки.

Запись через WorldGenLevel ограничена декорируемым чанком и существующими подходящими природными твёрдыми блоками. Воздух, вода, другие жидкости, неподходящие блоки и любые BlockEntity сохраняются. Пещера остаётся пустой; твёрдые осадки под водой могут пересекаться с телом. Чанки с действительными стартами/непустыми ссылками структур консервативно пропускаются; пустые reference-наборы не блокируют генерацию. Обычная генерация выполняется до доступа игроков; ретрогенерации нет. Повторное выполнение не перезаписывает владельцев запасов и не заполняет истощённый воздух.

Нефть сохраняет собственную замороженную преддекоративную карту поверхности 0.25.0; рудам она больше не нужна. Диагностический replay `LegacySurfaceWindow` используется исключительно для сравнения с 0.25.0, а не в рабочей генерации.

## Добыча и стоимость

Ручная добыча выдаёт одну единицу только после принятия инвентарём. Электробур сохраняет 5×5 область, глубину поиска 8, энергию и выходные транзакции. Более глубокая руда требует доступа с нижнего уровня; это трёхмерная адаптация Minecraft.

Объёмные тела содержат больше нетикающих BlockEntity, чем прежние поверхностные листы. Максимальные радиус/глубина заметно увеличивают память, размер чанков и стоимость генерации — это не бесплатная оптимизация. Измерения чистой геометрии и реальных записей мира разделены в BENCHMARKS.md. `/factorycraft resourceview` — операторский стенд только для отдельного плоского QA-мира: создаёт новые участки ровной поверхности, склона и обрыва, отказывается перезаписывать занятый объём и показывает реальные слои.

**0.25.1 несовместима со старыми промежуточными форматами FactoryCraft.** Миграции и ретрогенерации нет; пользовательские миры не удаляются. Старые диагностические данные сохранены отдельно, новые проверки используют свежие фикстуры.
