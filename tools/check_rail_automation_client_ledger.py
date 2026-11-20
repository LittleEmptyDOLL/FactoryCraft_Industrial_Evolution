#!/usr/bin/env python3
"""Compare the finite marked v0320 two-client stand across a real dedicated-server restart.
Requires nbtlib. Inputs are closed-world copies, never modifies a world or invents resources.
"""
import argparse, hashlib, json, uuid
from pathlib import Path
import nbtlib

def key(value):
    return str(uuid.UUID(int=sum((int(n)&0xffffffff) << (96-32*i) for i,n in enumerate(value))))

def load(path):
    return nbtlib.load(path)['data']

def snapshot(prefix):
    prefix=Path(prefix)
    paths={name:Path(str(prefix)+name+'.dat') for name in ['factorycraft_trains','factorycraft_rail_automation','factorycraft_railways']}
    return {name:load(path)for name,path in paths.items()}, {name:hashlib.sha256(path.read_bytes()).hexdigest()for name,path in paths.items()}

def compare(before,after):
    one={key(t['Id']):t for t in before['factorycraft_trains']['Trains']}
    two={key(t['Id']):t for t in after['factorycraft_trains']['Trains']}
    assert len(one)==2 and one.keys()==two.keys(),'canonical train UUIDs'
    cars=set();cargo=energy=spent=delta_spent=0;advanced=False
    for identity,t in one.items():
        u=two[identity]
        assert t['Owner']==u['Owner'] and t['Scope']==u['Scope'],'ownership scope'
        assert [key(c['Id'])for c in t['Cars']]==[key(c['Id'])for c in u['Cars']],'ordered carriage IDs'
        assert bool(t['Automation']['Automatic']) and bool(u['Automation']['Automatic']),'automatic mode'
        assert t['Automation']['Schedule']==u['Automation']['Schedule'],'saved GUI schedule'
        assert t['Automation']['Number']==u['Automation']['Number'],'public train identity'
        advanced |= abs(float(t['Distance'])-float(u['Distance']))>1e-6
        for a,b in zip(t['Cars'],u['Cars']):
            assert key(a['Id'])not in cars,'duplicate resource owner';cars.add(key(a['Id']))
            for field in ['Kind','Reversed','Health','Color','Shell','Bar','Filters','FuelSpeed','FuelAcceleration']:
                assert a[field]==b[field],field
            if int(a['Kind'])==0:
                totals=[]
                for c in [a,b]:
                    coal=0
                    for item in c['Items']['Items']:
                        assert str(item['id'])=='minecraft:coal','undeclared fuel'
                        coal+=int(item['count'])
                    totals.append(coal*4_000_000+int(c['FuelJ'])+int(c['SpentJ']))
                assert totals[0]==totals[1]==128_000_000,'finite per-locomotive fuel'
                assert int(b['SpentJ'])>=int(a['SpentJ']),'spent energy moved backwards'
                energy+=totals[1];spent+=int(b['SpentJ']);delta_spent+=int(b['SpentJ'])-int(a['SpentJ'])
            else:
                assert a['Items']==b['Items'] and a.get('Fluid')==b.get('Fluid'),'component-exact cargo/fluid'
                cargo+=sum(int(item['count'])for item in b['Items']['Items'])
    assert len(cars)==6 and cargo==62 and energy==512_000_000,'declared finite stand'
    assert delta_spent>0 and advanced,'no-rider restart did not run real paid movement'
    assert before['factorycraft_railways']==after['factorycraft_railways'],'paid physical rails changed'
    a=before['factorycraft_rail_automation'];b=after['factorycraft_rail_automation']
    assert a['Devices']==b['Devices'] and len(a['Devices'])==12,'trackside identities/settings'
    assert a['NextTrainId']==b['NextTrainId'],'restart created another train identity'
    return dict(trains=2,cars=6,cargo_items=62,coal_buffer_plus_spent_j=energy,spent_j=spent,
                paid_restart_delta_j=delta_spent,driverless_movement_after_restart=True,
                schedules_and_scopes_exact=True,trackside_devices=12,paid_rails_exact=True)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('before_prefix');p.add_argument('after_prefix');p.add_argument('--output',required=True);args=p.parse_args()
    a,ha=snapshot(args.before_prefix);b,hb=snapshot(args.after_prefix);report=compare(a,b)
    report.update(before_sha256=ha,after_sha256=hb)
    Path(args.output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
