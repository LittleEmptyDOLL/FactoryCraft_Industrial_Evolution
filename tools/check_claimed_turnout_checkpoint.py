#!/usr/bin/env python3
"""Read-only claimed-junction diagnostic receipt validation. Requires nbtlib; never edits a world."""
import argparse, hashlib, json, uuid
from pathlib import Path
import nbtlib

def identity(a):
    return str(uuid.UUID(int=sum((int(v)&0xffffffff)<<(96-32*i) for i,v in enumerate(a))))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('before');p.add_argument('after');p.add_argument('after_trains');p.add_argument('--output',required=True)
    args=p.parse_args()
    before=nbtlib.load(args.before)['data']['Checkpoint'];after=nbtlib.load(args.after)['data']['Checkpoint']
    actual=nbtlib.load(args.after_trains)['data']['Trains']
    assert int(before['Stage'])==1 and int(after['Stage'])==2 and bool(after['ReplayedClaim'])
    present={identity(t['Id']) for t in actual};energy=cargo=cars=0;car_ids=set();claimed=False
    for i in range(2):
        assert before['State'+str(i)]==after['State'+str(i)],'immutable initial expected owner changed'
        t=before['State'+str(i)];assert identity(t['Id'])==identity(before['Train'+str(i)])
        assert identity(t['Id']) not in present,'completed diagnostic resurrected old train owner'
        assert bool(t['Automation']['Automatic']) and 'Destination' in t['Automation']
        if identity(t['Id'])==identity(before['ClaimOwner']):
            claimed=any(identity(c['Block'])==identity(before['Region']) for c in t['Automation']['Reserved'])
        for c in t['Cars']:
            car_id=identity(c['Id']);assert car_id not in car_ids;car_ids.add(car_id);cars+=1
            if int(c['Kind'])==0:
                coal=sum(int(item['count']) for item in c['Items']['Items']);energy+=coal*4_000_000+int(c['FuelJ'])+int(c['SpentJ'])
            else:cargo+=sum(int(item['count']) for item in c['Items']['Items'])
    assert claimed and cars==6 and cargo==14 and energy==128_000_000
    report=dict(initialExclusiveClaim=identity(before['Region']),originalClaimOwner=identity(before['ClaimOwner']),
                trains=2,cars=cars,finiteCargoItems=cargo,coalBufferPlusSpentJ=energy,
                independentJvmClaimReplayAndCompletion=True,retiredOwnersAbsent=True,
                beforeSha256=hashlib.sha256(Path(args.before).read_bytes()).hexdigest(),
                afterSha256=hashlib.sha256(Path(args.after).read_bytes()).hexdigest())
    Path(args.output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
