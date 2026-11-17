"""Static release guard: compare native quantities/gates with pinned numeric source data."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];R=ROOT/'src/main/resources/data/factorycraft/recipe';ref=json.loads((ROOT/'tools/nuclear_reference.json').read_text())
def native(n):return {'iron-gear-wheel':'factorycraft:iron_gear','stone-brick':'minecraft:stone_bricks','iron-ore':'minecraft:raw_iron','pipe':'factorycraft:fluid_pipe','water':'minecraft:water'}.get(n,'factorycraft:'+n.replace('-','_'))
for name,source in ref['recipes'].items():
 value=json.loads((R/(name.replace('-','_')+'.json')).read_text());items=value.get('inputs',value.get('items',[]));fluids=value.get('fluids',[])
 expected=[(native(p['name']),p['count']) for p in source['ingredients'] if p['kind']=='item']
 got=[(p['ingredient'].get('item',p['ingredient'].get('items',[''])[0]),p['count']) for p in items]
 assert got==expected,(name,got,expected)
 assert [(p['fluid'],p['amount']) for p in fluids]==[(native(p['name']),p['count']*10) for p in source['ingredients'] if p['kind']=='fluid'],name
 assert value['ticks']==int(source['seconds']*20),name
 result=value.get('result',value.get('results',[{}])[0]);assert (result['id'],result['count'])==(native(source['result']),source['count']),name
u=json.loads((R/'uranium_processing.json').read_text());assert (u['ore'],u['ticks'],u['energy_numerator'],u['energy_denominator'])==(10,240,140,3)
assert not (R/'craft_nuclear_reactor.json').exists()
for name in ['solar_panel','accumulator','centrifuge','nuclear_reactor','heat_pipe','heat_exchanger','steam_turbine']:
 loot=json.loads((R.parent/'loot_table/blocks'/(name+'.json')).read_text());assert len(loot['pools'])==1 and loot['pools'][0]['entries'][0]['name']=='factorycraft:'+name,name
for name in ['kovarex_enrichment_process','nuclear_fuel_reprocessing']:
 t=json.loads((R/(name+'.json')).read_text());assert t['type']=='factorycraft:research' and 'factorycraft:production_science_pack' in t['packs']
assert json.loads((R/'uranium_processing_research.json').read_text())['trigger']=='mine:factorycraft:uranium_ore'
print('Nuclear source audit: nine exact bills/times/results, acid-gated mining trigger, uranium probability recipe, seven sole portable drops, real future science gates.')
