#!/usr/bin/env python3
"""Read closed network-v0313 train snapshots. Requires nbtlib; never edits or refills a save."""
import argparse
import json
from pathlib import Path

import nbtlib

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('before', type=Path)
parser.add_argument('after', type=Path)
args = parser.parse_args()
before = nbtlib.load(args.before)['data']
after = nbtlib.load(args.after)['data']
assert before == after, 'canonical UUID/route/phase/resources changed across JVMs'
assert not after['Wrecks'], 'unexpected wreck owner'
assert len(after['Trains']) == 3, 'wrong declared scene'
seen = set()
kinds = [0, 0, 0]
spent = 0
energy = 0
iron = 0
water = 0
for train in after['Trains']:
    assert float(train['Speed']) == 0, 'checkpoint was not stopped'
    for car in train['Cars']:
        identity = tuple(map(int, car['Id']))
        assert identity not in seen, 'duplicate car UUID'
        seen.add(identity)
        kind = int(car['Kind'])
        kinds[kind] += 1
        assert 0 < float(car['Health']) <= (1000 if kind == 0 else 600)
        items = car['Items']['Items']
        if kind == 0:
            assert all(str(item['id']) == 'minecraft:coal' for item in items)
            balance = (int(car['SpentJ']) + int(car['FuelJ'])
                       + 4_000_000 * sum(int(item['count']) for item in items))
            assert balance == 128_000_000, 'coal/buffer/spent balance'
            spent += int(car['SpentJ'])
            energy += balance
        elif kind == 1:
            assert len(items) == 1 and str(items[0]['id']) == 'minecraft:iron_ingot'
            assert int(items[0]['count']) == 31
            iron += int(items[0]['count'])
        else:
            assert str(car['Fluid']['id']) == 'minecraft:water'
            assert int(car['Fluid']['amount']) == 123457
            water += int(car['Fluid']['amount'])
assert kinds == [3, 5, 4] and len(seen) == 12
assert spent > 0, 'no paid driving occurred'
print(json.dumps(dict(status='PASS', exact_canonical_state=True, trains=3, cars=12,
                      locomotives=3, iron=iron, fluid_mB=water,
                      chemical_energy_plus_spent_J=energy, spent_J=spent,
                      wrecks=0), indent=2))
