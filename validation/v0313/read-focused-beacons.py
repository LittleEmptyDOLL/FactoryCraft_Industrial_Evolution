#!/usr/bin/env python3
"""Read native GameTest success beacons from a closed QA world, without modifying it."""
import argparse
import io
import json
import zlib
from pathlib import Path

import nbtlib

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('world', type=Path)
args = parser.parse_args()
chunks = {}


def chunk(x, z):
    key = (x, z)
    if key not in chunks:
        region = args.world / 'region' / f'r.{x // 32}.{z // 32}.mca'
        with region.open('rb') as stream:
            stream.seek(((x % 32) + (z % 32) * 32) * 4)
            location = stream.read(4)
            offset = int.from_bytes(location[:3], 'big') * 4096
            assert offset, f'missing chunk {key}'
            stream.seek(offset)
            size = int.from_bytes(stream.read(4), 'big')
            assert stream.read(1) == b'\x02', 'expected native zlib chunk'
            chunks[key] = nbtlib.File.parse(io.BytesIO(zlib.decompress(stream.read(size - 1))))
    return chunks[key]


def block(x, y, z):
    for section in chunk(x // 16, z // 16)['sections']:
        if int(section['Y']) != y // 16:
            continue
        states = section['block_states']
        palette = states['palette']
        if len(palette) == 1:
            return str(palette[0]['Name'])
        bits = max(4, (len(palette) - 1).bit_length())
        per_word = 64 // bits
        index = (y % 16) * 256 + (z % 16) * 16 + (x % 16)
        word = int(states['data'][index // per_word]) & ((1 << 64) - 1)
        return str(palette[(word >> ((index % per_word) * bits)) & ((1 << bits) - 1)]['Name'])
    return 'minecraft:air'


expected = {
    448: 'cancellednativedeathisnotinferredfromzerohealth',
    512: 'maximumspeedkillsthreemobsintravelorderwithonefueldebit',
    576: 'mobimpactusesnativedamageattributionandpreservestraincargo',
}
receipts = []
for x, name in expected.items():
    entities = chunk(x // 16, 0)['block_entities']
    owner = next(e for e in entities if str(e['id']) == 'minecraft:structure_block'
                 and int(e['x']) == x and str(e['metadata']) == name)
    y, z = int(owner['y']), int(owner['z'])
    assert block(x - 1, y - 2, z - 1) == 'minecraft:beacon', name
    glass = block(x - 1, y - 1, z - 1)
    assert glass == 'minecraft:lime_stained_glass', (name, glass)
    receipts.append(dict(test=name, structure=[x, y, z], native_result=glass))
print(json.dumps(dict(status='PASS', required_tests=3, receipts=receipts), indent=2))
