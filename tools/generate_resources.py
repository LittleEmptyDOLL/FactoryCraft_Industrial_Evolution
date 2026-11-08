#!/usr/bin/env python3
"""Reproducible original industrial pixel assets and first-factory datapack content (stdlib only)."""
from pathlib import Path
import json, random, zlib, struct, gzip
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
# Curated upstream artwork is source material, never a procedural output.
ARTWORK=set(json.loads(Path(__file__).with_name('item_artwork.json').read_text()))
def write(name,obj):
 p=ROOT/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
def png(name,pixels):
 p=ROOT/f'assets/factorycraft/textures/{name}.png';p.parent.mkdir(parents=True,exist_ok=True)
 if name in ARTWORK:
  if not p.is_file():raise FileNotFoundError(f'Missing curated artwork: {p}')
  return
 def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
 raw=b''.join(b'\x00'+bytes(sum(row,())) for row in pixels)
 p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',len(pixels[0]),len(pixels),8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
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
nbt=b'\x0a\x00\x00'+named(3,'DataVersion',struct.pack('>i',3955))+named(9,'size',b'\x03'+struct.pack('>i',3)+struct.pack('>iii',32,12,24))+named(9,'palette',b'\x0a'+struct.pack('>i',1)+named(8,'Name',utf('minecraft:air'))+b'\x00')+named(9,'blocks',b'\x0a'+struct.pack('>i',0))+named(9,'entities',b'\x0a'+struct.pack('>i',0))+b'\x00'
p=ROOT/'data/factorycraft/structure/empty.nbt';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(gzip.compress(nbt,mtime=0))

# Original fluid-logistics assets, recipes and translations.
texture('block/fluid_pipe',(103,143,157),'machine')
texture('block/fluid_tank',(62,116,145),'machine')
pump=texture('block/electric_pump',(62,143,113),'machine')
top=[row.copy() for row in pump]
for yy in range(4,12):
 for xx in range(7,9):top[yy][xx]=(244,191,62,255)
for yy in range(2,6):
 for xx in range(7-(yy-2),9+(yy-2)):top[yy][xx]=(244,191,62,255)
png('block/electric_pump_top',top)
frames=[]
for frame in range(2):
 image=[row.copy() for row in pump]
 for yy in [5,8,11]:
  for xx in range(5,11):image[yy][xx]=(62,198+frame*35,226,255)
 frames.extend(image)
png('block/electric_pump_active',frames)
write('assets/factorycraft/textures/block/electric_pump_active.png.mcmeta',{'animation':{'frametime':5,'interpolate':True}})
def pipe_model(name,lo,hi):
 write(f'assets/factorycraft/models/block/{name}.json',{'parent':'minecraft:block/block','textures':{'all':'factorycraft:block/fluid_pipe','particle':'factorycraft:block/fluid_pipe'},'elements':[{'from':lo,'to':hi,'faces':{face:{'texture':'#all'} for face in ['up','down','north','south','east','west']}}]})
pipe_model('fluid_pipe',[5,5,5],[11,11,11]);pipe_model('fluid_pipe_item',[0,5,5],[16,11,11])
parts=[{'apply':{'model':'factorycraft:block/fluid_pipe'}}]
for direction,lo,hi in [('down',[5,0,5],[11,5,11]),('up',[5,11,5],[11,16,11]),('north',[5,5,0],[11,11,5]),('south',[5,5,11],[11,11,16]),('west',[0,5,5],[5,11,11]),('east',[11,5,5],[16,11,11])]:
 pipe_model('fluid_pipe_'+direction,lo,hi);parts.append({'when':{direction:'true'},'apply':{'model':'factorycraft:block/fluid_pipe_'+direction}})
write('assets/factorycraft/blockstates/fluid_pipe.json',{'multipart':parts})
write('assets/factorycraft/models/block/fluid_tank.json',{'parent':'minecraft:block/cube_all','textures':{'all':'factorycraft:block/fluid_tank'}})
for name in ['fluid_tank','electric_pump']:
 variants={}
 for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
  for lit in ['false','true']:variants[f'facing={facing},lit={lit}']={'model':'factorycraft:block/'+name+('_active' if name=='electric_pump' and lit=='true' else ''),'y':angle}
 write(f'assets/factorycraft/blockstates/{name}.json',{'variants':variants})
for suffix in ['','_active']:
 write(f'assets/factorycraft/models/block/electric_pump{suffix}.json',{'parent':'minecraft:block/orientable','textures':{'front':'factorycraft:block/electric_pump'+suffix,'side':'factorycraft:block/fluid_pipe','top':'factorycraft:block/electric_pump_top'}})
for name in ['fluid_pipe','fluid_tank','electric_pump']:
 write(f'assets/factorycraft/models/item/{name}.json',{'parent':'factorycraft:block/'+name+('_item' if name=='fluid_pipe' else '')})
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:'+name}]}]})
craft('fluid_pipe',['P P'],{'P':'factorycraft:iron_plate'},count=4)
craft('fluid_tank',['PPP','G G','PPP'],{'P':'factorycraft:iron_plate','G':'minecraft:glass'})
craft('electric_pump',['IGI','RBR','IPI'],{'I':'minecraft:iron_ingot','G':'factorycraft:iron_gear','R':'minecraft:redstone','B':'minecraft:bucket','P':'factorycraft:electric_pole'})
fluid_lang={
 'block.factorycraft.fluid_pipe':('Fluid Pipe','Жидкостная труба'),
 'block.factorycraft.fluid_tank':('Fluid Tank','Резервуар'),
 'block.factorycraft.electric_pump':('Electric Pump','Электрический насос'),
 'tooltip.factorycraft.fluid_pipe':('1000 mB. Up to 100 mB/t per connection; liquids equalize locally.','1000 mB. До 100 mB/т на соединение; локальное выравнивание.'),
 'tooltip.factorycraft.fluid_tank':('16000 mB. Holds one fluid; retains contents when harvested.','16000 mB. Одна жидкость; содержимое сохраняется при добыче.'),
 'tooltip.factorycraft.electric_pump':('Water source or fluid input behind; output ahead. 100 mB/t, 20 FE per active tick.','Вода или вход сзади, выход спереди. 100 mB/т, 20 FE за рабочий такт.'),
 'tooltip.factorycraft.fluid_contents':('%s: %s mB','%s: %s mB'),
 'gui.factorycraft.fluid_empty':('Empty','Пусто'),
 'gui.factorycraft.fluid_volume':('%s / %s mB','%s / %s mB'),
 'gui.factorycraft.pump_rate':('100 mB/t; 20 FE/active tick','100 mB/т; 20 FE/раб. такт'),
 'gui.factorycraft.bucket_controls':('Use a bucket to fill/drain','ПКМ ведром: налить/забрать'),
 'gui.factorycraft.fluid_sides':('All sides','Все стороны'),
 'gui.factorycraft.fluid_pump_sides':('Input behind; output ahead','Сзади → спереди'),
 'command.factorycraft.fluid_demo':('Water-to-tank line built at %s.','Линия подачи воды построена: %s.'),
 'command.factorycraft.fluid_space':('Need a loaded empty 9 x 4 x 5 area beside you.','Рядом нужна свободная загруженная область 9 x 4 x 5.')}
