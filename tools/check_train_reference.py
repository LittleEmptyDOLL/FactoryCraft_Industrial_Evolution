"""Pinned numerical stock/progression audit; independent of native runtime acceptance."""
import json
from pathlib import Path
R=Path(__file__).resolve().parents[1]/'src/main/resources'
def recipe(name):return json.loads((R/'data/factorycraft/recipe'/f'{name}.json').read_text())
for name,ticks,parts in [('locomotive',80,{'engine_unit':20,'electronic_circuit':10,'steel_plate':30}),('cargo_wagon',20,{'iron_gear':10,'iron_plate':20,'steel_plate':20}),('fluid_wagon',30,{'iron_gear':10,'steel_plate':16,'fluid_pipe':8,'fluid_tank':1})]:
    r=recipe(name);assert r['ticks']==ticks
    actual={}
    for p in r['inputs']:
        ingredient=p['ingredient'];key=ingredient.get('item',ingredient.get('items',[None])[0]);actual[key.removeprefix('factorycraft:')]=p['count']
        if key in ['factorycraft:fluid_pipe','factorycraft:fluid_tank']:assert ingredient['strict'] and ingredient['components']=={}
    assert actual==parts
    assert recipe('craft_'+name)['inputs']==r['inputs']
t=recipe('fluid_wagon_technology');assert t['units']==200 and t['ticks']==600 and t['prerequisites']==['factorycraft:railway','factorycraft:fluid_handling']
assert t['packs']=={'factorycraft:automation_science_pack':1,'factorycraft:logistic_science_pack':1}
assert all('factorycraft:'+s in recipe('railway')['unlocks'] for s in ['locomotive','cargo_wagon'])
for lang in ['en_us','ru_ru']:
    d=json.loads((R/'assets/factorycraft/lang'/f'{lang}.json').read_text())
    for s in ['locomotive','cargo_wagon','fluid_wagon']:assert 'item.factorycraft.'+s in d
    for s in ['driving','filter_hint','unloaded','destroyed']:assert 'train.factorycraft.'+s in d
print('TRAIN_REFERENCE: source stock bills/times, strict empty fluid inputs, Railway/Fluid Wagon gates, EN/RU: PASS')
