"""Numerical Factorio2.1.24 + Space Age rail/source-gate audit; no proprietary art."""
import json
from pathlib import Path
R=Path(__file__).resolve().parents[1]/'src/main/resources'
def read(n):return json.loads((R/'data/factorycraft/recipe'/f'{n}.json').read_text())
for name,ingredients in [('rail',{'minecraft:cobblestone':1,'factorycraft:iron_stick':1,'factorycraft:steel_plate':1}),('iron_stick',{'factorycraft:iron_plate':1})]:
 r=read(name);assert {i['ingredient']['item']:i['count'] for i in r['inputs']}==ingredients
 assert r['ticks']==10 and r['result']=={'id':'factorycraft:'+name,'count':2}
 hand=read('craft_'+name);assert hand['inputs']==r['inputs'] and hand['result']==r['result']
tech=read('railway');assert tech['units']==75 and tech['ticks']==600
assert tech['prerequisites']==['factorycraft:logistics_2','factorycraft:engine']
assert tech['packs']=={'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1}
assert tech['unlocks']==['factorycraft:rail','factorycraft:iron_stick']
assert json.loads((R/'data/factorycraft/loot_table/blocks/rail_bed.json').read_text())['pools']==[]
for lang in ['en_us','ru_ru']:
 d=json.loads((R/'assets/factorycraft/lang'/f'{lang}.json').read_text())
 for i in range(-1,16):assert 'rail.factorycraft.direction_'+str(i) in d
 for key in ['item.factorycraft.rail','technology.factorycraft.railway','rail.factorycraft.preview']:assert key in d
print('RAILWAY_REFERENCE: source rail/stick bills, 0.5s, Railway75x30s/logistics2+engine, real gates and no part loot: PASS')
