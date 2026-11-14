# Furnace visuals — 0.25.1

Stone and Steel use original per-cell masonry/metal meshes, 2×2 footprints and Minecraft heights 2/3. Electric retains its 3×3×3 body. Burner flames use the actual active-work state, so idle/blocked/unfuelled machines stop animating. Item models, placement preview, collision cells and portable dismantling all use the same MachineProfile/layout definitions. No Factorio artwork is copied. See FURNACES.md for mechanics.

# Визуальная система машин — 0.20.0

Все 27 профилей сохраняют собственные оригинальные цельные модели. Размеры берутся из MachineProfile; высоты адаптированы к Minecraft. Статические поверхности делятся CellMesh по физическим клеткам без внутренних крышек на стыках и запекаются в чанковые меши. Части содержат локальную визуальную идентификацию и рисуются без чтения чанка контроллера.

Контроллер больше не обязан соответствовать нулевому фрагменту меша: загрузчик выбирает его локальную клетку из профиля. Анимированные механизмы используют тот же сдвиг начала координат. Производственную логику клиент не рассчитывает.

Патрубки и стрелки создаются непосредственно из `factorycraft/machine_layouts.json`, общего источника серверной маршрутизации и NeoForge capabilities. Синий — INPUT, оранжевый — OUTPUT, бирюзовый — BOTH. У бака четыре подключения в двух угловых клетках, у котла два боковых водяных подключения и передний паровой выход, у двигателя пар проходит через оба торца. У помпы учтены разные смещения подключения для нечётных поворотов Factorio. Список: FLUIDS.md / PETROLEUM.md.

Отражённая карта портов имеет отдельный запечённый вариант. ПКМ ключом отражает только подключения и их маркеры; геометрия корпуса/анимации остаётся оригинальной геометрией FactoryCraft. Части сохраняют визуальный флаг отражения, а контроллер — его единственное авторитетное значение. Стандартный предмет показывает каноническое неотражённое расположение. Подсказка жидкостного меню показывает действительные координаты/сторону выбранного канала, включая отражение.

Удерживаемый предмет использует PlacementPlan и настоящий BLOCK hit в пределах разрешённого reach. MISS/воздух/дальняя цель не рисуют рамку. Размеры и управление описаны в MACHINE_DIMENSIONS.md. Selection/collision shapes прежних машин не перерабатывались; у новых столбов они вырезаны по реальным стойкам и оголовкам.

Ресурсы воспроизводимы: `python tools/generate_resources.py`; генератор моделей `tools/machine_geometry.py` читает тот же JSON портов для предметных моделей. Графика Factorio не копировалась. Числовой справочник — [Factorio 2.0.77, ce6741a](https://github.com/wube/factorio-data/tree/ce6741a54c3199caad4ed147e9987fd8d5653fdf/base/prototypes/entity).

Скриншоты 0.19.0 в VALIDATION.md показывают реальное превью, меню через часть, подсказку координат и подключённую трубу. Старые скриншоты 0.6–0.18 сохранены как исторические материалы и не описывают текущие порты. `showroom` и сложные административные производственные макеты прежних версий требуют ревизии координат; их старые проверки перечислены в архиве, они не являются доказательством новой схемы.

## Столбы, провода и информация — 0.20

`factorycraft/pole_geometry.json` содержит оригинальные коробки стоек/изоляторов и точки крепления. Один источник используется Python-генератором статических моделей, серверными shapes и клиентским проводом. Оголовки находятся на высоте 1.88/2.88/4.88/3.88 от основания. У всех ориентаций преобразование совпадает с MachineProfile. Нет скопированных текстур Factorio.

Каждое сохранённое взаимное медное соединение рисуется один раз контроллером с меньшей позицией: кэшированная полилиния из 16 участков, небольшой провис по длине, без Entity. Нет глобального поиска соседей по кадрам. Если конец/часть выгружены, провод не рисуется. Медные электрические провода не изменяют красные/зелёные/синие/белые логические сети.

Синий квадрат показывает настоящее coverage; зелёные линии — предполагаемые соединения при установке (включая проекцию на основание для читаемости сверху), оранжевые — действительные провода. Превью не рисуется при MISS/воздухе/дальней цели. Кандидаты обновляются локально при смене цели либо раз в десять тиков, без покадрового сканирования площади.

FactoryCraft использует единое имя владельца для контроллера и частей. Необязательный Jade заменяет имя/иконку и стандартные строки хранилищ единым снимком владельца; FE, предметы и жидкости показываются без дублирования. Снимок не расширяет реальные возможности доступа через корпуса. Скриншоты и фактические клиентские проверки — VALIDATION.md.

## 0.21.0 inserters

Six original generated bases distinguish Burner (fuel housing), yellow Standard, red Long, blue Fast, green Bulk and white Stack (reinforced bases). One client renderer animates two links by the server's paid phase; Long extends both links to reach two. A held stack stays component-exact in the server hand and renders a quantity label. Interrupted power/gates stop authoritative movement; the client interpolates bounded snapshots only. Belt rendering draws one to four vertically offset item layers per physical carrier. These are original functional assets, not extracted Factorio art or a claim of final animated artwork.

## Circuit contacts — 0.22.0

Selector uses an original 1×2 cyan industrial enclosure in the existing per-cell generator. All direct circuit contacts use server-defined, rotated attachment points and four distinct studs. `CircuitVisuals` replaces the old combinator-only renderer with reciprocal UUID-checked, incident-edge/chunk-indexed cached curves. Vertices are camera-relative to retain precision far from spawn; no neighbor discovery or resource simulation runs per frame. Copper power rendering is unchanged.


## 0.23 transport

Original generated belts have separate dark frames, brushed rails, rollers, tier-colored accents and directional animated surfaces. Lift entry/exit mouths, repeated open-sided shaft windows and collars share cell-local geometry; exits follow the real rotated route. Splitters share tier surfaces and a raised central mechanical housing. No graphics from reference projects/Satisfactory are included. Static geometry remains chunk-baked with a bounded loader cache, and transported item stacks retain their existing server snapshots. Selection/collision shapes and placement volumes are unchanged. Design/license review and unimplemented future ideas: TRANSPORT_REFERENCES.md.