for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';content=json.loads(path.read_text());content.update({key:value[index] for key,value in fluid_lang.items()});write(str(path.relative_to(ROOT)),content)
write('data/factorycraft/advancement/recipes/fluid_basics.json',{'criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},'rewards':{'recipes':['factorycraft:craft_'+name for name in ['fluid_pipe','fluid_tank','electric_pump']]}})
write('data/factorycraft/advancement/recipes/factory_basics.json',{'criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},'rewards':{'recipes':['factorycraft:'+p.stem for p in sorted((ROOT/'data/factorycraft/recipe').glob('craft_*.json'))]}})

# Compact steam equipment: original procedural textures, container-only steam and data-driven boiling.
for name,color in [('boiler',(161,104,53)),('steam_engine',(56,133,150))]:
 base=texture('block/'+name,color,'machine')
 top=[row.copy() for row in base]
 for y in range(4,13):
  for x in [7,8]:top[y][x]=(245,190,58,255)
 for y in range(2,6):
  for x in range(7-(y-2),9+(y-2)):top[y][x]=(245,190,58,255)
 png('block/'+name+'_top',top)
 frames=[]
 for f in range(2):
  image=[row.copy() for row in base]
  for y in [5,8,11]:
   for x in range(5,11):image[y][x]=(252,143+f*65,43,255) if name=='boiler' else (62,186+f*50,222,255)
  frames.extend(image)
 png('block/'+name+'_active',frames)
 write(f'assets/factorycraft/textures/block/{name}_active.png.mcmeta',{'animation':{'frametime':6,'interpolate':True}})
 variants={}
 for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
  for lit in ['false','true']:variants[f'facing={facing},lit={lit}']={'model':'factorycraft:block/'+name+('_active' if lit=='true' else ''),'y':angle}
 write(f'assets/factorycraft/blockstates/{name}.json',{'variants':variants})
 for suffix in ['','_active']:
  write(f'assets/factorycraft/models/block/{name}{suffix}.json',{'parent':'minecraft:block/orientable','textures':{'front':'factorycraft:block/'+name+suffix,'side':'factorycraft:block/'+name,'top':'factorycraft:block/'+name+'_top'}})
 write(f'assets/factorycraft/models/item/{name}.json',{'parent':'factorycraft:block/'+name})
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:'+name}]}]})
texture('block/steam',(186,213,219),'ore')
texture('item/steam_bucket',(191,215,219),'item')
write('assets/factorycraft/models/item/steam_bucket.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/steam_bucket'}})
craft('boiler',['PPP','BFB','PGP'],{'P':'factorycraft:iron_plate','B':'factorycraft:fluid_pipe','F':'minecraft:furnace','G':'factorycraft:iron_gear'})
craft('steam_engine',['PGP','BCB','PGP'],{'P':'factorycraft:iron_plate','G':'factorycraft:iron_gear','B':'factorycraft:fluid_pipe','C':'factorycraft:copper_plate'})
write('data/factorycraft/recipe/boil_water.json',{'type':'factorycraft:boiling','input':{'fluid':'minecraft:water','amount':200},'result':{'id':'factorycraft:steam','amount':200},'ticks':20})
steam_lang={
 'message.factorycraft.component_bucket':('This bucket cannot preserve fluid components. Move the filled tank instead.','Ведро не сохраняет свойства жидкости. Перенесите заполненный резервуар.'),
 'block.factorycraft.boiler':('Boiler','Котёл'),
 'block.factorycraft.steam_engine':('Steam Engine','Паровой двигатель'),
 'fluid.factorycraft.steam':('Steam (165 °C)','Пар (165 °C)'),
 'item.factorycraft.steam_bucket':('Steam Bucket','Ведро пара'),
 'gui.factorycraft.thermal_water':('Input: %s mB','Вход: %s mB'),
 'gui.factorycraft.thermal_energy':('Energy: %s FE','Энергия: %s FE'),
 'gui.factorycraft.thermal_steam':('Steam/output: %s mB','Пар/выход: %s mB'),
 'gui.factorycraft.thermal_fuel':('Fuel: %s working ticks','Топливо: %s раб. тактов'),
 'gui.factorycraft.engine_rate':('Up to 120 FE/t; 12 FE/mB','До 120 FE/т; 12 FE/mB'),
 'gui.factorycraft.boiler_ports':('In behind; out ahead','Сзади → спереди'),
 'gui.factorycraft.engine_ports':('Steam behind; FE to poles','Пар сзади; FE к столбам'),
 'gui.factorycraft.engine_buffer':('Stops at full FE buffer','Полный буфер: остановка'),
 'gui.factorycraft.boiler_status_0':('Waiting','Ожидание'),
 'gui.factorycraft.boiler_status_1':('Needs fuel','Нет топлива'),
 'gui.factorycraft.boiler_status_2':('Needs recipe input','Нет сырья для рецепта'),
 'gui.factorycraft.boiler_status_3':('Output blocked','Выход заполнен'),
 'gui.factorycraft.boiler_status_4':('Boiling','Кипячение'),
 'tooltip.factorycraft.boiler':('Water behind, steam ahead. Fuel and fluids stay in the harvested block.','Вода сзади, пар спереди. Топливо и жидкости сохраняются при добыче.'),
 'tooltip.factorycraft.steam_engine':('Steam behind. 12 FE/mB, up to 120 FE/t; supplies poles or FE extraction.','Пар сзади. 12 FE/mB, до 120 FE/т; питание столбов или выдача FE.'),
 'command.factorycraft.steam_space':('Need a loaded empty 13 x 4 x 7 area beside you.','Рядом нужна свободная загруженная область 13 x 4 x 7.'),
 'command.factorycraft.steam_demo':('Steam-powered furnace built at %s. The coal generator only bootstraps the pump.','Паровая электростанция с печью: %s. Угольный генератор нужен для запуска насоса.')}
for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';content=json.loads(path.read_text());content.update({k:v[index] for k,v in steam_lang.items()});write(str(path.relative_to(ROOT)),content)
write('data/factorycraft/advancement/recipes/steam_basics.json',{'criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},'rewards':{'recipes':['factorycraft:craft_boiler','factorycraft:craft_steam_engine']}})
write('data/factorycraft/advancement/recipes/factory_basics.json',{'criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},'rewards':{'recipes':['factorycraft:'+p.stem for p in sorted((ROOT/'data/factorycraft/recipe').glob('craft_*.json'))]}})

# Petroleum stage: original refinery machinery, finite reservoir and mixed recipes.
for name,color in [('pumpjack',(99,135,99)),('oil_refinery',(128,124,83)),('chemical_plant',(100,132,161)),('refinery_casing',(96,111,123))]:
 pixels=texture('block/'+name,color,'machine');top=[row.copy() for row in pixels]
 for y in range(4,12):
  for x in [7,8]:top[y][x]=(244,191,62,255)
 for y in range(2,6):
  for x in range(7-(y-2),9+(y-2)):top[y][x]=(244,191,62,255)
 png('block/'+name+'_top',top);frames=[]
 for frame in range(2):
  image=[row.copy() for row in pixels]
  for y in [5,8,11]:
   for x in range(5,11):image[y][x]=(180+frame*50,197,83,255)
  frames.extend(image)
 png('block/'+name+'_active',frames);write(f'assets/factorycraft/textures/block/{name}_active.png.mcmeta',{'animation':{'frametime':6,'interpolate':True}})
 for suffix in ['','_active']:
  write(f'assets/factorycraft/models/block/{name}{suffix}.json',{'parent':'minecraft:block/orientable','textures':{'front':'factorycraft:block/'+name+suffix,'side':'factorycraft:block/'+name,'top':'factorycraft:block/'+name+'_top'}})
 variants={}
 for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
  for lit in ['false','true']:variants[f'facing={facing},lit={lit}']={'model':'factorycraft:block/'+name+('_active' if lit=='true' else ''),'y':angle}
 write(f'assets/factorycraft/blockstates/{name}.json',{'variants':variants})
 write(f'assets/factorycraft/models/item/{name}.json',{'parent':'factorycraft:block/'+name})
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:'+name}]}]})
for name,color in [('crude_oil',(64,54,39)),('petroleum_gas',(208,177,113)),('heavy_oil',(133,74,48)),('light_oil',(214,162,60)),('lubricant',(83,152,84)),('sulfuric_acid',(199,206,66))]:
 texture('block/'+name,color,'ore');texture('item/'+name+'_bucket',color,'item')
 write(f'assets/factorycraft/models/item/{name}_bucket.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/'+name+'_bucket'}})
for name,color in [('plastic_bar',(222,223,213)),('sulfur',(221,209,51)),('advanced_circuit',(185,63,62)),('battery',(74,167,185))]:
 texture('item/'+name,color,'item');write(f'assets/factorycraft/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/'+name}})
texture('block/oil_deposit',(53,55,47),'ore');texture('block/oil_deposit_depleted',(112,116,110),'ore')
for suffix in ['','_depleted']:write(f'assets/factorycraft/models/block/oil_deposit{suffix}.json',{'parent':'minecraft:block/cube_all','textures':{'all':'factorycraft:block/oil_deposit'+suffix}})
write('assets/factorycraft/blockstates/oil_deposit.json',{'variants':{'depleted=false':{'model':'factorycraft:block/oil_deposit'},'depleted=true':{'model':'factorycraft:block/oil_deposit_depleted'}}})
write('data/factorycraft/loot_table/blocks/oil_deposit.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'minecraft:cobblestone'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write('data/factorycraft/worldgen/configured_feature/oil_deposit.json',{'type':'factorycraft:oil_deposit','config':{'size':4,'discard_chance_on_air_exposure':0.0,'targets':[{'target':{'predicate_type':'minecraft:tag_match','tag':tag},'state':{'Name':'factorycraft:oil_deposit','Properties':{'depleted':'false'}}} for tag in ['minecraft:stone_ore_replaceables','minecraft:deepslate_ore_replaceables']]}})
write('data/factorycraft/worldgen/placed_feature/oil_deposit.json',{'feature':'factorycraft:oil_deposit','placement':[{'type':'minecraft:rarity_filter','chance':8},{'type':'minecraft:in_square'},{'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':16},'max_inclusive':{'absolute':80}}},{'type':'minecraft:biome'}]})
write('data/factorycraft/neoforge/biome_modifier/oil_deposit.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':['factorycraft:oil_deposit'],'step':'underground_ores'})
craft('pumpjack',['PGP','CDC','PPP'],{'P':'factorycraft:iron_plate','G':'factorycraft:iron_gear','C':'factorycraft:electronic_circuit','D':'factorycraft:electric_drill'})
craft('oil_refinery',['PBP','CFC','PBP'],{'P':'factorycraft:iron_plate','B':'factorycraft:fluid_pipe','C':'factorycraft:electronic_circuit','F':'factorycraft:electric_furnace'})
craft('chemical_plant',['PBP','CAC','PBP'],{'P':'factorycraft:iron_plate','B':'factorycraft:fluid_pipe','C':'factorycraft:electronic_circuit','A':'factorycraft:assembling_machine'})
write('data/factorycraft/recipe/basic_oil_processing.json',{'type':'factorycraft:chemistry','machine':'oil_refinery','fluids':[{'fluid':'factorycraft:crude_oil','amount':100}],'fluid_results':[{'id':'factorycraft:petroleum_gas','amount':45}],'ticks':100,'energy':30})
write('data/factorycraft/recipe/plastic.json',{'type':'factorycraft:chemistry','machine':'chemical_plant','items':[{'ingredient':{'item':'minecraft:coal'},'count':1}],'fluids':[{'fluid':'factorycraft:petroleum_gas','amount':20}],'results':[{'id':'factorycraft:plastic_bar','count':2}],'ticks':20,'energy':20})
write('data/factorycraft/recipe/sulfur.json',{'type':'factorycraft:chemistry','machine':'chemical_plant','fluids':[{'fluid':'factorycraft:petroleum_gas','amount':30},{'fluid':'minecraft:water','amount':30}],'results':[{'id':'factorycraft:sulfur','count':2}],'ticks':20,'energy':20})
# Fixed port order: crude/water inputs; existing gas/front and light/right outputs,
# with heavy oil on the new third/front-left outlet.
write('data/factorycraft/recipe/advanced_oil_processing.json',{'type':'factorycraft:chemistry','machine':'oil_refinery','fluids':[{'fluid':'factorycraft:crude_oil','amount':100},{'fluid':'minecraft:water','amount':50}],'fluid_results':[{'id':'factorycraft:petroleum_gas','amount':55},{'id':'factorycraft:light_oil','amount':45},{'id':'factorycraft:heavy_oil','amount':25}],'ticks':100,'energy':30})
for recipe,source,n,result,m in [('heavy_oil_cracking','heavy_oil',40,'light_oil',30),('light_oil_cracking','light_oil',30,'petroleum_gas',20)]:
 write(f'data/factorycraft/recipe/{recipe}.json',{'type':'factorycraft:chemistry','machine':'chemical_plant','fluids':[{'fluid':'factorycraft:'+source,'amount':n},{'fluid':'minecraft:water','amount':30}],'fluid_results':[{'id':'factorycraft:'+result,'amount':m}],'ticks':40,'energy':20})
