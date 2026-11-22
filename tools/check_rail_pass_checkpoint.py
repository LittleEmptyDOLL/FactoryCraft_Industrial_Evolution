#!/usr/bin/env python3
"""Read-only independent-JVM waypoint receipt check. Requires nbtlib; never edits worlds."""
import argparse, json, uuid
from pathlib import Path
import nbtlib


def identity(value):
    return str(uuid.UUID(int=sum((int(n) & 0xffffffff) << (96 - 32 * i) for i, n in enumerate(value))))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path)
    parser.add_argument('before_trains', type=Path)
    parser.add_argument('after', type=Path)
    parser.add_argument('after_trains', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    before = nbtlib.load(args.before)['data']['Checkpoint']
    after = nbtlib.load(args.after)['data']['Checkpoint']
    owners = nbtlib.load(args.before_trains)['data']['Trains']
    final = nbtlib.load(args.after_trains)['data']['Trains']
    train = identity(before['Train'])
    assert int(before['Stage']) == 1 and int(after['Stage']) == 2 and bool(after['Replayed'])
    assert before['State'] == after['State'], 'frozen checkpoint was rewritten'
    state = next(t for t in owners if identity(t['Id']) == train)
    assert state == before['State'], 'disk owner differs from exact frozen receipt'
    assert all(identity(t['Id']) != train for t in final), 'retired train restored twice'
    auto = state['Automation']
    assert len(auto['Upcoming']) == 2 and bool(auto['Automatic']) and bool(auto['Routed'])
    assert int(auto['WaypointDirection']) == int(state['Direction']) == 1
    assert len(state['Cars']) == 2
    cargo = state['Cars'][1]['Items']['Items']
    assert sum(int(i['count']) for i in cargo) == 7 and all(str(i['id']) == 'minecraft:iron_ingot' for i in cargo)
    assert 'components' in cargo[0], 'typed cargo lost its data components'
    loco = state['Cars'][0]
    energy = int(loco['FuelJ']) + int(loco['SpentJ']) + 4_000_000 * sum(int(i['count']) for i in loco['Items']['Items'])
    assert energy == 64_000_000
    previous = float(auto['TargetHead'])
    for row in auto['Upcoming']:
        assert float(row['Head']) > previous
        previous = float(row['Head'])
    result = dict(status='PASS', canonical_train=train, cars=2, component_typed_iron=7,
                  coal_buffer_plus_spent_j=energy, prepared_waypoints=2,
                  exact_frozen_metadata_preserved=True, completed_owner_retired=True,
                  note='Native independent process compares the live owner before loading chunks and proves actual paid passage; this script checks the closed-world receipts.')
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
