"""Original FactoryCraft assets and numeric-only Wube 2.1.24 military definitions."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
REF=json.loads((ROOT/'tools/combat_reference.json').read_text())
def write(path,data):
 p=RES/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def native(name):
 return {'iron-gear-wheel':'factorycraft:iron_gear','stone-brick':'minecraft:stone_bricks','coal':'minecraft:coal','wood':'minecraft:oak_planks','pipe':'factorycraft:fluid_pipe','water':'minecraft:water'}.get(name,'factorycraft:'+name.replace('-','_'))
def tech(name):
 return {'automation-science-pack':'automation_science','logistic-science-pack':'logistic_science','chemical-science-pack':'chemical_science','military-science-pack':'military_science','battery':'battery_research'}.get(name,name.replace('-','_'))
for name,r in REF['recipes'].items():
 key=name.replace('-','_');inputs=[];fluids=[]
 for i in r['ingredients']:
  if i['kind']=='fluid':fluids.append({'fluid':native(i['name']),'amount':i['count']*10});continue
  ingredient={'tag':'minecraft:planks'} if i['name']=='wood' else {'item':native(i['name'])}
  if i['name'] in ['firearm-magazine','shotgun-shell','rocket','stone-wall','pipe']:
   ingredient={'type':'neoforge:components','items':[native(i['name'])],'components':{},'strict':True}
  inputs.append({'ingredient':ingredient,'count':i['count']})
 result={'id':native(r['result']),'count':r['count']}
 if fluids:
  value={'type':'factorycraft:chemistry','machine':'chemical_plant','items':inputs,'fluids':fluids,'results':[result],'ticks':round(r['seconds']*20),'energy':28}
 else:
  value={'type':'factorycraft:processing','machine':'assembling_machine','inputs':inputs,'result':result,'ticks':round(r['seconds']*20),'energy':10,'categories':['military']}
 write(Path('data/factorycraft/recipe')/(key+'.json'),value)
for i,(name,t) in enumerate(REF['technologies'].items()):
 unlocks=[native(n) for n in t['unlocks'] if n in REF['recipes'] or n=='battery']
 value={'type':'factorycraft:research','prerequisites':['factorycraft:'+tech(n) for n in t['prerequisites']],'packs':{native(k):v for k,v in t['packs'].items()},'units':t['units'],'ticks':t['seconds']*20,'unlocks':unlocks,'x':18+i%8,'y':i//8}
 # Keep recipe and research IDs separate where names coincide.
 tid=tech(name)+('_research' if name in REF['recipes'] and name!='military-science-pack' else '')
 write(Path('data/factorycraft/recipe')/(tid+'.json'),value)
# All dependencies point to the separate native research definitions.
renames={tech(n):tech(n)+'_research' for n in REF['technologies'] if n in REF['recipes'] and n!='military-science-pack'}
for name in REF['technologies']:
 tid=tech(name)+('_research' if name in REF['recipes'] and name!='military-science-pack' else '')
 path=Path('data/factorycraft/recipe')/(tid+'.json');v=json.loads((RES/path).read_text());v['prerequisites']=['factorycraft:'+renames.get(p.split(':')[1],p.split(':')[1]) for p in v['prerequisites']];write(path,v)
profiles={
 'pistol':('bullet','bullet',15,15,1,1,0,0),
 'submachine_gun':('bullet','bullet',6,18,1,1,0,0),
 'shotgun':('shotgun-shell','shotgun-shell',40,15,1,12,.3,0),
 'combat_shotgun':('shotgun-shell','shotgun-shell',30,15,1.5,12,.2,0),
 'rocket_launcher':('rocket','rocket',60,36,1,1,0,0),
 'flamethrower':('fire','fire',1,15,2,1,.2,0),
 'gun_turret':('bullet','bullet',6,18,1,1,0,0),
 'laser_turret':('laser','',40,24,20,1,0,800000),
 'flamethrower_turret':('fire','',4,30,3,1,.05,0)}
for name,p in profiles.items():write(Path('data/factorycraft/recipe/combat')/(name+'.json'),dict(zip(['category','ammunition','cooldown','range','damage','pellets','spread','energy'],p),type='factorycraft:combat'))
for name,tags in {'physical':['bypasses_cooldown'],'explosion':['bypasses_cooldown','is_explosion'],'fire':['bypasses_cooldown','is_fire'],'laser':['bypasses_cooldown']}.items():
 write(Path('data/factorycraft/damage_type')/(name+'.json'),{'message_id':'factorycraft.'+name,'scaling':'never','exhaustion':0.0})
for tag in ['bypasses_cooldown','is_explosion','is_fire']:
 values=['factorycraft:'+n for n,tt in {'physical':['bypasses_cooldown'],'explosion':['bypasses_cooldown','is_explosion'],'fire':['bypasses_cooldown','is_fire'],'laser':['bypasses_cooldown']}.items() if tag in tt]
 write(Path('data/minecraft/tags/damage_type')/(tag+'.json'),{'replace':False,'values':values})
# Existing science identity gains genuine production, not an alternate placeholder item.
p=RES/'data/factorycraft/tags/item/productivity_outputs.json';v=json.loads(p.read_text());v['values']=list(dict.fromkeys(v['values']+['factorycraft:military_science_pack','factorycraft:explosives']));write(p.relative_to(RES),v)
layouts=json.loads((RES/'factorycraft/machine_layouts.json').read_text())
for name in ['gun_turret','laser_turret']:layouts['machines'][name]={'controller':[1,0,0],'ports':[]}
layouts['machines']['flamethrower_turret']={'controller':[1,0,0],'ports':[{'name':'oil_left','at':[0,0,2],'face':'west','mode':'BOTH','input':0,'output':0},{'name':'oil_right','at':[1,0,2],'face':'east','mode':'BOTH','input':0,'output':0}]}
write(Path('factorycraft/machine_layouts.json'),layouts)
print('Generated source-audited military recipes, finite technologies, combat profiles and physical ports.')
# Counted handcrafts share exactly the machine recipe's research lock and full-magazine checks.
for name,r in REF['recipes'].items():
 if any(i['kind']=='fluid' for i in r['ingredients']):continue
 key=name.replace('-','_');production=json.loads((RES/'data/factorycraft/recipe'/f'{key}.json').read_text())
 write(Path('data/factorycraft/recipe')/f'craft_{key}.json',{'type':'factorycraft:counted_crafting','inputs':production['inputs'],'result':production['result']})
# Original solid industrial textures and true three-dimensional inventory/held meshes.
from PIL import Image,ImageDraw
for name,color in {'combat_steel':(92,110,119),'combat_dark':(24,32,37),'combat_brass':(192,151,56),'combat_red':(147,48,35),'combat_wall':(94,103,105),'combat_wood':(100,67,39)}.items():
 im=Image.new('RGBA',(32,32),color+(255,));d=ImageDraw.Draw(im)
 for y in range(0,32,8):
  d.line((0,y,31,y),fill=tuple(int(c*.55) for c in color)+(255,))
  for x in range((y//8%2)*8,32,16):d.line((x,y,x,y+7),fill=tuple(int(c*.7) for c in color)+(255,))
 dest=RES/'assets/factorycraft/textures/block'/f'{name}.png';dest.parent.mkdir(parents=True,exist_ok=True);im.save(dest)
tex={n:'factorycraft:block/combat_'+n for n in ['steel','dark','brass','red','wall','wood']};tex['particle']=tex['steel']
def mesh(boxes,display=None):
 v={'parent':'minecraft:block/block','textures':tex,'elements':[{'from':a,'to':b,'faces':{f:{'texture':'#'+mat,'uv':[0,0,16,16]} for f in ['north','south','east','west','up','down']}} for a,b,mat in boxes]}
 if display:v['display']=display
 return v
for name in ['pistol','submachine_gun','shotgun','combat_shotgun','rocket_launcher','flamethrower']:
 long=name!='pistol';barrel=2 if name=='pistol' else -2
 boxes=[([6,7,5],[10,11,13],'steel'),([6.5,1,10],[9.5,8,13],'dark'),([7,8,barrel],[9,10,6],'dark'),([6.5,7,barrel],[9.5,8,barrel+1],'brass')]
 if long:boxes += [([6.5,7,13],[9.5,10,18],'wood'),([7,3,6],[9,7,9],'brass')]
 if name in ['shotgun','combat_shotgun']:boxes += [([6,6,0],[10,8,5],'wood')]
 if name=='rocket_launcher':boxes=[([4,7,-3],[12,15,18],'steel'),([5,8,-3.05],[11,14,-2.9],'dark'),([7,1,9],[10,8,12],'dark'),([4,11,4],[5,15,9],'brass')]
 if name=='flamethrower':boxes += [([4,4,9],[6,12,14],'red'),([10,4,9],[12,12,14],'brass')]
 display={'firstperson_righthand':{'rotation':[0,165,0],'translation':[2,2,-3],'scale':[.8,.8,.8]},'firstperson_lefthand':{'rotation':[0,195,0],'translation':[2,2,-3],'scale':[.8,.8,.8]},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,1],'scale':[.75,.75,.75]},'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,2,1],'scale':[.75,.75,.75]},'gui':{'rotation':[25,135,0],'scale':[.7,.7,.7]},'ground':{'scale':[.45,.45,.45]}}
 write(Path('assets/factorycraft/models/item')/f'{name}.json',mesh(boxes,display))
for name in ['firearm_magazine','piercing_rounds_magazine','shotgun_shell','piercing_shotgun_shell','flamethrower_ammo','rocket','explosive_rocket','grenade','explosives','light_armor','heavy_armor']:
 if 'rocket' in name:boxes=[([6,0,6],[10,13,10],'steel'),([7,13,7],[9,16,9],'red' if name.startswith('explosive') else 'brass')]
 elif name=='grenade':boxes=[([4,2,4],[12,11,12],'dark'),([6,11,6],[10,14,10],'brass'),([10,11,7],[13,15,9],'steel')]
 elif 'armor' in name:boxes=[([3,3,6],[13,13,10],'steel'),([1,10,6],[3,14,10],'dark'),([13,10,6],[15,14,10],'dark'),([5,5,5],[11,11,6],'brass' if name=='heavy_armor' else 'dark')]
 else:boxes=[([4,1,5],[12,12,11],'red' if name.startswith('piercing') else 'dark')]+[([x,12,6],[x+1.5,15,10],'brass') for x in [4.5,7,9.5]]
 write(Path('assets/factorycraft/models/item')/f'{name}.json',mesh(boxes,{'gui':{'rotation':[20,135,0],'scale':[.9,.9,.9]}}))
# Walls use vanilla's joining/collision machinery, with FactoryCraft artwork.
for model,parent in [('post','template_wall_post'),('side','template_wall_side'),('side_tall','template_wall_side_tall'),('inventory','wall_inventory')]:write(Path('assets/factorycraft/models/block')/f'stone_wall_{model}.json',{'parent':'minecraft:block/'+parent,'textures':{'wall':tex['wall']}})
parts=[{'when':{'up':'true'},'apply':{'model':'factorycraft:block/stone_wall_post'}}]
for side,rot in [('north',0),('east',90),('south',180),('west',270)]:
 for shape,model in [('low','side'),('tall','side_tall')]:parts.append({'when':{side:shape},'apply':{'model':'factorycraft:block/stone_wall_'+model,'y':rot,'uvlock':True}})
write(Path('assets/factorycraft/blockstates/stone_wall.json'),{'multipart':parts});write(Path('assets/factorycraft/models/item/stone_wall.json'),{'parent':'factorycraft:block/stone_wall_inventory'})
for suffix in ['','_open','_wall','_wall_open']:write(Path('assets/factorycraft/models/block')/f'gate{suffix}.json',{'parent':'minecraft:block/template_fence_gate'+suffix,'textures':{'texture':tex['steel']}})
variants={}
for facing,rot in [('south',0),('west',90),('north',180),('east',270)]:
 for opened in [False,True]:
  for wall in [False,True]:variants[f'facing={facing},in_wall={str(wall).lower()},open={str(opened).lower()}']={'model':'factorycraft:block/gate'+('_wall' if wall else '')+('_open' if opened else ''),'y':rot,'uvlock':True}
write(Path('assets/factorycraft/blockstates/gate.json'),{'variants':variants});write(Path('assets/factorycraft/models/item/gate.json'),{'parent':'factorycraft:block/gate'})
write(Path('assets/factorycraft/models/block/land_mine.json'),mesh([([2,0,2],[14,2,14],'dark'),([5,2,5],[11,3,11],'red')]))
write(Path('assets/factorycraft/blockstates/land_mine.json'),{'variants':{'':{'model':'factorycraft:block/land_mine'}}});write(Path('assets/factorycraft/models/item/land_mine.json'),{'parent':'factorycraft:block/land_mine'})
for name in ['stone_wall','gate','land_mine','gun_turret','laser_turret','flamethrower_turret']:write(Path('data/factorycraft/loot_table/blocks')/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'factorycraft:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
im=Image.new('RGBA',(64,32));d=ImageDraw.Draw(im)
for box in [(20,16,27,27),(0,16,7,27),(40,16,47,27)]:d.rectangle(box,fill=(85,111,123,255));d.rectangle(box,outline=(180,146,64,255))
p=RES/'assets/factorycraft/textures/models/armor/industrial_layer_1.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
for locale in ['en_us','ru_ru']:
 path=Path('assets/factorycraft/lang')/(locale+'.json');lang=json.loads((RES/path).read_text());ru=locale=='ru_ru'
 names={'pistol':('Pistol','Пистолет'),'submachine_gun':('Submachine gun','Пистолет-пулемёт'),'shotgun':('Shotgun','Дробовик'),'combat_shotgun':('Combat shotgun','Боевой дробовик'),'rocket_launcher':('Rocket launcher','Ракетница'),'flamethrower':('Flamethrower','Огнемёт'),'firearm_magazine':('Firearm magazine','Магазин с патронами'),'piercing_rounds_magazine':('Piercing rounds magazine','Бронебойные патроны'),'shotgun_shell':('Shotgun shells','Патроны для дробовика'),'piercing_shotgun_shell':('Piercing shotgun shells','Бронебойные патроны для дробовика'),'rocket':('Rocket','Ракета'),'explosive_rocket':('Explosive rocket','Разрывная ракета'),'flamethrower_ammo':('Flamethrower ammunition','Огнемётная смесь'),'grenade':('Grenade','Граната'),'explosives':('Explosives','Взрывчатка'),'light_armor':('Light armor','Лёгкая броня'),'heavy_armor':('Heavy armor','Тяжёлая броня'),'stone_wall':('Stone wall','Каменная стена'),'gate':('Gate','Ворота'),'land_mine':('Land mine','Наземная мина'),'gun_turret':('Gun turret','Пулемётная турель'),'laser_turret':('Laser turret','Лазерная турель'),'flamethrower_turret':('Flamethrower turret','Огнемётная турель')}
 for name,pair in names.items():lang[('block' if name in ['stone_wall','gate','land_mine','gun_turret','laser_turret','flamethrower_turret'] else 'item')+'.factorycraft.'+name]=pair[1 if ru else 0]
 lang.update({'tooltip.factorycraft.rounds':'Патроны: %s / %s' if ru else 'Rounds: %s / %s','tooltip.factorycraft.loaded':'В оружии: %s патронов' if ru else 'Loaded: %s rounds','tooltip.factorycraft.unload':'Shift + ПКМ: извлечь магазин' if ru else 'Sneak + use: unload magazine','gui.factorycraft.turret_ammo':'Боезапас / текущий магазин' if ru else 'Supply / loaded magazine'})
 lang.update({'gui.factorycraft.turret_laser':'Электрическое питание' if ru else 'Electric supply','gui.factorycraft.turret_oil':'Нефтяное питание' if ru else 'Oil supply','gui.factorycraft.turret_pipe':'Трубный буфер: %s / 1000 мБ' if ru else 'Pipe buffer: %s / 1000 mB','gui.factorycraft.turret_fuel':'Топливо: %s / 1000 мБ' if ru else 'Fuel: %s / 1000 mB'})
 for name in REF['technologies']:
  tid=tech(name)+('_research' if name in REF['recipes'] and name!='military-science-pack' else '')
  translated={'military':'Военное дело','military-2':'Военное дело 2','military-3':'Военное дело 3','military-4':'Военное дело 4','heavy-armor':'Тяжёлая броня','gun-turret':'Пулемётные турели','stone-wall':'Каменные стены','gate':'Ворота','military-science-pack':'Военные научные пакеты','flammables':'Горючие вещества','flamethrower':'Огнемёт','land-mine':'Наземные мины','rocketry':'Ракетное вооружение','explosive-rocketry':'Разрывные ракеты','explosives':'Взрывчатка','laser':'Лазер','laser-turret':'Лазерные турели','battery':'Аккумуляторы'}.get(name,name.replace('-',' ').capitalize())
  for prefix,label in [('physical-projectile-damage-','Урон физических боеприпасов'),('weapon-shooting-speed-','Скорострельность'),('stronger-explosives-','Урон взрывчатки'),('refined-flammables-','Урон огнемёта'),('laser-weapons-damage-','Урон лазерного оружия'),('laser-shooting-speed-','Скорострельность лазеров')]:
   if name.startswith(prefix):translated=label+' '+name.removeprefix(prefix)
  lang['technology.factorycraft.'+tid]=translated if ru else name.replace('-',' ').capitalize()
 for damage in ['physical','explosion','fire','laser']:
  lang['death.attack.factorycraft.'+damage]='%1$s погибает от промышленного оружия' if ru else '%1$s was killed by industrial weaponry'
  lang['death.attack.factorycraft.'+damage+'.player']='%1$s погибает от оружия %2$s' if ru else '%1$s was shot by %2$s'
 write(path,lang)

effects=[]
for name,technology in REF['technologies'].items():
 tid=tech(name)+('_research' if name in REF['recipes'] and name!='military-science-pack' else '')
 for effect in technology['effects']:effects.append(dict(effect,technology='factorycraft:'+tid))
write(Path('factorycraft/combat_upgrades.json'),effects)

# First-person glove/forearm meshes are original geometry; other contexts keep the bare weapon.
for name in ['pistol','submachine_gun','shotgun','combat_shotgun','rocket_launcher','flamethrower']:
 path=Path('assets/factorycraft/models/item')/f'{name}.json';base=json.loads((RES/path).read_text())
 arm_boxes=[([6,-2,9],[11,5,14],'dark'),([7,-10,10],[11,-2,15],'wood')]
 if name!='pistol':arm_boxes += [([2,-7,1],[6,1,6],'wood'),([3,1,2],[7,6,7],'dark')]
 fp=dict(base);fp['elements']=base['elements']+mesh(arm_boxes)['elements']
 write(path,{'loader':'neoforge:separate_transforms','base':base,'perspectives':{'firstperson_righthand':fp,'firstperson_lefthand':fp}})
