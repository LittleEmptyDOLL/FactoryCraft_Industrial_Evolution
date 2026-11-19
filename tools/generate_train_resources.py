"""Original 0.31 train art/audio and exact Wube 2.1.24 normal-quality stock bills/gates."""
from pathlib import Path
import json, math, random, struct, subprocess, tempfile, wave
from PIL import Image
R=Path(__file__).resolve().parents[1]/'src/main/resources'
def write(path,value):
    p=R/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
bills={
 'locomotive':(80,[('engine_unit',20),('electronic_circuit',10),('steel_plate',30)]),
 'cargo_wagon':(20,[('iron_gear',10),('iron_plate',20),('steel_plate',20)]),
 'fluid_wagon':(30,[('iron_gear',10),('steel_plate',16),('fluid_pipe',8),('fluid_tank',1)])}
for name,(ticks,parts) in bills.items():
    inputs=[]
    for item,count in parts:
        ingredient={'item':'factorycraft:'+item}
        if item in ['fluid_pipe','fluid_tank']:
            ingredient={'type':'neoforge:components','items':['factorycraft:'+item],'components':{},'strict':True}
        inputs.append({'ingredient':ingredient,'count':count})
    result={'id':'factorycraft:'+name,'count':1}
    write(Path('data/factorycraft/recipe')/(name+'.json'),{'type':'factorycraft:processing','machine':'assembling_machine','inputs':inputs,'result':result,'ticks':ticks,'energy':10,'categories':['railway']})
    write(Path('data/factorycraft/recipe')/('craft_'+name+'.json'),{'type':'factorycraft:counted_crafting','inputs':inputs,'result':result})
