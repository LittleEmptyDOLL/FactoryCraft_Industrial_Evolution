"""0.30.1 original procedural SFX and forward-axis weapon transforms; no imported assets."""
from pathlib import Path
import json,math,random,struct,subprocess,tempfile,wave
R=Path(__file__).resolve().parents[1]/'src/main/resources/assets/factorycraft'
for name in ['pistol','submachine_gun','shotgun','combat_shotgun','rocket_launcher','flamethrower']:
 p=R/'models/item'/f'{name}.json';data=json.loads(p.read_text())
 for model in [data.get('base',data),*data.get('perspectives',{}).values()]:
  display=model.setdefault('display',{})
  for side in ['right','left']:
   display[f'firstperson_{side}hand']={'rotation':[0,0,0],'translation':[0,1,-1],'scale':[.8,.8,.8]}
 p.write_text(json.dumps(data,indent=2)+'\n')
sounds={}
for name,duration in [('gun_shot',.13),('laser_shot',.22),('flame_shot',.3)]:
 sounds[name]={'subtitle':'subtitles.factorycraft.'+name,'sounds':[{'name':'factorycraft:'+name,'attenuation_distance':40}]}
 dest=R/'sounds'/f'{name}.ogg';dest.parent.mkdir(exist_ok=True)
 # Seeded noise and chirps are authored by this generator; all samples original.
 rng=random.Random('FactoryCraft0301-'+name);rate=22050;phase=0;last=0;samples=[]
 for i in range(round(rate*duration)):
  t=i/rate;noise=rng.uniform(-1,1);last=.6*last+.4*noise
  if name=='gun_shot':value=(noise*.5+math.sin(2*math.pi*110*t)*.5)*math.exp(-38*t)
  elif name=='laser_shot':
   phase+=2*math.pi*(1700*math.exp(-9*t)+180)/rate;value=(math.sin(phase)*.7+math.sin(phase*2)*.15)*math.exp(-15*t)
  else:value=last*.9*min(1,t/.02)*math.exp(-8*t)
  samples.append(struct.pack('<h',int(max(-1,min(1,value))*.7*32767)))
 with tempfile.TemporaryDirectory() as tmp:
  src=Path(tmp)/'original.wav'
  with wave.open(str(src),'wb') as f:f.setparams((1,2,rate,0,'NONE','not compressed'));f.writeframes(b''.join(samples))
  subprocess.run(['ffmpeg','-v','error','-y','-i',str(src),'-fflags','+bitexact','-flags:a','+bitexact','-map_metadata','-1','-c:a','libvorbis','-q:a','4',str(dest)],check=True)
p=R/'sounds.json';existing=json.loads(p.read_text()) if p.exists() else {};existing.update(sounds);p.write_text(json.dumps(existing,indent=2)+'\n')
status_en=['Incomplete structure','Searching for targets','Line of sight blocked','Rotating','Cooldown','Insufficient FE','Priming fuel buffer','No valid oil','Flame queue full','No ammunition','Firing','Path not loaded']
status_ru=['Конструкция недоступна','Поиск цели','Преграда перед целью','Поворот','Перезарядка','Недостаточно FE','Заполнение буфера','Нет подходящей нефти','Очередь пламени полна','Нет боеприпасов','Стрельба','Путь не загружен']
for lang,ru in [('en_us',False),('ru_ru',True)]:
 p=R/'lang'/f'{lang}.json';data=json.loads(p.read_text())
 data.update({f'gui.factorycraft.turret_status_{i}':s for i,s in enumerate(status_ru if ru else status_en)})
 data.update({'subtitles.factorycraft.gun_shot':'Выстрел' if ru else 'Gun fires','subtitles.factorycraft.laser_shot':'Выстрел лазера' if ru else 'Laser fires','subtitles.factorycraft.flame_shot':'Струя огня' if ru else 'Flame fires'})
 data['rail.factorycraft.controls']='ПКМ по земле: короткие рельсы. ПКМ по рельсам: выбрать конец, затем соединить. R: направление; X: развернуть; C или Shift-ПКМ (в том числе в воздух): отменить. После соединения выбор снимается.' if ru else 'Use on ground: short rail. Use on rail: select end, then connect. R: goal direction; X: reverse; C or sneak-use (also in air): cancel. Selection clears after connecting.'
 data['rail.factorycraft.selected']='Конец выбран; укажите место соединения. C: отмена' if ru else 'Rail end selected; choose a connection. C: cancel'
 p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
print('Original 0.30.1 sounds, forward weapon transforms and localized controls generated')
