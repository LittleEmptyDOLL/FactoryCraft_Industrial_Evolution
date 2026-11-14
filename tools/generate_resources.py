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
 'iron_ore':(104,110,115),'copper_ore':(110,112,108),'steel_furnace':(187,93,43),'machine_casing':(79,99,116)}
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
 active=name in ['electric_drill','electric_furnace','assembling_machine','coal_generator','steel_furnace','machine_casing']
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
top=texture('block/steel_furnace_top',colors['steel_furnace'],'machine')
for yy in range(2,13):
 for xx in range(16):
  if (yy<=6 and abs(xx-7.5)<=yy-1.5) or (6<=yy<=12 and xx in (7,8)):top[yy][xx]=(255,194,61,255)
png('block/steel_furnace_top',top)
for suffix in ['', '_active']:
 write(f'assets/factorycraft/models/block/steel_furnace{suffix}.json',{'parent':'minecraft:block/orientable','textures':{'front':'factorycraft:block/steel_furnace'+suffix,'side':'factorycraft:block/machine_casing','top':'factorycraft:block/steel_furnace_top'}})
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
 write(f'data/factorycraft/recipe/{name}.json',{'type':'factorycraft:processing','machine':machine,'inputs':[{'ingredient':ingredient(i),'count':n} for i,n in inputs],'result':{'id':result if ':' in result else 'factorycraft:'+result,'count':count},'ticks':ticks,'energy':energy})
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
craft('steel_furnace',['CPC','FGF','CRC'],{'C':'minecraft:stone_bricks','P':'factorycraft:iron_plate','F':'factorycraft:electric_furnace','G':'factorycraft:iron_gear','R':'minecraft:redstone'})
labels={
 'steel_furnace':('Industrial Furnace','Промышленная печь'),'machine_casing':('Machine Casing','Корпус машины'),
 'transport_belt':('Transport Belt','Конвейер'),'fast_transport_belt':('Fast Transport Belt','Быстрый конвейер'),'express_transport_belt':('Express Transport Belt','Экспресс-конвейер'),'splitter':('T-Splitter','Т-разделитель'),
 'inserter':('Mechanical Inserter','Механический манипулятор'),'electric_drill':('Electric Mining Drill','Электрический бур'),'electric_furnace':('Electric Furnace','Электрическая печь'),'assembling_machine':('Assembling Machine','Сборочный автомат'),
 'coal_generator':('Coal Generator','Угольный генератор'),'electric_pole':('Electric Pole','Электрический столб'),'iron_ore':('Industrial Iron Ore','Промышленная железная руда'),'copper_ore':('Industrial Copper Ore','Промышленная медная руда'),
 'iron_plate':('Iron Plate','Железная пластина'),'copper_plate':('Copper Plate','Медная пластина'),'iron_gear':('Iron Gear','Железная шестерня'),'copper_wire':('Copper Wire','Медный провод'),'electronic_circuit':('Electronic Circuit','Электронная схема'),'wrench':('Factory Wrench','Заводской ключ')}
ui={'message.factorycraft.placement_space':('Need a loaded, unobstructed 2x2x2 area to the right, above and behind the front block.','Нужен загруженный свободный объём 2x2x2: справа, сверху и позади переднего блока.'),'command.factorycraft.smelter_space':('Need a loaded empty 6 x 5 x 7 area beside you.','Рядом нужна загруженная свободная область 6 x 5 x 7.'),'tooltip.factorycraft.multiblock_controls':('Places the complete machine. Sneak + wrench dismantles it.','Сразу устанавливает всю машину. Shift + ключ — разборка.'),'gui.factorycraft.assembly.0':('Unformed','Не собрана'),'gui.factorycraft.assembly.1':('Chunks pending','Чанк выгружен'),'gui.factorycraft.assembly.2':('Assembled','Собрана'),
 'message.factorycraft.assembly_missing':('Structure unavailable. Check that all its chunks are loaded and parts are intact.','Структура недоступна. Проверьте загрузку чанков и целостность частей.'),
 'message.factorycraft.dismantle':('Sneak + wrench dismantles the machine safely; contents are not destroyed.','Shift + ключ: безопасно разобрать машину; предметы не уничтожаются.'),
 'command.factorycraft.smelter':('Multiblock smelter built at %s. Its source chest contains 12 raw iron.','Мультиблочная печь построена: %s. Во входном сундуке 12 необработанного железа.'),
 'tooltip.factorycraft.steel_furnace':('One item places a 2x2x2 machine. Twice the furnace speed; same FE per result.','Один предмет устанавливает машину 2x2x2. Скорость x2; тот же расход FE на результат.'),
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
write('data/factorycraft/advancement/recipes/multiblock_basics.json',{'criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},'rewards':{'recipes':['factorycraft:craft_steel_furnace']}})
# Empty structure used by all code-built integration tests (NBT, data version 3955 / Minecraft 1.21.1).
def utf(s):
 b=s.encode();return struct.pack('>H',len(b))+b
def named(t,n,p):return bytes([t])+utf(n)+p
nbt=b'\x0a\x00\x00'+named(3,'DataVersion',struct.pack('>i',3955))+named(9,'size',b'\x03'+struct.pack('>i',3)+struct.pack('>iii',32,12,24))+named(9,'palette',b'\x0a'+struct.pack('>i',1)+named(8,'Name',utf('minecraft:air'))+b'\x00')+named(9,'blocks',b'\x0a'+struct.pack('>i',0))+named(9,'entities',b'\x0a'+struct.pack('>i',0))+b'\x00'
p=ROOT/'data/factorycraft/structure/empty.nbt';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(gzip.compress(nbt,mtime=0))
large=nbt.replace(struct.pack('>iii',32,12,24),struct.pack('>iii',32,16,24),1);write_path=ROOT/'data/factorycraft/structure/industrial.nbt';write_path.write_bytes(gzip.compress(large,mtime=0))

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
write('assets/factorycraft/blockstates/oil_deposit.json',{'variants':{'':{'model':'factorycraft:block/oil_deposit'}}})
write('data/factorycraft/loot_table/blocks/oil_deposit.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'minecraft:cobblestone'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write('data/factorycraft/worldgen/configured_feature/oil_deposit.json',{'type':'factorycraft:oil_deposit','config':{}})
write('data/factorycraft/worldgen/placed_feature/oil_deposit.json',{'feature':'factorycraft:oil_deposit','placement':[{'type':'minecraft:biome'}]})
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
profiles={'electric_drill':(3,2,3),'electric_furnace':(3,3,3),'assembling_machine':(3,3,3),'coal_generator':(3,3,3),'steel_furnace':(2,3,2),'stone_furnace':(2,2,2),'pumpjack':(3,4,3),'oil_refinery':(5,4,5),'chemical_plant':(3,3,3),'boiler':(3,2,2),'steam_engine':(3,2,5),'fluid_tank':(3,3,3),'electric_pump':(1,1,2),'splitter':(2,1,1)}
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
 'tooltip.factorycraft.steel_furnace':'Один предмет создаёт печь 3×3×3. Скорость ×2; тот же расход FE на результат.' if ru else 'One item builds a 3×3×3 furnace. Twice the speed; same FE per result.',
 'tooltip.factorycraft.oil_refinery':'НПЗ 5×5. Нефть — в центр задней грани, газ — из центра передней.' if ru else '5×5 refinery. Crude enters rear center; gas leaves front center.',
 'tooltip.factorycraft.electric_drill':'Бур 3×3. Добывает запас залежей в области 5×5 на глубину до 8 блоков.' if ru else '3×3 drill. Extracts finite deposits in a 5×5 mining area, up to 8 blocks below.',
 'tooltip.factorycraft.splitter':'Два передних выхода, чередование предметов. Ширина 2 блока.' if ru else 'Alternates between two forward outputs. Two blocks wide.',
 'preview.factorycraft.valid':'%s × %s × %s — можно установить' if ru else '%s × %s × %s — ready to place',
 'preview.factorycraft.blocked':'%s × %s × %s — занятых ячеек: %s' if ru else '%s × %s × %s — blocked cells: %s',
 })
 write(f'assets/factorycraft/lang/{lang}.json',data)

# 0.6.1: one deterministic large surface-field sampler per resource and chunk.
for index,name in enumerate(['iron_ore','copper_ore','coal_deposit','stone_deposit']):
 write(f'data/factorycraft/worldgen/configured_feature/{name}.json',{'type':'factorycraft:resource_patch','config':{'state':{'Name':'factorycraft:'+name},'salt':920174+(index*812791),'use_world_settings':True,'vein':{'min_radius':8,'max_radius':20,'min_stock':500,'max_stock':5000,'spacing_chunks':16,'min_depth':5,'max_depth':8,'noise':0.12}}})
 write(f'data/factorycraft/worldgen/placed_feature/{name}.json',{'feature':'factorycraft:'+name,'placement':[{'type':'minecraft:biome'}]})
for lang in ['en_us','ru_ru']:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text());ru=lang=='ru_ru'
 data['tooltip.factorycraft.machine_preview']='3×3 — от центра; крупные — от ближайшего края. Shift: от центра. Синяя ячейка — точка установки.' if ru else '3×3: center; large: nearest edge. Hold Shift for center. Blue cell: placement target.'
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

# 0.12 transport surfaces use native cached geometry; all artwork remains original.
transport_tiers=[('transport_belt','conveyor_lift','underground_belt'),('fast_transport_belt','fast_conveyor_lift','fast_underground_belt'),('express_transport_belt','express_conveyor_lift','express_underground_belt')]
for tier,names in enumerate(transport_tiers,1):
 for name in names:
  write(f'assets/factorycraft/models/block/{name}.json',{'loader':'factorycraft:belt','textures':{'normal':'factorycraft:block/'+names[0],'fast':'factorycraft:block/fast_transport_belt','express':'factorycraft:block/express_transport_belt','particle':'factorycraft:block/'+names[0]}})
  write(f'assets/factorycraft/blockstates/{name}.json',{'variants':{'':{'model':'factorycraft:block/'+name}}})
  if name!=names[0]:
   pixels=texture('item/'+name,[(221,168,42),(212,63,48),(53,156,209)][tier-1],'machine')
   write(f'assets/factorycraft/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/'+name}})
   write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
  if 'lift' in name:craft(name,['BGB','B B'],{'B':'factorycraft:'+names[0],'G':'factorycraft:iron_gear'})
  elif 'underground' in name:craft(name,['III','BBB'],{'I':'minecraft:iron_ingot','B':'factorycraft:'+names[0]},count=2)
