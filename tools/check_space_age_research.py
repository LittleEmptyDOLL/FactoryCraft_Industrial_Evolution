"""Bounded source-derived release assertions. Wube 2.1.24 Space Age enabled, normal quality."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/data/factorycraft/recipe'
def read(name): return json.loads((ROOT / (name + '.json')).read_text())
def packs(names): return {'factorycraft:' + n + '_science_pack': 1 for n in names}
base = ['automation', 'logistic', 'chemical', 'space']
materials = {'speed': ('tungsten_carbide', 1, 'metallurgic_science_pack'),
             'efficiency': ('spoilage', 5, 'agricultural_science_pack'),
             'productivity': ('biter_egg', 1, 'biter_egg_handling')}
for family, (material, count, prerequisite) in materials.items():
    for tier in range(1, 4):
        stem = family + '_module' + ('' if tier == 1 else '_' + str(tier))
        recipe = read(stem)
        assert recipe['categories'] == ['crafting', 'electromagnetics'], stem
        assert recipe['ticks'] == [300, 600, 1200][tier - 1], stem
        expected = ([ (family + '_module' + ('' if tier == 2 else '_2'), 4)] if tier > 1 else [])
        expected += [('advanced_circuit', 5), ('electronic_circuit' if tier == 1 else 'processing_unit', 5)]
        if tier == 3: expected += [(material, count)]
        actual = [(i['ingredient']['item'].removeprefix('factorycraft:'), i['count']) for i in recipe['inputs']]
        assert actual == expected, (stem, actual)
        tech = read(family + '_modules_' + str(tier))
        if tier == 1:
            assert tech['units'] == 50 and tech['ticks'] == 600
            assert tech['packs'] == packs(['automation', 'logistic'])
        elif tier == 2:
            assert tech['units'] == 200 and tech['ticks'] == 600
            assert tech['packs'] == packs(base)
            assert tech['prerequisites'] == ['factorycraft:' + family + '_modules_1', 'factorycraft:space_science_pack']
        else:
            assert tech['units'] == (2000 if family == 'speed' else 300) and tech['ticks'] == 1200
            assert tech['packs'] == packs(base + ['metallurgic' if family == 'speed' else 'agricultural'])
            assert tech['prerequisites'] == ['factorycraft:' + family + '_modules_2', 'factorycraft:' + prerequisite]
for level, units in enumerate([100, 200, 250, 500, 500, 500], 1):
    tech = read('lab_research_speed_' + str(level))
    names = ['automation', 'logistic'] + (['chemical'] if level >= 3 else []) + (['production'] if level >= 5 else []) + (['utility'] if level >= 6 else [])
    assert tech['units'] == units and tech['ticks'] == 600 and tech['packs'] == packs(names)
    assert not tech.get('repeatable', False)
for name, units, ticks, science in [
    ('automation',10,200,['automation']),
    ('automation_2',40,300,['automation','logistic']),
    ('automation_3',500,1200,['automation','logistic','chemical','production']),
    ('electronics',200,300,['automation','logistic']),
    ('advanced_electronics_2',300,600,['automation','logistic','chemical']),
    ('chemical_science',75,200,['automation','logistic']),
    ('effect_transmission',75,600,['automation','logistic','chemical','production'])]:
    technology=read(name)
    assert technology['units']==units and technology['ticks']==ticks and technology['packs']==packs(science),name
for level,units in [(1,2000),(2,3000)]:
    tech=read('transport_belt_capacity_'+str(level))
    assert tech['sandbox_only'] and tech['units']==units and tech['ticks']==1200
    assert tech['packs']==packs(['automation','logistic','chemical','production','utility','space','agricultural'])
    assert tech['prerequisites']==(['factorycraft:stack_inserter'] if level==1 else ['factorycraft:transport_belt_capacity_1','factorycraft:inserter_capacity_bonus_7'])
assert not (ROOT / 'lab_efficiency.json').exists()
future = {'production','utility','space','metallurgic','agricultural','electromagnetic','cryogenic','promethium'}
for path in ROOT.glob('*.json'):
    d = json.loads(path.read_text())
    outputs = [d.get('result', {})] + d.get('results', [])
    for result in outputs:
        if isinstance(result, dict):
            assert result.get('id') not in ({'factorycraft:' + n + '_science_pack' for n in future} | {'factorycraft:tungsten_carbide','factorycraft:biter_egg','factorycraft:spoilage'}), path
print('SPACE_AGE_MODULE_REFERENCE: nine module recipes/technologies, six finite research speeds, Automation 3, categories and no fake future science: PASS')
