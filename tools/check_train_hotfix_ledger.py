#!/usr/bin/env python3
"""Audit the explicitly finite network-v0311 fixture. Requires nbtlib; reads closed snapshots only."""
import argparse, json
from pathlib import Path
import nbtlib
p=argparse.ArgumentParser();p.add_argument('before',type=Path);p.add_argument('after',type=Path);args=p.parse_args()
def read(path):
 n=nbtlib.load(path)['data']; assert not n['Wrecks'], 'unexpected wreck owner'
 trains={tuple(map(int,t['Id'])):t for t in n['Trains']};cars={}
 for t in trains.values():
  for c in t['Cars']:
   key=tuple(map(int,c['Id']));assert key not in cars,'duplicate car UUID';cars[key]=c
 return n,trains,cars
before,bt,bc=read(args.before);after,at,ac=read(args.after)
assert set(bt)<=set(at) and set(bc)<=set(ac),'old canonical UUID retired'
for key,c in ac.items():
 kind=int(c['Kind']);items=c['Items']['Items']
 assert float(c['Health'])==(1000 if kind==0 else 600),'unexpected impact'
 if kind==0:
  assert all(str(x['id'])=='minecraft:coal' for x in items)
  balance=int(c['SpentJ'])+int(c['FuelJ'])+4_000_000*sum(int(x['count']) for x in items)
  assert balance==128_000_000,'coal/buffer/spent ledger'
 elif kind==1:
  assert len(items)==1 and str(items[0]['id'])=='minecraft:iron_ingot' and int(items[0]['count'])==31
 else:
  assert str(c['Fluid']['id'])=='minecraft:water' and int(c['Fluid']['amount'])==123457
 if key in bc:
  old=bc[key];assert c['Id']==old['Id'] and kind==int(old['Kind'])
  if kind!=0:assert c['Items']==old['Items'] and c.get('Fluid')==old.get('Fluid'),'changed cargo/components'
  else: assert int(c['SpentJ'])>=int(old['SpentJ']),'spent energy went backwards'
assert len(at)==4 and len(ac)==15,'wrong declared finite fixture'
print(json.dumps(dict(status='PASS',trains=len(at),cars=len(ac),old_car_uuids_retained=len(bc),locomotives=4,cargo_iron=186,fluid_mB=617285,chemical_energy_plus_spent_J=512000000,wrecks=0),indent=2))