write('assets/factorycraft/models/block/transport_part.json',{'loader':'factorycraft:belt','textures':{'normal':'factorycraft:block/transport_belt','fast':'factorycraft:block/fast_transport_belt','express':'factorycraft:block/express_transport_belt','particle':'factorycraft:block/transport_belt'}})
write('assets/factorycraft/blockstates/transport_part.json',{'variants':{'':{'model':'factorycraft:block/transport_part'}}})
write('data/factorycraft/loot_table/blocks/transport_part.json',{'type':'minecraft:block','pools':[]})
for locale in ['en_us','ru_ru']:
 path=ROOT/f'assets/factorycraft/lang/{locale}.json';strings=json.loads(path.read_text());ru=locale=='ru_ru'
 for tier,names in enumerate(transport_tiers):
  prefix=['','Быстрый ','Экспресс-'][tier] if ru else ['','Fast ','Express '][tier]
  strings['block.factorycraft.'+names[1]]=prefix+('конвейерный лифт' if ru else 'Conveyor Lift')
  strings['block.factorycraft.'+names[2]]=prefix+('подземный конвейер' if ru else 'Underground Belt')
 strings['block.factorycraft.transport_part']='Часть транспортной конструкции' if ru else 'Transport Structure Part'
 strings['message.factorycraft.lift_select_height']='Основание выбрано. Нажмите на нужной высоте; направление взгляда задаёт выход. Shift+ПКМ — отмена.' if ru else 'Base selected. Click the target height; look toward the exit. Sneak-use cancels.'
 strings['message.factorycraft.lift_invalid']='Выберите высоту от 1 до %s блоков; переносной лифт сохраняет свою геометрию.' if ru else 'Choose a height from 1 to %s blocks; portable lifts retain their geometry.'
 strings['tooltip.factorycraft.transport_controls']='Ключ: поворот. Shift+ключ: авто/прямой/подъём/спуск; у подземного — вход/выход.' if ru else 'Wrench: rotate. Sneak-wrench: auto/flat/up/down; underground: input/output.'
 strings['tooltip.factorycraft.lift_controls']='Два нажатия: основание и высота. Shift+ПКМ — отмена. Ключ — выход; Shift+ключ — демонтаж.' if ru else 'Two clicks: base, then height. Sneak-use cancels. Wrench rotates exit; sneak-wrench packs.'
 write(f'assets/factorycraft/lang/{locale}.json',strings)

for locale in ['en_us','ru_ru']:
 path=ROOT/f'assets/factorycraft/lang/{locale}.json';strings=json.loads(path.read_text());ru=locale=='ru_ru'
 shapes={'flat':('Horizontal','Горизонтальный'),'left':('Left curve','Левый поворот'),'right':('Right curve','Правый поворот'),'up':('Incline up','Подъём'),'down':('Incline down','Спуск'),'lift':('Conveyor lift','Конвейерный лифт'),'underground':('Underground','Подземный')}
 directions={'north':('North','Север'),'south':('South','Юг'),'west':('West','Запад'),'east':('East','Восток')}
 modes={'0':('Automatic','Авто'),'1':('Forced horizontal','Горизонтальный вручную'),'2':('Forced up','Подъём вручную'),'3':('Forced down','Спуск вручную'),'input':('Input','Вход'),'output':('Output','Выход')}
 for prefix,values in [('shape',shapes),('direction',directions),('mode',modes)]:
  for key,value in values.items():strings['transport.factorycraft.'+prefix+'.'+key]=value[1 if ru else 0]
 strings['message.factorycraft.transport_state']='%s · %s · %s'
 strings['message.factorycraft.transport_blocked']='Освободите участок от предметов и проверьте свободное место.' if ru else 'Drain the section and check physical clearance.'
 write(f'assets/factorycraft/lang/{locale}.json',strings)

# Native tool tags include generated physical parts and all new transport tiers.
tag_path=ROOT/'data/minecraft/tags/block/mineable/pickaxe.json'
tag=json.loads(tag_path.read_text())
for names in transport_tiers:
 for name in names[1:]:
  key='factorycraft:'+name
  if key not in tag['values']:tag['values'].append(key)
if 'factorycraft:transport_part' not in tag['values']:tag['values'].append('factorycraft:transport_part')
write('data/minecraft/tags/block/mineable/pickaxe.json',tag)

for locale in ['en_us','ru_ru']:
 path=ROOT/f'assets/factorycraft/lang/{locale}.json';strings=json.loads(path.read_text());ru=locale=='ru_ru'
 labels={
 'splitter':('Splitter controls','Настройка разделителя'),
 'splitter_input':('Input: %s','Вход: %s'),
 'splitter_output':('Output: %s','Выход: %s'),
 'splitter_mode.0':('Balanced','Поровну'),
 'splitter_mode.1':('Left','Левый'),
 'splitter_mode.2':('Right','Правый'),
 'splitter_filter':('Filter','Фильтр'),
 'splitter_clear':('Clear','Сброс'),
 'splitter_hint':('Sides follow travel. Filtered exit is strict.','Стороны — по ходу ленты. Фильтр строгий.'),
 'splitter_filter_tip':('Click with a carried item to set its type; right-click to clear. No item is consumed. Components are ignored.','Нажмите предметом на курсоре для выбора типа; ПКМ — очистить. Предмет не расходуется. Компоненты не учитываются.')}
 for key,value in labels.items():strings['gui.factorycraft.'+key]=value[1 if ru else 0]
 write(f'assets/factorycraft/lang/{locale}.json',strings)