p=Path('data/factorycraft/recipe/railway.json');tech=json.loads((R/p).read_text());tech['unlocks']=list(dict.fromkeys(tech['unlocks']+['factorycraft:locomotive','factorycraft:cargo_wagon']));write(p,tech)
write(Path('data/factorycraft/recipe/fluid_wagon_technology.json'),{'type':'factorycraft:research','prerequisites':['factorycraft:railway','factorycraft:fluid_handling'],'packs':{'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1},'units':200,'ticks':600,'unlocks':['factorycraft:fluid_wagon'],'x':9,'y':3})
# Neutral original painted-metal grain, shaded by the distinct cached model surfaces.
rng=random.Random('FactoryCraft0310');im=Image.new('RGBA',(32,32));im.putdata([(v,v,v,255) for v in [rng.randrange(224,256) for _ in range(1024)]]);p=R/'assets/factorycraft/textures/entity/rolling_stock.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
for name in bills:
    elements=[]
    def box(a,b,tex):elements.append({'from':a,'to':b,'faces':{d:{'texture':'#'+tex} for d in ['up','down','north','south','east','west']}})
    box([3,2,-7],[13,4,23],'metal')
    for z in [-3,17]:
        for x in [2,12]:box([x,0,z-2],[x+2,3,z+2],'dark')
    if name=='locomotive':
        box([4,4,-6],[12,12,12],'paint');box([3,4,12],[13,9,22],'paint');box([3,15,12],[13,17,22],'dark')
        for x in [3,12]:
            for z in [12,21]:box([x,9,z],[x+1,15,z+1],'paint')
        box([6,12,3],[10,15,7],'dark')
    elif name=='cargo_wagon':
        for x in [3,12]:box([x,4,-6],[x+1,13,22],'paint')
        for z in [-6,21]:box([3,4,z],[13,13,z+1],'paint')
    else:
        box([3,6,-6],[13,14,22],'metal');box([5,4,-6],[11,16,22],'metal')
        for z in [0,16]:box([2.8,6,z],[13.2,14,z+1],'paint')
        box([6,16,6],[10,18,10],'dark')
    write(Path('assets/factorycraft/models/item')/(name+'.json'),{'parent':'minecraft:block/block','textures':{'paint':'factorycraft:block/machine_casing','metal':'factorycraft:block/rail_metal','dark':'factorycraft:block/rail_sleepers','particle':'factorycraft:block/rail_metal'},'elements':elements,'display':{'gui':{'rotation':[25,145,0],'translation':[0,-1,0],'scale':[.48,.48,.48]},'ground':{'scale':[.2,.2,.2]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.3,.3,.3]}}})
labels={
 'item.factorycraft.locomotive':('Locomotive','Локомотив'),'item.factorycraft.cargo_wagon':('Cargo Wagon','Грузовой вагон'),'item.factorycraft.fluid_wagon':('Fluid Wagon','Вагон-цистерна'),
 'technology.factorycraft.fluid_wagon_technology':('Fluid Wagon','Вагон-цистерна'),
 'train.factorycraft.item_help':('Use on rail; look along the intended front. Wrench: retrieve; sneak-wrench: uncouple behind.','ПКМ по рельсам; взгляд задаёт перед. Ключ: забрать; Shift-ключ: расцепить позади.'),
 'train.factorycraft.cargo_slots':('%s cargo slots','Грузовых слотов: %s'),'train.factorycraft.fluid_capacity':('%s mB capacity','Вместимость: %s мБ'),
 'train.factorycraft.driving':('%s km/h | W/S: power/reverse, Space: brake, A/D: %s, Shift: exit','%s км/ч | W/S: тяга/реверс, пробел: тормоз, A/D: %s, Shift: выход'),
 'train.factorycraft.branch_-1':('left','влево'),'train.factorycraft.branch_0':('straight','прямо'),'train.factorycraft.branch_1':('right','вправо'),
 'train.factorycraft.filters':('Filters','Фильтры'),'train.factorycraft.bar_less':('Bar -','Лимит −'),'train.factorycraft.bar_more':('Bar +','Лимит +'),
 'train.factorycraft.uncouple':('Uncouple','Расцепить'),'train.factorycraft.couple':('Couple','Сцепить'),'train.factorycraft.color':('Paint','Окраска'),
 'train.factorycraft.summary':('HP: %s | Cars: %s','Здоровье: %s | Вагонов: %s'),'train.factorycraft.fuel':('Burner: %s J','Топливо: %s Дж'),
 'train.factorycraft.enter_help':('Use to enter. Sneak-use for fuel. Loading requires a complete halt.','ПКМ: сесть. Shift-ПКМ: топливо. Погрузка — при полной остановке.'),
 'train.factorycraft.bar':('Cargo slots: %s / 40','Слоты: %s / 40'),'train.factorycraft.filter_hint':('Sample / right-click: clear','Образец / ПКМ: сброс'),
 'train.factorycraft.empty':('Empty','Пусто'),'subtitles.factorycraft.train_engine':('Locomotive engine','Двигатель локомотива'),
 'rail.factorycraft.occupied':('Track is occupied or reserved by rolling stock','Рельсы заняты или зарезервированы составом')}
for key,en,ru in [('placed','Rolling stock placed','Подвижной состав установлен'),('permission','No permission or placement was cancelled','Нет прав или установка отменена'),('track','No available railway at the target','В точке нет доступных рельсов'),('research','Technology is not researched','Технология не исследована'),('invalid','Invalid or changed stock item','Данные предмета неверны или изменились'),('destroyed','This stock is destroyed; its saved contents remain intact','Подвижной состав разрушен; содержимое сохранено'),('space','Not enough connected track for the full carriage','Недостаточно соединённых рельсов для вагона'),('unloaded','Track or clearance is not loaded and ticking','Рельсы или габарит не загружены для работы'),('blocked','Vehicle clearance is obstructed','Габарит состава занят блоками'),('occupied','Track is occupied by another train','Рельсы заняты другим составом'),('entity','Vehicle clearance contains an entity','В габарите находится существо'),('border','Outside the world border','За границей мира')]:labels['train.factorycraft.'+key]=(en,ru)
labels.update({'key.factorycraft.stock_debug': ('Trains: collider diagnostics (F8)', 'Поезда: коллайдеры (F8)')})
labels.update({'train.factorycraft.debug_legend': ('F8: hull green, mesh pink, broadphase blue, planned route amber', 'F8: корпус — зелёный, модель — розовый, AABB — синий, маршрут — янтарный')})
for lang,index in [('en_us',0),('ru_ru',1)]:
    p=Path('assets/factorycraft/lang')/(lang+'.json');d=json.loads((R/p).read_text());d.update({k:v[index] for k,v in labels.items()});write(p,d)
# Two-second seamless procedural diesel/rolling loop, authored here; no sampled third-party audio.
rate=22050;seconds=2;rng=random.Random('FactoryCraft0310engine');samples=[]
for i in range(rate*seconds):
    t=i/rate;v=.20*math.sin(2*math.pi*44*t)+.09*math.sin(2*math.pi*88*t)+.05*math.sin(2*math.pi*132*t)+.035*rng.uniform(-1,1)
    samples.append(struct.pack('<h',int(v*32767)))
dest=R/'assets/factorycraft/sounds/train_engine.ogg'
with tempfile.TemporaryDirectory() as tmp:
    src=Path(tmp)/'engine.wav'
    with wave.open(str(src),'wb') as f:f.setparams((1,2,rate,0,'NONE','not compressed'));f.writeframes(b''.join(samples))
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(src),'-fflags','+bitexact','-flags:a','+bitexact','-map_metadata','-1','-c:a','libvorbis','-q:a','4',str(dest)],check=True)
p=Path('assets/factorycraft/sounds.json');d=json.loads((R/p).read_text());d['train_engine']={'subtitle':'subtitles.factorycraft.train_engine','sounds':[{'name':'factorycraft:train_engine','attenuation_distance':48}]};write(p,d)
print('Stock bills, gates, original models/texture/audio and EN/RU: generated')