write('data/factorycraft/recipe/lubricant.json',{'type':'factorycraft:chemistry','machine':'chemical_plant','fluids':[{'fluid':'factorycraft:heavy_oil','amount':10}],'fluid_results':[{'id':'factorycraft:lubricant','amount':10}],'ticks':20,'energy':20})
write('data/factorycraft/recipe/sulfuric_acid.json',{'type':'factorycraft:chemistry','machine':'chemical_plant','items':[{'ingredient':{'item':'factorycraft:sulfur'},'count':5},{'ingredient':{'item':'factorycraft:iron_plate'},'count':1}],'fluids':[{'fluid':'minecraft:water','amount':100}],'fluid_results':[{'id':'factorycraft:sulfuric_acid','amount':50}],'ticks':20,'energy':20})
write('data/factorycraft/recipe/battery.json',{'type':'factorycraft:chemistry','machine':'chemical_plant','items':[{'ingredient':{'item':'factorycraft:iron_plate'},'count':1},{'ingredient':{'item':'factorycraft:copper_plate'},'count':1}],'fluids':[{'fluid':'factorycraft:sulfuric_acid','amount':20}],'results':[{'id':'factorycraft:battery','count':1}],'ticks':80,'energy':20})
process('advanced_circuit','assembling_machine',[('factorycraft:electronic_circuit',2),('factorycraft:copper_wire',4),('factorycraft:plastic_bar',2)],'advanced_circuit',1,120,24)
oil_lang={
 'gui.factorycraft.chemical_recipe_info':('%s — %s t / %s FE/t','%s — %s т / %s FE/т'),
 'block.factorycraft.pumpjack':('Electric Pumpjack','Нефтяная помпа'),'block.factorycraft.oil_refinery':('Oil Refinery','Нефтеперерабатывающий завод'),'block.factorycraft.chemical_plant':('Chemical Plant','Химический завод'),'block.factorycraft.refinery_casing':('Refinery Part','Часть НПЗ'),'block.factorycraft.oil_deposit':('Finite Oil Deposit','Конечная нефтяная залежь'),
 'fluid.factorycraft.crude_oil':('Crude Oil','Сырая нефть'),'fluid.factorycraft.petroleum_gas':('Petroleum Gas','Нефтяной газ'),'item.factorycraft.crude_oil_bucket':('Crude Oil Bucket','Ведро нефти'),'item.factorycraft.petroleum_gas_bucket':('Petroleum Gas Bucket','Ведро нефтяного газа'),
 'item.factorycraft.plastic_bar':('Plastic Bar','Пластик'),'item.factorycraft.sulfur':('Sulfur','Сера'),'item.factorycraft.advanced_circuit':('Advanced Circuit','Продвинутая схема'),
 'message.factorycraft.oil_remaining':('Remaining oil: %s mB. Pump above; mining destroys unused reserve.','Нефти осталось: %s mB. Помпа сверху; добыча блока уничтожает остаток.'),
 'tooltip.factorycraft.pumpjack':('10 mB/t, 15 FE/t. Finite deposits in 3x3, up to 8 blocks below. Output ahead.','10 mB/т, 15 FE/т. Залежи в 3x3 до 8 блоков ниже. Выход спереди.'),
 'tooltip.factorycraft.oil_refinery':('One item forms 2x2x2. Crude behind the rear part; gas from the controller front.','Один предмет создаёт 2x2x2. Нефть сзади задней части, газ — спереди контроллера.'),
 'tooltip.factorycraft.chemical_plant':('Gas behind, second fluid on left; items via inserters. Open for recipes.','Газ сзади, вторая жидкость слева; предметы — манипуляторами. Рецепты в окне.'),
 'tooltip.factorycraft.refinery_casing':('Internal refinery part; resources belong to the controller.','Служебная часть НПЗ; ресурсы хранятся только в контроллере.'),
 'gui.factorycraft.chemical_fluid_0':('In 1: %s — %s mB','Вход 1: %s — %s mB'),'gui.factorycraft.chemical_fluid_1':('In 2: %s — %s mB','Вход 2: %s — %s mB'),'gui.factorycraft.chemical_fluid_2':('Out 1: %s — %s mB','Выход 1: %s — %s mB'),'gui.factorycraft.chemical_fluid_3':('Out 2: %s — %s mB','Выход 2: %s — %s mB'),
 'gui.factorycraft.petroleum_status_0':('Waiting','Ожидание'),'gui.factorycraft.petroleum_status_1':('Needs input/recipe','Нет сырья/рецепта'),'gui.factorycraft.petroleum_status_2':('Output blocked','Выход заполнен'),'gui.factorycraft.petroleum_status_3':('Needs energy','Нет энергии'),'gui.factorycraft.petroleum_status_4':('Working','Работает'),'gui.factorycraft.pumpjack_rate':('10 mB/t / 15 FE/t; finite oil','10 mB/т / 15 FE/т; конечная нефть'),
 'command.factorycraft.oil_space':('Need a loaded empty 22 x 6 x 9 area beside you.','Рядом нужна свободная загруженная область 22 x 6 x 9.'),'command.factorycraft.oil_demo':('Finite oil-to-plastic factory built at %s.','Фабрика из конечной нефти в пластик: %s.')}