# 0.14: higher-tier splitters reuse the original four-lane transport and native viewers.
process('fast_splitter','assembling_machine',[('factorycraft:splitter',1),('factorycraft:iron_gear',10),('factorycraft:electronic_circuit',10)],'fast_splitter',1,40,24)
write('data/factorycraft/recipe/express_splitter.json',{'type':'factorycraft:chemistry','machine':'assembling_machine_2','items':[{'ingredient':ingredient(i),'count':n} for i,n in [('factorycraft:fast_splitter',1),('factorycraft:iron_gear',10),('factorycraft:advanced_circuit',10)]],'fluids':[{'fluid':'factorycraft:lubricant','amount':80}],'results':[{'id':'factorycraft:express_splitter','count':1}],'ticks':40,'energy':40})
for name,parents,packs,x,y in [('logistics_2',['logistics','electronics'],['automation','logistic'],2,4),('logistics_3',['logistics_2','chemical_science'],['automation','logistic','chemical'],3,4)]:
 write(f'data/factorycraft/recipe/{name}.json',{'type':'factorycraft:research','prerequisites':['factorycraft:'+p for p in parents],'packs':{'factorycraft:'+p+'_science_pack':1 for p in packs},'units':40 if name=='logistics_2' else 60,'ticks':60,'unlocks':['factorycraft:'+('fast_splitter' if name=='logistics_2' else 'express_splitter')],'x':x,'y':y})
for name,base,field in [('fast_splitter','splitter','inputs'),('express_splitter','fast_splitter','items')]:
 path=ROOT/f'data/factorycraft/recipe/{name}.json';recipe=json.loads(path.read_text())
 recipe[field][0]['ingredient']={'type':'neoforge:components','items':['factorycraft:'+base],'components':{},'strict':True}
 write(str(path.relative_to(ROOT)),recipe)
for name in ['fast_splitter','express_splitter']:
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:'+name}]}]})
 tag=json.loads((ROOT/'data/minecraft/tags/block/mineable/pickaxe.json').read_text())
 if 'factorycraft:'+name not in tag['values']:tag['values'].append('factorycraft:'+name)
 write('data/minecraft/tags/block/mineable/pickaxe.json',tag)
for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text())
 for name,en,ru in [('fast_splitter','Fast Splitter','Быстрый разделитель'),('express_splitter','Express Splitter','Экспресс-разделитель')]:
  data['block.factorycraft.'+name]=(en,ru)[index];data['recipe.factorycraft.'+name]=(en,ru)[index]
 for name,en,ru in [('logistics_2','Logistics 2','Логистика 2'),('logistics_3','Logistics 3','Логистика 3')]:data['technology.factorycraft.'+name]=(en,ru)[index]
 data['tooltip.factorycraft.splitter_upgrade']=('Upgrade recipe: plain, unused splitter only.','Для рецепта нужен обычный новый разделитель.')[index]
 data['tooltip.factorycraft.splitter_rate']=('Two input belts: %s items/s total. No FE required.','Две входные ленты: суммарно %s предметов/с. Без FE.')[index]
 data['command.factorycraft.tiered_sorting']=('Three finite sorting lines built at %s; outputs: 32 iron + 32 copper per tier.','Три конечные сортировочные линии построены: %s; на каждом уровне 32 железа + 32 меди.')[index]
 write(f'assets/factorycraft/lang/{lang}.json',data)

# 0.21: final six types, original colored bases and shared articulated renderer.
arm_types=[('burner_inserter',(105,76,46),1,76,700,'Burner Inserter','Топливный манипулятор'),('inserter',(211,170,52),1,70,70,'Inserter','Манипулятор'),('long_inserter',(186,64,49),2,48,70,'Long-handed Inserter','Длинный манипулятор'),('fast_inserter',(57,126,194),1,24,98,'Fast Inserter','Быстрый манипулятор'),('bulk_inserter',(72,172,82),1,24,280,'Bulk Inserter','Пакетный манипулятор'),('stack_inserter',(217,225,212),1,24,560,'Stack Inserter','Стопочный манипулятор')]
for name,color,reach,units,fe,en,ru in arm_types:
 texture('block/'+name,color,'machine');tex='factorycraft:block/'+name
 boxes=[([4,0,4],[12,3,12]),([5,3,5],[11,6,11]),([6,6,6],[10,8,10])]
 if name=='burner_inserter':boxes.append(([4,3,9],[12,7,12]))
 if name in ['bulk_inserter','stack_inserter']:boxes.extend([([3,1,5],[5,5,11]),([11,1,5],[13,5,11])])
 write(f'assets/factorycraft/models/block/{name}.json',{'parent':'minecraft:block/block','textures':{'all':tex,'particle':tex},'elements':[{'from':lo,'to':hi,'faces':{f:{'texture':'#all'} for f in ['up','down','north','south','east','west']}} for lo,hi in boxes]})
 write(f'assets/factorycraft/models/item/{name}.json',{'parent':'factorycraft:block/'+name})
 write(f'assets/factorycraft/blockstates/{name}.json',{'variants':{'facing='+d:{'model':'factorycraft:block/'+name,'y':rot} for d,rot in [('north',0),('east',90),('south',180),('west',270)]}})
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[]})
 tag=json.loads((ROOT/'data/minecraft/tags/block/mineable/pickaxe.json').read_text());tag['values'].append('factorycraft:'+name);tag['values']=list(dict.fromkeys(tag['values']));write('data/minecraft/tags/block/mineable/pickaxe.json',tag)
 for locale,index in [('en_us',0),('ru_ru',1)]:
  path=ROOT/f'assets/factorycraft/lang/{locale}.json';strings=json.loads(path.read_text());strings['block.factorycraft.'+name]=(en,ru)[index];strings['recipe.factorycraft.'+name]=(en,ru)[index]
  strings['tooltip.factorycraft.'+name]=(f'Reach {reach}; {units}/3 ticks per nominal cycle; five filters. '+('Solid fuel.' if name=='burner_inserter' else f'{fe} FE per nominal cycle.'),f'Дальность {reach}; {units}/3 тика на цикл; пять фильтров. '+('Твёрдое топливо.' if name=='burner_inserter' else f'{fe} FE на номинальный цикл.'))[index];write(str(path.relative_to(ROOT)),strings)
# Use original recipe counts; strict native components reject resource-owning upgrade inputs.
arm_inputs={
 'burner_inserter':[('factorycraft:iron_plate',1),('factorycraft:iron_gear',1)],
 'inserter':[('factorycraft:iron_plate',1),('factorycraft:iron_gear',1),('factorycraft:electronic_circuit',1)],
 'long_inserter':[('factorycraft:inserter',1),('factorycraft:iron_plate',1),('factorycraft:iron_gear',1)],
 'fast_inserter':[('factorycraft:inserter',1),('factorycraft:iron_plate',2),('factorycraft:electronic_circuit',2)],
 'bulk_inserter':[('factorycraft:fast_inserter',1),('factorycraft:iron_gear',15),('factorycraft:electronic_circuit',15),('factorycraft:advanced_circuit',1)]}
for name,inputs in arm_inputs.items():
 process(name,'assembling_machine',inputs,name,1,10 if name!='bulk_inserter' else 10,12)
 path=ROOT/f'data/factorycraft/recipe/{name}.json';recipe=json.loads(path.read_text())
 for part in recipe['inputs']:
  item=part['ingredient'].get('item','')
  if item in ['factorycraft:inserter','factorycraft:fast_inserter']:part['ingredient']={'type':'neoforge:components','items':[item],'components':{},'strict':True}
 write(str(path.relative_to(ROOT)),recipe)
