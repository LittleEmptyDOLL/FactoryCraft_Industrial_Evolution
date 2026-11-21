#!/usr/bin/env python3
"""Read-only independent-JVM held-red ledger. Requires nbtlib; never writes to a world."""
import argparse,hashlib,json,uuid
from pathlib import Path
import nbtlib

def key(a):
    return str(uuid.UUID(int=sum((int(v)&0xffffffff)<<(96-32*i) for i,v in enumerate(a))))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('checkpoint');p.add_argument('after_trains');p.add_argument('--output',required=True)
    args=p.parse_args()
    checkpoint=nbtlib.load(args.checkpoint)['data']['Checkpoint']
    actual=nbtlib.load(args.after_trains)['data']['Trains']
    byid={key(t['Id']):t for t in actual};cargo=energy=spent=cars=0
    for name,state in [('Train','State'),('Parked','ParkedState')]:
        t=byid[key(checkpoint[name])]
        assert t==checkpoint[state], 'different canonical train after JVM restart'
        for car in t['Cars']:
            cars+=1
            if int(car['Kind'])==0:
                coal=sum(int(s['count']) for s in car['Items']['Items'])
                energy+=coal*4_000_000+int(car['FuelJ'])+int(car['SpentJ'])
                spent+=int(car['SpentJ'])
            else:cargo+=sum(int(s['count']) for s in car['Items']['Items'])
    train=byid[key(checkpoint['Train'])];a=train['Automation']
    assert 'HeldSignal' in a and str(a['Phase'])=='SIGNAL_WAIT' and float(train['Speed'])==0
    assert cars==4 and cargo==7 and energy==96_000_000
    report=dict(independentJvmCanonicalEquality=True,trains=2,cars=cars,cargoItems=cargo,
                coalBufferPlusSpentJ=energy,spentJ=spent,heldSignal=key(a['HeldSignal']),
                limitedDestination=key(a['Destination']),sourcePhase=str(a['Phase']),speed=0,
                checkpointSha256=hashlib.sha256(Path(args.checkpoint).read_bytes()).hexdigest())
    Path(args.output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
