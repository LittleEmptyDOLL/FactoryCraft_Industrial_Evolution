#!/usr/bin/env python3
"""Reproducible original industrial pixel assets and first-factory datapack content (stdlib only)."""
from pathlib import Path
import json, random, zlib, struct, gzip
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
def write(name,obj):
 p=ROOT/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
def png(name,pixels):
 p=ROOT/f'assets/factorycraft/textures/{name}.png';p.parent.mkdir(parents=True,exist_ok=True)
 def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
 raw=b''.join(b'\x00'+bytes(sum(row,())) for row in pixels)
 p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,len(pixels),8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
def texture(name,color,style):
 rng=random.Random(name);a=[]
 for y in range(16):
  row=[]
  for x in range(16):
   d=rng.randrange(-12,13);c=tuple(max(0,min(255,v+d)) for v in color)
   if style=='machine':
    if x in (0,15) or y in (0,15):c=(25,33,41)
    if (x in (2,13) and y in (2,13)):c=(174,188,195)
    if 4<=x<=11 and 4<=y<=11:c=(29,45,53)
    if y in (5,8,11) and 5<=x<=10:c=tuple(min(255,v+35) for v in color)
   if style=='belt':
    if x in (0,1,14,15):c=(51,64,75)
    elif x in (7,8):c=(22,28,32)
    elif y%4==0:c=(75,84,87)
    if (x in (4,11) and 3<=y<=9) or (y==3 and x in (3,4,5,10,11,12)) or (y==4 and x in (2,6,9,13)):c=color
   if style=='ore' and (x*7+y*11)%17<4:c=(204,125,69) if 'copper' in name else (190,193,196)
   if style=='item':
    if x<3 or x>12 or y<3 or y>12:row.append((0,0,0,0));continue
    if x in (3,12) or y in (3,12):c=(30,40,49)
    if (x+y)%5==0:c=tuple(min(255,v+40) for v in color)
   row.append((*c,255))
  a.append(row)
 png(name,a)
 return a
colors={
 'transport_belt':(221,168,42),'fast_transport_belt':(212,63,48),'express_transport_belt':(53,156,209),'splitter':(177,126,49),
 'inserter':(215,160,50),'electric_drill':(67,135,143),'electric_furnace':(187,95,52),'assembling_machine':(55,126,161),'coal_generator':(114,127,108),'electric_pole':(162,129,88),
 'iron_ore':(104,110,115),'copper_ore':(110,112,108),'industrial_furnace':(187,93,43),'machine_casing':(79,99,116)}
for name,color in colors.items():
 belt='belt' in name or name=='splitter'
 pixels=texture('block/'+name,color,'belt' if belt else 'ore' if 'ore' in name else 'machine')
 tex='factorycraft:block/'+name
 if belt:
  model={'parent':'minecraft:block/block','textures':{'all':tex,'particle':tex},'elements':[{'from':[0,0,0],'to':[16,3,16],'faces':{f:{'texture':'#all'} for f in ['up','down','north','south','east','west']}}]}
 elif name in ['electric_pole','inserter']:
  lo,hi,height=(6,10,16) if name=='electric_pole' else (5,11,7)
  model={'parent':'minecraft:block/block','textures':{'all':tex,'particle':tex},'elements':[{'from':[lo,0,lo],'to':[hi,height,hi],'faces':{f:{'texture':'#all'} for f in ['up','down','north','south','east','west']}}]}
 else:model={'parent':'minecraft:block/cube_all','textures':{'all':tex}}
 write(f'assets/factorycraft/models/block/{name}.json',model)
 active=name in ['electric_drill','electric_furnace','assembling_machine','coal_generator','industrial_furnace','machine_casing']
 if active:
  frames=[]
  for frame in range(2):
   canvas=[row.copy() for row in pixels]
   for yy in [5,8,11]:
    for xx in range(5,11):canvas[yy][xx]=(255,167+frame*50,52,255) if name=='electric_furnace' else (61,183+frame*50,174,255)
   frames.extend(canvas)
  png('block/'+name+'_active',frames)
  write(f'assets/factorycraft/textures/block/{name}_active.png.mcmeta',{'animation':{'frametime':5,'interpolate':True}})
  write(f'assets/factorycraft/models/block/{name}_active.json',{'parent':'minecraft:block/cube_all','textures':{'all':tex+'_active'}})
 write(f'assets/factorycraft/models/item/{name}.json',{'parent':'factorycraft:block/'+name})
 if 'ore' in name:variants={'':{'model':'factorycraft:block/'+name}}
 else:
  variants={}
  for direction,rot in [('north',0),('east',90),('south',180),('west',270)]:
   for lit in ['false','true']:
    variants[f'facing={direction},lit={lit}']={'model':'factorycraft:block/'+name+('_active' if active and lit=='true' else ''),'y':rot}
 write(f'assets/factorycraft/blockstates/{name}.json',{'variants':variants})
 result='minecraft:raw_iron' if name=='iron_ore' else 'minecraft:raw_copper' if name=='copper_ore' else 'factorycraft:'+name
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':result}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
# A visible arrow and a distinct front make rotated multiblock layouts legible in game.
top=texture('block/industrial_furnace_top',colors['industrial_furnace'],'machine')
for yy in range(2,13):
 for xx in range(16):
  if (yy<=6 and abs(xx-7.5)<=yy-1.5) or (6<=yy<=12 and xx in (7,8)):top[yy][xx]=(255,194,61,255)