write('data/factorycraft/recipe/craft_burner_inserter.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:iron_ingot'},{'item':'factorycraft:iron_gear'}],'result':{'id':'factorycraft:burner_inserter','count':1}})
# Standard is needed before the first green-science assembler: handcrafting uses existing components.
write('data/factorycraft/recipe/craft_inserter.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'factorycraft:iron_plate'},{'item':'factorycraft:iron_gear'},{'item':'factorycraft:electronic_circuit'}],'result':{'id':'factorycraft:inserter','count':1}})
write('data/factorycraft/recipe/electric_inserters.json',{'type':'factorycraft:research','prerequisites':['factorycraft:automation'],'packs':{'factorycraft:automation_science_pack':1},'units':30,'ticks':300,'unlocks':['factorycraft:long_inserter','factorycraft:fast_inserter'],'x':2,'y':5})
write('data/factorycraft/recipe/bulk_inserters.json',{'type':'factorycraft:research','prerequisites':['factorycraft:electric_inserters','factorycraft:logistics_2','factorycraft:electronics'],'packs':{'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1},'units':150,'ticks':600,'unlocks':['factorycraft:bulk_inserter'],'x':4,'y':5})
for n,count in enumerate([200,250,250,250,300,400,600],1):
 parents=['factorycraft:bulk_inserters' if n==1 else 'factorycraft:inserter_capacity_bonus_'+str(n-1)]
 if n==3:parents.append('factorycraft:chemical_science')
 packs={'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1}
 if n>=3:packs['factorycraft:chemical_science_pack']=1
 write(f'data/factorycraft/recipe/inserter_capacity_bonus_{n}.json',{'type':'factorycraft:research','prerequisites':parents,'packs':packs,'units':count,'ticks':600,'unlocks':[],'x':5+n,'y':5})
# Space Age access is explicitly sandbox-only until its real materials/science exist; no Stack recipe.
for n,count in [(1,2000),(2,3000)]:
 write(f'data/factorycraft/recipe/transport_belt_capacity_{n}.json',{'type':'factorycraft:research','sandbox_only':True,'prerequisites':['factorycraft:inserter_capacity_bonus_7'] if n==1 else ['factorycraft:transport_belt_capacity_1','factorycraft:inserter_capacity_bonus_7'],'packs':{'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1,'factorycraft:chemical_science_pack':1},'units':count,'ticks':1200,'unlocks':[],'x':12+n,'y':5})
arm_ui={'inserter':('Electric Inserter','Электроманипулятор'),'inserter_enabled.0':('Disabled','Выключен'),'inserter_enabled.1':('Enabled','Включён'),'inserter_mode.0':('Allow type','Принимать'),'inserter_mode.1':('Deny type','Исключать'),'inserter_hand':('Hand','Рука'),'inserter_power':('%s / 2000 FE','%s / 2000 FE')}
for locale,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{locale}.json';strings=json.loads(path.read_text());strings.update({'gui.factorycraft.'+k:v[index] for k,v in arm_ui.items()});strings['technology.factorycraft.electric_inserters']=('Electric inserters','Электрические манипуляторы')[index];write(str(path.relative_to(ROOT)),strings)

# 0.16 additive bounded circuit devices and reusable red/green wiring tools.
circuit_names=[('constant_combinator','Constant Combinator','Постоянный комбинатор'),('arithmetic_combinator','Arithmetic Combinator','Арифметический комбинатор'),('decider_combinator','Decider Combinator','Решающий комбинатор'),('selector_combinator','Selector Combinator','Комбинатор выбора'),('circuit_reader','Inventory Reader','Считыватель инвентаря'),('circuit_controller','Circuit Controller','Логический контроллер')]
for name,en,ru in circuit_names:
 process(name,'assembling_machine',[('factorycraft:electronic_circuit',5 if name in ['arithmetic_combinator','decider_combinator'] else 2),('factorycraft:iron_plate',2),('factorycraft:copper_wire',5)],name,1,40,12)
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:'+name}]}]})
 tag=json.loads((ROOT/'data/minecraft/tags/block/mineable/pickaxe.json').read_text());tag['values'].append('factorycraft:'+name);write('data/minecraft/tags/block/mineable/pickaxe.json',tag)
for name,color in [('red_circuit_wire',(195,52,48)),('green_circuit_wire',(47,184,91))]:
 texture('item/'+name,color,'item');write(f'assets/factorycraft/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/'+name}})
 craft(name,['WD'],{'W':'factorycraft:copper_wire','D':'minecraft:'+('red' if name.startswith('red') else 'green')+'_dye'})
write('data/factorycraft/recipe/circuit_networks.json',{'type':'factorycraft:research','prerequisites':['factorycraft:electronics','factorycraft:logistics'],'packs':{'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1},'units':20,'ticks':60,'unlocks':['factorycraft:'+n for n,en,ru in circuit_names if n not in ['selector_combinator','circuit_reader','circuit_controller']],'x':3,'y':5})
circuit_ui={'circuit_live_input':('Input: %s','Вход: %s'),'circuit_live_output':('Output: %s','Выход: %s'),'circuit_enabled':('Enabled: %s','Включён: %s'),'circuit_a':('Virtual signal A','Виртуальный сигнал A'),'circuit_gate':('In: %s / enabled: %s','Вход: %s / включён: %s'),'circuit_operand':('Scalar','Число'),'circuit_input':('Input','Вход'),'circuit_output':('Output','Выход'),'circuit_values':('In: %s / out: %s','Вход: %s / выход: %s'),'circuit_count.0':('Emit one','Выдавать единицу'),'circuit_count.1':('Emit input count','Выдавать входное значение'),'circuit_signal_hint':('Click with a sample to choose an item signal; right-click or empty cursor restores virtual A. Samples are not consumed.','Нажмите образцом для выбора предметного сигнала; ПКМ или пустой курсор возвращает виртуальный A. Образец не расходуется.')}
circuit_messages={'circuit_node_required':('Select a loaded circuit node or its input/output contact.','Выберите загруженный узел логики или его входной/выходной контакт.'),'circuit_first':('First contact %s selected; click the other end.','Выбран первый контакт %s; нажмите на второй.'),'circuit_cleared':('Connections on this contact/color cleared.','Соединения этого контакта и цвета удалены.'),'circuit_connected':('Circuit link toggled.','Логическое соединение переключено.'),'circuit_rejected':('Cannot connect: check loaded ends, distance, degree or network limit.','Соединение невозможно: проверьте загрузку, расстояние, число проводов и лимит сети.')}
for locale,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{locale}.json';strings=json.loads(path.read_text())
 for name,en,ru in circuit_names:
  strings['block.factorycraft.'+name]=(en,ru)[index];strings['recipe.factorycraft.'+name]=(en,ru)[index];strings['tooltip.factorycraft.'+name]=('Red/green item/virtual signals. Right-click for program. Controller/reader faces its adjacent target.','Предметные и виртуальные сигналы двух цветов. ПКМ: программа. Контроллер/считыватель смотрит на соседнюю цель.')[index]
 strings.update({'gui.factorycraft.'+k:v[index] for k,v in circuit_ui.items()});strings.update({'message.factorycraft.'+k:v[index] for k,v in circuit_messages.items()});strings['technology.factorycraft.circuit_networks']=('Circuit networks','Логические сети')[index]
 strings['item.factorycraft.red_circuit_wire']=('Red Circuit Wire','Красный логический провод')[index];strings['item.factorycraft.green_circuit_wire']=('Green Circuit Wire','Зелёный логический провод')[index];strings['tooltip.factorycraft.circuit_wire']=('Reusable: click two contacts. Sneak-click clears this contact/color. Range 16.','Многоразовый: нажмите два контакта. Shift+ПКМ очищает контакт/цвет. Дальность 16.')[index]
 write(str(path.relative_to(ROOT)),strings)

# 0.17: component-sensitive fluid reader mode, without changing legacy recipe IDs.
fluid_reader_ui={'circuit_reader_mode.0':('Mode: items','Режим: предметы'),'circuit_reader_mode.1':('Mode: fluids (mB)','Режим: жидкости (мБ)'),'circuit_reader_mode.2':('Invalid mode: click to reset','Неверный режим: сбросить'),'circuit_fluid_filter':('Fluid sample','Жидкость'),'circuit_fluid_output':('Output: %s mB','Выход: %s мБ'),'circuit_fluid_hint':('Click with a filled bucket/canister to select its fluid AND components. Sample is not consumed. Right-click or empty cursor counts all fluids. The volume is in mB.','Нажмите заполненным ведром/канистрой: учитываются жидкость И компоненты. Образец не расходуется. ПКМ или пустой курсор: все жидкости. Объём в мБ.')}
for locale,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{locale}.json';strings=json.loads(path.read_text());strings.update({'gui.factorycraft.'+k:v[index] for k,v in fluid_reader_ui.items()});strings['block.factorycraft.circuit_reader']=('Circuit Reader','Считыватель логической сети')[index];strings['recipe.factorycraft.circuit_reader']=strings['block.factorycraft.circuit_reader'];write(str(path.relative_to(ROOT)),strings)

for locale,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{locale}.json';strings=json.loads(path.read_text());strings['gui.factorycraft.circuit_sensor_ready.0']=('Reading unavailable','Показание недоступно')[index];strings['gui.factorycraft.circuit_sensor_ready.1']=('Reading available','Показание доступно')[index];write(str(path.relative_to(ROOT)),strings)
for locale,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{locale}.json';strings=json.loads(path.read_text());strings['gui.factorycraft.circuit_all_fluids']=('All fluids','Все жидкости')[index];write(str(path.relative_to(ROOT)),strings)

# 0.18: appended independent colors; old wire assets and recipes remain unchanged.
for name,color,dye in [('blue_circuit_wire',(64,128,255),'blue'),('white_circuit_wire',(240,240,240),'white')]:
 texture('item/'+name,color,'item');write(f'assets/factorycraft/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/'+name}})
 craft(name,['WD'],{'W':'factorycraft:copper_wire','D':'minecraft:'+dye+'_dye'})
for locale,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{locale}.json';strings=json.loads(path.read_text())
 strings['item.factorycraft.blue_circuit_wire']=('Blue Circuit Wire','Синий логический провод')[index];strings['item.factorycraft.white_circuit_wire']=('White Circuit Wire','Белый логический провод')[index]
 for n in ['constant_combinator','arithmetic_combinator','decider_combinator','circuit_reader','circuit_controller']:
  strings['tooltip.factorycraft.'+n]=('Four independent wire colors. Right-click for program. Controller can gate an adjacent electric arm, pump or splitter.','Четыре независимых цвета проводов. ПКМ: программа. Контроллер управляет соседним электроманипулятором, насосом или разделителем.')[index]
 for value,en,ru in [(0,'Circuit: unbound','Логика: нет привязки'),(1,'Circuit: enabled','Логика: разрешено'),(2,'Circuit: stopped','Логика: остановлено')]:strings['gui.factorycraft.splitter_circuit.'+str(value)]=(en,ru)[index]
 write(str(path.relative_to(ROOT)),strings)

# Schema-4 physical-port labels; channel coordinates come from MachinePorts at runtime.
for locale,ru in [('en_us',False),('ru_ru',True)]:
 p=ROOT/f'assets/factorycraft/lang/{locale}.json';data=json.loads(p.read_text())
 data.update({
  'gui.factorycraft.physical_port':'%s, %s (%s, %s, %s)',
  'gui.factorycraft.port_input':'Вход' if ru else 'Input',
  'gui.factorycraft.port_output':'Выход' if ru else 'Output',
  'gui.factorycraft.port_both':'Вход/выход' if ru else 'Input/output',
  'gui.factorycraft.internal_channel':'Внутренняя ёмкость без внешнего порта' if ru else 'Internal cell without an external port',
  'gui.factorycraft.boiler_ports':'Вода: сбоку ↔; пар: спереди →' if ru else 'Water: sides ↔; steam: front →',
  'gui.factorycraft.engine_ports':'Пар: оба торца ↔' if ru else 'Steam: both ends ↔'
 })
 for side,en,rus in [('north','North','Север'),('east','East','Восток'),('south','South','Юг'),('west','West','Запад'),('up','Up','Верх'),('down','Down','Низ')]:data['gui.factorycraft.side_'+side]=rus if ru else en
 write(f'assets/factorycraft/lang/{locale}.json',data)

# Internal parts have no inventory item and never drop separately.
for name in ['machine_casing','refinery_casing']:
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[]})

for locale,ru in [('en_us',False),('ru_ru',True)]:
 p=ROOT/f'assets/factorycraft/lang/{locale}.json';data=json.loads(p.read_text())
 data['message.factorycraft.ports_mirrored']='Порты отражены слева направо. Shift + ключ: безопасная разборка.' if ru else 'Ports mirrored left/right. Sneak + wrench: safe dismantling.'
 data['tooltip.factorycraft.fluid_nozzles']+=' Ключ: отразить порты.' if ru else ' Wrench: mirror ports.'
 write(f'assets/factorycraft/lang/{locale}.json',data)

# 0.20: explicit normal-quality electric poles; no existing production/fuel/speed recipes changed.
write('data/factorycraft/recipe/craft_electric_pole.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'tag':'minecraft:planks'},{'item':'minecraft:copper_ingot'}],'result':{'id':'factorycraft:electric_pole','count':2}})
write('data/factorycraft/recipe/craft_copper_wire_tool.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:copper_ingot'},{'item':'minecraft:iron_ingot'}],'result':{'id':'factorycraft:copper_wire_tool','count':1}})
for name,plates,steel,wire in [('medium_electric_pole',2,2,2),('big_electric_pole',4,5,4),('substation',0,10,6)]:
 inputs=[{'ingredient':{'item':'factorycraft:steel_plate'},'count':steel},{'ingredient':{'item':'factorycraft:copper_wire'},'count':wire},{'ingredient':{'item':'factorycraft:iron_plate' if plates else 'factorycraft:advanced_circuit'},'count':plates or 5}]
 write(f'data/factorycraft/recipe/{name}.json',{'type':'factorycraft:processing','machine':'assembling_machine','inputs':inputs,'result':{'id':'factorycraft:'+name,'count':1},'ticks':20,'energy':12})
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:'+name}]}]})
write('data/factorycraft/recipe/electric_energy_distribution_1.json',{'type':'factorycraft:research','prerequisites':['factorycraft:steel_processing','factorycraft:automation'],'packs':{'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1},'units':120,'ticks':600,'unlocks':['factorycraft:medium_electric_pole','factorycraft:big_electric_pole'],'x':4,'y':6})
write('data/factorycraft/recipe/electric_energy_distribution_2.json',{'type':'factorycraft:research','prerequisites':['factorycraft:electric_energy_distribution_1','factorycraft:chemical_science'],'packs':{'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1,'factorycraft:chemical_science_pack':1},'units':100,'ticks':900,'unlocks':['factorycraft:substation'],'x':5,'y':6})
texture('item/copper_wire_tool',(184,105,43),'item')
write('assets/factorycraft/models/item/copper_wire_tool.json',{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/copper_wire_tool'}})
for locale,index in [('en_us',0),('ru_ru',1)]:
 p=ROOT/f'assets/factorycraft/lang/{locale}.json';data=json.loads(p.read_text())
 for name,en,ru in [('electric_pole','Small Electric Pole','Малый электрический столб'),('medium_electric_pole','Medium Electric Pole','Средний электрический столб'),('big_electric_pole','Big Electric Pole','Большая электрическая опора'),('substation','Substation','Подстанция')]:
  data['block.factorycraft.'+name]=(en,ru)[index];data['recipe.factorycraft.'+name]=(en,ru)[index];data['tooltip.factorycraft.'+name]=('Saved copper connections; separate square supply area. Copper wire tool: connect/disconnect; sneak: clear.','Сохранённые медные связи и отдельная квадратная зона снабжения. Инструмент: соединить/разорвать; Shift: отключить все.')[index]
 for name,en,ru in [('electric_energy_distribution_1','Electric Energy Distribution 1','Распределение электроэнергии 1'),('electric_energy_distribution_2','Electric Energy Distribution 2','Распределение электроэнергии 2')]:data['technology.factorycraft.'+name]=(en,ru)[index]
 data['item.factorycraft.copper_wire_tool']=('Copper Power Wire Tool','Инструмент медных силовых проводов')[index]
 messages={'selected':('First pole selected','Первый столб выбран'),'connected':('Copper wire connected','Силовой провод подключён'),'removed':('Copper wire removed','Силовой провод снят'),'cleared':('All copper wires removed','Все силовые провода сняты'),'too_far':('Beyond the wire reach of one endpoint','Превышена дальность проводов одного из столбов'),'unavailable':('First pole unavailable or changed','Первый столб недоступен или изменился'),'cancelled':('Wire selection cancelled','Выбор столба отменён')}
 for key,value in messages.items():data['message.factorycraft.power_wire_'+key]=value[index]
 texts={'supply':('Supply: %s × %s','Зона питания: %s × %s'),'wire':('Wire reach: %s blocks','Дальность провода: %s блоков'),'connections':('Wires loaded / saved: %s / %s','Провода: загружено / всего %s / %s'),'network':('Network poles: %s','Столбов в сети: %s'),'sources':('Generators: %s','Генераторов: %s'),'consumers':('Consumers: %s','Потребителей: %s'),'pending':('unavailable / rebuilding','недоступна / перестраивается'),'controls':('Copper tool: link; sneak: clear','Медный инструмент: связь; Shift: сброс')}
 for key,value in texts.items():data['gui.factorycraft.pole_'+key]=value[index]
 data['gui.factorycraft.inspection_energy']=('Stored: %s FE','Запас: %s FE')[index];data['gui.factorycraft.inspection_progress']=('Progress: %s / %s ticks','Прогресс: %s / %s тактов')[index]
 write(f'assets/factorycraft/lang/{locale}.json',data)

write('data/factorycraft/recipe/electric_pole.json',{'type':'factorycraft:processing','machine':'assembling_machine','inputs':[{'ingredient':{'tag':'minecraft:planks'},'count':1},{'ingredient':{'item':'factorycraft:copper_wire'},'count':2}],'result':{'id':'factorycraft:electric_pole','count':2},'ticks':10,'energy':12})
for locale,index in [('en_us',0),('ru_ru',1)]:
 p=ROOT/f'assets/factorycraft/lang/{locale}.json';data=json.loads(p.read_text());data['tooltip.factorycraft.pole_properties']=('Supply %s × %s; wire reach %s blocks','Зона питания %s × %s; дальность провода %s блоков')[index];write(f'assets/factorycraft/lang/{locale}.json',data)
p=ROOT/'data/factorycraft/advancement/recipes/factory_basics.json';data=json.loads(p.read_text());data['rewards']['recipes']=['factorycraft:'+p.stem for p in sorted((ROOT/'data/factorycraft/recipe').glob('craft_*.json'))];write(str(p.relative_to(ROOT)),data)

# Retired Filter Inserter resources/progression must never survive a repeat generation.
for group,ext in [('assets/factorycraft/blockstates','json'),('assets/factorycraft/models/block','json'),('assets/factorycraft/models/item','json'),('assets/factorycraft/textures/block','png'),('data/factorycraft/loot_table/blocks','json'),('data/factorycraft/recipe','json')]:
 (ROOT/group/('filter_inserter.'+ext)).unlink(missing_ok=True)
for locale,index in [('en_us',0),('ru_ru',1)]:
 p=ROOT/f'assets/factorycraft/lang/{locale}.json';data=json.loads(p.read_text())
 for key in list(data):
  if 'filter_inserter' in key:del data[key]
 for n in range(1,8):data[f'technology.factorycraft.inserter_capacity_bonus_{n}']=(f'Inserter Capacity Bonus {n}',f'Вместимость манипуляторов {n}')[index]
 data['technology.factorycraft.bulk_inserters']=('Bulk Inserters','Пакетные манипуляторы')[index]
 for n in [1,2]:data[f'technology.factorycraft.transport_belt_capacity_{n}']=(f'Space Age Belt Capacity {n} (sandbox)',f'Вместимость лент Space Age {n} (стенд)')[index]
 ui={'inserter_auto':('Auto','Авто'),'inserter_capacity':('%s / %s','%s / %s'),'inserter_fuel':('%s fuel units','%s ед. топлива'),'inserter_speed':('%s ticks/cycle; reach %s','%s тика/цикл; дальность %s'),'inserter_mode.0':('Whitelist','Белый список'),'inserter_mode.1':('Blacklist','Чёрный список')}
 states=[('Disabled / circuit stop','Выключен / сигнал'),('Waiting for power / fuel','Нет энергии / топлива'),('Waiting for items','Нет подходящих предметов'),('Collecting full hand','Набор полной руки'),('Moving to output','Поворот к выходу'),('Output blocked / partial','Выход занят / остаток'),('Returning','Возврат'),('Endpoint unavailable','Точка недоступна')]
 ui.update({'inserter_status.'+str(i):pair for i,pair in enumerate(states)})
 data.update({'gui.factorycraft.'+key:value[index] for key,value in ui.items()});write(f'assets/factorycraft/lang/{locale}.json',data)
# Include late-added crafts and exclude retired recipes in the bootstrap recipe book.
p=ROOT/'data/factorycraft/advancement/recipes/factory_basics.json';data=json.loads(p.read_text());data['rewards']['recipes']=['factorycraft:'+p.stem for p in sorted((ROOT/'data/factorycraft/recipe').glob('craft_*.json'))];write(str(p.relative_to(ROOT)),data)

# 0.22: canonical direct circuit contacts replace adjacent reader/controller progression.
for retired in ['circuit_reader','circuit_controller']:
 (ROOT/f'data/factorycraft/recipe/{retired}.json').unlink(missing_ok=True)
for name,circuits in [('constant_combinator',2),('arithmetic_combinator',5),('decider_combinator',5)]:
 process(name,'assembling_machine',[('factorycraft:copper_wire',5),('factorycraft:electronic_circuit',circuits)],name,1,10,12)
process('selector_combinator','assembling_machine',[('factorycraft:advanced_circuit',2),('factorycraft:decider_combinator',5)],'selector_combinator',1,10,12)
write('data/factorycraft/recipe/advanced_combinators.json',{'type':'factorycraft:research','prerequisites':['factorycraft:circuit_networks','factorycraft:electronics','factorycraft:chemical_science'],'packs':{'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1,'factorycraft:chemical_science_pack':1},'units':100,'ticks':600,'unlocks':['factorycraft:selector_combinator'],'x':6,'y':6})
logic_ui={
 'research_circuit':('Next research: circuit','Следующее исследование: схема'),'research_manual':('Next research: manual','Следующее исследование: вручную'),
 'limited':('Signal limit','Лимит сигналов'),
 'title':('Circuit Network','Логическая сеть'), 'tab.0':('Device','Устройство'),'tab.1':('I/O colors','Цвета В/В'),'tab.2':('Program','Программа'),'tab.3':('Signals','Сигналы'),
 'save':('Apply','Применить'),'waiting':('Waiting…','Ожидание…'),'rejected':('Rejected','Отклонено'),'modified':('Modified','Изменено'),'ready':('Input ready','Вход доступен'),'unknown':('Unavailable','Недоступно'),
 'add':('Add row','Добавить строку'),'back':('Back','Назад'),'search':('Search / ID','Поиск / ID'),'use_id':('Use typed ID','Ввести ID'),'publish':('Enabled: %s','Включён: %s'),
 'input':('Input','Вход'),'output':('Output','Выход'),'input_colors':('Read colors','Цвета чтения'),'output_colors':('Write colors','Цвета записи'),
 'mask_hint':('Colors are independent. Reading the same source on two colors adds it twice. ∅ disables this direction.','Цвета независимы. Один источник на двух цветах суммируется дважды. ∅ выключает направление.'),
 'rules.0':('Conditions','Условия'),'rules.1':('Then outputs','Выходы Тогда'),'rules.2':('Else outputs','Выходы Иначе'),'gate_rules':('Enable conditions (AND / OR)','Условия включения (И / ИЛИ)'),
 'hand_signal':('Hand size signal','Сигнал размера руки'),'input_count':('Input count','Входное значение'),'constant':('Constant','Константа'),
 'highest':('Highest first','От большего'),'lowest':('Lowest first','От меньшего'),'game_tick':('Game tick','Тик мира'),'day_tick':('Day tick','Тик суток'),'day_length':('Day length','Длина суток'),'interval':('Interval','Интервал'),
 'quality_later':('Quality and rocket-capacity modes are unavailable until their real subsystems exist.','Качество и вместимость ракет недоступны до реализации соответствующих подсистем.'),
 'page':('Page %s · in %s / out %s','Стр. %s · вход %s / выход %s')}
for field,en,ru in [('Contents','Read contents','Читать содержимое'),('State','Read state / FE','Читать состояние / FE'),('Ingredients','Read ingredients','Читать ингредиенты'),('Results','Read products','Читать продукты'),('Research','Read research','Читать исследование'),('Gate','Enable by condition','Включать по условию'),('Filters','Filters from signals','Фильтры из сигналов'),('Hand','Hand size from signal','Рука по сигналу'),('Recipe','Select recipe','Выбирать рецепт'),('SelectResearch','Select next research','Выбор технологии'),('Pulse','Belt pulse mode','Импульс ленты')]:logic_ui['option.'+field]=(en+': %s',ru+': %s')
for key,en,ru in [('SIGNAL','Signal','Сигнал'),('CONSTANT','Number','Число'),('EACH','Each','Каждый'),('ANYTHING','Anything','Любой'),('EVERYTHING','Everything','Всё')]:logic_ui['mode.'+key]=(en,ru)
for key,en,ru in [('SELECT','Select by index','Выбор по индексу'),('COUNT','Count signals','Число сигналов'),('RANDOM','Random signal','Случайный сигнал'),('STACK_SIZE','Item stack size','Размер стака'),('TIME','Time','Время')]:logic_ui['selector.'+key]=(en,ru)
for key,en,ru in [('ITEM','Item','Предмет'),('FLUID','Fluid','Жидкость'),('VIRTUAL','Virtual','Виртуальный'),('RECIPE','Recipe','Рецепт'),('TECHNOLOGY','Technology','Технология')]:logic_ui['type.'+key]=(en,ru)
for key,label in [('ADD','+'),('SUBTRACT','−'),('MULTIPLY','×'),('DIVIDE','÷'),('REMAINDER','%'),('AND','AND'),('OR','OR'),('XOR','XOR'),('LEFT_SHIFT','<<'),('RIGHT_SHIFT','>>'),('POWER','^')]:logic_ui['operation.'+key]=(label,label)
for c,en,ru in [(0,'Red','Красный'),(1,'Green','Зелёный'),(2,'Blue','Синий'),(3,'White','Белый')]:logic_ui['color.'+str(c)]=(en,ru)
for locale,index in [('en_us',0),('ru_ru',1)]:
 p=ROOT/f'assets/factorycraft/lang/{locale}.json';data=json.loads(p.read_text());data.update({'gui.factorycraft.logic_'+k:v[index] for k,v in logic_ui.items()})
 data['technology.factorycraft.advanced_combinators']=('Advanced Combinators','Продвинутые комбинаторы')[index]
 for name,_,_ in circuit_names:data['tooltip.factorycraft.'+name]=('Four independent colors. Right-click: program. Shift + empty-hand click on devices: direct circuit settings.','Четыре независимых цвета. ПКМ: программа. Shift + пустая рука по устройству: прямая настройка логики.')[index]
 for key,en,ru in [('state','Working state','Состояние работы'),('energy','Stored FE / fuel','Запас FE / топлива'),('progress','Work progress','Прогресс работы'),('game_tick','Game tick','Тик мира'),('day_tick','Day tick','Тик суток'),('day_length','Day length','Длина суток'),('signal_count','Signal count','Количество сигналов'),('hand_size','Hand size','Размер руки'),('research_cost','Research cost','Стоимость исследования'),('research_units','Completed research units','Готовые единицы исследования'),('research_level','Research level','Уровень исследования')]:data['signal.factorycraft.'+key]=(en,ru)[index]
 for char in 'abcdefghijklmnopqrstuvwxyz0123456789':data['signal.factorycraft.signal_'+char]=('Signal '+char.upper(),'Сигнал '+char.upper())[index]
 write(f'assets/factorycraft/lang/{locale}.json',data)

# 0.23 reflected drop selection: own labels; recipe/energy/capacity numbers unchanged.
for locale in ['en_us','ru_ru']:
 p=ROOT/f'assets/factorycraft/lang/{locale}.json'; d=json.loads(p.read_text()); ru=locale=='ru_ru'
 d['gui.factorycraft.inserter_reflected.0']='Отражение: нет' if ru else 'Reflected: no'
 d['gui.factorycraft.inserter_reflected.1']='Отражение: да' if ru else 'Reflected: yes'
 write(f'assets/factorycraft/lang/{locale}.json',d)

# 0.23: original industrial transport surfaces and hardware, no imported assets.
for tier,color in enumerate([(222,172,45),(209,65,49),(52,165,214)],1):
 frames=[]
 for frame in range(32):
  image=[]
  for y in range(32):
   row=[]; phase=(y+frame*3*tier)%32
   for x in range(32):
    c=(31+(x%3)*3,39+(x%3)*3,43+(x%3)*3)
    if phase%8 in (0,1):c=(72,82,86)
    # paired moving chevrons point north, distinct from the stationary metal rails
    if ((phase-abs(x-8))%16==0 and 3<=x<=13) or ((phase-abs(x-24))%16==0 and 19<=x<=29):c=color
    if x in (15,16):c=(14,20,23)
    row.append((*c,255))
   image.append(row)
  frames.extend(image)
 png(f'block/transport/surface_{tier}',frames)
 write(f'assets/factorycraft/textures/block/transport/surface_{tier}.png.mcmeta',{'animation':{'frametime':1,'interpolate':False}})
 for kind,base in [('accent',color),('frame',(65,78,89)),('rail',(155,172,179)),('roller',(95,117,126))]:
  pixels=[]
  for y in range(16):
   row=[]
   for x in range(16):
    c=base
    if kind=='accent' and (x+y)%12<4:c=(30,39,45)
    if kind=='rail':c=tuple(max(0,v-(12 if x%4==0 else 0)) for v in base)
    if kind=='roller' and y%4==0:c=(180,194,199)
    if kind=='frame' and (x in (2,13) and y in (2,13)):c=(184,195,202)
    row.append((*c,255))
   pixels.append(row)
  png(f'block/transport/{kind}_{tier}' if kind=='accent' else f'block/transport/{kind}',pixels)
for p in (ROOT/'assets/factorycraft/models/block').glob('*.json'):
 d=json.loads(p.read_text())
 if d.get('loader')!='factorycraft:belt':continue
 d['textures'].update({'normal':'factorycraft:block/transport/surface_1','fast':'factorycraft:block/transport/surface_2','express':'factorycraft:block/transport/surface_3','frame':'factorycraft:block/transport/frame','rail':'factorycraft:block/transport/rail','accent':'factorycraft:block/transport/accent_1','accent_fast':'factorycraft:block/transport/accent_2','accent_express':'factorycraft:block/transport/accent_3','roller':'factorycraft:block/transport/roller'})
 write(str(p.relative_to(ROOT)),d)
# Shared splitter body uses the same tier-specific surface and hardware palette.
p=ROOT/'assets/factorycraft/models/block/machine_shell.json'; d=json.loads(p.read_text())
for tier,name in enumerate(['splitter','fast_splitter','express_splitter'],1):
 d['textures'][f'belt_{tier}']=f'factorycraft:block/transport/surface_{tier}'
 for entry in next(v['boxes'] for v in d['machines'] if v['profile']==name):
  if entry.get('material')=='belt':entry['material']=f'belt_{tier}'
write(str(p.relative_to(ROOT)),d)

for tier,name in enumerate(['splitter','fast_splitter','express_splitter'],1):
 p=ROOT/f'assets/factorycraft/models/item/{name}.json'; d=json.loads(p.read_text()); d['textures']['belt']=f'factorycraft:block/transport/surface_{tier}'; write(str(p.relative_to(ROOT)),d)

# 0.24 common pipeline status and bounded membership preview.
for locale,index in [('en_us',0),('ru_ru',1)]:
 p=ROOT/f'assets/factorycraft/lang/{locale}.json';d=json.loads(p.read_text())
 values={'pipeline_preview':('Show nearby members (10 s)','Показать соседние части (10 с)'),'pipeline_amount':('Amount: %s mB','Объём: %s мБ'),'pipeline_capacity':('Capacity: %s mB','Вместимость: %s мБ'),'pipeline_members':('Members: %s','Участников: %s'),'pipeline_conflicts':('Blocked boundaries: %s','Конфликтных границ: %s'),'pipeline_extent':('X×Z: %s×%s; height %s','X×Z: %s×%s; высота %s'),'pipeline_status.0':('Rebuilding / unavailable','Пересчёт / недоступно'),'pipeline_status.1':('Pipeline ready','Сегмент готов'),'pipeline_status.2':('Too long: maximum 320×320','Превышен охват 320×320'),'pipeline_status.3':('Conflicting fluid filter','Конфликт фильтра жидкости'),'pipeline_status.4':('Overfull: drain to recover','Переполнение: требуется отбор'),'pipeline_status.5':('Incompatible boundary is blocked','Несовместимая граница закрыта')}
 d.update({'gui.factorycraft.'+k:v[index] for k,v in values.items()});write(f'assets/factorycraft/lang/{locale}.json',d)

# 0.25 source definitions above use Factorio units; generated datapacks store native mB once.
for path in (ROOT/'data/factorycraft/recipe').glob('*.json'):
 data=json.loads(path.read_text())
 if data.get('type')=='factorycraft:chemistry':
  for fluid in data.get('fluids',[])+data.get('fluid_results',[]):fluid['amount']*=10
 elif data.get('type')=='factorycraft:boiling':
  data['input']['amount']*=10;data['result']['amount']*=10
 else:continue
 write(str(path.relative_to(ROOT)),data)

for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text())
 entries={'block.factorycraft.oil_deposit':('Oil Point','Нефтяная скважина'),'message.factorycraft.oil_yield':('Initial %s%%; current %s%%; %s mB/s at normal speed','Начальная %s%%; текущая %s%%; %s мБ/с при обычной скорости'),'gui.factorycraft.recipe_browser':('Select production recipe','Выбор производственного рецепта'),'gui.factorycraft.search':('Search recipes','Поиск рецептов'),'gui.factorycraft.group':('Change category','Сменить категорию'),'gui.factorycraft.recipe_group.0':('All recipes','Все рецепты'),'gui.factorycraft.recipe_group.1':('Factory processing','Промышленная обработка'),'gui.factorycraft.recipe_group.2':('Fluid processing','Жидкостное производство'),'gui.factorycraft.recipe_group.3':('Vanilla smelting','Плавка Minecraft'),'gui.factorycraft.recipe_unlocked':('Research: available','Исследование: доступно'),'gui.factorycraft.recipe_wait':('Waiting for server…','Ожидание сервера…')}
 data.update({k:v[index] for k,v in entries.items()});write(str(path.relative_to(ROOT)),data)

for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text());data['gui.factorycraft.recipe_chosen']=('Selected','Выбрано')[index];write(str(path.relative_to(ROOT)),data)

for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text());data.pop('message.factorycraft.oil_remaining',None)
 data['tooltip.factorycraft.pumpjack']=('Center directly above an oil point. 20 ticks/cycle; 100% yield = 100 mB/s, 300 FE/cycle. Speed/efficiency modules supported.','Центром строго над нефтяной точкой. 20 тактов/цикл; 100% = 100 мБ/с, 300 FE/цикл. Модули скорости и эффективности.')[index]
 data['gui.factorycraft.pumpjack_rate']=('Yield-based cycles; minimum yield keeps producing.','Добыча по отдаче; минимальная отдача не истощается.')[index]
 write(str(path.relative_to(ROOT)),data)

for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text());data['gui.factorycraft.select']=('Choose recipe','Выбрать рецепт')[index];write(str(path.relative_to(ROOT)),data)

# Compact labels fit the standalone recipe browser at the smallest supported GUI width.
for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text());data['gui.factorycraft.recipe_auto']=('Auto','Авто')[index];data['gui.factorycraft.recipe_choose']=('Select','Выбрать')[index];write(str(path.relative_to(ROOT)),data)

for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text())
 for row,pair in enumerate([('Initial yield: %s%%','Начальная отдача: %s%%'),('Current yield: %s%%','Текущая отдача: %s%%'),('Normal speed: %s mB/s','Номинально: %s мБ/с')]):data[f'gui.factorycraft.oil_stat_{row}']=pair[index]
 write(str(path.relative_to(ROOT)),data)

# 0.25.1: precisely the three Factorio furnace families, one common base smelting definition.
for path in (ROOT/'data/factorycraft/recipe').glob('*.json'):
 data=json.loads(path.read_text())
 if data.get('type')=='factorycraft:processing' and data.get('machine')=='electric_furnace':
  data['machine']='smelting';data['energy']=24;write(str(path.relative_to(ROOT)),data)
process('stone_brick','smelting',[('minecraft:cobblestone',2)],'minecraft:stone_bricks',1,64,24)
# Stone is the Factorio entity; Minecraft cobblestone/bricks are explicit material substitutes.
craft('stone_furnace',['CCC','C C'],{'C':'minecraft:cobblestone'})
craft('steel_furnace',['SSS','B B','SSS'],{'S':'factorycraft:steel_plate','B':'minecraft:bricks'})
craft('electric_furnace',['SSS','ABA','BBB'],{'S':'factorycraft:steel_plate','A':'factorycraft:advanced_circuit','B':'minecraft:bricks'})
process('steel_furnace','assembling_machine',[('factorycraft:steel_plate',6),('minecraft:stone_bricks',10)],'steel_furnace',1,60,20)
process('electric_furnace','assembling_machine',[('factorycraft:steel_plate',10),('factorycraft:advanced_circuit',5),('minecraft:stone_bricks',10)],'electric_furnace',1,100,24)
for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text())
 for name,pair in {'stone_furnace':('Stone Furnace','Каменная печь'),'steel_furnace':('Steel Furnace','Стальная печь')}.items():
  data['block.factorycraft.'+name]=pair[index]
  data['tooltip.factorycraft.'+name]=(('Solid fuel; 2×2 footprint; speed ×1.' if name=='stone_furnace' else 'Solid fuel; 2×2 footprint; speed ×2. Same active fuel consumption as stone.') if index==0 else ('Твёрдое топливо; основание 2×2; скорость ×1.' if name=='stone_furnace' else 'Твёрдое топливо; основание 2×2; скорость ×2. Расход топлива за такт как у каменной.'))
 data['tooltip.factorycraft.electric_furnace']=('Electric, speed ×2, 24 FE/active tick, two module slots.' if index==0 else 'Электрическая, скорость ×2, 24 FE/рабочий такт, два слота модулей.')
 data['gui.factorycraft.burner_recipe_info']=('%s t / solid fuel' if index==0 else '%s т / твёрдое топливо')
 write(str(path.relative_to(ROOT)),data)

# Higher furnaces are made in assemblers under real research gates; no early ungated shortcut.
for name in ['steel_furnace','electric_furnace']:
 (ROOT/f'data/factorycraft/recipe/craft_{name}.json').unlink(missing_ok=True)
for name,parents,unlocks,packs,units,x,y in [
 ('advanced_material_processing',['steel_processing','logistics'],['steel_furnace'],['automation_science_pack','logistic_science_pack'],75,0,3),
 ('advanced_material_processing_2',['advanced_material_processing','chemical_science'],['electric_furnace'],['automation_science_pack','logistic_science_pack','chemical_science_pack'],250,0,4)]:
 write(f'data/factorycraft/recipe/{name}.json',{'type':'factorycraft:research','prerequisites':['factorycraft:'+p for p in parents],'packs':{'factorycraft:'+p:1 for p in packs},'units':units,'ticks':600,'repeatable':False,'unlocks':['factorycraft:'+p for p in unlocks],'x':x,'y':y})
for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text())
 for k,pair in {'advanced_material_processing':('Advanced material processing','Продвинутая обработка материалов'),'advanced_material_processing_2':('Advanced material processing 2','Продвинутая обработка материалов 2')}.items():data['technology.factorycraft.'+k]=pair[index]
 write(str(path.relative_to(ROOT)),data)
for name in ['stone_furnace','steel_furnace']:
 write(f'data/factorycraft/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:'+name}]}]})
path=ROOT/'data/minecraft/tags/block/mineable/pickaxe.json';data=json.loads(path.read_text());data['values']=list(dict.fromkeys(data['values']+['factorycraft:stone_furnace','factorycraft:steel_furnace']));write(str(path.relative_to(ROOT)),data)
# Old ID assets are intentionally retired rather than retained as aliases.
for path in ROOT.rglob('*'):
 if path.is_file() and 'industrial_furnace' in path.name:path.unlink()

# Break the old refinery -> late electric furnace -> chemical-science bootstrap loop.
path=ROOT/'data/factorycraft/recipe/craft_oil_refinery.json';data=json.loads(path.read_text());data['key']['F']={'item':'minecraft:stone_bricks'};write(str(path.relative_to(ROOT)),data)
for path in (ROOT/'data/factorycraft/advancement').rglob('*.json'):
 data=json.loads(path.read_text());rewards=data.get('rewards',{})
 if 'recipes' in rewards:
  rewards['recipes']=list(dict.fromkeys(('factorycraft:craft_stone_furnace' if r in ['factorycraft:craft_electric_furnace','factorycraft:craft_steel_furnace'] else r) for r in rewards['recipes']));write(str(path.relative_to(ROOT)),data)

for lang,index in [('en_us',0),('ru_ru',1)]:
 path=ROOT/f'assets/factorycraft/lang/{lang}.json';data=json.loads(path.read_text());data['block.factorycraft.smelting']=('Smelting: stone / steel / electric','Плавка: каменная / стальная / электрическая')[index];data['category.factorycraft.smelting']=('Smelting','Плавка')[index];data['emi.category.factorycraft.smelting']=('Smelting','Плавка')[index];write(str(path.relative_to(ROOT)),data)
