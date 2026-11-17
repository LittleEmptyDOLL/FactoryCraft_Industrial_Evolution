"""FactoryCraft 0.29: numeric Wube 2.1.24 definitions and original authored assets."""
import json, math, random
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources'
CURATED=set(json.loads((ROOT/'tools/item_artwork.json').read_text()))
REF=json.loads((ROOT/'tools/nuclear_reference.json').read_text())
def write(path,value):
 p=RES/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def native(n):return {'iron-gear-wheel':'factorycraft:iron_gear','stone-brick':'minecraft:stone_bricks','iron-ore':'minecraft:raw_iron','pipe':'factorycraft:fluid_pipe','water':'minecraft:water'}.get(n,'factorycraft:'+n.replace('-','_'))
def tid(n):return {'logistic-science-pack':'logistic_science','chemical-science-pack':'chemical_science','production-science-pack':'production_science','electric-energy-distribution-1':'electric_energy_distribution_1','battery':'battery_research','concrete':'concrete_research','uranium-processing':'uranium_processing_research'}.get(n,n.replace('-','_'))
for name,r in REF['recipes'].items():
 key=name.replace('-','_');inputs=[];fluids=[]
 for i in r['ingredients']:
  if i['kind']=='fluid':fluids.append({'fluid':native(i['name']),'amount':10*i['count']});continue
  ingredient={'item':native(i['name'])}
  if i['name']=='pipe':ingredient={'type':'neoforge:components','items':[native(i['name'])],'components':{},'strict':True}
  inputs.append({'ingredient':ingredient,'count':i['count']})
 result={'id':native(r['result']),'count':r['count']}
 if fluids:v={'type':'factorycraft:chemistry','machine':'assembling_machine_2','items':inputs,'fluids':fluids,'results':[result],'ticks':round(r['seconds']*20),'energy':20}
 else:v={'type':'factorycraft:processing','machine':'assembling_machine','inputs':inputs,'result':result,'ticks':round(r['seconds']*20),'energy':10,'categories':['nuclear' if key=='uranium_fuel_cell' else 'nuclear_construction']}
 write(Path('data/factorycraft/recipe')/(key+'.json'),v)
 # Minecraft's nine-cell grid cannot express the full 2000-item reactor bill. No cheap substitute.
 if not fluids and sum(math.ceil(i['count']/64) for i in inputs)<=9:
  write(Path('data/factorycraft/recipe')/('craft_'+key+'.json'),{'type':'factorycraft:counted_crafting','inputs':inputs,'result':result})
for index,(name,t) in enumerate(REF['technologies'].items()):
 unlocks=[native(n) for n in t['unlocks'] if n in REF['recipes'] or n=='uranium-processing']
 if name=='uranium-mining':unlocks=['factorycraft:uranium_ore']
 v={'type':'factorycraft:research','prerequisites':['factorycraft:'+tid(n) for n in t['prerequisites']],'packs':{native(n):c for n,c in t['packs'].items()},'units':t['units'],'ticks':max(1,t['seconds']*20),'unlocks':unlocks,'x':32+index*2,'y':5}
 if 'trigger' in t:v['trigger']=t['trigger']
 write(Path('data/factorycraft/recipe')/(tid(name)+'.json'),v)