png('block/industrial_furnace_top',top)
for suffix in ['', '_active']:
 write(f'assets/factorycraft/models/block/industrial_furnace{suffix}.json',{'parent':'minecraft:block/orientable','textures':{'front':'factorycraft:block/industrial_furnace'+suffix,'side':'factorycraft:block/machine_casing','top':'factorycraft:block/industrial_furnace_top'}})
items={'iron_plate':(176,192,203),'copper_plate':(211,121,64),'iron_gear':(137,160,178),'copper_wire':(226,141,65),'electronic_circuit':(69,167,115),'wrench':(194,197,204)}
for name,color in items.items():
 texture('item/'+name,color,'item');write(f'assets/factorycraft/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/'+name}})
allblocks=['factorycraft:'+n for n in colors]
write('data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':allblocks})
write('data/minecraft/tags/block/needs_stone_tool.json',{'replace':False,'values':['factorycraft:iron_ore','factorycraft:copper_ore']})
for material in ['iron','copper']:
 write(f'data/c/tags/block/ores/{material}.json',{'replace':False,'values':['factorycraft:'+material+'_ore']})
 write(f'data/minecraft/tags/block/{material}_ores.json',{'replace':False,'values':['factorycraft:'+material+'_ore']})
 write(f'data/factorycraft/worldgen/configured_feature/{material}_ore.json',{'type':'minecraft:ore','config':{'size':24,'discard_chance_on_air_exposure':0.0,'targets':[{'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:stone_ore_replaceables'},'state':{'Name':'factorycraft:'+material+'_ore'}}]}})
 write(f'data/factorycraft/worldgen/placed_feature/{material}_ore.json',{'feature':'factorycraft:'+material+'_ore','placement':[{'type':'minecraft:count','count':12},{'type':'minecraft:in_square'},{'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':0},'max_inclusive':{'absolute':96}}},{'type':'minecraft:biome'}]})
 write(f'data/factorycraft/neoforge/biome_modifier/{material}_ore.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':['factorycraft:'+material+'_ore'],'step':'underground_ores'})
def ingredient(item):return {'item':item}
def process(name,machine,inputs,result,count,ticks,energy):
 write(f'data/factorycraft/recipe/{name}.json',{'type':'factorycraft:processing','machine':machine,'inputs':[{'ingredient':ingredient(i),'count':n} for i,n in inputs],'result':{'id':'factorycraft:'+result,'count':count},'ticks':ticks,'energy':energy})