for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';content=json.loads(path.read_text());content.update({k:v[index] for k,v in oil_lang.items()});write(str(path.relative_to(ROOT)),content)
write('data/factorycraft/advancement/recipes/petroleum_basics.json',{'criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},'rewards':{'recipes':['factorycraft:craft_'+name for name in ['pumpjack','oil_refinery','chemical_plant']]}})
write('data/factorycraft/advancement/recipes/factory_basics.json',{'criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},'rewards':{'recipes':['factorycraft:'+p.stem for p in sorted((ROOT/'data/factorycraft/recipe').glob('craft_*.json'))]}})

# 0.6 resource tiles and automatic machine footprints. These overrides replace one-drop ores.
profiles={'electric_drill':(3,2,3),'electric_furnace':(3,3,3),'assembling_machine':(3,3,3),'coal_generator':(3,3,3),'industrial_furnace':(3,3,3),'pumpjack':(3,4,3),'oil_refinery':(5,4,5),'chemical_plant':(3,3,3),'boiler':(3,2,2),'steam_engine':(3,2,5),'fluid_tank':(3,3,3),'electric_pump':(1,1,2),'splitter':(2,1,1)}
for casing in ['machine_casing','refinery_casing']:
 variants={}
 for profile in profiles:
  for direction,rot in [('north',0),('east',90),('south',180),('west',270)]:
   for lit in ['false','true']:
    # Skin follows its owner; splitter parts also retain the low belt silhouette.
    variants[f'profile={profile},facing={direction},lit={lit}']={'model':'factorycraft:block/'+profile,'y':rot}
 write(f'assets/factorycraft/blockstates/{casing}.json',{'variants':variants})
for name,color in [('coal_deposit',(57,61,66)),('stone_deposit',(131,127,119))]:
 texture('block/'+name,color,'ore')
 write(f'assets/factorycraft/models/block/{name}.json',{'parent':'minecraft:block/cube_all','textures':{'all':'factorycraft:block/'+name}})
 write(f'assets/factorycraft/blockstates/{name}.json',{'variants':{'':{'model':'factorycraft:block/'+name}}})
for name in ['iron_ore','copper_ore','coal_deposit','stone_deposit']:
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[]})
 configured={'type':'factorycraft:resource_patch','config':{'size':32,'discard_chance_on_air_exposure':0.0,'targets':[{'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:stone_ore_replaceables'},'state':{'Name':'factorycraft:'+name}}]}}
 write(f'data/factorycraft/worldgen/configured_feature/{name}.json',configured)
 if name in ['coal_deposit','stone_deposit']:
  placed=json.loads((ROOT/'data/factorycraft/worldgen/placed_feature/iron_ore.json').read_text());placed['feature']='factorycraft:'+name
  write(f'data/factorycraft/worldgen/placed_feature/{name}.json',placed)
  write(f'data/factorycraft/neoforge/biome_modifier/{name}.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':['factorycraft:'+name],'step':'underground_ores'})