write(Path('data/factorycraft/recipe/uranium_processing.json'),{'type':'factorycraft:uranium_processing','ore':10,'ticks':240,'energy_numerator':140,'energy_denominator':3})
p=RES/'data/factorycraft/tags/item/productivity_outputs.json';v=json.loads(p.read_text());v['values']=list(dict.fromkeys(v['values']+['factorycraft:uranium_fuel_cell','factorycraft:uranium_235','factorycraft:uranium_238']));write(p.relative_to(RES),v)
layouts=json.loads((RES/'factorycraft/machine_layouts.json').read_text())
for name,c in {'solar_panel':[1,0,0],'accumulator':[1,0,0],'centrifuge':[1,0,0],'nuclear_reactor':[2,0,0],'heat_pipe':[0,0,0],'heat_exchanger':[1,0,0],'steam_turbine':[1,0,0]}.items():layouts['machines'][name]={'controller':c,'ports':[]}
def port(name,at,face,mode,inp=-1,out=-1):return {'name':name,'at':at,'face':face,'mode':mode,'input':inp,'output':out}
layouts['machines'].setdefault('electric_drill',{'controller':[1,0,0]})['ports']=[port('acid_left',[0,0,1],'west','BOTH',0,0),port('acid_right',[2,0,1],'east','BOTH',0,0),port('acid_rear',[1,0,2],'south','BOTH',0,0)]
layouts['machines']['heat_exchanger']['ports']=[port('water_left',[0,0,1],'west','BOTH',0,0),port('water_right',[2,0,1],'east','BOTH',0,0),port('hot_steam',[1,0,0],'north','OUTPUT',-1,1)]
layouts['machines']['steam_turbine']['ports']=[port('steam_front',[1,0,0],'north','BOTH',0,0),port('steam_rear',[1,0,4],'south','BOTH',0,0)]
write(Path('factorycraft/machine_layouts.json'),layouts)
world=json.loads((RES/'data/factorycraft/worldgen/configured_feature/iron_ore.json').read_text());world['config']['state']['Name']='factorycraft:uranium_ore';world['config']['salt']=351923;world['config']['vein'].update(min_radius=6,max_radius=12,min_stock=500,max_stock=3000,spacing_chunks=24)
write(Path('data/factorycraft/worldgen/configured_feature/uranium_ore.json'),world)
write(Path('data/factorycraft/worldgen/placed_feature/uranium_ore.json'),{'feature':'factorycraft:uranium_ore','placement':[{'type':'minecraft:biome'}]})
write(Path('data/factorycraft/neoforge/biome_modifier/uranium_ore.json'),{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':['factorycraft:uranium_ore'],'step':'underground_ores'})
from PIL import Image,ImageDraw
for name,color in {'uranium_ore':(77,110,57),'concrete':(145,149,143),'uranium_235':(119,222,53),'uranium_238':(66,116,45),'uranium_fuel_cell':(116,166,82),'depleted_uranium_fuel_cell':(97,105,81)}.items():
 rng=random.Random('nuclear-original-'+name);im=Image.new('RGBA',(32,32));pix=im.load()
 for y in range(32):
  for x in range(32):
   noise=rng.randrange(-18,19);base=color
   if name=='uranium_ore' and (x//4+y//5)%3:base=(91,94,89)
   pix[x,y]=tuple(max(0,min(255,c+noise)) for c in base)+(255,)
 if name not in ['uranium_ore','concrete']:
  mask=Image.new('L',(32,32));d=ImageDraw.Draw(mask);d.rounded_rectangle((7,3,24,28),radius=4,fill=255);im.putalpha(mask)
 texture=('block' if name in ['uranium_ore','concrete'] else 'item')+'/'+name
 dest=RES/'assets/factorycraft/textures'/(texture+'.png');dest.parent.mkdir(parents=True,exist_ok=True)
 if texture in CURATED:
  if not dest.exists():raise FileNotFoundError('Missing curated artwork: '+texture)
 else:im.save(dest)
 if name in ['uranium_ore','concrete']:
  write(Path('assets/factorycraft/blockstates')/(name+'.json'),{'variants':{'':{'model':'factorycraft:block/'+name}}})
  write(Path('assets/factorycraft/models/block')/(name+'.json'),{'parent':'minecraft:block/cube_all','textures':{'all':'factorycraft:block/'+name}})
  if name=='concrete':write(Path('assets/factorycraft/models/item/concrete.json'),{'parent':'factorycraft:block/concrete'})
 else:write(Path('assets/factorycraft/models/item')/(name+'.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/'+name}})
write(Path('assets/factorycraft/models/item/hot_steam_bucket.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/bucket','layer1':'factorycraft:item/steam_bucket'}})
write(Path('assets/factorycraft/models/item/uranium_ore.json'),{'parent':'factorycraft:block/uranium_ore'})
# Steam uses the original FactoryCraft steam texture; no source/world block is created.
for n in ['solar_panel','accumulator','centrifuge','nuclear_reactor','heat_pipe','heat_exchanger','steam_turbine','uranium_ore']:
 write(Path('data/factorycraft/loot_table/blocks')/(n+'.json'),{'type':'minecraft:block','pools':[] if n=='uranium_ore' else [{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:'+n}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(Path('data/factorycraft/loot_table/blocks/concrete.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:concrete'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for tag in ['mineable/pickaxe','needs_stone_tool']:
 p=RES/'data/minecraft/tags/block'/(tag+'.json');v=json.loads(p.read_text()) if p.exists() else {'replace':False,'values':[]};v['values']=list(dict.fromkeys(v['values']+['factorycraft:'+n for n in ['solar_panel','accumulator','centrifuge','nuclear_reactor','heat_pipe','heat_exchanger','steam_turbine','uranium_ore','concrete']]));write(p.relative_to(RES),v)
labels={'solar_panel':('Solar panel','Солнечная панель'),'accumulator':('Accumulator','Аккумулятор'),'centrifuge':('Centrifuge','Центрифуга'),'nuclear_reactor':('Nuclear reactor','Ядерный реактор'),'heat_pipe':('Heat pipe','Тепловая труба'),'heat_exchanger':('Heat exchanger','Теплообменник'),'steam_turbine':('Steam turbine','Паровая турбина'),'uranium_ore':('Uranium ore','Урановая руда'),'uranium_235':('Uranium-235','Уран-235'),'uranium_238':('Uranium-238','Уран-238'),'uranium_fuel_cell':('Uranium fuel cell','Урановый топливный элемент'),'depleted_uranium_fuel_cell':('Used-up uranium fuel cell','Отработанный урановый топливный элемент'),'concrete':('Concrete','Бетон'),'hot_steam_bucket':('500°C steam bucket','Ведро пара 500 °C')}
techlabels={'solar_energy':('Solar energy','Солнечная энергия'),'electric_energy_accumulators':('Electric energy accumulators','Электрические аккумуляторы'),'concrete_research':('Concrete','Бетон'),'uranium_mining':('Uranium mining','Добыча урана'),'uranium_processing_research':('Uranium processing','Переработка урана'),'nuclear_power':('Nuclear power','Атомная энергия'),'kovarex_enrichment_process':('Kovarex enrichment process','Обогащение по Коварексу'),'nuclear_fuel_reprocessing':('Nuclear fuel reprocessing','Переработка ядерного топлива')}
extra={'gui.factorycraft.nuclear_stored':('Stored: %s J','Запас: %s Дж'),'gui.factorycraft.nuclear_capacity':('Capacity: %s J','Ёмкость: %s Дж'),'gui.factorycraft.nuclear_generation':('Generation: %s W','Генерация: %s Вт'),'gui.factorycraft.nuclear_peak':('Peak: %s W','Пик: %s Вт'),'emi.category.factorycraft.centrifuge':('Centrifuge','Центрифуга'),'gui.factorycraft.uranium_recipe':('%s ore / %s s; U-235 0.7%%, U-238 99.3%%','%s руды / %s с; U-235 0,7%%, U-238 99,3%%'),'gui.factorycraft.solar_output':('Generation: %s W / %s W peak','Генерация: %s Вт / пик %s Вт'),'gui.factorycraft.nuclear_energy':('Stored: %s J / %s J','Запас: %s Дж / %s Дж'),'gui.factorycraft.nuclear_temperature':('Temperature: %s °C','Температура: %s °C'),'gui.factorycraft.nuclear_fuel':('Fuel: %s ticks; active neighbors: %s','Топливо: %s т.; активных соседей: %s'),'gui.factorycraft.nuclear_fluids':('Water: %s mB; steam: %s mB','Вода: %s мБ; пар: %s мБ'),'gui.factorycraft.return_construction':('Return materials','Вернуть сырьё'),'tooltip.factorycraft.uranium_acid':('Requires researched electric drill and sulfuric acid','Требует исследованный электрический бур и серную кислоту'),'tooltip.factorycraft.solar_panel':('60 kW peak; sunlight and open sky required. Supplies loads before charging.','Пик 60 кВт; требуется солнечный свет и открытое небо. Сначала питает потребителей.'),'tooltip.factorycraft.accumulator':('5 MJ; 300 kW input/output; surplus charging and tertiary discharge.','5 МДж; приём/выдача 300 кВт; заряд от избытка, выдача при дефиците.'),'tooltip.factorycraft.centrifuge':('Speed 1; 350 kW; two modules; 10 ore per categorical isotope roll.','Скорость 1; 350 кВт; два модуля; 10 руды на один вероятностный изотоп.'),'tooltip.factorycraft.nuclear_reactor':('40 MW +100% per active cardinal neighbor; each fuel cell burns for 200 s.','40 МВт +100% за активного соседа по стороне; элемент горит 200 с.'),'tooltip.factorycraft.heat_pipe':('1 MJ/°C; bounded heat transfer; 1000°C maximum.','1 МДж/°C; ограниченная передача тепла; максимум 1000 °C.'),'tooltip.factorycraft.heat_exchanger':('10 MW; consumes real water and heat above 500°C to produce hot steam.','10 МВт; расходует воду и тепло выше 500 °C на горячий пар.'),'tooltip.factorycraft.steam_turbine':('5.82 MW at 500°C; 165°C steam yields only its actual enthalpy.','5,82 МВт при 500 °C; пар 165 °C отдаёт только собственную энтальпию.'),'fluid.factorycraft.hot_steam':('Steam (500°C)','Пар (500 °C)'),'gui.factorycraft.uranium_chance':('U-235 0.7% / U-238 99.3%; mutually exclusive','U-235 0,7% / U-238 99,3%; один изотоп')}
for lang,ru in [('en_us',False),('ru_ru',True)]:
 p=RES/'assets/factorycraft/lang'/(lang+'.json');v=json.loads(p.read_text());idx=int(ru)
 for n,l in labels.items():
  v['block.factorycraft.'+n]=l[idx];v['item.factorycraft.'+n]=l[idx]
 for n,l in techlabels.items():v['technology.factorycraft.'+n]=l[idx]
 for n,l in extra.items():v[n]=l[idx]
 write(p.relative_to(RES),v)
print('Generated nuclear source bills, technology gates, physical ports and original resources.')

# Reuse the existing cell baker, adding only the new devices' original geometry.
from machine_geometry import generate
def png(name,rows):
 image=Image.new('RGBA',(len(rows[0]),len(rows)));image.putdata([p for row in rows for p in row]);dest=RES/'assets/factorycraft/textures'/(name+'.png');dest.parent.mkdir(parents=True,exist_ok=True);
 if not dest.exists() or Image.open(dest).tobytes()!=image.tobytes():image.save(dest)
generate(RES,lambda p,v:write(Path(p),v),png)