process('iron_plate','electric_furnace',[('minecraft:raw_iron',1)],'iron_plate',1,64,10)
process('copper_plate','electric_furnace',[('minecraft:raw_copper',1)],'copper_plate',1,64,10)
process('iron_gear','assembling_machine',[('factorycraft:iron_plate',2)],'iron_gear',1,10,8)
process('copper_wire','assembling_machine',[('factorycraft:copper_plate',1)],'copper_wire',2,10,8)
process('electronic_circuit','assembling_machine',[('factorycraft:iron_plate',1),('factorycraft:copper_wire',3)],'electronic_circuit',1,20,12)
def craft(name,pattern,keys,result=None,count=1):
 write(f'data/factorycraft/recipe/craft_{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:ingredient(v) for k,v in keys.items()},'result':{'id':'factorycraft:'+(result or name),'count':count}})
craft('iron_gear',[' I ','I I',' I '],{'I':'minecraft:iron_ingot'})
craft('transport_belt',['III','G G'],{'I':'minecraft:iron_ingot','G':'factorycraft:iron_gear'},count=8)
craft('fast_transport_belt',['RGR','BBB'],{'R':'minecraft:redstone','G':'factorycraft:iron_gear','B':'factorycraft:transport_belt'},count=3)
craft('express_transport_belt',['RDR','BBB'],{'R':'minecraft:redstone','D':'minecraft:diamond','B':'factorycraft:fast_transport_belt'},count=3)
craft('splitter',[' R ','BGB'],{'R':'minecraft:redstone','G':'factorycraft:iron_gear','B':'factorycraft:transport_belt'})
craft('inserter',[' I ','GSR',' I '],{'I':'minecraft:iron_ingot','G':'factorycraft:iron_gear','S':'minecraft:stick','R':'minecraft:redstone'})
craft('electric_drill',['III','GPG','IRI'],{'I':'minecraft:iron_ingot','G':'factorycraft:iron_gear','P':'minecraft:iron_pickaxe','R':'minecraft:redstone'})
craft('electric_furnace',['III','IFI','IRI'],{'I':'minecraft:iron_ingot','F':'minecraft:furnace','R':'minecraft:redstone'})
craft('assembling_machine',['III','GCG','IRI'],{'I':'minecraft:iron_ingot','G':'factorycraft:iron_gear','C':'minecraft:crafting_table','R':'minecraft:redstone'})
craft('coal_generator',['III','CFC','IRI'],{'I':'minecraft:iron_ingot','C':'minecraft:cobblestone','F':'minecraft:furnace','R':'minecraft:redstone'})
craft('electric_pole',[' C ',' S ',' S '],{'C':'minecraft:copper_ingot','S':'minecraft:stick'},count=4)
craft('wrench',['I I',' I ',' I '],{'I':'minecraft:iron_ingot'})
# Keep the old casing ID/item for existing worlds, but generated parts are not crafted.
(ROOT/'data/factorycraft/recipe/craft_machine_casing.json').unlink(missing_ok=True)
craft('industrial_furnace',['CPC','FGF','CRC'],{'C':'minecraft:stone_bricks','P':'factorycraft:iron_plate','F':'factorycraft:electric_furnace','G':'factorycraft:iron_gear','R':'minecraft:redstone'})
labels={
 'industrial_furnace':('Industrial Furnace','Промышленная печь'),'machine_casing':('Machine Casing','Корпус машины'),
 'transport_belt':('Transport Belt','Конвейер'),'fast_transport_belt':('Fast Transport Belt','Быстрый конвейер'),'express_transport_belt':('Express Transport Belt','Экспресс-конвейер'),'splitter':('T-Splitter','Т-разделитель'),
 'inserter':('Mechanical Inserter','Механический манипулятор'),'electric_drill':('Electric Mining Drill','Электрический бур'),'electric_furnace':('Electric Furnace','Электрическая печь'),'assembling_machine':('Assembling Machine','Сборочный автомат'),
 'coal_generator':('Coal Generator','Угольный генератор'),'electric_pole':('Electric Pole','Электрический столб'),'iron_ore':('Industrial Iron Ore','Промышленная железная руда'),'copper_ore':('Industrial Copper Ore','Промышленная медная руда'),
 'iron_plate':('Iron Plate','Железная пластина'),'copper_plate':('Copper Plate','Медная пластина'),'iron_gear':('Iron Gear','Железная шестерня'),'copper_wire':('Copper Wire','Медный провод'),'electronic_circuit':('Electronic Circuit','Электронная схема'),'wrench':('Factory Wrench','Заводской ключ')}
ui={'message.factorycraft.placement_space':('Need a loaded, unobstructed 2x2x2 area to the right, above and behind the front block.','Нужен загруженный свободный объём 2x2x2: справа, сверху и позади переднего блока.'),'command.factorycraft.smelter_space':('Need a loaded empty 6 x 5 x 7 area beside you.','Рядом нужна загруженная свободная область 6 x 5 x 7.'),'tooltip.factorycraft.multiblock_controls':('Places the complete machine. Sneak + wrench dismantles it.','Сразу устанавливает всю машину. Shift + ключ — разборка.'),'gui.factorycraft.assembly.0':('Unformed','Не собрана'),'gui.factorycraft.assembly.1':('Chunks pending','Чанк выгружен'),'gui.factorycraft.assembly.2':('Assembled','Собрана'),
 'message.factorycraft.assembly_missing':('Structure unavailable. Check that all its chunks are loaded and parts are intact.','Структура недоступна. Проверьте загрузку чанков и целостность частей.'),
 'message.factorycraft.dismantle':('Sneak + wrench dismantles the machine safely; contents are not destroyed.','Shift + ключ: безопасно разобрать машину; предметы не уничтожаются.'),
 'command.factorycraft.smelter':('Multiblock smelter built at %s. Its source chest contains 12 raw iron.','Мультиблочная печь построена: %s. Во входном сундуке 12 необработанного железа.'),
 'tooltip.factorycraft.industrial_furnace':('One item places a 2x2x2 machine. Twice the furnace speed; same FE per result.','Один предмет устанавливает машину 2x2x2. Скорость x2; тот же расход FE на результат.'),
 'tooltip.factorycraft.machine_casing':('Part of a multiblock. Shares the controller inventory and power; stores no items.','Часть мультиблока. Общий с контроллером инвентарь и энергия; своих предметов нет.'),
 'tooltip.factorycraft.belt':('Two lanes. Items follow the arrow; stopped output causes a jam.','Две полосы. Движение по стрелке; закрытый выход создаёт затор.'),
 'tooltip.factorycraft.splitter':('Alternates between the forward and right outputs.','Чередует передний и правый выходы.'),
 'tooltip.factorycraft.inserter':('Picks up behind, delivers ahead. Mechanical; one item per cycle.','Забирает сзади, кладёт спереди. Механический; один предмет за цикл.'),
 'tooltip.factorycraft.electric_drill':('Mines finite ore in a 3x3 area, up to 8 blocks below. Requires power.','Добывает конечную руду в области 3x3 на глубину до 8 блоков. Нужна энергия.'),
 'tooltip.factorycraft.electric_furnace':('Smelts raw iron/copper into plates. Requires power.','Переплавляет необработанное железо и медь в пластины. Нужна энергия.'),
 'tooltip.factorycraft.assembling_machine':('Assembles counted ingredients. Open to inspect/select a recipe.','Собирает компоненты. Откройте для просмотра и выбора рецепта.'),
 'tooltip.factorycraft.coal_generator':('Fuel goes in the bottom slot. Supplies machines through poles.','Топливо — в нижний слот. Питает машины через столбы.'),
 'tooltip.factorycraft.electric_pole':('Connects nearby poles and powers machines inside coverage.','Соединяет соседние столбы и питает машины в зоне покрытия.'),
 'tooltip.factorycraft.rotate':('Right-click with a factory wrench to rotate.','ПКМ заводским ключом — повернуть.'),'command.factorycraft.space':('Need a loaded empty 16 x 5 x 8 area beside you.','Рядом нужна загруженная свободная область 16 x 5 x 8.'),'command.factorycraft.demo':('Automatic iron factory built at %s. Nine finite ores will produce four gears.','Автоматическая фабрика железа построена: %s. Из девяти блоков руды получатся четыре шестерни.'),'itemGroup.factorycraft':('FactoryCraft: Industrial Evolution','FactoryCraft: Промышленная эволюция'),'message.factorycraft.direction':('Output: %s. Use a wrench to rotate.','Выход: %s. Поверните ключом.'),'gui.factorycraft.select':('Next recipe','След. рецепт'),'gui.factorycraft.input':('Input','Вход'),'gui.factorycraft.output':('Output','Выход'),'gui.factorycraft.auto':('Automatic recipe','Автовыбор рецепта'),'gui.factorycraft.burn':('Fuel: %s t','Топливо: %s т'),'gui.factorycraft.recipe_info':('%s t / %s FE per tick','%s т / %s FE за такт')}
for lang,index in [('en_us',0),('ru_ru',1)]:
 d={('block' if n in colors else 'item')+'.factorycraft.'+n:pair[index] for n,pair in labels.items()};d.update({k:v[index] for k,v in ui.items()});write(f'assets/factorycraft/lang/{lang}.json',d)
write('data/factorycraft/advancement/recipes/factory_basics.json',{'criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},'rewards':{'recipes':['factorycraft:'+p.stem for p in sorted((ROOT/'data/factorycraft/recipe').glob('craft_*.json'))]}})
write('data/factorycraft/advancement/recipes/multiblock_basics.json',{'criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},'rewards':{'recipes':['factorycraft:craft_industrial_furnace']}})
# Empty structure used by all code-built integration tests (NBT, data version 3955 / Minecraft 1.21.1).
def utf(s):
 b=s.encode();return struct.pack('>H',len(b))+b
def named(t,n,p):return bytes([t])+utf(n)+p
nbt=b'\x0a\x00\x00'+named(3,'DataVersion',struct.pack('>i',3955))+named(9,'size',b'\x03'+struct.pack('>i',3)+struct.pack('>iii',16,8,16))+named(9,'palette',b'\x0a'+struct.pack('>i',1)+named(8,'Name',utf('minecraft:air'))+b'\x00')+named(9,'blocks',b'\x0a'+struct.pack('>i',0))+named(9,'entities',b'\x0a'+struct.pack('>i',0))+b'\x00'
p=ROOT/'data/factorycraft/structure/empty.nbt';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(gzip.compress(nbt,mtime=0))