tag=json.loads((ROOT/'data/minecraft/tags/block/mineable/pickaxe.json').read_text());tag['values']+=['factorycraft:coal_deposit','factorycraft:stone_deposit'];write('data/minecraft/tags/block/mineable/pickaxe.json',tag)
for lang in ['en_us','ru_ru']:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text());ru=lang=='ru_ru'
 data.update({
 'block.factorycraft.coal_deposit':'Залежь угля' if ru else 'Coal deposit',
 'block.factorycraft.stone_deposit':'Залежь камня' if ru else 'Stone deposit',
 'message.factorycraft.resource_remaining':'%s — запас: %s' if ru else '%s — remaining: %s',
 'message.factorycraft.resource_inventory_full':'Нет места для добытого ресурса' if ru else 'No room for mined resource',
 'message.factorycraft.placement_space':'Весь объём машины должен быть свободен и загружен' if ru else 'The complete machine volume must be clear and loaded',
 'tooltip.factorycraft.machine_dimensions':'Габариты: %s × %s блоков; высота %s' if ru else 'Footprint: %s × %s blocks; height %s',
 'tooltip.factorycraft.machine_preview':'Один предмет создаёт всю машину. Превью: зелёное — свободно, красное — препятствие.' if ru else 'One item builds the complete machine. Preview: green = clear, red = obstructed.',
 'command.factorycraft.full_size_space':'Нужна загруженная свободная область 30 × 8 × 13 рядом с вами.' if ru else 'Need a loaded, empty 30 × 8 × 13 area beside you.',
 'command.factorycraft.full_size_built':'Построено: %s, координаты %s' if ru else 'Built %s at %s',
 'tooltip.factorycraft.industrial_furnace':'Один предмет создаёт печь 3×3×3. Скорость ×2; тот же расход FE на результат.' if ru else 'One item builds a 3×3×3 furnace. Twice the speed; same FE per result.',
 'tooltip.factorycraft.oil_refinery':'НПЗ 5×5. Нефть — в центр задней грани, газ — из центра передней.' if ru else '5×5 refinery. Crude enters rear center; gas leaves front center.',
 'tooltip.factorycraft.electric_drill':'Бур 3×3. Добывает запас залежей в области 5×5 на глубину до 8 блоков.' if ru else '3×3 drill. Extracts finite deposits in a 5×5 mining area, up to 8 blocks below.',
 'tooltip.factorycraft.splitter':'Два передних выхода, чередование предметов. Ширина 2 блока.' if ru else 'Alternates between two forward outputs. Two blocks wide.',
 'preview.factorycraft.valid':'%s × %s × %s — можно установить' if ru else '%s × %s × %s — ready to place',
 'preview.factorycraft.blocked':'%s × %s × %s — занятых ячеек: %s' if ru else '%s × %s × %s — blocked cells: %s',
 })
 write(f'assets/factorycraft/lang/{lang}.json',data)

# 0.6.1: one deterministic large surface-field sampler per resource and chunk.
for index,name in enumerate(['iron_ore','copper_ore','coal_deposit','stone_deposit']):
 write(f'data/factorycraft/worldgen/configured_feature/{name}.json',{'type':'factorycraft:resource_patch','config':{'state':{'Name':'factorycraft:'+name},'salt':920174+(index*812791),'use_world_settings':True,'vein':{'min_radius':8,'max_radius':20,'min_stock':500,'max_stock':5000,'spacing_chunks':6}}})
 write(f'data/factorycraft/worldgen/placed_feature/{name}.json',{'feature':'factorycraft:'+name,'placement':[{'type':'minecraft:biome'}]})
