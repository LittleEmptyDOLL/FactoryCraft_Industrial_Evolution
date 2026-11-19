"""Original FactoryCraft rail art; numeric source Wube factorio-data 2.1.24 + Space Age."""
from pathlib import Path
import json,random
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources'
def write(p,v):
 p=RES/p;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
for name,ingredients,result in [('iron_stick',[('factorycraft:iron_plate',1)],('factorycraft:iron_stick',2)),('rail',[('minecraft:cobblestone',1),('factorycraft:iron_stick',1),('factorycraft:steel_plate',1)],('factorycraft:rail',2))]:
 inputs=[{'ingredient':{'item':item},'count':count} for item,count in ingredients]
 r={'type':'factorycraft:processing','machine':'assembling_machine','inputs':inputs,'result':{'id':result[0],'count':result[1]},'ticks':10,'energy':10,'categories':['railway']}
 write(Path('data/factorycraft/recipe')/(name+'.json'),r)
 write(Path('data/factorycraft/recipe')/('craft_'+name+'.json'),{'type':'factorycraft:counted_crafting','inputs':inputs,'result':r['result']})
write(Path('data/factorycraft/recipe/railway.json'),{'type':'factorycraft:research','prerequisites':['factorycraft:logistics_2','factorycraft:engine'],'packs':{'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1},'units':75,'ticks':600,'unlocks':['factorycraft:rail','factorycraft:iron_stick'],'x':8,'y':3})
p=RES/'data/factorycraft/tags/item/productivity_outputs.json';v=json.loads(p.read_text());v['values']=list(dict.fromkeys(v['values']+['factorycraft:iron_stick']));write(p.relative_to(RES),v)
for n,col in [('rail_ballast',(81,84,78)),('rail_metal',(177,183,184)),('rail_sleepers',(85,63,41))]:
 rng=random.Random('FactoryCraft0300-'+n);im=Image.new('RGBA',(32,32));pixels=[]
 for y in range(32):
  for x in range(32):
   noise=rng.randrange(-22,23) if n=='rail_ballast' else rng.randrange(-8,9)
   stripe=12 if n=='rail_metal' and x%8<2 else -8 if n=='rail_sleepers' and y%4==0 else 0
   pixels.append(tuple(max(0,min(255,c+noise+stripe)) for c in col)+(255,))
 im.putdata(pixels);p=RES/'assets/factorycraft/textures/block'/(n+'.png');p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
write(Path('assets/factorycraft/blockstates/rail_bed.json'),{'variants':{'':{'model':'factorycraft:block/rail_bed'}}})
write(Path('assets/factorycraft/models/block/rail_bed.json'),{'loader':'factorycraft:rail','textures':{'bed':'factorycraft:block/rail_ballast','metal':'factorycraft:block/rail_metal','sleepers':'factorycraft:block/rail_sleepers','particle':'factorycraft:block/rail_ballast'}})
# Inventory miniature only; world rail width/length come from the actual sampled centerline.
faces={d:{'texture':'#metal' if d=='up' else '#sleepers'} for d in ['up','down','north','south','east','west']}
elements=[]
for z in [1,5,9,13]:elements.append({'from':[0,0,z],'to':[16,1,z+2],'faces':{d:{'texture':'#sleepers'} for d in faces}})
for x in [2,12]:elements.append({'from':[x,1,0],'to':[x+2,3,16],'faces':{d:{'texture':'#metal'} for d in faces}})
write(Path('assets/factorycraft/models/item/rail.json'),{'parent':'minecraft:block/block','textures':{'metal':'factorycraft:block/rail_metal','sleepers':'factorycraft:block/rail_sleepers','particle':'factorycraft:block/rail_metal'},'elements':elements})
write(Path('assets/factorycraft/models/item/iron_stick.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'factorycraft:item/iron_stick'}})
# Curated upstream artwork is required; never synthesize a silent replacement.
if not (RES/'assets/factorycraft/textures/item/iron_stick.png').exists():raise FileNotFoundError('Required curated iron_stick artwork is missing')
write(Path('data/factorycraft/loot_table/blocks/rail_bed.json'),{'type':'minecraft:block','pools':[]})
labels={'item.factorycraft.rail':('Rail','Рельсы'),'item.factorycraft.iron_stick':('Iron stick','Железный прут'),'block.factorycraft.rail_bed':('Rail','Рельсы'),'technology.factorycraft.railway':('Railway','Железная дорога'),'key.categories.factorycraft':('FactoryCraft','FactoryCraft'),'key.factorycraft.rail_rotate':('Rail: rotate goal tangent','Рельсы: поворот конечной касательной'),'key.factorycraft.rail_flip':('Rail: reverse starting tangent','Рельсы: развернуть начальную касательную'),'key.factorycraft.rail_cancel':('Rail: cancel planner','Рельсы: отменить планирование'),'rail.factorycraft.controls':('Right-click two endpoints. R: goal direction; X: reverse start; C or sneak-use: cancel. Continues after placement.','ПКМ по двум концам. R: направление конца; X: развернуть начало; C или Shift-ПКМ: отменить. После установки можно продолжать.'),'rail.factorycraft.dimensions':('Centered 2-block bed; 3-wide corridor; radius >=13; grade <=1:4. Endpoints need foundations. Clear 4 blocks above.','Полотно 2 блока по центру клетки; коридор 3; радиус от 13; уклон до 1:4. Концам нужны основания. Сверху оставьте 4 блока.'),'rail.factorycraft.preview':('Rail cost: %s | goal %s | %s','Стоимость: %s рельсов | конец %s | %s'),'rail.factorycraft.inspect':('Rail %s | %s m | endpoint degree %s/%s | loaded: %s','Рельс %s | %s м | связи концов %s/%s | загружен: %s')}
reasons={'ready':('Ready (server also checks claims)','Готово (сервер дополнительно проверит защиту)'),'selected':('Start selected; choose the second endpoint','Начало выбрано; укажите второй конец'),'blocked':('Clear the full rail and future train corridor','Освободите полотно и весь коридор будущего поезда'),'unloaded':('All touched chunks must be loaded','Все затронутые чанки должны быть загружены'),'border':('Outside world height or border','За пределами высоты или границы мира'),'support':('Both endpoints need a solid foundation','Обоим концам нужны твёрдые основания'),'entity':('An entity blocks the physical rail bed','Существо мешает установке полотна'),'permission':('Placement/removal denied by protection','Установка/демонтаж запрещены защитой'),'research':('Railway research required','Требуется исследование железной дороги'),'price':('Not enough rail items','Недостаточно рельсов'),'distance':('Rail length must be 1–128 blocks','Длина рельса должна быть 1–128 блоков'),'grade':('Grade exceeds 1:4; extend the approach','Уклон больше 1:4; удлините подход'),'radius':('Horizontal turn radius below 13 blocks','Горизонтальный радиус поворота меньше 13 блоков'),'fold':('Track folds backwards; adjust endpoint tangents','Путь загибается назад; измените касательные'),'geometry':('No valid smooth route for these endpoints','Для этих концов нет плавного допустимого пути'),'crossing':('Unrelated same-level tracks conflict; use a junction endpoint or a bridge','Конфликт путей на одной высоте; используйте конец развилки или мост'),'junction':('Junction is full (eight local segments maximum)','Узел заполнен (не более восьми локальных участков)'),'endpoint':('Select an existing rail endpoint, not its middle','Выберите конец существующего пути, а не середину'),'reach':('Selected target is outside interaction reach','Выбранная точка вне досягаемости'),'missing':('Segment no longer exists','Участок больше не существует')}
for k,v in reasons.items():labels['rail.factorycraft.'+k]=v
directions_en=['N','NNE (1:2)','NE','ENE (2:1)','E','ESE (2:1)','SE','SSE (1:2)','S','SSW (1:2)','SW','WSW (2:1)','W','WNW (2:1)','NW','NNW (1:2)']
directions_ru=['С','ССВ (1:2)','СВ','ВСВ (2:1)','В','ВЮВ (2:1)','ЮВ','ЮЮВ (1:2)','Ю','ЮЮЗ (1:2)','ЮЗ','ЗЮЗ (2:1)','З','ЗСЗ (2:1)','СЗ','ССЗ (1:2)']
labels['rail.factorycraft.direction_-1']=('auto','авто')
for i in range(16):labels['rail.factorycraft.direction_'+str(i)]=(directions_en[i],directions_ru[i])
labels.update({'rail.factorycraft.controls': ('Use: starter / select endpoint / connect. Camera: end tangent; R: override; X: reverse start; C/sneak: cancel.', 'ПКМ: короткий рельс / выбрать конец / соединить. Камера: направление конца; R: вручную; X: развернуть начало; C/Shift: отмена.'), 'rail.factorycraft.dimensions': ('Centered 2-block bed; 3-wide corridor; radius >=13; grade <=1:4. Endpoints need foundations. Clear 4 blocks above.', 'Полотно 2 блока по центру клетки; коридор 3; радиус от 13; уклон до 1:4. Концам нужны основания. Сверху оставьте 4 блока.')})
for lang,index in [('en_us',0),('ru_ru',1)]:
 p=RES/'assets/factorycraft/lang'/(lang+'.json');d=json.loads(p.read_text());d.update({k:v[index] for k,v in labels.items()});write(p.relative_to(RES),d)
print('Railway source bills/gate and original authored assets generated.')