for lang in ['en_us','ru_ru']:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text());ru=lang=='ru_ru'
 data['tooltip.factorycraft.machine_preview']='Установка от центра основания. Синяя ячейка — выбранный центр; красное — препятствие.' if ru else 'Places around the base center. Blue cell = selected center; red = obstruction.'
 write(f'assets/factorycraft/lang/{lang}.json',data)

# 0.7 original unified mesh specifications, item models and fixed nozzle markers.
from machine_geometry import generate as generate_machine_geometry
generate_machine_geometry(ROOT,write,png)
for lang in ['en_us','ru_ru']:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text());ru=lang=='ru_ru'
 data.update({
 'tooltip.factorycraft.splitter':'Два входа и выхода, четыре полосы. Каждая половина имеет свой предметный порт.' if ru else 'Two inputs/outputs and four lanes. Each physical track has its own item port.',
 'command.factorycraft.showroom_space':'Нужна загруженная свободная область 32 × 6 × 32.' if ru else 'Need a loaded, clear 32 × 6 × 32 area.',
 'command.factorycraft.showroom_built':'Выставка машин с конечными запасами построена: %s' if ru else 'Machine showroom with finite supplies built at %s',
 'tooltip.factorycraft.fluid_nozzles':'Синие патрубки — вход, оранжевые — выход, бирюзовые — двусторонние.' if ru else 'Blue nozzles: input; orange: output; teal: bidirectional.'})
 write(f'assets/factorycraft/lang/{lang}.json',data)

# 0.8 advanced petroleum resources and physical-port help.
for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text())
 for name,en,ru in [('heavy_oil','Heavy Oil','Тяжёлая нефть'),('light_oil','Light Oil','Лёгкая нефть'),('lubricant','Lubricant','Смазка'),('sulfuric_acid','Sulfuric Acid','Серная кислота')]:
  data['fluid.factorycraft.'+name]=(en,ru)[index];data['item.factorycraft.'+name+'_bucket']=(en+' Bucket','Ведро: '+ru.lower())[index]
 data['item.factorycraft.battery']=('Battery','Аккумулятор')[index]
 data['tooltip.factorycraft.oil_refinery']=('5×5 refinery. Crude rear, water left. Gas front-center, light oil right, heavy oil front-left (3).','НПЗ 5×5. Нефть сзади, вода слева. Газ спереди по центру, лёгкая нефть справа, тяжёлая спереди слева (3).')[index]
 data['gui.factorycraft.chemical_fluid_4']=('Out 3: %s — %s mB','Выход 3: %s — %s мБ')[index]
 for i,(en,ru) in enumerate([('Rear input','Вход сзади'),('Left input','Вход слева'),('Front-center output','Выход спереди по центру'),('Right output','Выход справа'),('Front-left output (3)','Выход спереди слева (3)')]):data['gui.factorycraft.chemical_port_'+str(i)]=(en,ru)[index]
 data['command.factorycraft.petrochemistry_recipe']=('Advanced oil processing is missing; restore its datapack recipe before building the example.','Рецепт продвинутой переработки отсутствует. Восстановите его в датапаке для постройки примера.')[index]
 data['command.factorycraft.petrochemistry_space']=('Need a loaded empty 30 × 8 × 16 area beside you.','Рядом нужна загруженная свободная область 30 × 8 × 16.')[index]
 for name,en,ru in [('basic_oil_processing','Basic oil processing','Базовая переработка нефти'),('advanced_oil_processing','Advanced oil processing','Продвинутая переработка нефти'),('heavy_oil_cracking','Heavy oil cracking','Крекинг тяжёлой нефти'),('light_oil_cracking','Light oil cracking','Крекинг лёгкой нефти'),('lubricant','Lubricant','Смазка'),('sulfuric_acid','Sulfuric acid','Серная кислота'),('battery','Battery','Аккумулятор'),('plastic','Plastic','Пластик'),('sulfur','Sulfur','Сера')]:data['recipe.factorycraft.'+name]=(en,ru)[index]
 write(f'assets/factorycraft/lang/{lang}.json',data)

# 0.9 research bootstrap: red packs are hand-craftable; green packs require automation research.
for name,color in [('automation_science_pack',(194,65,62)),('logistic_science_pack',(71,180,112))]:
 texture('item/'+name,color,'item');write(f'assets/factorycraft/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/'+name}})
craft('automation_science_pack',['GC'],{'G':'factorycraft:iron_gear','C':'factorycraft:copper_plate'})
craft('lab',['CCC','GAG','PPP'],{'C':'factorycraft:electronic_circuit','G':'factorycraft:iron_gear','A':'factorycraft:assembling_machine','P':'factorycraft:iron_plate'})
process('automation_science_pack','assembling_machine',[('factorycraft:iron_gear',1),('factorycraft:copper_plate',1)],'automation_science_pack',1,100,16)
process('logistic_science_pack','assembling_machine',[('factorycraft:transport_belt',1),('factorycraft:inserter',1)],'logistic_science_pack',1,120,20)
techs=[('automation',[],10,40,False,['logistic_science_pack'],0,0),('logistics',['automation'],15,60,False,[],1,0),('electronics',['automation'],15,60,False,['advanced_circuit'],1,1),('oil_processing',['logistics'],20,80,False,['advanced_oil_processing','heavy_oil_cracking','light_oil_cracking','lubricant'],2,0),('batteries',['oil_processing','electronics'],20,80,False,['sulfuric_acid','battery'],2,1),('lab_efficiency',['batteries'],30,60,True,[],3,1)]
for name,parents,units,ticks,repeat,unlocks,x,y in techs:
 packs={'factorycraft:automation_science_pack':1}
 if parents:packs['factorycraft:logistic_science_pack']=1
 write(f'data/factorycraft/recipe/{name}.json',{'type':'factorycraft:research','prerequisites':['factorycraft:'+p for p in parents],'packs':packs,'units':units,'ticks':ticks,'repeatable':repeat,'unlocks':['factorycraft:'+p for p in unlocks],'x':x,'y':y})
for lang,index in [('en_us',0),('ru_ru',1)]:
 p=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(p.read_text())
 values={'block.factorycraft.lab':('Laboratory','Лаборатория'),'item.factorycraft.automation_science_pack':('Automation Science Pack','Автоматизационный научный пакет'),'item.factorycraft.logistic_science_pack':('Logistic Science Pack','Логистический научный пакет'),'gui.factorycraft.research_search':('Search technologies','Поиск технологий'),'gui.factorycraft.science':('Science packs','Научные пакеты'),'gui.factorycraft.lab_work':('Unit work: %s t','Работа единицы: %s т'),'gui.factorycraft.requires':('Requires: %s','Требуется: %s'),'tooltip.factorycraft.lab':('Powered team research. One item forms 3×3×3; insert science packs and select a technology.','Исследования команды. Один предмет создаёт 3×3×3; подайте научные пакеты и выберите технологию.')}
 for name,en,ru in [('automation','Automation','Автоматизация'),('logistics','Logistics','Логистика'),('electronics','Electronics','Электроника'),('oil_processing','Advanced oil processing','Переработка нефти'),('batteries','Batteries','Аккумуляторы'),('lab_efficiency','Lab efficiency (repeatable)','Эффективность лабораторий')]:values['technology.factorycraft.'+name]=(en,ru)
 data.update({k:v[index] for k,v in values.items()});write(f'assets/factorycraft/lang/{lang}.json',data)

write('data/factorycraft/loot_table/blocks/lab.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:lab'}]}]})

for lang,index in [('en_us',0),('ru_ru',1)]:
 p=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(p.read_text());data['gui.factorycraft.research_drag']=('Right-drag to pan','ПКМ: двигать дерево')[index]
 for name in ['electric_furnace','assembling_machine','oil_refinery','chemical_plant','boiler','lab']:data['category.factorycraft.'+name]=data['block.factorycraft.'+name]
 write(f'assets/factorycraft/lang/{lang}.json',data)

for lang,index in [('en_us',0),('ru_ru',1)]:
 p=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(p.read_text());data['command.factorycraft.research_team_unavailable']=('Research team is unavailable.','Команда исследований недоступна.')[index];data['message.factorycraft.research_team_required']=('This laboratory belongs to another team.','Лаборатория принадлежит другой команде.')[index];write(f'assets/factorycraft/lang/{lang}.json',data)

for lang in ['en_us','ru_ru']:
 p=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(p.read_text())
 for name in ['electric_furnace','assembling_machine','oil_refinery','chemical_plant','boiler','lab']:data['emi.category.factorycraft.'+name]=data['block.factorycraft.'+name]
 write(f'assets/factorycraft/lang/{lang}.json',data)

for lang,index in [('en_us',0),('ru_ru',1)]:
 p=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(p.read_text());data['gui.factorycraft.needs_research']=('Research needed','Исследование')[index];data['gui.factorycraft.petroleum_status_5']=('Research needed','Нужно исследование')[index];write(f'assets/factorycraft/lang/{lang}.json',data)

# 0.10: fluid assembler, chemical science and exact-volume recovery.
for name,color in [('steel_plate',(123,140,157)),('engine_unit',(177,145,67)),('electric_engine_unit',(73,135,191)),('processing_unit',(65,106,194)),('chemical_science_pack',(62,141,204)),('fluid_canister',(135,166,181))]:
 texture('item/'+name,color,'item')
 write(f'assets/factorycraft/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/'+name}})
process('steel_plate','electric_furnace',[('factorycraft:iron_plate',5)],'steel_plate',1,320,12)
process('engine_unit','assembling_machine',[('factorycraft:steel_plate',1),('factorycraft:iron_gear',1),('factorycraft:fluid_pipe',2)],'engine_unit',1,200,24)
process('assembling_machine_2','assembling_machine',[('factorycraft:steel_plate',5),('factorycraft:advanced_circuit',3),('factorycraft:iron_gear',5)],'assembling_machine_2',1,200,24)
process('chemical_science_pack','assembling_machine',[('factorycraft:advanced_circuit',2),('factorycraft:engine_unit',3),('factorycraft:sulfur',1)],'chemical_science_pack',2,480,24)
for name,ingredients,fluid,amount,energy in [('electric_engine_unit',[('engine_unit',1),('electronic_circuit',2)],'lubricant',15,30),('processing_unit',[('electronic_circuit',20),('advanced_circuit',2)],'sulfuric_acid',5,40)]:
 write(f'data/factorycraft/recipe/{name}.json',{'type':'factorycraft:chemistry','machine':'assembling_machine_2','items':[{'ingredient':{'item':'factorycraft:'+i},'count':n} for i,n in ingredients],'fluids':[{'fluid':'factorycraft:'+fluid,'amount':amount}],'results':[{'id':'factorycraft:'+name,'count':1}],'ticks':200,'energy':energy})
craft('fluid_canister',['IPI','IGI','III'],{'I':'factorycraft:iron_plate','P':'factorycraft:fluid_pipe','G':'minecraft:glass'})
write('data/factorycraft/loot_table/blocks/assembling_machine_2.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:assembling_machine_2'}]}]})
p=ROOT/'data/minecraft/tags/block/mineable/pickaxe.json';tag=json.loads(p.read_text());tag['values'].append('factorycraft:assembling_machine_2');write('data/minecraft/tags/block/mineable/pickaxe.json',tag)
new_techs=[('steel_processing',['automation'],10,60,['steel_plate'],1,2,False),('engine',['steel_processing','logistics'],20,60,['engine_unit'],2,2,False),('automation_2',['electronics'],20,60,['assembling_machine_2'],2,3,False),('chemical_science',['engine','batteries'],30,80,['chemical_science_pack'],3,2,False),('advanced_electronics_2',['chemical_science','automation_2'],50,120,['processing_unit'],4,2,True),('electric_engine',['chemical_science','automation_2'],40,100,['electric_engine_unit'],4,3,True)]
for name,parents,units,ticks,unlocks,x,y,blue in new_techs:
 packs={'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1}
 if blue:packs['factorycraft:chemical_science_pack']=1
 write(f'data/factorycraft/recipe/{name}.json',{'type':'factorycraft:research','prerequisites':['factorycraft:'+p for p in parents],'packs':packs,'units':units,'ticks':ticks,'repeatable':False,'unlocks':['factorycraft:'+p for p in unlocks],'x':x,'y':y})
for lang,index in [('en_us',0),('ru_ru',1)]:
 p=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(p.read_text())
 for name,en,ru in [('steel_plate','Steel Plate','Стальная пластина'),('engine_unit','Engine Unit','Двигатель'),('electric_engine_unit','Electric Engine Unit','Электродвигатель'),('processing_unit','Processing Unit','Процессор'),('chemical_science_pack','Chemical Science Pack','Химический научный пакет'),('fluid_canister','Fluid Canister','Канистра')]:
  data['item.factorycraft.'+name]=(en,ru)[index];data['recipe.factorycraft.'+name]=(en,ru)[index]
 for name,en,ru in [('steel_processing','Steel processing','Обработка стали'),('engine','Engines','Двигатели'),('automation_2','Automation 2','Автоматизация 2'),('chemical_science','Chemical science','Химическая наука'),('advanced_electronics_2','Advanced electronics 2','Продвинутая электроника 2'),('electric_engine','Electric engines','Электродвигатели')]:data['technology.factorycraft.'+name]=(en,ru)[index]
 values={'block.factorycraft.assembling_machine_2':('Assembling Machine 2','Сборочный автомат 2'),'recipe.factorycraft.assembling_machine_2':('Assembling Machine 2','Сборочный автомат 2'),'tooltip.factorycraft.assembling_machine_2':('3×3 fluid assembler. Feed lubricant/acid at the rear input; recipes in its menu.','Сборщик 3×3 с жидкостями. Смазка/кислота — через вход сзади; рецепты в меню.'),'gui.factorycraft.recover_fluid':('Carry a canister on the cursor; recover this input fluid without loss.','Возьмите канистру курсором: извлечь жидкость этого входа без потерь.'),'tooltip.factorycraft.canister_contents':('%s: %s / %s mB','%s: %s / %s mB'),'tooltip.factorycraft.canister_recovery':('Recover input leftovers in a machine menu; right-click a fluid port to transfer.','Остатки входов — кнопками в меню машины; перенос — ПКМ по патрубку.')}
 data.update({k:v[index] for k,v in values.items()});data['emi.category.factorycraft.assembling_machine_2']=data['block.factorycraft.assembling_machine_2'];write(f'assets/factorycraft/lang/{lang}.json',data)
# Include the canister in the ordinary recipe book on the first generation as well.
p=ROOT/'data/factorycraft/advancement/recipes/factory_basics.json';adv=json.loads(p.read_text());adv['rewards']['recipes']=sorted(set(adv['rewards']['recipes']+['factorycraft:craft_fluid_canister']));write('data/factorycraft/advancement/recipes/factory_basics.json',adv)

# 0.11: bounded module effects and powered beacons.
for name,color in [('speed_module',(71,135,207)),('efficiency_module',(63,174,94)),('productivity_module',(203,75,71))]:
 texture('item/'+name,color,'item');write(f'assets/factorycraft/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/'+name}})
 process(name,'assembling_machine',[('factorycraft:advanced_circuit',5),('factorycraft:processing_unit' if name=='productivity_module' else 'factorycraft:electronic_circuit',5)],name,1,300,24)
process('beacon','assembling_machine',[('factorycraft:steel_plate',10),('factorycraft:electronic_circuit',10),('factorycraft:processing_unit',10)],'beacon',1,400,30)
for name,parents,units,unlocks,x,y,blue in [('modules',['automation_2'],30,['speed_module','efficiency_module'],3,3,False),('productivity',['modules','advanced_electronics_2'],25,['productivity_module'],5,2,True),('effect_transmission',['modules','advanced_electronics_2'],40,['beacon'],5,3,True)]:
 packs={'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1}
 if blue:packs['factorycraft:chemical_science_pack']=1
 write(f'data/factorycraft/recipe/{name}.json',{'type':'factorycraft:research','prerequisites':['factorycraft:'+p for p in parents],'packs':packs,'units':units,'ticks':120 if blue else 60,'repeatable':False,'unlocks':['factorycraft:'+p for p in unlocks],'x':x,'y':y})
write('data/factorycraft/tags/item/productivity_outputs.json',{'replace':False,'values':['factorycraft:'+p for p in ['iron_plate','copper_plate','steel_plate','iron_gear','copper_wire','electronic_circuit','advanced_circuit','processing_unit','engine_unit','electric_engine_unit','automation_science_pack','logistic_science_pack','chemical_science_pack','plastic_bar','sulfur','battery']]})
write('data/factorycraft/loot_table/blocks/beacon.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:beacon'}]}]})
p=ROOT/'data/minecraft/tags/block/mineable/pickaxe.json';tag=json.loads(p.read_text());tag['values'].append('factorycraft:beacon');write('data/minecraft/tags/block/mineable/pickaxe.json',tag)
for lang,index in [('en_us',0),('ru_ru',1)]:
 data=json.loads((ROOT/f'assets/factorycraft/lang/{lang}.json').read_text())
 values={'item.factorycraft.speed_module':('Speed Module','Модуль скорости'),'item.factorycraft.efficiency_module':('Efficiency Module','Модуль эффективности'),'item.factorycraft.productivity_module':('Productivity Module','Модуль продуктивности'),'block.factorycraft.beacon':('Beacon','Маяк эффектов'),'gui.factorycraft.modules':('Modules','Модули'),'gui.factorycraft.module_rates':('Speed %s%%; FE %s%%','Скорость %s%%; FE %s%%'),'gui.factorycraft.module_productivity':('Bonus %s%%; credit %s%%','Бонус %s%%; запас %s%%'),'gui.factorycraft.beacon_on':('Broadcasting: 40 FE/t','Передача: 40 FE/т'),'gui.factorycraft.beacon_off':('No power/modules/structure','Нет FE/модулей/структуры'),'tooltip.factorycraft.module_speed':('+20% speed; +50% energy','+20% скорости; +50% энергии'),'tooltip.factorycraft.module_efficiency':('-30% energy; minimum 20%','−30% энергии; минимум 20%'),'tooltip.factorycraft.module_productivity':('+4% eligible output; -5% speed; +40% energy','+4% подходящего выпуска; −5% скорости; +40% энергии'),'tooltip.factorycraft.beacon':('3×3 powered beacon. Two speed/efficiency slots; range 3 beyond footprint.','Маяк 3×3. Два модуля скорости/эффективности; охват 3 за габаритами.')}
 for name,en,ru in [('modules','Modules','Модули'),('productivity','Productivity','Продуктивность'),('effect_transmission','Effect transmission','Передача эффектов')]:values['technology.factorycraft.'+name]=(en,ru)
 for name in ['speed_module','efficiency_module','productivity_module']:values['recipe.factorycraft.'+name]=values['item.factorycraft.'+name]
 values['recipe.factorycraft.beacon']=values['block.factorycraft.beacon'];data.update({k:v[index] for k,v in values.items()});write(f'assets/factorycraft/lang/{lang}.json',data)
